package io.github.roviicc.colordict.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * App-wide entry point. Definitions come from the built-in offline
 * dictionary; Claude (with the user's own API key) is called only when the
 * user taps "Check connotation", and that answer is saved per word so it
 * shows automatically from then on. Also holds settings and word lists.
 */
public final class Dictionary {

    public interface Callback {
        void onEntry(Entry entry);

        /** needsSettings: the fix is in Settings (no key, bad key, unavailable model). */
        void onError(String message, boolean retryable, boolean needsSettings);
    }

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    /** Model choices offered in Settings: id, then label resource is chosen by the UI. */
    public static final String[] MODELS = {"claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5"};

    private static final String PREFS = "dictionary";
    /** Kept in its own file so backup rules can exclude it. */
    private static final String SECRETS = "secrets";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_MODEL = "model";
    private static final String KEY_THEME = "theme";

    private static Dictionary instance;

    private final SharedPreferences prefs;
    private final SharedPreferences secrets;
    private final BuiltInDictionary builtIn;
    private final ConnotationStore connotations;
    private final WordList recent;
    private final WordList saved;
    // One thread: lookups are fast and the first one unpacks the database.
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService network = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());

    private ClaudeDictionary claude;
    private String claudeKey;
    private String claudeModel;

    private Dictionary(Context app) {
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        secrets = app.getSharedPreferences(SECRETS, Context.MODE_PRIVATE);
        builtIn = new BuiltInDictionary(app);
        connotations = new ConnotationStore(new File(app.getFilesDir(), "connotations"));
        recent = new WordList(prefs, "recent", 100);
        saved = new WordList(prefs, "saved", 1000);
    }

    public static synchronized Dictionary get(Context context) {
        if (instance == null) {
            instance = new Dictionary(context.getApplicationContext());
        }
        return instance;
    }

    public WordList recent() {
        return recent;
    }

    public WordList saved() {
        return saved;
    }

    public ConnotationStore connotations() {
        return connotations;
    }

    /** True when the first lookup has to unpack the built-in dictionary (a few seconds). */
    public boolean firstLookupIsSlow() {
        return builtIn.needsInstall();
    }

    /**
     * Instant lookup for list previews: the entry when the dictionary is
     * already open, else null. Never blocks on unpacking.
     */
    public Entry peek(String word) {
        if (!builtIn.isReady()) {
            return null;
        }
        try {
            return withSavedConnotation(builtIn.lookup(word));
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** True once the built-in dictionary is open; lookups are then answered immediately. */
    public boolean isReady() {
        return builtIn.isReady();
    }

    /** Opens (and on first launch unpacks) the dictionary in the background. */
    public void warmUp(Runnable whenReady) {
        executor.execute(() -> {
            try {
                builtIn.lookup("a");
            } catch (java.io.IOException ignored) {
                // reported on the first real lookup
            }
            if (whenReady != null) {
                main.post(whenReady);
            }
        });
    }

    /**
     * Looks a word up in the built-in dictionary. Answered synchronously when
     * the dictionary is open; otherwise in the background (first launch), with
     * the callback on the main thread.
     */
    public void define(String word, Callback callback) {
        if (builtIn.isReady()) {
            try {
                Entry entry = withSavedConnotation(builtIn.lookup(word));
                Entry result = entry != null ? entry : Entry.notFound(word, builtIn.similar(word, 6));
                onDefined(word, result);
                callback.onEntry(result);
                return;
            } catch (java.io.IOException e) {
                // fall through to the background path, which reports the error
            }
        }
        executor.execute(() -> {
            try {
                Entry entry = withSavedConnotation(builtIn.lookup(word));
                Entry result = entry != null ? entry : Entry.notFound(word, builtIn.similar(word, 6));
                main.post(() -> {
                    onDefined(word, result);
                    callback.onEntry(result);
                });
            } catch (java.io.IOException e) {
                main.post(() -> callback.onError(
                        "The built-in dictionary couldn't be opened. Free up some storage and try again.",
                        true, false));
            }
        });
    }

    /** Search-as-you-type matches; the callback runs on the main thread. */
    public void suggest(String prefix, int limit, java.util.function.Consumer<java.util.List<String>> callback) {
        executor.execute(() -> {
            java.util.List<String> words;
            try {
                words = builtIn.startingWith(prefix, limit);
            } catch (java.io.IOException e) {
                words = java.util.Collections.emptyList();
            }
            java.util.List<String> result = words;
            main.post(() -> callback.accept(result));
        });
    }

    private Entry withSavedConnotation(Entry entry) {
        if (entry == null || !entry.hasSenses()) {
            return entry;
        }
        JSONObject saved = connotations.get(entry.word);
        if (saved == null) {
            return entry;
        }
        try {
            return entry.withConnotation(saved);
        } catch (JSONException e) {
            return entry;
        }
    }

    /**
     * Asks Claude for the word's connotation and saves it, so every later
     * lookup shows it without asking again. Delivers the updated entry.
     */
    public void checkConnotation(String query, Entry entry, Callback callback) {
        if (entry.connotation != null) {
            callback.onEntry(entry);
            return;
        }
        ClaudeDictionary api = claude();
        if (api == null) {
            callback.onError(null, false, true);
            return;
        }
        String headword = entry.word.isEmpty() ? query : entry.word;
        network.execute(() -> {
            try {
                JSONObject connotation = api.connotation(headword, entry);
                connotations.put(headword, connotation);
                Entry updated = entry.withConnotation(connotation);
                main.post(() -> callback.onEntry(updated));
            } catch (ClaudeDictionary.LookupException e) {
                main.post(() -> callback.onError(e.getMessage(), e.retryable, e.needsSettings));
            } catch (JSONException e) {
                main.post(() -> callback.onError("Couldn't save the connotation.", true, false));
            }
        });
    }

    private void onDefined(String query, Entry entry) {
        if (entry.hasSenses()) {
            recent.add(entry.word.isEmpty() ? query : entry.word);
        }
    }

    /** A client for the current key and model, or null when no key is set. */
    private synchronized ClaudeDictionary claude() {
        String key = apiKey();
        if (key.isEmpty()) {
            return null;
        }
        String model = model();
        if (claude == null || !key.equals(claudeKey) || !model.equals(claudeModel)) {
            claude = new ClaudeDictionary(key, model);
            claudeKey = key;
            claudeModel = model;
        }
        return claude;
    }

    // --- settings --------------------------------------------------------

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

    public String model() {
        return prefs.getString(KEY_MODEL, ClaudeDictionary.DEFAULT_MODEL);
    }

    public void setModel(String model) {
        prefs.edit().putString(KEY_MODEL, model).apply();
    }

    public int theme() {
        return prefs.getInt(KEY_THEME, THEME_SYSTEM);
    }

    public void setTheme(int theme) {
        prefs.edit().putInt(KEY_THEME, theme).apply();
    }
}
