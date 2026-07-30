package org.xyplugin.xyforgecrafting.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.xyplugin.xycore.api.XyCore;
import org.xyplugin.xycore.api.economy.EconomyResult;
import org.xyplugin.xycore.api.economy.EconomyService;
import org.xyplugin.xycore.api.item.ItemLibraryService;
import org.xyplugin.xyforgecrafting.XyForgeCraftingPlugin;
import org.xyplugin.xyforgecrafting.integration.SoulSpaceBridge;
import org.xyplugin.xyforgecrafting.recipe.RecipeDefinition;
import org.xyplugin.xyforgecrafting.util.Text;
import org.xyplugin.xyitems.api.ForgeOutcomeProfile;
import org.xyplugin.xyitems.api.ForgeRollResult;
import org.xyplugin.xyitems.api.XyItems;
import org.xyplugin.xyitems.api.XyItemsApi;

/** Main-thread transactional forging pipeline. */
public final class ForgeService {
    private final XyForgeCraftingPlugin plugin;
    private final ItemLibraryService itemLibrary;
    private final EconomyService economy;
    private final XyItemsApi xyItems;
    private final SoulSpaceBridge soulSpace;
    private final InventoryMaterialService inventoryMaterials;
    private final PendingDeliveryStore pending;

    public ForgeService(XyForgeCraftingPlugin plugin, SoulSpaceBridge soulSpace, PendingDeliveryStore pending) {
        this.plugin = plugin;
        this.itemLibrary = XyCore.get().getItems();
        this.economy = XyCore.get().getEconomy();
        this.xyItems = XyItems.get();
        this.soulSpace = soulSpace;
        this.inventoryMaterials = new InventoryMaterialService(itemLibrary);
        this.pending = pending;
    }

    public Optional<ForgeOutcomeProfile> profile(RecipeDefinition recipe) {
        return recipe == null ? Optional.<ForgeOutcomeProfile>empty()
                : xyItems.getForgeOutcomeProfile(recipe.getResult().getXyItemsId());
    }

    public long inventoryCount(Player player, String itemId) {
        return inventoryMaterials.count(player, itemId);
    }

    public long soulCount(Player player, String itemId) {
        return soulSpace.count(player.getUniqueId(), itemId);
    }

    public long totalCount(Player player, String itemId) {
        long inventory = inventoryCount(player, itemId);
        long soul = soulCount(player, itemId);
        return Long.MAX_VALUE - inventory < soul ? Long.MAX_VALUE : inventory + soul;
    }

    public Validation validate(Player player, RecipeDefinition recipe, boolean displayOverflow) {
        if (player == null || recipe == null) return Validation.failure("blueprint-invalid");
        if (displayOverflow) return Validation.failure("display-overflow");
        Optional<ForgeOutcomeProfile> profile = profile(recipe);
        if (!profile.isPresent() || profile.get().getOutcomes().isEmpty() || profile.get().getTotalWeight() <= 0D) {
            return Validation.failure("probability-missing");
        }
        for (Map.Entry<String, Long> requirement : recipe.getRequirements().entrySet()) {
            if (totalCount(player, requirement.getKey()) < requirement.getValue()) {
                return Validation.failure("requirements-missing");
            }
        }
        double money = recipe.getEconomy().getAmount();
        if (money > 0D) {
            if (!economy.isAvailable()) return Validation.failure("economy-unavailable");
            if (economy.getBalance(player) < money) return Validation.failure("money-missing");
        }
        int resultSlots = resultSlots(recipe);
        if (resultSlots <= 0) return Validation.failure("probability-missing");
        if (!xyItems.hasDeliverySpace(player, resultSlots)) return Validation.failure("inventory-full");
        return Validation.success();
    }

    public Execution execute(Player player, RecipeDefinition recipe, boolean displayOverflow) {
        Validation validation = validate(player, recipe, displayOverflow);
        if (!validation.isValid()) return Execution.error(validation.getMessageKey());

        MaterialAllocation allocation = allocate(player, recipe);
        if (allocation == null) return Execution.error("requirements-missing");
        double charged = 0D;
        Object soulReceipt = null;
        InventoryMaterialService.Receipt inventoryReceipt = null;

        if (recipe.getEconomy().getAmount() > 0D) {
            EconomyResult result = economy.withdraw(player, recipe.getEconomy().getAmount(),
                    "XyForgeCrafting:" + recipe.getId());
            if (!result.isSuccess()) return Execution.error("transaction-failed");
            charged = recipe.getEconomy().getAmount();
        }

        if (!allocation.soul.isEmpty()) {
            Optional<Object> withdrawn = soulSpace.withdraw(player.getUniqueId(), allocation.soul);
            if (!withdrawn.isPresent()) {
                refundMoney(player, charged, 100, recipe);
                return Execution.error("transaction-failed");
            }
            soulReceipt = withdrawn.get();
        }

        try {
            Optional<InventoryMaterialService.Receipt> withdrawn = inventoryMaterials.withdraw(player, allocation.inventory);
            if (!withdrawn.isPresent()) {
                rollback(player, recipe, charged, soulReceipt, null);
                return Execution.error("transaction-failed");
            }
            inventoryReceipt = withdrawn.get();
        } catch (RuntimeException failure) {
            plugin.getLogger().severe("背包材料事务异常，玩家 " + player.getName() + ": " + failure.getMessage());
            rollback(player, recipe, charged, soulReceipt, inventoryReceipt);
            return Execution.error("transaction-failed");
        }

        ForgeRollResult roll = xyItems.rollForgeOutcome(recipe.getResult().getXyItemsId());
        if (!roll.isSuccess() && !roll.isFailure()) {
            rollback(player, recipe, charged, soulReceipt, inventoryReceipt);
            return Execution.error("transaction-failed");
        }

        if (roll.isFailure()) {
            int materialPercent = recipe.getFailure().getMaterialsRefundPercent();
            if (soulReceipt != null) soulSpace.refund(player.getUniqueId(), soulReceipt, materialPercent);
            inventoryMaterials.refund(player, inventoryReceipt, materialPercent, pending);
            refundMoney(player, charged, recipe.getFailure().getMoneyRefundPercent(), recipe);
            boolean consume = recipe.getFailure().getBlueprintPolicy()
                    == RecipeDefinition.Failure.BlueprintPolicy.DESTROY;
            return Execution.failure(consume, recipe.getFailure().getMessage());
        }

        Optional<ItemStack> rolledItem = roll.getItem();
        if (!rolledItem.isPresent()) {
            rollback(player, recipe, charged, soulReceipt, inventoryReceipt);
            return Execution.error("transaction-failed");
        }
        List<ItemStack> output = split(rolledItem.get(), recipe.getResult().getAmount());
        if (output.isEmpty() || !xyItems.deliverItems(player, output)) {
            rollback(player, recipe, charged, soulReceipt, inventoryReceipt);
            return Execution.error("transaction-failed");
        }
        player.updateInventory();
        String resultName = displayName(output.get(0));
        runCommands(player, recipe, resultName);
        return Execution.success(resultName);
    }

    private MaterialAllocation allocate(Player player, RecipeDefinition recipe) {
        Map<String, Long> soul = new LinkedHashMap<String, Long>();
        Map<String, Long> inventory = new LinkedHashMap<String, Long>();
        for (Map.Entry<String, Long> requirement : recipe.getRequirements().entrySet()) {
            long soulAvailable = soulCount(player, requirement.getKey());
            long soulAmount = Math.min(requirement.getValue(), soulAvailable);
            long inventoryAmount = requirement.getValue() - soulAmount;
            if (inventoryCount(player, requirement.getKey()) < inventoryAmount) return null;
            if (soulAmount > 0L) soul.put(requirement.getKey(), soulAmount);
            if (inventoryAmount > 0L) inventory.put(requirement.getKey(), inventoryAmount);
        }
        return new MaterialAllocation(soul, inventory);
    }

    private void rollback(Player player, RecipeDefinition recipe, double charged, Object soulReceipt,
                          InventoryMaterialService.Receipt inventoryReceipt) {
        if (soulReceipt != null) soulSpace.refund(player.getUniqueId(), soulReceipt, 100);
        if (inventoryReceipt != null) inventoryMaterials.refund(player, inventoryReceipt, 100, pending);
        refundMoney(player, charged, 100, recipe);
    }

    private void refundMoney(Player player, double charged, int percent, RecipeDefinition recipe) {
        if (charged <= 0D || percent <= 0) return;
        double refund = charged * Math.min(100, percent) / 100D;
        EconomyResult result = economy.deposit(player, refund, "XyForgeCrafting refund:" + recipe.getId());
        if (!result.isSuccess()) {
            plugin.getLogger().severe("金币退款失败，需要人工核查玩家 " + player.getName() + "，金额 " + refund);
        }
    }

    private int resultSlots(RecipeDefinition recipe) {
        Optional<ItemStack> base = xyItems.createItem(recipe.getResult().getXyItemsId(), 1);
        if (!base.isPresent()) return 0;
        int max = Math.max(1, base.get().getMaxStackSize());
        return (recipe.getResult().getAmount() + max - 1) / max;
    }

    private List<ItemStack> split(ItemStack prototype, int amount) {
        if (prototype == null || prototype.getType() == Material.AIR || amount <= 0) return Collections.emptyList();
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        int remaining = amount;
        int max = Math.max(1, prototype.getMaxStackSize());
        while (remaining > 0) {
            ItemStack stack = prototype.clone();
            stack.setAmount(Math.min(max, remaining));
            stacks.add(stack);
            remaining -= stack.getAmount();
        }
        return stacks;
    }

    private String displayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() ? meta.getDisplayName() : item.getType().name();
    }

    private void runCommands(Player player, RecipeDefinition recipe, String resultName) {
        String plain = ChatColor.stripColor(resultName);
        for (String configured : recipe.getSuccessCommands()) {
            String command = configured
                    .replace("%player_name%", player.getName())
                    .replace("%result_name%", resultName)
                    .replace("%result_name_plain%", plain == null ? "" : plain)
                    .replace("%recipe_id%", recipe.getId());
            if (command.toLowerCase().startsWith("console:")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.substring("console:".length()).trim());
            } else if (command.toLowerCase().startsWith("player:")) {
                player.performCommand(command.substring("player:".length()).trim());
            } else {
                plugin.getLogger().warning("忽略没有console:/player:前缀的成功命令: " + configured);
            }
        }
    }

    public static final class Validation {
        private final boolean valid;
        private final String messageKey;
        private Validation(boolean valid, String messageKey) { this.valid = valid; this.messageKey = messageKey; }
        public static Validation success() { return new Validation(true, ""); }
        public static Validation failure(String messageKey) { return new Validation(false, messageKey); }
        public boolean isValid() { return valid; }
        public String getMessageKey() { return messageKey; }
    }

    public static final class Execution {
        public enum Status { SUCCESS, FAILURE, ERROR }
        private final Status status;
        private final boolean consumeBlueprint;
        private final String message;
        private final String resultName;

        private Execution(Status status, boolean consumeBlueprint, String message, String resultName) {
            this.status = status;
            this.consumeBlueprint = consumeBlueprint;
            this.message = message;
            this.resultName = resultName;
        }

        public static Execution success(String resultName) { return new Execution(Status.SUCCESS, true, "", resultName); }
        public static Execution failure(boolean consume, String message) { return new Execution(Status.FAILURE, consume, message, ""); }
        public static Execution error(String messageKey) { return new Execution(Status.ERROR, false, messageKey, ""); }
        public Status getStatus() { return status; }
        public boolean shouldConsumeBlueprint() { return consumeBlueprint; }
        public String getMessage() { return message; }
        public String getResultName() { return resultName; }
    }

    private static final class MaterialAllocation {
        private final Map<String, Long> soul;
        private final Map<String, Long> inventory;
        private MaterialAllocation(Map<String, Long> soul, Map<String, Long> inventory) {
            this.soul = soul;
            this.inventory = inventory;
        }
    }
}
