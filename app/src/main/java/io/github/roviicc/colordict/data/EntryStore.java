package io.github.roviicc.colordict.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Keeps every entry the app has fetched as a small JSON file, so looking a
 * word up again (or opening a saved word) is instant and works offline.
 */
public final class EntryStore {

    private final File dir;

    public EntryStore(File dir) {
        this.dir = dir;
    }

    /** Lookups are case-insensitive: "Serene" and "serene" share one entry. */
    public static String key(String word) {
        return word.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public synchronized Entry get(String word) {
        File f = file(word);
        if (!f.isFile()) {
            return null;
        }
        try (InputStream in = new FileInputStream(f)) {
            byte[] data = new byte[(int) f.length()];
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n < 0) {
                    break;
                }
                off += n;
            }
            return Entry.fromJson(new String(data, 0, off, StandardCharsets.UTF_8));
        } catch (IOException | org.json.JSONException e) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            return null;
        }
    }

    public synchronized void put(String word, Entry entry) {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return;
        }
        File f = file(word);
        File tmp = new File(dir, f.getName() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(entry.toJson().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(f);
    }

    /** Removes every cached entry; returns how many were deleted. */
    public synchronized int clear() {
        File[] files = dir.listFiles();
        int n = 0;
        if (files != null) {
            for (File f : files) {
                if (f.delete()) {
                    n++;
                }
            }
        }
        return n;
    }

    private File file(String word) {
        return new File(dir, sha1(key(word)) + ".json");
    }

    private static String sha1(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
