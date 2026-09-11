/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.tile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ae2stuff.tile.WirelessLink.Step;

class WirelessLinkTest {

    @Test
    void anUnpairedConnectorSleeps() {
        assertEquals(Step.SLEEP, WirelessLink.next(false, true, true, false, true, true, true));
    }

    @Test
    void anUnloadedTargetIsWaitedForAndNeverForgotten() {
        assertEquals(Step.WAIT, WirelessLink.next(true, false, false, false, true, true, true));
    }

    @Test
    void aTargetThatNoLongerAgreesIsForgotten() {
        assertEquals(Step.FORGET, WirelessLink.next(true, true, false, false, true, true, true));
    }

    @Test
    void aLiveLinkIsKeptEvenIfTheRulesChanged() {
        assertEquals(Step.KEEP, WirelessLink.next(true, true, true, true, false, false, true));
    }

    @Test
    void outOfRangeKeepsThePairingButDoesNotConnect() {
        assertEquals(Step.SLEEP, WirelessLink.next(true, true, true, false, false, true, true));
    }

    @Test
    void aFullHubOrANodeNotReadyIsWaitedFor() {
        assertEquals(Step.WAIT, WirelessLink.next(true, true, true, false, true, false, true));
        assertEquals(Step.WAIT, WirelessLink.next(true, true, true, false, true, true, false));
    }

    @Test
    void everythingInPlaceConnects() {
        assertEquals(Step.CONNECT, WirelessLink.next(true, true, true, false, true, true, true));
    }

    @Test
    void powerFollowsTheUpstreamFormula() {
        assertEquals(1, WirelessLink.power(1, 0.1, 0), 1e-9);
        assertEquals(1 + 0.1 * 10 * Math.log(103), WirelessLink.power(1, 0.1, 10), 1e-9);
    }

    @Test
    void queuedHubSlotsCountAgainstItsRoom() {
        assertEquals(22, WirelessLink.freeHubSlots(32, 10, 0));
        assertEquals(2, WirelessLink.freeHubSlots(32, 10, 20));
        assertEquals(0, WirelessLink.freeHubSlots(32, 30, 5));
    }

    @Test
    void zeroRangeMeansNoLimit() {
        assertTrue(WirelessLink.inRange(0, 1e6));
        assertTrue(WirelessLink.inRange(16, 16));
        assertFalse(WirelessLink.inRange(16, 16.5));
        assertEquals(5, WirelessLink.distance(3, 0, -4), 1e-9);
    }
}
