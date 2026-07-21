package dev.campfireanyplace;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Campfire;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class CampfireListenerTest {
    @Test
    void successfulTopInteractionPlacesOneCopyAndConsumesOneHeldItem() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.recipeIterator()).thenReturn(Collections.emptyIterator());
        CampfireListener listener = new CampfireListener(plugin);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        Player player = mock(Player.class);
        Block block = mock(Block.class);
        Campfire campfire = mock(Campfire.class);
        ItemStack held = mock(ItemStack.class);
        ItemStack placed = mock(ItemStack.class);
        Material heldType = mock(Material.class);

        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getBlockFace()).thenReturn(BlockFace.UP);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getState()).thenReturn(campfire);
        when(event.getItem()).thenReturn(held);
        when(held.getType()).thenReturn(heldType);
        when(heldType.isAir()).thenReturn(false);
        when(held.clone()).thenReturn(placed);
        when(campfire.getSize()).thenReturn(4);
        when(campfire.getItem(0)).thenReturn(null);
        when(campfire.update(true, false)).thenReturn(true);
        when(event.getPlayer()).thenReturn(player);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);

        listener.onCampfireInteract(event);

        verify(placed).setAmount(1);
        verify(campfire).setItem(0, placed);
        verify(campfire).setCookTime(0, 0);
        verify(campfire).setCookTimeTotal(0, 600);
        verify(held).subtract(1);
        verify(event).setCancelled(true);
    }
}
