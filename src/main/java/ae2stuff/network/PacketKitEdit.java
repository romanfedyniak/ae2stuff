/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.network;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import ae2stuff.container.ContainerWirelessKit;
import ae2stuff.container.KitEntry;
import ae2stuff.item.KitSettings;

/**
 * A change to how the manager window is set out, or to a row's name.
 */
public final class PacketKitEdit implements IMessage {

    public enum Action {
        GROUPING,
        HIDE_LINKED,
        PIN,
        UNPIN,
        RENAME,
        FORGET
    }

    private Action action;
    @Nullable
    private KitEntry entry;
    private int value;
    private String text = "";

    public PacketKitEdit() {
    }

    public PacketKitEdit(final Action action, @Nullable final KitEntry entry, final int value, final String text) {
        this.action = action;
        this.entry = entry;
        this.value = value;
        this.text = text;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        final int ordinal = buf.readByte();
        this.action = ordinal >= 0 && ordinal < Action.values().length ? Action.values()[ordinal] : null;
        this.entry = buf.readBoolean() ? KitEntry.read(buf) : null;
        this.value = buf.readInt();
        final String text = ByteBufUtils.readUTF8String(buf);
        this.text = text.length() > KitSettings.MAX_NAME_LENGTH ? text.substring(0, KitSettings.MAX_NAME_LENGTH) : text;
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeByte(this.action.ordinal());
        buf.writeBoolean(this.entry != null);
        if (this.entry != null) {
            this.entry.write(buf);
        }
        buf.writeInt(this.value);
        ByteBufUtils.writeUTF8String(buf, this.text);
    }

    public static final class Handler implements IMessageHandler<PacketKitEdit, IMessage> {

        @Override
        public IMessage onMessage(final PacketKitEdit message, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.openContainer instanceof ContainerWirelessKit kit && message.action != null) {
                    kit.edit(message.action, message.entry, message.value, message.text);
                }
            });
            return null;
        }
    }
}
