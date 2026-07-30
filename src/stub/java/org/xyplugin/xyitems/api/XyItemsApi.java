package org.xyplugin.xyitems.api;

import java.util.List;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface XyItemsApi {
    Optional<ItemStack> createItem(String itemId, int amount);
    Optional<ForgeOutcomeProfile> getForgeOutcomeProfile(String itemId);
    ForgeRollResult rollForgeOutcome(String itemId);
    boolean hasDeliverySpace(Player player, int requiredSlots);
    boolean deliverItems(Player player, List<ItemStack> output);
}
