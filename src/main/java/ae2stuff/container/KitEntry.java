/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.container;

import java.util.Objects;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/**
 * One row of the manager: a device, a whole network, or the devices of one colour on a network.
 */
public final class KitEntry {

    public enum Kind {
        DEVICE,
        NETWORK,
        COLOR
    }

    public final Kind kind;
    /** The device's position, or the block its network is remembered by. */
    public final BlockPos pos;
    /** The colour's ordinal for a colour group, otherwise -1. */
    public final int color;

    private KitEntry(final Kind kind, final BlockPos pos, final int color) {
        this.kind = kind;
        this.pos = pos;
        this.color = color;
    }

    public static KitEntry device(final BlockPos pos) {
        return new KitEntry(Kind.DEVICE, pos, -1);
    }

    public static KitEntry network(final BlockPos anchor) {
        return new KitEntry(Kind.NETWORK, anchor, -1);
    }

    public static KitEntry color(final BlockPos anchor, final int color) {
        return new KitEntry(Kind.COLOR, anchor, color);
    }

    public boolean isGroup() {
        return this.kind != Kind.DEVICE;
    }

    public void write(final ByteBuf buf) {
        buf.writeByte(this.kind.ordinal());
        buf.writeLong(this.pos.toLong());
        buf.writeByte(this.color);
    }

    @Nullable
    public static KitEntry read(final ByteBuf buf) {
        final int kind = buf.readByte();
        final BlockPos pos = BlockPos.fromLong(buf.readLong());
        final int color = buf.readByte();
        return kind >= 0 && kind < Kind.values().length ? new KitEntry(Kind.values()[kind], pos, color) : null;
    }

    public NBTTagCompound toNBT(final int dimension) {
        final NBTTagCompound tag = new NBTTagCompound();
        tag.setByte("kind", (byte) this.kind.ordinal());
        tag.setInteger("x", this.pos.getX());
        tag.setInteger("y", this.pos.getY());
        tag.setInteger("z", this.pos.getZ());
        tag.setInteger("dim", dimension);
        tag.setByte("color", (byte) this.color);
        return tag;
    }

    @Nullable
    public static KitEntry fromNBT(final NBTTagCompound tag) {
        final int kind = tag.getByte("kind");
        if (kind < 0 || kind >= Kind.values().length) {
            return null;
        }
        return new KitEntry(Kind.values()[kind], new BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")),
                tag.getByte("color"));
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof KitEntry other && this.kind == other.kind && this.pos.equals(other.pos) && this.color == other.color;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.kind, this.pos, this.color);
    }
}
