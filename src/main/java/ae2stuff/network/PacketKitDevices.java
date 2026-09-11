/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.network;

import io.netty.buffer.ByteBuf;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import ae2stuff.client.gui.GuiWirelessKit;
import ae2stuff.container.KitManagerData;

/**
 * The devices the open manager window shows, sent on open, after an action and whenever they change.
 */
public final class PacketKitDevices implements IMessage {

    private KitManagerData data;

    public PacketKitDevices() {
    }

    public PacketKitDevices(final KitManagerData data) {
        this.data = data;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.data = KitManagerData.read(buf);
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        this.data.write(buf);
    }

    public static final class Handler implements IMessageHandler<PacketKitDevices, IMessage> {

        @Override
        public IMessage onMessage(final PacketKitDevices message, final MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> GuiWirelessKit.receive(message.data));
            return null;
        }
    }
}
