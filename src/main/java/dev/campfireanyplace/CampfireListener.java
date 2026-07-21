package dev.campfireanyplace;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Campfire;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.Plugin;

final class CampfireListener implements Listener {
    private final Plugin plugin;
    private final Map<UUID, CampfireContents.Snapshot> pendingPlacements = new HashMap<>();

    CampfireListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void rememberCampfireBeforePlacement(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        CampfireContents.Snapshot contents = contentsFrom(event.getItem());
        if (contents == null) {
            return;
        }

        UUID playerId = event.getPlayer().getUniqueId();
        pendingPlacements.put(playerId, contents);
        plugin.getServer().getScheduler().runTask(plugin,
                () -> pendingPlacements.remove(playerId, contents));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCampfireInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND
                || event.getBlockFace() != BlockFace.UP) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null || !(clicked.getState() instanceof Campfire campfire)) {
            return;
        }

        ItemStack held = event.getItem();
        if (held == null || held.getType().isAir()) {
            return;
        }

        int slot = CampfireContents.firstEmptySlot(campfire);
        if (slot < 0) {
            return;
        }

        campfire.setItem(slot, CampfireContents.oneItem(held));
        campfire.setCookTime(slot, 0);
        campfire.setCookTimeTotal(slot, cookingTimeFor(held));
        if (!campfire.update(true, false)) {
            return;
        }

        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            held.subtract(1);
        }
        event.setCancelled(true);
    }

    private int cookingTimeFor(ItemStack item) {
        var recipes = plugin.getServer().recipeIterator();
        while (recipes.hasNext()) {
            Recipe recipe = recipes.next();
            if (recipe instanceof CampfireRecipe campfireRecipe
                    && campfireRecipe.getInputChoice().test(item)) {
                return campfireRecipe.getCookingTime();
            }
        }
        // A positive total keeps non-recipe items in a stable block-entity state.
        return 600;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCampfirePlaced(BlockPlaceEvent event) {
        CampfireContents.Snapshot contents = pendingPlacements.remove(event.getPlayer().getUniqueId());
        if (contents == null) {
            contents = contentsFrom(event.getItemInHand());
        }
        if (contents == null) {
            return;
        }

        Block placed = event.getBlockPlaced();
        CampfireContents.Snapshot restoreContents = contents;
        restore(placed, restoreContents);
        plugin.getServer().getScheduler().runTask(plugin, () -> restore(placed, restoreContents));
    }

    private static CampfireContents.Snapshot contentsFrom(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof BlockStateMeta meta)) {
            return null;
        }
        BlockState state = meta.getBlockState();
        if (!(state instanceof Campfire campfire)) {
            return null;
        }
        return CampfireContents.capture(campfire);
    }

    private static void restore(Block block, CampfireContents.Snapshot contents) {
        if (!(block.getState() instanceof Campfire campfire)) {
            return;
        }
        contents.applyTo(campfire);
        campfire.update(true, false);
    }
}
