/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.core;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import ae2stuff.Tags;
import appeng.api.AEApi;

/**
 * Items the old mod had and this one does not, turned into what replaces them when a world loads.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class LegacyRemaps {

    private static final ResourceLocation VISUALISER = new ResourceLocation(Tags.MOD_ID, "visualiser");
    private static final ResourceLocation ADVANCED_WIRELESS_KIT = new ResourceLocation(Tags.MOD_ID, "adv_wireless_kit");

    private LegacyRemaps() {
    }

    @SubscribeEvent
    public static void remapItems(final RegistryEvent.MissingMappings<Item> event) {
        for (final RegistryEvent.MissingMappings.Mapping<Item> mapping : event.getAllMappings()) {
            if (VISUALISER.equals(mapping.key)) {
                // AE2UD's visualiser can be switched off, and then the old one is simply dropped
                AEApi.instance().definitions().items().networkVisualiser().maybeItem().ifPresent(mapping::remap);
            } else if (ADVANCED_WIRELESS_KIT.equals(mapping.key) && Registration.wirelessKit != null) {
                mapping.remap(Registration.wirelessKit);
            }
        }
    }
}
