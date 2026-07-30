package org.xyplugin.xyforgecrafting.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.logging.Logger;
import org.junit.Test;
import org.xyplugin.xyforgecrafting.recipe.RecipeRegistry;

public class DefaultConfigurationTest {
    @Test
    public void defaultGuiAndRecipeAreValid() throws Exception {
        ForgeSettings settings = ForgeSettings.load(new File("src/main/resources/config.yml"));
        assertEquals(54, settings.getSize());
        assertEquals(1, settings.getSlots(GuiComponentType.FORGE_BLUEPRINT).size());
        assertEquals(1, settings.getSlots(GuiComponentType.FORGE_START).size());
        assertEquals(7, settings.getSlots(GuiComponentType.FORGE_PROBABILITY).size());

        RecipeRegistry.LoadResult recipes = RecipeRegistry.load(new File("src/main/resources/ForgeRecipe"),
                Logger.getLogger("DefaultConfigurationTest"));
        assertTrue(recipes.getErrors().toString(), recipes.isSuccess());
        assertEquals(1, recipes.getRegistry().size());
        assertTrue(recipes.getRegistry().find("example_forge_soul").isPresent());
    }
}
