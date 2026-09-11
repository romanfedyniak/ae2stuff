/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.container;

import net.minecraft.entity.player.InventoryPlayer;

import ae2stuff.core.AE2StuffConfig;
import ae2stuff.tile.TileAdvancedInscriber;
import appeng.api.config.InscriberInputCapacity;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.util.IConfigManager;
import appeng.container.guisync.GuiSync;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.container.interfaces.IProgressProvider;
import appeng.container.slot.SlotOutput;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.util.Platform;

public final class ContainerAdvancedInscriber extends ContainerUpgradeable implements IProgressProvider {

    public static final int HEIGHT = 194;

    /** The four faces that are neither the top nor the bottom. */
    private static final ButtonToolTips[] FLANKS = {
            ButtonToolTips.SideLeft, ButtonToolTips.SideRight, ButtonToolTips.SideBack, ButtonToolTips.SideFront
    };

    @GuiSync(20)
    public int processingTime = -1;
    @GuiSync(21)
    public int maxProcessingTime = -1;
    @GuiSync(22)
    public YesNo separateSides = YesNo.NO;
    @GuiSync(23)
    public YesNo autoExport = YesNo.NO;
    @GuiSync(24)
    public InscriberInputCapacity bufferSize = InscriberInputCapacity.SIXTY_FOUR;

    private final TileAdvancedInscriber inscriber;

    public ContainerAdvancedInscriber(final InventoryPlayer ip, final TileAdvancedInscriber inscriber) {
        super(ip, inscriber);
        this.inscriber = inscriber;
    }

    @Override
    protected int getHeight() {
        return HEIGHT;
    }

    @Override
    protected void setupConfig() {
        final SlotRestrictedInput top = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.INSCRIBER_PLATE,
                this.getUpgradeable().getInventoryByName("top"), 0, 45, 16, this.getInventoryPlayer());
        top.setEmptyTooltip(() -> this.insertFrom(ButtonToolTips.SideTop));
        // The tile's own limit is not known on the client, which predicts clicks, so the synced setting caps the slot
        top.setStackLimitCap(this::inputCapacity);
        this.addSlotToContainer(top);

        final SlotRestrictedInput bottom = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.INSCRIBER_PLATE,
                this.getUpgradeable().getInventoryByName("bottom"), 0, 45, 62, this.getInventoryPlayer());
        bottom.setEmptyTooltip(() -> this.insertFrom(ButtonToolTips.SideBottom));
        bottom.setStackLimitCap(this::inputCapacity);
        this.addSlotToContainer(bottom);

        final SlotRestrictedInput input = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.INSCRIBER_INPUT,
                this.getUpgradeable().getInventoryByName("input"), 0, 63, 39, this.getInventoryPlayer());
        input.setEmptyTooltip(() -> this.insertFrom(FLANKS));
        input.setStackLimitCap(this::inputCapacity);
        this.addSlotToContainer(input);

        final SlotOutput output = new SlotOutput(this.getUpgradeable().getInventoryByName("output"), 0, 113, 40, -1);
        output.setEmptyTooltip(() -> this.extractFrom(FLANKS));
        this.addSlotToContainer(output);

        this.setupUpgrades();
    }

    private int inputCapacity() {
        return this.bufferSize.capacity;
    }

    private String insertFrom(final ButtonToolTips... whenSeparate) {
        return String.format(ButtonToolTips.CanInsertFrom.getLocal(), this.sideList(whenSeparate));
    }

    private String extractFrom(final ButtonToolTips... whenSeparate) {
        return String.format(ButtonToolTips.CanExtractFrom.getLocal(), this.sideList(whenSeparate));
    }

    /**
     * Read on every hover: the automation access mode decides whether a slot belongs to some faces or to all.
     */
    private String sideList(final ButtonToolTips[] whenSeparate) {
        if (this.separateSides != YesNo.YES) {
            return ButtonToolTips.SideAny.getLocal();
        }

        final StringBuilder sides = new StringBuilder(whenSeparate[0].getLocal());
        for (int i = 1; i < whenSeparate.length; i++) {
            sides.append(i == whenSeparate.length - 1 ? " " + GuiText.And.getLocal() + " " : ", ");
            sides.append(whenSeparate[i].getLocal());
        }
        return sides.toString();
    }

    @Override
    public int availableUpgrades() {
        return TileAdvancedInscriber.UPGRADE_SLOTS;
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            this.processingTime = this.inscriber.getProcessingTime();
            this.maxProcessingTime = AE2StuffConfig.instance().getAdvancedInscriberCycleTicks();
        }
        super.detectAndSendChanges();
    }

    @Override
    protected void loadSettingsFromHost(final IConfigManager cm) {
        // Not the base implementation: the inscriber registers neither a redstone nor a fuzzy mode
        this.separateSides = (YesNo) cm.getSetting(Settings.INSCRIBER_SEPARATE_SIDES);
        this.autoExport = (YesNo) cm.getSetting(Settings.AUTO_EXPORT);
        this.bufferSize = (InscriberInputCapacity) cm.getSetting(Settings.INSCRIBER_INPUT_CAPACITY);
    }

    @Override
    public int getCurrentProgress() {
        return this.processingTime;
    }

    @Override
    public int getMaxProgress() {
        return this.maxProcessingTime;
    }
}
