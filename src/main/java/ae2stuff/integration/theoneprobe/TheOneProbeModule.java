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
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import ae2stuff.Tags;
import ae2stuff.integration.WirelessColors;
import ae2stuff.tile.TileGrowthChamber;
import ae2stuff.tile.TileWirelessBase;
import ae2stuff.tile.TileWirelessConnector;
import ae2stuff.tile.TileWirelessHub;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.pathing.ChannelTiers;
import appeng.api.util.AEColor;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;
import mcjty.theoneprobe.api.TextStyleClass;

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
            info.text(TextStyleClass.INFO + key("top.ae2stuff.grower.growing") + " " + TextStyleClass.OK
                    + chamber.getGrowingStacks());
        } else if (tile instanceof TileWirelessBase wireless) {
            addWireless(info, wireless);
        }
    }

    private static void addWireless(final IProbeInfo info, final TileWirelessBase tile) {
        final boolean linked = tile.isLinked();
        if (tile instanceof TileWirelessHub hub) {
            final int links = hub.getLinkCount();
            final int max = TileWirelessHub.getMaxConnections();
            final TextStyleClass style = links < max ? TextStyleClass.OK : TextStyleClass.WARNING;
            info.text(TextStyleClass.INFO + key("top.ae2stuff.wireless.links") + " " + style + links + TextStyleClass.INFO + " / "
                    + style + max);
        } else if (tile instanceof TileWirelessConnector connector && connector.getTarget() != null) {
            final BlockPos target = connector.getTarget();
            info.text((linked ? TextStyleClass.OK : TextStyleClass.WARNING)
                    + key(linked ? "top.ae2stuff.wireless.linked" : "top.ae2stuff.wireless.waiting") + " " + target.getX() + ", "
                    + target.getY() + ", " + target.getZ());
        } else {
            info.text(TextStyleClass.WARNING + key("top.ae2stuff.wireless.unlinked"));
        }

        if (linked && AEConfig.instance().isFeatureEnabled(AEFeature.CHANNELS)) {
            final int capacity = ChannelTiers.capacityOf(tile.getChannelTier());
            info.text(TextStyleClass.INFO + key("top.ae2stuff.wireless.channels") + " " + TextStyleClass.OK + tile.getUsedChannels()
                    + (capacity < 0 ? "" : TextStyleClass.INFO + " / " + TextStyleClass.OK + capacity));
        }
        if (linked) {
            info.text(TextStyleClass.INFO + key("top.ae2stuff.wireless.power") + " " + TextStyleClass.OK
                    + String.format("%.1f", PowerMultiplier.CONFIG.multiply(tile.getPowerUse())) + " AE/t");
        }
        if (tile.hasCustomInventoryName()) {
            info.text(TextStyleClass.INFO + key("top.ae2stuff.wireless.name") + " " + TextStyleClass.OK
                    + tile.getCustomInventoryName());
        }
        if (tile.getColor() != AEColor.TRANSPARENT) {
            info.text(WirelessColors.of(tile.getColor()) + key(tile.getColor().unlocalizedName));
        }
    }

    private static String key(final String key) {
        return IProbeInfo.STARTLOC + key + IProbeInfo.ENDLOC;
    }
}
