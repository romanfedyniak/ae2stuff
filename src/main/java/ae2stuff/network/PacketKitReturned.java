/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import ae2stuff.client.gui.GuiWirelessKit;
import ae2stuff.container.KitEntry;

/**
 * The rows an action got done with, which go back to the manager's list; the rest stay where they are.
 */
public final class PacketKitReturned implements IMessage {

    private List<KitEntry> entries;

    public PacketKitReturned() {
    }

    public PacketKitReturned(final List<KitEntry> entries) {
        this.entries = entries;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        final int count = Math.max(0, Math.min(buf.readInt(), PacketKitAction.MAX_ROWS));
        this.entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final KitEntry entry = KitEntry.read(buf);
            if (entry != null) {
                this.entries.add(entry);
            }
        }
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(this.entries.size());
        for (final KitEntry entry : this.entries) {
            entry.write(buf);
        }
    }

    public static final class Handler implements IMessageHandler<PacketKitReturned, IMessage> {

        @Override
        public IMessage onMessage(final PacketKitReturned message, final MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> GuiWirelessKit.returned(message.entries));
            return null;
        }
    }
}
