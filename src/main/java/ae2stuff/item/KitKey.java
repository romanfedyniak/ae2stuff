/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.item;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import ae2stuff.Tags;

/**
 * Which players hold the Wireless Setup Kit's key, as their clients last said. Written from the network thread.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class KitKey {

    private static final Set<UUID> HELD = ConcurrentHashMap.newKeySet();

    private KitKey() {
    }

    public static void set(final EntityPlayer player, final boolean held) {
        if (held) {
            HELD.add(player.getUniqueID());
        } else {
            HELD.remove(player.getUniqueID());
        }
    }

    public static boolean isHeld(final EntityPlayer player) {
        return HELD.contains(player.getUniqueID());
    }

    @SubscribeEvent
    public static void onLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        HELD.remove(event.player.getUniqueID());
    }
}
