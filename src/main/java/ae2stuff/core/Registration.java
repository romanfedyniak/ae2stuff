/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.core;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

import ae2stuff.Tags;
import ae2stuff.block.BlockGrowthChamber;
import ae2stuff.tile.TileGrowthChamber;
import appeng.api.AEApi;
import appeng.api.upgrades.CardTraits;
import appeng.api.upgrades.IUpgradeRegistry;
import appeng.block.AEBaseItemBlock;
import appeng.core.features.ActivityState;
import appeng.core.features.BlockStackSrc;
import appeng.tile.AEBaseTile;

/**
 * The blocks and items of every machine the config leaves switched on. A machine switched off is never
 * registered, so it leaves nothing behind in the registries.
 */
@Mod.EventBusSubscriber(modid = Tags.MOD_ID)
public final class Registration {

    public static final String GROWTH_CHAMBER = "grower";

    @Nullable
    public static BlockGrowthChamber growthChamber;

    private Registration() {
    }

    @SubscribeEvent
    public static void registerBlocks(final RegistryEvent.Register<Block> event) {
        if (AE2StuffConfig.instance().isGrowthChamberEnabled()) {
            growthChamber = new BlockGrowthChamber();
            growthChamber.setRegistryName(Tags.MOD_ID, GROWTH_CHAMBER);
            growthChamber.setTranslationKey(Tags.MOD_ID + "." + GROWTH_CHAMBER);
            event.getRegistry().register(growthChamber);

            GameRegistry.registerTileEntity(TileGrowthChamber.class, new ResourceLocation(Tags.MOD_ID, GROWTH_CHAMBER));
            AEBaseTile.registerTileItem(TileGrowthChamber.class, new BlockStackSrc(growthChamber, 0, ActivityState.Enabled));
        }
    }

    @SubscribeEvent
    public static void registerItems(final RegistryEvent.Register<Item> event) {
        if (growthChamber != null) {
            event.getRegistry().register(new AEBaseItemBlock(growthChamber).setRegistryName(growthChamber.getRegistryName()));
        }
    }

    public static void registerUpgrades() {
        final IUpgradeRegistry upgrades = AEApi.instance().registries().upgrades();
        final AE2StuffConfig config = AE2StuffConfig.instance();

        if (growthChamber != null) {
            final ItemStack chamber = new ItemStack(growthChamber);
            if (config.getGrowthChamberSpeedCards() > 0) {
                upgrades.addTraitSupport(CardTraits.SPEED, chamber, config.getGrowthChamberSpeedCards());
            }
            if (config.getGrowthChamberSpeedPoints() > 0) {
                upgrades.setTraitLimit(CardTraits.SPEED, chamber, config.getGrowthChamberSpeedPoints());
            }
            upgrades.addTraitSupport(CardTraits.REDSTONE, chamber, 1);
        }
    }
}
