package org.xyplugin.xyforgecrafting.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
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
        assertTrue(settings.getForgeRecord().isEnabled());
        assertEquals("1970-01-01 08:00:00", settings.getForgeRecord().format(0L));

        RecipeRegistry.LoadResult recipes = RecipeRegistry.load(new File("src/main/resources/ForgeRecipe"),
                Logger.getLogger("DefaultConfigurationTest"));
        assertTrue(recipes.getErrors().toString(), recipes.isSuccess());
        assertEquals(1, recipes.getRegistry().size());
        assertTrue(recipes.getRegistry().find("example_forge_soul").isPresent());
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
        assertEquals("1.0.4", descriptor.getString("version"));
        assertTrue(descriptor.isConfigurationSection("commands.xyfc"));
        assertTrue(!descriptor.isConfigurationSection("commands.xyff"));
        assertTrue(descriptor.isConfigurationSection("permissions.xyforgecrafting.get"));
        assertEquals("op", descriptor.getString("permissions.xyforgecrafting.get.default"));
    }
}
