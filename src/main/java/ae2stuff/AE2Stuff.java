/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

import ae2stuff.client.ClientRegistration;
import ae2stuff.core.AE2StuffConfig;
import ae2stuff.core.ModGuiHandler;
import ae2stuff.core.Registration;
import ae2stuff.integration.visualiser.WirelessVisualiserProvider;
import ae2stuff.network.ModNetwork;
import ae2stuff.tile.TileWirelessConnector;
import ae2stuff.tile.TileWirelessHub;
import appeng.api.AEApi;
import appeng.api.networking.pathing.IChannelTierRegistry;
import appeng.api.networking.visualiser.NetworkVisualisers;

@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION, dependencies = AE2Stuff.DEPENDENCIES)
public final class AE2Stuff {

    // No version range until AE2UD 1.6.0 is tagged: a JitPack build of a commit reports a bare hash, which sorts below it
    static final String DEPENDENCIES = "required-after:appliedenergistics2;after:theoneprobe;after:waila";

    public static final Logger LOG = LogManager.getLogger(Tags.MOD_ID);

    /** What the old mod's connector and hub carried, as a dense cable. */
    private static final int WIRELESS_CHANNELS = 32;

    @Mod.Instance(Tags.MOD_ID)
    public static AE2Stuff instance;

    @Mod.EventHandler
    public void preInit(final FMLPreInitializationEvent event) {
        AE2StuffConfig.init(event.getModConfigurationDirectory());
        ModNetwork.init();

        // A tier registered after pre-initialisation never reaches AE2UD's config
        final AE2StuffConfig config = AE2StuffConfig.instance();
        final IChannelTierRegistry tiers = AEApi.instance().registries().channelTiers();
        if (config.isWirelessEnabled()) {
            tiers.register(TileWirelessConnector.CHANNEL_TIER, WIRELESS_CHANNELS);
        }
        if (config.isWirelessHubEnabled()) {
            tiers.register(TileWirelessHub.CHANNEL_TIER, WIRELESS_CHANNELS);
        }
    }

    @Mod.EventHandler
    public void init(final FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new ModGuiHandler());
        Registration.registerUpgrades();

        if (AE2StuffConfig.instance().isWirelessEnabled()) {
            NetworkVisualisers.register(new WirelessVisualiserProvider());
            if (event.getSide().isClient()) {
                ClientRegistration.init();
            }
        }

        if (Loader.isModLoaded("theoneprobe")) {
            FMLInterModComms.sendFunctionMessage("theoneprobe", "getTheOneProbe",
                    "ae2stuff.integration.theoneprobe.TheOneProbeModule");
        }
    }
}
