package io.github.roviicc.colordict.data;

import android.content.Context;
import android.content.SharedPreferences;

import io.github.roviicc.colordict.data.connotation.ConnotationClient;

/** User settings: theme, and the Claude API key and model used for connotations. */
public final class Settings {

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    private static final String PREFS = "dictionary";
    /** Kept in its own file so the backup rules can exclude it. */
    private static final String SECRETS = "secrets";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";
    private static final String KEY_THEME = "theme";

    private final SharedPreferences prefs;
    private final SharedPreferences secrets;

    public Settings(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        secrets = context.getSharedPreferences(SECRETS, Context.MODE_PRIVATE);
    }

    /** Preferences shared with the word lists (same file as before the split). */
    SharedPreferences prefs() {
        return prefs;
    }

    public int theme() {
        return prefs.getInt(KEY_THEME, THEME_SYSTEM);
    }

    public void setTheme(int theme) {
        prefs.edit().putInt(KEY_THEME, theme).apply();
    }

    public boolean hasApiKey() {
        return !apiKey().isEmpty();
    }

    public String apiKey() {
        return secrets.getString(KEY_API_KEY, "");
    }

    public void setApiKey(String key) {
        secrets.edit().putString(KEY_API_KEY, key == null ? "" : key.trim()).apply();
    }

    /** The key with only its ends visible, for display. */
    public String maskedApiKey() {
        String k = apiKey();
        if (k.length() <= 12) {
            return k.isEmpty() ? "" : "••••";
        }
        return k.substring(0, 7) + "…" + k.substring(k.length() - 4);
    }

    /** One of {@link ConnotationClient#MODELS}. */
    public String model() {
        return prefs.getString(KEY_MODEL, ConnotationClient.DEFAULT_MODEL);
    }

    public void setModel(String model) {
        prefs.edit().putString(KEY_MODEL, model).apply();
    }
}
