package org.xyplugin.xyforgecrafting.gui;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.xyplugin.xyforgecrafting.recipe.RecipeDefinition;

final class ForgeSession {
    private final UUID playerId;
    private final UUID sessionId;
    private final Inventory inventory;
    private ItemStack blueprint;
    private RecipeDefinition recipe;
    private boolean busy;
    private boolean displayOverflow;
    private BukkitTask animationTask;

    ForgeSession(UUID playerId, UUID sessionId, Inventory inventory) {
        this.playerId = playerId;
        this.sessionId = sessionId;
        this.inventory = inventory;
    }

    UUID getPlayerId() { return playerId; }
    UUID getSessionId() { return sessionId; }
    boolean matches(ForgeHolder holder) {
        return holder != null && playerId.equals(holder.getOwner()) && sessionId.equals(holder.getSessionId());
    }
    Inventory getInventory() { return inventory; }
    ItemStack getBlueprint() { return blueprint == null ? null : blueprint.clone(); }
    void setBlueprint(ItemStack blueprint) { this.blueprint = blueprint == null ? null : blueprint.clone(); }
    RecipeDefinition getRecipe() { return recipe; }
    void setRecipe(RecipeDefinition recipe) { this.recipe = recipe; }
    boolean isBusy() { return busy; }
    void setBusy(boolean busy) { this.busy = busy; }
    boolean isDisplayOverflow() { return displayOverflow; }
    void setDisplayOverflow(boolean displayOverflow) { this.displayOverflow = displayOverflow; }
    BukkitTask getAnimationTask() { return animationTask; }
    void setAnimationTask(BukkitTask animationTask) { this.animationTask = animationTask; }

    void cancelAnimation() {
        if (animationTask != null) animationTask.cancel();
        animationTask = null;
        busy = false;
    }
}
