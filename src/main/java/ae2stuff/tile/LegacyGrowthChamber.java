/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.tile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The old chamber kept seeds and grown crystals in the same twenty-seven slots. This decides which of the
 * two new sides each of them belongs on, keeping their order.
 */
final class LegacyGrowthChamber {

    private LegacyGrowthChamber() {
    }

    static <T> Sorted<T> sort(final List<T> stacks, final Predicate<T> isInput) {
        final List<T> input = new ArrayList<>();
        final List<T> output = new ArrayList<>();
        for (final T stack : stacks) {
            (isInput.test(stack) ? input : output).add(stack);
        }
        return new Sorted<>(input, output);
    }

    // A class rather than a record: Jabel lowers the syntax, but Java 8 has no java.lang.Record to extend
    static final class Sorted<T> {
        private final List<T> input;
        private final List<T> output;

        Sorted(final List<T> input, final List<T> output) {
            this.input = input;
            this.output = output;
        }

        List<T> input() {
            return this.input;
        }

        List<T> output() {
            return this.output;
        }
    }
}
