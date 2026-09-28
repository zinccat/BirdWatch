package org.birdwatch.wear;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import java.util.*;
import java.util.function.Consumer;

/** One foreground fix per session; timeout/cancellation stops all provider requests. */
final class LocationOnce {
    private final Context context;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<CancellationSignal> signals=new ArrayList<>();
    private boolean done;
    private Runnable timeout;
    LocationOnce(Context context){this.context=context;}
    static boolean hasPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }
    static boolean usable(Location location,long nowNanos) {
        if(location==null || !location.hasAccuracy() || !Float.isFinite(location.getAccuracy()) || location.getAccuracy()<0 || location.getAccuracy()>20000)return false;
        long age=nowNanos-location.getElapsedRealtimeNanos();
        return Double.isFinite(location.getLatitude()) && Double.isFinite(location.getLongitude()) && Math.abs(location.getLatitude())<=90 &&
            Math.abs(location.getLongitude())<=180 && age>=-5_000_000_000L && age<=120_000_000_000L;
    }
    void request(Consumer<Location> callback) {
        if(!hasPermission(context)){finish(null,callback);return;}
        LocationManager manager=context.getSystemService(LocationManager.class);
        timeout=()->finish(null,callback);handler.postDelayed(timeout,30000);
        boolean fine=context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        for(String provider:new String[]{LocationManager.GPS_PROVIDER,"fused",LocationManager.NETWORK_PROVIDER}) {
            if(done)break;
            if(LocationManager.GPS_PROVIDER.equals(provider) && !fine)continue;
            try {
                if(manager==null || !manager.getAllProviders().contains(provider) || !manager.isProviderEnabled(provider))continue;
                CancellationSignal signal=new CancellationSignal();signals.add(signal);
                manager.getCurrentLocation(provider,signal,context.getMainExecutor(),location -> {
                    if(!done && usable(location,SystemClock.elapsedRealtimeNanos()))finish(location,callback);
                });
            }catch(RuntimeException ignored) { }
        }
        if(signals.isEmpty())finish(null,callback);
    }
    private void finish(Location location,Consumer<Location> callback){if(done)return;cancel();callback.accept(location);}
    void cancel(){done=true;if(timeout!=null)handler.removeCallbacks(timeout);for(var signal:signals)signal.cancel();signals.clear();}
}
