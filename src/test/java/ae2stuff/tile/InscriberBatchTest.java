/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.tile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class InscriberBatchTest {

    private static final int FREE = InscriberBatch.UNLIMITED;

    @Test
    void withoutCapacityCardsOneItemIsPressedAtATime() {
        assertEquals(1, InscriberBatch.size(1, 64, FREE, FREE, 64, 1));
    }

    @Test
    void capacityPointsWidenTheBatch() {
        assertEquals(4, InscriberBatch.size(4, 64, FREE, FREE, 64, 1));
    }

    @Test
    void theBatchIsWhatThereIsWhenThereIsLess() {
        assertEquals(2, InscriberBatch.size(4, 2, FREE, FREE, 64, 1));
    }

    @Test
    void platesThatAreUsedUpCapTheBatch() {
        assertEquals(3, InscriberBatch.size(4, 64, 3, 10, 64, 1));
        assertEquals(1, InscriberBatch.size(4, 64, 10, 1, 64, 1));
    }

    @Test
    void theOutputHasToTakeTheWholeBatch() {
        assertEquals(2, InscriberBatch.size(4, 64, FREE, FREE, 5, 2));
        assertEquals(0, InscriberBatch.size(4, 64, FREE, FREE, 1, 2));
    }

    @Test
    void nothingIsPressedFromAnEmptySlot() {
        assertEquals(0, InscriberBatch.size(4, 0, FREE, FREE, 64, 1));
    }

    @Test
    void powerScalesWithSpeedAndBatch() {
        assertEquals(10D, InscriberBatch.powerPerTick(1000, 100, 1, 1), 1e-9);
        assertEquals(60D, InscriberBatch.powerPerTick(1000, 100, 2, 3), 1e-9);
    }
}
