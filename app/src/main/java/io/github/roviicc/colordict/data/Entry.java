package io.github.roviicc.colordict.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One dictionary entry. Definitions come from the built-in dictionary
 * (see {@code data.dictionary.BuiltInDictionary}, built from Open English WordNet); the
 * connotation is optional, written by Claude only when the user asks for it
 * and then stored per word so it shows up automatically from then on.
 * Parsing is lenient: missing fields become empty values.
 */
public final class Entry {

    public enum Tone { POSITIVE, NEGATIVE, NEUTRAL, MIXED }

    public static final class Sense {
        public final String partOfSpeech;
        public final String definition;
        public final List<String> examples;
        public final List<String> synonyms;
        public final List<String> antonyms;
        public final String register;

        Sense(String partOfSpeech, String definition, List<String> examples,
              List<String> synonyms, List<String> antonyms, String register) {
            this.partOfSpeech = partOfSpeech;
            this.definition = definition;
            this.examples = examples;
            this.synonyms = synonyms;
            this.antonyms = antonyms;
            this.register = register;
        }
    }

    /** How a word feels in use. Present only once the user has asked for it. */
    public static final class Connotation {
        public final Tone tone;
        public final String summary;
        public final List<String> feelsLike;
        public final String caution;

        Connotation(Tone tone, String summary, List<String> feelsLike, String caution) {
            this.tone = tone;
            this.summary = summary;
            this.feelsLike = feelsLike;
            this.caution = caution;
        }
    }

    /** Value of the {@code "source"} field for entries written by Claude. */
    public static final String SOURCE_CLAUDE = "claude";

    public final String word;
    public final boolean found;
    public final String phonetic;
    public final String syllables;
    public final List<Sense> senses;
    /** Null until the user taps "Check connotation". */
    public final Connotation connotation;
    public final String usageNote;
    public final String etymology;
    public final List<String> relatedForms;
    public final List<String> suggestions;
    /** True when the meanings came from Claude (word not in the built-in dictionary). */
    public final boolean fromClaude;

    /** The raw JSON, kept so the entry can be cached exactly as received. */
    private final String json;

    private Entry(JSONObject o) {
        word = o.optString("word", "");
        found = o.optBoolean("found", true);
        phonetic = o.optString("phonetic", "");
        syllables = o.optString("syllables", "");
        List<Sense> list = new ArrayList<>();
        JSONArray arr = o.optJSONArray("senses");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject s = arr.optJSONObject(i);
                if (s == null) {
                    continue;
                }
                String definition = s.optString("definition", "");
                if (definition.isEmpty()) {
                    continue;
                }
                list.add(new Sense(
                        s.optString("part_of_speech", ""),
                        definition,
                        strings(s.optJSONArray("examples")),
                        strings(s.optJSONArray("synonyms")),
                        strings(s.optJSONArray("antonyms")),
                        s.optString("register", "")));
            }
        }
        senses = Collections.unmodifiableList(list);
        connotation = parseConnotation(o.optJSONObject("connotation"));
        usageNote = o.optString("usage_note", "");
        etymology = o.optString("etymology", "");
        relatedForms = strings(o.optJSONArray("related_forms"));
        suggestions = strings(o.optJSONArray("suggestions"));
        fromClaude = SOURCE_CLAUDE.equals(o.optString("source", ""));
        json = o.toString();
    }

    public static Entry fromJson(String json) throws JSONException {
        return new Entry(new JSONObject(json));
    }

    /** Part-of-speech names by the index stored in dictionary rows (see tools/build_dictionary.py). */
    private static final String[] POS = {"", "noun", "verb", "adjective", "adverb"};

    /**
     * Expands a compact built-in dictionary row:
     * {@code [phonetic, [related...], [[posIndex, definition, [examples], [synonyms], [antonyms]], ...]]}.
     */
    public static Entry fromDictionaryRow(String word, String compactJson) throws JSONException {
        JSONArray row = new JSONArray(compactJson);
        JSONArray senses = new JSONArray();
        JSONArray rows = row.getJSONArray(2);
        for (int i = 0; i < rows.length(); i++) {
            JSONArray r = rows.getJSONArray(i);
            int pos = r.optInt(0, 0);
            senses.put(new JSONObject()
                    .put("part_of_speech", pos >= 0 && pos < POS.length ? POS[pos] : "")
                    .put("definition", r.getString(1))
                    .put("examples", r.getJSONArray(2))
                    .put("synonyms", r.getJSONArray(3))
                    .put("antonyms", r.getJSONArray(4))
                    .put("register", ""));
        }
        return new Entry(new JSONObject()
                .put("word", word)
                .put("found", true)
                .put("phonetic", row.optString(0, ""))
                .put("senses", senses)
                .put("related_forms", row.getJSONArray(1)));
    }

    /** An entry for a word the dictionary doesn't have, with spelling suggestions. */
    public static Entry notFound(String word, List<String> suggestions) {
        try {
            return new Entry(new JSONObject()
                    .put("word", word)
                    .put("found", false)
                    .put("senses", new JSONArray())
                    .put("suggestions", new JSONArray(suggestions)));
        } catch (JSONException e) {
            throw new AssertionError(e);
        }
    }

    public String toJson() {
        return json;
    }

    /** A copy of this entry with the given connotation (as returned by Claude) attached. */
    public Entry withConnotation(JSONObject connotationJson) throws JSONException {
        JSONObject o = new JSONObject(json);
        o.put("connotation", connotationJson);
        return new Entry(o);
    }

    private static Connotation parseConnotation(JSONObject c) {
        if (c == null) {
            return null;
        }
        String summary = c.optString("summary", "").trim();
        if (summary.isEmpty()) {
            return null;
        }
        return new Connotation(parseTone(c.optString("tone", null)), summary,
                strings(c.optJSONArray("feels_like")), c.optString("caution", "").trim());
    }

    /** True when there is at least one definition to show. */
    public boolean hasSenses() {
        return found && !senses.isEmpty();
    }

    /** First definition, used for one-line previews in lists. */
    public String preview() {
        if (!hasSenses()) {
            return "";
        }
        Sense s = senses.get(0);
        return s.partOfSpeech.isEmpty() ? s.definition : s.partOfSpeech + " · " + s.definition;
    }

    /** Plain-text version for sharing and copying. */
    public String toPlainText() {
        StringBuilder sb = new StringBuilder(word);
        if (!phonetic.isEmpty()) {
            sb.append("  ").append(phonetic);
        }
        String lastPos = null;
        int n = 0;
        for (Sense s : senses) {
            if (!s.partOfSpeech.equals(lastPos)) {
                sb.append("\n\n").append(s.partOfSpeech);
                lastPos = s.partOfSpeech;
                n = 0;
            }
            sb.append("\n").append(++n).append(". ").append(s.definition);
        }
        if (connotation != null) {
            sb.append("\n\nConnotation: ").append(connotation.summary);
        }
        return sb.toString();
    }

    static Tone parseTone(String value) {
        if (value == null) {
            return Tone.NEUTRAL;
        }
        switch (value) {
            case "positive":
                return Tone.POSITIVE;
            case "negative":
                return Tone.NEGATIVE;
            case "mixed":
                return Tone.MIXED;
            default:
                return Tone.NEUTRAL;
        }
    }

    private static List<String> strings(JSONArray arr) {
        if (arr == null) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i, "").trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return Collections.unmodifiableList(out);
    }
}
