package io.github.roviicc.colordict.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.json.JSONObject;
import org.junit.Test;

public class EntryTest {

    static final String SERENE = "{\"word\":\"serene\",\"found\":true,\"phonetic\":\"/səˈriːn/\","
            + "\"syllables\":\"se·rene\",\"senses\":[{\"part_of_speech\":\"adjective\","
            + "\"definition\":\"Calm, peaceful, and untroubled.\",\"examples\":[\"A serene smile.\"],"
            + "\"synonyms\":[\"calm\",\"tranquil\"],\"antonyms\":[\"agitated\"],\"register\":\"\"},"
            + "{\"part_of_speech\":\"adjective\",\"definition\":\"Clear and bright (of the sky).\","
            + "\"examples\":[],\"synonyms\":[],\"antonyms\":[],\"register\":\"literary\"}],"
            + "\"usage_note\":\"\",\"etymology\":\"From Latin serenus.\","
            + "\"related_forms\":[\"serenely\",\"serenity\"],\"suggestions\":[]}";

    @Test
    public void parsesFullEntry() throws Exception {
        Entry e = Entry.fromJson(SERENE);
        assertEquals("serene", e.word);
        assertTrue(e.hasSenses());
        assertEquals("/səˈriːn/", e.phonetic);
        assertEquals(2, e.senses.size());
        assertEquals("adjective", e.senses.get(0).partOfSpeech);
        assertEquals(2, e.senses.get(0).synonyms.size());
        assertEquals("literary", e.senses.get(1).register);
        assertNull("connotation is only fetched on request", e.connotation);
        assertEquals("adjective · Calm, peaceful, and untroubled.", e.preview());
    }

    @Test
    public void connotationIsAttachedAndSurvivesStorage() throws Exception {
        Entry e = Entry.fromJson(SERENE).withConnotation(new JSONObject(
                "{\"tone\":\"positive\",\"summary\":\"Gentle and admiring.\","
                        + "\"feels_like\":[\"calm\",\"approving\"],\"caution\":\"\"}"));
        Entry reloaded = Entry.fromJson(e.toJson());
        assertNotNull(reloaded.connotation);
        assertEquals(Entry.Tone.POSITIVE, reloaded.connotation.tone);
        assertEquals("Gentle and admiring.", reloaded.connotation.summary);
        assertEquals(2, reloaded.connotation.feelsLike.size());
        assertEquals("", reloaded.connotation.caution);
        assertEquals(2, reloaded.senses.size());
        assertTrue(reloaded.toPlainText().contains("Connotation: Gentle and admiring."));
    }

    @Test
    public void expandsCompactDictionaryRows() throws Exception {
        Entry e = Entry.fromDictionaryRow("serene", "[\"/səˈriːn/\",[\"serenity\"],"
                + "[[3,\"Not agitated.\",[\"He remained serene.\"],[\"calm\",\"tranquil\"],[]],"
                + "[1,\"A title.\",[],[],[\"agitation\"]]]]");
        assertEquals("serene", e.word);
        assertTrue(e.hasSenses());
        assertEquals("/səˈriːn/", e.phonetic);
        assertEquals("adjective", e.senses.get(0).partOfSpeech);
        assertEquals("noun", e.senses.get(1).partOfSpeech);
        assertEquals("tranquil", e.senses.get(0).synonyms.get(1));
        assertEquals("agitation", e.senses.get(1).antonyms.get(0));
        assertEquals("serenity", e.relatedForms.get(0));
        assertNull(e.connotation);
        // A saved connotation attaches to a built-in entry.
        Entry withC = e.withConnotation(new JSONObject("{\"tone\":\"positive\",\"summary\":\"Calm.\"}"));
        assertEquals(Entry.Tone.POSITIVE, withC.connotation.tone);
    }

    @Test
    public void notFoundCarriesSuggestions() {
        Entry e = Entry.notFound("recieve", java.util.Arrays.asList("receive", "relieve"));
        assertFalse(e.hasSenses());
        assertEquals("recieve", e.word);
        assertEquals(2, e.suggestions.size());
    }

    @Test
    public void roundTripsThroughJson() throws Exception {
        Entry e = Entry.fromJson(Entry.fromJson(SERENE).toJson());
        assertEquals("serene", e.word);
        assertEquals(2, e.senses.size());
    }

    @Test
    public void toleratesMissingFieldsAndUnknownTone() throws Exception {
        Entry e = Entry.fromJson("{\"word\":\"x\",\"senses\":[{\"definition\":\"d\"},{}],"
                + "\"connotation\":{\"tone\":\"sarcastic\",\"summary\":\"s\"}}");
        assertEquals(1, e.senses.size());
        assertEquals("", e.senses.get(0).partOfSpeech);
        assertTrue(e.senses.get(0).examples.isEmpty());
        assertEquals(Entry.Tone.NEUTRAL, e.connotation.tone);
        assertEquals("", e.etymology);
    }

    @Test
    public void notFoundEntryHasNoSensesButSuggestions() throws Exception {
        Entry e = Entry.fromJson("{\"word\":\"recieve\",\"found\":false,\"senses\":[],"
                + "\"suggestions\":[\"receive\"]}");
        assertFalse(e.hasSenses());
        assertEquals("receive", e.suggestions.get(0));
        assertEquals("", e.preview());
    }

    @Test
    public void plainTextGroupsByPartOfSpeech() throws Exception {
        String text = Entry.fromJson(SERENE).toPlainText();
        assertTrue(text.startsWith("serene  /səˈriːn/"));
        assertTrue(text.contains("adjective\n1. Calm"));
        assertTrue(text.contains("\n2. Clear and bright"));
    }
}
