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

    public static final String GROWTH_CHAMBER = "growthChamber";

    private static AE2StuffConfig instance;

    private final boolean growthChamberEnabled;
    private final double growthChamberPowerCapacity;
    private final double growthChamberIdlePower;
    private final double growthChamberCyclePower;
    private final int growthChamberCycleTicks;
    private final int growthChamberSpeedCards;
    private final int growthChamberSpeedPoints;
    private final boolean jeiGrowthChamberFluix;

    private AE2StuffConfig(final File file) {
        super(file);

        this.growthChamberEnabled = this.get(GROWTH_CHAMBER, "enabled", true,
                "Whether the Crystal Growth Chamber and its recipe exist at all.").getBoolean();
        this.growthChamberPowerCapacity = Math.max(0, this.get(GROWTH_CHAMBER, "powerCapacity", 10000D,
                "How much power the chamber stores (AE).").getDouble());
        this.growthChamberIdlePower = Math.max(0, this.get(GROWTH_CHAMBER, "idlePower", 0D,
                "Power drawn while doing nothing (AE/t).").getDouble());
        this.growthChamberCyclePower = Math.max(0, this.get(GROWTH_CHAMBER, "cyclePower", 100D,
                "Power one growth cycle costs (AE), multiplied by the chamber's speed.").getDouble());
        this.growthChamberCycleTicks = Math.max(1, this.get(GROWTH_CHAMBER, "cycleTicks", 1,
                "Ticks one growth cycle takes.").getInt());

        this.setCategoryComment("upgrades.cards", "How many cards of a kind fit in each machine. Zero refuses "
                + "the card there outright.");
        this.setCategoryComment("upgrades.points", "The most points of an upgrade a machine takes, however many "
                + "its cards carry. Zero lets it take them all.");
        this.growthChamberSpeedCards = Math.max(0, this.get("upgrades.cards", "speed.grower", 3).getInt());
        this.growthChamberSpeedPoints = Math.max(0, this.get("upgrades.points", "speed.grower", 0).getInt());

        this.jeiGrowthChamberFluix = this.get("jei", "growthChamberFluix", true,
                "Fluix made in the Crystal Growth Chamber, as a recipe category of its own.").getBoolean();
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

    /**
     * For recipe conditions, which name a machine by its config section.
     */
    public boolean isEnabled(final String machine) {
        return GROWTH_CHAMBER.equals(machine) && this.growthChamberEnabled;
    }

    public boolean isGrowthChamberEnabled() {
        return this.growthChamberEnabled;
    }

    public double getGrowthChamberPowerCapacity() {
        return this.growthChamberPowerCapacity;
    }

    public double getGrowthChamberIdlePower() {
        return this.growthChamberIdlePower;
    }

    public double getGrowthChamberCyclePower() {
        return this.growthChamberCyclePower;
    }

    public int getGrowthChamberCycleTicks() {
        return this.growthChamberCycleTicks;
    }

    public int getGrowthChamberSpeedCards() {
        return this.growthChamberSpeedCards;
    }

    public int getGrowthChamberSpeedPoints() {
        return this.growthChamberSpeedPoints;
    }

    public boolean isJeiGrowthChamberFluix() {
        return this.jeiGrowthChamberFluix;
    }
}
