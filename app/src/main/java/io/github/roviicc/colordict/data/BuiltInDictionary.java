package io.github.roviicc.colordict.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.roviicc.colordict.BuildConfig;

/**
 * The offline dictionary bundled with the app: about 150,000 English words
 * from Open English WordNet 2024 (CC BY 4.0) with IPA from WordNet and the
 * CMU Pronouncing Dictionary. Built by tools/build_dictionary.py and shipped
 * as assets/dictionary.db.gz, which is copied out of the APK once on first use.
 *
 * Methods block (first call may take a few seconds to unpack); call them off
 * the main thread unless {@link #isReady()}.
 */
public final class BuiltInDictionary {

    /*
     * The repository keeps assets/dictionary.db.gz; the Android build unpacks
     * .gz assets and stores them as "dictionary.db" (deflated inside the APK),
     * so that is the name to open at runtime.
     */
    private static final String ASSET = "dictionary.db";

    private final Context context;
    private SQLiteDatabase db;

    public BuiltInDictionary(Context context) {
        this.context = context.getApplicationContext();
    }

    /** True once the database is unpacked and open (lookups are then instant). */
    public synchronized boolean isReady() {
        return db != null;
    }

    /** True when the next call has to unpack the database first. */
    public boolean needsInstall() {
        return !isReady() && !dbFile().isFile();
    }

    private File dbFile() {
        // Versioned by app build so an updated dictionary replaces the old one.
        return new File(context.getNoBackupFilesDir(), "dictionary-" + BuildConfig.VERSION_CODE + ".db");
    }

    private synchronized SQLiteDatabase open() throws IOException {
        if (db != null) {
            return db;
        }
        File file = dbFile();
        if (!file.isFile()) {
            File[] old = context.getNoBackupFilesDir().listFiles(
                    (dir, name) -> name.startsWith("dictionary-") && name.endsWith(".db"));
            if (old != null) {
                for (File f : old) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
            File tmp = new File(file.getPath() + ".tmp");
            try (InputStream in = context.getAssets().open(ASSET);
                 OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            if (!tmp.renameTo(file)) {
                throw new IOException("could not install dictionary");
            }
        }
        db = SQLiteDatabase.openDatabase(file.getPath(), null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        return db;
    }

    /**
     * The entry for a word, trying irregular forms ("geese"), regular
     * inflections ("running") and compound spellings; null when absent.
     */
    public Entry lookup(String word) throws IOException {
        String key = Words.key(word);
        if (key.isEmpty()) {
            return null;
        }
        Entry e = exact(key);
        if (e != null) {
            return e;
        }
        String base = irregularBase(key);
        if (base != null && (e = exact(base)) != null) {
            return e;
        }
        for (String candidate : Words.baseFormCandidates(key)) {
            if ((e = exact(candidate)) != null) {
                return e;
            }
        }
        return null;
    }

    private Entry exact(String key) throws IOException {
        try (Cursor c = open().rawQuery("SELECT word, json FROM entries WHERE key = ?", new String[]{key})) {
            if (!c.moveToFirst()) {
                return null;
            }
            return Entry.fromDictionaryRow(c.getString(0), c.getString(1));
        } catch (JSONException e) {
            return null;
        }
    }

    private String irregularBase(String key) throws IOException {
        try (Cursor c = open().rawQuery("SELECT key FROM forms WHERE form = ?", new String[]{key})) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    /** Words starting with the prefix, most common first (for search-as-you-type). */
    public List<String> startingWith(String prefix, int limit) throws IOException {
        String key = Words.key(prefix);
        if (key.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        try (Cursor c = open().rawQuery(
                "SELECT word FROM entries WHERE key >= ? AND key < ? ORDER BY rank DESC, length(key) LIMIT ?",
                new String[]{key, key + "\uffff", String.valueOf(limit)})) {
            while (c.moveToNext()) {
                out.add(c.getString(0));
            }
        }
        return out;
    }

    /** Close spellings for a word that wasn't found, best match first. */
    public List<String> similar(String word, int limit) throws IOException {
        String key = Words.key(word);
        if (key.length() < 2) {
            return Collections.emptyList();
        }
        int max = key.length() <= 4 ? 1 : 2;
        String prefix = key.substring(0, 1);
        List<String[]> scored = new ArrayList<>();
        try (Cursor c = open().rawQuery(
                "SELECT key, word, rank FROM entries WHERE key >= ? AND key < ?"
                        // Lengths are inlined: bound parameters arrive as text, and
                        // SQLite never finds an integer between two text values.
                        + " AND length(key) BETWEEN " + (key.length() - max)
                        + " AND " + (key.length() + max) + " AND key NOT LIKE '% %'",
                new String[]{prefix, prefix + "\uffff"})) {
            while (c.moveToNext()) {
                int d = Words.distance(key, c.getString(0), max);
                if (d <= max) {
                    scored.add(new String[]{c.getString(1), String.valueOf(d), c.getString(2)});
                }
            }
        }
        scored.sort((a, b) -> {
            int byDistance = Integer.compare(Integer.parseInt(a[1]), Integer.parseInt(b[1]));
            return byDistance != 0 ? byDistance
                    : Integer.compare(Integer.parseInt(b[2]), Integer.parseInt(a[2]));
        });
        List<String> out = new ArrayList<>();
        for (String[] s : scored) {
            if (out.size() == limit) {
                break;
            }
            out.add(s[0]);
        }
        return out;
    }
}
