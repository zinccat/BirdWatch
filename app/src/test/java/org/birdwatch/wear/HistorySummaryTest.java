package org.birdwatch.wear;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class HistorySummaryTest {
    @Test public void mergesByScientificNameUsingLatestTimeAndItsConfidence() {
        var old = new HistorySummary.Entry(100, "Species a", "Old name", .99f);
        var latest = new HistorySummary.Entry(300, "Species a", "New name", .51f);
        var other = new HistorySummary.Entry(200, "Species b", "Other", .7f);
        assertEquals(List.of(latest, other), HistorySummary.latestBySpecies(List.of(other, latest, old)));
    }

    @Test public void distinctSpeciesWithSameCommonNameRemainSeparate() {
        var a = new HistorySummary.Entry(100, "Species a", "Shared name", .6f);
        var b = new HistorySummary.Entry(200, "Species b", "Shared name", .7f);
        assertEquals(List.of(b, a), HistorySummary.latestBySpecies(List.of(a, b)));
    }

    @Test public void filtersInvalidCandidatesAndResolvesTimestampTies() {
        var accepted = new HistorySummary.Entry(100, "Species a", "A", .7f);
        assertEquals(List.of(accepted), HistorySummary.latestBySpecies(List.of(
            new HistorySummary.Entry(100, "Species a", "A", .35f), accepted,
            new HistorySummary.Entry(200, "Species a", "A", .34f),
            new HistorySummary.Entry(300, "Species b", "B", Float.NaN),
            new HistorySummary.Entry(400, "", "Unknown", .9f))));
        assertTrue(HistorySummary.latestBySpecies(List.of()).isEmpty());
    }
}
