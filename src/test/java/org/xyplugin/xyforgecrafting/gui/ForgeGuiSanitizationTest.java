package org.xyplugin.xyforgecrafting.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.xyplugin.xyitems.api.ForgeOutcomeProfile;

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

    @Test
    public void oneGuaranteedQualityHidesZeroFailureAndUsesOneLoreLine() {
        List<ForgeOutcomeProfile.Outcome> outcomes = Arrays.asList(
                new ForgeOutcomeProfile.Outcome(ForgeOutcomeProfile.Outcome.Type.FAILURE,
                        "failure", "锻造失败", "&c", 0D, 0D),
                new ForgeOutcomeProfile.Outcome(ForgeOutcomeProfile.Outcome.Type.QUALITY,
                        "legendary", "传说", "&6", 1D, 100D));

        assertEquals(Arrays.asList("&6传说: &f100%"), ForgeGuiManager.probabilityLore(outcomes));
    }

    @Test
    public void probabilityLoreShowsFailureAndAllNonZeroQualities() {
        List<ForgeOutcomeProfile.Outcome> outcomes = Arrays.asList(
                new ForgeOutcomeProfile.Outcome(ForgeOutcomeProfile.Outcome.Type.FAILURE,
                        "failure", "锻造失败", "&c", 30D, 30D),
                new ForgeOutcomeProfile.Outcome(ForgeOutcomeProfile.Outcome.Type.QUALITY,
                        "1", "白描", "&f", 19.6D, 19.6D),
                new ForgeOutcomeProfile.Outcome(ForgeOutcomeProfile.Outcome.Type.QUALITY,
                        "2", "萌黄", "&a", 15.4D, 15.4D));

        assertEquals(Arrays.asList("&c失败几率: &f30%", "&f白描: &f19.6%", "&a萌黄: &f15.4%"),
                ForgeGuiManager.probabilityLore(outcomes));
    }
}
