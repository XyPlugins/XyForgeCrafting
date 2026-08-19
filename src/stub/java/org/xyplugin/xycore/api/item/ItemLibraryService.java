package org.xyplugin.xycore.api.item;

import java.util.Optional;
import org.bukkit.inventory.ItemStack;

public interface ItemLibraryService {
    void registerProvider(ItemProvider provider);
    void unregisterProvider(String providerId);
    Optional<ItemProvider> getProvider(String providerId);
    java.util.Collection<ItemProvider> getProviders();
    Optional<ItemStack> create(String namespacedId, int amount);
    boolean matches(String namespacedId, ItemStack item);
    java.util.Collection<String> getItemIds(String providerId);
}
