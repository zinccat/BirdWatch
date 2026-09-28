package org.birdwatch.wear;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;

public class StreamingTest {
    @Test public void overlappingWindowsStayOrderedAcrossChunkBoundaries() {
        RollingPcm ring=new RollingPcm(6,3); List<short[]> out=new ArrayList<>();
        ring.append(new short[]{0,1,2,3},4,out::add); assertTrue(out.isEmpty());
        ring.append(new short[]{4,5,6,7,8,9,10,11,12},9,out::add);
        assertEquals(3,out.size());
        assertArrayEquals(new short[]{0,1,2,3,4,5},out.get(0));
        assertArrayEquals(new short[]{3,4,5,6,7,8},out.get(1));
        assertArrayEquals(new short[]{6,7,8,9,10,11},out.get(2));
    }
    @Test public void realSampleRateProducesExactThreeSecondWindows() {
        for(int rate:new int[]{44100,48000}) {
            RollingPcm ring=new RollingPcm(rate*3,rate*3/2); List<short[]> out=new ArrayList<>();
            short[] chunk=new short[2048]; int remaining=rate*6;
            while(remaining>0) {int count=Math.min(remaining,chunk.length);ring.append(chunk,count,out::add);remaining-=count;}
            assertEquals(3,out.size());
            for(short[] pcm:out)assertEquals(144000,AudioMath.resample(pcm,rate,48000).length);
        }
    }
    @Test public void slowConsumerReceivesOnlyLatestWindow() throws Exception {
        LatestWindow<Integer> box=new LatestWindow<>(); box.offer(1);box.offer(2);box.offer(3);
        assertEquals(Integer.valueOf(3),box.take());box.close();box.offer(4);assertNull(box.take());
    }
    @Test public void closeUnblocksWaitingConsumer() throws Exception {
        LatestWindow<Integer> box=new LatestWindow<>();ExecutorService worker=Executors.newSingleThreadExecutor();
        try {Future<Integer> result=worker.submit(box::take);box.close();assertNull(result.get(1,TimeUnit.SECONDS));}
        finally {worker.shutdownNow();}
    }
    @Test public void captureFailureDiscardsPendingWindowAndPropagates() throws Exception {
        LatestWindow<Integer> box=new LatestWindow<>();box.offer(1);box.fail(new IllegalStateException("microphone"));
        try {box.take();fail();}catch(IllegalStateException expected){assertEquals("microphone",expected.getMessage());}
    }
    @Test public void historyRequiresConfirmationAndCooldown() {
        DetectionGate gate=new DetectionGate();
        assertFalse(gate.accept("A",.9f,3000));assertTrue(gate.accept("A",.8f,4500));
        assertFalse(gate.accept("A",.9f,6000));
        assertFalse(gate.accept("B",.9f,7500));assertTrue(gate.accept("B",.9f,9000));
        assertFalse(gate.accept("A",.9f,63000));assertTrue(gate.accept("A",.9f,64500));
    }
    @Test public void lowConfidenceAndLongGapsBreakConfirmation() {
        DetectionGate gate=new DetectionGate();
        assertFalse(gate.accept("A",.8f,3000));assertFalse(gate.accept("A",.2f,4500));
        assertFalse(gate.accept("A",.8f,6000));assertFalse(gate.accept("A",.8f,12000));
        assertTrue(gate.accept("A",.8f,13500));
    }
}
