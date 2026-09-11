/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.tile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import ae2stuff.Tags;
import ae2stuff.core.AE2StuffConfig;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;

/**
 * Links to many connectors at once. It never ticks: each connector keeps its own link alive, and the hub only
 * remembers which connectors are paired with it, so it knows how full it is.
 */
public final class TileWirelessHub extends TileWirelessBase {

    public static final ResourceLocation CHANNEL_TIER = new ResourceLocation(Tags.MOD_ID, "wireless_hub");

    private static final int LEGACY_LINKS = 32;

    private final Set<BlockPos> connectors = new LinkedHashSet<>();
    @Nullable
    private List<BlockPos> clientTargets;

    public TileWirelessHub() {
        super(CHANNEL_TIER);
    }

    public static int getMaxConnections() {
        return AE2StuffConfig.instance().getWirelessHubMaxConnections();
    }

    @Override
    public Collection<BlockPos> getLinkTargets() {
        if (this.world != null && this.world.isRemote) {
            return this.clientTargets == null ? Collections.emptyList() : this.clientTargets;
        }
        return Collections.unmodifiableSet(this.connectors);
    }

    @Override
    protected void setClientLinkTargets(final List<BlockPos> targets) {
        this.clientTargets = targets;
    }

    @Override
    public boolean acceptsLinkFrom(final BlockPos connector) {
        return true;
    }

    @Override
    public boolean hasRoomFor(final BlockPos connector) {
        return this.connectors.contains(connector) || this.getLinkCount() < getMaxConnections();
    }

    public boolean hasRoom() {
        return this.getLinkCount() < getMaxConnections();
    }

    /**
     * Connectors paired with this hub, after dropping the loaded ones that no longer are.
     */
    public int getLinkCount() {
        this.prune();
        return this.connectors.size();
    }

    private void prune() {
        boolean changed = false;
        for (final BlockPos at : new ArrayList<>(this.connectors)) {
            if (this.world.isBlockLoaded(at)
                    && !(this.loadedPeer(at) instanceof TileWirelessConnector connector && this.pos.equals(connector.getTarget()))) {
                this.connectors.remove(at);
                changed = true;
            }
        }
        if (changed) {
            this.saveChanges();
            this.markForUpdate();
        }
    }

    public void addConnector(final BlockPos connector) {
        if (this.connectors.add(connector)) {
            this.saveChanges();
            this.markForUpdate();
        }
    }

    @Override
    void onLinked(final BlockPos connector) {
        this.addConnector(connector);
        super.onLinked(connector);
    }

    @Override
    void forget(final BlockPos other) {
        if (this.connectors.remove(other)) {
            this.saveChanges();
            this.markForUpdate();
        }
        this.refresh();
    }

    @Override
    public void unpairAll() {
        final IGridNode node = this.getProxy().getNode();
        for (final BlockPos at : new ArrayList<>(this.connectors)) {
            final TileWirelessBase peer = this.loadedPeer(at);
            if (peer == null) {
                continue;
            }
            final IGridNode peerNode = peer.getProxy().getNode();
            if (node != null && peerNode != null) {
                final IGridConnection connection = connectionBetween(node, peerNode);
                if (connection != null) {
                    connection.destroy();
                }
            }
            peer.forget(this.pos);
        }

        this.connectors.clear();
        this.saveChanges();
        this.markForUpdate();
        this.refresh();
    }

    @Override
    protected double linkPower(final double distance) {
        final AE2StuffConfig config = AE2StuffConfig.instance();
        return WirelessLink.power(config.getWirelessHubPowerBase(), config.getWirelessHubPowerDistanceMultiplier(), distance);
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        final NBTTagList list = new NBTTagList();
        for (final BlockPos connector : this.connectors) {
            list.appendTag(NBTUtil.createPosTag(connector));
        }
        data.setTag("connectors", list);
        return data;
    }

    @Override
    protected void readLinks(final NBTTagCompound data) {
        this.connectors.clear();
        final NBTTagList list = data.getTagList("connectors", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            this.connectors.add(NBTUtil.getPosFromTag(list.getCompoundTagAt(i)));
        }
    }

    @Override
    protected void readLegacyLinks(final NBTTagCompound data) {
        this.connectors.clear();
        for (int i = 1; i <= LEGACY_LINKS; i++) {
            final BlockPos connector = readLegacyPos(data, "link" + i);
            if (connector != null) {
                this.connectors.add(connector);
            }
        }
    }
}
