package org.birdwatch.wear;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class LanguageSettingsTest {
    @Test public void defaultsAndIndependentPreferences() throws Exception {
        var instrumentation=InstrumentationRegistry.getInstrumentation();var context=instrumentation.getTargetContext();
        var prefs=context.getSharedPreferences("birdwatch_settings",0);
        String ui=prefs.getString("ui_language",null),bird=prefs.getString("bird_language",null);
        try {
            prefs.edit().remove("ui_language").remove("bird_language").commit();
            assertEquals("en",AppLanguage.language(context));assertEquals("en",BirdNames.language(context));
            assertEquals("●  Start listening",AppLanguage.apply(context).getString(R.string.start));
            BirdNames.setLanguage(context,"zh");assertEquals("en",AppLanguage.language(context));
            AppLanguage.set(context,"zh");BirdNames.setLanguage(context,"en");
            assertEquals("zh",AppLanguage.language(context));assertEquals("en",BirdNames.language(context));
            assertNotEquals("●  Start listening",AppLanguage.apply(context).getString(R.string.start));
            BirdNames.setLanguage(context,"scientific");assertEquals("zh",AppLanguage.language(context));
            for(String language:new String[]{"zh","en"}) {
                AppLanguage.set(context,language);
                var intent=new android.content.Intent(context,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                android.app.Activity activity=instrumentation.startActivitySync(intent);
                try {
                    instrumentation.runOnMainSync(() -> {
                        assertEquals(language,activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
                        activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    });
                    instrumentation.waitForIdleSync();android.os.SystemClock.sleep(700);
                    var bitmap=instrumentation.getUiAutomation().takeScreenshot();
                    try(var out=context.openFileOutput("language-"+language+".png",0)) {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
                }finally{instrumentation.runOnMainSync(activity::finish);instrumentation.waitForIdleSync();}
            }
        }finally {
            var edit=prefs.edit();if(ui==null)edit.remove("ui_language");else edit.putString("ui_language",ui);
            if(bird==null)edit.remove("bird_language");else edit.putString("bird_language",bird);
            edit.commit();
        }
    }
}
