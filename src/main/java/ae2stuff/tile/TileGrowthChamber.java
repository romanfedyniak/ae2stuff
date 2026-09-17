/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.tile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import ae2stuff.core.AE2StuffConfig;
import ae2stuff.core.Registration;
import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.implementations.IAutoExportHost;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.implementations.items.IGrowableCrystal;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.upgrades.CardTrait;
import appeng.api.upgrades.CardTraits;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.AutoExport;
import appeng.api.util.IConfigManager;
import appeng.me.GridAccessException;
import appeng.parts.automation.BlockUpgradeInventory;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.grid.AENetworkPowerTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.UpgradeSpeedCalculations;
import appeng.util.inv.InvOperation;
import appeng.util.inv.WrapperChainedItemHandler;
import appeng.util.inv.WrapperFilteredItemHandler;
import appeng.util.inv.filter.IAEItemFilter;

public final class TileGrowthChamber extends AENetworkPowerTile
        implements IGridTickable, IUpgradeableHost, IConfigManagerHost, IPowerChannelState, IAutoExportHost {

    public static final int SLOTS = 27;
    public static final int UPGRADE_SLOTS = 4;

    private static final int FLUIX_PER_CRAFT = 2;
    /** More cycles than this in one call are dropped rather than caught up, so a long sleep costs one tick. */
    private static final int MAX_CYCLES_PER_CALL = 20;

    private final AppEngInternalInventory input = new AppEngInternalInventory(this, SLOTS);
    private final AppEngInternalInventory output = new AppEngInternalInventory(this, SLOTS);
    private final IItemHandler inventory = new WrapperChainedItemHandler(this.input, this.output);
    private final IItemHandler automation = new WrapperChainedItemHandler(
            new WrapperFilteredItemHandler(this.input, new InputFilter()),
            new WrapperFilteredItemHandler(this.output, new OutputFilter()));
    private final UpgradeInventory upgrades;
    private final IConfigManager settings = new ConfigManager(this);
    private final AutoExport autoExport = new AutoExport(this, this::onAutoExportChanged);

    private YesNo lastRedstoneState = YesNo.UNDECIDED;
    private int ticksIntoCycle;
    private boolean working;

    public TileGrowthChamber() {
        final AE2StuffConfig config = AE2StuffConfig.instance();
        this.setInternalMaxPower(config.getGrowthChamberPowerCapacity());
        this.getProxy().setIdlePowerUsage(config.getGrowthChamberIdlePower());

        this.settings.registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        this.upgrades = new BlockUpgradeInventory(Registration.growthChamber, this, UPGRADE_SLOTS);
    }

    /**
     * What belongs on the input side: seeds, and the three things fluix is made from.
     */
    public static boolean isInput(final ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.getItem() instanceof IGrowableCrystal
                || stack.getItem() == Items.QUARTZ
                || stack.getItem() == Items.REDSTONE
                || AEApi.instance().definitions().materials().certusQuartzCrystalCharged().isSameAs(stack);
    }

    public boolean isWorking() {
        return this.working;
    }

    /**
     * How many input slots hold something still growing, for Waila and The One Probe.
     */
    public int getGrowingStacks() {
        int growing = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (this.input.getStackInSlot(slot).getItem() instanceof IGrowableCrystal) {
                growing++;
            }
        }
        return growing;
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(1, 20, !this.hasCycleWork() && !this.hasExportWork(), false);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        boolean worked = false;

        if (this.isEnabled() && this.hasCycleWork()) {
            final int cycleTicks = AE2StuffConfig.instance().getGrowthChamberCycleTicks();
            this.ticksIntoCycle += ticksSinceLastCall;
            final int cycles = Math.min(this.ticksIntoCycle / cycleTicks, MAX_CYCLES_PER_CALL);
            this.ticksIntoCycle %= cycleTicks;

            for (int i = 0; i < cycles && this.runCycle(); i++) {
                worked = true;
            }
        }

        final boolean exported = this.pushOutResult();
        this.setWorking(worked);

        if (worked || exported) {
            this.saveChanges();
        }

        if (this.isEnabled() && this.hasCycleWork()) {
            return TickRateModulation.URGENT;
        }
        return this.hasExportWork() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
    }

    private boolean hasCycleWork() {
        for (int slot = 0; slot < SLOTS; slot++) {
            final ItemStack stack = this.input.getStackInSlot(slot);
            if (stack.getItem() instanceof IGrowableCrystal) {
                return true;
            }
            if (!stack.isEmpty() && !isInput(stack) && this.fitsOutput(stack)) {
                return true;
            }
        }
        return this.findFluixIngredients() != null;
    }

    private boolean hasExportWork() {
        if (!this.autoExport.isEnabled()) {
            return false;
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!this.output.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * One cycle: every seed grows once per point of speed, what has finished growing moves down, and one
     * batch of fluix is made.
     *
     * @return false when there was not the power for it
     */
    private boolean runCycle() {
        final int speed = UpgradeSpeedCalculations.linearSpeed(this.upgrades.getInstalledPoints(CardTraits.SPEED));
        if (!this.extractPower(AE2StuffConfig.instance().getGrowthChamberCyclePower() * speed)) {
            return false;
        }

        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = this.input.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (stack.getItem() instanceof IGrowableCrystal) {
                final ItemStack before = stack;
                for (int i = 0; i < speed && stack.getItem() instanceof IGrowableCrystal crystal; i++) {
                    stack = crystal.triggerGrowth(stack);
                }
                if (stack != before) {
                    this.input.setStackInSlot(slot, stack);
                }
            }

            if (!stack.isEmpty() && !isInput(stack)) {
                this.input.setStackInSlot(slot, ItemHandlerHelper.insertItemStacked(this.output, stack, false));
            }
        }

        final int[] fluix = this.findFluixIngredients();
        if (fluix != null) {
            for (final int slot : fluix) {
                this.input.extractItem(slot, 1, false);
            }
            ItemHandlerHelper.insertItemStacked(this.output, fluixResult(), false);
        }

        return true;
    }

    /**
     * The slots of charged certus quartz, redstone and nether quartz, or null when one is missing or the
     * whole result would not fit - nothing is used up for a result that would be lost.
     */
    @Nullable
    private int[] findFluixIngredients() {
        int charged = -1;
        int redstone = -1;
        int quartz = -1;

        for (int slot = 0; slot < SLOTS; slot++) {
            final ItemStack stack = this.input.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() == Items.REDSTONE) {
                redstone = slot;
            } else if (stack.getItem() == Items.QUARTZ) {
                quartz = slot;
            } else if (AEApi.instance().definitions().materials().certusQuartzCrystalCharged().isSameAs(stack)) {
                charged = slot;
            }
        }

        if (charged < 0 || redstone < 0 || quartz < 0) {
            return null;
        }
        final ItemStack result = fluixResult();
        if (result.isEmpty() || !this.fitsOutput(result)) {
            return null;
        }
        return new int[] { charged, redstone, quartz };
    }

    private static ItemStack fluixResult() {
        final Optional<ItemStack> fluix = AEApi.instance().definitions().materials().fluixCrystal().maybeStack(FLUIX_PER_CRAFT);
        return fluix.orElse(ItemStack.EMPTY);
    }

    private boolean fitsOutput(final ItemStack stack) {
        return ItemHandlerHelper.insertItemStacked(this.output, stack.copy(), true).isEmpty();
    }

    private boolean extractPower(final double amount) {
        final double threshold = amount - 0.01;
        IEnergySource source = this;
        double available = this.extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG);

        if (available <= threshold) {
            try {
                source = this.getProxy().getEnergy();
                available = source.extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG);
            } catch (final GridAccessException e) {
                return false;
            }
        }

        if (available <= threshold) {
            return false;
        }
        source.extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.CONFIG);
        return true;
    }

    /**
     * Hands the output to whatever sits against the chosen faces.
     *
     * @return true if anything moved
     */
    private boolean pushOutResult() {
        if (!this.hasExportWork()) {
            return false;
        }

        boolean moved = false;
        for (int slot = 0; slot < SLOTS; slot++) {
            final ItemStack result = this.output.getStackInSlot(slot);
            if (result.isEmpty()) {
                continue;
            }

            // Pushed before anything is taken out, so a push that goes nowhere changes nothing
            final long pushed = this.autoExport.push(AEItemKey.of(result), result.getCount());
            if (pushed > 0) {
                this.output.extractItem(slot, (int) pushed, false);
                moved = true;
            }
        }
        return moved;
    }

    @Override
    public AutoExport getAutoExport() {
        return this.autoExport;
    }

    @Override
    public Set<AEKeyType> getAutoExportTypes() {
        return Collections.singleton(AEKeyType.items());
    }

    private void onAutoExportChanged() {
        this.saveChanges();
        this.wake();
    }

    private void setWorking(final boolean working) {
        if (this.working != working) {
            this.working = working;
            this.markForUpdate();
        }
    }

    public void updateRedstoneState() {
        final YesNo current = this.world.getRedstonePowerFromNeighbors(this.pos) != 0 ? YesNo.YES : YesNo.NO;
        if (this.lastRedstoneState == current) {
            return;
        }
        this.lastRedstoneState = current;

        if (current == YesNo.YES && this.settings.getSetting(Settings.REDSTONE_CONTROLLED) == RedstoneMode.SIGNAL_PULSE
                && this.hasCycleWork() && this.runCycle()) {
            this.saveChanges();
        }
        this.wake();
    }

    private boolean isEnabled() {
        if (!this.isInstalled(CardTraits.REDSTONE)) {
            return true;
        }
        if (this.lastRedstoneState == YesNo.UNDECIDED) {
            this.updateRedstoneState();
        }

        return switch ((RedstoneMode) this.settings.getSetting(Settings.REDSTONE_CONTROLLED)) {
            case IGNORE -> true;
            case HIGH_SIGNAL -> this.lastRedstoneState == YesNo.YES;
            case LOW_SIGNAL -> this.lastRedstoneState == YesNo.NO;
            default -> false;
        };
    }

    private void wake() {
        try {
            this.getProxy().getTick().wakeDevice(this.getProxy().getNode());
        } catch (final GridAccessException e) {
            // Not on a network yet; ticking starts when it joins one
        }
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc, final ItemStack removed, final ItemStack added) {
        this.wake();
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        this.saveChanges();
        this.wake();
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.upgrades.writeToNBT(data, "upgrades");
        this.settings.writeToNBT(data);
        this.autoExport.writeToNBT(data);
        return data;
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);

        if (data.hasKey("Items", Constants.NBT.TAG_LIST)) {
            this.readLegacy(data);
            return;
        }
        this.upgrades.readFromNBT(data, "upgrades");
        this.settings.readFromNBT(data);
        this.autoExport.readFromNBT(data);
    }

    /**
     * A chamber saved by the old mod: its items, its cards and its stored power carry over.
     */
    private void readLegacy(final NBTTagCompound data) {
        final List<ItemStack> stacks = new ArrayList<>();
        final NBTTagList items = data.getTagList("Items", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < items.tagCount(); i++) {
            final ItemStack stack = new ItemStack(items.getCompoundTagAt(i));
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }

        final LegacyGrowthChamber.Sorted<ItemStack> sorted = LegacyGrowthChamber.sort(stacks, TileGrowthChamber::isInput);
        for (int slot = 0; slot < sorted.input().size(); slot++) {
            this.input.setStackInSlot(slot, sorted.input().get(slot));
        }
        for (int slot = 0; slot < sorted.output().size(); slot++) {
            this.output.setStackInSlot(slot, sorted.output().get(slot));
        }

        final NBTTagList cards = data.getTagList("upgrades", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < cards.tagCount() && i < UPGRADE_SLOTS; i++) {
            this.upgrades.setStackInSlot(i, new ItemStack(cards.getCompoundTagAt(i)));
        }

        this.setInternalCurrentPower(Math.min(data.getDouble("power"), this.getInternalMaxPower()));
    }

    @Override
    protected boolean readFromStream(final ByteBuf data) throws IOException {
        final boolean changed = super.readFromStream(data);
        final boolean wasWorking = this.working;
        this.working = data.readBoolean();
        return changed || wasWorking != this.working;
    }

    @Override
    protected void writeToStream(final ByteBuf data) throws IOException {
        super.writeToStream(data);
        data.writeBoolean(this.working);
    }

    @Override
    public void getDrops(final World world, final BlockPos pos, final List<ItemStack> drops) {
        super.getDrops(world, pos, drops);
        for (int slot = 0; slot < this.upgrades.getSlots(); slot++) {
            final ItemStack card = this.upgrades.getStackInSlot(slot);
            if (!card.isEmpty()) {
                drops.add(card);
            }
        }
    }

    @Nonnull
    @Override
    public IItemHandler getInternalInventory() {
        return this.inventory;
    }

    @Nonnull
    @Override
    protected IItemHandler getItemHandlerForSide(@Nonnull final EnumFacing side) {
        return this.automation;
    }

    @Override
    public IItemHandler getInventoryByName(final String name) {
        return switch (name) {
            case "upgrades" -> this.upgrades;
            case "input" -> this.input;
            case "output" -> this.output;
            default -> null;
        };
    }

    @Override
    public int getInstalledUpgrades(final ItemStack upgradeCard) {
        return this.upgrades.getInstalledUpgrades(upgradeCard);
    }

    @Override
    public int getInstalledPoints(final CardTrait trait) {
        return this.upgrades.getInstalledPoints(trait);
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.settings;
    }

    @Override
    public TileEntity getTile() {
        return this;
    }

    @Override
    public AECableType getCableConnectionType(final AEPartLocation dir) {
        return AECableType.COVERED;
    }

    @Override
    public boolean canBeRotated() {
        return false;
    }

    @Override
    public boolean isPowered() {
        return this.getProxy().isPowered();
    }

    @Override
    public boolean isActive() {
        return this.getProxy().isActive();
    }

    private static final class InputFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(final IItemHandler inv, final int slot, final int amount) {
            return false;
        }

        @Override
        public boolean allowInsert(final IItemHandler inv, final int slot, final ItemStack stack) {
            return isInput(stack);
        }
    }

    private static final class OutputFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(final IItemHandler inv, final int slot, final int amount) {
            return true;
        }

        @Override
        public boolean allowInsert(final IItemHandler inv, final int slot, final ItemStack stack) {
            return false;
        }
    }
}
