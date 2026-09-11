/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.core;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import ae2stuff.client.gui.GuiGrowthChamber;
import ae2stuff.container.ContainerGrowthChamber;
import ae2stuff.tile.TileGrowthChamber;
import appeng.container.AEBaseContainer;
import appeng.container.ContainerOpenContext;

public final class ModGuiHandler implements IGuiHandler {

    public static final int GROWTH_CHAMBER = 0;

    @Nullable
    @Override
    public Object getServerGuiElement(final int id, final EntityPlayer player, final World world, final int x, final int y, final int z) {
        final TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
        if (id == GROWTH_CHAMBER && tile instanceof TileGrowthChamber chamber) {
            return withContext(new ContainerGrowthChamber(player.inventory, chamber), world, x, y, z);
        }
        return null;
    }

    @Nullable
    @Override
    public Object getClientGuiElement(final int id, final EntityPlayer player, final World world, final int x, final int y, final int z) {
        final TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
        if (id == GROWTH_CHAMBER && tile instanceof TileGrowthChamber chamber) {
            return new GuiGrowthChamber(withContext(new ContainerGrowthChamber(player.inventory, chamber), world, x, y, z));
        }
        return null;
    }

    /**
     * What AE2UD's own screens are given when they open, so a container behaves the same whoever opened it.
     */
    private static <T extends AEBaseContainer> T withContext(final T container, final World world, final int x, final int y, final int z) {
        final ContainerOpenContext context = new ContainerOpenContext(container.getTarget());
        context.setWorld(world);
        context.setX(x);
        context.setY(y);
        context.setZ(z);
        container.setOpenContext(context);
        return container;
    }
}
