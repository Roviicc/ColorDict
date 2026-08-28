package io.github.roviicc.colordict.engine;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.nio.charset.StandardCharsets;

/**
 * A loaded StarDict dictionary: .ifo metadata, the in-memory .idx (and
 * optional .syn) indexes, and random access to the .dict(.dz) payload.
 */
public final class StarDictDictionary implements Closeable {

    private final File ifoFile;
    private final StarDictInfo info;
    private final StarDictIndex index;
    private final SynonymIndex synonyms; // nullable
    private final DictData data;

    private StarDictDictionary(File ifoFile, StarDictInfo info, StarDictIndex index,
                               SynonymIndex synonyms, DictData data) {
        this.ifoFile = ifoFile;
        this.info = info;
        this.index = index;
        this.synonyms = synonyms;
        this.data = data;
    }

    /** Loads a dictionary given its .ifo file, resolving the sibling files. */
    public static StarDictDictionary load(File ifoFile) throws IOException {
        StarDictInfo info = StarDictInfo.parse(ifoFile);

        File idxFile = sibling(ifoFile, ".idx", ".idx.gz");
        if (idxFile == null) {
            throw new IOException(ifoFile.getName() + ": missing .idx/.idx.gz file");
        }
        File dictFile = sibling(ifoFile, ".dict.dz", ".dict");
        if (dictFile == null) {
            throw new IOException(ifoFile.getName() + ": missing .dict/.dict.dz file");
        }

        StarDictIndex index = StarDictIndex.load(idxFile, info.idxoffsetbits);
        File synFile = sibling(ifoFile, ".syn");
        SynonymIndex synonyms = synFile != null ? SynonymIndex.load(synFile) : null;
        DictData data = DictData.open(dictFile);
        return new StarDictDictionary(ifoFile, info, index, synonyms, data);
    }

    private static File sibling(File ifoFile, String... extensions) {
        String name = ifoFile.getName();
        String base = name.substring(0, name.length() - ".ifo".length());
        for (String ext : extensions) {
            File f = new File(ifoFile.getParentFile(), base + ext);
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }

    public File ifoFile() {
        return ifoFile;
    }

    public StarDictInfo info() {
        return info;
    }

    public String bookName() {
        return info.bookname;
    }

    public int wordCount() {
        return index.size();
    }

    /**
     * All entries matching {@code word} ASCII-case-insensitively, both by
     * headword and by .syn synonym; byte-exact headword matches come first
     * and duplicates are collapsed by index position.
     */
    public List<IndexEntry> lookup(String word) {
        List<IndexEntry> result = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        for (IndexEntry e : index.exactMatches(word)) {
            if (seen.add(e.position)) {
                result.add(e);
            }
        }
        if (synonyms != null) {
            for (int target : synonyms.exactTargets(word)) {
                if (target >= 0 && target < index.size() && seen.add(target)) {
                    result.add(index.entryAt(target));
                }
            }
        }
        return result;
    }

    /** Up to {@code max} distinct suggestion words starting with {@code prefix}. */
    public List<String> suggest(String prefix, int max) {
        TreeSet<String> merged = new TreeSet<>((a, b) -> StarDictCollation.compare(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)));
        for (IndexEntry e : index.prefixMatches(prefix, max)) {
            merged.add(e.word);
        }
        if (synonyms != null) {
            merged.addAll(synonyms.prefixWords(prefix, max));
        }
        List<String> out = new ArrayList<>(Math.min(max, merged.size()));
        for (String w : merged) {
            if (out.size() >= max) {
                break;
            }
            out.add(w);
        }
        return out;
    }

    /** Alphabetical neighbours for "did you mean" when a lookup finds nothing. */
    public List<String> nearWords(String word, int max) {
        return index.nearWords(word, max);
    }

    public byte[] rawArticle(IndexEntry entry) throws IOException {
        return data.read(entry.offset, entry.size);
    }

    public Article article(IndexEntry entry) throws IOException {
        return ArticleParser.parse(rawArticle(entry), info.sametypesequence);
    }

    /** The article rendered as a self-contained HTML fragment. */
    public String articleHtml(IndexEntry entry) throws IOException {
        return ArticleHtml.render(article(entry));
    }

    @Override
    public void close() throws IOException {
        data.close();
    }
}
