/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.tile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import appeng.api.config.SecurityPermissions;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.implementations.tiles.IColorableTile;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.util.AECableType;
import appeng.api.util.AEColor;
import appeng.api.util.AEPartLocation;
import appeng.me.GridAccessException;
import appeng.tile.grid.AENetworkTile;

/**
 * What the Wireless Connector and the Wireless Hub share: a real grid colour, the links drawn on the client, and
 * power drawn per live link.
 */
public abstract class TileWirelessBase extends AENetworkTile implements IColorableTile, IPowerChannelState {

    private AEColor color = AEColor.TRANSPARENT;
    private boolean linked;

    protected TileWirelessBase(final ResourceLocation channelTier) {
        this.getProxy().setChannelTier(channelTier);
        this.getProxy().setIdlePowerUsage(0);
    }

    /**
     * Where this end is paired to, loaded or not. On the client, what the overlay draws.
     */
    public abstract Collection<BlockPos> getLinkTargets();

    /**
     * Whether the connector at this position is still meant to link here.
     */
    public abstract boolean acceptsLinkFrom(BlockPos connector);

    /**
     * Whether a connector at this position could link here now.
     */
    public abstract boolean hasRoomFor(BlockPos connector);

    /**
     * Drops every pairing, at both ends, and takes the links down.
     */
    public abstract void unpairAll();

    /**
     * The other end dropped its pairing with this one.
     */
    abstract void forget(BlockPos other);

    protected abstract double linkPower(double distance);

    /**
     * A connector's link to this end came up or was checked.
     */
    void onLinked(final BlockPos connector) {
        this.refresh();
    }

    /**
     * Recounts the live links, which decide the power drawn and whether the block lights up.
     */
    public final void refresh() {
        if (this.world == null || this.world.isRemote) {
            return;
        }

        final IGridNode node = this.getProxy().getNode();
        boolean live = false;
        double power = 0;
        if (node != null) {
            for (final TileWirelessBase peer : this.livePeers(node)) {
                live = true;
                power += this.linkPower(distance(this.pos, peer.getPos()));
            }
        }

        if (this.getProxy().getIdlePowerUsage() != power) {
            this.getProxy().setIdlePowerUsage(power);
        }
        if (this.linked != live) {
            this.linked = live;
            this.markForUpdate();
        }
    }

    private List<TileWirelessBase> livePeers(final IGridNode node) {
        final List<TileWirelessBase> peers = new ArrayList<>();
        for (final IGridConnection connection : node.getConnections()) {
            if (!connection.hasDirection() && connection.getOtherSide(node).getMachine() instanceof TileWirelessBase peer
                    && this.getLinkTargets().contains(peer.getPos())) {
                peers.add(peer);
            }
        }
        return peers;
    }

    /**
     * Channels in use across this end's live links.
     */
    public int getUsedChannels() {
        final IGridNode node = this.getProxy().getNode();
        if (node == null) {
            return 0;
        }

        int used = 0;
        for (final IGridConnection connection : node.getConnections()) {
            if (!connection.hasDirection() && connection.getOtherSide(node).getMachine() instanceof TileWirelessBase peer
                    && this.getLinkTargets().contains(peer.getPos())) {
                used += connection.getUsedChannels();
            }
        }
        return used;
    }

    public boolean isLinked() {
        return this.linked;
    }

    public double getPowerUse() {
        return this.getProxy().getIdlePowerUsage();
    }

    public ResourceLocation getChannelTier() {
        return this.getProxy().getChannelTier();
    }

    public boolean canBeChangedBy(final EntityPlayer player) {
        try {
            return this.getProxy().getSecurity().hasPermission(player, SecurityPermissions.BUILD);
        } catch (final GridAccessException e) {
            return true;
        }
    }

    @Nullable
    protected final TileWirelessBase loadedPeer(@Nullable final BlockPos at) {
        if (at == null || !this.world.isBlockLoaded(at)) {
            return null;
        }
        final TileEntity tile = this.world.getTileEntity(at);
        return tile instanceof TileWirelessBase peer ? peer : null;
    }

    @Nullable
    protected static IGridConnection connectionBetween(final IGridNode node, final IGridNode other) {
        for (final IGridConnection connection : node.getConnections()) {
            if (connection.getOtherSide(node) == other) {
                return connection;
            }
        }
        return null;
    }

    protected static double distance(final BlockPos a, final BlockPos b) {
        return WirelessLink.distance(a.getX() - b.getX(), a.getY() - b.getY(), a.getZ() - b.getZ());
    }

    /**
     * The colour a placed block takes from its item, before it has a node.
     */
    public void setPlacedColor(final AEColor color) {
        this.color = color;
        this.getProxy().setColor(color);
    }

    @Override
    public AEColor getColor() {
        return this.color;
    }

    @Override
    public boolean recolourBlock(final EnumFacing side, final AEColor colour, final EntityPlayer who) {
        if (this.color == colour) {
            return false;
        }

        this.setPlacedColor(colour);
        final IGridNode node = this.getProxy().getNode();
        if (node != null) {
            node.updateState();
        }
        this.saveChanges();
        this.markForUpdate();
        return true;
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        data.setByte("color", (byte) this.color.ordinal());
        return data;
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        final boolean legacy = data.hasKey("Color", Constants.NBT.TAG_SHORT);
        if (legacy && data.hasKey("ae_node", Constants.NBT.TAG_COMPOUND)) {
            // The old mod saved the node in the format AE2UD reads, under its own name
            data.setTag("proxy", data.getTag("ae_node"));
        }

        super.readFromNBT(data);

        if (legacy) {
            this.setPlacedColor(colorAt(data.getShort("Color")));
            if (data.hasKey("CustomName", Constants.NBT.TAG_STRING)) {
                this.setName(data.getString("CustomName"));
            }
            this.readLegacyLinks(data);
        } else {
            this.setPlacedColor(colorAt(data.getByte("color")));
            this.readLinks(data);
        }
    }

    protected abstract void readLinks(NBTTagCompound data);

    /**
     * The links a block placed by the old mod had, which become pairings.
     */
    protected abstract void readLegacyLinks(NBTTagCompound data);

    @Nullable
    protected static BlockPos readLegacyPos(final NBTTagCompound data, final String key) {
        if (!data.hasKey(key, Constants.NBT.TAG_COMPOUND)) {
            return null;
        }
        final NBTTagCompound pos = data.getCompoundTag(key);
        return new BlockPos(pos.getInteger("x"), pos.getInteger("y"), pos.getInteger("z"));
    }

    private static AEColor colorAt(final int ordinal) {
        return ordinal >= 0 && ordinal < AEColor.values().length ? AEColor.values()[ordinal] : AEColor.TRANSPARENT;
    }

    @Override
    protected void writeToStream(final ByteBuf data) throws IOException {
        super.writeToStream(data);
        data.writeByte(this.color.ordinal());
        data.writeBoolean(this.linked);

        final Collection<BlockPos> targets = this.getLinkTargets();
        data.writeInt(targets.size());
        for (final BlockPos target : targets) {
            data.writeLong(target.toLong());
        }
    }

    @Override
    protected boolean readFromStream(final ByteBuf data) throws IOException {
        final boolean changed = super.readFromStream(data);
        final AEColor oldColor = this.color;
        final boolean wasLinked = this.linked;

        this.color = colorAt(data.readByte());
        this.linked = data.readBoolean();

        final int count = data.readInt();
        final List<BlockPos> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            targets.add(BlockPos.fromLong(data.readLong()));
        }
        this.setClientLinkTargets(targets);

        return changed || oldColor != this.color || wasLinked != this.linked;
    }

    protected abstract void setClientLinkTargets(List<BlockPos> targets);

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
}
