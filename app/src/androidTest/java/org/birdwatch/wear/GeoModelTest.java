package org.birdwatch.wear;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class GeoModelTest {
    @Test public void realGeoModelMatchesReferenceCoordinates() throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        long start=android.os.SystemClock.elapsedRealtime();
        boolean[] ny=GeoModel.predict(context,40.7,-74,20),sydney=GeoModel.predict(context,-33.87,151.21,20);
        assertEquals(6522,ny.length);assertEquals(135,count(ny));assertEquals(153,count(sydney));
        var labels=BirdNames.load(context,"en");
        for(int i=0;i<labels.size();i++) {
            if(labels.get(i).startsWith("Turdus migratorius_")){assertTrue(ny[i]);assertFalse(sydney[i]);}
            if(labels.get(i).startsWith("Gymnorhina tibicen_")){assertFalse(ny[i]);assertTrue(sydney[i]);}
        }
        android.util.Log.i("BirdWatchGeoTest","Two geo model loads and predictions: "+(android.os.SystemClock.elapsedRealtime()-start)+" ms");
        try(var engine=new BirdEngine(context)) {
            assertTrue(engine.identify(new float[BirdEngine.WINDOW],new boolean[6522]).isEmpty());
            try{engine.identify(new float[BirdEngine.WINDOW],new boolean[1]);fail("Bad mask accepted");}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void staleAndInaccurateLocationsAreRejectedWithoutRequestingGps() {
        var location=new android.location.Location("test");long now=300_000_000_000L;
        location.setLatitude(40.7);location.setLongitude(-74);location.setAccuracy(5000);location.setElapsedRealtimeNanos(now);
        assertTrue(LocationOnce.usable(location,now));
        location.setElapsedRealtimeNanos(now-121_000_000_000L);assertFalse(LocationOnce.usable(location,now));
        location.setElapsedRealtimeNanos(now);location.setAccuracy(21000);assertFalse(LocationOnce.usable(location,now));
        location.setAccuracy(10);location.setLatitude(Double.NaN);assertFalse(LocationOnce.usable(location,now));
        assertFalse(LocationOnce.usable(null,now));
    }
    private int count(boolean[] mask){int count=0;for(boolean value:mask)if(value)count++;return count;}
}
