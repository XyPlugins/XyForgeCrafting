package org.xyplugin.xyforgecrafting.gui;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.xyplugin.xycore.api.XyCore;
import org.xyplugin.xyforgecrafting.XyForgeCraftingPlugin;
import org.xyplugin.xyforgecrafting.config.ForgeSettings;
import org.xyplugin.xyforgecrafting.config.GuiComponent;
import org.xyplugin.xyforgecrafting.config.GuiComponentType;
import org.xyplugin.xyforgecrafting.recipe.RecipeDefinition;
import org.xyplugin.xyforgecrafting.service.ForgeService;
import org.xyplugin.xyforgecrafting.service.PendingDeliveryStore;
import org.xyplugin.xyforgecrafting.util.Text;
import org.xyplugin.xyitems.api.ForgeOutcomeProfile;
import org.xyplugin.xyitems.api.XyItems;

/** Locked Bukkit inventory GUI with one explicitly managed real blueprint input. */
public final class ForgeGuiManager implements Listener {
    private final XyForgeCraftingPlugin plugin;
    private final ForgeService forge;
    private final PendingDeliveryStore pending;
    private final Map<UUID, ForgeSession> sessions = new HashMap<UUID, ForgeSession>();
    private final DecimalFormat percentFormat = new DecimalFormat("0.##",
            DecimalFormatSymbols.getInstance(Locale.US));

    public ForgeGuiManager(XyForgeCraftingPlugin plugin, ForgeService forge, PendingDeliveryStore pending) {
        this.plugin = plugin;
        this.forge = forge;
        this.pending = pending;
    }

    public void open(Player player) {
        closeSession(player, true, false);
        ForgeSettings settings = plugin.getSettings();
        UUID sessionId = UUID.randomUUID();
        ForgeHolder holder = new ForgeHolder(player.getUniqueId(), sessionId);
        Inventory inventory = Bukkit.createInventory(holder, settings.getSize(), settings.getTitle());
        holder.setInventory(inventory);
        ForgeSession session = new ForgeSession(player.getUniqueId(), sessionId, inventory);
        sessions.put(player.getUniqueId(), session);
        render(player, session);
        player.openInventory(inventory);
        plugin.send(player, "opened");
    }

    public void closeAll() {
        List<UUID> players = new ArrayList<UUID>(sessions.keySet());
        for (UUID playerId : players) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) closeSession(player, true, true);
            else {
                ForgeSession session = sessions.remove(playerId);
                if (session != null) {
                    session.cancelAnimation();
                    if (session.getBlueprint() != null) pending.queue(playerId, session.getBlueprint());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        ForgeHolder holder = holder(top);
        if (holder == null) return;
        if (!(event.getWhoClicked() instanceof Player)) {
            deny(event);
            return;
        }
        Player player = (Player) event.getWhoClicked();
        ForgeSession session = session(top);
        if (session == null || !player.getUniqueId().equals(session.getPlayerId())) {
            deny(event);
            closeInvalidViewNextTick(player, holder);
            return;
        }
        int topSize = session.getInventory().getSize();

        if (session.isBusy()) {
            deny(event);
            synchronizeNextTick(player, session);
            return;
        }

        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            deny(event);
            synchronizeNextTick(player, session);
            return;
        }

        if (event.getRawSlot() >= 0 && event.getRawSlot() < topSize) {
            deny(event);
            GuiComponentType type = typeAt(event.getRawSlot());
            boolean primaryClick = event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT;
            if (type == GuiComponentType.FORGE_BLUEPRINT) {
                if (event.getClick() == ClickType.NUMBER_KEY) placeFromHotbar(player, session, event.getHotbarButton());
                else if (primaryClick) handleBlueprintCursor(player, session, event.getCursor());
            } else if (type == GuiComponentType.FORGE_START && primaryClick) {
                startForge(player, session);
            } else if (type == GuiComponentType.CLOSE && primaryClick) {
                player.closeInventory();
            }
            synchronizeNextTick(player, session);
            return;
        }

        if (event.isShiftClick()) {
            deny(event);
            placeFromInventory(player, session, event.getCurrentItem(), event);
            synchronizeNextTick(player, session);
            return;
        }
        synchronizeNextTick(player, session);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        ForgeHolder holder = holder(top);
        if (holder == null) return;
        ForgeSession session = session(top);
        if (session == null || !event.getWhoClicked().getUniqueId().equals(session.getPlayerId())) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
            if (event.getWhoClicked() instanceof Player) {
                closeInvalidViewNextTick((Player) event.getWhoClicked(), holder);
            }
            return;
        }
        int size = session.getInventory().getSize();
        for (Integer rawSlot : event.getRawSlots()) {
            if (rawSlot < size) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
                int blueprintSlot = plugin.getSettings().getOnlySlot(GuiComponentType.FORGE_BLUEPRINT);
                if (rawSlot == blueprintSlot && event.getWhoClicked() instanceof Player
                        && !plugin.getBlueprints().identify(event.getOldCursor()).isPresent()) {
                    Player player = (Player) event.getWhoClicked();
                    plugin.send(player, plugin.getBlueprints().hasBlueprintIdentity(event.getOldCursor())
                            ? "blueprint-invalid" : "not-blueprint");
                }
                if (event.getWhoClicked() instanceof Player) {
                    synchronizeNextTick((Player) event.getWhoClicked(), session);
                }
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        ForgeSession session = session(event.getInventory());
        if (session == null) return;
        Player player = (Player) event.getPlayer();
        closeSession(player, true, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        closeSession(event.getPlayer(), true, false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (pending.deliver(event.getPlayer())) plugin.send(event.getPlayer(), "pending-delivered");
        });
    }

    private void handleBlueprintCursor(Player player, ForgeSession session, ItemStack cursor) {
        if (!usable(cursor)) {
            ItemStack blueprint = session.getBlueprint();
            if (blueprint == null) return;
            player.setItemOnCursor(blueprint);
            session.setBlueprint(null);
            session.setRecipe(null);
            render(player, session);
            return;
        }
        Optional<RecipeDefinition> recipe = plugin.getBlueprints().identify(cursor);
        if (!recipe.isPresent()) {
            plugin.send(player, plugin.getBlueprints().hasBlueprintIdentity(cursor) ? "blueprint-invalid" : "not-blueprint");
            return;
        }
        if (session.getBlueprint() != null) {
            plugin.send(player, "blueprint-occupied");
            return;
        }
        ItemStack placed = cursor.clone();
        placed.setAmount(1);
        if (cursor.getAmount() == 1) player.setItemOnCursor(null);
        else {
            ItemStack remaining = cursor.clone();
            remaining.setAmount(cursor.getAmount() - 1);
            player.setItemOnCursor(remaining);
        }
        session.setBlueprint(placed);
        session.setRecipe(recipe.get());
        render(player, session);
    }

    private void placeFromInventory(Player player, ForgeSession session, ItemStack clicked, InventoryClickEvent event) {
        if (!usable(clicked)) return;
        Optional<RecipeDefinition> recipe = plugin.getBlueprints().identify(clicked);
        if (!recipe.isPresent()) {
            plugin.send(player, plugin.getBlueprints().hasBlueprintIdentity(clicked) ? "blueprint-invalid" : "not-blueprint");
            return;
        }
        if (session.getBlueprint() != null) {
            plugin.send(player, "blueprint-occupied");
            return;
        }
        ItemStack placed = clicked.clone();
        placed.setAmount(1);
        if (clicked.getAmount() == 1) event.setCurrentItem(null);
        else {
            ItemStack reduced = clicked.clone();
            reduced.setAmount(clicked.getAmount() - 1);
            event.setCurrentItem(reduced);
        }
        session.setBlueprint(placed);
        session.setRecipe(recipe.get());
        render(player, session);
    }

    private void placeFromHotbar(Player player, ForgeSession session, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8) return;
        ItemStack item = player.getInventory().getItem(hotbarSlot);
        if (!usable(item)) return;
        Optional<RecipeDefinition> recipe = plugin.getBlueprints().identify(item);
        if (!recipe.isPresent()) {
            plugin.send(player, plugin.getBlueprints().hasBlueprintIdentity(item) ? "blueprint-invalid" : "not-blueprint");
            return;
        }
        if (session.getBlueprint() != null) {
            plugin.send(player, "blueprint-occupied");
            return;
        }
        ItemStack placed = item.clone();
        placed.setAmount(1);
        if (item.getAmount() == 1) player.getInventory().setItem(hotbarSlot, null);
        else {
            ItemStack reduced = item.clone();
            reduced.setAmount(item.getAmount() - 1);
            player.getInventory().setItem(hotbarSlot, reduced);
        }
        session.setBlueprint(placed);
        session.setRecipe(recipe.get());
        render(player, session);
    }

    private void startForge(Player player, ForgeSession session) {
        if (session.getRecipe() == null || session.getBlueprint() == null) {
            plugin.send(player, "blueprint-invalid");
            return;
        }
        ForgeService.Validation validation = forge.validate(player, session.getRecipe(), session.isDisplayOverflow());
        if (!validation.isValid()) {
            plugin.send(player, validation.getMessageKey());
            render(player, session);
            return;
        }
        session.setBusy(true);
        if (!plugin.getSettings().isAnimationEnabled()) {
            finishForge(player, session);
            return;
        }
        List<List<Integer>> frames = plugin.getSettings().getAnimationFrames();
        if (frames.isEmpty()) {
            finishForge(player, session);
            return;
        }
        int steps = frames.size() * plugin.getSettings().getAnimationLoops();
        BukkitTask task = new BukkitRunnable() {
            private int index;
            private List<Integer> previousHeads = Collections.emptyList();
            private final Set<Integer> touched = new LinkedHashSet<Integer>();

            @Override
            public void run() {
                if (sessions.get(player.getUniqueId()) != session || !player.isOnline()) {
                    session.setAnimationTask(null);
                    session.setBusy(false);
                    cancel();
                    return;
                }
                if (index >= steps) {
                    restoreAnimationSlots(session, touched);
                    session.setAnimationTask(null);
                    cancel();
                    finishForge(player, session);
                    return;
                }
                List<Integer> currentHeads = frames.get(index % frames.size());
                for (Integer previous : previousHeads) {
                    if (previous == null || currentHeads.contains(previous)) continue;
                    session.getInventory().setItem(previous, plugin.getSettings().getAnimationTrailDisplay().create());
                    touched.add(previous);
                }
                for (Integer current : currentHeads) {
                    if (current == null) continue;
                    session.getInventory().setItem(current, plugin.getSettings().getAnimationHeadDisplay().create());
                    touched.add(current);
                }
                previousHeads = new ArrayList<Integer>(currentHeads);
                index++;
            }
        }.runTaskTimer(plugin, 0L, plugin.getSettings().getAnimationInterval());
        session.setAnimationTask(task);
    }

    private void restoreAnimationSlots(ForgeSession session, Set<Integer> touched) {
        for (Integer slot : touched) {
            if (slot != null) restoreBaseSlot(session, slot);
        }
    }

    private void finishForge(Player player, ForgeSession session) {
        if (sessions.get(player.getUniqueId()) != session || session.getRecipe() == null) return;
        session.setBusy(false);
        session.setAnimationTask(null);
        ForgeService.Execution execution = forge.execute(player, session.getRecipe(), session.isDisplayOverflow());
        if (execution.shouldConsumeBlueprint()) {
            session.setBlueprint(null);
            session.setRecipe(null);
        }
        if (execution.getStatus() == ForgeService.Execution.Status.SUCCESS) {
            plugin.sendRaw(player, plugin.getSettings().message("forge-success")
                    .replace("%result_name%", execution.getResultName()));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1F, 1F);
        } else if (execution.getStatus() == ForgeService.Execution.Status.FAILURE) {
            plugin.sendRaw(player, execution.getMessage().isEmpty()
                    ? plugin.getSettings().message("forge-failure") : execution.getMessage());
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1F, 1F);
        } else {
            plugin.send(player, execution.getMessage());
        }
        render(player, session);
    }

    private void render(Player player, ForgeSession session) {
        ForgeSettings settings = plugin.getSettings();
        for (int slot = 0; slot < settings.getSize(); slot++) restoreBaseSlot(session, slot);

        int blueprintSlot = settings.getOnlySlot(GuiComponentType.FORGE_BLUEPRINT);
        if (session.getBlueprint() != null) session.getInventory().setItem(blueprintSlot, session.getBlueprint());
        RecipeDefinition recipe = session.getRecipe();
        session.setDisplayOverflow(false);
        if (recipe == null) return;

        List<Integer> requirementSlots = settings.getSlots(GuiComponentType.FORGE_REQUIREMENTS);
        int index = 0;
        for (Map.Entry<String, Long> requirement : recipe.getRequirements().entrySet()) {
            if (index >= requirementSlots.size()) {
                session.setDisplayOverflow(true);
                break;
            }
            ItemStack display = XyCore.get().getItems().create(requirement.getKey(), 1)
                    .orElse(new ItemStack(Material.BARRIER));
            long inventory = forge.inventoryCount(player, requirement.getKey());
            long soul = forge.soulCount(player, requirement.getKey());
            long total = inventory + soul;
            boolean enough = total >= requirement.getValue();
            appendLore(display,
                    "",
                    "&7物品ID: &f" + requirement.getKey(),
                    "&7背包: &f" + inventory + " &8| &7灵魂仓库: &f" + soul,
                    "&7拥有: &f" + total + "/" + requirement.getValue() + " " + (enough ? "&a√" : "&c×"));
            session.getInventory().setItem(requirementSlots.get(index++), display);
        }

        Optional<ForgeOutcomeProfile> profile = forge.profile(recipe);
        List<Integer> probabilitySlots = settings.getSlots(GuiComponentType.FORGE_PROBABILITY);
        double success = 0D;
        if (profile.isPresent()) {
            for (ForgeOutcomeProfile.Outcome outcome : profile.get().getOutcomes()) {
                if (!outcome.isFailure()) success += outcome.getProbability();
            }
            List<String> probabilityLore = probabilityLore(profile.get().getOutcomes());
            for (Integer probabilitySlot : probabilitySlots) {
                ItemStack display = session.getInventory().getItem(probabilitySlot);
                if (!usable(display)) continue;
                ItemMeta meta = display.getItemMeta();
                if (meta != null) {
                    meta.setLore(Text.colored(probabilityLore));
                    display.setItemMeta(meta);
                }
                session.getInventory().setItem(probabilitySlot, display);
            }
        }

        List<Integer> resultSlots = settings.getSlots(GuiComponentType.FORGE_RESULT);
        if (!resultSlots.isEmpty()) {
            ItemStack result = XyItems.get().createItem(recipe.getResult().getXyItemsId(), 1)
                    .orElse(new ItemStack(Material.BARRIER));
            appendLore(result, "", "&7成品数量: &f" + recipe.getResult().getAmount(),
                    "&7成功后立即确定结果和随机属性。");
            session.getInventory().setItem(resultSlots.get(0), result);
        }

        int startSlot = settings.getOnlySlot(GuiComponentType.FORGE_START);
        ItemStack start = session.getInventory().getItem(startSlot);
        appendLore(start, "", "&7锻造成功: &a" + percentFormat.format(success) + "%",
                "&7锻造失败: &c" + percentFormat.format(100D - success) + "%",
                "&7金币: &6" + percentFormat.format(recipe.getEconomy().getAmount()));
        ForgeService.Validation validation = forge.validate(player, recipe, session.isDisplayOverflow());
        appendLore(start, validation.isValid() ? "&a材料与金币满足，点击开始。" : "&c当前条件尚未满足。");
        session.getInventory().setItem(startSlot, start);
    }

    private void restoreBaseSlot(ForgeSession session, int slot) {
        ForgeSettings settings = plugin.getSettings();
        int row = slot / 9;
        int column = slot % 9;
        if (row >= settings.getLayout().size()) return;
        char key = settings.getLayout().get(row).charAt(column);
        GuiComponent component = settings.getComponent(key);
        ItemStack display = component == null ? null : component.getDisplay().create();
        if (component != null && component.getType() == GuiComponentType.FORGE_PROBABILITY) {
            sanitizeProbabilityPlaceholder(display);
        }
        session.getInventory().setItem(slot, display);
    }

    private GuiComponentType typeAt(int slot) {
        ForgeSettings settings = plugin.getSettings();
        char key = settings.getLayout().get(slot / 9).charAt(slot % 9);
        GuiComponent component = settings.getComponent(key);
        return component == null ? null : component.getType();
    }

    private ForgeSession session(Inventory inventory) {
        ForgeHolder holder = holder(inventory);
        if (holder == null) return null;
        ForgeSession session = sessions.get(holder.getOwner());
        return session != null && session.matches(holder) ? session : null;
    }

    private ForgeHolder holder(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof ForgeHolder
                ? (ForgeHolder) inventory.getHolder() : null;
    }

    private void deny(InventoryClickEvent event) {
        event.setCancelled(true);
        event.setResult(Event.Result.DENY);
    }

    private void synchronizeNextTick(Player player, ForgeSession session) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            ForgeSession current = sessions.get(player.getUniqueId());
            if (current != session || !player.isOnline()) return;
            Inventory top = player.getOpenInventory().getTopInventory();
            if (session(top) != session) return;
            if (!current.isBusy()) render(player, current);
            player.updateInventory();
        });
    }

    private void closeInvalidViewNextTick(Player player, ForgeHolder invalidHolder) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            ForgeHolder openHolder = holder(player.getOpenInventory().getTopInventory());
            if (openHolder == null || !openHolder.getSessionId().equals(invalidHolder.getSessionId())) return;
            player.closeInventory();
            plugin.sendRaw(player, "&c锻造会话已失效，请重新使用 /xyfc open。");
        });
    }

    private void closeSession(Player player, boolean returnBlueprint, boolean closeInventory) {
        ForgeSession session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        session.cancelAnimation();
        if (returnBlueprint && session.getBlueprint() != null) pending.returnOrQueue(player, session.getBlueprint());
        session.setBlueprint(null);
        session.setRecipe(null);
        if (closeInventory && player.getOpenInventory().getTopInventory() == session.getInventory()) player.closeInventory();
    }

    private void appendLore(ItemStack item, String... lines) {
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<String> lore = meta.hasLore() ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
        for (String line : lines) lore.add(Text.color(line));
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private void sanitizeProbabilityPlaceholder(ItemStack item) {
        if (!usable(item)) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return;
        meta.setLore(sanitizeProbabilityLore(meta.getLore()));
        item.setItemMeta(meta);
    }

    static List<String> sanitizeProbabilityLore(List<String> lore) {
        List<String> sanitized = new ArrayList<String>();
        if (lore == null) return sanitized;
        for (String line : lore) {
            if (line == null) continue;
            String cleaned = line.replace("失败与品质概率", "锻造概率").replace("品质", "");
            String plain = ChatColor.stripColor(cleaned).trim();
            if ("0.0".equals(plain)) continue;
            sanitized.add(cleaned);
        }
        return sanitized;
    }

    static List<String> probabilityLore(List<ForgeOutcomeProfile.Outcome> outcomes) {
        DecimalFormat formatter = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));
        List<String> lore = new ArrayList<String>();
        if (outcomes == null) return lore;
        for (ForgeOutcomeProfile.Outcome outcome : outcomes) {
            if (outcome == null || outcome.getProbability() <= 0D) continue;
            String label = outcome.isFailure() ? "失败几率" : outcome.getName();
            lore.add(outcome.getColor() + label + ": &f" + formatter.format(outcome.getProbability()) + "%");
        }
        return lore;
    }

    private boolean usable(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getAmount() > 0;
    }
}
