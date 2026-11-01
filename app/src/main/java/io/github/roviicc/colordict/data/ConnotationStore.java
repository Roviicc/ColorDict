package io.github.roviicc.colordict.data;

import org.json.JSONException;
import org.json.JSONObject;

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
 * Saved connotations, one small JSON file per word. Once the user has checked
 * a word's connotation it is read from here on every later lookup, so Claude
 * is asked only once per word.
 */
public final class ConnotationStore {

    private final File dir;

    public ConnotationStore(File dir) {
        this.dir = dir;
    }

    public synchronized JSONObject get(String word) {
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
            return new JSONObject(new String(data, 0, off, StandardCharsets.UTF_8));
        } catch (IOException | JSONException e) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            return null;
        }
    }

    public synchronized void put(String word, JSONObject connotation) {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return;
        }
        File f = file(word);
        File tmp = new File(dir, f.getName() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(connotation.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(f);
    }

    /** Removes every saved connotation; returns how many were deleted. */
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
        return new File(dir, sha1(Words.key(word)) + ".json");
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
