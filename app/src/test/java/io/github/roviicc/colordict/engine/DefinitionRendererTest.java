package io.github.roviicc.colordict.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class DefinitionRendererTest {

    private static final DefinitionRenderer.Labels LABELS =
            new DefinitionRenderer.Labels("No results for “x”.", "Similar words:");

    private static List<DefinitionRenderer.Section> oneSection() {
        return Collections.singletonList(new DefinitionRenderer.Section(
                "My Dictionary", 0xFF1E88E5,
                Collections.singletonList(
                        new DefinitionRenderer.Entry("cat", "a small feline"))));
    }

    @Test
    public void formLineSitsBetweenHeadwordAndBody() {
        List<DefinitionRenderer.Section> sections = Collections.singletonList(
                new DefinitionRenderer.Section("My Dictionary", 0xFF1E88E5,
                        Collections.singletonList(new DefinitionRenderer.Entry(
                                "emerge", "come out", "emerged — past tense of emerge"))));
        String html = DefinitionRenderer.page("body{}", sections,
                Collections.emptyList(), LABELS);
        int hw = html.indexOf("<div class=\"hw\">emerge</div>");
        int form = html.indexOf("<div class=\"form\">emerged — past tense of emerge</div>");
        int body = html.indexOf("<div class=\"body\">come out</div>");
        assertTrue(hw >= 0 && form > hw && body > form);
        assertFalse(DefinitionRenderer.page("body{}", oneSection(),
                Collections.emptyList(), LABELS).contains("class=\"form\""));
    }

    @Test
    public void hexColorDropsAlpha() {
        assertEquals("#1E88E5", DefinitionRenderer.hexColor(0xFF1E88E5));
        assertEquals("#000000", DefinitionRenderer.hexColor(0xFF000000));
    }

    @Test
    public void cardCarriesTheDictionaryColor() {
        String html = DefinitionRenderer.page("body{}", oneSection(),
                Collections.emptyList(), LABELS);
        assertTrue(html.contains("border-left-color:#1E88E5"));
        assertTrue(html.contains("color:#1E88E5"));
        assertTrue(html.contains("My Dictionary"));
        assertTrue(html.contains("a small feline"));
        assertTrue(html.startsWith("<!doctype html>"));
        assertTrue(html.endsWith("</body></html>"));
    }

    @Test
    public void multipleEntriesAreSeparated() {
        List<DefinitionRenderer.Section> sections = Collections.singletonList(
                new DefinitionRenderer.Section("D", 0xFF43A047, Arrays.asList(
                        new DefinitionRenderer.Entry("Apple", "capital A"),
                        new DefinitionRenderer.Entry("apple", "the fruit"))));
        String html = DefinitionRenderer.page("body{}", sections,
                Collections.emptyList(), LABELS);
        assertTrue(html.contains("<hr class=\"sep\">"));
        assertTrue(html.contains("capital A"));
        assertTrue(html.contains("the fruit"));
    }

    @Test
    public void emptyResultOffersSimilarWordsAsLinks() {
        String html = DefinitionRenderer.page("body{}", Collections.emptyList(),
                Arrays.asList("café", "cat"), LABELS);
        assertTrue(html.contains("No results for"));
        assertTrue(html.contains("Similar words:"));
        assertTrue(html.contains("href=\"bword://caf%C3%A9\""));
        assertTrue(html.contains("href=\"bword://cat\""));
        assertFalse(html.contains("class=\"card\""));
    }

    @Test
    public void dictionaryNamesAreEscaped() {
        List<DefinitionRenderer.Section> sections = Collections.singletonList(
                new DefinitionRenderer.Section("A & B <x>", 0xFFE53935,
                        Collections.singletonList(
                                new DefinitionRenderer.Entry("w", "body"))));
        String html = DefinitionRenderer.page("body{}", sections,
                Collections.emptyList(), LABELS);
        assertTrue(html.contains("A &amp; B &lt;x&gt;"));
        assertFalse(html.contains("<x>"));
    }

    @Test
    public void cssIsInlinedAndThemeAware() {
        assertTrue(DefinitionRenderer.page("body{color:red}", oneSection(),
                Collections.emptyList(), LABELS).contains("<style>body{color:red}</style>"));
        assertTrue(DefinitionRenderer.defaultCss(true).contains("#121212"));
        assertTrue(DefinitionRenderer.defaultCss(false).contains("#FAFAFA"));
    }
}
