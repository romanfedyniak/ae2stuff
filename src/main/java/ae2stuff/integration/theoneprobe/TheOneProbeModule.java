/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.integration.theoneprobe;

import javax.annotation.Nullable;

import com.google.common.base.Function;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import ae2stuff.Tags;
import ae2stuff.tile.TileGrowthChamber;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;

/**
 * Handed to The One Probe by name through IMC. The text is sent as a translation key, so each player reads
 * it in their own language rather than the server's.
 */
public final class TheOneProbeModule implements Function<ITheOneProbe, Void>, IProbeInfoProvider {

    @Nullable
    @Override
    public Void apply(@Nullable final ITheOneProbe probe) {
        if (probe != null) {
            probe.registerProvider(this);
        }
        return null;
    }

    @Override
    public String getID() {
        return Tags.MOD_ID + ":machines";
    }

    @Override
    public void addProbeInfo(final ProbeMode mode, final IProbeInfo info, final EntityPlayer player, final World world,
            final IBlockState state, final IProbeHitData data) {
        final TileEntity tile = world.getTileEntity(data.getPos());
        if (tile instanceof TileGrowthChamber chamber) {
            info.text(IProbeInfo.STARTLOC + "top.ae2stuff.grower.growing" + IProbeInfo.ENDLOC + " " + chamber.getGrowingStacks());
        }
    }
}
