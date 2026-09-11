/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.network;

import io.netty.buffer.ByteBuf;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import ae2stuff.item.KitKey;

/**
 * The Wireless Setup Kit's key went down or up. Sent only when it changes.
 */
public final class PacketKitKey implements IMessage {

    private boolean held;

    public PacketKitKey() {
    }

    public PacketKitKey(final boolean held) {
        this.held = held;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.held = buf.readBoolean();
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeBoolean(this.held);
    }

    public static final class Handler implements IMessageHandler<PacketKitKey, IMessage> {

        @Override
        public IMessage onMessage(final PacketKitKey message, final MessageContext ctx) {
            KitKey.set(ctx.getServerHandler().player, message.held);
            return null;
        }
    }
}
