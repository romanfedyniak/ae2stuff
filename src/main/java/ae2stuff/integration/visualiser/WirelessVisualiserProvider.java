/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.integration.visualiser;

import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

import ae2stuff.Tags;
import ae2stuff.tile.TileWirelessBase;
import ae2stuff.tile.TileWirelessConnector;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.visualiser.INetworkVisualiserProvider;
import appeng.api.util.AEColor;

/**
 * Wireless links are real grid connections, so AE2UD's Network Visualiser already draws them; this only draws
 * them in the connector's colour.
 */
public final class WirelessVisualiserProvider implements INetworkVisualiserProvider {

    private static final ResourceLocation[] STYLES = new ResourceLocation[AEColor.values().length];

    static {
        for (final AEColor color : AEColor.values()) {
            STYLES[color.ordinal()] = new ResourceLocation(Tags.MOD_ID, "wireless/" + color.name().toLowerCase(Locale.ROOT));
        }
    }

    public static ResourceLocation styleFor(final AEColor color) {
        return STYLES[color.ordinal()];
    }

    @Nullable
    @Override
    public ResourceLocation styleOf(final IGridConnection connection) {
        if (!connection.hasDirection() && connection.a().getMachine() instanceof TileWirelessBase a
                && connection.b().getMachine() instanceof TileWirelessBase b) {
            return styleFor(a instanceof TileWirelessConnector ? a.getColor() : b.getColor());
        }
        return null;
    }
}
