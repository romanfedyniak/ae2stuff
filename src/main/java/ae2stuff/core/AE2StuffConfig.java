/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.core;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/**
 * Everything a pack can change, in {@code config/ae2stuff.cfg}. Each machine adds a section of its own.
 */
public final class AE2StuffConfig extends Configuration {

    private static AE2StuffConfig instance;

    private AE2StuffConfig(final File file) {
        super(file);
    }

    public static void init(final File configDirectory) {
        instance = new AE2StuffConfig(new File(configDirectory, "ae2stuff.cfg"));
        if (instance.hasChanged()) {
            instance.save();
        }
    }

    public static AE2StuffConfig instance() {
        return instance;
    }
}
