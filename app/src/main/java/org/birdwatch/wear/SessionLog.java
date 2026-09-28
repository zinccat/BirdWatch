package org.birdwatch.wear;

import java.util.*;

/** UI-thread session log, in audio-window order with bounded memory. */
public final class SessionLog {
    public record Entry(long audioEndMs, String scientific, String common, float score) {}
    public static final int LIMIT = 200;
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    public void append(long audioEndMs, String scientific, String common, float score) {
        if (!DetectionPolicy.isVisible(score)) return;
        if (!entries.isEmpty() && audioEndMs < entries.getLast().audioEndMs()) throw new IllegalArgumentException("Window order reversed");
        entries.addLast(new Entry(audioEndMs, scientific, common, score));
        if (entries.size() > LIMIT) entries.removeFirst();
    }
    public List<Entry> entries() { return new ArrayList<>(entries); }
    public List<Entry> newestFirst() {
        List<Entry> result=entries();
        result.sort(Comparator.comparingLong(Entry::audioEndMs).reversed()
            .thenComparing((a,b) -> Float.compare(b.score(),a.score())));
        return result;
    }
    public void clear() { entries.clear(); }
}
