package org.birdwatch.wear;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class OptimizedEngineTest {
    @Test public void matchesPreviousEngineWithReuseMultiWindowAndGeoMasks() throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        var output=BirdEngine.class.getDeclaredField("output");output.setAccessible(true);
        try(var old=new BenchmarkEngine(context,2);var updated=new BirdEngine(context,2)) {
            java.util.Random random=new java.util.Random(47);
            for(int fixture=0;fixture<4;fixture++) {
                float[] pcm=new float[BirdEngine.WINDOW*(fixture==2?2:1)];
                for(int i=0;i<pcm.length;i++)pcm[i]=fixture==3?0:(random.nextFloat()-.5f)*.03f+(float)Math.sin(i*.22)*.04f;
                boolean[] mask=null;
                if(fixture==1){mask=new boolean[6522];for(int i=0;i<mask.length;i++)mask[i]=(i%3!=0);}
                var expected=old.identify(pcm,mask);var actual=updated.identify(pcm,mask);
                assertEquals(expected.size(),actual.size());
                for(int i=0;i<expected.size();i++) {
                    assertEquals(expected.get(i).scientific(),actual.get(i).scientific());
                    assertEquals(expected.get(i).common(),actual.get(i).common());
                    assertEquals(expected.get(i).score(),actual.get(i).score(),1e-6f);
                }
                // Compare every raw class score, even when the visible result is empty.
                assertArrayEquals(old.logits(),((float[][])output.get(updated))[0],1e-6f);
            }
        }
    }
}
