/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.tile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

class LegacyGrowthChamberTest {

    @Test
    void mixedSlotsAreSortedIntoInputAndOutputKeepingTheirOrder() {
        final LegacyGrowthChamber.Sorted<String> sorted = LegacyGrowthChamber.sort(
                Arrays.asList("seed-a", "crystal-a", "redstone", "fluix", "seed-b"), stack -> !stack.startsWith("crystal") && !stack.equals("fluix"));

        assertEquals(Arrays.asList("seed-a", "redstone", "seed-b"), sorted.input());
        assertEquals(Arrays.asList("crystal-a", "fluix"), sorted.output());
    }

    @Test
    void anEmptyChamberStaysEmpty() {
        final LegacyGrowthChamber.Sorted<String> sorted = LegacyGrowthChamber.sort(Collections.emptyList(), stack -> true);

        assertEquals(Collections.emptyList(), sorted.input());
        assertEquals(Collections.emptyList(), sorted.output());
    }
}
