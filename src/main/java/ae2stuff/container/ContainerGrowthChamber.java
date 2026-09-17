/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.container;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import ae2stuff.tile.TileGrowthChamber;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.util.IConfigManager;
import appeng.container.guisync.GuiSync;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotOutput;
import appeng.util.Platform;

public final class ContainerGrowthChamber extends ContainerUpgradeable {

    public static final int HEIGHT = 236;
    public static final int INPUT_TOP = 18;
    public static final int OUTPUT_TOP = 86;

    /** The export faces as AutoExport packs them for a window: chosen, with a taker, refused. */
    @GuiSync(20)
    public int autoExport;

    private final TileGrowthChamber chamber;

    public ContainerGrowthChamber(final InventoryPlayer ip, final TileGrowthChamber chamber) {
        super(ip, chamber);
        this.chamber = chamber;
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            this.autoExport = this.chamber.getAutoExport().getSyncState();
        }
        super.detectAndSendChanges();
    }

    @Override
    protected int getHeight() {
        return HEIGHT;
    }

    @Override
    protected void setupConfig() {
        final IItemHandler input = this.getUpgradeable().getInventoryByName("input");
        final IItemHandler output = this.getUpgradeable().getInventoryByName("output");

        for (int slot = 0; slot < TileGrowthChamber.SLOTS; slot++) {
            final int x = 8 + slot % 9 * 18;
            this.addSlotToContainer(new InputSlot(input, slot, x, INPUT_TOP + slot / 9 * 18));
            this.addSlotToContainer(new SlotOutput(output, slot, x, OUTPUT_TOP + slot / 9 * 18, -1));
        }

        this.setupUpgrades();
    }

    @Override
    public int availableUpgrades() {
        return TileGrowthChamber.UPGRADE_SLOTS;
    }

    @Override
    protected void loadSettingsFromHost(final IConfigManager cm) {
        // Not the base implementation: the chamber registers no fuzzy mode, and asking for one would throw
        this.setRedStoneMode((RedstoneMode) cm.getSetting(Settings.REDSTONE_CONTROLLED));
    }

    private static final class InputSlot extends AppEngSlot {
        InputSlot(final IItemHandler inv, final int slot, final int x, final int y) {
            super(inv, slot, x, y);
        }

        @Override
        public boolean isItemValid(@Nonnull final ItemStack stack) {
            return TileGrowthChamber.isInput(stack);
        }
    }
}
