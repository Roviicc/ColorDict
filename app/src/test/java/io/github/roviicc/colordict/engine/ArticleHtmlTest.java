package io.github.roviicc.colordict.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ArticleHtmlTest {

    @Test
    public void htmlDictionaryPassesMarkupThrough() throws Exception {
        try (StarDictDictionary dict =
                     StarDictDictionary.load(TestFixtures.file("html/html.ifo"))) {
            String html = dict.articleHtml(dict.lookup("link").get(0));
            assertTrue(html.contains("<a href=\"bword://target\">target</a>"));
            assertTrue(html.contains("https://example.com/"));
        }
    }

    @Test
    public void escaping() {
        assertEquals("a &amp; b &lt;c&gt;", ArticleHtml.escape("a & b <c>"));
        assertEquals("one<br>two", ArticleHtml.textToHtml("one\ntwo"));
    }

    @Test
    public void hrefEncodeIsPercentUtf8() {
        assertEquals("caf%C3%A9", ArticleHtml.hrefEncode("café"));
        assertEquals("two%20words", ArticleHtml.hrefEncode("two words"));
        assertEquals("plain-word_1.2~", ArticleHtml.hrefEncode("plain-word_1.2~"));
    }

    @Test
    public void xdxfConversion() {
        String html = ArticleHtml.xdxfToHtml(
                "<k>word</k><tr>wɜːd</tr> <c c=\"#123456\">colored</c> "
                        + "<kref>other</kref> <ex>example</ex> <weird>gone</weird>");
        assertTrue(html.contains("<div class=\"xk\">word</div>"));
        assertTrue(html.contains("<span class=\"phon\">[wɜːd]</span>"));
        assertTrue(html.contains("<span style=\"color:#123456\">colored</span>"));
        assertTrue(html.contains("<a href=\"bword://other\">other</a>"));
        assertTrue(html.contains("<span class=\"xex\">example</span>"));
        assertFalse(html.contains("<weird>"));
        assertTrue(html.contains("gone")); // tag stripped, content kept
    }

    @Test
    public void pangoConversion() {
        String html = ArticleHtml.pangoToHtml(
                "<b>bold</b> <span foreground=\"#00FF00\" weight=\"bold\">green</span> <tt>mono</tt>");
        assertTrue(html.contains("<b>bold</b>"));
        assertTrue(html.contains("<span style=\"color:#00FF00\">green</span>"));
        assertTrue(html.contains("<code>mono</code>"));
    }

    @Test
    public void mediaWikiConversion() {
        String html = ArticleHtml.wikiToHtml("'''strong''' ''soft'' [[cat|cats]] [[dog]]");
        assertTrue(html.contains("<b>strong</b>"));
        assertTrue(html.contains("<i>soft</i>"));
        assertTrue(html.contains("<a href=\"bword://cat\">cats</a>"));
        assertTrue(html.contains("<a href=\"bword://dog\">dog</a>"));
    }
}
