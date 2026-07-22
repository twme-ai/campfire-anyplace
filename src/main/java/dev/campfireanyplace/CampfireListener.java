package dev.campfireanyplace;

import io.papermc.paper.event.player.PlayerPickBlockEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Campfire;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.BlockDataMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.Plugin;

final class CampfireListener implements Listener {
    private final Plugin plugin;
    private final Map<UUID, CampfirePlacement> pendingPlacements = new HashMap<>();

    CampfireListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void rememberCampfireBeforePlacement(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !shouldHandleHand(event)) {
            return;
        }

        CampfirePlacement placement = placementFrom(event.getItem());
        if (placement == null) {
            return;
        }

        UUID playerId = event.getPlayer().getUniqueId();
        pendingPlacements.put(playerId, placement);
        plugin.getServer().getScheduler().runTask(plugin,
                () -> pendingPlacements.remove(playerId, placement));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCampfireInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || !shouldHandleHand(event)
                || event.getBlockFace() != BlockFace.UP) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null || !(clicked.getState() instanceof Campfire campfire)) {
            return;
        }

        ItemStack held = event.getItem();
        if (CampfireContents.isEmpty(held)) {
            return;
        }

        int slot = CampfireContents.firstEmptySlot(campfire);
        if (slot < 0) {
            return;
        }

        BlockData originalBlockData = campfire.getBlockData().clone();
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);

        campfire.setItem(slot, CampfireContents.oneItem(held));
        campfire.setCookTime(slot, 0);
        OptionalInt cookingTime = cookingTimeFor(held);
        if (cookingTime.isPresent()) {
            campfire.setCookTimeTotal(slot, cookingTime.getAsInt());
            campfire.startCooking(slot);
        } else {
            // Paper ejects a non-recipe item when its cooking timer completes.
            campfire.setCookTimeTotal(slot, Integer.MAX_VALUE);
            campfire.stopCooking(slot);
        }
        campfire.setBlockData(originalBlockData);
        if (!campfire.update(false, false)) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract(1);
        }
        sendCampfireUpdate(player, clicked);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            restoreBlockData(clicked, originalBlockData);
            broadcastCampfireUpdate(clicked);
            player.updateInventory();
        });
    }

    private OptionalInt cookingTimeFor(ItemStack item) {
        var recipes = plugin.getServer().recipeIterator();
        while (recipes.hasNext()) {
            Recipe recipe = recipes.next();
            if (recipe instanceof CampfireRecipe campfireRecipe
                    && campfireRecipe.getInputChoice().test(item)) {
                return OptionalInt.of(campfireRecipe.getCookingTime());
            }
        }
        return OptionalInt.empty();
    }

    private static boolean shouldHandleHand(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.HAND) {
            return true;
        }
        if (event.getHand() != EquipmentSlot.OFF_HAND) {
            return false;
        }
        return CampfireContents.isEmpty(event.getPlayer().getInventory().getItemInMainHand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCampfirePicked(PlayerPickBlockEvent event) {
        Player player = event.getPlayer();
        if (!event.isIncludeData()
                || player.getGameMode() != GameMode.CREATIVE
                || !(event.getBlock().getState() instanceof Campfire source)
                || !(source.getBlockData() instanceof Lightable lightable)
                || lightable.isLit()) {
            return;
        }

        int targetSlot = event.getTargetSlot();
        PickedBlockData picked = new PickedBlockData(
                source.getType(),
                plugin.getServer().createBlockData(source.getType(), "[lit=false]"));
        plugin.getServer().getScheduler().runTask(plugin,
                () -> preservePickedBlockData(player, targetSlot, picked));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCampfirePlaced(BlockPlaceEvent event) {
        CampfirePlacement placement = pendingPlacements.remove(event.getPlayer().getUniqueId());
        if (placement == null) {
            placement = placementFrom(event.getItemInHand());
        }
        if (placement == null) {
            return;
        }

        Block placed = event.getBlockPlaced();
        CampfirePlacement restorePlacement = placement;
        plugin.getServer().getScheduler().runTask(plugin, () -> restore(placed, restorePlacement));
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        stabilizeCampfires(event.getChunk());
    }

    void stabilizeLoadedCampfires() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                stabilizeCampfires(chunk);
            }
        }
    }

    private static CampfirePlacement placementFrom(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof BlockStateMeta meta)) {
            return null;
        }
        BlockState state = meta.getBlockState();
        if (!(state instanceof Campfire campfire)) {
            return null;
        }
        boolean unlit = campfire.getBlockData() instanceof Lightable lightable && !lightable.isLit();
        return new CampfirePlacement(CampfireContents.capture(campfire), unlit);
    }

    private void restore(Block block, CampfirePlacement placement) {
        if (!(block.getState() instanceof Campfire campfire)) {
            return;
        }
        placement.contents().applyTo(campfire);
        if (placement.unlit()) {
            BlockData blockData = campfire.getBlockData().clone();
            if (blockData instanceof Lightable lightable) {
                lightable.setLit(false);
                campfire.setBlockData(blockData);
            }
        }
        stabilizeNonRecipeItems(campfire);
        if (campfire.update(false, false)) {
            broadcastCampfireUpdate(block);
        }
    }

    private static void preservePickedBlockData(Player player, int targetSlot, PickedBlockData picked) {
        if (targetSlot < 0 || targetSlot >= player.getInventory().getSize()) {
            return;
        }
        ItemStack item = player.getInventory().getItem(targetSlot);
        if (item == null
                || item.getType() != picked.material()
                || !(item.getItemMeta() instanceof BlockStateMeta meta)
                || !(meta instanceof BlockDataMeta blockDataMeta)) {
            return;
        }
        blockDataMeta.setBlockData(picked.blockData());
        item.setItemMeta(meta);
        player.getInventory().setItem(targetSlot, item);
        player.updateInventory();
    }

    private void stabilizeCampfires(Chunk chunk) {
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Campfire campfire && stabilizeNonRecipeItems(campfire)) {
                if (campfire.update(false, false) && campfire.getBlock() != null) {
                    broadcastCampfireUpdate(campfire.getBlock());
                }
            }
        }
    }

    private boolean stabilizeNonRecipeItems(Campfire campfire) {
        boolean changed = false;
        for (int slot = 0; slot < campfire.getSize(); slot++) {
            ItemStack item = campfire.getItem(slot);
            if (CampfireContents.isEmpty(item)) {
                continue;
            }
            if (campfire.getCookTimeTotal(slot) == Integer.MAX_VALUE
                    && campfire.isCookingDisabled(slot)) {
                continue;
            }
            if (cookingTimeFor(item).isPresent()) {
                continue;
            }
            if (campfire.getCookTimeTotal(slot) != Integer.MAX_VALUE) {
                campfire.setCookTimeTotal(slot, Integer.MAX_VALUE);
                changed = true;
            }
            if (!campfire.isCookingDisabled(slot)) {
                campfire.stopCooking(slot);
                changed = true;
            }
        }
        return changed;
    }

    private static void restoreBlockData(Block block, BlockData originalBlockData) {
        if (block.getType() == originalBlockData.getMaterial()
                && !block.getBlockData().equals(originalBlockData)) {
            block.setBlockData(originalBlockData, false);
        }
    }

    private static void broadcastCampfireUpdate(Block block) {
        for (Player player : block.getChunk().getPlayersSeeingChunk()) {
            sendCampfireUpdate(player, block);
        }
    }

    private static void sendCampfireUpdate(Player player, Block block) {
        if (block.getState() instanceof Campfire campfire) {
            Location location = block.getLocation();
            player.sendBlockChange(location, campfire.getBlockData());
            player.sendBlockUpdate(location, campfire);
        }
    }

    private record CampfirePlacement(CampfireContents.Snapshot contents, boolean unlit) {
    }

    private record PickedBlockData(Material material, BlockData blockData) {
    }
}
