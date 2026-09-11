/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.client;

import java.util.Locale;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import ae2stuff.Tags;
import ae2stuff.block.BlockWireless;
import ae2stuff.core.Registration;
import ae2stuff.integration.visualiser.WirelessVisualiserProvider;
import appeng.api.client.NetworkVisualiserStyles;
import appeng.api.client.VisualiserStyle;
import appeng.api.util.AEColor;

@Mod.EventBusSubscriber(modid = Tags.MOD_ID, value = Side.CLIENT)
public final class ClientRegistration {

    private ClientRegistration() {
    }

    @SubscribeEvent
    public static void registerModels(final ModelRegistryEvent event) {
        for (final Block block : new Block[] { Registration.growthChamber, Registration.advancedInscriber }) {
            if (block != null) {
                ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), 0,
                        new ModelResourceLocation(block.getRegistryName(), "inventory"));
            }
        }

        for (final BlockWireless block : new BlockWireless[] { Registration.wirelessConnector, Registration.wirelessHub }) {
            if (block != null) {
                final Item item = Item.getItemFromBlock(block);
                for (int meta = 0; meta < AEColor.values().length; meta++) {
                    final String color = BlockWireless.colorOf(meta).name().toLowerCase(Locale.ROOT);
                    ModelLoader.setCustomModelResourceLocation(item, meta, new ModelResourceLocation(
                            new ResourceLocation(Tags.MOD_ID, block.getRegistryName().getPath() + "/" + color), "inventory"));
                }
            }
        }

        if (Registration.wirelessKit != null) {
            ModelLoader.setCustomModelResourceLocation(Registration.wirelessKit, 0,
                    new ModelResourceLocation(Registration.wirelessKit.getRegistryName(), "inventory"));
        }
    }

    public static void registerVisualiserStyles() {
        for (final AEColor color : AEColor.values()) {
            NetworkVisualiserStyles.register(WirelessVisualiserProvider.styleFor(color),
                    new VisualiserStyle(0xFF000000 | color.mediumVariant));
        }
    }
}
