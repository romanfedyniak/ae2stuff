/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.tile;

/**
 * The rules of a wireless link, apart from the world so they can be tested.
 */
public final class WirelessLink {

    private WirelessLink() {
    }

    /**
     * What a paired connector does on its next tick.
     */
    public enum Step {
        /** Nothing to maintain, or nothing that can change before a restart. */
        SLEEP,
        /** Try again soon: the other end is unloaded, not ready, or has no room yet. */
        WAIT,
        /** The other end is gone or paired elsewhere, so the pairing is dropped. */
        FORGET,
        /** The link is up; check it again later. */
        KEEP,
        /** Make the link. */
        CONNECT
    }

    /**
     * The pairing is the player's intent and survives unloading; only the other end being gone ends it. Whether
     * the link is up is asked of the grid every time, never remembered.
     */
    public static Step next(final boolean paired, final boolean targetLoaded, final boolean targetAgrees,
            final boolean linked, final boolean inRange, final boolean targetHasRoom, final boolean nodesReady) {
        if (!paired) {
            return Step.SLEEP;
        }
        if (!targetLoaded) {
            return Step.WAIT;
        }
        if (!targetAgrees) {
            return Step.FORGET;
        }
        if (linked) {
            return Step.KEEP;
        }
        if (!inRange) {
            return Step.SLEEP;
        }
        if (!targetHasRoom || !nodesReady) {
            return Step.WAIT;
        }
        return Step.CONNECT;
    }

    public static double distance(final int dx, final int dy, final int dz) {
        return Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
    }

    /**
     * What one end of a link draws, in AE/t.
     */
    public static double power(final double base, final double multiplier, final double distance) {
        return base + multiplier * distance * Math.log(distance * distance + 3);
    }

    /**
     * How many more times a hub can be queued: its slots taken by paired connectors and by queued entries both count.
     */
    public static int freeHubSlots(final int maxConnections, final int linked, final int queued) {
        return Math.max(0, maxConnections - linked - queued);
    }

    /**
     * @param maxRange in blocks, or 0 for no limit
     */
    public static boolean inRange(final int maxRange, final double distance) {
        return maxRange <= 0 || distance <= maxRange;
    }
}
