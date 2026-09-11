/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.tile;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import ae2stuff.AE2Stuff;
import ae2stuff.Tags;
import ae2stuff.core.AE2StuffConfig;
import appeng.api.AEApi;
import appeng.api.exceptions.FailedConnectionException;
import appeng.api.exceptions.SecurityConnectionException;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.me.GridAccessException;

/**
 * One end of a link, paired with another connector or with a hub. The connector keeps the link alive: it makes the
 * link again whenever both ends are loaded and it is down.
 */
public final class TileWirelessConnector extends TileWirelessBase implements IGridTickable {

    public static final ResourceLocation CHANNEL_TIER = new ResourceLocation(Tags.MOD_ID, "wireless_connector");

    private static final int RETRY_TICKS = 5;
    private static final int CHECK_TICKS = 20;

    public enum LinkAttempt {
        LINKED,
        SECURITY,
        FAILED
    }

    @Nullable
    private BlockPos target;
    @Nullable
    private List<BlockPos> clientTargets;
    private boolean failureLogged;

    public TileWirelessConnector() {
        super(CHANNEL_TIER);
    }

    @Nullable
    public BlockPos getTarget() {
        return this.target;
    }

    @Override
    public Collection<BlockPos> getLinkTargets() {
        if (this.world != null && this.world.isRemote) {
            return this.clientTargets == null ? Collections.emptyList() : this.clientTargets;
        }
        return this.target == null ? Collections.emptyList() : Collections.singletonList(this.target);
    }

    @Override
    protected void setClientLinkTargets(final List<BlockPos> targets) {
        this.clientTargets = targets;
    }

    @Override
    public boolean acceptsLinkFrom(final BlockPos connector) {
        return connector.equals(this.target);
    }

    @Override
    public boolean hasRoomFor(final BlockPos connector) {
        return true;
    }

    /**
     * Pairs with another end, dropping whatever this was paired with. The pairing lasts until it is replaced or
     * either block is removed.
     */
    public void pair(final BlockPos other) {
        if (other.equals(this.target)) {
            return;
        }

        this.unpairAll();
        this.target = other;
        this.failureLogged = false;
        this.saveChanges();
        this.markForUpdate();
        this.wake();
    }

    @Override
    public void unpairAll() {
        if (this.target == null) {
            return;
        }

        final TileWirelessBase peer = this.loadedPeer(this.target);
        this.target = null;
        if (peer != null) {
            final IGridNode node = this.getProxy().getNode();
            final IGridNode peerNode = peer.getProxy().getNode();
            if (node != null && peerNode != null) {
                final var connection = connectionBetween(node, peerNode);
                if (connection != null) {
                    connection.destroy();
                }
            }
            peer.forget(this.pos);
        }

        this.saveChanges();
        this.markForUpdate();
        this.refresh();
    }

    @Override
    void forget(final BlockPos other) {
        if (other.equals(this.target)) {
            this.target = null;
            this.saveChanges();
            this.markForUpdate();
            this.refresh();
        }
    }

    /**
     * Makes the link at once, so the kit can say how it went.
     */
    public LinkAttempt connectNow() {
        final IGridNode node = this.getProxy().getNode();
        final TileWirelessBase peer = this.loadedPeer(this.target);
        final IGridNode peerNode = peer == null ? null : peer.getProxy().getNode();
        if (node == null || peerNode == null) {
            return LinkAttempt.FAILED;
        }
        if (connectionBetween(node, peerNode) != null) {
            peer.onLinked(this.pos);
            this.refresh();
            return LinkAttempt.LINKED;
        }

        try {
            AEApi.instance().grid().createGridConnection(node, peerNode);
        } catch (final SecurityConnectionException e) {
            return LinkAttempt.SECURITY;
        } catch (final FailedConnectionException e) {
            return LinkAttempt.FAILED;
        }
        peer.onLinked(this.pos);
        this.refresh();
        return LinkAttempt.LINKED;
    }

    /**
     * Naming a connector names the end it is paired with, as the old mod did.
     */
    @Override
    public void setCustomName(@Nullable final String customName) {
        super.setCustomName(customName);
        this.saveChanges();

        final TileWirelessBase peer = this.loadedPeer(this.target);
        if (peer != null && !Objects.equals(nameOf(this), nameOf(peer))) {
            peer.setCustomName(customName);
            peer.saveChanges();
        }
    }

    @Nullable
    private static String nameOf(final TileWirelessBase tile) {
        return tile.hasCustomInventoryName() ? tile.getCustomInventoryName() : null;
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(RETRY_TICKS, CHECK_TICKS, this.target == null, false);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        final boolean loaded = this.target != null && this.world.isBlockLoaded(this.target);
        final TileWirelessBase peer = loaded ? this.loadedPeer(this.target) : null;
        final IGridNode peerNode = peer == null ? null : peer.getProxy().getNode();
        final boolean linked = peerNode != null && connectionBetween(node, peerNode) != null;
        final boolean inRange = this.target != null
                && WirelessLink.inRange(AE2StuffConfig.instance().getWirelessMaxRange(), distance(this.pos, this.target));

        switch (WirelessLink.next(this.target != null, loaded, peer != null && peer.acceptsLinkFrom(this.pos), linked,
                inRange, peer != null && peer.hasRoomFor(this.pos), peerNode != null)) {
            case KEEP:
                this.failureLogged = false;
                peer.onLinked(this.pos);
                this.refresh();
                return TickRateModulation.IDLE;
            case CONNECT:
                return this.connect(node, peer, peerNode) ? TickRateModulation.IDLE : TickRateModulation.URGENT;
            case WAIT:
                this.refresh();
                return TickRateModulation.URGENT;
            case FORGET:
                this.target = null;
                this.saveChanges();
                this.markForUpdate();
                this.refresh();
                return TickRateModulation.SLEEP;
            case SLEEP:
            default:
                this.refresh();
                return TickRateModulation.SLEEP;
        }
    }

    private boolean connect(final IGridNode node, final TileWirelessBase peer, final IGridNode peerNode) {
        try {
            AEApi.instance().grid().createGridConnection(node, peerNode);
        } catch (final FailedConnectionException e) {
            // Retried quietly; logged once until it works again
            if (!this.failureLogged) {
                this.failureLogged = true;
                AE2Stuff.LOG.warn("Wireless link from {} to {} failed: {}", this.pos, peer.getPos(), e.getMessage());
            }
            return false;
        }

        this.failureLogged = false;
        peer.onLinked(this.pos);
        this.refresh();
        return true;
    }

    private void wake() {
        final IGridNode node = this.getProxy().getNode();
        if (node == null) {
            return;
        }
        try {
            this.getProxy().getTick().wakeDevice(node);
        } catch (final GridAccessException e) {
            // Not on a network yet; ticking starts when it joins one
        }
    }

    @Override
    public void onChunkUnload() {
        final TileWirelessBase peer = this.loadedPeer(this.target);
        super.onChunkUnload();
        if (peer != null) {
            peer.refresh();
        }
    }

    @Override
    protected double linkPower(final double distance) {
        final AE2StuffConfig config = AE2StuffConfig.instance();
        return WirelessLink.power(config.getWirelessPowerBase(), config.getWirelessPowerDistanceMultiplier(), distance);
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        if (this.target != null) {
            data.setTag("target", NBTUtil.createPosTag(this.target));
        }
        return data;
    }

    @Override
    protected void readLinks(final NBTTagCompound data) {
        this.target = data.hasKey("target", Constants.NBT.TAG_COMPOUND) ? NBTUtil.getPosFromTag(data.getCompoundTag("target")) : null;
    }

    @Override
    protected void readLegacyLinks(final NBTTagCompound data) {
        this.target = readLegacyPos(data, "link");
    }
}
