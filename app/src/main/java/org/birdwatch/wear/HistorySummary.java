package org.birdwatch.wear;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Groups persisted candidates by scientific name without changing stored records. */
final class HistorySummary {
    record Entry(long time, String scientific, String common, float score) {}

    static List<Entry> latestBySpecies(List<Entry> entries) {
        Map<String, Entry> latest = new HashMap<>();
        for (Entry entry : entries) {
            if (entry.scientific().isEmpty() || !DetectionPolicy.isVisible(entry.score())) continue;
            latest.merge(entry.scientific(), entry, (old, next) ->
                next.time() > old.time() || (next.time() == old.time() && next.score() > old.score()) ? next : old);
        }
        List<Entry> result = new ArrayList<>(latest.values());
        result.sort(Comparator.comparingLong(Entry::time).reversed().thenComparing(Entry::scientific));
        return result;
    }

    private HistorySummary() {}
}
