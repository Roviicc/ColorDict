package io.github.roviicc.colordict.desktop;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import io.github.roviicc.colordict.data.Palette;
import io.github.roviicc.colordict.engine.ArticleHtml;
import io.github.roviicc.colordict.engine.DefinitionRenderer;
import io.github.roviicc.colordict.engine.DictionaryScanner;
import io.github.roviicc.colordict.engine.IndexEntry;
import io.github.roviicc.colordict.engine.StarDictCollation;
import io.github.roviicc.colordict.engine.StarDictDictionary;

/**
 * The desktop harness's dictionary collection: loads StarDict sets from
 * folders, assigns each a palette color, and answers the same aggregated
 * queries the Android app does.
 */
public final class DictionarySet {

    /** One loaded dictionary with its display color. */
    public static final class Loaded {
        public final StarDictDictionary dictionary;
        public final int color;
        public boolean enabled = true;

        Loaded(StarDictDictionary dictionary, int color) {
            this.dictionary = dictionary;
            this.color = color;
        }

        public String name() {
            return dictionary.bookName();
        }
    }

    /** A merged suggestion: the word plus the colors of dictionaries holding it. */
    public static final class Suggestion {
        public final String word;
        public final List<Integer> colors;

        Suggestion(String word, List<Integer> colors) {
            this.word = word;
            this.colors = colors;
        }
    }

    /** The aggregated answer for one query. */
    public static final class Result {
        public final String word;
        public final List<DefinitionRenderer.Section> sections;
        public final List<String> similar;

        Result(String word, List<DefinitionRenderer.Section> sections, List<String> similar) {
            this.word = word;
            this.sections = sections;
            this.similar = similar;
        }
    }

    private final List<Loaded> dictionaries = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();

    private static int collate(String a, String b) {
        return StarDictCollation.compare(a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    public List<Loaded> dictionaries() {
        return dictionaries;
    }

    public List<String> failures() {
        return failures;
    }

    /** Loads every dictionary found under {@code folder}; returns how many loaded. */
    public int addFolder(File folder) {
        int added = 0;
        for (File ifo : DictionaryScanner.findIfoFiles(folder)) {
            try {
                dictionaries.add(new Loaded(StarDictDictionary.load(ifo),
                        Palette.auto(dictionaries.size())));
                added++;
            } catch (IOException | RuntimeException e) {
                failures.add(ifo.getName() + ": " + e.getMessage());
            }
        }
        return added;
    }

    private List<Loaded> enabled() {
        List<Loaded> out = new ArrayList<>();
        for (Loaded d : dictionaries) {
            if (d.enabled) {
                out.add(d);
            }
        }
        return out;
    }

    /** Merged prefix suggestions across enabled dictionaries. */
    public List<Suggestion> suggest(String prefix, int max) {
        Map<String, Set<Integer>> merged = new TreeMap<>(DictionarySet::collate);
        for (Loaded d : enabled()) {
            for (String w : d.dictionary.suggest(prefix, max)) {
                merged.computeIfAbsent(w, k -> new LinkedHashSet<>()).add(d.color);
            }
        }
        List<Suggestion> out = new ArrayList<>();
        for (Map.Entry<String, Set<Integer>> e : merged.entrySet()) {
            if (out.size() >= max) {
                break;
            }
            out.add(new Suggestion(e.getKey(), new ArrayList<>(e.getValue())));
        }
        return out;
    }

    /** Looks the word up in every enabled dictionary. */
    public Result define(String word) {
        List<DefinitionRenderer.Section> sections = new ArrayList<>();
        for (Loaded d : enabled()) {
            List<IndexEntry> entries = d.dictionary.lookup(word);
            if (entries.isEmpty()) {
                continue;
            }
            List<DefinitionRenderer.Entry> rendered = new ArrayList<>(entries.size());
            for (IndexEntry entry : entries) {
                String html;
                try {
                    html = d.dictionary.articleHtml(entry);
                } catch (IOException | RuntimeException e) {
                    html = "<i>" + ArticleHtml.escape("error reading article: "
                            + e.getMessage()) + "</i>";
                }
                rendered.add(new DefinitionRenderer.Entry(entry.word, html));
            }
            sections.add(new DefinitionRenderer.Section(d.name(), d.color, rendered));
        }

        List<String> similar = new ArrayList<>();
        if (sections.isEmpty()) {
            Set<String> near = new TreeSet<>(DictionarySet::collate);
            for (Loaded d : enabled()) {
                near.addAll(d.dictionary.nearWords(word, 8));
            }
            similar.addAll(near);
            if (similar.size() > 20) {
                similar = similar.subList(0, 20);
            }
        }
        return new Result(word, sections, similar);
    }

    /** Plain-text rendering of a result, for the command-line mode. */
    public static String toPlainText(Result result) {
        StringBuilder sb = new StringBuilder();
        if (result.sections.isEmpty()) {
            sb.append("No results for \"").append(result.word).append("\".\n");
            if (!result.similar.isEmpty()) {
                sb.append("Similar words: ").append(String.join(", ", result.similar))
                        .append('\n');
            }
            return sb.toString();
        }
        for (DefinitionRenderer.Section section : result.sections) {
            sb.append("== ").append(section.title).append(" ==\n");
            for (DefinitionRenderer.Entry e : section.entries) {
                sb.append(e.headword).append('\n')
                        .append(htmlToText(e.html)).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** Crude HTML-to-text for terminal output. */
    static String htmlToText(String html) {
        String text = html.replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</div>|</p>|<hr[^>]*>", "\n")
                .replaceAll("(?s)<[^>]*>", "");
        text = text.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&nbsp;", " ").replace("&amp;", "&");
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\n")) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty()) {
                out.append("  ").append(trimmed).append('\n');
            }
        }
        return out.toString();
    }

    /** Sorts dictionaries by name — used to keep the UI list stable. */
    public void sortByName() {
        dictionaries.sort(Comparator.comparing(Loaded::name));
    }

    public void closeAll() {
        for (Loaded d : dictionaries) {
            try {
                d.dictionary.close();
            } catch (IOException ignored) {
                // Closing on exit; nothing useful to do with the failure.
            }
        }
        dictionaries.clear();
    }
}
