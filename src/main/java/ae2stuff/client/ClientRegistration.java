/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.client;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import ae2stuff.Tags;
import ae2stuff.core.Registration;

@Mod.EventBusSubscriber(modid = Tags.MOD_ID, value = Side.CLIENT)
public final class ClientRegistration {

    private ClientRegistration() {
    }

    @SubscribeEvent
    public static void registerModels(final ModelRegistryEvent event) {
        if (Registration.growthChamber != null) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(Registration.growthChamber), 0,
                    new ModelResourceLocation(Registration.growthChamber.getRegistryName(), "inventory"));
        }
    }
}
