package org.birdwatch.wear;

public final class AudioMath {
    private AudioMath() {}
    public static float[] resample(short[] input, int sourceRate, int targetRate) {
        if (input.length == 0 || sourceRate <= 0 || targetRate <= 0) throw new IllegalArgumentException("Invalid audio");
        float[] output = new float[(int) Math.round(input.length * (double) targetRate / sourceRate)];
        for (int i = 0; i < output.length; i++) {
            double position = i * (double) sourceRate / targetRate;
            int left = Math.min((int) position, input.length - 1);
            int right = Math.min(left + 1, input.length - 1);
            double fraction = position - left;
            output[i] = (float) ((input[left] * (1 - fraction) + input[right] * fraction) / 32768.0);
        }
        return output;
    }
    public static float confidence(float logit) {
        return (float) (1.0 / (1.0 + Math.exp(-Math.max(-15, Math.min(15, logit)))));
    }
    public static float rms(short[] data, int length) {
        if (length == 0) return 0;
        double sum = 0;
        for (int i = 0; i < length; i++) { double v = data[i] / 32768.0; sum += v * v; }
        return (float) Math.sqrt(sum / length);
    }
}
