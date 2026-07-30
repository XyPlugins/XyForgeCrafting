package org.xyplugin.xyforgecrafting.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.xyplugin.xyforgecrafting.XyForgeCraftingPlugin;

/** Persists overflow returns so closing a full inventory never destroys a blueprint or refund. */
public final class PendingDeliveryStore {
    private final XyForgeCraftingPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public PendingDeliveryStore(XyForgeCraftingPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pending-returns.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void queue(UUID playerId, ItemStack item) {
        if (playerId == null || item == null || item.getAmount() <= 0) return;
        List<ItemStack> queued = read(playerId);
        queued.add(item.clone());
        write(playerId, queued);
        save();
    }

    public synchronized boolean deliver(Player player) {
        List<ItemStack> queued = read(player.getUniqueId());
        if (queued.isEmpty()) return false;
        long queuedAmount = amountOf(queued);
        List<ItemStack> remaining = new ArrayList<ItemStack>();
        for (ItemStack item : queued) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
            remaining.addAll(leftovers.values());
        }
        write(player.getUniqueId(), remaining);
        save();
        player.updateInventory();
        return amountOf(remaining) < queuedAmount;
    }

    public void returnOrQueue(Player player, ItemStack item) {
        if (item == null || item.getAmount() <= 0) return;
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
        for (ItemStack leftover : leftovers.values()) queue(player.getUniqueId(), leftover);
        if (!leftovers.isEmpty()) plugin.send(player, "pending-return");
        player.updateInventory();
    }

    private List<ItemStack> read(UUID playerId) {
        List<?> raw = yaml.getList("players." + playerId.toString(), Collections.emptyList());
        List<ItemStack> items = new ArrayList<ItemStack>();
        for (Object value : raw) if (value instanceof ItemStack) items.add(((ItemStack) value).clone());
        return items;
    }

    private void write(UUID playerId, List<ItemStack> items) {
        yaml.set("players." + playerId.toString(), items.isEmpty() ? null : items);
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException failure) {
            plugin.getLogger().severe("无法保存pending-returns.yml: " + failure.getMessage());
        }
    }

    private long amountOf(List<ItemStack> items) {
        long amount = 0L;
        for (ItemStack item : items) amount += item == null ? 0L : Math.max(0, item.getAmount());
        return amount;
    }
}
