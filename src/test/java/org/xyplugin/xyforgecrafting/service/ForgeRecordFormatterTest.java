package org.xyplugin.xyforgecrafting.service;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.xyplugin.xyforgecrafting.config.ForgeRecordSettings;

public class ForgeRecordFormatterTest {
    @Test
    public void replacesOnlyTrailingSeparatorAndAppendsResolvedRecord() {
        List<String> existing = Arrays.asList("§7描述", "§7[✭]§d可强化", "§7----------------------------------");
        List<String> templates = Arrays.asList(
                "&7------------[ &c锻造&7 ]--------------",
                "&e锻造者：&7%player_name%",
                "&e锻造时间：&7%forge_time%",
                "&7----------------------------------");
        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("%player_name%", "XiYouuuuu");
        values.put("%forge_time%", "2026-07-30 23:36:18");

        List<String> result = ForgeRecordFormatter.append(existing, true, templates, values);
        assertEquals(6, result.size());
        assertEquals("§7[✭]§d可强化", result.get(1));
        assertEquals("§7------------[ §c锻造§7 ]--------------", result.get(2));
        assertEquals("§e锻造者：§7XiYouuuuu", result.get(3));
        assertEquals("§e锻造时间：§72026-07-30 23:36:18", result.get(4));
        assertEquals("§7----------------------------------", result.get(5));
    }

    @Test
    public void keepsNormalFinalLoreAndFormatsShanghaiTime() {
        ForgeRecordSettings settings = new ForgeRecordSettings(true, "Asia/Shanghai",
                "yyyy-MM-dd HH:mm:ss", true, Arrays.asList("&e锻造者：&7%player_name%"));
        assertEquals("1970-01-01 08:00:00", settings.format(0L));

        Map<String, String> values = new LinkedHashMap<String, String>();
        values.put("%player_name%", "Player");
        List<String> result = ForgeRecordFormatter.append(Arrays.asList("§7最后一行是描述"), true,
                settings.getLore(), values);
        assertEquals(Arrays.asList("§7最后一行是描述", "§e锻造者：§7Player"), result);
    }
}
