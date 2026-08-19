package org.xyplugin.xycore.api.item;

import java.util.Collection;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

/** 某个物品库的统一提供器，例如 minecraft、mythicmobs 或锻造图纸。 */
public interface ItemProvider {
    String getId();
    boolean isAvailable();
    Collection<String> getItemIds();
    Optional<ItemStack> createItem(String itemId, int amount);

    default Optional<String> identify(ItemStack item) {
        return Optional.empty();
    }

    default boolean matches(String itemId, ItemStack item) {
        if (itemId == null || itemId.trim().isEmpty() || item == null) return false;
        Optional<String> identified = identify(item);
        return identified.isPresent() && itemId.trim().equalsIgnoreCase(identified.get());
    }
}
