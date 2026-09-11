/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.tile;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.IItemHandler;

import ae2stuff.core.AE2StuffConfig;
import ae2stuff.core.Registration;
import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.InscriberInputCapacity;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.definitions.IComparableDefinition;
import appeng.api.features.IInscriberRecipe;
import appeng.api.features.IInscriberRecipeBuilder;
import appeng.api.features.InscriberProcessType;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.upgrades.CardTrait;
import appeng.api.upgrades.CardTraits;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.IConfigManager;
import appeng.me.GridAccessException;
import appeng.parts.automation.BlockUpgradeInventory;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.grid.AENetworkPowerTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.UpgradeSpeedCalculations;
import appeng.util.inv.InvOperation;
import appeng.util.inv.WrapperChainedItemHandler;
import appeng.util.inv.WrapperFilteredItemHandler;
import appeng.util.inv.filter.IAEItemFilter;

public final class TileAdvancedInscriber extends AENetworkPowerTile
        implements IGridTickable, IUpgradeableHost, IConfigManagerHost, IPowerChannelState {

    public static final int UPGRADE_SLOTS = 5;

    private final AppEngInternalInventory top = new AppEngInternalInventory(this, 1, 64);
    private final AppEngInternalInventory bottom = new AppEngInternalInventory(this, 1, 64);
    private final AppEngInternalInventory input = new AppEngInternalInventory(this, 1, 64);
    private final AppEngInternalInventory output = new AppEngInternalInventory(this, 1, 64);
    private final IItemHandler inventory = new WrapperChainedItemHandler(this.top, this.bottom, this.input, this.output);

    private final IItemHandler topExtern = new WrapperFilteredItemHandler(this.top, new PlateFilter());
    private final IItemHandler bottomExtern = new WrapperFilteredItemHandler(this.bottom, new PlateFilter());
    private final IItemHandler sideExtern = new WrapperChainedItemHandler(
            new WrapperFilteredItemHandler(this.input, new InputFilter()),
            new WrapperFilteredItemHandler(this.output, new OutputFilter()));
    private final IItemHandler combinedExtern = new WrapperChainedItemHandler(this.topExtern, this.bottomExtern, this.sideExtern);

    private final UpgradeInventory upgrades;
    private final IConfigManager settings = new ConfigManager(this);

    private int processingTime;
    private boolean working;
    /** The recipe the slots hold, looked up again only once they change. */
    @Nullable
    private IInscriberRecipe cachedRecipe;
    private boolean recipeKnown;
    /** What the old mod had already pressed and not yet put out, handed to the output once there is room. */
    private ItemStack legacyPending = ItemStack.EMPTY;

    public TileAdvancedInscriber() {
        final AE2StuffConfig config = AE2StuffConfig.instance();
        this.setInternalMaxPower(config.getAdvancedInscriberPowerCapacity());
        this.getProxy().setIdlePowerUsage(config.getAdvancedInscriberIdlePower());

        this.settings.registerSetting(Settings.INSCRIBER_SEPARATE_SIDES, YesNo.NO);
        this.settings.registerSetting(Settings.AUTO_EXPORT, YesNo.NO);
        this.settings.registerSetting(Settings.INSCRIBER_INPUT_CAPACITY, InscriberInputCapacity.SIXTY_FOUR);
        this.upgrades = new BlockUpgradeInventory(Registration.advancedInscriber, this, UPGRADE_SLOTS);
        this.applyInputCapacity();
    }

    /**
     * What may go on the top or bottom: a name press, or anything a recipe uses as a plate.
     */
    public static boolean isPlate(final ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (AEApi.instance().definitions().materials().namePress().isSameAs(stack)) {
            return true;
        }
        for (final ItemStack plate : AEApi.instance().registries().inscriber().getOptionals()) {
            if (Platform.itemComparisons().isSameItem(stack, plate)) {
                return true;
            }
        }
        return false;
    }

    public boolean isWorking() {
        return this.working;
    }

    public int getProcessingTime() {
        return this.processingTime;
    }

    private boolean isSeparateSides() {
        return this.settings.getSetting(Settings.INSCRIBER_SEPARATE_SIDES) == YesNo.YES;
    }

    private void applyInputCapacity() {
        final int capacity = ((InscriberInputCapacity) this.settings.getSetting(Settings.INSCRIBER_INPUT_CAPACITY)).capacity;
        this.top.setMaxStackSize(0, capacity);
        this.bottom.setMaxStackSize(0, capacity);
        this.input.setMaxStackSize(0, capacity);
    }

    @Nullable
    private IInscriberRecipe findRecipe() {
        final ItemStack item = this.input.getStackInSlot(0);
        if (item.isEmpty()) {
            return null;
        }

        final ItemStack plateA = this.top.getStackInSlot(0);
        final ItemStack plateB = this.bottom.getStackInSlot(0);
        final IComparableDefinition namePress = AEApi.instance().definitions().materials().namePress();
        if (namePress.isSameAs(plateA) && (plateB.isEmpty() || namePress.isSameAs(plateB))) {
            return namePressRecipe(item, plateA, plateB);
        }
        if (plateA.isEmpty() && namePress.isSameAs(plateB)) {
            return namePressRecipe(item, plateB, plateA);
        }

        for (final IInscriberRecipe recipe : AEApi.instance().registries().inscriber().getRecipes()) {
            if (!matchesAny(recipe.getInputs(), item)) {
                continue;
            }
            if (platesMatch(recipe, plateA, plateB) || platesMatch(recipe, plateB, plateA)) {
                return recipe;
            }
        }
        return null;
    }

    private static boolean platesMatch(final IInscriberRecipe recipe, final ItemStack upper, final ItemStack lower) {
        return plateMatches(recipe.getTopInputs(), upper) && plateMatches(recipe.getBottomInputs(), lower);
    }

    private static boolean plateMatches(final List<ItemStack> wanted, final ItemStack plate) {
        return wanted.isEmpty() ? plate.isEmpty() : matchesAny(wanted, plate);
    }

    private static boolean matchesAny(final List<ItemStack> options, final ItemStack stack) {
        for (final ItemStack option : options) {
            if (Platform.itemComparisons().isSameItem(stack, option)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Renaming with a name press: the names on the plates, joined, become the item's name.
     */
    private static IInscriberRecipe namePressRecipe(final ItemStack item, final ItemStack plateA, final ItemStack plateB) {
        String name = "";
        if (!plateA.isEmpty()) {
            name += Platform.openNbtData(plateA).getString("InscribeName");
        }
        if (!plateB.isEmpty()) {
            name += " " + Platform.openNbtData(plateB).getString("InscribeName");
        }

        final ItemStack start = item.copy();
        start.setCount(1);
        final ItemStack renamed = item.copy();
        renamed.setCount(1);
        final NBTTagCompound tag = Platform.openNbtData(renamed);
        final NBTTagCompound display = tag.getCompoundTag("display");
        tag.setTag("display", display);
        if (name.isEmpty()) {
            display.removeTag("Name");
        } else {
            display.setString("Name", name);
        }

        final IInscriberRecipeBuilder builder = AEApi.instance().registries().inscriber().builder();
        builder.withInputs(Collections.singletonList(start)).withOutput(renamed).withProcessType(InscriberProcessType.INSCRIBE);
        if (!plateA.isEmpty()) {
            builder.withTopOptional(Collections.singletonList(plateA));
        }
        if (!plateB.isEmpty()) {
            builder.withBottomOptional(Collections.singletonList(plateB));
        }
        return builder.build();
    }

    /**
     * How many items the next press works, for the recipe in the slots now: capped by capacity points, by what
     * is there to use up, and by how much the output still takes.
     */
    private int batchSize(final IInscriberRecipe recipe) {
        final boolean consumesPlates = recipe.getProcessType() == InscriberProcessType.PRESS;
        final ItemStack result = recipe.getOutput();
        final ItemStack current = this.output.getStackInSlot(0);

        final int room;
        if (current.isEmpty()) {
            room = Math.min(result.getMaxStackSize(), this.output.getSlotLimit(0));
        } else if (Platform.itemComparisons().isSameItem(current, result)) {
            room = Math.min(current.getMaxStackSize(), this.output.getSlotLimit(0)) - current.getCount();
        } else {
            room = 0;
        }

        return InscriberBatch.size(
                1 + this.upgrades.getInstalledPoints(CardTraits.CAPACITY),
                this.input.getStackInSlot(0).getCount(),
                plates(consumesPlates, this.top),
                plates(consumesPlates, this.bottom),
                room,
                result.getCount());
    }

    private static int plates(final boolean consumed, final AppEngInternalInventory side) {
        final ItemStack plate = side.getStackInSlot(0);
        return consumed && !plate.isEmpty() ? plate.getCount() : InscriberBatch.UNLIMITED;
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(1, 20, !this.hasWork(), false);
    }

    private boolean hasWork() {
        final IInscriberRecipe recipe = this.recipe();
        return recipe != null && this.batchSize(recipe) > 0 || this.hasExportWork() || !this.legacyPending.isEmpty();
    }

    private boolean hasExportWork() {
        return this.settings.getSetting(Settings.AUTO_EXPORT) == YesNo.YES && !this.output.getStackInSlot(0).isEmpty();
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (!this.legacyPending.isEmpty()) {
            this.legacyPending = this.output.insertItem(0, this.legacyPending, false);
        }

        final IInscriberRecipe recipe = this.recipe();
        final int batch = recipe == null ? 0 : this.batchSize(recipe);
        boolean worked = false;

        if (batch <= 0) {
            this.processingTime = 0;
        } else {
            final AE2StuffConfig config = AE2StuffConfig.instance();
            final int speed = UpgradeSpeedCalculations.linearSpeed(this.upgrades.getInstalledPoints(CardTraits.SPEED));
            final int ticks = Math.max(1, ticksSinceLastCall);
            final double power = InscriberBatch.powerPerTick(config.getAdvancedInscriberCyclePower(),
                    config.getAdvancedInscriberCycleTicks(), speed, batch) * ticks;

            if (this.extractPower(power)) {
                worked = true;
                this.processingTime = (int) Math.min((long) this.processingTime + (long) speed * ticks, Integer.MAX_VALUE);

                if (this.processingTime >= config.getAdvancedInscriberCycleTicks()) {
                    this.press(recipe, batch);
                    this.processingTime = 0;
                }
            }
        }

        final boolean exported = this.pushOutResult();
        this.setWorking(worked);
        if (worked || exported) {
            this.saveChanges();
        }

        if (this.hasWork() && (batch > 0 || !this.legacyPending.isEmpty())) {
            return TickRateModulation.URGENT;
        }
        return this.hasExportWork() ? TickRateModulation.SLOWER : TickRateModulation.SLEEP;
    }

    private void press(final IInscriberRecipe recipe, final int batch) {
        final ItemStack result = recipe.getOutput().copy();
        result.setCount(result.getCount() * batch);

        if (!this.output.insertItem(0, result, true).isEmpty()) {
            return;
        }

        this.input.extractItem(0, batch, false);
        if (recipe.getProcessType() == InscriberProcessType.PRESS) {
            this.top.extractItem(0, batch, false);
            this.bottom.extractItem(0, batch, false);
        }
        this.output.insertItem(0, result, false);
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
     * Hands the result to whatever sits against the inscriber, a face at a time. With separate sides the top
     * and bottom faces belong to the plates, and hand out nothing.
     *
     * @return true if anything moved
     */
    private boolean pushOutResult() {
        if (!this.hasExportWork()) {
            return false;
        }

        final EnumSet<EnumFacing> faces = EnumSet.allOf(EnumFacing.class);
        if (this.isSeparateSides()) {
            faces.remove(EnumFacing.UP);
            faces.remove(EnumFacing.DOWN);
        }

        for (final EnumFacing dir : faces) {
            final TileEntity neighbour = this.world.getTileEntity(this.pos.offset(dir));
            final InventoryAdaptor target = neighbour == null ? null : InventoryAdaptor.getAdaptor(neighbour, dir.getOpposite());
            if (target == null) {
                continue;
            }

            final ItemStack result = this.output.getStackInSlot(0);
            // Asked before anything is taken out, so a refusing neighbour costs no inventory change
            final int movable = result.getCount() - target.simulateAdd(result.copy()).getCount();
            if (movable <= 0) {
                continue;
            }

            final ItemStack leftOver = target.addItems(this.output.extractItem(0, movable, false));
            if (!leftOver.isEmpty()) {
                this.output.insertItem(0, leftOver, false);
            }
            return true;
        }
        return false;
    }

    private void setWorking(final boolean working) {
        if (this.working != working) {
            this.working = working;
            this.markForUpdate();
        }
    }

    private void wake() {
        try {
            this.getProxy().getTick().wakeDevice(this.getProxy().getNode());
        } catch (final GridAccessException e) {
            // Not on a network yet; ticking starts when it joins one
        }
    }

    @Nullable
    private IInscriberRecipe recipe() {
        if (!this.recipeKnown) {
            this.cachedRecipe = this.findRecipe();
            this.recipeKnown = true;
        }
        return this.cachedRecipe;
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc, final ItemStack removed, final ItemStack added) {
        this.recipeKnown = false;
        // A different item throws away progress; topping the same item up does not
        if (inv == this.input && mc == InvOperation.SET) {
            this.processingTime = 0;
        }
        this.wake();
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        if (settingName == Settings.INSCRIBER_INPUT_CAPACITY) {
            this.applyInputCapacity();
        }
        this.saveChanges();
        this.wake();
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.upgrades.writeToNBT(data, "upgrades");
        this.settings.writeToNBT(data);
        data.setInteger("processingTime", this.processingTime);
        if (!this.legacyPending.isEmpty()) {
            data.setTag("legacyPending", this.legacyPending.writeToNBT(new NBTTagCompound()));
        }
        return data;
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);

        if (data.hasKey("Items", Constants.NBT.TAG_LIST)) {
            this.readLegacy(data);
        } else {
            this.upgrades.readFromNBT(data, "upgrades");
            this.settings.readFromNBT(data);
            this.processingTime = data.getInteger("processingTime");
            this.legacyPending = new ItemStack(data.getCompoundTag("legacyPending"));
        }
        this.applyInputCapacity();
    }

    /**
     * An inscriber saved by the old mod. Its slots were top, middle, bottom and output; what it was pressing
     * had already left the input, so that result is handed out rather than pressed again.
     */
    private void readLegacy(final NBTTagCompound data) {
        final NBTTagList items = data.getTagList("Items", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < items.tagCount(); i++) {
            final NBTTagCompound tag = items.getCompoundTagAt(i);
            final ItemStack stack = new ItemStack(tag);
            switch (tag.getByte("Slot")) {
                case 0 -> this.top.setStackInSlot(0, stack);
                case 1 -> this.input.setStackInSlot(0, stack);
                case 2 -> this.bottom.setStackInSlot(0, stack);
                case 3 -> this.output.setStackInSlot(0, stack);
                default -> {
                }
            }
        }

        final NBTTagList cards = data.getTagList("upgrades", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < cards.tagCount(); i++) {
            final NBTTagCompound tag = cards.getCompoundTagAt(i);
            final int slot = tag.getByte("Slot");
            if (slot >= 0 && slot < UPGRADE_SLOTS) {
                this.upgrades.setStackInSlot(slot, new ItemStack(tag));
            }
        }

        if (data.hasKey("output", Constants.NBT.TAG_COMPOUND)) {
            this.legacyPending = new ItemStack(data.getCompoundTag("output"));
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
        if (!this.legacyPending.isEmpty()) {
            drops.add(this.legacyPending);
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
        if (!this.isSeparateSides()) {
            return this.combinedExtern;
        }
        return switch (side) {
            case UP -> this.topExtern;
            case DOWN -> this.bottomExtern;
            default -> this.sideExtern;
        };
    }

    @Override
    public IItemHandler getInventoryByName(final String name) {
        return switch (name) {
            case "upgrades" -> this.upgrades;
            case "top" -> this.top;
            case "bottom" -> this.bottom;
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

    /** Plates go in from anywhere, and come back out only through a face of their own. */
    private final class PlateFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(final IItemHandler inv, final int slot, final int amount) {
            return TileAdvancedInscriber.this.isSeparateSides();
        }

        @Override
        public boolean allowInsert(final IItemHandler inv, final int slot, final ItemStack stack) {
            return isPlate(stack);
        }
    }

    private static final class InputFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(final IItemHandler inv, final int slot, final int amount) {
            return false;
        }

        @Override
        public boolean allowInsert(final IItemHandler inv, final int slot, final ItemStack stack) {
            return true;
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
