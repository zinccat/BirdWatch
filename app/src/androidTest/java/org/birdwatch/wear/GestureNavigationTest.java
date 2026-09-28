package org.birdwatch.wear;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.WindowManager;
import android.widget.Button;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real gesture-service dispatch with click probes; never starts audio or GPS. */
@RunWith(AndroidJUnit4.class)
public class GestureNavigationTest {
    private static Object field(Object target,String name) {
        try {var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
        catch(Exception e){throw new AssertionError(e);}
    }
    private static void page(Activity activity,String name) {
        try {var method=MainActivity.class.getDeclaredMethod(name);method.setAccessible(true);method.invoke(activity);}
        catch(Exception e){throw new AssertionError(e);}
    }
    private static void shell(String command) throws Exception {
        try(var input=new ParcelFileDescriptor.AutoCloseInputStream(
                InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command))) {
            while(input.read()!=-1) {}
        }
    }
    private static void awaitMain(BooleanSupplier condition) {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        long deadline=SystemClock.elapsedRealtime()+5000;
        AtomicBoolean ready=new AtomicBoolean();
        do {
            instrumentation.runOnMainSync(() -> ready.set(condition.getAsBoolean()));
            if(ready.get())return;
            SystemClock.sleep(50);
        }while(SystemClock.elapsedRealtime()<deadline);
        fail("Expected UI state was not reached");
    }
    @Test public void primaryAndBackGesturesFollowCurrentPageWithoutRecording() throws Exception {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        var context=instrumentation.getTargetContext();
        Activity activity=instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        AtomicInteger clicks=new AtomicInteger();
        try {
            instrumentation.runOnMainSync(() -> activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
            shell("input keyevent KEYCODE_WAKEUP");
            shell("cmd IWearGestureService override-constraints offbody-state");
            awaitMain(activity::hasWindowFocus);
            instrumentation.runOnMainSync(() -> {
                Object gestures=field(activity,"gestures");
                assertNotNull(field(gestures,"binding"));
                ((Button)field(gestures,"target")).setOnClickListener(v -> clicks.incrementAndGet());
            });
            shell("cmd IWearGestureService gesture DoublePinch");
            awaitMain(() -> clicks.get()==1);
            instrumentation.runOnMainSync(() -> {
                page(activity,"renderListening");
                ((Button)field(field(activity,"gestures"),"target")).setOnClickListener(v -> clicks.incrementAndGet());
            });
            shell("cmd IWearGestureService gesture DoublePinch");
            awaitMain(() -> clicks.get()==2);
            instrumentation.runOnMainSync(() -> {page(activity,"settingsMenu");page(activity,"languages");});
            shell("cmd IWearGestureService gesture WristTurn");
            awaitMain(() -> (int)field(activity,"settingsDepth")==1 && !activity.isFinishing());
            shell("cmd IWearGestureService gesture WristTurn");
            awaitMain(() -> (boolean)field(activity,"isHome") && !activity.isFinishing());
            instrumentation.runOnMainSync(() -> {page(activity,"settingsMenu");page(activity,"languages");});
            shell("input keyevent KEYCODE_BACK");
            awaitMain(() -> (int)field(activity,"settingsDepth")==1 && !activity.isFinishing());
            instrumentation.runOnMainSync(() -> {
                try {
                    var method=MainActivity.class.getDeclaredMethod("showBird",BirdEngine.Detection.class,String.class);method.setAccessible(true);
                    method.invoke(activity,new BirdEngine.Detection("Poecile atricapillus","Black-capped Chickadee",.81f),"Gesture UI test");
                }catch(Exception e){throw new AssertionError(e);}
            });
            awaitMain(() -> ((Dialog)field(activity,"birdDialog")).getWindow().getDecorView().hasWindowFocus());
            shell("cmd IWearGestureService gesture WristTurn");
            awaitMain(() -> !((Dialog)field(activity,"birdDialog")).isShowing() && !activity.isFinishing());
            assertEquals(2,clicks.get());
        }finally {
            shell("cmd IWearGestureService override-constraints reset");
            instrumentation.runOnMainSync(activity::finish);
        }
    }
}
