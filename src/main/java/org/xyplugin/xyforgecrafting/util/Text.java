package org.xyplugin.xyforgecrafting.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.ChatColor;

public final class Text {
    private Text() {
    }

    public static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    public static String strip(String value) {
        String stripped = ChatColor.stripColor(color(value));
        return stripped == null ? "" : stripped;
    }

    public static String replace(String value, Map<String, String> placeholders) {
        String result = value == null ? "" : value;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    public static List<String> colored(List<String> lines) {
        List<String> result = new ArrayList<String>();
        for (String line : lines) result.add(color(line));
        return result;
    }
}
