package dev.campfireanyplace;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Campfire;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class CampfireListenerTest {
    @ParameterizedTest
    @EnumSource(value = Material.class, names = {"CAMPFIRE", "SOUL_CAMPFIRE"})
    void nonRecipeItemsWorkOnEveryCampfireType(Material campfireType) {
        InteractionFixture fixture = new InteractionFixture(campfireType, EquipmentSlot.HAND);

        fixture.listener.onCampfireInteract(fixture.event);

        verify(fixture.placed).setAmount(1);
        verify(fixture.campfire).setItem(0, fixture.placed);
        verify(fixture.campfire).setCookTime(0, 0);
        verify(fixture.campfire).setCookTimeTotal(0, Integer.MAX_VALUE);
        verify(fixture.campfire).stopCooking(0);
        verify(fixture.campfire).setBlockData(fixture.originalBlockData);
        verify(fixture.held).subtract(1);
        verify(fixture.event).setUseInteractedBlock(Event.Result.DENY);
        verify(fixture.event).setUseItemInHand(Event.Result.DENY);
        verify(fixture.event).setCancelled(true);

        fixture.runNextTick();

        verify(fixture.player, times(2)).sendBlockChange(fixture.location, fixture.blockData);
        verify(fixture.player, times(2)).sendBlockUpdate(fixture.location, fixture.campfire);
        verify(fixture.observer).sendBlockChange(fixture.location, fixture.blockData);
        verify(fixture.observer).sendBlockUpdate(fixture.location, fixture.campfire);
        verify(fixture.player).updateInventory();
    }

    @Test
    void campfireRecipeItemsContinueCooking() {
        InteractionFixture fixture = new InteractionFixture(Material.SOUL_CAMPFIRE, EquipmentSlot.HAND);
        CampfireRecipe recipe = mock(CampfireRecipe.class);
        RecipeChoice input = mock(RecipeChoice.class);
        when(recipe.getInputChoice()).thenReturn(input);
        when(recipe.getCookingTime()).thenReturn(200);
        when(input.test(fixture.held)).thenReturn(true);
        when(fixture.server.recipeIterator()).thenAnswer(ignored -> List.<Recipe>of(recipe).iterator());

        fixture.listener.onCampfireInteract(fixture.event);

        verify(fixture.campfire).setCookTimeTotal(0, 200);
        verify(fixture.campfire).startCooking(0);
        verify(fixture.campfire, never()).stopCooking(0);
    }

    @Test
    void offHandWorksWhenTheMainHandIsEmpty() {
        InteractionFixture fixture = new InteractionFixture(Material.SOUL_CAMPFIRE, EquipmentSlot.OFF_HAND);

        fixture.listener.onCampfireInteract(fixture.event);

        verify(fixture.campfire).setItem(0, fixture.placed);
        verify(fixture.held).subtract(1);
        verify(fixture.event).setCancelled(true);
    }

    @Test
    void offHandDoesNotCreateADuplicateWhenTheMainHandHasAnItem() {
        InteractionFixture fixture = new InteractionFixture(Material.CAMPFIRE, EquipmentSlot.OFF_HAND);
        when(fixture.mainHand.getType()).thenReturn(Material.DIRT);

        fixture.listener.onCampfireInteract(fixture.event);

        verify(fixture.campfire, never()).setItem(anyInt(), any(ItemStack.class));
        verify(fixture.event, never()).setCancelled(true);
    }

    @Test
    void failedBlockStateUpdateDoesNotConsumeTheHeldItem() {
        InteractionFixture fixture = new InteractionFixture(Material.CAMPFIRE, EquipmentSlot.HAND);
        when(fixture.campfire.update(false, false)).thenReturn(false);

        fixture.listener.onCampfireInteract(fixture.event);

        verify(fixture.held, never()).subtract(1);
        verify(fixture.scheduler, never()).runTask(eq(fixture.plugin), any(Runnable.class));
        verify(fixture.player, never()).sendBlockUpdate(any(Location.class), any(Campfire.class));
    }

    @Test
    void loadedCampfiresFromOlderVersionsAreStabilized() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        Campfire regular = legacyCampfire(Material.CAMPFIRE);
        Campfire soul = legacyCampfire(Material.SOUL_CAMPFIRE);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorlds()).thenReturn(List.of(world));
        when(server.recipeIterator()).thenAnswer(ignored -> Collections.emptyIterator());
        when(world.getLoadedChunks()).thenReturn(new Chunk[] {chunk});
        when(chunk.getTileEntities()).thenReturn(new BlockState[] {regular, soul});
        CampfireListener listener = new CampfireListener(plugin);

        listener.stabilizeLoadedCampfires();

        verifyStabilized(regular);
        verifyStabilized(soul);
    }

    @Test
    void newlyLoadedChunksAreStabilizedWithoutRewritingStableOrEmptySlots() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        Chunk chunk = mock(Chunk.class);
        Campfire legacy = legacyCampfire(Material.SOUL_CAMPFIRE);
        Campfire stable = mock(Campfire.class);
        ItemStack stableItem = mock(ItemStack.class);
        Campfire empty = mock(Campfire.class);
        ItemStack air = mock(ItemStack.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.recipeIterator()).thenAnswer(ignored -> Collections.emptyIterator());
        when(stable.getSize()).thenReturn(1);
        when(stable.getItem(0)).thenReturn(stableItem);
        when(stableItem.getType()).thenReturn(Material.PLAYER_HEAD);
        when(stable.getCookTimeTotal(0)).thenReturn(Integer.MAX_VALUE);
        when(stable.isCookingDisabled(0)).thenReturn(true);
        when(empty.getSize()).thenReturn(1);
        when(empty.getItem(0)).thenReturn(air);
        when(air.getType()).thenReturn(Material.AIR);
        when(chunk.getTileEntities()).thenReturn(new BlockState[] {legacy, stable, empty});
        ChunkLoadEvent event = mock(ChunkLoadEvent.class);
        when(event.getChunk()).thenReturn(chunk);
        CampfireListener listener = new CampfireListener(plugin);

        listener.onChunkLoad(event);

        verifyStabilized(legacy);
        verify(stable, never()).update(false, false);
        verify(empty, never()).update(false, false);
        verify(server, times(1)).recipeIterator();
    }

    @Test
    void chunkMigrationRefreshesPlayersWatchingTheChunk() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        Chunk chunk = mock(Chunk.class);
        Campfire campfire = legacyCampfire(Material.CAMPFIRE);
        Block block = mock(Block.class);
        Location location = mock(Location.class);
        Player observer = mock(Player.class);
        BlockData blockData = mock(BlockData.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.recipeIterator()).thenAnswer(ignored -> Collections.emptyIterator());
        when(chunk.getTileEntities()).thenReturn(new BlockState[] {campfire});
        when(chunk.getPlayersSeeingChunk()).thenReturn(List.of(observer));
        when(campfire.getBlock()).thenReturn(block);
        when(campfire.getBlockData()).thenReturn(blockData);
        when(block.getChunk()).thenReturn(chunk);
        when(block.getState()).thenReturn(campfire);
        when(block.getLocation()).thenReturn(location);
        ChunkLoadEvent event = mock(ChunkLoadEvent.class);
        when(event.getChunk()).thenReturn(chunk);
        CampfireListener listener = new CampfireListener(plugin);

        listener.onChunkLoad(event);

        verify(observer).sendBlockChange(location, blockData);
        verify(observer).sendBlockUpdate(location, campfire);
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {"CAMPFIRE", "SOUL_CAMPFIRE"})
    void containedItemsAreRestoredWhenEitherCampfireTypeIsPlaced(Material campfireType) {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.recipeIterator()).thenAnswer(ignored -> Collections.emptyIterator());
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenReturn(mock(BukkitTask.class));
        CampfireListener listener = new CampfireListener(plugin);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        ItemStack campfireItem = mock(ItemStack.class);
        when(campfireItem.getType()).thenReturn(campfireType);
        BlockStateMeta meta = mock(BlockStateMeta.class);
        when(campfireItem.getItemMeta()).thenReturn(meta);
        Campfire source = mock(Campfire.class);
        when(meta.getBlockState()).thenReturn(source);
        when(source.getType()).thenReturn(campfireType);
        when(source.getSize()).thenReturn(1);
        ItemStack sourceItem = mock(ItemStack.class);
        ItemStack capturedItem = mock(ItemStack.class);
        ItemStack restoredItem = mock(ItemStack.class);
        when(source.getItem(0)).thenReturn(sourceItem);
        when(sourceItem.clone()).thenReturn(capturedItem);
        when(capturedItem.clone()).thenReturn(restoredItem);
        when(source.getCookTime(0)).thenReturn(7);
        when(source.getCookTimeTotal(0)).thenReturn(Integer.MAX_VALUE);
        when(source.isCookingDisabled(0)).thenReturn(true);

        PlayerInteractEvent interact = mock(PlayerInteractEvent.class);
        when(interact.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(interact.getHand()).thenReturn(EquipmentSlot.HAND);
        when(interact.getPlayer()).thenReturn(player);
        when(interact.getItem()).thenReturn(campfireItem);
        listener.rememberCampfireBeforePlacement(interact);

        Block placedBlock = mock(Block.class);
        Campfire target = mock(Campfire.class);
        when(placedBlock.getState()).thenReturn(target);
        when(target.getSize()).thenReturn(1);
        when(target.update(false, false)).thenReturn(true);
        BlockData targetData = mock(BlockData.class);
        Location location = mock(Location.class);
        Chunk chunk = mock(Chunk.class);
        Player observer = mock(Player.class);
        when(target.getBlockData()).thenReturn(targetData);
        when(placedBlock.getLocation()).thenReturn(location);
        when(placedBlock.getChunk()).thenReturn(chunk);
        when(chunk.getPlayersSeeingChunk()).thenReturn(List.of(observer));
        BlockPlaceEvent place = mock(BlockPlaceEvent.class);
        when(place.getPlayer()).thenReturn(player);
        when(place.getBlockPlaced()).thenReturn(placedBlock);
        when(place.getItemInHand()).thenReturn(null);

        listener.onCampfirePlaced(place);
        ArgumentCaptor<Runnable> tasks = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, times(2)).runTask(eq(plugin), tasks.capture());
        tasks.getAllValues().get(1).run();

        verify(target).setItem(0, restoredItem);
        verify(target).setCookTime(0, 7);
        verify(target).setCookTimeTotal(0, Integer.MAX_VALUE);
        verify(target).stopCooking(0);
        verify(target).update(false, false);
        verify(observer).sendBlockUpdate(location, target);
    }

    private static Campfire legacyCampfire(Material type) {
        Campfire campfire = mock(Campfire.class);
        ItemStack item = mock(ItemStack.class);
        when(campfire.getType()).thenReturn(type);
        when(campfire.getSize()).thenReturn(1);
        when(campfire.getItem(0)).thenReturn(item);
        when(item.getType()).thenReturn(Material.PLAYER_HEAD);
        when(campfire.getCookTimeTotal(0)).thenReturn(600);
        when(campfire.isCookingDisabled(0)).thenReturn(false);
        when(campfire.update(false, false)).thenReturn(true);
        return campfire;
    }

    private static void verifyStabilized(Campfire campfire) {
        verify(campfire).setCookTimeTotal(0, Integer.MAX_VALUE);
        verify(campfire).stopCooking(0);
        verify(campfire).update(false, false);
    }

    private static final class InteractionFixture {
        private final Plugin plugin = mock(Plugin.class);
        private final Server server = mock(Server.class);
        private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        private final PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        private final Player player = mock(Player.class);
        private final Player observer = mock(Player.class);
        private final PlayerInventory inventory = mock(PlayerInventory.class);
        private final ItemStack mainHand = mock(ItemStack.class);
        private final Block block = mock(Block.class);
        private final Chunk chunk = mock(Chunk.class);
        private final Campfire campfire = mock(Campfire.class);
        private final ItemStack held = mock(ItemStack.class);
        private final ItemStack placed = mock(ItemStack.class);
        private final BlockData blockData = mock(BlockData.class);
        private final BlockData originalBlockData = mock(BlockData.class);
        private final Location location = mock(Location.class);
        private final CampfireListener listener;

        private InteractionFixture(Material campfireType, EquipmentSlot hand) {
            when(plugin.getServer()).thenReturn(server);
            when(server.getScheduler()).thenReturn(scheduler);
            when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenReturn(mock(BukkitTask.class));
            when(server.recipeIterator()).thenAnswer(ignored -> Collections.emptyIterator());
            listener = new CampfireListener(plugin);

            when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
            when(event.getHand()).thenReturn(hand);
            when(event.getBlockFace()).thenReturn(BlockFace.UP);
            when(event.getClickedBlock()).thenReturn(block);
            when(event.getItem()).thenReturn(held);
            when(event.getPlayer()).thenReturn(player);
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(player.getInventory()).thenReturn(inventory);
            when(inventory.getItemInMainHand()).thenReturn(mainHand);
            when(mainHand.getType()).thenReturn(Material.AIR);

            when(block.getState()).thenReturn(campfire);
            when(block.getLocation()).thenReturn(location);
            when(block.getChunk()).thenReturn(chunk);
            when(block.getType()).thenReturn(campfireType);
            when(block.getBlockData()).thenReturn(originalBlockData);
            when(chunk.getPlayersSeeingChunk()).thenReturn(List.of(player, observer));
            when(held.getType()).thenReturn(Material.STONE);
            when(held.clone()).thenReturn(placed);
            when(campfire.getSize()).thenReturn(4);
            when(campfire.getItem(0)).thenReturn(null);
            when(campfire.getBlockData()).thenReturn(blockData);
            when(blockData.clone()).thenReturn(originalBlockData);
            when(originalBlockData.getMaterial()).thenReturn(campfireType);
            when(campfire.update(false, false)).thenReturn(true);
        }

        private void runNextTick() {
            ArgumentCaptor<Runnable> nextTick = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTask(eq(plugin), nextTick.capture());
            nextTick.getValue().run();
        }
    }
}
