package io.github.roviicc.colordict.engine;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * An in-memory StarDict .syn file: records sorted like the .idx, each
 * {@code synonym\0 + index-of-.idx-entry (u32 BE)}.
 */
public final class SynonymIndex {

    private final byte[][] words;
    private final int[] targets;

    private SynonymIndex(byte[][] words, int[] targets) {
        this.words = words;
        this.targets = targets;
    }

    public static SynonymIndex load(File file) throws IOException {
        byte[] blob = StarDictIndex.readFully(file);
        List<byte[]> wordList = new ArrayList<>();
        List<Integer> targetList = new ArrayList<>();
        int pos = 0;
        while (pos < blob.length) {
            int nul = pos;
            while (nul < blob.length && blob[nul] != 0) {
                nul++;
            }
            if (nul + 5 > blob.length) {
                break;
            }
            byte[] word = new byte[nul - pos];
            System.arraycopy(blob, pos, word, 0, word.length);
            int target = ((blob[nul + 1] & 0xFF) << 24) | ((blob[nul + 2] & 0xFF) << 16)
                    | ((blob[nul + 3] & 0xFF) << 8) | (blob[nul + 4] & 0xFF);
            wordList.add(word);
            targetList.add(target);
            pos = nul + 5;
        }
        int n = wordList.size();
        int[] targets = new int[n];
        for (int i = 0; i < n; i++) {
            targets[i] = targetList.get(i);
        }
        return new SynonymIndex(wordList.toArray(new byte[0][]), targets);
    }

    public int size() {
        return words.length;
    }

    private int lowerBoundFold(byte[] key) {
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

    /** .idx entry positions whose synonym equals {@code word} (fold), exact bytes first. */
    public List<Integer> exactTargets(String word) {
        byte[] key = word.getBytes(StandardCharsets.UTF_8);
        List<Integer> exact = new ArrayList<>();
        List<Integer> folded = new ArrayList<>();
        for (int i = lowerBoundFold(key);
                i < words.length && StarDictCollation.compareFold(words[i], key) == 0; i++) {
            if (StarDictCollation.compareBytes(words[i], key) == 0) {
                exact.add(targets[i]);
            } else {
                folded.add(targets[i]);
            }
        }
        exact.addAll(folded);
        return exact;
    }

    /** Up to {@code max} synonym words starting with {@code prefix} (fold). */
    public List<String> prefixWords(String prefix, int max) {
        byte[] key = prefix.getBytes(StandardCharsets.UTF_8);
        List<String> out = new ArrayList<>();
        for (int i = lowerBoundFold(key);
                i < words.length && out.size() < max
                        && StarDictCollation.foldStartsWith(words[i], key); i++) {
            out.add(new String(words[i], StandardCharsets.UTF_8));
        }
        return out;
    }
}
