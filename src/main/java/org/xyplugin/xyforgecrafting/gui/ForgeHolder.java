package org.xyplugin.xyforgecrafting.gui;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ForgeHolder implements InventoryHolder {
    private final UUID owner;
    private final UUID sessionId;
    private Inventory inventory;

    public ForgeHolder(UUID owner, UUID sessionId) {
        this.owner = owner;
        this.sessionId = sessionId;
    }

    public UUID getOwner() {
        return owner;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
