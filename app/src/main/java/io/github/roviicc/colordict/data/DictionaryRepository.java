package io.github.roviicc.colordict.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.connotation.ConnotationClient;
import io.github.roviicc.colordict.data.connotation.ConnotationStore;
import io.github.roviicc.colordict.data.dictionary.BuiltInDictionary;

/**
 * What the screens talk to. Definitions come from the built-in offline
 * dictionary; a word's connotation comes from Claude (with the user's own API
 * key) only when the user asks, and is then saved so it shows automatically.
 * Also owns the Recent and Saved word lists.
 *
 * All callbacks run on the main thread.
 */
public final class DictionaryRepository {

    public interface Callback<T> {
        void onResult(T value);

        void onError(LookupError error);
    }

    private static final int SUGGESTIONS_WHEN_NOT_FOUND = 6;

    private final Context context;
    private final Settings settings;
    private final BuiltInDictionary builtIn;
    private final ConnotationStore connotations;
    private final WordList recent;
    private final WordList saved;
    /** One thread for the database: the first task unpacks it, later ones are fast. */
    private final ExecutorService databaseThread = Executors.newSingleThreadExecutor();
    private final ExecutorService networkThreads = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());

    private ConnotationClient client;
    private String clientKey;
    private String clientModel;

    public DictionaryRepository(Context context, Settings settings) {
        this.context = context.getApplicationContext();
        this.settings = settings;
        builtIn = new BuiltInDictionary(this.context);
        connotations = new ConnotationStore(new File(this.context.getFilesDir(), "connotations"));
        recent = new WordList(settings.prefs(), "recent", 100);
        saved = new WordList(settings.prefs(), "saved", 1000);
    }

    public WordList recent() {
        return recent;
    }

    public WordList saved() {
        return saved;
    }

    // ------------------------------------------------------------ definitions

    /** True once the built-in dictionary is open; lookups then answer immediately. */
    public boolean isReady() {
        return builtIn.isReady();
    }

    /** Opens (and on first launch unpacks) the dictionary in the background. */
    public void warmUp(Runnable whenReady) {
        databaseThread.execute(() -> {
            try {
                builtIn.open();
            } catch (IOException ignored) {
                // Reported by the first real lookup.
            }
            if (whenReady != null) {
                main.post(whenReady);
            }
        });
    }

    /** The entry if the dictionary is already open, else null. Never blocks; for list previews. */
    public Entry peek(String word) {
        if (!builtIn.isReady()) {
            return null;
        }
        try {
            return withSavedConnotation(builtIn.lookup(word));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Looks a word up. Answers synchronously when the dictionary is open,
     * otherwise once it has been unpacked (first launch). A word that isn't in
     * the dictionary gives an entry with spelling suggestions, not an error.
     */
    public void define(String word, Callback<Entry> callback) {
        if (builtIn.isReady()) {
            try {
                deliver(word, resolve(word), callback);
                return;
            } catch (IOException e) {
                // Retry on the database thread, which reports the error.
            }
        }
        databaseThread.execute(() -> {
            try {
                Entry entry = resolve(word);
                main.post(() -> deliver(word, entry, callback));
            } catch (IOException e) {
                main.post(() -> callback.onError(new LookupError(
                        context.getString(R.string.error_dictionary_unavailable), LookupError.Fix.RETRY)));
            }
        });
    }

    private Entry resolve(String word) throws IOException {
        Entry entry = withSavedConnotation(builtIn.lookup(word));
        return entry != null ? entry : Entry.notFound(word, builtIn.similar(word, SUGGESTIONS_WHEN_NOT_FOUND));
    }

    private void deliver(String query, Entry entry, Callback<Entry> callback) {
        if (entry.hasSenses()) {
            recent.add(entry.word.isEmpty() ? query : entry.word);
        }
        callback.onResult(entry);
    }

    /** Search-as-you-type matches, most common first. */
    public void suggest(String prefix, int limit, Consumer<List<String>> callback) {
        databaseThread.execute(() -> {
            List<String> words;
            try {
                words = builtIn.startingWith(prefix, limit);
            } catch (IOException e) {
                words = Collections.emptyList();
            }
            List<String> result = words;
            main.post(() -> callback.accept(result));
        });
    }

    // ------------------------------------------------------------ connotations

    /**
     * Asks Claude for the word's connotation and saves it, so every later
     * lookup shows it without asking again. Delivers the updated entry.
     */
    public void checkConnotation(String query, Entry entry, Callback<Entry> callback) {
        if (entry.connotation != null) {
            callback.onResult(entry);
            return;
        }
        ConnotationClient api = client();
        if (api == null) {
            callback.onError(new LookupError(context.getString(R.string.no_api_key),
                    LookupError.Fix.OPEN_SETTINGS));
            return;
        }
        String headword = entry.word.isEmpty() ? query : entry.word;
        networkThreads.execute(() -> {
            try {
                JSONObject connotation = api.connotation(headword, entry);
                connotations.put(headword, connotation);
                Entry updated = entry.withConnotation(connotation);
                main.post(() -> callback.onResult(updated));
            } catch (LookupError.Exception e) {
                main.post(() -> callback.onError(e.error));
            } catch (JSONException e) {
                main.post(() -> callback.onError(new LookupError(
                        context.getString(R.string.error_connotation_save), LookupError.Fix.RETRY)));
            }
        });
    }

    /** Deletes every saved connotation; returns how many there were. */
    public int clearConnotations() {
        return connotations.clear();
    }

    private Entry withSavedConnotation(Entry entry) {
        if (entry == null || !entry.hasSenses()) {
            return entry;
        }
        JSONObject savedConnotation = connotations.get(entry.word);
        if (savedConnotation == null) {
            return entry;
        }
        try {
            return entry.withConnotation(savedConnotation);
        } catch (JSONException e) {
            return entry;
        }
    }

    /** A client for the current key and model, or null when no key is set. */
    private synchronized ConnotationClient client() {
        String key = settings.apiKey();
        if (key.isEmpty()) {
            return null;
        }
        String model = settings.model();
        if (client == null || !key.equals(clientKey) || !model.equals(clientModel)) {
            client = new ConnotationClient(key, model);
            clientKey = key;
            clientModel = model;
        }
        return client;
    }
}
