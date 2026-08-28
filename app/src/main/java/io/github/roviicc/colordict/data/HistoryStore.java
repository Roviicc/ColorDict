package io.github.roviicc.colordict.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Lookup history and bookmarks, stored in a small SQLite database. */
public final class HistoryStore extends SQLiteOpenHelper {

    private static final String DB_NAME = "colordict.db";
    private static final int DB_VERSION = 1;

    public HistoryStore(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE history (word TEXT PRIMARY KEY, ts INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE bookmarks (word TEXT PRIMARY KEY, ts INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 — nothing to migrate yet.
    }

    public void addHistory(String word, int limit) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("word", word);
        cv.put("ts", System.currentTimeMillis());
        db.insertWithOnConflict("history", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        db.execSQL("DELETE FROM history WHERE word NOT IN "
                + "(SELECT word FROM history ORDER BY ts DESC LIMIT ?)",
                new Object[] {limit});
    }

    public List<String> history(int max) {
        return queryWords("history", max);
    }

    public void removeHistory(String word) {
        getWritableDatabase().delete("history", "word = ?", new String[] {word});
    }

    public void clearHistory() {
        getWritableDatabase().delete("history", null, null);
    }

    public boolean isBookmarked(String word) {
        try (Cursor c = getReadableDatabase().query("bookmarks", new String[] {"word"},
                "word = ?", new String[] {word}, null, null, null, "1")) {
            return c.moveToFirst();
        }
    }

    /** Toggles the bookmark; returns true if the word is now bookmarked. */
    public boolean toggleBookmark(String word) {
        SQLiteDatabase db = getWritableDatabase();
        if (isBookmarked(word)) {
            db.delete("bookmarks", "word = ?", new String[] {word});
            return false;
        }
        ContentValues cv = new ContentValues();
        cv.put("word", word);
        cv.put("ts", System.currentTimeMillis());
        db.insertWithOnConflict("bookmarks", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        return true;
    }

    public List<String> bookmarks(int max) {
        return queryWords("bookmarks", max);
    }

    public void removeBookmark(String word) {
        getWritableDatabase().delete("bookmarks", "word = ?", new String[] {word});
    }

    public void clearBookmarks() {
        getWritableDatabase().delete("bookmarks", null, null);
    }

    private List<String> queryWords(String table, int max) {
        List<String> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(table, new String[] {"word"},
                null, null, null, null, "ts DESC", String.valueOf(max))) {
            while (c.moveToNext()) {
                out.add(c.getString(0));
            }
        }
        return out;
    }
}
