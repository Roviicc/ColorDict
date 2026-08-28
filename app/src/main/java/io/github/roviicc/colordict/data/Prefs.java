package io.github.roviicc.colordict.data;

import android.content.Context;
import android.content.SharedPreferences;

/** Small typed facade over the app's SharedPreferences. */
public final class Prefs {

    public static final String FILE = "settings";

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    private Prefs() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static int themeMode(Context c) {
        return sp(c).getInt("theme_mode", THEME_SYSTEM);
    }

    public static void setThemeMode(Context c, int mode) {
        sp(c).edit().putInt("theme_mode", mode).apply();
    }

    /** WebView text zoom for definitions, in percent. */
    public static int textZoom(Context c) {
        return sp(c).getInt("text_zoom", 100);
    }

    public static void setTextZoom(Context c, int zoom) {
        sp(c).edit().putInt("text_zoom", zoom).apply();
    }

    public static int maxSuggestions(Context c) {
        return sp(c).getInt("max_suggestions", 50);
    }

    public static void setMaxSuggestions(Context c, int max) {
        sp(c).edit().putInt("max_suggestions", max).apply();
    }

    public static int historyLimit(Context c) {
        return sp(c).getInt("history_limit", 200);
    }

    public static void setHistoryLimit(Context c, int limit) {
        sp(c).edit().putInt("history_limit", limit).apply();
    }
}
