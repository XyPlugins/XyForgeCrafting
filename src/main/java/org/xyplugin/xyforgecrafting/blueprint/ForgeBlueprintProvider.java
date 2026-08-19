package org.xyplugin.xyforgecrafting.blueprint;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;
import org.xyplugin.xycore.api.item.ItemProvider;
import org.xyplugin.xyforgecrafting.XyForgeCraftingPlugin;
import org.xyplugin.xyforgecrafting.recipe.RecipeDefinition;

/** Exposes enabled forge blueprints through XyCore's unified item library. */
public final class ForgeBlueprintProvider implements ItemProvider {
    public static final String PROVIDER_ID = "xyforgecrafting";

    private final XyForgeCraftingPlugin plugin;
    private final BlueprintService blueprints;

    public ForgeBlueprintProvider(XyForgeCraftingPlugin plugin, BlueprintService blueprints) {
        this.plugin = plugin;
        this.blueprints = blueprints;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isAvailable() {
        return plugin.isEnabled();
    }

    @Override
    public Collection<String> getItemIds() {
        return isAvailable() ? plugin.getRecipeRegistry().getIds() : Collections.<String>emptyList();
    }

    @Override
    public Optional<ItemStack> createItem(String itemId, int amount) {
        if (!isAvailable()) return Optional.empty();
        Optional<RecipeDefinition> recipe = plugin.getRecipeRegistry().find(itemId);
        return recipe.isPresent() ? blueprints.create(recipe.get(), amount) : Optional.<ItemStack>empty();
    }

    @Override
    public Optional<String> identify(ItemStack item) {
        return isAvailable() ? blueprints.readBlueprintId(item) : Optional.<String>empty();
    }
}
