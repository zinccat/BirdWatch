package org.birdwatch.wear;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.LruCache;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Separate, bounded network worker. No audio, location or account data is sent. */
public final class BirdPhotos implements AutoCloseable {
    public record Photo(String scientific, String attribution, String author, String license, long id, Bitmap bitmap) {
        public String credit() {return author+" · "+license.toUpperCase(Locale.ROOT);}
        public String sourceUrl() { return "https://www.inaturalist.org/photos/"+id; }
        public String licenseUrl() { return BirdPhotos.licenseUrl(license); }
    }
    record Metadata(String scientific,String attribution,String author,String license,long id,String url) {}
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(1),new ThreadPoolExecutor.DiscardOldestPolicy());
    private final LruCache<String,Photo> cache=new LruCache<>(6*1024*1024) {
        @Override protected int sizeOf(String key,Photo photo){return photo.bitmap().getAllocationByteCount();}
    };
    private final LinkedHashMap<String,Long> misses=new LinkedHashMap<>();
    private final Object stateLock=new Object();
    private final ExecutorService maintenance=Executors.newSingleThreadExecutor();
    private final PhotoDiskCache disk;
    private long cacheEpoch;
    public BirdPhotos(Context context){disk=new PhotoDiskCache(new File(context.getFilesDir(),"bird_photos"));}
    BirdPhotos(File directory){disk=new PhotoDiskCache(directory);}
    public void cacheStats(Consumer<PhotoDiskCache.Stats> callback) {
        maintenance.execute(() -> {var stats=disk.stats();main.post(() -> {if(!closed)callback.accept(stats);});});
    }
    public void clearCache(Consumer<Boolean> callback) {
        maintenance.execute(() -> {
            boolean success=true;
            synchronized(stateLock) {
                cacheEpoch++;cache.evictAll();misses.clear();
                try{disk.clear();}catch(IOException e){success=false;}
            }
            boolean result=success;main.post(() -> {if(!closed)callback.accept(result);});
        });
    }
    private long lastRequest;
    private volatile boolean closed;
    public void load(String scientific,Consumer<Photo> callback) {
        if(closed)return;
        worker.execute(() -> {
            Photo photo=null;long epoch;
            synchronized(stateLock){epoch=cacheEpoch;}
            try {
                boolean canFetch;
                synchronized(stateLock) {
                    photo=cache.get(scientific);
                    if(photo==null) {
                        var entry=disk.get(scientific);
                        if(entry!=null) {
                            photo=decode(entry.metadata(),entry.image());
                            if(photo==null)disk.remove(scientific);
                        }
                    }
                    canFetch=SystemClock.elapsedRealtime()-misses.getOrDefault(scientific,-300001L)>300000;
                }
                if(photo==null && canFetch)photo=fetch(scientific,epoch);
                synchronized(stateLock) {
                    if(epoch!=cacheEpoch)return;
                    if(photo!=null)cache.put(scientific,photo);else rememberMiss(scientific);
                }
            } catch(Exception e) {synchronized(stateLock){if(epoch==cacheEpoch)rememberMiss(scientific);}}
            Photo result=photo;
            main.post(() -> {synchronized(stateLock){if(!closed && epoch==cacheEpoch)callback.accept(result);}});
        });
    }
    private void rememberMiss(String scientific) {
        misses.put(scientific,SystemClock.elapsedRealtime());
        if(misses.size()>64)misses.remove(misses.keySet().iterator().next());
    }
    Photo fetch(String scientific) throws Exception {
        long epoch; synchronized(stateLock){epoch=cacheEpoch;}
        return fetch(scientific,epoch);
    }
    private Photo fetch(String scientific,long epoch) throws Exception {
        String query=URLEncoder.encode(scientific,StandardCharsets.UTF_8.name());
        JSONObject response=json("https://api.inaturalist.org/v1/taxa?q="+query+"&rank=species&per_page=10");
        JSONObject taxon=exactTaxon(response,scientific);
        if(taxon==null)return null;
        Metadata meta=metadata(taxon.optJSONObject("default_photo"),scientific);
        if(meta==null) {
            JSONArray results=json("https://api.inaturalist.org/v1/taxa/"+taxon.getLong("id")).optJSONArray("results");
            if(results!=null && results.length()>0) {
                JSONObject full=results.getJSONObject(0);
                if(!scientific.equals(full.optString("name")))return null;
                JSONArray photos=full.optJSONArray("taxon_photos");
                if(photos!=null)for(int i=0;i<photos.length();i++) {
                    meta=metadata(photos.getJSONObject(i).optJSONObject("photo"),scientific);
                    if(meta!=null)break;
                }
            }
        }
        if(meta==null)return null;
        byte[] bytes=read(meta.url(),2*1024*1024);
        Photo photo=decode(meta,bytes);
        if(photo!=null)synchronized(stateLock) {
            if(!closed && epoch==cacheEpoch)try{disk.put(meta,bytes);}catch(Exception ignored){ /* Display still works when storage is full. */ }
        }
        return photo;
    }
    private static Photo decode(Metadata meta,byte[] bytes) {
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(options.outWidth<=0 || options.outHeight<=0 || options.outWidth>10000 || options.outHeight>10000)return null;
        options.inSampleSize=1;
        while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>640)options.inSampleSize*=2;
        options.inJustDecodeBounds=false;
        Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        return bitmap==null?null:new Photo(meta.scientific(),meta.attribution(),meta.author(),meta.license(),meta.id(),bitmap);
    }
    static JSONObject exactTaxon(JSONObject response,String scientific) {
        JSONArray results=response.optJSONArray("results");
        if(results==null)return null;
        for(int i=0;i<results.length();i++) {
            JSONObject item=results.optJSONObject(i);
            if(item!=null && item.optBoolean("is_active") && "species".equals(item.optString("rank"))
                && "Aves".equals(item.optString("iconic_taxon_name")) && scientific.equals(item.optString("name")))return item;
        }
        return null;
    }
    static Metadata metadata(JSONObject photo,String scientific) {
        if(photo==null)return null;
        String license=photo.optString("license_code").toLowerCase(Locale.ROOT);
        String attribution=photo.optString("attribution").trim();
        String url=photo.optString("medium_url");long id=photo.optLong("id");
        JSONArray flags=photo.optJSONArray("flags");
        if(licenseUrl(license).isEmpty() || attribution.isEmpty() || id<=0 || (flags!=null && flags.length()>0))return null;
        try {
            URI uri=new URI(url);
            if(!safeImageUrl(uri) || !uri.getPath().matches("/photos/"+id+"/medium\\.(jpg|jpeg|png)"))return null;
        } catch(Exception e){return null;}
        return new Metadata(scientific,attribution,photo.optString("attribution_name",attribution),license,id,url);
    }
    static String licenseUrl(String code) {
        return switch(code) {
            case "cc0" -> "https://creativecommons.org/publicdomain/zero/1.0/";
            case "cc-by" -> "https://creativecommons.org/licenses/by/4.0/";
            case "cc-by-nc" -> "https://creativecommons.org/licenses/by-nc/4.0/";
            default -> "";
        };
    }
    private static boolean safeImageUrl(URI uri) {
        return "https".equals(uri.getScheme()) && uri.getUserInfo()==null && uri.getPort()==-1
            && "inaturalist-open-data.s3.amazonaws.com".equals(uri.getHost()) && uri.getQuery()==null && uri.getFragment()==null;
    }
    private JSONObject json(String url) throws Exception {
        long delay=2100-(SystemClock.elapsedRealtime()-lastRequest);
        if(delay>0)Thread.sleep(delay);
        lastRequest=SystemClock.elapsedRealtime();
        return new JSONObject(new String(read(url,1024*1024),StandardCharsets.UTF_8));
    }
    private static byte[] read(String address,int limit) throws Exception {
        URI uri=new URI(address);
        if(!safeImageUrl(uri) && !("https".equals(uri.getScheme()) && "api.inaturalist.org".equals(uri.getHost())
            && uri.getUserInfo()==null && uri.getPort()==-1))throw new IOException("Unexpected host");
        HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(6000);connection.setReadTimeout(6000);
        connection.setRequestProperty("User-Agent","BirdWatch/0.7 (personal noncommercial Wear OS bird identification)");
        try {
            if(connection.getResponseCode()!=200 || connection.getContentLengthLong()>limit)throw new IOException("Photo unavailable");
            try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192];int n;
                while((n=input.read(buffer))!=-1) {
                    if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                    if(output.size()+n>limit)throw new IOException("Response too large");
                    output.write(buffer,0,n);
                }
                return output.toByteArray();
            }
        } finally {connection.disconnect();}
    }
    @Override public void close(){closed=true;worker.shutdownNow();maintenance.shutdown();cache.evictAll();}
}
