package org.birdwatch.wear;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
/** Synthetic audio only. ABBA order limits simple warm-up/order bias. Not a battery test. */
@RunWith(AndroidJUnit4.class)
public class OneTwoThreadBenchmarkTest {
    @Test public void compareOneAndTwoThreads() throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        var results=new java.util.HashMap<Integer,java.util.List<Double>>();
        results.put(1,new java.util.ArrayList<>());results.put(2,new java.util.ArrayList<>());
        float[] pcm=new float[BirdEngine.WINDOW];var random=new java.util.Random(7);
        for(int i=0;i<pcm.length;i++)pcm[i]=(random.nextFloat()-.5f)*.002f;
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        var activity=instrumentation.startActivitySync(new android.content.Intent(context,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.runOnMainSync(() -> activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        try {
        for(int threads:new int[]{2,1,1,2}) {
            try(var engine=new BenchmarkEngine(context,threads)) {
                engine.identify(pcm);engine.identify(pcm);
                for(int i=0;i<6;i++) {
                    long start=System.nanoTime();var detections=engine.identify(pcm);
                    results.get(threads).add((System.nanoTime()-start)/1e6);
                    for(var bird:detections)assertTrue(Float.isFinite(bird.score()));
                }
            }
        }
        } finally {instrumentation.runOnMainSync(activity::finish);}
        var report=new StringBuilder("Synthetic 3-second windows; order 2,1,1,2; 2 warmups per block; foreground screen on; no microphone/GPS/network.\n");
        for(int threads:new int[]{1,2}) {
            var times=results.get(threads);java.util.Collections.sort(times);
            report.append(String.format(java.util.Locale.US,"%d threads: n=%d mean=%.1f ms median=%.1f ms min=%.1f max=%.1f\n",threads,times.size(),times.stream().mapToDouble(x->x).average().getAsDouble(),(times.get(5)+times.get(6))/2,times.get(0),times.get(11)));
        }
        try(var out=context.openFileOutput("one-two-thread-benchmark.txt",0)){out.write(report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        android.util.Log.i("BirdWatchBenchmark",report.toString());
    }
}
