/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.tile;

/**
 * How many items one press of the Advanced Inscriber works at once.
 */
final class InscriberBatch {

    /** Stands for a side whose plate the recipe does not use up. */
    static final int UNLIMITED = Integer.MAX_VALUE;

    private InscriberBatch() {
    }

    /**
     * @param parallel    one more than the capacity points installed
     * @param inputs      items in the input slot
     * @param topPlates   plates the press can use up on top, or {@link #UNLIMITED}
     * @param bottomPlates the same underneath
     * @param outputRoom  how many more items the output slot takes
     * @param perResult   how many items one press makes
     * @return the batch, or 0 when not even one fits
     */
    static int size(final int parallel, final int inputs, final int topPlates, final int bottomPlates, final int outputRoom,
            final int perResult) {
        if (perResult <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(Math.min(parallel, inputs), Math.min(Math.min(topPlates, bottomPlates), outputRoom / perResult)));
    }

    /**
     * Power one tick of a batch costs: the cycle's price spread over its ticks, for every item and every point of speed.
     */
    static double powerPerTick(final double cyclePower, final int cycleTicks, final int speed, final int batch) {
        return cyclePower / Math.max(1, cycleTicks) * speed * batch;
    }
}
