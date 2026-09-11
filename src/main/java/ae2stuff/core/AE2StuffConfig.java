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
    public static final String WIRELESS = "wireless";
    public static final String WIRELESS_HUB = "wireless.hub";
    public static final String WIRELESS_KIT = "wireless.kit";

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

    private final boolean wirelessEnabled;
    private final double wirelessPowerBase;
    private final double wirelessPowerDistanceMultiplier;
    private final int wirelessMaxRange;
    private final boolean wirelessHubEnabled;
    private final int wirelessHubMaxConnections;
    private final double wirelessHubPowerBase;
    private final double wirelessHubPowerDistanceMultiplier;
    private final boolean wirelessKitQueueMode;
    private final boolean wirelessKitManagerMode;

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

        this.wirelessEnabled = this.get(WIRELESS, "enabled", true,
                "Whether the Wireless Connector, the Wireless Setup Kit and their recipes exist at all. Off takes the "
                        + "Wireless Hub with them.").getBoolean();
        this.wirelessPowerBase = Math.max(0, this.get(WIRELESS, "powerBase", 1D,
                "Power a connector draws for its link before distance counts (AE/t).").getDouble());
        this.wirelessPowerDistanceMultiplier = Math.max(0, this.get(WIRELESS, "powerDistanceMultiplier", 0.1D,
                "A connector draws powerBase + powerDistanceMultiplier * distance * ln(distance^2 + 3) (AE/t).").getDouble());
        this.wirelessMaxRange = Math.max(0, this.get(WIRELESS, "maxRange", 0,
                "The farthest apart two ends can be linked, in blocks. Zero sets no limit.").getInt());

        this.wirelessHubEnabled = this.get(WIRELESS_HUB, "enabled", true,
                "Whether the Wireless Hub and its recipe exist.").getBoolean();
        this.wirelessHubMaxConnections = Math.max(1, this.get(WIRELESS_HUB, "maxConnections", 32,
                "How many Wireless Connectors one hub links to.").getInt());
        this.wirelessHubPowerBase = Math.max(0, this.get(WIRELESS_HUB, "powerBase", 1D,
                "Power a hub draws for each link before distance counts (AE/t).").getDouble());
        this.wirelessHubPowerDistanceMultiplier = Math.max(0, this.get(WIRELESS_HUB, "powerDistanceMultiplier", 0.1D,
                "A hub draws powerBase + powerDistanceMultiplier * distance * ln(distance^2 + 3) for each link (AE/t).")
                .getDouble());

        this.wirelessKitQueueMode = this.get(WIRELESS_KIT, "queueMode", true,
                "Whether the Wireless Setup Kit has its queue mode, which queues many ends and links them one after "
                        + "another.").getBoolean();
        this.wirelessKitManagerMode = this.get(WIRELESS_KIT, "managerMode", true,
                "Whether the Wireless Setup Kit has its manager mode, a window listing the connectors and hubs of chosen "
                        + "networks.").getBoolean();

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
            case WIRELESS -> this.isWirelessEnabled();
            case WIRELESS_HUB -> this.isWirelessHubEnabled();
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

    public boolean isWirelessEnabled() {
        return this.wirelessEnabled;
    }

    public double getWirelessPowerBase() {
        return this.wirelessPowerBase;
    }

    public double getWirelessPowerDistanceMultiplier() {
        return this.wirelessPowerDistanceMultiplier;
    }

    public int getWirelessMaxRange() {
        return this.wirelessMaxRange;
    }

    public boolean isWirelessHubEnabled() {
        return this.wirelessEnabled && this.wirelessHubEnabled;
    }

    public int getWirelessHubMaxConnections() {
        return this.wirelessHubMaxConnections;
    }

    public double getWirelessHubPowerBase() {
        return this.wirelessHubPowerBase;
    }

    public double getWirelessHubPowerDistanceMultiplier() {
        return this.wirelessHubPowerDistanceMultiplier;
    }

    public boolean isWirelessKitQueueModeEnabled() {
        return this.wirelessKitQueueMode;
    }

    public boolean isWirelessKitManagerModeEnabled() {
        return this.wirelessKitManagerMode;
    }

    public boolean isJeiGrowthChamberFluix() {
        return this.jeiGrowthChamberFluix;
    }
}
