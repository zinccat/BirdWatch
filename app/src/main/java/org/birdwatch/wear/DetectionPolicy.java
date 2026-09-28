package org.birdwatch.wear;

/** Shared display threshold for inference, current-session and persisted candidates. */
public final class DetectionPolicy {
    public static final float MIN_VISIBLE = .35f;
    private DetectionPolicy() {}
    public static boolean isVisible(float score) { return Float.isFinite(score) && score >= MIN_VISIBLE; }
}
