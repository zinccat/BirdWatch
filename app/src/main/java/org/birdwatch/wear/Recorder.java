package org.birdwatch.wear;

import android.media.*;
import android.os.SystemClock;

public final class Recorder {
    public interface Progress { void update(float seconds, float level); }
    public record Window(float[] pcm, long endedAtMs, long audioEndMs) {}
    private volatile boolean cancelled;
    public void cancel() { cancelled = true; }
    @android.annotation.SuppressLint("MissingPermission") // MainActivity checks runtime permission.
    public void stream(LatestWindow<Window> windows, Progress progress) throws Exception {
        AudioRecord audio = null;
        int rate = 48000;
        for (int candidate : new int[]{48000, 44100}) {
            int min = AudioRecord.getMinBufferSize(candidate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) continue;
            try {
                AudioRecord attempt = new AudioRecord(MediaRecorder.AudioSource.UNPROCESSED, candidate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min * 2, candidate));
                if (attempt.getState() == AudioRecord.STATE_INITIALIZED) { audio = attempt; rate = candidate; break; }
                attempt.release();
            } catch (IllegalArgumentException ignored) { }
        }
        if (audio == null) throw new IllegalStateException("Unable to start the watch microphone");
        try {
            final int captureRate = rate;
            RollingPcm rolling = new RollingPcm(rate * 3, rate * 3 / 2);
            final long[] windowEndSamples = {rate * 3L};
            short[] chunk = new short[2048]; long samples = 0, nextProgress = 0;
            if (cancelled) return;
            audio.startRecording();
            while (!cancelled && !Thread.currentThread().isInterrupted()) {
                int count = audio.read(chunk, 0, chunk.length, AudioRecord.READ_BLOCKING);
                if (cancelled) break;
                if (count <= 0) throw new IllegalStateException("Audio recording interrupted; please try again");
                rolling.append(chunk, count, pcm -> {
                    windows.offer(new Window(AudioMath.resample(pcm, captureRate, BirdEngine.RATE),
                        SystemClock.elapsedRealtime(), windowEndSamples[0] * 1000 / captureRate));
                    windowEndSamples[0] += captureRate * 3L / 2;
                });
                samples += count;
                if (samples >= nextProgress) {
                    progress.update(samples / (float) rate, AudioMath.rms(chunk, count));
                    nextProgress = samples + rate / 5; // UI updates at most five times per second.
                }
            }
        } finally {
            if (audio.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) audio.stop();
            audio.release();
        }
    }
}
