/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.block;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import ae2stuff.AE2Stuff;
import ae2stuff.core.ModGuiHandler;
import ae2stuff.tile.TileGrowthChamber;
import appeng.api.upgrades.CardTraits;
import appeng.block.AEBaseTileBlock;
import appeng.core.CreativeTab;
import appeng.util.Platform;

public final class BlockGrowthChamber extends AEBaseTileBlock {

    private static final PropertyBool ACTIVE = PropertyBool.create("active");

    public BlockGrowthChamber() {
        super(Material.IRON);
        this.setHardness(1.0F);
        this.setHarvestLevel("pickaxe", 2);
        this.setCreativeTab(CreativeTab.instance);
        this.setTileEntity(TileGrowthChamber.class);
        this.setDefaultState(this.getDefaultState().withProperty(ACTIVE, false));
    }

    @Override
    public IBlockState getActualState(final IBlockState state, final IBlockAccess world, final BlockPos pos) {
        final TileGrowthChamber chamber = this.getTileEntity(world, pos);
        return super.getActualState(state, world, pos).withProperty(ACTIVE, chamber != null && chamber.isWorking());
    }

    @Override
    protected IProperty[] getAEStates() {
        return new IProperty[] { ACTIVE };
    }

    @Override
    public boolean onActivated(final World world, final BlockPos pos, final EntityPlayer player, final EnumHand hand,
            final @Nullable ItemStack heldItem, final EnumFacing side, final float hitX, final float hitY, final float hitZ) {
        if (player.isSneaking()) {
            return false;
        }

        if (Platform.isServer() && this.getTileEntity(world, pos) != null) {
            player.openGui(AE2Stuff.instance, ModGuiHandler.GROWTH_CHAMBER, world, pos.getX(), pos.getY(), pos.getZ());
        }
        return true;
    }

    @Override
    public void neighborChanged(final IBlockState state, final World world, final BlockPos pos, final Block block, final BlockPos fromPos) {
        final TileGrowthChamber chamber = this.getTileEntity(world, pos);
        if (chamber != null && chamber.isInstalled(CardTraits.REDSTONE)) {
            chamber.updateRedstoneState();
        }
    }
}
