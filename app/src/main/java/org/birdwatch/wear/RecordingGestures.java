package org.birdwatch.wear;

import android.app.Activity;
import android.app.Dialog;
import android.os.Build;
import android.util.Log;
import android.view.Window;
import android.widget.Button;
import java.util.function.Consumer;

/** Optional foreground-only Wear SDK gestures; touch controls work on older watches. */
final class RecordingGestures {
    interface Binding {
        void bind(Window window, Button button, Runnable back);
        void clear();
    }
    private final Activity activity;
    private final Runnable back;
    private final Binding binding;
    private Button target;
    private Dialog dialog;
    private boolean started;

    RecordingGestures(Activity activity,Runnable back) {
        this.activity=activity;this.back=back;
        Binding candidate=null;
        if(Build.VERSION.SDK_INT>=36) {
            try {candidate=WearBinding.create(activity);}
            catch(RuntimeException | LinkageError e){Log.i("BirdWatch","Wear gestures unavailable",e);}
        }
        binding=candidate;
    }
    void setTarget(Button button) {target=button;update();}
    void showDialog(Dialog dialog) {
        this.dialog=dialog;
        dialog.setOnDismissListener(ignored -> {if(this.dialog==dialog){this.dialog=null;update();}});
        update();
    }
    void refresh() {update();}
    void start() {started=true;update();}
    void stop() {started=false;update();}
    private void update() {
        if(binding==null)return;
        try {
            binding.clear();
            if(!started)return;
            if(dialog!=null && dialog.isShowing())binding.bind(dialog.getWindow(),null,dialog::dismiss);
            else binding.bind(activity.getWindow(),target,back);
        }catch(RuntimeException | LinkageError e){Log.w("BirdWatch","Unable to update gesture binding",e);}
    }

    /** Loaded only on devices with the optional Wear SDK library. */
    private static final class WearBinding implements Binding {
        private static final String EXPERIENCE="birdwatch_recording";
        private final Activity activity;
        private final com.google.wear.input.GestureInputManager manager;
        private Consumer<com.google.wear.input.GestureEvent> listener;
        private int generation;

        static Binding create(Activity activity) {
            if(!com.google.wear.Sdk.hasApiFeature(com.google.wear.Sdk.FEATURE_WEAR_GESTURE_DETECTION))return null;
            var manager=com.google.wear.Sdk.getWearManager(activity,com.google.wear.input.GestureInputManager.class);
            return manager==null ? null : new WearBinding(activity,manager);
        }
        WearBinding(Activity activity,com.google.wear.input.GestureInputManager manager) {
            this.activity=activity;this.manager=manager;
        }
        @Override public void bind(Window window,Button button,Runnable back) {
            if(window==null || window.peekDecorView()==null)return;
            int primary=com.google.wear.input.GestureEvent.ACTION_PRIMARY;
            int dismiss=com.google.wear.input.GestureEvent.ACTION_DISMISS;
            boolean hasPrimary=button!=null && manager.isActionSupported(primary);
            boolean hasDismiss=manager.isActionSupported(dismiss);
            int[] actions=hasPrimary ? (hasDismiss?new int[]{primary,dismiss}:new int[]{primary})
                : (hasDismiss?new int[]{dismiss}:new int[0]);
            if(actions.length==0)return;
            int current=++generation;
            listener=event -> {
                if(generation!=current || activity.isFinishing() || activity.isDestroyed()
                    || !window.getDecorView().hasWindowFocus())return;
                if(event.getAction()==dismiss) {
                    manager.notifyGestureConsumed(EXPERIENCE,dismiss);
                    back.run();
                }else if(event.getAction()==primary && button!=null && button.isAttachedToWindow()
                    && button.isShown() && button.isEnabled()) {
                    manager.notifyGestureConsumed(EXPERIENCE,primary);
                    button.performClick();
                }
            };
            manager.addGestureEventListener(actions,window,activity.getMainExecutor(),listener);
        }
        @Override public void clear() {
            generation++;
            if(listener!=null){manager.removeGestureEventListener(listener);listener=null;}
        }
    }
}
