package org.birdwatch.wear;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.DataType;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.*;

/** BirdNET v2.4: 48 kHz mono float PCM; 3 second windows; one engine reused throughout a streaming session. */
public final class BirdEngine implements AutoCloseable {
    public static final int RATE = 48000, WINDOW = 144000;
    public record Detection(String scientific, String common, float score) {}
    private final Interpreter interpreter;
    private final ByteBuffer input = ByteBuffer.allocateDirect(WINDOW * 4).order(ByteOrder.nativeOrder());
    private final FloatBuffer samples = input.asFloatBuffer();
    private final float[] best;
    private final String[] scientific, common;
    private final float[][] output;
    public BirdEngine(Context context) throws IOException {
        this(context,2);
    }
    BirdEngine(Context context,int threads) throws IOException {
        if(threads!=2 && threads!=4)throw new IllegalArgumentException("Use 2 or 4 threads");
        List<String> labels = BirdNames.load(context);
        try (AssetFileDescriptor fd = context.getAssets().openFd("birdnet.tflite"); FileInputStream stream = new FileInputStream(fd.getFileDescriptor())) {
            MappedByteBuffer model = stream.getChannel().map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
            interpreter = new Interpreter(model, new Interpreter.Options().setNumThreads(threads).setUseXNNPACK(true));
        }
        output = new float[1][labels.size()];
        best = new float[labels.size()];
        scientific = new String[labels.size()]; common = new String[labels.size()];
        // Parse all 6,522 names once, rather than allocating strings for every window.
        for (int i = 0; i < labels.size(); i++) {
            String[] parts = labels.get(i).split("_", 2);
            if (parts.length == 2 && parts[0].contains(" ")) {scientific[i] = parts[0]; common[i] = parts[1];}
        }
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
        if(allowed!=null && allowed.length!=best.length)throw new IllegalArgumentException("Invalid species filter");
        if (pcm.length < WINDOW || pcm.length % WINDOW != 0) throw new IllegalArgumentException("Expected complete 3-second audio windows");
        Arrays.fill(best, 0f);
        for (int offset = 0; offset < pcm.length; offset += WINDOW) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            input.clear();
            // Bulk native-order transfer avoids 144,000 individual buffer writes.
            samples.clear(); samples.put(pcm, offset, WINDOW);
            input.rewind(); interpreter.run(input, output);
            for (int i = 0; i < best.length; i++) best[i] = Math.max(best[i], AudioMath.confidence(output[0][i]));
        }
        List<Detection> ranked = new ArrayList<>();
        for (int i = 0; i < best.length; i++) {
            if(allowed!=null && !allowed[i])continue;
            if (scientific[i] != null && DetectionPolicy.isVisible(best[i]))
                ranked.add(new Detection(scientific[i], common[i], best[i]));
        }
        ranked.sort((a, b) -> Float.compare(b.score(), a.score()));
        return new ArrayList<>(ranked.subList(0, Math.min(3, ranked.size())));
    }
    @Override public void close() { interpreter.close(); }
}
