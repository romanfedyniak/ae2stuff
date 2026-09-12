/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import ae2stuff.Tags;

public final class ModNetwork {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(Tags.MOD_ID);

    private ModNetwork() {
    }

    public static void init() {
        CHANNEL.registerMessage(PacketKitKey.Handler.class, PacketKitKey.class, 0, Side.SERVER);
        CHANNEL.registerMessage(PacketKitAction.Handler.class, PacketKitAction.class, 1, Side.SERVER);
        CHANNEL.registerMessage(PacketKitDevices.Handler.class, PacketKitDevices.class, 2, Side.CLIENT);
        CHANNEL.registerMessage(PacketKitReturned.Handler.class, PacketKitReturned.class, 3, Side.CLIENT);
        CHANNEL.registerMessage(PacketKitEdit.Handler.class, PacketKitEdit.class, 4, Side.SERVER);
    }
}
