package org.xyplugin.xyforgecrafting.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ForgeGuiSanitizationTest {
    @Test
    public void removesTipTriggerAndStandaloneZeroFromLegacyProbabilityLore() {
        List<String> sanitized = ForgeGuiManager.sanitizeProbabilityLore(Arrays.asList(
                "§8放入图纸后显示失败与品质概率。",
                "§70.0",
                "§7保留这一行"));

        assertEquals(Arrays.asList("§8放入图纸后显示锻造概率。", "§7保留这一行"), sanitized);
        assertFalse(String.join("", sanitized).contains("品质"));
    }
}
