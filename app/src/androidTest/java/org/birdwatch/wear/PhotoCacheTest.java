package org.birdwatch.wear;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;
import java.util.concurrent.*;
@RunWith(AndroidJUnit4.class)
public class PhotoCacheTest {
    private BirdPhotos.Metadata metadata(String species) {
        return new BirdPhotos.Metadata(species,"© Test author, CC BY","Test author","cc-by",123,
            "https://inaturalist-open-data.s3.amazonaws.com/photos/123/medium.jpg");
    }
    private File directory(){return new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),"photo-test-"+System.nanoTime());}
    @Test public void persistsAttributionEvictsOldestAndRejectsCorruption() throws Exception {
        File directory=directory();PhotoDiskCache cache=new PhotoDiskCache(directory,4500);
        try {
            cache.put(metadata("Bird A"),new byte[1600]);cache.put(metadata("Bird B"),new byte[1600]);
            assertEquals(2,cache.stats().count());
            for(File file:directory.listFiles())file.setLastModified(1);
            assertNotNull(cache.get("Bird A"));
            cache.put(metadata("Bird C"),new byte[1600]);
            assertNull(cache.get("Bird B"));assertNotNull(cache.get("Bird A"));assertNotNull(cache.get("Bird C"));
            assertTrue(cache.stats().bytes()<=4500);
            var reopened=new PhotoDiskCache(directory,4500);var entry=reopened.get("Bird A");
            assertEquals("© Test author, CC BY",entry.metadata().attribution());assertEquals("cc-by",entry.metadata().license());
            assertEquals(1600,entry.image().length);
            for(File file:directory.listFiles())try(var out=new FileOutputStream(file)){out.write(1);}
            assertNull(reopened.get("Bird A"));assertNull(reopened.get("Bird C"));assertEquals(0,reopened.stats().count());
        }finally{cache.clear();directory.delete();}
    }
    @Test public void newLoaderDisplaysDiskPhotoAndClearRemovesBothCaches() throws Exception {
        File directory=directory();PhotoDiskCache disk=new PhotoDiskCache(directory);
        var bitmap=android.graphics.Bitmap.createBitmap(4,4,android.graphics.Bitmap.Config.ARGB_8888);
        byte[] bytes;
        try(var out=new ByteArrayOutputStream()){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);bytes=out.toByteArray();}finally{bitmap.recycle();}
        disk.put(metadata("Offline cached species"),bytes);
        try(var photos=new BirdPhotos(directory)) {
            CountDownLatch loaded=new CountDownLatch(1);BirdPhotos.Photo[] result=new BirdPhotos.Photo[1];
            photos.load("Offline cached species",photo->{result[0]=photo;loaded.countDown();});
            assertTrue(loaded.await(3,TimeUnit.SECONDS));assertNotNull(result[0]);assertEquals(4,result[0].bitmap().getWidth());
            assertEquals("Test author",result[0].author());
            CountDownLatch cleared=new CountDownLatch(1);boolean[] success={false};
            photos.clearCache(ok->{success[0]=ok;cleared.countDown();});
            assertTrue(cleared.await(3,TimeUnit.SECONDS));assertTrue(success[0]);assertEquals(0,disk.stats().count());
            // New on-disk content must be read, proving the old in-memory entry was evicted too.
            disk.put(new BirdPhotos.Metadata("Offline cached species","© Other author, CC BY","Other author","cc-by",123,
                "https://inaturalist-open-data.s3.amazonaws.com/photos/123/medium.jpg"),bytes);
            CountDownLatch reloaded=new CountDownLatch(1);
            photos.load("Offline cached species",photo->{result[0]=photo;reloaded.countDown();});
            assertTrue(reloaded.await(3,TimeUnit.SECONDS));assertEquals("Other author",result[0].author());
        }finally{disk.clear();directory.delete();}
    }
    private boolean hasPhoto(android.view.View view) {
        if(view instanceof android.widget.ImageView image && image.getDrawable()!=null)return true;
        if(view instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(hasPhoto(group.getChildAt(i)))return true;
        return false;
    }
    @Test public void historyDetailsLoadDiskPhotoWithoutCurrentSessionPhoto() throws Exception {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        var context=instrumentation.getTargetContext();
        File directory=directory();PhotoDiskCache disk=new PhotoDiskCache(directory);
        var bitmap=android.graphics.Bitmap.createBitmap(4,4,android.graphics.Bitmap.Config.ARGB_8888);
        try(var out=new ByteArrayOutputStream()) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
            disk.put(metadata("Poecile atricapillus"),out.toByteArray());
        }finally{bitmap.recycle();}
        var activity=instrumentation.startActivitySync(new android.content.Intent(context,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            instrumentation.runOnMainSync(() -> {
                try {
                    var field=MainActivity.class.getDeclaredField("photos");field.setAccessible(true);
                    ((BirdPhotos)field.get(activity)).close();field.set(activity,new BirdPhotos(directory));
                    var current=MainActivity.class.getDeclaredField("currentPhoto");current.setAccessible(true);
                    assertNull(current.get(activity));
                    var show=MainActivity.class.getDeclaredMethod("showBird",BirdEngine.Detection.class,String.class);show.setAccessible(true);
                    show.invoke(activity,new BirdEngine.Detection("Poecile atricapillus","Black-capped Chickadee",.8f),"Photo cache test");
                }catch(Exception e){throw new AssertionError(e);}
            });
            java.util.concurrent.atomic.AtomicBoolean visible=new java.util.concurrent.atomic.AtomicBoolean();
            long deadline=android.os.SystemClock.elapsedRealtime()+5000;
            do {
                instrumentation.runOnMainSync(() -> {
                    try {
                        var field=MainActivity.class.getDeclaredField("birdDialog");field.setAccessible(true);
                        var dialog=(android.app.Dialog)field.get(activity);
                        visible.set(dialog.isShowing() && hasPhoto(dialog.getWindow().getDecorView()));
                    }catch(Exception e){throw new AssertionError(e);}
                });
                if(visible.get())break;
                android.os.SystemClock.sleep(50);
            }while(android.os.SystemClock.elapsedRealtime()<deadline);
            assertTrue("History details should restore a cached photo without a live detection",visible.get());
        }finally{instrumentation.runOnMainSync(activity::finish);instrumentation.waitForIdleSync();disk.clear();directory.delete();}
    }
}
