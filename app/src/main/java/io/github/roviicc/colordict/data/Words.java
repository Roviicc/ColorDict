package io.github.roviicc.colordict.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Word normalization, base-form guessing and spelling distance. Pure Java, unit-tested. */
public final class Words {

    private Words() {
    }

    /** Lookup key: lowercase with single spaces. "Serene" and " serene " share one key. */
    public static String key(String word) {
        return word.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    // WordNet's "morphy" detachment rules: suffix -> replacement, nouns, verbs, adjectives.
    private static final String[][] RULES = {
            {"'s", ""}, {"’s", ""},
            {"ses", "s"}, {"xes", "x"}, {"zes", "z"}, {"ches", "ch"}, {"shes", "sh"},
            {"men", "man"}, {"ies", "y"}, {"s", ""},
            {"es", "e"}, {"es", ""}, {"ied", "y"}, {"ed", "e"}, {"ed", ""},
            {"ying", "ie"}, {"ing", "e"}, {"ing", ""},
            {"ier", "y"}, {"iest", "y"}, {"er", "e"}, {"er", ""}, {"est", "e"}, {"est", ""},
    };

    /**
     * Possible base forms of an inflected word, most likely first, e.g.
     * "running" → run, "stopped" → stop, "cities" → city. Does not include the
     * word itself. Candidates are guesses; the caller keeps the first one the
     * dictionary actually has.
     */
    public static List<String> baseFormCandidates(String word) {
        String w = key(word);
        Set<String> out = new LinkedHashSet<>();
        for (String[] rule : RULES) {
            if (w.length() > rule[0].length() && w.endsWith(rule[0])) {
                String stem = w.substring(0, w.length() - rule[0].length()) + rule[1];
                out.add(stem);
                // Doubled final consonant: "running" → "runn" → "run", "bigger" → "big".
                int n = stem.length();
                if (rule[1].isEmpty() && n >= 3 && stem.charAt(n - 1) == stem.charAt(n - 2)
                        && "bdfgklmnprtvz".indexOf(stem.charAt(n - 1)) >= 0) {
                    out.add(stem.substring(0, n - 1));
                }
            }
        }
        // Hyphen and space spellings of compounds: "ice-cream" ↔ "ice cream".
        if (w.contains("-")) {
            out.add(w.replace('-', ' '));
        } else if (w.contains(" ")) {
            out.add(w.replace(' ', '-'));
        }
        out.remove(w);
        return new ArrayList<>(out);
    }

    /**
     * Edit distance where swapping two neighbouring letters counts as one edit
     * ("recieve" → "receive" is 1), giving up (returning max + 1) once it
     * exceeds max.
     */
    public static int distance(String a, String b, int max) {
        if (Math.abs(a.length() - b.length()) > max) {
            return max + 1;
        }
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int rowMin = Integer.MAX_VALUE;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int v = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    v = Math.min(v, d[i - 2][j - 2] + 1);
                }
                d[i][j] = v;
                rowMin = Math.min(rowMin, v);
            }
            if (rowMin > max) {
                return max + 1;
            }
        }
        return Math.min(d[a.length()][b.length()], max + 1);
    }
}
