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
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.registries.IForgeRegistry;

import ae2stuff.Tags;
import ae2stuff.block.BlockAdvancedInscriber;
import ae2stuff.block.BlockGrowthChamber;
import ae2stuff.tile.TileAdvancedInscriber;
import ae2stuff.tile.TileGrowthChamber;
import appeng.api.AEApi;
import appeng.api.upgrades.CardTrait;
import appeng.api.upgrades.CardTraits;
import appeng.api.upgrades.IUpgradeRegistry;
import appeng.block.AEBaseItemBlock;
import appeng.block.AEBaseTileBlock;
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
    public static final String ADVANCED_INSCRIBER = "inscriber";

    @Nullable
    public static BlockGrowthChamber growthChamber;
    @Nullable
    public static BlockAdvancedInscriber advancedInscriber;

    private Registration() {
    }

    @SubscribeEvent
    public static void registerBlocks(final RegistryEvent.Register<Block> event) {
        final AE2StuffConfig config = AE2StuffConfig.instance();

        if (config.isGrowthChamberEnabled()) {
            growthChamber = register(event.getRegistry(), new BlockGrowthChamber(), GROWTH_CHAMBER, TileGrowthChamber.class);
        }
        if (config.isAdvancedInscriberEnabled()) {
            advancedInscriber = register(event.getRegistry(), new BlockAdvancedInscriber(), ADVANCED_INSCRIBER, TileAdvancedInscriber.class);
        }
    }

    private static <T extends AEBaseTileBlock> T register(final IForgeRegistry<Block> registry, final T block, final String name,
            final Class<? extends TileEntity> tile) {
        block.setRegistryName(Tags.MOD_ID, name);
        block.setTranslationKey(Tags.MOD_ID + "." + name);
        registry.register(block);

        // The same id the old mod gave its tiles, so a machine it placed loads into this one
        GameRegistry.registerTileEntity(tile, new ResourceLocation(Tags.MOD_ID, name));
        AEBaseTile.registerTileItem(tile, new BlockStackSrc(block, 0, ActivityState.Enabled));
        return block;
    }

    @SubscribeEvent
    public static void registerItems(final RegistryEvent.Register<Item> event) {
        for (final Block block : new Block[] { growthChamber, advancedInscriber }) {
            if (block != null) {
                event.getRegistry().register(new AEBaseItemBlock(block).setRegistryName(block.getRegistryName()));
            }
        }
    }

    public static void registerUpgrades() {
        final IUpgradeRegistry upgrades = AEApi.instance().registries().upgrades();
        final AE2StuffConfig config = AE2StuffConfig.instance();

        if (growthChamber != null) {
            final ItemStack chamber = new ItemStack(growthChamber);
            support(upgrades, CardTraits.SPEED, chamber, config.getGrowthChamberSpeedCards(), config.getGrowthChamberSpeedPoints());
            upgrades.addTraitSupport(CardTraits.REDSTONE, chamber, 1);
        }

        if (advancedInscriber != null) {
            final ItemStack inscriber = new ItemStack(advancedInscriber);
            support(upgrades, CardTraits.SPEED, inscriber, config.getAdvancedInscriberSpeedCards(), config.getAdvancedInscriberSpeedPoints());
            support(upgrades, CardTraits.CAPACITY, inscriber, config.getAdvancedInscriberCapacityCards(),
                    config.getAdvancedInscriberCapacityPoints());
        }
    }

    private static void support(final IUpgradeRegistry upgrades, final CardTrait trait, final ItemStack host, final int cards,
            final int points) {
        if (cards > 0) {
            upgrades.addTraitSupport(trait, host, cards);
        }
        if (points > 0) {
            upgrades.setTraitLimit(trait, host, points);
        }
    }
}
