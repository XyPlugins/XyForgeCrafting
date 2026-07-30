package org.xyplugin.xyforgecrafting.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
import org.xyplugin.xyforgecrafting.util.Text;

public final class XyForgeCommand implements CommandExecutor, TabCompleter {
    private final XyForgeCraftingPlugin plugin;

    public XyForgeCommand(XyForgeCraftingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase();
        if ("open".equals(sub)) return open(sender);
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
            sender.sendMessage(Text.color("&c用法: /xyff give <玩家> <配方ID> [数量]"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Text.color("&c玩家不在线: " + args[1]));
            return true;
        }
        Optional<RecipeDefinition> recipe = plugin.getRecipeRegistry().find(args[2]);
        if (!recipe.isPresent()) {
            sender.sendMessage(Text.color("&c不存在或未启用的配方: " + args[2]));
            return true;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException failure) {
                sender.sendMessage(Text.color("&c数量必须是1到64的整数。"));
                return true;
            }
        }
        if (amount <= 0 || amount > 64) {
            sender.sendMessage(Text.color("&c数量必须是1到64的整数。"));
            return true;
        }
        Optional<ItemStack> blueprint = plugin.getBlueprints().create(recipe.get(), 1);
        if (!blueprint.isPresent()) {
            sender.sendMessage(Text.color("&c图纸基础物品无法生成，请检查blueprint.template。"));
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
        sender.sendMessage(Text.color("&a已给予 " + target.getName() + " " + amount + " 张 "
                + recipe.get().getBlueprint().getDisplayName() + "&a。"));
        return true;
    }

    private boolean list(CommandSender sender) {
        if (!sender.hasPermission("xyforgecrafting.list")) {
            plugin.send(sender, "no-permission");
            return true;
        }
        sender.sendMessage(Text.color("&6已加载锻造配方 (&f" + plugin.getRecipeRegistry().size() + "&6): &f"
                + String.join(", ", plugin.getRecipeRegistry().getIds())));
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
        sender.sendMessage(Text.color("&6=== XyForgeCrafting 1.0.1 ==="));
        sender.sendMessage(Text.color("&e/xyff open &7- 打开锻造台"));
        if (sender.hasPermission("xyforgecrafting.list")) sender.sendMessage(Text.color("&e/xyff list &7- 查看配方"));
        if (sender.hasPermission("xyforgecrafting.give")) sender.sendMessage(Text.color("&e/xyff give <玩家> <配方ID> [数量] &7- 生成签名图纸"));
        if (sender.hasPermission("xyforgecrafting.reload")) sender.sendMessage(Text.color("&e/xyff reload &7- 安全重载"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(Arrays.asList("open", "give", "list", "reload", "help"), args[0]);
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            List<String> names = new ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
            return filter(names, args[1]);
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) return filter(plugin.getRecipeRegistry().getIds(), args[2]);
        return Collections.emptyList();
    }

    private List<String> filter(List<String> values, String prefix) {
        List<String> result = new ArrayList<String>();
        String lower = prefix == null ? "" : prefix.toLowerCase();
        for (String value : values) if (value.toLowerCase().startsWith(lower)) result.add(value);
        return result;
    }
}
