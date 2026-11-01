package io.github.roviicc.colordict.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class WordsTest {

    @Test
    public void keyNormalizesCaseAndSpacing() {
        assertEquals("in lieu of", Words.key("  In   LIEU of "));
    }

    private static void assertCandidate(String word, String base) {
        List<String> c = Words.baseFormCandidates(word);
        assertTrue(word + " should suggest " + base + " but gave " + c, c.contains(base));
    }

    @Test
    public void guessesBaseFormsOfRegularInflections() {
        assertCandidate("cities", "city");
        assertCandidate("boxes", "box");
        assertCandidate("running", "run");
        assertCandidate("stopped", "stop");
        assertCandidate("making", "make");
        assertCandidate("hoped", "hope");
        assertCandidate("happier", "happy");
        assertCandidate("bigger", "big");
        assertCandidate("dying", "die");
        assertCandidate("women", "woman");
        assertCandidate("teacher's", "teacher");
        assertCandidate("ice-cream", "ice cream");
    }

    @Test
    public void candidatesNeverIncludeTheWordItself() {
        assertFalse(Words.baseFormCandidates("serene").contains("serene"));
    }

    @Test
    public void distanceCountsSwapsAsOneEdit() {
        assertEquals(0, Words.distance("serene", "serene", 2));
        assertEquals(1, Words.distance("serine", "serene", 2));
        assertEquals(1, Words.distance("recieve", "receive", 2)); // swapped letters = 1 edit
        assertEquals(2, Words.distance("recieve", "reprieve", 2));
        assertEquals(3, Words.distance("cat", "dictionary", 2)); // gave up: max + 1
    }
}
