package org.xyplugin.xycore.api.item;

import java.util.Optional;
import org.bukkit.inventory.ItemStack;

public interface ItemLibraryService {
    Optional<ItemStack> create(String namespacedId, int amount);
    boolean matches(String namespacedId, ItemStack item);
}
