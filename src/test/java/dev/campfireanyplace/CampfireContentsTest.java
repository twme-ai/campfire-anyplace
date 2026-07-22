package dev.campfireanyplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.block.Campfire;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class CampfireContentsTest {
    @Test
    void firstEmptySlotUsesTheLowestAvailableIndex() {
        Campfire campfire = mock(Campfire.class);
        ItemStack occupied = mock(ItemStack.class);
        when(campfire.getSize()).thenReturn(4);
        when(campfire.getItem(0)).thenReturn(occupied);
        when(occupied.getType()).thenReturn(Material.STONE);
        when(campfire.getItem(1)).thenReturn(null);

        assertEquals(1, CampfireContents.firstEmptySlot(campfire));

        verify(campfire, never()).getItem(2);
        verify(campfire, never()).getItem(3);
    }

    @Test
    void firstEmptySlotRecognizesAirAndFullCampfires() {
        Campfire withAir = mock(Campfire.class);
        ItemStack air = mock(ItemStack.class);
        when(withAir.getSize()).thenReturn(4);
        when(withAir.getItem(0)).thenReturn(air);
        when(air.getType()).thenReturn(Material.AIR);
        assertEquals(0, CampfireContents.firstEmptySlot(withAir));

        Campfire full = mock(Campfire.class);
        ItemStack occupied = mock(ItemStack.class);
        when(full.getSize()).thenReturn(4);
        when(full.getItem(0)).thenReturn(occupied);
        when(full.getItem(1)).thenReturn(occupied);
        when(full.getItem(2)).thenReturn(occupied);
        when(full.getItem(3)).thenReturn(occupied);
        when(occupied.getType()).thenReturn(Material.STONE);
        assertEquals(-1, CampfireContents.firstEmptySlot(full));
    }

    @Test
    void lastOccupiedSlotUsesTheHighestNonEmptyIndex() {
        Campfire campfire = mock(Campfire.class);
        ItemStack air = mock(ItemStack.class);
        ItemStack occupied = mock(ItemStack.class);
        when(campfire.getSize()).thenReturn(4);
        when(campfire.getItem(3)).thenReturn(air);
        when(air.getType()).thenReturn(Material.AIR);
        when(campfire.getItem(2)).thenReturn(null);
        when(campfire.getItem(1)).thenReturn(occupied);
        when(occupied.getType()).thenReturn(Material.STONE);

        assertEquals(1, CampfireContents.lastOccupiedSlot(campfire));

        verify(campfire, never()).getItem(0);
    }

    @Test
    void lastOccupiedSlotReturnsMinusOneForAnEmptyCampfire() {
        Campfire campfire = mock(Campfire.class);
        when(campfire.getSize()).thenReturn(4);

        assertEquals(-1, CampfireContents.lastOccupiedSlot(campfire));
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
    void snapshotClonesAndRestoresItemsTimersAndCookingFlags() {
        Campfire source = mock(Campfire.class);
        ItemStack sourceItem = mock(ItemStack.class);
        ItemStack capturedItem = mock(ItemStack.class);
        ItemStack restoredItem = mock(ItemStack.class);
        when(source.getSize()).thenReturn(2);
        when(source.getItem(0)).thenReturn(sourceItem);
        when(sourceItem.clone()).thenReturn(capturedItem);
        when(capturedItem.clone()).thenReturn(restoredItem);
        when(source.getCookTime(0)).thenReturn(12);
        when(source.getCookTimeTotal(0)).thenReturn(80);
        when(source.isCookingDisabled(0)).thenReturn(true);
        when(source.getItem(1)).thenReturn(null);
        when(source.getCookTime(1)).thenReturn(3);
        when(source.getCookTimeTotal(1)).thenReturn(40);
        when(source.isCookingDisabled(1)).thenReturn(false);

        CampfireContents.Snapshot snapshot = CampfireContents.capture(source);
        Campfire target = mock(Campfire.class);
        when(target.getSize()).thenReturn(2);

        snapshot.applyTo(target);

        verify(target).setItem(0, restoredItem);
        verify(target).setCookTime(0, 12);
        verify(target).setCookTimeTotal(0, 80);
        verify(target).stopCooking(0);
        verify(target, never()).startCooking(0);
        verify(target).setItem(1, null);
        verify(target).setCookTime(1, 3);
        verify(target).setCookTimeTotal(1, 40);
        verify(target).startCooking(1);
        verify(target, never()).stopCooking(1);
    }
}
