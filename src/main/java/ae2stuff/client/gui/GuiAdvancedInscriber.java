/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.client.gui;

import java.io.IOException;
import java.util.Collections;

import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.common.Loader;

import ae2stuff.container.ContainerAdvancedInscriber;
import ae2stuff.tile.TileAdvancedInscriber;
import appeng.api.config.InscriberInputCapacity;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.client.gui.implementations.GuiUpgradeable;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiProgressBar;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;

/**
 * The Inscriber's own screen, taken from AE2UD's texture, in a window tall enough for five upgrade slots
 * to sit above the network tool's panel.
 */
public final class GuiAdvancedInscriber extends GuiUpgradeable {

    private static final String TEXTURE = "guis/inscriber.png";

    /**
     * The arrow between what goes in and what comes out. HEI opens the inscriber's recipes from there, which
     * is where the eye looks for them - not the thin progress bar at the edge of the window.
     */
    public static final int RECIPE_LEFT = 82;
    public static final int RECIPE_TOP = 39;
    public static final int RECIPE_WIDTH = 26;
    public static final int RECIPE_HEIGHT = 16;

    /** Whether a recipe viewer is there to open, which is the only reason the arrow answers the mouse. */
    private static final boolean RECIPE_VIEWER = Loader.isModLoaded("jei");
    private static final int PANEL_WIDTH = 176;
    /** Where AE2UD's inscriber texture keeps the player inventory, which this taller window puts lower. */
    private static final int TEXTURE_INVENTORY_TOP = 176 - 83;
    private static final int LABEL_COLOR = 4210752;

    private final ContainerAdvancedInscriber container;
    private GuiProgressBar progress;
    private GuiImgButton separateSides;
    private GuiImgButton autoExport;
    private GuiImgButton bufferSize;

    public GuiAdvancedInscriber(final ContainerAdvancedInscriber container) {
        super(container);
        this.container = container;
        this.ySize = ContainerAdvancedInscriber.HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.progress = new GuiProgressBar(this.container, TEXTURE, 135, 39, 135, 177, 6, 18, GuiProgressBar.Direction.VERTICAL);
        this.buttonList.add(this.progress);
    }

    @Override
    protected void addButtons() {
        this.separateSides = new GuiImgButton(this.guiLeft + SIDE_BUTTON_LEFT, this.guiTop + 8, Settings.INSCRIBER_SEPARATE_SIDES, YesNo.NO);
        this.autoExport = new GuiImgButton(this.guiLeft + SIDE_BUTTON_LEFT, this.guiTop + 28, Settings.AUTO_EXPORT, YesNo.NO);
        this.bufferSize = new GuiImgButton(this.guiLeft + SIDE_BUTTON_LEFT, this.guiTop + 48, Settings.INSCRIBER_INPUT_CAPACITY,
                InscriberInputCapacity.SIXTY_FOUR);

        // initGui runs again after HEI's recipe screen closes, so the column is rebuilt rather than added to
        this.column.clear();
        for (final GuiImgButton button : new GuiImgButton[] { this.separateSides, this.autoExport, this.bufferSize }) {
            this.buttonList.add(button);
            this.column.add(button);
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.separateSides || btn == this.autoExport || btn == this.bufferSize) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(((GuiImgButton) btn).getSetting(), Mouse.isButtonDown(1)));
        }
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        // The line is HEI's own, so whoever has it installed already has it translated
        if (RECIPE_VIEWER && mouseX >= this.guiLeft + RECIPE_LEFT && mouseX < this.guiLeft + RECIPE_LEFT + RECIPE_WIDTH
                && mouseY >= this.guiTop + RECIPE_TOP && mouseY < this.guiTop + RECIPE_TOP + RECIPE_HEIGHT) {
            this.drawHoveringText(Collections.singletonList(I18n.format("jei.tooltip.show.recipes")), mouseX, mouseY);
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);

        this.fontRenderer.drawString(I18n.format("tile.ae2stuff.inscriber.name"), 8, 6, LABEL_COLOR);
        if (this.container.getMaxProgress() > 0) {
            this.progress.setFullMsg(this.container.getCurrentProgress() * 100 / this.container.getMaxProgress() + "%");
        }
        this.separateSides.set(this.container.separateSides);
        this.autoExport.set(this.container.autoExport);
        this.bufferSize.set(this.container.bufferSize);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.handleButtonVisibility();
        this.progress.x = 135 + this.guiLeft;
        this.progress.y = 39 + this.guiTop;

        this.drawPanel(offsetX, offsetY, PANEL_WIDTH, this.ySize);

        this.bindTexture(TEXTURE);
        // The machine: plates, input, arrows and output, where the texture has them
        this.drawTexturedModalRect(offsetX + 8, offsetY + 14, 8, 14, 160, 70);
        // The player inventory, moved down with the taller window
        final int inventoryTop = offsetY + this.ySize - 83;
        this.drawTexturedModalRect(offsetX + 7, inventoryTop, 7, TEXTURE_INVENTORY_TOP, 162, 76);

        this.drawPanel(offsetX + 179, offsetY, 32, 14 + TileAdvancedInscriber.UPGRADE_SLOTS * 18);
        for (int slot = 0; slot < TileAdvancedInscriber.UPGRADE_SLOTS; slot++) {
            drawSlotWell(offsetX + 187, offsetY + 8 + slot * 18);
        }

        if (this.hasToolbox()) {
            this.drawPanel(offsetX + 178, offsetY + this.ySize - 90, 68, 68);
            for (int slot = 0; slot < 9; slot++) {
                drawSlotWell(offsetX + 186 + slot % 3 * 18, offsetY + this.ySize - 82 + slot / 3 * 18);
            }
        }
    }

    @Override
    protected String getBackground() {
        return TEXTURE;
    }
}
