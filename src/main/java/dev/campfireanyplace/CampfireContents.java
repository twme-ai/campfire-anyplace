package dev.campfireanyplace;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.Campfire;
import org.bukkit.inventory.ItemStack;

final class CampfireContents {
    private CampfireContents() {
    }

    static int firstEmptySlot(Campfire campfire) {
        for (int slot = 0; slot < campfire.getSize(); slot++) {
            ItemStack item = campfire.getItem(slot);
            if (isEmpty(item)) {
                return slot;
            }
        }
        return -1;
    }

    static boolean isEmpty(ItemStack item) {
        Material type = item == null ? null : item.getType();
        if (type == null) {
            return true;
        }
        return type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR;
    }

    static ItemStack oneItem(ItemStack source) {
        ItemStack result = source.clone();
        result.setAmount(1);
        return result;
    }

    static Snapshot capture(Campfire campfire) {
        List<Slot> slots = new ArrayList<>(campfire.getSize());
        for (int i = 0; i < campfire.getSize(); i++) {
            ItemStack item = campfire.getItem(i);
            slots.add(new Slot(
                    item == null ? null : item.clone(),
                    campfire.getCookTime(i),
                    campfire.getCookTimeTotal(i),
                    campfire.isCookingDisabled(i)));
        }
        return new Snapshot(List.copyOf(slots));
    }

    record Slot(ItemStack item, int cookTime, int cookTimeTotal, boolean cookingDisabled) {
    }

    record Snapshot(List<Slot> slots) {
        void applyTo(Campfire campfire) {
            int count = Math.min(slots.size(), campfire.getSize());
            for (int i = 0; i < count; i++) {
                Slot slot = slots.get(i);
                campfire.setItem(i, slot.item() == null ? null : slot.item().clone());
                campfire.setCookTime(i, slot.cookTime());
                campfire.setCookTimeTotal(i, slot.cookTimeTotal());
                if (slot.cookingDisabled()) {
                    campfire.stopCooking(i);
                } else {
                    campfire.startCooking(i);
                }
            }
        }
    }
}
