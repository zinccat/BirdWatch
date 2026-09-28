package org.birdwatch.wear;

import java.util.HashMap;
import java.util.Map;

/** Confirm across two adjacent windows; save a species at most once per minute. */
public final class DetectionGate {
    private String previous = "";
    private long previousTime = Long.MIN_VALUE;
    private final Map<String,Long> saved = new HashMap<>();
    public boolean accept(String species, float confidence, long timeMs) {
        boolean consecutive = species.equals(previous) && timeMs - previousTime <= 4000;
        previous = confidence >= .5f ? species : ""; previousTime = timeMs;
        if (species.isEmpty() || confidence < .5f || !consecutive) return false;
        Long last = saved.get(species);
        if (last != null && timeMs - last < 60000) return false;
        saved.put(species, timeMs); return true;
    }
}
