package org.xyplugin.xyforgecrafting.integration;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.xyplugin.xyforgecrafting.XyForgeCraftingPlugin;

/** Optional reflection bridge keeps XyForgeCrafting loadable when XySoulSpace is absent. */
public final class SoulSpaceBridge {
    private final XyForgeCraftingPlugin plugin;
    private Object api;
    private Method countMethod;
    private Method withdrawMethod;
    private Method refundMethod;

    public SoulSpaceBridge(XyForgeCraftingPlugin plugin) {
        this.plugin = plugin;
        refresh();
    }

    public void refresh() {
        api = null;
        countMethod = null;
        withdrawMethod = null;
        refundMethod = null;
        Plugin soul = Bukkit.getPluginManager().getPlugin("XySoulSpace");
        if (soul == null || !soul.isEnabled()) return;
        try {
            ClassLoader loader = soul.getClass().getClassLoader();
            Class<?> entry = Class.forName("org.xyplugin.xysoulspace.api.XySoulSpace", true, loader);
            api = entry.getMethod("get").invoke(null);
            countMethod = api.getClass().getMethod("getAmountByItemId", UUID.class, String.class);
            withdrawMethod = api.getClass().getMethod("withdrawItems", UUID.class, Map.class);
            for (Method method : api.getClass().getMethods()) {
                if ("refund".equals(method.getName()) && method.getParameterTypes().length == 3) {
                    refundMethod = method;
                    break;
                }
            }
            if (refundMethod == null) throw new NoSuchMethodException("refund");
            plugin.getLogger().info("已接入XySoulSpace，材料默认优先从灵魂仓库扣除。");
        } catch (Exception failure) {
            api = null;
            plugin.getLogger().warning("XySoulSpace版本不支持锻造事务API，将只读取玩家背包: " + failure.getMessage());
        }
    }

    public boolean isAvailable() {
        return api != null;
    }

    public long count(UUID playerId, String itemId) {
        if (!isAvailable()) return 0L;
        try {
            Object value = countMethod.invoke(api, playerId, itemId);
            return value instanceof Number ? ((Number) value).longValue() : 0L;
        } catch (Exception failure) {
            plugin.getLogger().warning("读取灵魂仓库材料失败: " + failure.getMessage());
            return 0L;
        }
    }

    public Optional<Object> withdraw(UUID playerId, Map<String, Long> requirements) {
        if (!isAvailable() || requirements == null || requirements.isEmpty()) return Optional.empty();
        try {
            Object result = withdrawMethod.invoke(api, playerId, requirements);
            if (!(result instanceof Optional)) return Optional.empty();
            Optional<?> optional = (Optional<?>) result;
            return optional.isPresent() ? Optional.of(optional.get()) : Optional.empty();
        } catch (Exception failure) {
            plugin.getLogger().warning("扣除灵魂仓库材料失败: " + failure.getMessage());
            return Optional.empty();
        }
    }

    public long refund(UUID playerId, Object receipt, int percent) {
        if (!isAvailable() || receipt == null || percent <= 0) return 0L;
        try {
            Object value = refundMethod.invoke(api, playerId, receipt, percent);
            return value instanceof Number ? ((Number) value).longValue() : 0L;
        } catch (Exception failure) {
            plugin.getLogger().severe("灵魂仓库退款失败，需要人工核查玩家 " + playerId + ": " + failure.getMessage());
            return 0L;
        }
    }
}
