package org.birdwatch.wear;
import org.junit.Test;
import static org.junit.Assert.*;
public class SessionLogTest {
    @Test public void thresholdIncludesThirtyFiveAndRejectsLowerAndNan() {
        SessionLog log=new SessionLog();
        log.append(3000,"A","a",.3499f);log.append(3000,"B","b",Float.NaN);
        log.append(3000,"C","c",.35f);log.append(4500,"D","d",.80f);
        assertEquals(2,log.entries().size());assertEquals("C",log.entries().get(0).scientific());
        assertEquals(4500,log.entries().get(1).audioEndMs());
    }
    @Test public void longSessionKeepsMostRecentTwoHundredInTimeOrder() {
        SessionLog log=new SessionLog();
        for(int i=0;i<1000;i++)log.append(i*1500L,"A","a",.5f);
        assertEquals(200,log.entries().size());
        assertEquals(800*1500L,log.entries().get(0).audioEndMs());
        assertEquals(999*1500L,log.entries().get(199).audioEndMs());
        log.clear();assertTrue(log.entries().isEmpty());
    }
    @Test public void presentationPlacesNewestWindowsFirstThenHighestScore() {
        SessionLog log=new SessionLog();
        log.append(3000,"A","a",.8f);log.append(4500,"B","b",.4f);log.append(4500,"C","c",.9f);
        assertEquals("C",log.newestFirst().get(0).scientific());
        assertEquals("B",log.newestFirst().get(1).scientific());
        assertEquals("A",log.newestFirst().get(2).scientific());
        assertEquals("A",log.entries().get(0).scientific());
    }
}
