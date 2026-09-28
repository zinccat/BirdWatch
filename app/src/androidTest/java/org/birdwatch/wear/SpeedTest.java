package org.birdwatch.wear;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.tensorflow.lite.Interpreter;
import static org.junit.Assert.*;
import java.util.*;
@RunWith(AndroidJUnit4.class)
public class SpeedTest {
    @Test public void profileBackends() throws Exception {
        var instrumentation=InstrumentationRegistry.getInstrumentation();var context=instrumentation.getTargetContext();
        var activity=instrumentation.startActivitySync(new android.content.Intent(context,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.runOnMainSync(() -> activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        float[] pcm=new float[BirdEngine.WINDOW];Random random=new Random(29);
        for(int i=0;i<pcm.length;i++)pcm[i]=(random.nextFloat()-.5f)*.03f+(float)Math.sin(i*.22)*.04f;
        Map<String,List<double[]>> results=new LinkedHashMap<>();StringBuilder report=new StringBuilder("2 threads; same 3-second synthetic audio; screen on; no microphone or GPS.\n");
        float[] reference;
        try(var engine=new BenchmarkEngine(context,2)){engine.identify(pcm);reference=engine.logits();}
        try {
            for(String name:new String[]{"baseline","optimized","xnnpack","nnapi-requested","nnapi-requested","xnnpack","optimized","baseline"}) {
                List<double[]> times=results.computeIfAbsent(name,key->new ArrayList<>());
                try {
                    if(name.equals("baseline")) {
                        try(var engine=new BenchmarkEngine(context,2)) {
                            engine.identify(pcm);
                            for(int i=0;i<4;i++){long start=System.nanoTime();engine.identify(pcm);times.add(new double[]{(System.nanoTime()-start)/1e6,engine.copyNanos/1e6,engine.inferenceNanos/1e6,engine.postNanos/1e6});}
                        }
                    }else {
                        Interpreter.Options options=new Interpreter.Options();
                        if(name.equals("xnnpack"))options.setUseXNNPACK(true);
                        if(name.equals("nnapi-requested"))options.setUseNNAPI(true);
                        try(var engine=new OptimizedBenchmarkEngine(context,2,options)) {
                            engine.identify(pcm);float[] logits=engine.logits();double maxError=0;
                            for(int i=0;i<logits.length;i++){assertTrue(Float.isFinite(logits[i]));maxError=Math.max(maxError,Math.abs(reference[i]-logits[i]));}
                            report.append(name+" max logit error="+maxError+"\n");
                            assertTrue("Numerical difference for "+name,maxError<.05);
                            for(int i=0;i<4;i++){long start=System.nanoTime();engine.identify(pcm);times.add(new double[]{(System.nanoTime()-start)/1e6,engine.copyNanos/1e6,engine.inferenceNanos/1e6,engine.postNanos/1e6});}
                        }
                    }
                }catch(Exception e){report.append(name+" unavailable: "+e.getClass().getSimpleName()+": "+e.getMessage()+"\n");}
            }
        }finally{instrumentation.runOnMainSync(activity::finish);}
        for(var entry:results.entrySet()) {
            var values=entry.getValue();if(values.isEmpty())continue;
            double[] mean=new double[4];for(double[] row:values)for(int i=0;i<4;i++)mean[i]+=row[i]/values.size();
            report.append(String.format(Locale.US,"%s n=%d total=%.1f copy=%.1f inference=%.1f post=%.1f ms\n",entry.getKey(),values.size(),mean[0],mean[1],mean[2],mean[3]));
        }
        try(var out=context.openFileOutput("speed-profile.txt",0)){out.write(report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        android.util.Log.i("BirdWatchSpeed",report.toString());
    }
}
