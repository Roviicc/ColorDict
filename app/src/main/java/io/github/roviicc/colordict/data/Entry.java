package io.github.roviicc.colordict.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One dictionary entry as returned by the server's /api/define endpoint
 * (see api/define.ts for the schema). Parsing is lenient: missing fields
 * become empty values so an older or newer server never crashes the app.
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

    public final String word;
    public final boolean found;
    public final String phonetic;
    public final String syllables;
    public final List<Sense> senses;
    public final Tone tone;
    public final String connotation;
    public final String usageNote;
    public final String etymology;
    public final List<String> relatedForms;
    public final List<String> suggestions;

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
        JSONObject c = o.optJSONObject("connotation");
        tone = parseTone(c == null ? null : c.optString("tone", null));
        connotation = c == null ? "" : c.optString("summary", "");
        usageNote = o.optString("usage_note", "");
        etymology = o.optString("etymology", "");
        relatedForms = strings(o.optJSONArray("related_forms"));
        suggestions = strings(o.optJSONArray("suggestions"));
        json = o.toString();
    }

    public static Entry fromJson(String json) throws JSONException {
        return new Entry(new JSONObject(json));
    }

    public String toJson() {
        return json;
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
        if (!connotation.isEmpty()) {
            sb.append("\n\nConnotation: ").append(connotation);
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
