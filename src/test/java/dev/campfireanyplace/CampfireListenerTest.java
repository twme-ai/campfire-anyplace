package dev.campfireanyplace;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Campfire;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CampfireListenerTest {
    @Test
    void successfulTopInteractionPlacesOneCopyAndConsumesOneHeldItem() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenReturn(mock(BukkitTask.class));
        when(server.recipeIterator()).thenReturn(Collections.emptyIterator());
        CampfireListener listener = new CampfireListener(plugin);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        Campfire campfire = mock(Campfire.class);
        ItemStack held = mock(ItemStack.class);
        ItemStack placed = mock(ItemStack.class);
        Material heldType = mock(Material.class);
        Material campfireType = mock(Material.class);
        BlockData blockData = mock(BlockData.class);
        BlockData originalBlockData = mock(BlockData.class);
        Location location = mock(Location.class);
        World world = mock(World.class);

        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getBlockFace()).thenReturn(BlockFace.UP);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getState()).thenReturn(campfire);
        when(block.getLocation()).thenReturn(location);
        when(block.getWorld()).thenReturn(world);
        when(block.getType()).thenReturn(campfireType);
        when(block.getBlockData()).thenReturn(originalBlockData);
        when(world.getPlayers()).thenReturn(List.of(player));
        when(event.getItem()).thenReturn(held);
        when(held.getType()).thenReturn(heldType);
        when(heldType.isAir()).thenReturn(false);
        when(held.clone()).thenReturn(placed);
        when(campfire.getSize()).thenReturn(4);
        when(campfire.getItem(0)).thenReturn(null);
        when(campfire.getBlockData()).thenReturn(blockData);
        when(blockData.clone()).thenReturn(originalBlockData);
        when(originalBlockData.getMaterial()).thenReturn(campfireType);
        when(campfire.update(false, false)).thenReturn(true);
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);

        listener.onCampfireInteract(event);

        verify(placed).setAmount(1);
        verify(campfire).setItem(0, placed);
        verify(campfire).setCookTime(0, 0);
        verify(campfire).setCookTimeTotal(0, Integer.MAX_VALUE);
        verify(campfire).stopCooking(0);
        verify(campfire).setBlockData(originalBlockData);
        verify(held).subtract(1);
        verify(event).setUseInteractedBlock(Event.Result.DENY);
        verify(event).setUseItemInHand(Event.Result.DENY);
        verify(event).setCancelled(true);

        ArgumentCaptor<Runnable> nextTick = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), nextTick.capture());
        nextTick.getValue().run();

        verify(player, times(2)).sendBlockChange(location, blockData);
        verify(player, times(2)).sendBlockUpdate(location, campfire);
        verify(player).updateInventory();
    }
}
