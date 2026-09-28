package org.birdwatch.wear;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
/** Explicit live-network smoke test. Synthetic UI fixture, never records audio or history. */
@RunWith(AndroidJUnit4.class)
public class PhotoNetworkTest {
    @Test public void licensedPhotoLoadsAndRendersOnWatch() throws Exception {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        var context=instrumentation.getTargetContext();
        BirdPhotos.Photo photo;
        try(var photos=new BirdPhotos(context)) {
            photo=photos.fetch("Poecile atricapillus");
            assertNotNull("Requires internet and an available licensed reference photo",photo);
            assertFalse(photo.attribution().isEmpty());assertTrue(photo.bitmap().getWidth()<=640);
        }
        var intent=new android.content.Intent(context,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        android.app.Activity activity=instrumentation.startActivitySync(intent);
        try {
            instrumentation.runOnMainSync(() -> {
                try {
                    var render=MainActivity.class.getDeclaredMethod("renderListening");render.setAccessible(true);render.invoke(activity);
                    var bg=MainActivity.class.getDeclaredField("backdrop");bg.setAccessible(true);
                    ((android.widget.ImageView)bg.get(activity)).setImageBitmap(photo.bitmap());
                    var credit=MainActivity.class.getDeclaredField("photoCredit");credit.setAccessible(true);
                    ((android.widget.TextView)credit.get(activity)).setText("Black-capped Chickadee - Reference photo\n"+photo.credit()+"\niNaturalist · Source and license ↗");
                    var status=MainActivity.class.getDeclaredField("status");status.setAccessible(true);
                    ((android.widget.TextView)status.get(activity)).setText("Test - Black-capped Chickadee");
                    var detail=MainActivity.class.getDeclaredField("detail");detail.setAccessible(true);
                    ((android.widget.TextView)detail.get(activity)).setText("UI test - not a real detection");
                }catch(Exception e){throw new RuntimeException(e);}
            });
            instrumentation.waitForIdleSync();
            android.os.SystemClock.sleep(700); // Allow the new view tree to reach the compositor before capture.
            android.graphics.Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();
            assertNotNull(screenshot);
            try(java.io.FileOutputStream out=context.openFileOutput("photo-test.png",0)) {screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
            finally {screenshot.recycle();}
        }finally {instrumentation.runOnMainSync(activity::finish);}
    }
}
