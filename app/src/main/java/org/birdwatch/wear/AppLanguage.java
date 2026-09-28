package org.birdwatch.wear;
import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import java.util.Locale;

/** App language is independent of both system locale and bird-name preferences. */
public final class AppLanguage {
    public static String language(Context context) {
        return context.getSharedPreferences("birdwatch_settings",0).getString("ui_language","en");
    }
    public static void set(Context context,String code) {
        if(!"en".equals(code) && !"zh".equals(code))throw new IllegalArgumentException("Unsupported language");
        context.getSharedPreferences("birdwatch_settings",0).edit().putString("ui_language",code).apply();
    }
    public static Context apply(Context context) {
        Configuration configuration=new Configuration(context.getResources().getConfiguration());
        configuration.setLocales(new LocaleList(Locale.forLanguageTag(language(context))));
        return context.createConfigurationContext(configuration);
    }
    private AppLanguage() {}
}
