package org.birdwatch.wear;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.LocalDate;
public class GeoPolicyTest {
    @Test public void datesUseFourPeriodsPerMonth() {
        assertEquals(1,GeoPolicy.week(LocalDate.of(2026,1,1)));
        assertEquals(1,GeoPolicy.week(LocalDate.of(2026,1,7)));
        assertEquals(2,GeoPolicy.week(LocalDate.of(2026,1,8)));
        assertEquals(4,GeoPolicy.week(LocalDate.of(2026,1,31)));
        assertEquals(8,GeoPolicy.week(LocalDate.of(2024,2,29)));
        assertEquals(48,GeoPolicy.week(LocalDate.of(2026,12,31)));
    }
    @Test public void occurrenceFilterHasSeparateThresholdAndFailsOpenViaException() {
        assertArrayEquals(new boolean[]{false,true,true},GeoPolicy.mask(new float[]{.0299f,.03f,1f}));
        for(float[] invalid:new float[][]{{0,0},{Float.NaN},{Float.POSITIVE_INFINITY},{-1},{1.1f}}) {
            try{GeoPolicy.mask(invalid);fail("Expected invalid filter");}catch(IllegalArgumentException expected){}
        }
    }
}
