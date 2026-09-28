package org.birdwatch.wear;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import androidx.wear.remote.interactions.RemoteActivityHelper;
import java.util.Locale;
import java.util.concurrent.Executor;

/** In-activity dialog: inspecting a record does not stop foreground streaming. */
public final class BirdDetails {
    private static final int BG=Color.rgb(11,18,14), GREEN=Color.rgb(201,245,139), MUTED=Color.rgb(152,174,157);
    private BirdDetails() {}
    public static Dialog show(Activity activity, BirdEngine.Detection bird, String time, Executor linksExecutor) {
        return show(activity,bird,time,linksExecutor,null);
    }
    public static Dialog show(Activity activity, BirdEngine.Detection bird, String time, Executor linksExecutor, BirdPhotos.Photo photo) {
        return show(activity,bird,time,linksExecutor,photo,null);
    }
    public static Dialog show(Activity activity, BirdEngine.Detection bird, String time, Executor linksExecutor, BirdPhotos.Photo photo, BirdPhotos loader) {
        Dialog dialog=new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll=new ScrollView(activity);scroll.setBackgroundColor(BG);scroll.setFillViewport(true);
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(activity,25),dp(activity,25),dp(activity,25),dp(activity,30));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        label(activity,content,time,11,MUTED);
        label(activity,content,bird.common(),21,GREEN).setTypeface(null,Typeface.BOLD);
        if(!bird.common().equals(bird.scientific()))label(activity,content,bird.scientific(),12,MUTED);
        label(activity,content,String.format(activity.getResources().getConfiguration().getLocales().get(0),activity.getString(R.string.confidence),bird.score()*100),13,Color.WHITE);
        String link;
        try {link=SpeciesLinks.ebird(activity,bird.scientific());}catch(java.io.IOException e){link="";}
        if(!link.isEmpty()) {
            String url=link;
            remoteButton(activity,content,dialog,activity.getString(R.string.open_ebird),url,linksExecutor,R.string.sent_check);
        } else label(activity,content,activity.getString(R.string.ebird_missing),12,MUTED);
        LinearLayout photoContent=new LinearLayout(activity);
        photoContent.setOrientation(LinearLayout.VERTICAL);
        photoContent.setVisibility(View.GONE);
        content.addView(photoContent,new LinearLayout.LayoutParams(-1,-2));
        boolean hasPhoto=photo!=null && photo.scientific().equals(bird.scientific());
        if(hasPhoto)renderPhoto(activity,photoContent,bird,photo);
        button(activity,content,activity.getString(R.string.back)).setOnClickListener(v -> dialog.dismiss());
        scroll.setFocusableInTouchMode(true);scroll.requestFocus();
        scroll.setOnGenericMotionListener((v,event) -> {
            if(event.getAction()==MotionEvent.ACTION_SCROLL){scroll.smoothScrollBy(0,(int)(-event.getAxisValue(MotionEvent.AXIS_SCROLL)*dp(activity,36)));return true;}return false;
        });
        dialog.setContentView(scroll);dialog.show();
        if(dialog.getWindow()!=null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(-1,-1);
        }
        if(!hasPhoto && loader!=null)loader.load(bird.scientific(),loaded -> {
            if(!dialog.isShowing() || activity.isDestroyed() || loaded==null
                || !loaded.scientific().equals(bird.scientific()))return;
            renderPhoto(activity,photoContent,bird,loaded);
        });
        return dialog;
    }
    private static void renderPhoto(Activity activity,LinearLayout content,BirdEngine.Detection bird,BirdPhotos.Photo photo) {
        content.removeAllViews();
        ImageView image=new ImageView(activity);image.setImageBitmap(photo.bitmap());
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription(bird.common()+activity.getString(R.string.reference_description));
        content.addView(image,new LinearLayout.LayoutParams(-1,dp(activity,110)));
        label(activity,content,photo.attribution(),11,Color.WHITE);
        label(activity,content,"iNaturalist · "+photo.license().toUpperCase(Locale.ROOT),10,MUTED);
        content.setVisibility(View.VISIBLE);
    }
    private static void remoteButton(Activity activity,LinearLayout content,Dialog dialog,String title,
            String url,Executor executor,int successMessage) {
        TextView feedback=label(activity,content,"",10,MUTED);
        feedback.setVisibility(View.GONE);
        Button button=button(activity,content,title);
        button.setOnClickListener(v -> {
            button.setEnabled(false);
            feedback.setVisibility(View.VISIBLE);
            feedback.setText(R.string.opening_phone);
            try {
                var future=new RemoteActivityHelper(activity.getApplicationContext(),executor).startRemoteActivity(
                    new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE),null);
                future.addListener(() -> {
                    if(!dialog.isShowing() || activity.isDestroyed())return;
                    button.setEnabled(true);
                    try {future.get();feedback.setText(activity.getString(successMessage));}
                    catch(Exception e){feedback.setText(activity.getString(R.string.phone_retry));}
                },activity.getMainExecutor());
            }catch(Exception e){button.setEnabled(true);feedback.setText(activity.getString(R.string.open_retry));}
        });
    }
    private static int dp(Context context,int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    private static TextView label(Context context,LinearLayout parent,String text,int size,int color) {
        TextView view=new TextView(context);view.setText(text);view.setTextSize(size);view.setTextColor(color);view.setGravity(Gravity.CENTER);
        view.setPadding(0,dp(context,4),0,dp(context,4));parent.addView(view,new LinearLayout.LayoutParams(-1,-2));return view;
    }
    private static Button button(Context context,LinearLayout parent,String text) {
        Button view=new Button(context);view.setText(text);view.setTextSize(13);view.setAllCaps(false);view.setTextColor(GREEN);
        view.setPadding(dp(context,6),0,dp(context,6),0);
        GradientDrawable shape=new GradientDrawable();shape.setColor(Color.rgb(29,43,32));shape.setCornerRadius(dp(context,28));view.setBackground(shape);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(context,48));params.topMargin=dp(context,7);parent.addView(view,params);return view;
    }
}
