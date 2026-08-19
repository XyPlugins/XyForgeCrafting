package org.xyplugin.xyforgecrafting.blueprint;

import java.util.Optional;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.xyplugin.xycore.api.XyCore;
import org.xyplugin.xycore.api.item.ItemLibraryService;
import org.xyplugin.xycore.api.item.ItemTagService;
import org.xyplugin.xyforgecrafting.XyForgeCraftingPlugin;
import org.xyplugin.xyforgecrafting.recipe.RecipeDefinition;
import org.xyplugin.xyforgecrafting.util.Text;

/** Generates and verifies fixed blueprint NBT identity without trusting visible name or lore. */
public final class BlueprintService {
    public static final String ID_TAG = "xyforge-blueprint-id";
    public static final String SCHEMA_TAG = "xyforge-blueprint-schema";
    private static final String SCHEMA = "1";

    private final XyForgeCraftingPlugin plugin;
    private final ItemLibraryService items;
    private final ItemTagService tags;

    public BlueprintService(XyForgeCraftingPlugin plugin) {
        this.plugin = plugin;
        this.items = XyCore.get().getItems();
        this.tags = XyCore.get().getItemTags();
        if (!tags.isAvailable()) throw new IllegalStateException("XyCore物品NBT服务不可用。");
    }

    public Optional<ItemStack> create(RecipeDefinition recipe, int amount) {
        if (recipe == null || amount <= 0 || amount > 64) return Optional.empty();
        String template = recipe.getBlueprint().getTemplate();
        if (isForgeBlueprintId(template)) return Optional.empty();
        Optional<ItemStack> base = items.create(template, amount);
        if (!base.isPresent()) return Optional.empty();
        ItemStack blueprint = base.get();
        ItemMeta meta = blueprint.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Text.color(recipe.getBlueprint().getDisplayName()));
            meta.setLore(Text.colored(recipe.getBlueprint().getLore()));
            blueprint.setItemMeta(meta);
        }
        blueprint = tags.setString(blueprint, ID_TAG, recipe.getId());
        blueprint = tags.setString(blueprint, SCHEMA_TAG, SCHEMA);
        blueprint.setAmount(amount);
        return Optional.of(blueprint);
    }

    public Optional<RecipeDefinition> identify(ItemStack item) {
        Optional<String> id = readBlueprintId(item);
        return id.isPresent() ? plugin.getRecipeRegistry().find(id.get()) : Optional.empty();
    }

    public Optional<String> readBlueprintId(ItemStack item) {
        if (item == null || item.getAmount() <= 0) return Optional.empty();
        String id = tags.getString(item, ID_TAG).orElse("");
        String schema = tags.getString(item, SCHEMA_TAG).orElse("");
        if (id.trim().isEmpty() || !SCHEMA.equals(schema)) return Optional.empty();
        return Optional.of(id);
    }

    public boolean hasBlueprintIdentity(ItemStack item) {
        return item != null && tags.getString(item, ID_TAG).isPresent();
    }

    private boolean isForgeBlueprintId(String itemId) {
        if (itemId == null) return false;
        int separator = itemId.indexOf(':');
        return separator > 0 && "xyforgecrafting".equalsIgnoreCase(itemId.substring(0, separator).trim());
    }
}
