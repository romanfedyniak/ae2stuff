/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.integration.jei;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import ae2stuff.Tags;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeCategory;

/**
 * Fluix made in the Crystal Growth Chamber. Slots and arrow are cut from the furnace, as AE2UD's own
 * categories without a machine screen are.
 */
final class FluixCategory implements IRecipeCategory<FluixRecipe> {

    static final String UID = Tags.MOD_ID + ".growth_chamber_fluix";

    private static final ResourceLocation FURNACE = new ResourceLocation("minecraft", "textures/gui/container/furnace.png");
    private static final int WIDTH = 150;
    private static final int SLOT = 18;
    private static final int ARROW_X = 68;
    private static final int ARROW_WIDTH = 24;
    private static final int OUTPUT_X = 104;
    private static final int INPUTS = 3;
    /**
     * HEI stacks an entry's three buttons upwards from the bottom edge of the category, so a shorter one
     * leaves them hanging above the entry they belong to.
     */
    private static final int BUTTON_COLUMN = 13 * 3 + 4;
    private static final int HEIGHT = Math.max(SLOT, BUTTON_COLUMN);
    private static final int SLOT_TOP = (HEIGHT - SLOT) / 2;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable arrow;

    FluixCategory(final IGuiHelper helper, final ItemStack chamber) {
        this.background = helper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = helper.createDrawableIngredient(chamber);
        this.slot = helper.createDrawable(FURNACE, 55, 16, SLOT, SLOT);
        this.arrow = helper.createDrawable(FURNACE, 79, 35, ARROW_WIDTH, 17);
    }

    private static int inputX(final int input) {
        return ARROW_X - 6 - (INPUTS - input) * SLOT;
    }

    @Override
    public String getUid() {
        return UID;
    }

    @Override
    public String getTitle() {
        return I18n.format("jei.ae2stuff.grower.fluix");
    }

    @Override
    public String getModName() {
        return Tags.MOD_NAME;
    }

    @Override
    public IDrawable getBackground() {
        return this.background;
    }

    @Nullable
    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public void drawExtras(final Minecraft minecraft) {
        for (int input = 0; input < INPUTS; input++) {
            this.slot.draw(minecraft, inputX(input), SLOT_TOP);
        }
        this.arrow.draw(minecraft, ARROW_X, SLOT_TOP);
        this.slot.draw(minecraft, OUTPUT_X, SLOT_TOP);
    }

    @Override
    public void setRecipe(final IRecipeLayout layout, final FluixRecipe recipe, final IIngredients ingredients) {
        final IGuiItemStackGroup items = layout.getItemStacks();
        for (int input = 0; input < INPUTS; input++) {
            items.init(input, true, inputX(input), SLOT_TOP);
        }
        items.init(INPUTS, false, OUTPUT_X, SLOT_TOP);
        items.set(ingredients);
    }
}
