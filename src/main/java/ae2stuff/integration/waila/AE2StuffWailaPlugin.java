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
import net.minecraftforge.common.util.Constants;

import ae2stuff.tile.TileGrowthChamber;
import ae2stuff.tile.TileWirelessBase;
import ae2stuff.tile.TileWirelessConnector;
import ae2stuff.tile.TileWirelessHub;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.pathing.ChannelTiers;
import appeng.api.util.AEColor;
import appeng.core.AEConfig;
import appeng.core.features.AEFeature;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.IWailaRegistrar;
import mcp.mobius.waila.api.WailaPlugin;

@WailaPlugin
public final class AE2StuffWailaPlugin implements IWailaPlugin, IWailaDataProvider {

    private static final String GROWING = "ae2stuffGrowing";
    private static final String WIRELESS = "ae2stuffWireless";

    @Override
    public void register(final IWailaRegistrar registrar) {
        registrar.registerBodyProvider(this, TileGrowthChamber.class);
        registrar.registerNBTProvider(this, TileGrowthChamber.class);
        registrar.registerBodyProvider(this, TileWirelessBase.class);
        registrar.registerNBTProvider(this, TileWirelessBase.class);
    }

    @Nonnull
    @Override
    public List<String> getWailaBody(final ItemStack itemStack, final List<String> tooltip, final IWailaDataAccessor accessor,
            final IWailaConfigHandler config) {
        final NBTTagCompound data = accessor.getNBTData();
        if (data.hasKey(GROWING)) {
            tooltip.add(line("waila.ae2stuff.grower.growing", data.getInteger(GROWING)));
        }
        if (data.hasKey(WIRELESS, Constants.NBT.TAG_COMPOUND) && accessor.getTileEntity() instanceof TileWirelessBase tile) {
            addWireless(tooltip, data.getCompoundTag(WIRELESS), tile);
        }
        return tooltip;
    }

    private static void addWireless(final List<String> tooltip, final NBTTagCompound data, final TileWirelessBase tile) {
        final boolean linked = data.getBoolean("linked");
        if (tile instanceof TileWirelessHub) {
            tooltip.add(line("waila.ae2stuff.wireless.links", data.getInteger("links"), data.getInteger("max")));
        } else if (data.hasKey("target", Constants.NBT.TAG_LONG)) {
            final BlockPos target = BlockPos.fromLong(data.getLong("target"));
            tooltip.add(line(linked ? "waila.ae2stuff.wireless.linked" : "waila.ae2stuff.wireless.waiting", target.getX(),
                    target.getY(), target.getZ()));
        } else {
            tooltip.add(line("waila.ae2stuff.wireless.unlinked"));
        }

        if (linked && AEConfig.instance().isFeatureEnabled(AEFeature.CHANNELS)) {
            final int used = data.getInteger("used");
            final int capacity = ChannelTiers.capacityOf(tile.getChannelTier());
            tooltip.add(capacity < 0 ? line("waila.ae2stuff.wireless.channels", used)
                    : line("waila.appliedenergistics2.Channels", used, capacity));
        }
        if (linked) {
            tooltip.add(line("waila.ae2stuff.wireless.power", String.format("%.1f", data.getDouble("power"))));
        }
        if (tile.getColor() != AEColor.TRANSPARENT) {
            tooltip.add(line(tile.getColor().unlocalizedName));
        }
    }

    private static String line(final String key, final Object... args) {
        return new TextComponentTranslation(key, args).getFormattedText();
    }

    @Nonnull
    @Override
    public NBTTagCompound getNBTData(final EntityPlayerMP player, final TileEntity tile, final NBTTagCompound tag,
            final World world, final BlockPos pos) {
        if (tile instanceof TileGrowthChamber chamber) {
            tag.setInteger(GROWING, chamber.getGrowingStacks());
        } else if (tile instanceof TileWirelessBase wireless) {
            final NBTTagCompound data = new NBTTagCompound();
            data.setBoolean("linked", wireless.isLinked());
            data.setInteger("used", wireless.getUsedChannels());
            data.setDouble("power", PowerMultiplier.CONFIG.multiply(wireless.getPowerUse()));
            if (wireless instanceof TileWirelessHub hub) {
                data.setInteger("links", hub.getLinkCount());
                data.setInteger("max", TileWirelessHub.getMaxConnections());
            } else if (wireless instanceof TileWirelessConnector connector && connector.getTarget() != null) {
                data.setLong("target", connector.getTarget().toLong());
            }
            tag.setTag(WIRELESS, data);
        }
        return tag;
    }
}
