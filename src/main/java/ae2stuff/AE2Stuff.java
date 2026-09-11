/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import ae2stuff.core.AE2StuffConfig;

@Mod(modid = Tags.MOD_ID, name = Tags.MOD_NAME, version = Tags.VERSION, dependencies = AE2Stuff.DEPENDENCIES)
public final class AE2Stuff {

    // No version range until AE2UD 1.6.0 is tagged: a JitPack build of a commit reports a bare hash, which sorts below it
    static final String DEPENDENCIES = "required-after:appliedenergistics2";

    @Mod.EventHandler
    public void preInit(final FMLPreInitializationEvent event) {
        AE2StuffConfig.init(event.getModConfigurationDirectory());
    }
}
