package io.github.roviicc.colordict.engine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Says what an inflected search term is, relative to the headword it resolved
 * to: "past tense of see", "plural of child", "comparative of good".
 *
 * <p>The .syn index answers <i>which</i> headword a form belongs to; it says
 * nothing about <i>how</i>. A reader who typed <i>emerged</i> and landed on
 * <i>emerge</i> should not have to know the headword to understand the jump,
 * so this names the relation from the two strings alone, plus the parts of
 * speech the article carries when the caller has them.
 *
 * <p>Two sources, in order: a small table of the irregular forms a reader
 * actually meets (the strong verbs, the suppletive comparatives, the handful
 * of irregular plurals), then the regular suffix rules that
 * {@code tools/wordnet_import.py} generates the index from. A form the rules
 * cannot classify is still a form - OEWN listed it - and is labelled "a form
 * of", never guessed.
 *
 * <p>Pure Java, no Android imports: the desktop harness uses it too.
 */
public final class Morphology {

    /** Form -> (headword -> label). One form can belong to several headwords. */
    private static final Map<String, Map<String, String>> IRREGULAR = new HashMap<>();

    private static final String PAST = "past tense of";
    private static final String PARTICIPLE = "past participle of";
    private static final String PAST_AND_PARTICIPLE = "past tense and past participle of";
    private static final String PAST_OR_PARTICIPLE = "past tense or past participle of";
    private static final String PRESENT_PARTICIPLE = "present participle of";
    private static final String THIRD_PERSON = "third-person singular of";
    private static final String PLURAL = "plural of";
    private static final String PLURAL_OR_THIRD = "plural or third-person form of";
    private static final String COMPARATIVE = "comparative of";
    private static final String SUPERLATIVE = "superlative of";
    private static final String FORM_OF = "a form of";

    /** head past participle; a single form after the head is both. */
    private static final String[] VERBS = {
        "arise arose arisen", "awake awoke awoken", "be was been", "bear bore borne",
        "beat beat beaten", "become became become", "begin began begun", "bend bent",
        "bet bet", "bind bound", "bite bit bitten", "bleed bled", "blow blew blown",
        "break broke broken", "breed bred", "bring brought", "build built", "burn burnt",
        "burst burst", "buy bought", "cast cast", "catch caught", "choose chose chosen",
        "cling clung", "come came come", "cost cost", "creep crept", "cut cut",
        "deal dealt", "dig dug", "do did done", "draw drew drawn", "dream dreamt",
        "drink drank drunk", "drive drove driven", "eat ate eaten", "fall fell fallen",
        "feed fed", "feel felt", "fight fought", "find found", "flee fled",
        "fling flung", "fly flew flown", "forbid forbade forbidden",
        "forget forgot forgotten", "forgive forgave forgiven", "freeze froze frozen",
        "get got gotten", "give gave given", "go went gone", "grind ground",
        "grow grew grown", "hang hung", "have had", "hear heard", "hide hid hidden",
        "hit hit", "hold held", "hurt hurt", "keep kept", "kneel knelt", "know knew known",
        "lay laid", "lead led", "lean leant", "leap leapt", "learn learnt", "leave left",
        "lend lent", "let let", "lie lay lain", "light lit", "lose lost", "make made",
        "mean meant", "meet met", "pay paid", "put put", "quit quit", "read read",
        "ride rode ridden", "ring rang rung", "rise rose risen", "run ran run",
        "say said", "see saw seen", "seek sought", "sell sold", "send sent", "set set",
        "shake shook shaken", "shed shed", "shine shone", "shoot shot", "show showed shown",
        "shrink shrank shrunk", "shut shut", "sing sang sung", "sink sank sunk",
        "sit sat", "sleep slept", "slide slid", "smell smelt", "speak spoke spoken",
        "speed sped", "spell spelt", "spend spent", "spill spilt", "spin spun",
        "spit spat", "split split", "spoil spoilt", "spread spread", "spring sprang sprung",
        "stand stood", "steal stole stolen", "stick stuck", "sting stung", "stink stank stunk",
        "strike struck", "strive strove striven", "swear swore sworn", "sweep swept",
        "swell swelled swollen", "swim swam swum", "swing swung", "take took taken",
        "teach taught", "tear tore torn", "tell told", "think thought", "throw threw thrown",
        "tread trod trodden", "understand understood", "wake woke woken", "wear wore worn",
        "weave wove woven", "weep wept", "win won", "wind wound", "wring wrung",
        "write wrote written",
    };

    /** head comparative superlative. */
    private static final String[] COMPARATIVES = {
        "good better best", "well better best", "bad worse worst", "badly worse worst",
        "ill worse worst", "many more most", "much more most", "little less least",
        "far farther farthest", "far further furthest", "old elder eldest",
    };

    /** head plural. */
    private static final String[] PLURALS = {
        "child children", "foot feet", "tooth teeth", "goose geese", "mouse mice",
        "louse lice", "man men", "woman women", "person people", "ox oxen", "die dice",
        "penny pence", "brother brethren",
    };

    static {
        for (String line : VERBS) {
            String[] p = line.split(" ");
            if (p.length == 2) {
                put(p[1], p[0], PAST_AND_PARTICIPLE);
            } else {
                if (p[1].equals(p[2])) {
                    put(p[1], p[0], PAST_AND_PARTICIPLE);
                } else {
                    put(p[1], p[0], PAST);
                    put(p[2], p[0], PARTICIPLE);
                }
            }
        }
        // The verb "be" and its present forms, which no rule reaches.
        put("am", "be", "first-person present of");
        put("is", "be", THIRD_PERSON);
        put("are", "be", "present tense of");
        put("were", "be", PAST);
        put("being", "be", PRESENT_PARTICIPLE);
        put("has", "have", THIRD_PERSON);
        put("does", "do", THIRD_PERSON);
        for (String line : COMPARATIVES) {
            String[] p = line.split(" ");
            put(p[1], p[0], COMPARATIVE);
            put(p[2], p[0], SUPERLATIVE);
        }
        for (String line : PLURALS) {
            String[] p = line.split(" ");
            put(p[1], p[0], PLURAL);
        }
    }

    private static void put(String form, String head, String label) {
        // A form that is both past and participle of the same verb (found,
        // built) is recorded once; a form the loop meets twice for the same
        // head keeps the first label, which the table lists first.
        IRREGULAR.computeIfAbsent(form, k -> new HashMap<>()).putIfAbsent(head, label);
    }

    private static final Pattern POS_SPAN =
            Pattern.compile("<span class=\"pos\">([a-z]+)</span>");

    private Morphology() {
    }

    /**
     * The parts of speech an article of the bundled dictionary carries, read
     * off its "Part of Speech:" rows. Empty for any other dictionary, which
     * degrades to the two-way labels.
     */
    public static Set<String> partsOfSpeech(String articleHtml) {
        Set<String> out = new HashSet<>();
        if (articleHtml == null) {
            return out;
        }
        Matcher m = POS_SPAN.matcher(articleHtml);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /**
     * The relation of {@code query} to {@code headword}, as a label to print
     * before the headword ("past tense of"), or null when the two are the same
     * word. {@code pos} is the set of parts of speech the article carries,
     * lower-case ("noun", "verb", ...); it may be empty when unknown, in which
     * case ambiguous suffixes get the honest two-way label.
     */
    public static String describe(String query, String headword, Set<String> pos) {
        if (query == null || headword == null) {
            return null;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        String h = headword.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty() || q.equals(h)) {
            return null;
        }
        Map<String, String> heads = IRREGULAR.get(q);
        if (heads != null && heads.containsKey(h)) {
            return heads.get(h);
        }
        boolean noun = pos != null && pos.contains("noun");
        boolean verb = pos != null && pos.contains("verb");
        boolean adj = pos != null && (pos.contains("adjective") || pos.contains("adverb"));
        boolean known = noun || verb || adj;

        if (isSForm(q, h)) {
            if (known && noun && !verb) {
                return PLURAL;
            }
            if (known && verb && !noun) {
                return THIRD_PERSON;
            }
            return PLURAL_OR_THIRD;
        }
        if (isEdForm(q, h)) {
            return PAST_OR_PARTICIPLE;
        }
        if (isIngForm(q, h)) {
            return PRESENT_PARTICIPLE;
        }
        if (isErForm(q, h)) {
            return COMPARATIVE;
        }
        if (isEstForm(q, h)) {
            return SUPERLATIVE;
        }
        return FORM_OF;
    }

    /** A line for the page: "emerged — past tense or past participle of emerge". */
    public static String formLine(String query, String headword, Set<String> pos) {
        String label = describe(query, headword, pos);
        if (label == null) {
            return null;
        }
        return query.trim() + " — " + label + " " + headword;
    }

    // ---- the regular rules, mirroring tools/wordnet_import.py regular_forms ----

    private static boolean endsY(String h) {
        return h.length() > 1 && h.endsWith("y") && !isVowel(h.charAt(h.length() - 2));
    }

    private static boolean isVowel(char c) {
        return "aeiou".indexOf(c) >= 0;
    }

    private static boolean doubles(String h) {
        int n = h.length();
        if (n < 3) {
            return false;
        }
        char c = h.charAt(n - 1);
        return !isVowel(c) && c != 'w' && c != 'x' && c != 'y'
                && isVowel(h.charAt(n - 2)) && !isVowel(h.charAt(n - 3));
    }

    private static boolean isSForm(String q, String h) {
        return q.equals(h + "s") || q.equals(h + "es")
                || (endsY(h) && q.equals(h.substring(0, h.length() - 1) + "ies"));
    }

    private static boolean isEdForm(String q, String h) {
        if (h.endsWith("e")) {
            return q.equals(h + "d");
        }
        if (endsY(h)) {
            return q.equals(h.substring(0, h.length() - 1) + "ied");
        }
        return q.equals(h + "ed") || (doubles(h) && q.equals(h + h.charAt(h.length() - 1) + "ed"));
    }

    private static boolean isIngForm(String q, String h) {
        if (h.endsWith("ie")) {
            return q.equals(h.substring(0, h.length() - 2) + "ying");
        }
        if (h.endsWith("e") && !h.endsWith("ee")) {
            return q.equals(h.substring(0, h.length() - 1) + "ing");
        }
        return q.equals(h + "ing") || (doubles(h) && q.equals(h + h.charAt(h.length() - 1) + "ing"));
    }

    private static boolean isErForm(String q, String h) {
        if (h.endsWith("e")) {
            return q.equals(h + "r");
        }
        if (endsY(h)) {
            return q.equals(h.substring(0, h.length() - 1) + "ier");
        }
        return q.equals(h + "er") || (doubles(h) && q.equals(h + h.charAt(h.length() - 1) + "er"));
    }

    private static boolean isEstForm(String q, String h) {
        if (h.endsWith("e")) {
            return q.equals(h + "st");
        }
        if (endsY(h)) {
            return q.equals(h.substring(0, h.length() - 1) + "iest");
        }
        return q.equals(h + "est") || (doubles(h) && q.equals(h + h.charAt(h.length() - 1) + "est"));
    }
}
