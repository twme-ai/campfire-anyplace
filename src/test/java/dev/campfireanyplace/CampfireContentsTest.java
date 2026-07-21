package dev.campfireanyplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.Campfire;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class CampfireContentsTest {
    @Test
    void firstEmptySlotUsesTheLowestAvailableIndex() {
        Campfire campfire = mock(Campfire.class);
        ItemStack occupied = mock(ItemStack.class);
        Material occupiedType = mock(Material.class);
        when(campfire.getSize()).thenReturn(4);
        when(campfire.getItem(0)).thenReturn(occupied);
        when(occupied.getType()).thenReturn(occupiedType);
        when(occupiedType.isAir()).thenReturn(false);
        when(campfire.getItem(1)).thenReturn(null);

        assertEquals(1, CampfireContents.firstEmptySlot(campfire));

        verify(campfire, never()).getItem(2);
        verify(campfire, never()).getItem(3);
    }

    @Test
    void oneItemCopiesTheStackWithoutMutatingTheSource() {
        ItemStack source = mock(ItemStack.class);
        ItemStack copy = mock(ItemStack.class);
        when(source.clone()).thenReturn(copy);

        ItemStack result = CampfireContents.oneItem(source);

        assertEquals(copy, result);
        verify(copy).setAmount(1);
        verify(source, never()).setAmount(1);
    }

    @Test
    void snapshotRestoresTheCookingDisabledFlag() {
        ItemStack item = mock(ItemStack.class);
        ItemStack restoredItem = mock(ItemStack.class);
        when(item.clone()).thenReturn(restoredItem);
        Campfire target = mock(Campfire.class);
        when(target.getSize()).thenReturn(4);
        CampfireContents.Snapshot snapshot = new CampfireContents.Snapshot(List.of(
                new CampfireContents.Slot(item, 12, 80, true)));

        snapshot.applyTo(target);

        verify(target).setItem(0, restoredItem);
        verify(target).setCookTime(0, 12);
        verify(target).setCookTimeTotal(0, 80);
        verify(target).stopCooking(0);
        verify(target, never()).startCooking(0);
    }
}
