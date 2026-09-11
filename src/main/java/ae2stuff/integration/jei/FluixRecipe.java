/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.integration.jei;

import java.util.List;

import net.minecraft.item.ItemStack;

import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;

final class FluixRecipe implements IRecipeWrapper {

    private final List<ItemStack> inputs;
    private final ItemStack output;

    FluixRecipe(final List<ItemStack> inputs, final ItemStack output) {
        this.inputs = inputs;
        this.output = output;
    }

    List<ItemStack> inputs() {
        return this.inputs;
    }

    @Override
    public void getIngredients(final IIngredients ingredients) {
        ingredients.setInputs(VanillaTypes.ITEM, this.inputs);
        ingredients.setOutput(VanillaTypes.ITEM, this.output);
    }
}
