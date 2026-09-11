/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.block;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.google.common.base.Optional;

import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyHelper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;

import ae2stuff.tile.TileWirelessBase;
import ae2stuff.tile.TileWirelessConnector;
import ae2stuff.tile.TileWirelessHub;
import appeng.api.networking.pathing.ChannelTiers;
import appeng.api.util.AEColor;
import appeng.block.AEBaseTileBlock;
import appeng.core.AEConfig;
import appeng.core.CreativeTab;
import appeng.core.features.AEFeature;
import appeng.core.localization.GuiText;
import appeng.util.Platform;

/**
 * The Wireless Connector and the Wireless Hub. The colour lives in the tile and travels as item metadata: 0 for
 * fluix, one more than the colour's ordinal otherwise.
 */
public final class BlockWireless extends AEBaseTileBlock {

    private static final PropertyBool ACTIVE = PropertyBool.create("active");
    private static final ColorProperty COLOR = new ColorProperty();

    /** In the order of {@link AEColor}. */
    private static final String[] DYES = { "dyeWhite", "dyeOrange", "dyeMagenta", "dyeLightBlue", "dyeYellow", "dyeLime",
            "dyePink", "dyeGray", "dyeLightGray", "dyeCyan", "dyePurple", "dyeBlue", "dyeBrown", "dyeGreen", "dyeRed",
            "dyeBlack" };

    /** The tile is already gone when a harvested block's drops are asked for, so its colour is carried across. */
    private static final ThreadLocal<AEColor> HARVESTED = new ThreadLocal<>();

    private final boolean hub;

    public BlockWireless(final Class<? extends TileWirelessBase> tile) {
        super(Material.IRON);
        this.hub = tile == TileWirelessHub.class;
        this.setHardness(1.0F);
        this.setHarvestLevel("pickaxe", 2);
        this.setCreativeTab(CreativeTab.instance);
        this.setTileEntity(tile);
        this.setHasSubtypes(true);
        this.setDefaultState(this.getDefaultState().withProperty(ACTIVE, false).withProperty(COLOR, AEColor.TRANSPARENT));
    }

    public static AEColor colorOf(final int meta) {
        return meta <= 0 || meta >= AEColor.values().length ? AEColor.TRANSPARENT : AEColor.values()[meta - 1];
    }

    public static int metaOf(final AEColor color) {
        return color == AEColor.TRANSPARENT ? 0 : color.ordinal() + 1;
    }

    @Override
    protected IProperty[] getAEStates() {
        return new IProperty[] { ACTIVE, COLOR };
    }

    @Override
    public IBlockState getActualState(final IBlockState state, final IBlockAccess world, final BlockPos pos) {
        final IBlockState actual = super.getActualState(state, world, pos);
        final TileWirelessBase tile = this.getTileEntity(world, pos);
        return tile == null ? actual : actual.withProperty(ACTIVE, tile.isLinked()).withProperty(COLOR, tile.getColor());
    }

    @Override
    public void onBlockPlacedBy(final World world, final BlockPos pos, final IBlockState state, final EntityLivingBase placer,
            final ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        final TileWirelessBase tile = this.getTileEntity(world, pos);
        if (tile != null) {
            tile.setPlacedColor(colorOf(stack.getMetadata()));
        }
    }

    @Override
    public boolean onActivated(final World world, final BlockPos pos, final EntityPlayer player, final EnumHand hand,
            final @Nullable ItemStack heldItem, final EnumFacing side, final float hitX, final float hitY, final float hitZ) {
        final AEColor dye = heldItem == null ? null : dyeColorOf(heldItem);
        final TileWirelessBase tile = this.getTileEntity(world, pos);
        if (dye == null || tile == null) {
            return false;
        }

        if (Platform.isServer() && tile.recolourBlock(side, dye, player) && !player.capabilities.isCreativeMode) {
            heldItem.shrink(1);
        }
        return true;
    }

    @Nullable
    private static AEColor dyeColorOf(final ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (final int id : OreDictionary.getOreIDs(stack)) {
            final String name = OreDictionary.getOreName(id);
            for (int i = 0; i < DYES.length; i++) {
                if (DYES[i].equals(name)) {
                    return AEColor.values()[i];
                }
            }
        }
        return null;
    }

    @Override
    public void breakBlock(final World world, final BlockPos pos, final IBlockState state) {
        final TileWirelessBase tile = this.getTileEntity(world, pos);
        if (tile != null && !world.isRemote) {
            tile.unpairAll();
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public void harvestBlock(final World world, final EntityPlayer player, final BlockPos pos, final IBlockState state,
            @Nullable final TileEntity te, final ItemStack stack) {
        HARVESTED.set(te instanceof TileWirelessBase tile ? tile.getColor() : null);
        try {
            super.harvestBlock(world, player, pos, state, te, stack);
        } finally {
            HARVESTED.remove();
        }
    }

    @Override
    public void getDrops(final NonNullList<ItemStack> drops, final IBlockAccess world, final BlockPos pos, final IBlockState state,
            final int fortune) {
        AEColor color = HARVESTED.get();
        if (color == null) {
            final TileEntity te = world.getTileEntity(pos);
            color = te instanceof TileWirelessBase tile ? tile.getColor() : AEColor.TRANSPARENT;
        }
        drops.add(new ItemStack(this, 1, metaOf(color)));
    }

    @Override
    public ItemStack getPickBlock(final IBlockState state, final RayTraceResult target, final World world, final BlockPos pos,
            final EntityPlayer player) {
        final TileWirelessBase tile = this.getTileEntity(world, pos);
        return new ItemStack(this, 1, tile == null ? 0 : metaOf(tile.getColor()));
    }

    @Override
    public void getSubBlocks(final CreativeTabs tab, final NonNullList<ItemStack> items) {
        for (int meta = 0; meta < AEColor.values().length; meta++) {
            items.add(new ItemStack(this, 1, meta));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(final ItemStack stack, @Nullable final World world, final List<String> tooltip,
            final ITooltipFlag flag) {
        tooltip.add(I18n.format(colorOf(stack.getMetadata()).unlocalizedName));

        if (AEConfig.instance().isFeatureEnabled(AEFeature.CHANNELS)) {
            final int capacity = ChannelTiers.capacityOf(this.hub ? TileWirelessHub.CHANNEL_TIER : TileWirelessConnector.CHANNEL_TIER);
            tooltip.add(capacity < 0 ? I18n.format("tooltip.ae2stuff.wireless.channels_unlimited")
                    : String.format(GuiText.ChannelCapacity.getLocal(), capacity));
        }
        if (this.hub) {
            tooltip.add(I18n.format("tooltip.ae2stuff.wireless_hub.links", TileWirelessHub.getMaxConnections()));
        }
    }

    private static final class ColorProperty extends PropertyHelper<AEColor> {

        private static final Collection<AEColor> VALUES = Arrays.asList(AEColor.values());

        private ColorProperty() {
            super("color", AEColor.class);
        }

        @Override
        public Collection<AEColor> getAllowedValues() {
            return VALUES;
        }

        @Override
        public Optional<AEColor> parseValue(final String value) {
            for (final AEColor color : VALUES) {
                if (this.getName(color).equals(value)) {
                    return Optional.of(color);
                }
            }
            return Optional.absent();
        }

        @Override
        public String getName(final AEColor value) {
            return value.name().toLowerCase(Locale.ROOT);
        }
    }
}
