package org.xyplugin.xyforgecrafting.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import org.xyplugin.xyforgecrafting.recipe.RecipeRegistry;

public class DefaultConfigurationTest {
    @Test
    public void defaultGuiAndRecipeAreValid() throws Exception {
        ForgeSettings settings = ForgeSettings.load(new File("src/main/resources/config.yml"));
        assertEquals(54, settings.getSize());
        assertEquals(1, settings.getSlots(GuiComponentType.FORGE_BLUEPRINT).size());
        assertEquals(1, settings.getSlots(GuiComponentType.FORGE_START).size());
        assertEquals(1, settings.getSlots(GuiComponentType.FORGE_PROBABILITY).size());
        assertEquals(1, settings.getSlots(GuiComponentType.FORGE_RESULT).size());
        assertEquals("BORDER_CONVERGE", settings.getAnimationPreset());
        assertTrue(settings.getAnimationFrames().size() > 1);
        assertEquals(Arrays.asList(0), settings.getAnimationFrames().get(0));
        assertTrue(settings.getForgeRecord().isEnabled());
        assertEquals("1970-01-01 08:00:00", settings.getForgeRecord().format(0L));

        RecipeRegistry.LoadResult recipes = RecipeRegistry.load(new File("src/main/resources/ForgeRecipe"),
                Logger.getLogger("DefaultConfigurationTest"));
        assertTrue(recipes.getErrors().toString(), recipes.isSuccess());
        assertEquals(1, recipes.getRegistry().size());
        assertTrue(recipes.getRegistry().find("example_forge_soul").isPresent());
        assertEquals(1000D, recipes.getRegistry().find("example_forge_soul").get()
                .getEconomy().getAmount(), 0.000001D);
    }

    @Test
    public void allBuiltInAnimationPresetsLoadOnDefaultLayout() throws Exception {
        String original = new String(Files.readAllBytes(
                new File("src/main/resources/config.yml").toPath()), StandardCharsets.UTF_8);
        for (String preset : Arrays.asList("BORDER_CONVERGE", "BOTTOM_SWEEP", "DOUBLE_SWEEP")) {
            File temporary = File.createTempFile("xyforge-animation-" + preset.toLowerCase(), ".yml");
            try {
                String rewritten = original.replace("active-preset: BORDER_CONVERGE", "active-preset: " + preset);
                Files.write(temporary.toPath(), rewritten.getBytes(StandardCharsets.UTF_8));
                ForgeSettings settings = ForgeSettings.load(temporary);
                assertEquals(preset, settings.getAnimationPreset());
                assertTrue(settings.getAnimationFrames().size() > 0);
            } finally {
                assertTrue(temporary.delete() || !temporary.exists());
            }
        }
    }

    @Test
    public void componentTypeMustBeExplicit() throws Exception {
        String original = new String(Files.readAllBytes(
                new File("src/main/resources/config.yml").toPath()), StandardCharsets.UTF_8);
        String withoutType = original.replaceFirst("(?m)^ {6}type: FORGE_RESULT\\r?\\n", "");
        assertTrue(!original.equals(withoutType));
        File temporary = File.createTempFile("xyforge-missing-type", ".yml");
        try {
            Files.write(temporary.toPath(), withoutType.getBytes(StandardCharsets.UTF_8));
            ForgeSettings.load(temporary);
            fail("缺少type的GUI组件不应通过加载");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("必须明确填写type"));
        } finally {
            assertTrue(temporary.delete() || !temporary.exists());
        }
    }

    @Test
    public void pluginDescriptorUsesXyfcAndDeclaresGetPermission() {
        YamlConfiguration descriptor = YamlConfiguration.loadConfiguration(
                new File("src/main/resources/plugin.yml"));
        assertEquals("1.0.5", descriptor.getString("version"));
        assertTrue(descriptor.isConfigurationSection("commands.xyfc"));
        assertTrue(!descriptor.isConfigurationSection("commands.xyff"));
        assertTrue(descriptor.isConfigurationSection("permissions.xyforgecrafting.get"));
        assertEquals("op", descriptor.getString("permissions.xyforgecrafting.get.default"));
    }
}
