package org.birdwatch.wear;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Official, index-aligned labels; scientific names remain the stable history identifier. */
public final class BirdNames {
    public static final String[] CODES={"en","zh","scientific"};
    public static String language(Context context) {
        return context.getSharedPreferences("birdwatch_settings",0).getString("bird_language","en");
    }
    public static void setLanguage(Context context,String language) {
        if(!Arrays.asList(CODES).contains(language))throw new IllegalArgumentException();
        context.getSharedPreferences("birdwatch_settings",0).edit().putString("bird_language",language).apply();
    }
    public static String title(Context context) {
        return title(context,language(context));
    }
    public static String title(Context context,String code) {
        return switch(code) {case "zh" -> context.getString(R.string.language_chinese); case "scientific" -> context.getString(R.string.scientific); default -> "English";};
    }
    public static List<String> load(Context context) throws IOException {
        return load(context,language(context));
    }
    public static List<String> load(Context context,String language) throws IOException {
        List<String> labels=new ArrayList<>();
        String file="zh".equals(language)?"labels_zh.txt":"labels_en.txt";
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(context.getAssets().open(file),StandardCharsets.UTF_8))) {
            for(String line;(line=reader.readLine())!=null;) {
                if(line.trim().isEmpty())continue;
                if("scientific".equals(language)) {String scientific=line.split("_",2)[0];line=scientific+"_"+scientific;}
                labels.add(line);
            }
        }
        return labels;
    }
    public static Map<String,String> lookup(Context context) throws IOException {
        Map<String,String> names=new HashMap<>();
        for(String line:load(context)){String[] parts=line.split("_",2);if(parts.length==2)names.put(parts[0],parts[1]);}
        return names;
    }
}
