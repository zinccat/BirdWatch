package org.birdwatch.wear;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(11,18,14), GREEN = Color.rgb(201,245,139), MUTED = Color.rgb(152,174,157);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService linksExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService geoExecutor=Executors.newSingleThreadExecutor();
    private LocationOnce locationRequest;
    private Future<?> geoTask;
    private volatile boolean[] speciesAllowed;
    private TextView geoStatus;
    private Dialog birdDialog;
    private BirdPhotos photos;
    private final PhotoSelection photoSelection = new PhotoSelection();
    private ImageView backdrop;
    private TextView photoCredit;
    private BirdPhotos.Photo currentPhoto;
    private BirdEngine.Detection photoBird;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Future<?> task;
    private Recorder recorder;
    private LatestWindow<Recorder.Window> streamWindows;
    private LinearLayout column;
    private TextView status, detail, liveCandidates, sessionRows;
    private final SessionLog sessionLog = new SessionLog();
    private WaveView wave;
    private boolean busy, stopped, isHome;
    private int generation;
    private int settingsDepth;
    private RecordingGestures gestures;

    @Override protected void attachBaseContext(Context base) {super.attachBaseContext(AppLanguage.apply(base));}
    @Override public void onCreate(Bundle state) { super.onCreate(state); photos=new BirdPhotos(this); gestures=new RecordingGestures(this,this::goBack); if(state!=null && state.getBoolean("settings_open"))settingsMenu();else home(); if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::goBack); }
    @Override protected void onSaveInstanceState(Bundle state) {super.onSaveInstanceState(state);state.putBoolean("settings_open",settingsDepth>0);}
    @Override protected void onStart() { super.onStart(); stopped = false; gestures.start(); }
    @Override protected void onStop() { stopped = true; gestures.stop(); if(birdDialog!=null)birdDialog.dismiss(); if (busy) { cancel(); home(); } super.onStop(); }
    @Override protected void onDestroy() { cancel(); executor.shutdownNow(); linksExecutor.shutdownNow(); photos.close(); geoExecutor.shutdownNow(); super.onDestroy(); }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void page(String eyebrow) {
        if(gestures!=null)gestures.setTarget(null);
        isHome = false;
        settingsDepth = 0;
        FrameLayout frame = new FrameLayout(this); frame.setBackgroundColor(BG);
        backdrop = new ImageView(this); backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(.48f); frame.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
        photoCredit=null;
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        scroll.setClipToPadding(false); scroll.setVerticalScrollBarEnabled(false);
        column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL); column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(dp(25), dp(25), dp(25), dp(30));
        scroll.addView(column, new ScrollView.LayoutParams(-1, -2)); frame.addView(scroll,new FrameLayout.LayoutParams(-1,-1)); setContentView(frame);
        if(gestures!=null)gestures.refresh();
        scroll.setOnGenericMotionListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_SCROLL) {
                scroll.smoothScrollBy(0, (int) (-event.getAxisValue(MotionEvent.AXIS_SCROLL) * dp(36))); return true;
            } return false;
        });
        scroll.setFocusable(true); scroll.setFocusableInTouchMode(true); scroll.requestFocus();
        if (!eyebrow.isEmpty()) text(eyebrow, 10, MUTED);
    }
    private TextView text(CharSequence content, int size, int color) {
        TextView v = new TextView(this); v.setText(content); v.setTextSize(size); v.setTextColor(color);
        v.setShadowLayer(dp(2),0,dp(1),Color.BLACK);
        v.setGravity(Gravity.CENTER); v.setPadding(0, dp(3), 0, dp(3)); column.addView(v, new LinearLayout.LayoutParams(-1, -2)); return v;
    }
    private void gap(int height) { View v = new View(this); column.addView(v, new LinearLayout.LayoutParams(1,dp(height))); }
    private Button button(String title, boolean primary, Runnable action) {
        Button b = new Button(this); b.setText(title); b.setAllCaps(false); b.setTextSize(14); b.setTextColor(primary ? BG : GREEN);
        b.setGravity(Gravity.CENTER); b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48)); b.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable shape = new GradientDrawable(); shape.setColor(primary ? GREEN : Color.rgb(29,43,32)); shape.setCornerRadius(dp(28)); b.setBackground(shape);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,dp(48)); lp.topMargin=dp(7); column.addView(b,lp);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private void recordingButton(String title,Runnable action) {
        Button control=button(title,true,action);
        gestures.setTarget(control);
    }
    private Button coloredButton(String title,int foreground,int background,Runnable action) {
        Button control=button(title,false,action);
        control.setTextColor(foreground);
        ((GradientDrawable)control.getBackground()).setColor(background);
        return control;
    }
    private void homeButton() {
        coloredButton(getString(R.string.home),Color.rgb(227,231,235),Color.rgb(46,52,60),this::home);
    }
    private void home() {
        page("");
        text(getString(R.string.app_title), 27, GREEN).setTypeface(null, Typeface.BOLD);
        text(getString(R.string.tagline), 13, Color.WHITE);
        gap(3);
        recordingButton(getString(R.string.start),this::requestListen);
        text(getString(R.string.offline), 10, MUTED);
        button(getString(R.string.history), false, this::history);
        button(getString(R.string.app_settings), false, this::settingsMenu);
        isHome = true;
    }
    private void settingsMenu() {
        page(getString(R.string.app_settings));
        settingsDepth = 1;
        button(getString(R.string.language_menu)+BirdNames.title(this,AppLanguage.language(this)), false, this::appLanguages);
        button(getString(R.string.bird_language)+BirdNames.title(this), false, this::languages);
        button(getString(R.string.geo_menu)+(GeoModel.enabled(this)?getString(R.string.geo_on):getString(R.string.geo_off)),false,this::geoSettings);
        button(getString(R.string.photo_cache),false,this::photoCache);
        button(getString(R.string.about), false, this::about);
        homeButton();
    }
    private void geoSettings() {
        page(getString(R.string.geo_title));
        settingsDepth = 2;
        for(boolean enabled:new boolean[]{true,false})button(getString(enabled?R.string.geo_on:R.string.geo_off),GeoModel.enabled(this)==enabled,
            () -> {GeoModel.setEnabled(this,enabled);settingsMenu();});
        text(getString(R.string.geo_hint),11,MUTED);
        button(getString(R.string.settings),false,() -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName()))));
        button(getString(R.string.back),false,this::settingsMenu);
    }
    private void photoCache() {
        page(getString(R.string.photo_cache));
        settingsDepth = 2;
        TextView info=text(getString(R.string.cache_loading),12,Color.WHITE);
        photos.cacheStats(stats -> {if(info.isAttachedToWindow())info.setText(String.format(
            getResources().getConfiguration().getLocales().get(0),getString(R.string.cache_size),stats.count(),stats.bytes()/1048576.0));});
        text(getString(R.string.cache_hint),11,MUTED);
        Button clear=button(getString(R.string.cache_clear),false,() -> {});
        clear.setOnClickListener(v -> {
            clear.setEnabled(false);
            photos.clearCache(ok -> {
                currentPhoto=null;photoBird=null;photoSelection.clear();
                if(info.isAttachedToWindow()){clear.setEnabled(true);info.setText(getString(ok?R.string.cache_cleared:R.string.cache_clear_failed));}
            });
        });
        button(getString(R.string.back),false,this::settingsMenu);
    }
    private void appLanguages() {
        page(getString(R.string.ui_language));text(getString(R.string.choose_ui),21,GREEN);
        settingsDepth = 2;
        for(String code:new String[]{"en","zh"}) {
            boolean selected=code.equals(AppLanguage.language(this));
            button(BirdNames.title(this,code)+(selected?" ✓":""),selected,
                () -> {AppLanguage.set(this,code);recreate();});
        }
        text(getString(R.string.ui_hint),11,MUTED);
        button(getString(R.string.back),false,this::settingsMenu);
    }
    private void languages() {
        page(getString(R.string.bird_language_title)); text(getString(R.string.choose_names),21,GREEN);
        settingsDepth = 2;
        for(int i=0;i<BirdNames.CODES.length;i++) {
            String code=BirdNames.CODES[i];
            button(BirdNames.title(this,code)+(code.equals(BirdNames.language(this))?" ✓":""),
                code.equals(BirdNames.language(this)),() -> {BirdNames.setLanguage(this,code);settingsMenu();});
        }
        text(getString(R.string.names_hint),11,MUTED);
        button(getString(R.string.back),false,this::settingsMenu);
    }
    private void requestListen() {
        if (busy) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (getPreferences(0).getBoolean("asked", false) && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                page(getString(R.string.mic_permission)); text(getString(R.string.mic_required),20,GREEN); text(getString(R.string.mic_hint),13,Color.WHITE);
                button(getString(R.string.settings),true,() -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:"+getPackageName()))));
                button(getString(R.string.back),false,this::home); return;
            }
            getPreferences(0).edit().putBoolean("asked",true).apply();
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 42); return;
        }
        if(GeoModel.enabled(this) && !LocationOnce.hasPermission(this) && !getPreferences(0).getBoolean("location_asked",false)) {
            getPreferences(0).edit().putBoolean("location_asked",true).apply();
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},43);return;
        }
        listen();
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 42 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED && !stopped) requestListen();
        else if(requestCode==43 && !stopped)listen();
        else if (requestCode == 42) error(getString(R.string.mic_denied), getString(R.string.mic_retry));
    }
    private void listen() {
        busy=true; int run=++generation; sessionLog.clear(); photoSelection.clear(); currentPhoto=null; photoBird=null; speciesAllowed=null;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        renderListening();
        requestSessionLocation(run);
        recorder=new Recorder(); Recorder current=recorder;
        LatestWindow<Recorder.Window> windows=new LatestWindow<>(); streamWindows=windows;
        DetectionGate gate=new DetectionGate();
        task=executor.submit(() -> {
            Thread capture=null;
            try (BirdEngine engine = new BirdEngine(getApplicationContext())) {
                if (Thread.currentThread().isInterrupted()) return;
                post(run,() -> status.setText(""));
                capture=new Thread(() -> {
                    try {
                        current.stream(windows,(seconds,level) -> post(run,() -> {
                            detail.setText(String.format(getResources().getConfiguration().getLocales().get(0),getString(R.string.listening_time),(int)seconds/60,(int)seconds%60));
                            wave.level=level; wave.fraction=seconds; wave.invalidate();
                        }));
                    } catch(Exception e) { windows.fail(e); }
                    finally { windows.close(); }
                },"BirdWatch-microphone");
                capture.start();
                while (!Thread.currentThread().isInterrupted()) {
                    Recorder.Window window=windows.take();
                    if(window==null) break;
                    List<BirdEngine.Detection> found=engine.identify(window.pcm(),speciesAllowed);
                    windows.checkFailure();
                    post(run,() -> {
                        liveCandidates.setVisibility(View.VISIBLE);
                        if(found.isEmpty()) {
                            liveCandidates.setText(getString(R.string.no_candidate));
                            gate.accept("",0,window.endedAtMs());
                        } else {
                            BirdEngine.Detection top=found.get(0);
                            status.setText(top.common());
                            updatePhoto(top);
                            StringBuilder lines=new StringBuilder(top.score()>=.5f ? getString(R.string.possible) : getString(R.string.uncertain));
                            lines.append("\n").append(getString(R.string.confidence,top.score()*100));
                            for(int i=1;i<found.size();i++) {
                                var candidate=found.get(i);
                                lines.append(String.format(getResources().getConfiguration().getLocales().get(0),"\n%s  %.0f%%",candidate.common(),candidate.score()*100));
                            }
                            if(gate.accept(top.scientific(),top.score(),window.endedAtMs())) {
                                save(found); lines.append(getString(R.string.saved));
                            }
                            liveCandidates.setText(lines.toString());
                            for(var b:found) sessionLog.append(window.audioEndMs(),b.scientific(),b.common(),b.score());
                            sessionRows.setText(sessionText());
                        }
                    });
                }
            } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            catch (Exception e) {
                android.util.Log.e("BirdWatch","Streaming failed",e);
                post(run,() -> { cancel(); error(getString(R.string.stopped),getString(R.string.stream_error)); });
            } finally {
                current.cancel(); windows.close();
                if(capture!=null) {
                    capture.interrupt();
                    // Keep cleanup off the UI thread; prevents a new session racing the microphone.
                    boolean interrupted=Thread.interrupted();
                    try { capture.join(); } catch(InterruptedException e) { interrupted=true; }
                    if(interrupted) Thread.currentThread().interrupt();
                }
            }
        });
    }
    private void requestSessionLocation(int run) {
        if(!GeoModel.enabled(this)){geoStatus.setText(getString(R.string.geo_disabled));return;}
        if(!LocationOnce.hasPermission(this)){geoStatus.setText(getString(R.string.geo_no_permission));return;}
        locationRequest=new LocationOnce(this);
        locationRequest.request(location -> {
            if(generation!=run || stopped)return;
            if(location==null){geoStatus.setText(getString(R.string.geo_unavailable));return;}
            geoStatus.setText(getString(R.string.geo_building));
            int week=GeoPolicy.week(java.time.LocalDate.now());
            geoTask=geoExecutor.submit(() -> {
                try {
                    boolean[] allowed=GeoModel.predict(getApplicationContext(),location.getLatitude(),location.getLongitude(),week);
                    int total=0;for(boolean item:allowed)if(item)total++;int count=total;
                    post(run,() -> {speciesAllowed=allowed;geoStatus.setText(String.format(
                        getResources().getConfiguration().getLocales().get(0),getString(R.string.geo_active),count));});
                }catch(Exception e){post(run,()->geoStatus.setText(getString(R.string.geo_unavailable)));}
            });
        });
    }
    private void renderListening() {
        page(getString(R.string.live)); status=text(getString(R.string.loading_model),18,GREEN);
        status.setMaxLines(2); status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        detail=text(getString(R.string.listening_time,0,0),10,MUTED);
        photoCredit=text("",10,Color.WHITE);
        photoCredit.setVisibility(View.GONE);
        photoCredit.setOnClickListener(v -> {if(photoBird!=null)showBird(photoBird,getString(R.string.last_bird));});
        wave=new WaveView(); column.addView(wave,new LinearLayout.LayoutParams(-1,dp(16)));
        recordingButton(getString(R.string.stop),this::stopAndReview);
        liveCandidates=text("",12,Color.WHITE);
        liveCandidates.setVisibility(View.GONE);
        geoStatus=text(getString(R.string.geo_pending),10,MUTED);
        gap(8); text(getString(R.string.session),16,GREEN);
        sessionRows=text(getString(R.string.no_session),12,Color.WHITE);
        sessionRows.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
    }
    private CharSequence sessionText() {
        android.text.SpannableStringBuilder lines=new android.text.SpannableStringBuilder();
        for(var entry:sessionLog.newestFirst()) {
            if(lines.length()>0)lines.append("\n\n");
            int start=lines.length();
            String time=String.format(getResources().getConfiguration().getLocales().get(0),"%02d:%04.1f",entry.audioEndMs()/60000,(entry.audioEndMs()%60000)/1000.0);
            lines.append(String.format(getResources().getConfiguration().getLocales().get(0),getString(R.string.entry),time,entry.common(),entry.score()*100));
            lines.setSpan(new android.text.style.ClickableSpan() {
                @Override public void onClick(View view) {
                    showBird(new BirdEngine.Detection(entry.scientific(),entry.common(),entry.score()),getString(R.string.session_time)+time);
                }
                @Override public void updateDrawState(android.text.TextPaint paint) {paint.setColor(GREEN);paint.setUnderlineText(false);}
            },start,lines.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return lines.length()==0 ? getString(R.string.no_session) : lines;
    }
    private void showBird(BirdEngine.Detection bird,String time) {
        if(birdDialog!=null)birdDialog.dismiss();
        birdDialog=BirdDetails.show(this,bird,time,linksExecutor,
            currentPhoto!=null && currentPhoto.scientific().equals(bird.scientific())?currentPhoto:null,photos);
        gestures.showDialog(birdDialog);
    }
    private void updatePhoto(BirdEngine.Detection bird) {
        if(!photoSelection.accept(bird.scientific(),bird.score()))return;
        photoBird=bird; currentPhoto=null;
        ImageView target=backdrop; TextView credit=photoCredit;
        target.setImageDrawable(null);
        credit.setVisibility(View.VISIBLE);
        credit.setText(getString(R.string.photo_loading));
        photos.load(bird.scientific(),photo -> {
            if(backdrop!=target || !photoSelection.matches(bird.scientific()) || isDestroyed())return;
            currentPhoto=photo;
            if(photo==null) {
                credit.setText(getString(R.string.photo_unavailable));
            } else {
                target.setImageBitmap(photo.bitmap());
                target.setContentDescription(bird.common()+getString(R.string.photo_description));
                credit.setText(getString(R.string.photo_caption,photo.credit()));
            }
        });
    }
    private void stopAndReview() {
        cancel(); page(getString(R.string.session)); text(getString(R.string.stopped),21,GREEN);
        recordingButton(getString(R.string.again),this::requestListen); homeButton();
        text(sessionText(),12,Color.WHITE).setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
    }
    private void post(int run,Runnable action) { handler.post(() -> { if (generation==run && !stopped && !isFinishing()) action.run(); }); }
    private void finishBusy() { busy=false; recorder=null; streamWindows=null; getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); }
    private void cancel() { generation++; if(locationRequest!=null){locationRequest.cancel();locationRequest=null;} if(geoTask!=null)geoTask.cancel(true); speciesAllowed=null; if (recorder!=null) recorder.cancel(); if(streamWindows!=null) streamWindows.close(); if(task!=null) task.cancel(true); finishBusy(); }
    private void error(String title,String message) { page("BIRDWATCH"); text(title,20,GREEN); text(message,12,Color.WHITE); button(getString(R.string.retry),true,this::requestListen); button(getString(R.string.back),false,this::home); }
    private JSONArray loadHistory() { try { return new JSONArray(getPreferences(0).getString("history","[]")); } catch(JSONException e) { return new JSONArray(); } }
    private void save(List<BirdEngine.Detection> found) {
        try {
            JSONObject entry=new JSONObject(); entry.put("time",System.currentTimeMillis()); JSONArray birds=new JSONArray();
            for(var b:found) birds.put(new JSONObject().put("scientific",b.scientific()).put("common",b.common()).put("score",b.score()));
            entry.put("birds",birds); JSONArray next=new JSONArray().put(entry),old=loadHistory();
            for(int i=0;i<Math.min(29,old.length());i++) next.put(old.get(i));
            getPreferences(0).edit().putString("history",next.toString()).apply();
        } catch(JSONException e) { android.util.Log.e("BirdWatch","History save failed",e); }
    }
    private void history() {
        page(""); text(getString(R.string.history_title),22,GREEN); JSONArray entries=loadHistory();
        Map<String,String> translated;
        try { translated=BirdNames.lookup(this); }
        catch(java.io.IOException e) { translated=Collections.emptyMap(); }
        List<HistorySummary.Entry> candidates=new ArrayList<>();
        for(int i=0;i<entries.length();i++) {
            JSONObject entry=entries.optJSONObject(i); if(entry==null)continue;
            JSONArray birds=entry.optJSONArray("birds"); if(birds==null)continue;
            for(int j=0;j<birds.length();j++) {
                JSONObject bird=birds.optJSONObject(j); if(bird==null)continue;
                candidates.add(new HistorySummary.Entry(entry.optLong("time"),bird.optString("scientific"),
                    bird.optString("common"),(float)bird.optDouble("score",0)));
            }
        }
        List<HistorySummary.Entry> latest=HistorySummary.latestBySpecies(candidates);
        if(latest.isEmpty())text(getString(R.string.empty_history),13,MUTED);
        for(var entry:latest) {
            String date=new SimpleDateFormat("MM/dd HH:mm",getResources().getConfiguration().getLocales().get(0)).format(new Date(entry.time()));
            var bird=new BirdEngine.Detection(entry.scientific(),translated.getOrDefault(entry.scientific(),entry.common()),entry.score());
            Button row=button(getString(R.string.history_entry,date,bird.common()),false,() -> showBird(bird,date));
            row.getLayoutParams().height=LinearLayout.LayoutParams.WRAP_CONTENT;
            row.setPadding(dp(8),dp(10),dp(8),dp(10));
            row.requestLayout();
        }
        if(entries.length()>0) coloredButton(getString(R.string.clear_history),Color.rgb(255,188,184),Color.rgb(82,33,34),this::confirmClearHistory);
        homeButton();
    }
    private void confirmClearHistory() {
        Dialog confirmation=new AlertDialog.Builder(this).setMessage(getString(R.string.clear_confirm))
            .setNegativeButton(getString(R.string.cancel),null)
            .setPositiveButton(getString(R.string.clear),(d,w) -> {getPreferences(0).edit().remove("history").apply();history();}).show();
        gestures.showDialog(confirmation);
    }
    private void about() {
        page(getString(R.string.about_title)); text("BirdWatch",22,GREEN);
        settingsDepth = 2;
        text(getString(R.string.model_info),12,Color.WHITE);
        text(getString(R.string.privacy),12,MUTED);
        text(getString(R.string.model_license),11,MUTED);
        text(getString(R.string.photo_info),11,MUTED);
        text(getString(R.string.network_privacy),11,MUTED);
        text(getString(R.string.geo_privacy),11,MUTED);
        text("Kahl et al. (2021)\nBirdNET: A deep learning solution for avian diversity monitoring.",10,MUTED);
        button(getString(R.string.back),false,this::settingsMenu);
    }
    @Override public void onBackPressed() {goBack();}
    private void goBack() {
        if(birdDialog!=null && birdDialog.isShowing()){birdDialog.dismiss();return;}
        if(isHome){finish();return;}
        if(busy)cancel();
        if(settingsDepth==2)settingsMenu();else home();
    }
    private final class WaveView extends View {
        float level, fraction;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        WaveView(){super(MainActivity.this);setContentDescription(getString(R.string.wave_description));}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c); paint.setColor(GREEN); paint.setStrokeWidth(dp(3)); paint.setStrokeCap(Paint.Cap.ROUND);
            for(int i=0;i<21;i++) {float x=getWidth()*(i+1)/22f; float h=dp(3)+(float)Math.abs(Math.sin(i*1.7+fraction*16))*Math.min(dp(16),level*dp(200));c.drawLine(x,getHeight()/2f-h,x,getHeight()/2f+h,paint);}
        }
    }
}
