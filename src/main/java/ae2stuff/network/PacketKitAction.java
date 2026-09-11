/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import ae2stuff.container.ContainerWirelessKit;

/**
 * A button pressed in the manager window, with the devices in its columns.
 */
public final class PacketKitAction implements IMessage {

    private static final int MAX_POSITIONS = 4096;

    public enum Action {
        LINK,
        UNLINK
    }

    private Action action;
    private List<BlockPos> sources;
    private List<BlockPos> targets;

    public PacketKitAction() {
    }

    public PacketKitAction(final Action action, final List<BlockPos> sources, final List<BlockPos> targets) {
        this.action = action;
        this.sources = sources;
        this.targets = targets;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        final int ordinal = buf.readByte();
        this.action = ordinal >= 0 && ordinal < Action.values().length ? Action.values()[ordinal] : null;
        this.sources = readPositions(buf);
        this.targets = readPositions(buf);
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeByte(this.action.ordinal());
        writePositions(buf, this.sources);
        writePositions(buf, this.targets);
    }

    static List<BlockPos> readPositions(final ByteBuf buf) {
        final int count = buf.readInt();
        if (count < 0 || count > MAX_POSITIONS) {
            return Collections.emptyList();
        }
        final List<BlockPos> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            positions.add(BlockPos.fromLong(buf.readLong()));
        }
        return positions;
    }

    static void writePositions(final ByteBuf buf, final List<BlockPos> positions) {
        buf.writeInt(positions.size());
        for (final BlockPos pos : positions) {
            buf.writeLong(pos.toLong());
        }
    }

    public static final class Handler implements IMessageHandler<PacketKitAction, IMessage> {

        @Override
        public IMessage onMessage(final PacketKitAction message, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!(player.openContainer instanceof ContainerWirelessKit kit) || message.action == null) {
                    return;
                }
                switch (message.action) {
                    case LINK -> kit.link(message.sources, message.targets);
                    case UNLINK -> kit.unlink(message.sources);
                }
            });
            return null;
        }
    }
}
