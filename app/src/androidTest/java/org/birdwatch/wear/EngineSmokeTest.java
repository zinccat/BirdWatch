package org.birdwatch.wear;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.util.List;

/** Runs the packaged model on the device, so missing operators/ABI/assets fail here. */
@RunWith(AndroidJUnit4.class)
public class EngineSmokeTest {
    @Test public void testPackagedModelLoadsAndRunsTwoWindows() throws Exception {
        try (BirdEngine engine = new BirdEngine(InstrumentationRegistry.getInstrumentation().getTargetContext())) {
            float[] pcm=new float[BirdEngine.WINDOW*2];
            java.util.Random random=new java.util.Random(7);
            for(int i=0;i<pcm.length;i++)pcm[i]=(random.nextFloat()-.5f)*.002f;
            List<BirdEngine.Detection> results=engine.identify(pcm);
            assertTrue(results.size()<=3);
            for(var b:results) {assertTrue(Float.isFinite(b.score()));assertTrue(b.score()>=.35f && b.score()<=1f);assertFalse(b.common().isEmpty());}
        }
    }
    @Test public void testRepeatedStreamingWindowsOnDevice() throws Exception {
        try (BirdEngine engine=new BirdEngine(InstrumentationRegistry.getInstrumentation().getTargetContext())) {
            float[] pcm=new float[BirdEngine.WINDOW];
            long start=android.os.SystemClock.elapsedRealtime();
            for(int window=0;window<12;window++) {
                List<BirdEngine.Detection> results=engine.identify(pcm);
                assertTrue("Silence must not produce a strong bird detection",results.isEmpty() || results.get(0).score()<.5f);
            }
            android.util.Log.i("BirdWatchTest","12 streaming windows: "+(android.os.SystemClock.elapsedRealtime()-start)+" ms");
            Thread.currentThread().interrupt();
            try {engine.identify(pcm);fail("Cancellation ignored");}catch(InterruptedException expected) { }
            finally {Thread.interrupted();}
        }
    }
    @Test public void testLanguageLabelsAreIndexAligned() throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        var zh=BirdNames.load(context,"zh");var en=BirdNames.load(context,"en");var scientific=BirdNames.load(context,"scientific");
        assertEquals(6522,zh.size());assertEquals(zh.size(),en.size());assertEquals(zh.size(),scientific.size());
        for(int i=0;i<zh.size();i++) {
            String id=zh.get(i).split("_",2)[0];
            assertEquals(id,en.get(i).split("_",2)[0]);
            assertEquals(id+"_"+id,scientific.get(i));
        }
    }
    @Test public void testRejectsPartialWindow() throws Exception {
        try(BirdEngine engine=new BirdEngine(InstrumentationRegistry.getInstrumentation().getTargetContext())) {
            try {engine.identify(new float[100]);fail("Expected invalid length");}
            catch(IllegalArgumentException expected) { }
        }
    }
    @Test public void testCornellLinksUseExactScientificNames() throws Exception {
        var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("https://ebird.org/species/bkcchi",SpeciesLinks.ebird(context,"Poecile atricapillus"));
        assertEquals("",SpeciesLinks.ebird(context,"Unknown species"));
        assertEquals("",SpeciesLinks.urlForCode("../other"));
        assertEquals("",SpeciesLinks.urlForCode("https://example.com"));
    }
    @Test public void testPhotoMetadataRejectsUnlicensedAndWrongSpecies() throws Exception {
        var photo=new org.json.JSONObject().put("id",123).put("license_code","cc-by")
            .put("attribution","© Test Author, CC BY").put("medium_url","https://inaturalist-open-data.s3.amazonaws.com/photos/123/medium.jpg");
        assertNotNull(BirdPhotos.metadata(photo,"Poecile atricapillus"));
        for(String code:new String[]{"", "all-rights-reserved", "cc-by-nd", "cc-by-nc-nd", "cc-by-sa"}) {
            photo.put("license_code",code);assertNull(BirdPhotos.metadata(photo,"Poecile atricapillus"));
        }
        photo.put("license_code","cc-by-nc");
        photo.put("medium_url","https://example.com/photos/123/medium.jpg");assertNull(BirdPhotos.metadata(photo,"Bird"));
        photo.put("medium_url","https://inaturalist-open-data.s3.amazonaws.com/photos/124/medium.jpg");assertNull(BirdPhotos.metadata(photo,"Bird"));
        var taxon=new org.json.JSONObject().put("name","Poecile atricapillus").put("is_active",true).put("rank","species").put("iconic_taxon_name","Aves");
        var response=new org.json.JSONObject().put("results",new org.json.JSONArray().put(taxon));
        assertNotNull(BirdPhotos.exactTaxon(response,"Poecile atricapillus"));
        assertNull(BirdPhotos.exactTaxon(response,"Poecile carolinensis"));
        taxon.put("is_active",false);assertNull(BirdPhotos.exactTaxon(response,"Poecile atricapillus"));
        assertEquals("https://creativecommons.org/licenses/by-nc/4.0/",BirdPhotos.licenseUrl("cc-by-nc"));
    }
    @Test public void testDetailsDialogWithoutRecording() throws Exception {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        var context=instrumentation.getTargetContext();
        var intent=new android.content.Intent(context,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        android.app.Activity activity=instrumentation.startActivitySync(intent);
        final android.app.Dialog[] dialog=new android.app.Dialog[1];
        try {
            instrumentation.runOnMainSync(() -> {
                dialog[0]=BirdDetails.show(activity,new BirdEngine.Detection("Poecile atricapillus","Black-capped Chickadee",.81f),
                    "UI test - not a real detection",Runnable::run);
                assertTrue(dialog[0].isShowing());
            });
            instrumentation.waitForIdleSync();
            android.graphics.Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();
            assertNotNull(screenshot);
            try(java.io.FileOutputStream out=context.openFileOutput("details-test.png",0)) {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
            } finally {screenshot.recycle();}
        } finally {
            instrumentation.runOnMainSync(() -> {if(dialog[0]!=null)dialog[0].dismiss();activity.finish();});
        }
    }
}
