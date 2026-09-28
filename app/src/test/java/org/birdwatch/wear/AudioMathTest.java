package org.birdwatch.wear;
import org.junit.Test;
import static org.junit.Assert.*;
public class AudioMathTest {
    @Test public void sixSecondsAt44100BecomeTwoModelWindows() {
        short[] pcm = new short[44100 * 6];
        java.util.Arrays.fill(pcm,(short)16384);
        float[] out=AudioMath.resample(pcm,44100,48000);
        assertEquals(288000,out.length);
        assertEquals(.5f,out[0],1e-6f); assertEquals(.5f,out[out.length-1],1e-6f);
    }
    @Test public void pcmSignedRangeAndSilenceArePreserved() {
        float[] out=AudioMath.resample(new short[]{Short.MIN_VALUE,0,Short.MAX_VALUE},48000,48000);
        assertEquals(-1f,out[0],0); assertEquals(0f,out[1],0); assertEquals(32767/32768f,out[2],0);
    }
    @Test public void sigmoidMatchesBirdnetDefaultSensitivity() {
        assertEquals(.5f,AudioMath.confidence(0),1e-6);
        assertEquals(.9525741f,AudioMath.confidence(3),1e-6);
        assertEquals(.04742587f,AudioMath.confidence(-3),1e-6);
        assertTrue(AudioMath.confidence(-100)>0);assertTrue(AudioMath.confidence(100)<1);
    }
    @Test public void volumeUsesOnlyTheReadSamples() { assertEquals(.5f,AudioMath.rms(new short[]{16384,16384,32767},2),1e-6); }
}
