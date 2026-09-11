/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.integration.jei;

import java.util.Arrays;
import java.util.Collections;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import ae2stuff.client.gui.GuiWirelessKit;
import ae2stuff.core.AE2StuffConfig;
import ae2stuff.core.Registration;
import appeng.api.AEApi;
import appeng.api.definitions.IMaterials;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.recipe.IRecipeCategoryRegistration;

@JEIPlugin
public final class AE2StuffJeiPlugin implements IModPlugin {

    /** AE2UD's category for seeds growing; it skips a catalyst whose category is switched off. */
    private static final String CERTUS_GROWTH = "appliedenergistics2.certus_growth";
    private static final String INSCRIBER = "appliedenergistics2.inscriber";

    @Override
    public void registerCategories(final IRecipeCategoryRegistration registry) {
        if (showsFluix()) {
            registry.addRecipeCategories(new FluixCategory(registry.getJeiHelpers().getGuiHelper(),
                    new ItemStack(Registration.growthChamber)));
        }
    }

    @Override
    public void register(final IModRegistry registry) {
        // No properties means HEI draws nothing beside the kit's manager, which only needs the room
        registry.addGuiScreenHandler(GuiWirelessKit.class, gui -> null);

        if (Registration.advancedInscriber != null) {
            registry.addRecipeCatalyst(new ItemStack(Registration.advancedInscriber), INSCRIBER);
        }

        if (Registration.growthChamber == null) {
            return;
        }

        final ItemStack chamber = new ItemStack(Registration.growthChamber);
        registry.addRecipeCatalyst(chamber, CERTUS_GROWTH);

        if (showsFluix()) {
            final IMaterials materials = AEApi.instance().definitions().materials();
            final FluixRecipe recipe = new FluixRecipe(
                    Arrays.asList(materials.certusQuartzCrystalCharged().maybeStack(1).orElse(ItemStack.EMPTY),
                            new ItemStack(Items.REDSTONE), new ItemStack(Items.QUARTZ)),
                    materials.fluixCrystal().maybeStack(2).orElse(ItemStack.EMPTY));
            registry.addRecipes(Collections.singletonList(recipe), FluixCategory.UID);
            registry.addRecipeCatalyst(chamber, FluixCategory.UID);
        }
    }

    private static boolean showsFluix() {
        return Registration.growthChamber != null && AE2StuffConfig.instance().isJeiGrowthChamberFluix();
    }
}
