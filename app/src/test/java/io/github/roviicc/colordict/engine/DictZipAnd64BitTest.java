package io.github.roviicc.colordict.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The dz64 fixture combines a dictzip .dict.dz (96-byte chunks, so every
 * article spans chunk boundaries), a gzipped .idx.gz, and 64-bit offsets.
 */
public class DictZipAnd64BitTest {

    private static StarDictDictionary dict;

    @BeforeClass
    public static void open() throws Exception {
        dict = StarDictDictionary.load(TestFixtures.file("dz64/dz64.ifo"));
    }

    @AfterClass
    public static void close() throws Exception {
        dict.close();
    }

    @Test
    public void metadataReports64BitOffsets() {
        assertEquals(64, dict.info().idxoffsetbits);
        assertEquals(12, dict.wordCount());
    }

    @Test
    public void everyArticleReadsBackIntactAcrossChunks() throws Exception {
        String expectedBody = "lorem ipsum dolor sit amet ".repeat(12).trim();
        for (String word : new String[] {"alpha", "bravo", "charlie", "delta", "echo",
                "foxtrot", "golf", "hotel", "india", "juliet", "kilo", "lima"}) {
            List<IndexEntry> hits = dict.lookup(word);
            assertEquals(word, 1, hits.size());
            String raw = new String(dict.rawArticle(hits.get(0)), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals("[" + word + "] " + expectedBody, raw);
        }
    }

    @Test
    public void renderedHtmlContainsBody() throws Exception {
        String html = dict.articleHtml(dict.lookup("kilo").get(0));
        assertTrue(html.contains("lorem ipsum dolor sit amet"));
    }
}
