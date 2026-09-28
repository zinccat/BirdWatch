package org.birdwatch.wear;

import java.util.function.Consumer;

/** Fixed memory window; emits at exact sample boundaries, independent of read chunk size. */
public final class RollingPcm {
    private final short[] ring;
    private final int hop;
    private int position, untilNext;
    public RollingPcm(int window, int hop) {
        if (window <= 0 || hop <= 0 || hop > window) throw new IllegalArgumentException();
        ring = new short[window]; this.hop = hop; untilNext = window;
    }
    public void append(short[] samples, int count, Consumer<short[]> emit) {
        if (count < 0 || count > samples.length) throw new IllegalArgumentException();
        for (int i = 0; i < count; i++) {
            ring[position] = samples[i]; position = (position + 1) % ring.length;
            if (--untilNext == 0) {
                short[] window = new short[ring.length];
                int tail = ring.length - position;
                System.arraycopy(ring, position, window, 0, tail);
                System.arraycopy(ring, 0, window, tail, position);
                emit.accept(window); untilNext = hop;
            }
        }
    }
}
