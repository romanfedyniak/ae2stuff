/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.network;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import ae2stuff.client.gui.GuiWirelessKit;

/**
 * The devices an action got done with, which go back to the manager's list; the rest stay where they are.
 */
public final class PacketKitReturned implements IMessage {

    private List<BlockPos> positions;

    public PacketKitReturned() {
    }

    public PacketKitReturned(final List<BlockPos> positions) {
        this.positions = positions;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.positions = PacketKitAction.readPositions(buf);
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        PacketKitAction.writePositions(buf, this.positions);
    }

    public static final class Handler implements IMessageHandler<PacketKitReturned, IMessage> {

        @Override
        public IMessage onMessage(final PacketKitReturned message, final MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> GuiWirelessKit.returned(message.positions));
            return null;
        }
    }
}
