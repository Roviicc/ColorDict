package io.github.roviicc.colordict.data;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An ordered, de-duplicated list of words (most recent first) persisted in
 * SharedPreferences. Used for both "Recent" and "Saved".
 */
public final class WordList {

    private final SharedPreferences prefs;
    private final String prefKey;
    private final int limit;

    public WordList(SharedPreferences prefs, String prefKey, int limit) {
        this.prefs = prefs;
        this.prefKey = prefKey;
        this.limit = limit;
    }

    public synchronized List<String> all() {
        return Collections.unmodifiableList(read());
    }

    public synchronized boolean contains(String word) {
        String k = EntryStore.key(word);
        for (String w : read()) {
            if (EntryStore.key(w).equals(k)) {
                return true;
            }
        }
        return false;
    }

    /** Adds (or moves) the word to the top of the list. */
    public synchronized void add(String word) {
        List<String> list = read();
        removeKey(list, EntryStore.key(word));
        list.add(0, word);
        while (list.size() > limit) {
            list.remove(list.size() - 1);
        }
        write(list);
    }

    public synchronized void remove(String word) {
        List<String> list = read();
        removeKey(list, EntryStore.key(word));
        write(list);
    }

    /** Adds the word if absent, removes it if present; returns the new state. */
    public synchronized boolean toggle(String word) {
        if (contains(word)) {
            remove(word);
            return false;
        }
        add(word);
        return true;
    }

    public synchronized void clear() {
        write(new ArrayList<>());
    }

    private static void removeKey(List<String> list, String key) {
        for (int i = list.size() - 1; i >= 0; i--) {
            if (EntryStore.key(list.get(i)).equals(key)) {
                list.remove(i);
            }
        }
    }

    private List<String> read() {
        List<String> out = new ArrayList<>();
        String raw = prefs.getString(prefKey, null);
        if (raw == null) {
            return out;
        }
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                out.add(arr.getString(i));
            }
        } catch (JSONException ignored) {
            // corrupted value: start over
        }
        return out;
    }

    private void write(List<String> list) {
        prefs.edit().putString(prefKey, new JSONArray(list).toString()).apply();
    }
}
