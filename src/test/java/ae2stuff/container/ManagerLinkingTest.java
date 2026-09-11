/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.container;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import ae2stuff.container.ManagerLinking.Outcome;

class ManagerLinkingTest {

    private static final List<Integer> NONE = Collections.emptyList();

    /** Targets with this much room; a source in skipSources is refused wherever it goes, a target in fullTargets refuses all. */
    private static List<String> run(final int sources, final int[] room, final List<Integer> skipSources,
            final List<Integer> fullTargets) {
        final List<String> links = new ArrayList<>();
        final int[] left = room.clone();
        ManagerLinking.run(sources, room.length, new ManagerLinking.Attempt() {
            @Override
            public boolean hasRoom(final int target) {
                return left[target] > 0;
            }

            @Override
            public Outcome link(final int source, final int target) {
                if (skipSources.contains(source)) {
                    return Outcome.SKIP_SOURCE;
                }
                if (fullTargets.contains(target)) {
                    return Outcome.SKIP_TARGET;
                }
                left[target]--;
                links.add(source + ">" + target);
                return Outcome.LINKED;
            }
        });
        return links;
    }

    @Test
    void connectorsPairFromTheTop() {
        assertEquals(Arrays.asList("0>0", "1>1", "2>2"), run(3, new int[] { 1, 1, 1 }, NONE, NONE));
    }

    @Test
    void aHubTakesSourcesUntilItIsFull() {
        assertEquals(Arrays.asList("0>0", "1>0", "2>1"), run(3, new int[] { 2, 1 }, NONE, NONE));
    }

    @Test
    void aRefusedSourceIsSkippedAndItsTargetWaitsForTheNext() {
        assertEquals(Arrays.asList("1>0", "2>1"), run(3, new int[] { 1, 1 }, Collections.singletonList(0), NONE));
    }

    @Test
    void aRefusingTargetIsSkippedAndTheSourceTriesTheNext() {
        assertEquals(Collections.singletonList("0>1"), run(1, new int[] { 1, 1 }, NONE, Collections.singletonList(0)));
    }

    @Test
    void leftoversOnEitherSideAreLeftAlone() {
        assertEquals(Collections.singletonList("0>0"), run(1, new int[] { 1, 1, 1 }, NONE, NONE));
        assertEquals(Arrays.asList("0>0", "1>1"), run(4, new int[] { 1, 1 }, NONE, NONE));
    }
}
