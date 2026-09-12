/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.container;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

/**
 * A row put forward for Link or Unlink, with which kinds of device a group row takes part with.
 */
public final class KitSelection {

    public final KitEntry entry;
    public final boolean connectors;
    public final boolean hubs;

    public KitSelection(final KitEntry entry, final boolean connectors, final boolean hubs) {
        this.entry = entry;
        this.connectors = connectors;
        this.hubs = hubs;
    }

    public void write(final ByteBuf buf) {
        this.entry.write(buf);
        buf.writeBoolean(this.connectors);
        buf.writeBoolean(this.hubs);
    }

    @Nullable
    public static KitSelection read(final ByteBuf buf) {
        final KitEntry entry = KitEntry.read(buf);
        final boolean connectors = buf.readBoolean();
        final boolean hubs = buf.readBoolean();
        return entry == null ? null : new KitSelection(entry, connectors, hubs);
    }
}
