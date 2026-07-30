package org.xyplugin.xyforgecrafting.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.xyplugin.xyforgecrafting.util.Text;

/** Pure Lore transformation used after a successful forge roll. */
final class ForgeRecordFormatter {
    private static final Pattern SEPARATOR = Pattern.compile("^[-—━─═┅_]{3,}$");

    private ForgeRecordFormatter() {
    }

    static List<String> append(List<String> existing, boolean replaceLastSeparator,
                               List<String> templates, Map<String, String> placeholders) {
        List<String> result = existing == null
                ? new ArrayList<String>() : new ArrayList<String>(existing);
        if (replaceLastSeparator && !result.isEmpty() && isSeparator(result.get(result.size() - 1))) {
            result.remove(result.size() - 1);
        }
        for (String template : templates == null ? Collections.<String>emptyList() : templates) {
            result.add(Text.color(Text.replace(template, placeholders)));
        }
        return result;
    }

    static boolean isSeparator(String line) {
        return SEPARATOR.matcher(Text.strip(line).trim()).matches();
    }
}
