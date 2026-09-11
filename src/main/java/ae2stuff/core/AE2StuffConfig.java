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
    public static final String ADVANCED_INSCRIBER = "advancedInscriber";

    private static AE2StuffConfig instance;

    private final boolean growthChamberEnabled;
    private final double growthChamberPowerCapacity;
    private final double growthChamberIdlePower;
    private final double growthChamberCyclePower;
    private final int growthChamberCycleTicks;
    private final int growthChamberSpeedCards;
    private final int growthChamberSpeedPoints;

    private final boolean advancedInscriberEnabled;
    private final double advancedInscriberPowerCapacity;
    private final double advancedInscriberIdlePower;
    private final double advancedInscriberCyclePower;
    private final int advancedInscriberCycleTicks;
    private final int advancedInscriberSpeedCards;
    private final int advancedInscriberSpeedPoints;
    private final int advancedInscriberCapacityCards;
    private final int advancedInscriberCapacityPoints;

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

        this.advancedInscriberEnabled = this.get(ADVANCED_INSCRIBER, "enabled", true,
                "Whether the Advanced Inscriber and its recipe exist at all.").getBoolean();
        this.advancedInscriberPowerCapacity = Math.max(0, this.get(ADVANCED_INSCRIBER, "powerCapacity", 5000D,
                "How much power the inscriber stores (AE).").getDouble());
        this.advancedInscriberIdlePower = Math.max(0, this.get(ADVANCED_INSCRIBER, "idlePower", 0D,
                "Power drawn while doing nothing (AE/t).").getDouble());
        this.advancedInscriberCyclePower = Math.max(0, this.get(ADVANCED_INSCRIBER, "cyclePower", 1000D,
                "Power pressing one item costs (AE), spread over the cycle and multiplied by speed and batch.").getDouble());
        this.advancedInscriberCycleTicks = Math.max(1, this.get(ADVANCED_INSCRIBER, "cycleTicks", 50,
                "Ticks one press takes without Acceleration Cards.").getInt());

        this.setCategoryComment("upgrades.cards", "How many cards of a kind fit in each machine. Zero refuses "
                + "the card there outright.");
        this.setCategoryComment("upgrades.points", "The most points of an upgrade a machine takes, however many "
                + "its cards carry. Zero lets it take them all.");
        this.growthChamberSpeedCards = Math.max(0, this.get("upgrades.cards", "speed.grower", 3).getInt());
        this.growthChamberSpeedPoints = Math.max(0, this.get("upgrades.points", "speed.grower", 0).getInt());
        this.advancedInscriberSpeedCards = Math.max(0, this.get("upgrades.cards", "speed.inscriber", 5).getInt());
        this.advancedInscriberSpeedPoints = Math.max(0, this.get("upgrades.points", "speed.inscriber", 0).getInt());
        this.advancedInscriberCapacityCards = Math.max(0, this.get("upgrades.cards", "capacity.inscriber", 3).getInt());
        this.advancedInscriberCapacityPoints = Math.max(0, this.get("upgrades.points", "capacity.inscriber", 0).getInt());

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
        return switch (machine) {
            case GROWTH_CHAMBER -> this.growthChamberEnabled;
            case ADVANCED_INSCRIBER -> this.advancedInscriberEnabled;
            default -> false;
        };
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

    public boolean isAdvancedInscriberEnabled() {
        return this.advancedInscriberEnabled;
    }

    public double getAdvancedInscriberPowerCapacity() {
        return this.advancedInscriberPowerCapacity;
    }

    public double getAdvancedInscriberIdlePower() {
        return this.advancedInscriberIdlePower;
    }

    public double getAdvancedInscriberCyclePower() {
        return this.advancedInscriberCyclePower;
    }

    public int getAdvancedInscriberCycleTicks() {
        return this.advancedInscriberCycleTicks;
    }

    public int getAdvancedInscriberSpeedCards() {
        return this.advancedInscriberSpeedCards;
    }

    public int getAdvancedInscriberSpeedPoints() {
        return this.advancedInscriberSpeedPoints;
    }

    public int getAdvancedInscriberCapacityCards() {
        return this.advancedInscriberCapacityCards;
    }

    public int getAdvancedInscriberCapacityPoints() {
        return this.advancedInscriberCapacityPoints;
    }

    public boolean isJeiGrowthChamberFluix() {
        return this.jeiGrowthChamberFluix;
    }
}
