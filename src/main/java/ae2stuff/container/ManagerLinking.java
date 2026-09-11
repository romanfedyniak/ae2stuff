/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 AE2 Stuff UD contributors
 */

package ae2stuff.container;

/**
 * How the manager's "Link" walks its two columns: in pairs from the top, where a target with room left takes the
 * next source as well. Apart from the world so it can be tested.
 */
public final class ManagerLinking {

    private ManagerLinking() {
    }

    public enum Outcome {
        LINKED,
        /** The source cannot link here or anywhere; the next source tries this target. */
        SKIP_SOURCE,
        /** The target cannot take this source; the source tries the next target. */
        SKIP_TARGET
    }

    public interface Attempt {

        boolean hasRoom(int target);

        Outcome link(int source, int target);
    }

    public static void run(final int sources, final int targets, final Attempt attempt) {
        int source = 0;
        int target = 0;
        while (source < sources && target < targets) {
            if (!attempt.hasRoom(target)) {
                target++;
                continue;
            }

            switch (attempt.link(source, target)) {
                case LINKED, SKIP_SOURCE -> source++;
                case SKIP_TARGET -> target++;
            }
        }
    }
}
