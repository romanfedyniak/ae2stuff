/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.container;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;

import ae2stuff.item.KitNetworks;
import ae2stuff.item.KitSettings;
import ae2stuff.tile.TileWirelessBase;
import ae2stuff.tile.TileWirelessConnector;
import ae2stuff.tile.TileWirelessHub;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ChannelTiers;

/**
 * What the manager window shows: the listed networks that can be seen right now, every connector and hub on them,
 * and how the window was left.
 */
public final class KitManagerData {

    private static final int MAX_ENTRIES = 1 << 16;

    /** The block each shown network is remembered by. */
    public final List<BlockPos> networks;
    public final List<Device> devices;
    public final KitSettings.Grouping grouping;
    public final boolean hideLinked;
    public final List<KitEntry> pins;
    public final Map<KitEntry, String> names;

    private KitManagerData(final List<BlockPos> networks, final List<Device> devices, final KitSettings.Grouping grouping,
            final boolean hideLinked, final List<KitEntry> pins, final Map<KitEntry, String> names) {
        this.networks = networks;
        this.devices = devices;
        this.grouping = grouping;
        this.hideLinked = hideLinked;
        this.pins = pins;
        this.names = names;
    }

    public static KitManagerData collect(@Nullable final NBTTagCompound kitTag, final World world) {
        final NBTTagCompound tag = kitTag == null ? new NBTTagCompound() : kitTag;
        final int dimension = world.provider.getDimension();
        final List<BlockPos> networks = new ArrayList<>();
        final List<Device> devices = new ArrayList<>();
        final Set<BlockPos> seen = new HashSet<>();

        for (final KitNetworks.Anchor anchor : KitNetworks.read(tag)) {
            final IGrid grid = anchor.grid(world);
            if (grid == null) {
                continue;
            }

            final int network = networks.size();
            networks.add(anchor.pos);
            for (final Class<? extends IGridHost> type : Arrays.asList(TileWirelessConnector.class, TileWirelessHub.class)) {
                for (final IGridNode node : grid.getMachines(type)) {
                    if (node.getMachine() instanceof TileWirelessBase tile && seen.add(tile.getPos())) {
                        devices.add(Device.of(tile, network));
                    }
                }
            }
        }

        return new KitManagerData(networks, devices, KitSettings.grouping(tag), KitSettings.hideLinked(tag),
                KitSettings.pins(tag, dimension), KitSettings.names(tag, dimension));
    }

    public void write(final ByteBuf buf) {
        buf.writeInt(this.networks.size());
        for (final BlockPos network : this.networks) {
            buf.writeLong(network.toLong());
        }
        buf.writeInt(this.devices.size());
        for (final Device device : this.devices) {
            device.write(buf);
        }
        buf.writeByte(this.grouping.ordinal());
        buf.writeBoolean(this.hideLinked);
        buf.writeInt(this.pins.size());
        for (final KitEntry pin : this.pins) {
            pin.write(buf);
        }
        buf.writeInt(this.names.size());
        for (final Map.Entry<KitEntry, String> name : this.names.entrySet()) {
            name.getKey().write(buf);
            ByteBufUtils.writeUTF8String(buf, name.getValue());
        }
    }

    public static KitManagerData read(final ByteBuf buf) {
        final int networkCount = Math.min(buf.readInt(), MAX_ENTRIES);
        final List<BlockPos> networks = new ArrayList<>(networkCount);
        for (int i = 0; i < networkCount; i++) {
            networks.add(BlockPos.fromLong(buf.readLong()));
        }
        final int deviceCount = Math.min(buf.readInt(), MAX_ENTRIES);
        final List<Device> devices = new ArrayList<>(deviceCount);
        for (int i = 0; i < deviceCount; i++) {
            devices.add(Device.read(buf));
        }

        final int grouping = buf.readByte();
        final boolean hideLinked = buf.readBoolean();

        final int pinCount = Math.min(buf.readInt(), MAX_ENTRIES);
        final List<KitEntry> pins = new ArrayList<>(pinCount);
        for (int i = 0; i < pinCount; i++) {
            final KitEntry pin = KitEntry.read(buf);
            if (pin != null) {
                pins.add(pin);
            }
        }
        final int nameCount = Math.min(buf.readInt(), MAX_ENTRIES);
        final Map<KitEntry, String> names = new LinkedHashMap<>();
        for (int i = 0; i < nameCount; i++) {
            final KitEntry entry = KitEntry.read(buf);
            final String name = ByteBufUtils.readUTF8String(buf);
            if (entry != null) {
                names.put(entry, name);
            }
        }

        return new KitManagerData(networks, devices,
                grouping >= 0 && grouping < KitSettings.Grouping.values().length ? KitSettings.Grouping.values()[grouping]
                        : KitSettings.Grouping.SINGLE,
                hideLinked, pins, names);
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof KitManagerData other && this.networks.equals(other.networks) && this.devices.equals(other.devices)
                && this.grouping == other.grouping && this.hideLinked == other.hideLinked && this.pins.equals(other.pins)
                && this.names.equals(other.names);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.networks, this.devices, this.grouping, this.hideLinked, this.pins, this.names);
    }

    public static final class Device {

        public final BlockPos pos;
        public final boolean hub;
        public final int color;
        /** The block's own name, or empty for none. */
        public final String name;
        @Nullable
        public final BlockPos target;
        public final boolean live;
        public final int usedChannels;
        public final int capacity;
        public final int links;
        public final int maxLinks;
        public final double power;
        public final int network;

        private Device(final BlockPos pos, final boolean hub, final int color, final String name, @Nullable final BlockPos target,
                final boolean live, final int usedChannels, final int capacity, final int links, final int maxLinks, final double power,
                final int network) {
            this.pos = pos;
            this.hub = hub;
            this.color = color;
            this.name = name;
            this.target = target;
            this.live = live;
            this.usedChannels = usedChannels;
            this.capacity = capacity;
            this.links = links;
            this.maxLinks = maxLinks;
            this.power = power;
            this.network = network;
        }

        private static Device of(final TileWirelessBase tile, final int network) {
            final boolean hub = tile instanceof TileWirelessHub;
            return new Device(tile.getPos(), hub, tile.getColor().ordinal(),
                    tile.hasCustomInventoryName() ? tile.getCustomInventoryName() : "",
                    tile instanceof TileWirelessConnector connector ? connector.getTarget() : null, tile.isLinked(),
                    tile.getUsedChannels(), ChannelTiers.capacityOf(tile.getChannelTier()),
                    tile instanceof TileWirelessHub h ? h.getLinkCount() : 0, hub ? TileWirelessHub.getMaxConnections() : 0,
                    PowerMultiplier.CONFIG.multiply(tile.getPowerUse()), network);
        }

        private void write(final ByteBuf buf) {
            buf.writeLong(this.pos.toLong());
            buf.writeBoolean(this.hub);
            buf.writeByte(this.color);
            ByteBufUtils.writeUTF8String(buf, this.name);
            buf.writeBoolean(this.target != null);
            if (this.target != null) {
                buf.writeLong(this.target.toLong());
            }
            buf.writeBoolean(this.live);
            buf.writeInt(this.usedChannels);
            buf.writeInt(this.capacity);
            buf.writeInt(this.links);
            buf.writeInt(this.maxLinks);
            buf.writeDouble(this.power);
            buf.writeInt(this.network);
        }

        private static Device read(final ByteBuf buf) {
            final BlockPos pos = BlockPos.fromLong(buf.readLong());
            final boolean hub = buf.readBoolean();
            final int color = buf.readByte();
            final String name = ByteBufUtils.readUTF8String(buf);
            final BlockPos target = buf.readBoolean() ? BlockPos.fromLong(buf.readLong()) : null;
            return new Device(pos, hub, color, name, target, buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readDouble(), buf.readInt());
        }

        @Override
        public boolean equals(final Object o) {
            return o instanceof Device d && this.pos.equals(d.pos) && this.hub == d.hub && this.color == d.color
                    && this.name.equals(d.name) && Objects.equals(this.target, d.target) && this.live == d.live
                    && this.usedChannels == d.usedChannels && this.capacity == d.capacity && this.links == d.links
                    && this.maxLinks == d.maxLinks && Double.compare(this.power, d.power) == 0 && this.network == d.network;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.pos, this.color, this.name, this.target, this.live, this.usedChannels, this.links);
        }
    }
}
