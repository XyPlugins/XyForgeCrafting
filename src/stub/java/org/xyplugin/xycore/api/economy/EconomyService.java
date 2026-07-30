package org.xyplugin.xycore.api.economy;

import org.bukkit.OfflinePlayer;

public interface EconomyService {
    boolean isAvailable();
    double getBalance(OfflinePlayer player);
    EconomyResult deposit(OfflinePlayer player, double amount, String reason);
    EconomyResult withdraw(OfflinePlayer player, double amount, String reason);
}
