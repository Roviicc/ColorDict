package io.github.roviicc.colordict.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/** Exercises .ifo parsing, index search, and .syn lookups on the basic fixture. */
public class BasicDictionaryTest {

    private static StarDictDictionary dict;

    @BeforeClass
    public static void open() throws Exception {
        dict = StarDictDictionary.load(TestFixtures.file("basic/basic.ifo"));
    }

    @AfterClass
    public static void close() throws Exception {
        dict.close();
    }

    @Test
    public void ifoMetadata() {
        assertEquals("Basic Test Dict", dict.bookName());
        assertEquals(12, dict.wordCount());
        assertEquals("m", dict.info().sametypesequence);
        assertEquals(4, dict.info().synwordcount);
    }

    @Test
    public void exactLookupPrefersExactCase() throws Exception {
        List<IndexEntry> hits = dict.lookup("apple");
        assertEquals(3, hits.size()); // apple, Apple, APPLE fold together
        assertEquals("apple", hits.get(0).word);
        assertTrue(dict.articleHtml(hits.get(0)).contains("round fruit"));

        assertEquals("APPLE", dict.lookup("APPLE").get(0).word);
        assertEquals("Apple", dict.lookup("Apple").get(0).word);
    }

    @Test
    public void unicodeHeadwords() throws Exception {
        List<IndexEntry> hits = dict.lookup("naïve");
        assertEquals(1, hits.size());
        assertTrue(dict.articleHtml(hits.get(0)).contains("innocent trust"));
    }

    @Test
    public void prefixSearchIsCaseInsensitive() {
        List<String> got = dict.suggest("CAT", 10);
        assertEquals(List.of("cat", "catalog", "catapult", "cattle"), got);
    }

    @Test
    public void prefixSearchHonorsLimit() {
        assertEquals(2, dict.suggest("cat", 2).size());
    }

    @Test
    public void synonymResolvesToTargetEntry() {
        List<IndexEntry> hits = dict.lookup("automobile");
        assertEquals(1, hits.size());
        assertEquals("car", hits.get(0).word);

        // Case-insensitive synonym match too.
        assertEquals("résumé", dict.lookup("cv").get(0).word);
    }

    @Test
    public void synonymAndHeadwordDeduplicate() {
        // "car" matches by headword; its synonyms must not duplicate it.
        assertEquals(1, dict.lookup("car").size());
    }

    @Test
    public void suggestIncludesSynonyms() {
        assertTrue(dict.suggest("pomm", 10).contains("pomme"));
    }

    @Test
    public void nearWordsSurroundInsertionPoint() {
        List<String> near = dict.nearWords("cats", 4);
        assertTrue(near.contains("catapult"));
        assertTrue(near.contains("cattle"));
    }

    @Test
    public void missingWordHasNoMatches() {
        assertTrue(dict.lookup("zzzz").isEmpty());
        assertTrue(dict.suggest("zzzz", 10).isEmpty());
    }
}
