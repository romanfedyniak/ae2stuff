/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.integration.waila;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import ae2stuff.tile.TileGrowthChamber;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.IWailaRegistrar;
import mcp.mobius.waila.api.WailaPlugin;

@WailaPlugin
public final class AE2StuffWailaPlugin implements IWailaPlugin, IWailaDataProvider {

    private static final String GROWING = "ae2stuffGrowing";

    @Override
    public void register(final IWailaRegistrar registrar) {
        registrar.registerBodyProvider(this, TileGrowthChamber.class);
        registrar.registerNBTProvider(this, TileGrowthChamber.class);
    }

    @Nonnull
    @Override
    public List<String> getWailaBody(final ItemStack itemStack, final List<String> tooltip, final IWailaDataAccessor accessor,
            final IWailaConfigHandler config) {
        if (accessor.getNBTData().hasKey(GROWING)) {
            tooltip.add(new TextComponentTranslation("waila.ae2stuff.grower.growing", accessor.getNBTData().getInteger(GROWING))
                    .getFormattedText());
        }
        return tooltip;
    }

    @Nonnull
    @Override
    public NBTTagCompound getNBTData(final EntityPlayerMP player, final TileEntity tile, final NBTTagCompound tag,
            final World world, final BlockPos pos) {
        if (tile instanceof TileGrowthChamber chamber) {
            tag.setInteger(GROWING, chamber.getGrowingStacks());
        }
        return tag;
    }
}
