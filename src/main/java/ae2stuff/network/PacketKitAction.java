/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import ae2stuff.container.ContainerWirelessKit;
import ae2stuff.container.KitSelection;

/**
 * Link or Unlink pressed in the manager window, with the rows in its columns.
 */
public final class PacketKitAction implements IMessage {

    static final int MAX_ROWS = 4096;

    public enum Action {
        LINK,
        UNLINK
    }

    private Action action;
    private List<KitSelection> sources;
    private List<KitSelection> targets;

    public PacketKitAction() {
    }

    public PacketKitAction(final Action action, final List<KitSelection> sources, final List<KitSelection> targets) {
        this.action = action;
        this.sources = sources;
        this.targets = targets;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        final int ordinal = buf.readByte();
        this.action = ordinal >= 0 && ordinal < Action.values().length ? Action.values()[ordinal] : null;
        this.sources = readSelections(buf);
        this.targets = readSelections(buf);
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeByte(this.action.ordinal());
        writeSelections(buf, this.sources);
        writeSelections(buf, this.targets);
    }

    private static List<KitSelection> readSelections(final ByteBuf buf) {
        final int count = Math.max(0, Math.min(buf.readInt(), MAX_ROWS));
        final List<KitSelection> selections = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final KitSelection selection = KitSelection.read(buf);
            if (selection != null) {
                selections.add(selection);
            }
        }
        return selections;
    }

    private static void writeSelections(final ByteBuf buf, final List<KitSelection> selections) {
        buf.writeInt(selections.size());
        for (final KitSelection selection : selections) {
            selection.write(buf);
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
