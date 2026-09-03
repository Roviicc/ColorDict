package io.github.roviicc.colordict.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/**
 * The stage-6 done-check, app side: an inflected form names its relation to
 * the headword without the reader knowing the headword. The same four words
 * are asserted by {@code tools/resolve.py --check} against the shipped index.
 */
public class MorphologyTest {

    private static Set<String> pos(String... p) {
        return new HashSet<>(Arrays.asList(p));
    }

    @Test
    public void exactHeadwordHasNoRelation() {
        assertNull(Morphology.describe("saw", "saw", pos("noun", "verb")));
        assertNull(Morphology.describe("Saw", "saw", Collections.emptySet()));
        assertNull(Morphology.describe("", "saw", Collections.emptySet()));
    }

    @Test
    public void theFourWordsOfThePlan() {
        assertEquals("past tense of", Morphology.describe("saw", "see", pos("verb")));
        assertEquals("present participle of",
                Morphology.describe("emerging", "emerge", pos("verb")));
        assertEquals("comparative of", Morphology.describe("better", "good", pos("adjective")));
        assertEquals("comparative of", Morphology.describe("better", "well", pos("adverb")));
        assertEquals("past tense and past participle of",
                Morphology.describe("left", "leave", pos("verb")));
    }

    @Test
    public void regularSuffixesAreNamedFromTheStrings() {
        assertEquals("past tense or past participle of",
                Morphology.describe("emerged", "emerge", pos("verb")));
        assertEquals("past tense or past participle of",
                Morphology.describe("stopped", "stop", pos("verb")));
        assertEquals("past tense or past participle of",
                Morphology.describe("carried", "carry", pos("verb")));
        assertEquals("present participle of", Morphology.describe("running", "run", pos("verb")));
        assertEquals("present participle of", Morphology.describe("lying", "lie", pos("verb")));
        assertEquals("comparative of", Morphology.describe("happier", "happy", pos("adjective")));
        assertEquals("superlative of", Morphology.describe("nicest", "nice", pos("adjective")));
    }

    @Test
    public void sFormsUseThePartOfSpeechWhenItIsKnown() {
        assertEquals("plural of", Morphology.describe("books", "book", pos("noun")));
        assertEquals("third-person singular of",
                Morphology.describe("emerges", "emerge", pos("verb")));
        assertEquals("plural or third-person form of",
                Morphology.describe("runs", "run", pos("noun", "verb")));
        assertEquals("plural or third-person form of",
                Morphology.describe("runs", "run", Collections.emptySet()));
        assertEquals("plural of", Morphology.describe("families", "family", pos("noun")));
    }

    @Test
    public void irregularsComeFromTheTable() {
        assertEquals("past participle of", Morphology.describe("seen", "see", pos("verb")));
        assertEquals("past tense of", Morphology.describe("went", "go", pos("verb")));
        assertEquals("past participle of", Morphology.describe("gone", "go", pos("verb")));
        assertEquals("past participle of", Morphology.describe("sung", "sing", pos("verb")));
        assertEquals("plural of", Morphology.describe("children", "child", pos("noun")));
        assertEquals("superlative of", Morphology.describe("best", "good", pos("adjective")));
        assertEquals("past tense of", Morphology.describe("was", "be", pos("verb")));
    }

    @Test
    public void anUnknownRelationIsStillAForm() {
        assertEquals("a form of", Morphology.describe("aardwolves", "aardwolf", pos("noun")));
    }

    @Test
    public void formLineReadsAsASentenceFragment() {
        assertEquals("emerged — past tense or past participle of emerge",
                Morphology.formLine("emerged", "emerge", pos("verb")));
        assertNull(Morphology.formLine("emerge", "emerge", pos("verb")));
    }
}
