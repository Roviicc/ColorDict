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
 * App-wide entry point: settings (the user's Claude API key and model),
 * recent/saved word lists, the on-device entry store, and asynchronous
 * lookups. Everything is kept on the phone; there is no app server.
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
    private final EntryStore store;
    private final WordList recent;
    private final WordList saved;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());

    private ClaudeDictionary claude;
    private String claudeKey;
    private String claudeModel;

    private Dictionary(Context app) {
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        secrets = app.getSharedPreferences(SECRETS, Context.MODE_PRIVATE);
        store = new EntryStore(new File(app.getFilesDir(), "entries"));
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

    public EntryStore store() {
        return store;
    }

    /** Stored entry or null; never touches the network. */
    public Entry cached(String word) {
        return store.get(word);
    }

    /**
     * Looks a word up. A stored entry is delivered synchronously; otherwise
     * Claude is asked on a background thread and the callback runs on the
     * main thread.
     */
    public void define(String word, Callback callback) {
        Entry hit = store.get(word);
        if (hit != null) {
            onDefined(word, hit);
            callback.onEntry(hit);
            return;
        }
        ClaudeDictionary api = claude();
        if (api == null) {
            callback.onError(null, false, true);
            return;
        }
        executor.execute(() -> {
            try {
                Entry entry = api.define(word);
                if (entry.hasSenses()) {
                    store.put(word, entry);
                }
                main.post(() -> {
                    onDefined(word, entry);
                    callback.onEntry(entry);
                });
            } catch (ClaudeDictionary.LookupException e) {
                main.post(() -> callback.onError(e.getMessage(), e.retryable, e.needsSettings));
            }
        });
    }

    /**
     * Asks Claude for the word's connotation and saves it inside the stored
     * entry, so every later lookup shows it without asking again. Delivers
     * the updated entry.
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
        executor.execute(() -> {
            try {
                JSONObject connotation = api.connotation(headword);
                Entry updated = entry.withConnotation(connotation);
                store.put(query, updated);
                if (!EntryStore.key(query).equals(EntryStore.key(headword))) {
                    store.put(headword, updated);
                }
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
