package org.birdwatch.wear;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** BirdNET's 2024 eBird mapping, kept offline and keyed by scientific name. */
public final class SpeciesLinks {
    private static JSONObject codes;
    public static synchronized String ebird(Context context, String scientific) throws IOException {
        if(codes==null) {
            try(InputStream in=context.getAssets().open("ebird_codes.json"); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192];for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n);
                codes=new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
            } catch(JSONException e) { throw new IOException("Unable to read species data",e); }
        }
        return urlForCode(codes.optString(scientific,""));
    }
    public static String urlForCode(String code) {
        return code.matches("[a-z0-9]{3,20}") ? "https://ebird.org/species/"+code : "";
    }
}
