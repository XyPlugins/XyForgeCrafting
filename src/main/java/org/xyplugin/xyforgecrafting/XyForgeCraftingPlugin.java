package org.xyplugin.xyforgecrafting;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.xyplugin.xycore.api.XyCore;
import org.xyplugin.xycore.api.item.ItemLibraryService;
import org.xyplugin.xycore.api.item.ItemProvider;
import org.xyplugin.xycore.api.service.Reloadable;
import org.xyplugin.xyforgecrafting.blueprint.BlueprintService;
import org.xyplugin.xyforgecrafting.blueprint.ForgeBlueprintProvider;
import org.xyplugin.xyforgecrafting.command.XyForgeCommand;
import org.xyplugin.xyforgecrafting.config.ForgeSettings;
import org.xyplugin.xyforgecrafting.gui.ForgeGuiManager;
import org.xyplugin.xyforgecrafting.integration.SoulSpaceBridge;
import org.xyplugin.xyforgecrafting.recipe.RecipeDefinition;
import org.xyplugin.xyforgecrafting.recipe.RecipeRegistry;
import org.xyplugin.xyforgecrafting.service.ForgeService;
import org.xyplugin.xyforgecrafting.service.PendingDeliveryStore;
import org.xyplugin.xyforgecrafting.util.Text;
import org.xyplugin.xyitems.api.XyItems;
import org.xyplugin.xyitems.api.XyItemsApi;

/** XyForgeCrafting, intentionally targeting only Paper/Spigot 1.12.2. */
public final class XyForgeCraftingPlugin extends JavaPlugin implements Reloadable {
    private static final String DEFAULT_LOCAL_PREFIX = "&7[&6XyForgeCrafting&7]&r ";

    private ForgeSettings settings;
    private RecipeRegistry recipes = RecipeRegistry.empty();
    private BlueprintService blueprints;
    private ForgeBlueprintProvider blueprintProvider;
    private SoulSpaceBridge soulSpace;
    private PendingDeliveryStore pending;
    private ForgeService forge;
    private ForgeGuiManager gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureRecipeExample();
        getLogger().info("检测依赖: XyCore " + dependencyVersion("XyCore")
                + ", XyItems " + dependencyVersion("XyItems") + "。");
        if (!verifyDependencyApis()) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (!loadSnapshots(false)) {
            getLogger().severe("XyForgeCrafting启动失败：配置无效。");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        try {
            blueprints = new BlueprintService(this);
            blueprintProvider = new ForgeBlueprintProvider(this, blueprints);
            XyCore.get().getItems().registerProvider(blueprintProvider);
        } catch (Exception failure) {
            getLogger().severe("图纸身份服务启动失败: " + failure.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        pending = new PendingDeliveryStore(this);
        soulSpace = new SoulSpaceBridge(this);
        forge = new ForgeService(this, soulSpace, pending);
        gui = new ForgeGuiManager(this, forge, pending);
        Bukkit.getPluginManager().registerEvents(gui, this);
        XyForgeCommand command = new XyForgeCommand(this);
        if (getCommand("xyfc") != null) {
            getCommand("xyfc").setExecutor(command);
            getCommand("xyfc").setTabCompleter(command);
        }
        XyCore.get().getReloads().register(this);
        getLogger().info("XyForgeCrafting " + getDescription().getVersion()
                + " 已启用，仅支持Paper/Spigot 1.12.2。已加载配方: " + recipes.size());
    }

    @Override
    public void onDisable() {
        if (blueprintProvider != null) {
            try {
                XyCore.get().getItems().unregisterProvider(blueprintProvider.getId());
            } catch (LinkageError | RuntimeException ignored) {
            }
            blueprintProvider = null;
        }
        try {
            XyCore.get().getReloads().unregister(getId());
        } catch (RuntimeException ignored) {
        }
        if (gui != null) gui.closeAll();
    }

    public boolean reloadAll() {
        if (!reloadLinkedXyItems()) return false;
        if (!loadSnapshots(true)) return false;
        if (gui != null) gui.closeAll();
        if (soulSpace != null) soulSpace.refresh();
        return true;
    }

    private boolean loadSnapshots(boolean keepExisting) {
        try {
            ForgeSettings candidateSettings = ForgeSettings.load(new File(getDataFolder(), "config.yml"));
            RecipeRegistry.LoadResult candidateRecipes = RecipeRegistry.load(
                    new File(getDataFolder(), "ForgeRecipe"), getLogger());
            if (!candidateRecipes.isSuccess()) return false;
            if (!verifyRecipeReferences(candidateRecipes.getRegistry())) return false;
            settings = candidateSettings;
            recipes = candidateRecipes.getRegistry();
            return true;
        } catch (Exception failure) {
            getLogger().warning("[XyForgeCrafting] 配置加载失败: " + describeFailure(failure));
            return false;
        }
    }

    private void ensureRecipeExample() {
        File example = new File(getDataFolder(), "ForgeRecipe/Example.yml");
        if (!example.exists()) saveResource("ForgeRecipe/Example.yml", false);
    }

    private boolean verifyDependencyApis() {
        try {
            ItemLibraryService coreItems = XyCore.get().getItems();
            coreItems.getClass().getMethod("matches", String.class, ItemStack.class);
            coreItems.getClass().getMethod("registerProvider", ItemProvider.class);
            coreItems.getClass().getMethod("unregisterProvider", String.class);
            XyCore.get().getClass().getMethod("getMessagePrefix");
            XyItemsApi itemApi = XyItems.get();
            itemApi.getClass().getMethod("getForgeOutcomeProfile", String.class);
            itemApi.getClass().getMethod("rollForgeOutcome", String.class);
            itemApi.getClass().getMethod("deliverItems", Player.class, java.util.List.class);
            return true;
        } catch (Throwable failure) {
            getLogger().severe("依赖API版本不兼容。建议使用XyCore 0.3.12和XyItems 1.0.7: "
                    + describeFailure(failure));
            return false;
        }
    }

    private boolean verifyRecipeReferences(RecipeRegistry registry) {
        for (String recipeId : registry.getIds()) {
            RecipeDefinition recipe = registry.find(recipeId).get();
            if (!XyCore.get().getItems().create(recipe.getBlueprint().getTemplate(), 1).isPresent()) {
                getLogger().warning("配方 " + recipeId + " 的图纸基础物品不存在: "
                        + recipe.getBlueprint().getTemplate());
                return false;
            }
            for (String itemId : recipe.getRequirements().keySet()) {
                if (!XyCore.get().getItems().create(itemId, 1).isPresent()) {
                    getLogger().warning("配方 " + recipeId + " 的材料物品不存在: " + itemId);
                    return false;
                }
            }
            String resultId = recipe.getResult().getXyItemsId();
            if (!XyItems.get().getForgeOutcomeProfile(resultId).isPresent()) {
                getLogger().warning("配方 " + recipeId + " 的XyItems成品不存在，或没有可用于锻造抽取的配置: "
                        + recipe.getResult().getItem());
                getLogger().warning("请检查 XyItems/items/**/*.yml 中的 items." + resultId
                        + "，并确认同时存在 forge.failure.weight（允许填写0）和"
                        + " identify.enabled: true / identify.qualities。");
                return false;
            }
        }
        return true;
    }

    private boolean reloadLinkedXyItems() {
        Plugin xyItems = Bukkit.getPluginManager().getPlugin("XyItems");
        if (xyItems == null || !xyItems.isEnabled()) {
            getLogger().warning("XyItems未启用，无法同步重载锻造配置。");
            return false;
        }
        try {
            Method reload = xyItems.getClass().getMethod("reloadItemDefinitions");
            Object result = reload.invoke(xyItems);
            if (result instanceof Boolean && !((Boolean) result)) {
                getLogger().warning("XyItems重载失败，已保留当前锻造配置。");
                return false;
            }
            return true;
        } catch (Throwable failure) {
            getLogger().warning("XyItems同步重载失败: " + failure.getMessage());
            return false;
        }
    }

    private String dependencyVersion(String pluginName) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        return plugin == null ? "未安装" : plugin.getDescription().getVersion();
    }

    private String describeFailure(Throwable failure) {
        if (failure == null) return "未知异常";
        String message = failure.getMessage();
        if (message != null && !message.trim().isEmpty()) {
            return failure.getClass().getSimpleName() + ": " + message;
        }
        return failure.getClass().getSimpleName();
    }

    public void send(CommandSender sender, String messageKey) {
        sendRaw(sender, settings == null ? "" : settings.message(messageKey));
    }

    public void sendRaw(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) return;
        if (sender instanceof Player) {
            sendPlayerRaw((Player) sender, message);
        } else {
            sendLocalRaw(sender, message);
        }
    }

    public void sendPlayer(Player player, String messageKey) {
        sendPlayerRaw(player, settings == null ? "" : settings.message(messageKey));
    }

    public void sendPlayerRaw(Player player, String message) {
        if (player == null || message == null || message.isEmpty()) return;
        player.sendMessage(Text.color(XyCore.get().getMessagePrefix() + message));
    }

    public void sendLocal(CommandSender sender, String messageKey) {
        sendLocalRaw(sender, settings == null ? "" : settings.message(messageKey));
    }

    public void sendLocalRaw(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) return;
        sender.sendMessage(Text.color(localPrefix() + message));
    }

    private String localPrefix() {
        String configured = settings == null ? null : settings.message("prefix");
        return configured == null || configured.trim().isEmpty() ? DEFAULT_LOCAL_PREFIX : configured;
    }

    @Override
    public String getId() {
        return "xyforgecrafting";
    }

    @Override
    public void reload() throws Exception {
        if (!reloadAll()) throw new IllegalStateException("XyForgeCrafting配置无效，已保留当前配置。");
    }

    public ForgeSettings getSettings() { return settings; }
    public RecipeRegistry getRecipeRegistry() { return recipes; }
    public BlueprintService getBlueprints() { return blueprints; }
    public PendingDeliveryStore getPending() { return pending; }
    public ForgeGuiManager getGui() { return gui; }
}
