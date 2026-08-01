package org.xyplugin.xyforgecrafting;

import java.io.File;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.xyplugin.xycore.api.XyCore;
import org.xyplugin.xycore.api.service.Reloadable;
import org.xyplugin.xyforgecrafting.blueprint.BlueprintService;
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

/** XyForgeCrafting, intentionally targeting only Paper/Spigot 1.12.2. */
public final class XyForgeCraftingPlugin extends JavaPlugin implements Reloadable {
    private ForgeSettings settings;
    private RecipeRegistry recipes = RecipeRegistry.empty();
    private BlueprintService blueprints;
    private SoulSpaceBridge soulSpace;
    private PendingDeliveryStore pending;
    private ForgeService forge;
    private ForgeGuiManager gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureRecipeExample();
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
        try {
            XyCore.get().getReloads().unregister(getId());
        } catch (RuntimeException ignored) {
        }
        if (gui != null) gui.closeAll();
    }

    public boolean reloadAll() {
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
            getLogger().warning("[XyForgeCrafting] 配置加载失败: " + failure.getMessage());
            return false;
        }
    }

    private void ensureRecipeExample() {
        File example = new File(getDataFolder(), "ForgeRecipe/Example.yml");
        if (!example.exists()) saveResource("ForgeRecipe/Example.yml", false);
    }

    private boolean verifyDependencyApis() {
        try {
            Object coreItems = XyCore.get().getItems();
            coreItems.getClass().getMethod("matches", String.class, ItemStack.class);
            Object itemApi = XyItems.get();
            itemApi.getClass().getMethod("getForgeOutcomeProfile", String.class);
            itemApi.getClass().getMethod("rollForgeOutcome", String.class);
            itemApi.getClass().getMethod("deliverItems", Player.class, java.util.List.class);
            return true;
        } catch (Throwable failure) {
            getLogger().severe("依赖API版本不兼容。需要XyCore 0.3.10和XyItems 1.0.4: " + failure.getMessage());
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
            if (!XyItems.get().getForgeOutcomeProfile(recipe.getResult().getXyItemsId()).isPresent()) {
                getLogger().warning("配方 " + recipeId + " 的XyItems成品不存在或没有forge.failure/品质: "
                        + recipe.getResult().getItem());
                return false;
            }
        }
        return true;
    }

    public void send(CommandSender sender, String messageKey) {
        sendRaw(sender, settings == null ? "" : settings.message(messageKey));
    }

    public void sendRaw(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) return;
        String prefix = settings == null ? "" : settings.message("prefix");
        sender.sendMessage(Text.color(prefix + message));
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
