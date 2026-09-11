/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.client.gui;

import java.io.IOException;

import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;

import ae2stuff.container.ContainerGrowthChamber;
import ae2stuff.tile.TileGrowthChamber;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.client.gui.implementations.GuiUpgradeable;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;

public final class GuiGrowthChamber extends GuiUpgradeable {

    private static final int PANEL_WIDTH = 176;
    private static final int LABEL_COLOR = 4210752;

    private final ContainerGrowthChamber container;
    private GuiImgButton autoExport;

    public GuiGrowthChamber(final ContainerGrowthChamber container) {
        super(container);
        this.container = container;
        this.ySize = ContainerGrowthChamber.HEIGHT;
    }

    @Override
    protected void addButtons() {
        this.autoExport = new GuiImgButton(this.guiLeft + SIDE_BUTTON_LEFT, this.guiTop + 8, Settings.AUTO_EXPORT, YesNo.NO);
        this.redstoneMode = new GuiImgButton(this.guiLeft + SIDE_BUTTON_LEFT, this.guiTop + 28, Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);

        this.buttonList.add(this.autoExport);
        this.buttonList.add(this.redstoneMode);
        this.column.add(this.autoExport);
        this.column.add(this.redstoneMode);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.autoExport) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(this.autoExport.getSetting(), Mouse.isButtonDown(1)));
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);

        this.fontRenderer.drawString(I18n.format("tile.ae2stuff.grower.name"), 8, 6, LABEL_COLOR);
        this.fontRenderer.drawString(I18n.format("gui.ae2stuff.grower.output"), 8, ContainerGrowthChamber.OUTPUT_TOP - 11, LABEL_COLOR);
        this.autoExport.set(this.container.autoExport);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.handleButtonVisibility();

        this.drawPanel(offsetX, offsetY, PANEL_WIDTH, this.ySize);
        for (int slot = 0; slot < TileGrowthChamber.SLOTS; slot++) {
            final int x = offsetX + 8 + slot % 9 * 18;
            drawSlotWell(x, offsetY + ContainerGrowthChamber.INPUT_TOP + slot / 9 * 18);
            drawSlotWell(x, offsetY + ContainerGrowthChamber.OUTPUT_TOP + slot / 9 * 18);
        }

        final int inventoryTop = offsetY + this.ySize - 82;
        for (int slot = 0; slot < 36; slot++) {
            final int row = slot / 9;
            drawSlotWell(offsetX + 8 + slot % 9 * 18, inventoryTop + row * 18 + (row == 3 ? 4 : 0));
        }

        this.drawPanel(offsetX + 179, offsetY, 32, 14 + TileGrowthChamber.UPGRADE_SLOTS * 18);
        for (int slot = 0; slot < TileGrowthChamber.UPGRADE_SLOTS; slot++) {
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
        return "guis/inscriber.png";
    }
}
