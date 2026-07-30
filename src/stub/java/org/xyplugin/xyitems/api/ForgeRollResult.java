package org.xyplugin.xyitems.api;

import java.util.Optional;
import org.bukkit.inventory.ItemStack;

public final class ForgeRollResult {
    public boolean isSuccess() { return false; }
    public boolean isFailure() { return false; }
    public String getOutcomeId() { return ""; }
    public String getOutcomeName() { return ""; }
    public Optional<ItemStack> getItem() { return Optional.empty(); }
}
