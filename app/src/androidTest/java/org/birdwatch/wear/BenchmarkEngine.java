package org.birdwatch.wear;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.DataType;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.*;

/** Test-only copy of BirdEngine with 1-thread support. Production behavior is unchanged. */
public final class BenchmarkEngine implements AutoCloseable {
    public static final int RATE = 48000, WINDOW = 144000;
    public record Detection(String scientific, String common, float score) {}
    private final Interpreter interpreter;
    long copyNanos, inferenceNanos, postNanos;
    float[] logits(){return output[0].clone();}
    private final ByteBuffer input = ByteBuffer.allocateDirect(WINDOW * 4).order(ByteOrder.nativeOrder());
    private final float[][] output;
    private final List<String> labels = new ArrayList<>();
    public BenchmarkEngine(Context context) throws IOException {
        this(context,2);
    }
    BenchmarkEngine(Context context,int threads) throws IOException {this(context,threads,new Interpreter.Options());}
    BenchmarkEngine(Context context,int threads,Interpreter.Options options) throws IOException {
        if(threads!=1 && threads!=2 && threads!=4)throw new IllegalArgumentException("Use 1, 2 or 4 threads");
        labels.addAll(BirdNames.load(context));
        try (AssetFileDescriptor fd = context.getAssets().openFd("birdnet.tflite"); FileInputStream stream = new FileInputStream(fd.getFileDescriptor())) {
            MappedByteBuffer model = stream.getChannel().map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
            interpreter = new Interpreter(model, options.setNumThreads(threads));
        }
        output = new float[1][labels.size()];
        int[] in = interpreter.getInputTensor(0).shape();
        int[] out = interpreter.getOutputTensor(0).shape();
        if (!Arrays.equals(in, new int[]{1, WINDOW}) || !Arrays.equals(out, new int[]{1, labels.size()})
            || interpreter.getInputTensor(0).dataType() != DataType.FLOAT32) {
            interpreter.close(); throw new IOException("Model and labels do not match");
        }
    }
    public List<Detection> identify(float[] pcm) throws InterruptedException {
        return identify(pcm,null);
    }
    public List<Detection> identify(float[] pcm,boolean[] allowed) throws InterruptedException {
        if(allowed!=null && allowed.length!=labels.size())throw new IllegalArgumentException("Invalid species filter");
        if (pcm.length < WINDOW || pcm.length % WINDOW != 0) throw new IllegalArgumentException("Expected complete 3-second audio windows");
        copyNanos=0;inferenceNanos=0;postNanos=0;
        float[] best = new float[labels.size()];
        for (int offset = 0; offset < pcm.length; offset += WINDOW) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            long copyStart=System.nanoTime();
            input.clear();
            for (int i = 0; i < WINDOW; i++) input.putFloat(pcm[offset + i]);
            input.rewind(); copyNanos+=System.nanoTime()-copyStart;
            long inferStart=System.nanoTime();interpreter.run(input, output);inferenceNanos+=System.nanoTime()-inferStart;
            long postStart=System.nanoTime();
            for (int i = 0; i < best.length; i++) best[i] = Math.max(best[i], AudioMath.confidence(output[0][i]));
            postNanos+=System.nanoTime()-postStart;
        }
        long postStart=System.nanoTime();
        List<Detection> ranked = new ArrayList<>();
        for (int i = 0; i < best.length; i++) {
            if(allowed!=null && !allowed[i])continue;
            String[] parts = labels.get(i).split("_", 2);
            // Non-bird event classes have no binomial scientific name.
            if (parts.length != 2 || !parts[0].contains(" ") || !Float.isFinite(best[i])) continue;
            if (DetectionPolicy.isVisible(best[i])) ranked.add(new Detection(parts[0], parts[1], best[i]));
        }
        ranked.sort((a, b) -> Float.compare(b.score(), a.score()));
        postNanos+=System.nanoTime()-postStart;
        return new ArrayList<>(ranked.subList(0, Math.min(3, ranked.size())));
    }
    @Override public void close() { interpreter.close(); }
}
