package org.xyplugin.xyforgecrafting.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.xyplugin.xyforgecrafting.XyForgeCraftingPlugin;
import org.xyplugin.xyforgecrafting.recipe.RecipeDefinition;

public final class XyForgeCommand implements CommandExecutor, TabCompleter {
    private static final int MAX_BLUEPRINT_AMOUNT = 64;
    private final XyForgeCraftingPlugin plugin;

    public XyForgeCommand(XyForgeCraftingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if ("open".equals(sub)) return open(sender);
        if ("get".equals(sub)) return get(sender, args);
        if ("give".equals(sub)) return give(sender, args);
        if ("list".equals(sub)) return list(sender);
        if ("reload".equals(sub)) return reload(sender);
        help(sender);
        return true;
    }

    private boolean open(CommandSender sender) {
        if (!(sender instanceof Player)) {
            plugin.send(sender, "player-only");
            return true;
        }
        if (!sender.hasPermission("xyforgecrafting.use")) {
            plugin.send(sender, "no-permission");
            return true;
        }
        plugin.getGui().open((Player) sender);
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("xyforgecrafting.give")) {
            plugin.send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            plugin.sendRaw(sender, "&c用法: /xyfc give <玩家> <配方ID> [数量]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.sendRaw(sender, "&c玩家不在线: " + args[1]);
            return true;
        }
        return deliverBlueprint(sender, target, args[2], args.length >= 4 ? args[3] : null, true);
    }

    private boolean get(CommandSender sender, String[] args) {
        if (!sender.hasPermission("xyforgecrafting.get")) {
            plugin.send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player)) {
            plugin.send(sender, "player-only");
            return true;
        }
        if (args.length < 2) {
            plugin.sendRaw(sender, "&c用法: /xyfc get <配方ID> [数量]");
            return true;
        }
        return deliverBlueprint(sender, (Player) sender, args[1], args.length >= 3 ? args[2] : null, false);
    }

    private boolean deliverBlueprint(CommandSender sender, Player target, String recipeId,
                                     String amountText, boolean administrativeGive) {
        Optional<RecipeDefinition> recipe = plugin.getRecipeRegistry().find(recipeId);
        if (!recipe.isPresent()) {
            plugin.sendRaw(sender, "&c不存在或未启用的配方: " + recipeId);
            return true;
        }
        int amount = 1;
        if (amountText != null) {
            try {
                amount = Integer.parseInt(amountText);
            } catch (NumberFormatException failure) {
                amount = 0;
            }
        }
        if (amount <= 0 || amount > MAX_BLUEPRINT_AMOUNT) {
            plugin.sendRaw(sender, "&c数量必须是1到64的整数。");
            return true;
        }
        Optional<ItemStack> blueprint = plugin.getBlueprints().create(recipe.get(), 1);
        if (!blueprint.isPresent()) {
            plugin.sendRaw(sender, "&c图纸基础物品无法生成，请检查blueprint.material。");
            return true;
        }
        int remaining = amount;
        int maxStack = Math.max(1, blueprint.get().getMaxStackSize());
        while (remaining > 0) {
            ItemStack stack = blueprint.get().clone();
            stack.setAmount(Math.min(maxStack, remaining));
            plugin.getPending().returnOrQueue(target, stack);
            remaining -= stack.getAmount();
        }
        if (administrativeGive) {
            plugin.sendRaw(sender, "&a已给予 " + target.getName() + " " + amount + " 张 "
                    + recipe.get().getBlueprint().getDisplayName() + "&a。");
        }
        if (!administrativeGive || sender != target) {
            plugin.sendRaw(target, "&a获得了 " + amount + " 张 "
                    + recipe.get().getBlueprint().getDisplayName() + "&a。");
        }
        return true;
    }

    private boolean list(CommandSender sender) {
        if (!sender.hasPermission("xyforgecrafting.list")) {
            plugin.send(sender, "no-permission");
            return true;
        }
        plugin.sendRaw(sender, "&6已加载锻造配方 (&f" + plugin.getRecipeRegistry().size() + "&6): &f"
                + String.join(", ", plugin.getRecipeRegistry().getIds()));
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("xyforgecrafting.reload")) {
            plugin.send(sender, "no-permission");
            return true;
        }
        plugin.send(sender, plugin.reloadAll() ? "reload-success" : "reload-failed");
        return true;
    }

    private void help(CommandSender sender) {
        plugin.sendRaw(sender, "&6=== XyForgeCrafting " + plugin.getDescription().getVersion() + " ===");
        plugin.sendRaw(sender, "&e/xyfc open &7- 打开锻造台");
        if (sender instanceof Player && sender.hasPermission("xyforgecrafting.get")) {
            plugin.sendRaw(sender, "&e/xyfc get <配方ID> [数量] &7- 获得锻造图纸");
        }
        if (sender.hasPermission("xyforgecrafting.list")) {
            plugin.sendRaw(sender, "&e/xyfc list &7- 查看配方");
        }
        if (sender.hasPermission("xyforgecrafting.give")) {
            plugin.sendRaw(sender, "&e/xyfc give <玩家> <配方ID> [数量] &7- 给予锻造图纸");
        }
        if (sender.hasPermission("xyforgecrafting.reload")) {
            plugin.sendRaw(sender, "&e/xyfc reload &7- 安全重载");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<String>();
            if (sender.hasPermission("xyforgecrafting.use")) subcommands.add("open");
            if (sender instanceof Player && sender.hasPermission("xyforgecrafting.get")) subcommands.add("get");
            if (sender.hasPermission("xyforgecrafting.give")) subcommands.add("give");
            if (sender.hasPermission("xyforgecrafting.list")) subcommands.add("list");
            if (sender.hasPermission("xyforgecrafting.reload")) subcommands.add("reload");
            subcommands.add("help");
            return filter(subcommands, args[0]);
        }
        if (args.length == 2 && "get".equalsIgnoreCase(args[0])
                && sender instanceof Player && sender.hasPermission("xyforgecrafting.get")) {
            return filter(plugin.getRecipeRegistry().getIds(), args[1]);
        }
        if (args.length == 3 && "get".equalsIgnoreCase(args[0])
                && sender instanceof Player && sender.hasPermission("xyforgecrafting.get")) {
            return filter(Arrays.asList("1", "16", "32", "64"), args[2]);
        }
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])
                && sender.hasPermission("xyforgecrafting.give")) {
            List<String> names = new ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return filter(names, args[1]);
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0])
                && sender.hasPermission("xyforgecrafting.give")) {
            return filter(plugin.getRecipeRegistry().getIds(), args[2]);
        }
        if (args.length == 4 && "give".equalsIgnoreCase(args[0])
                && sender.hasPermission("xyforgecrafting.give")) {
            return filter(Arrays.asList("1", "16", "32", "64"), args[3]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> values, String prefix) {
        List<String> result = new ArrayList<String>();
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(lower)) result.add(value);
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }
}
