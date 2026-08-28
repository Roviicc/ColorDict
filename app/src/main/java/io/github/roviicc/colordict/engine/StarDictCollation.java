package io.github.roviicc.colordict.engine;

/**
 * The collation StarDict uses to sort .idx and .syn entries: an ASCII
 * case-insensitive compare of the raw UTF-8 bytes (g_ascii_strcasecmp),
 * with ties broken by a plain unsigned byte compare (stardict_strcmp).
 */
public final class StarDictCollation {

    private StarDictCollation() {
    }

    static int asciiLower(byte b) {
        int v = b & 0xFF;
        return (v >= 'A' && v <= 'Z') ? v + 32 : v;
    }

    /** Case-insensitive compare only (the coarse ordering). */
    public static int compareFold(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int c = asciiLower(a[i]) - asciiLower(b[i]);
            if (c != 0) {
                return c;
            }
        }
        return a.length - b.length;
    }

    /** Unsigned byte compare (the tie-break). */
    public static int compareBytes(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int c = (a[i] & 0xFF) - (b[i] & 0xFF);
            if (c != 0) {
                return c;
            }
        }
        return a.length - b.length;
    }

    /** Full StarDict order: fold compare, ties broken byte-wise. */
    public static int compare(byte[] a, byte[] b) {
        int c = compareFold(a, b);
        return c != 0 ? c : compareBytes(a, b);
    }

    /** True if {@code word} starts with {@code prefix}, ASCII case-insensitively. */
    public static boolean foldStartsWith(byte[] word, byte[] prefix) {
        if (word.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (asciiLower(word[i]) != asciiLower(prefix[i])) {
                return false;
            }
        }
        return true;
    }
}
