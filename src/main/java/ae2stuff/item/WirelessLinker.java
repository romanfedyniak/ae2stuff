/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.item;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

import ae2stuff.core.AE2StuffConfig;
import ae2stuff.tile.TileWirelessBase;
import ae2stuff.tile.TileWirelessConnector;
import ae2stuff.tile.TileWirelessHub;
import ae2stuff.tile.WirelessLink;
import appeng.api.AEApi;
import appeng.api.networking.IGridNode;

/**
 * Links two ends for the kit, whichever mode it is in.
 */
public final class WirelessLinker {

    public enum Result {
        LINKED,
        NOT_ALLOWED,
        TWO_HUBS,
        HUB_FULL,
        TOO_FAR,
        SECURITY,
        FAILED
    }

    private WirelessLinker() {
    }

    public static Result link(final TileWirelessBase a, final TileWirelessBase b, final EntityPlayer player) {
        if (!a.canBeChangedBy(player) || !b.canBeChangedBy(player)) {
            return Result.NOT_ALLOWED;
        }
        if (a instanceof TileWirelessHub && b instanceof TileWirelessHub) {
            return Result.TWO_HUBS;
        }

        final TileWirelessConnector connector = a instanceof TileWirelessConnector c ? c : (TileWirelessConnector) b;
        final TileWirelessBase other = connector == a ? b : a;
        if (!other.hasRoomFor(connector.getPos())) {
            return Result.HUB_FULL;
        }
        if (!WirelessLink.inRange(AE2StuffConfig.instance().getWirelessMaxRange(), distance(connector, other))) {
            return Result.TOO_FAR;
        }

        // The player who links both ends owns them, as with the old kit
        final int playerId = AEApi.instance().registries().players().getID(player);
        for (final TileWirelessBase end : new TileWirelessBase[] { connector, other }) {
            final IGridNode node = end.getProxy().getNode();
            if (node != null) {
                node.setPlayerID(playerId);
            }
        }

        connector.pair(other.getPos());
        if (other instanceof TileWirelessConnector peer) {
            peer.pair(connector.getPos());
        } else {
            ((TileWirelessHub) other).addConnector(connector.getPos());
        }
        shareName(connector, other);

        return switch (connector.connectNow()) {
            case LINKED -> Result.LINKED;
            case SECURITY -> {
                connector.unpairAll();
                yield Result.SECURITY;
            }
            case FAILED -> {
                connector.unpairAll();
                yield Result.FAILED;
            }
        };
    }

    /**
     * Why a link was refused, for a message. Not for {@link Result#LINKED}.
     */
    public static ITextComponent describe(final Result result, final TileWirelessBase a, final TileWirelessBase b) {
        return switch (result) {
            case NOT_ALLOWED -> new TextComponentTranslation("chat.ae2stuff.wireless.security.player");
            case TWO_HUBS -> new TextComponentTranslation("chat.ae2stuff.wireless.two_hubs");
            case HUB_FULL -> new TextComponentTranslation("chat.ae2stuff.wireless.hub_full");
            case TOO_FAR -> new TextComponentTranslation("chat.ae2stuff.wireless.too_far", (int) Math.ceil(distance(a, b)),
                    AE2StuffConfig.instance().getWirelessMaxRange());
            case SECURITY -> new TextComponentTranslation("chat.ae2stuff.wireless.security.network");
            case LINKED, FAILED -> new TextComponentTranslation("chat.ae2stuff.wireless.failed");
        };
    }

    public static double distance(final TileWirelessBase a, final TileWirelessBase b) {
        final BlockPos from = a.getPos();
        final BlockPos to = b.getPos();
        return WirelessLink.distance(from.getX() - to.getX(), from.getY() - to.getY(), from.getZ() - to.getZ());
    }

    private static void shareName(final TileWirelessBase a, final TileWirelessBase b) {
        if (a.hasCustomInventoryName() && !b.hasCustomInventoryName()) {
            b.setName(a.getCustomInventoryName());
            b.saveChanges();
        } else if (b.hasCustomInventoryName() && !a.hasCustomInventoryName()) {
            a.setName(b.getCustomInventoryName());
            a.saveChanges();
        }
    }
}
