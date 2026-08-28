package io.github.roviicc.colordict.engine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * An in-memory StarDict .idx index: a run of records sorted with
 * {@link StarDictCollation}, each {@code word\0 + offset(u32|u64 BE) + size(u32 BE)}.
 * Also reads gzip-compressed {@code .idx.gz} files.
 */
public final class StarDictIndex {

    private final byte[][] words;
    private final long[] offsets;
    private final int[] sizes;

    private StarDictIndex(byte[][] words, long[] offsets, int[] sizes) {
        this.words = words;
        this.offsets = offsets;
        this.sizes = sizes;
    }

    public static StarDictIndex load(File file, int offsetBits) throws IOException {
        byte[] blob = readFully(file);
        int offBytes = offsetBits == 64 ? 8 : 4;
        List<byte[]> wordList = new ArrayList<>();
        List<long[]> recList = new ArrayList<>();
        int pos = 0;
        while (pos < blob.length) {
            int nul = pos;
            while (nul < blob.length && blob[nul] != 0) {
                nul++;
            }
            if (nul + 1 + offBytes + 4 > blob.length) {
                if (nul != pos) {
                    throw new IOException(file.getName() + ": truncated idx record at byte " + pos);
                }
                break;
            }
            byte[] word = new byte[nul - pos];
            System.arraycopy(blob, pos, word, 0, word.length);
            long offset = readBE(blob, nul + 1, offBytes);
            long size = readBE(blob, nul + 1 + offBytes, 4);
            wordList.add(word);
            recList.add(new long[] {offset, size});
            pos = nul + 1 + offBytes + 4;
        }

        int n = wordList.size();
        byte[][] words = wordList.toArray(new byte[0][]);
        long[] offsets = new long[n];
        int[] sizes = new int[n];
        for (int i = 0; i < n; i++) {
            offsets[i] = recList.get(i)[0];
            sizes[i] = (int) recList.get(i)[1];
        }
        return new StarDictIndex(words, offsets, sizes);
    }

    static byte[] readFully(File file) throws IOException {
        boolean gz = file.getName().toLowerCase().endsWith(".gz");
        try (InputStream raw = new FileInputStream(file);
             InputStream in = gz ? new GZIPInputStream(raw, 32 * 1024) : raw) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(
                    (int) Math.min(Math.max(file.length(), 64), 1 << 20));
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static long readBE(byte[] b, int pos, int len) {
        long v = 0;
        for (int i = 0; i < len; i++) {
            v = (v << 8) | (b[pos + i] & 0xFF);
        }
        return v;
    }

    public int size() {
        return words.length;
    }

    public String wordAt(int i) {
        return new String(words[i], StandardCharsets.UTF_8);
    }

    public IndexEntry entryAt(int i) {
        return new IndexEntry(i, wordAt(i), offsets[i], sizes[i]);
    }

    /** First position whose word fold-compares {@code >= key}; {@code size()} if none. */
    int lowerBoundFold(byte[] key) {
        int lo = 0;
        int hi = words.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (StarDictCollation.compareFold(words[mid], key) < 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    /**
     * All entries whose headword equals {@code word} ASCII-case-insensitively.
     * Entries that match byte-exactly are listed first.
     */
    public List<IndexEntry> exactMatches(String word) {
        byte[] key = word.getBytes(StandardCharsets.UTF_8);
        List<IndexEntry> exact = new ArrayList<>();
        List<IndexEntry> folded = new ArrayList<>();
        for (int i = lowerBoundFold(key);
                i < words.length && StarDictCollation.compareFold(words[i], key) == 0; i++) {
            if (StarDictCollation.compareBytes(words[i], key) == 0) {
                exact.add(entryAt(i));
            } else {
                folded.add(entryAt(i));
            }
        }
        exact.addAll(folded);
        return exact;
    }

    /** Up to {@code max} entries whose headword starts with {@code prefix} (fold). */
    public List<IndexEntry> prefixMatches(String prefix, int max) {
        byte[] key = prefix.getBytes(StandardCharsets.UTF_8);
        List<IndexEntry> out = new ArrayList<>();
        for (int i = lowerBoundFold(key);
                i < words.length && out.size() < max
                        && StarDictCollation.foldStartsWith(words[i], key); i++) {
            out.add(entryAt(i));
        }
        return out;
    }

    /**
     * Alphabetical neighbours around where {@code word} would be inserted —
     * used for "did you mean" suggestions when there is no exact match.
     */
    public List<String> nearWords(String word, int max) {
        if (words.length == 0 || max <= 0) {
            return new ArrayList<>();
        }
        byte[] key = word.getBytes(StandardCharsets.UTF_8);
        int at = lowerBoundFold(key);
        int from = Math.max(0, at - max / 2);
        int to = Math.min(words.length, from + max);
        from = Math.max(0, to - max);
        List<String> out = new ArrayList<>(to - from);
        for (int i = from; i < to; i++) {
            out.add(wordAt(i));
        }
        return out;
    }
}
