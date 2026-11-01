package io.github.roviicc.colordict.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.roviicc.colordict.BuildConfig;

/**
 * App-wide entry point: settings, recent/saved word lists, the on-device
 * entry cache, and asynchronous lookups (cache first, then the server).
 */
public final class Dictionary {

    public interface Callback {
        void onEntry(Entry entry);

        void onError(String message, boolean retryable);
    }

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    private static final String PREFS = "dictionary";
    private static final String KEY_SERVER = "server_url";
    private static final String KEY_THEME = "theme";

    private static Dictionary instance;

    private final SharedPreferences prefs;
    private final EntryStore store;
    private final WordList recent;
    private final WordList saved;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());

    private Dictionary(Context app) {
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
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

    /** Cached entry or null; never touches the network. */
    public Entry cached(String word) {
        return store.get(word);
    }

    /**
     * Looks a word up. A cached entry is delivered synchronously; otherwise
     * the server is asked on a background thread and the callback runs on
     * the main thread.
     */
    public void define(String word, Callback callback) {
        Entry hit = store.get(word);
        if (hit != null) {
            onDefined(word, hit);
            callback.onEntry(hit);
            return;
        }
        DictionaryApi api = new DictionaryApi(serverUrl(), BuildConfig.APP_TOKEN);
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
            } catch (DictionaryApi.LookupException e) {
                main.post(() -> callback.onError(e.getMessage(), e.retryable));
            }
        });
    }

    private void onDefined(String query, Entry entry) {
        if (entry.hasSenses()) {
            recent.add(entry.word.isEmpty() ? query : entry.word);
        }
    }

    // --- settings --------------------------------------------------------

    /** The server address: the user's override, else the one built into the APK. */
    public String serverUrl() {
        String custom = prefs.getString(KEY_SERVER, "");
        return custom.isEmpty() ? BuildConfig.API_BASE_URL : custom;
    }

    public String customServerUrl() {
        return prefs.getString(KEY_SERVER, "");
    }

    public void setCustomServerUrl(String url) {
        prefs.edit().putString(KEY_SERVER, url == null ? "" : url.trim()).apply();
    }

    public int theme() {
        return prefs.getInt(KEY_THEME, THEME_SYSTEM);
    }

    public void setTheme(int theme) {
        prefs.edit().putInt(KEY_THEME, theme).apply();
    }
}
