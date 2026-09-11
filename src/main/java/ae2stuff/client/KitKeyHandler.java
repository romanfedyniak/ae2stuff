/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.client;

import javax.annotation.Nullable;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;

import ae2stuff.Tags;
import ae2stuff.network.ModNetwork;
import ae2stuff.network.PacketKitKey;

/**
 * The Wireless Setup Kit's key. Left Alt by default, since Left Control is sprint.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID, value = Side.CLIENT)
public final class KitKeyHandler {

    @Nullable
    private static KeyBinding key;
    private static volatile boolean sentHeld;

    private KitKeyHandler() {
    }

    static void register() {
        key = new KeyBinding("key.ae2stuff.wireless_kit", KeyConflictContext.IN_GAME, Keyboard.KEY_LMENU, "key.categories.ae2stuff");
        ClientRegistry.registerKeyBinding(key);
    }

    public static String keyName() {
        return key == null ? "?" : key.getDisplayName();
    }

    @SubscribeEvent
    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || key == null || Minecraft.getMinecraft().player == null) {
            return;
        }

        final boolean held = key.isKeyDown();
        if (held != sentHeld) {
            sentHeld = held;
            ModNetwork.CHANNEL.sendToServer(new PacketKitKey(held));
        }
    }

    @SubscribeEvent
    public static void onDisconnect(final FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        // A new server has not been told anything yet
        sentHeld = false;
    }
}
