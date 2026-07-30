package org.xyplugin.xyforgecrafting.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.xyplugin.xycore.api.item.ItemLibraryService;

/** Counts and atomically removes main-inventory materials using XyCore's exact library matcher. */
public final class InventoryMaterialService {
    private final ItemLibraryService items;

    public InventoryMaterialService(ItemLibraryService items) {
        this.items = items;
    }

    public long count(Player player, String itemId) {
        long total = 0L;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (ItemStack item : contents) {
            if (usable(item) && items.matches(itemId, item)) total += item.getAmount();
        }
        return total;
    }

    public Optional<Receipt> withdraw(Player player, Map<String, Long> requirements) {
        if (requirements == null || requirements.isEmpty()) return Optional.of(new Receipt(new ArrayList<Entry>()));
        ItemStack[] contents = player.getInventory().getStorageContents();
        int[] available = new int[contents.length];
        for (int slot = 0; slot < contents.length; slot++) available[slot] = usable(contents[slot]) ? contents[slot].getAmount() : 0;
        List<Planned> plan = new ArrayList<Planned>();
        List<Entry> receipt = new ArrayList<Entry>();
        for (Map.Entry<String, Long> requirement : requirements.entrySet()) {
            long remaining = requirement.getValue();
            for (int slot = 0; slot < contents.length && remaining > 0L; slot++) {
                if (available[slot] <= 0 || !items.matches(requirement.getKey(), contents[slot])) continue;
                int used = (int) Math.min(remaining, available[slot]);
                plan.add(new Planned(slot, used));
                receipt.add(new Entry(contents[slot], used));
                available[slot] -= used;
                remaining -= used;
            }
            if (remaining > 0L) return Optional.empty();
        }
        for (Planned entry : plan) {
            ItemStack current = player.getInventory().getItem(entry.slot);
            if (!usable(current) || current.getAmount() < entry.amount) {
                throw new IllegalStateException("Inventory material plan became inconsistent");
            }
            if (current.getAmount() == entry.amount) player.getInventory().setItem(entry.slot, null);
            else {
                ItemStack reduced = current.clone();
                reduced.setAmount(current.getAmount() - entry.amount);
                player.getInventory().setItem(entry.slot, reduced);
            }
        }
        player.updateInventory();
        return Optional.of(new Receipt(receipt));
    }

    public void refund(Player player, Receipt receipt, int percent, PendingDeliveryStore pending) {
        if (receipt == null || percent <= 0) return;
        int safePercent = Math.min(100, percent);
        for (Entry entry : receipt.entries) {
            long amount = entry.amount * safePercent / 100L;
            while (amount > 0L) {
                ItemStack stack = entry.item.clone();
                int part = (int) Math.min(amount, stack.getMaxStackSize());
                stack.setAmount(part);
                pending.returnOrQueue(player, stack);
                amount -= part;
            }
        }
    }

    private boolean usable(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getAmount() > 0;
    }

    public static final class Receipt {
        private final List<Entry> entries;
        private Receipt(List<Entry> entries) { this.entries = entries; }
    }

    private static final class Entry {
        private final ItemStack item;
        private final long amount;
        private Entry(ItemStack item, long amount) {
            this.item = item.clone();
            this.item.setAmount(1);
            this.amount = amount;
        }
    }

    private static final class Planned {
        private final int slot;
        private final int amount;
        private Planned(int slot, int amount) { this.slot = slot; this.amount = amount; }
    }
}
