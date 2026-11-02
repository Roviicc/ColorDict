package io.github.roviicc.colordict.ui.entry;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.Entry;
import io.github.roviicc.colordict.ui.widget.FlowLayout;
import io.github.roviicc.colordict.ui.widget.Views;

/**
 * Lays out the word view shown in the full entry page and (compact) in the
 * popup: a large serif headword, a tappable pronunciation pill, the
 * connotation area, then numbered senses grouped by part of speech with
 * quoted examples and synonym chips that look the tapped word up.
 */
public final class EntryRenderer {

    public interface Listener {
        /** A synonym, suggestion or related word was tapped. */
        void onWordTapped(String word);

        /** The pronunciation pill was tapped. */
        void onSpeak(String word);

        /** "Check connotation" was tapped (only shown while the entry has none). */
        void onConnotationRequested();

        /** "Look up with Claude" was tapped on a word the dictionary doesn't have. */
        void onLookUpWithClaude();

        /** The "See full entry" link at the end was tapped (only shown when enabled). */
        void onOpenFullEntry();
    }

    private static final int COMPACT_SENSES = 3;
    private static final int CHIP_LIMIT = 6;

    private final Context c;
    private final Listener listener;
    private final boolean compact;
    private boolean fullEntryLink;
    /** The slot waiting on Claude (connotation or look-up), for showing its errors. */
    private ClaudeSlot activeSlot;

    /** compact: the popup's shorter layout (top senses, one example each). */
    public EntryRenderer(Context context, Listener listener, boolean compact) {
        this.c = context;
        this.listener = listener;
        this.compact = compact;
    }


    /** Whether to end entries with a "See full entry" link. */
    public void setFullEntryLink(boolean show) {
        this.fullEntryLink = show;
    }

    // ---------------------------------------------------------------- states

    public void renderEntry(LinearLayout into, Entry e) {
        into.removeAllViews();
        activeSlot = null;
        if (!e.hasSenses()) {
            renderNotFound(into, e);
            return;
        }
        into.addView(headword(e.word));
        into.addView(pronunciation(e), gap(compact ? 8 : 12));
        if (e.fromClaude) {
            TextView source = Views.text(c, 12, R.color.textTertiary);
            source.setText(R.string.meaning_by_claude);
            into.addView(source, gap(8));
        }

        ClaudeSlot connotation = ClaudeSlot.forConnotation(c, listener::onConnotationRequested);
        if (e.connotation != null) {
            connotation.showCard(e.connotation);
        } else {
            connotation.showButton();
            activeSlot = connotation;
        }
        into.addView(connotation.view(), gap(compact ? 16 : 24));

        addSenses(into, e);

        if (compact) {
            List<String> synonyms = e.senses.get(0).synonyms;
            if (!synonyms.isEmpty()) {
                into.addView(Views.label(c, c.getString(R.string.label_similar)), gap(18));
                into.addView(chips(synonyms), gap(8));
            }
            addFullEntryLink(into);
            return;
        }
        if (!e.usageNote.isEmpty()) {
            into.addView(note(c.getString(R.string.label_usage), e.usageNote), gap(28));
        }
        if (!e.etymology.isEmpty()) {
            into.addView(Views.label(c, c.getString(R.string.label_origin)), gap(28));
            into.addView(body(c, e.etymology, 16, R.color.textSecondary), gap(6));
        }
        if (!e.relatedForms.isEmpty()) {
            into.addView(Views.label(c, c.getString(R.string.label_related)), gap(28));
            into.addView(chips(e.relatedForms), gap(8));
        }
        addFullEntryLink(into);
        TextView footer = Views.text(c, 12, R.color.textTertiary);
        footer.setText(e.fromClaude ? R.string.claude_entry_note : R.string.generated_note);
        into.addView(footer, gap(fullEntryLink ? 20 : 36));
    }

    /** "See full entry ›" at the end of the meanings, when enabled. */
    private void addFullEntryLink(LinearLayout into) {
        if (!fullEntryLink) {
            return;
        }
        LinearLayout link = new LinearLayout(c);
        link.setOrientation(LinearLayout.HORIZONTAL);
        link.setGravity(Gravity.CENTER_VERTICAL);
        link.setPadding(0, Views.dp(c, 12), 0, Views.dp(c, 12));
        link.setBackgroundResource(Views.selectableBackground(c, false));
        TextView text = Views.text(c, 15, R.color.accent);
        text.setText(R.string.open_full_entry);
        text.setTypeface(Views.medium());
        link.addView(text);
        ImageView chevron = new ImageView(c);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setImageTintList(android.content.res.ColorStateList.valueOf(Views.color(c, R.color.accent)));
        int s = Views.dp(c, 18);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(s, s);
        cp.setMarginStart(Views.dp(c, 2));
        link.addView(chevron, cp);
        link.setOnClickListener(v -> listener.onOpenFullEntry());

        View line = new View(c);
        line.setBackgroundColor(Views.color(c, R.color.outline));
        into.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        ((LinearLayout.LayoutParams) line.getLayoutParams()).topMargin = Views.dp(c, 24);
        into.addView(link, gap(4));
    }

    /** Skeleton shown while the dictionary is unpacked on first launch. */
    public void renderLoading(LinearLayout into, String word) {
        into.removeAllViews();
        activeSlot = null;
        into.addView(headword(word));
        Skeleton.addBar(into, 110, 28, 999, 12);
        Skeleton.addBar(into, 72, 12, 6, compact ? 22 : 30);
        Skeleton.addBar(into, -1, 14, 6, 10);
        Skeleton.addBar(into, 240, 14, 6, 8);
        Skeleton.addBar(into, -1, 14, 6, compact ? 20 : 28);
        Skeleton.addBar(into, 200, 14, 6, 8);
        if (!compact) {
            Skeleton.addBar(into, -1, 14, 6, 28);
            Skeleton.addBar(into, 160, 14, 6, 8);
        }
        TextView hint = Views.text(c, 13, R.color.textTertiary);
        hint.setText(R.string.loading_hint);
        into.addView(hint, gap(compact ? 18 : 28));
    }

    /** Error state; with an action label, a button runs the action. */
    public void renderError(LinearLayout into, String word, String message, String actionLabel,
                            Runnable action) {
        into.removeAllViews();
        activeSlot = null;
        into.addView(headword(word));
        into.addView(body(c, message, 16, R.color.textSecondary), gap(12));
        if (actionLabel != null && action != null) {
            TextView button = pillButton(actionLabel);
            button.setOnClickListener(v -> action.run());
            LinearLayout.LayoutParams lp = gap(18);
            lp.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            into.addView(button, lp);
        }
    }

    /** Shows a failed Claude request (connotation or look-up) in place of its loading state. */
    public void showClaudeError(String message, String actionLabel, Runnable action) {
        if (activeSlot != null) {
            activeSlot.showError(message, actionLabel, action);
        }
    }

    /** Not in the built-in dictionary: offer Claude, then spelling suggestions. */
    private void renderNotFound(LinearLayout into, Entry e) {
        into.addView(headword(e.word));
        into.addView(body(c, c.getString(R.string.not_found), 16, R.color.textSecondary), gap(10));
        activeSlot = ClaudeSlot.forLookUp(c, listener::onLookUpWithClaude);
        activeSlot.showButton();
        into.addView(activeSlot.view(), gap(16));
        if (!e.suggestions.isEmpty()) {
            into.addView(Views.label(c, c.getString(R.string.label_did_you_mean)), gap(22));
            into.addView(chips(e.suggestions), gap(8));
        }
    }

    // ---------------------------------------------------------------- pieces

    private void addSenses(LinearLayout into, Entry e) {
        String lastPos = null;
        int number = 0;
        int shown = 0;
        for (Entry.Sense s : e.senses) {
            if (compact && shown == COMPACT_SENSES) {
                break;
            }
            if (!s.partOfSpeech.equals(lastPos)) {
                into.addView(partOfSpeech(s.partOfSpeech, lastPos != null), gap(compact ? 16 : 28));
                lastPos = s.partOfSpeech;
                number = 0;
            }
            into.addView(sense(e.word, s, ++number), gap(10));
            shown++;
        }
    }

    private TextView headword(String word) {
        TextView t = Views.text(c, compact ? 28 : 38, R.color.textPrimary);
        t.setTypeface(Views.serifBold());
        t.setText(word);
        t.setLineSpacing(0, 1.05f);
        t.setOnLongClickListener(v -> {
            Views.copyToClipboard(c, word);
            return true;
        });
        return t;
    }

    private View pronunciation(Entry e) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout pill = new LinearLayout(c);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(Views.dp(c, 12), Views.dp(c, 5), Views.dp(c, 10), Views.dp(c, 5));
        float r = Views.dp(c, 999);
        pill.setBackground(Views.pressable(c, Views.rounded(Views.color(c, R.color.surfaceMuted), r), r));
        pill.setContentDescription(c.getString(R.string.pronounce, e.word));
        pill.setOnClickListener(v -> listener.onSpeak(e.word));

        if (!e.phonetic.isEmpty()) {
            TextView ipa = Views.text(c, 15, R.color.textPrimary);
            ipa.setText(e.phonetic);
            pill.addView(ipa);
        }
        ImageView speaker = new ImageView(c);
        speaker.setImageResource(R.drawable.ic_speaker);
        speaker.setImageTintList(android.content.res.ColorStateList.valueOf(Views.color(c, R.color.accent)));
        int s = Views.dp(c, 18);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(s, s);
        sp.setMarginStart(e.phonetic.isEmpty() ? 0 : Views.dp(c, 8));
        pill.addView(speaker, sp);
        row.addView(pill);

        if (!e.syllables.isEmpty()) {
            TextView syllables = Views.text(c, 14, R.color.textTertiary);
            syllables.setText(e.syllables);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(Views.dp(c, 12));
            row.addView(syllables, lp);
        }
        return row;
    }

    private View partOfSpeech(String pos, boolean divider) {
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        if (divider) {
            View line = new View(c);
            line.setBackgroundColor(Views.color(c, R.color.outline));
            wrap.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        }
        TextView t = Views.text(c, 18, R.color.accent);
        t.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
        t.setText(pos.isEmpty() ? c.getString(R.string.pos_unknown) : pos);
        wrap.addView(t, gap(divider ? 18 : 0));
        return wrap;
    }

    private View sense(String word, Entry.Sense s, int number) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView num = Views.text(c, 16, R.color.textTertiary);
        num.setText(String.format(Locale.ROOT, "%d", number));
        num.setTypeface(Views.medium());
        row.addView(num, new LinearLayout.LayoutParams(Views.dp(c, 22), LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        if (!s.register.isEmpty()) {
            TextView reg = Views.text(c, 11, R.color.textSecondary);
            reg.setText(s.register);
            reg.setPadding(Views.dp(c, 8), Views.dp(c, 1), Views.dp(c, 8), Views.dp(c, 2));
            reg.setBackground(Views.outlined(0, Views.color(c, R.color.outline), Views.dp(c, 1), Views.dp(c, 999)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Views.dp(c, 4);
            col.addView(reg, lp);
        }

        col.addView(body(c, s.definition, compact ? 16 : 17, R.color.textPrimary));

        int examples = compact ? Math.min(1, s.examples.size()) : s.examples.size();
        for (int i = 0; i < examples; i++) {
            col.addView(example(word, s.examples.get(i)), gap(8));
        }
        if (!compact) {
            if (!s.synonyms.isEmpty()) {
                col.addView(chipRow(c.getString(R.string.label_similar), s.synonyms), gap(12));
            }
            if (!s.antonyms.isEmpty()) {
                col.addView(chipRow(c.getString(R.string.label_opposite), s.antonyms), gap(8));
            }
        }
        return row;
    }

    /** Quote-style example sentence with the headword in bold. */
    private View example(String word, String sentence) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        View bar = new View(c);
        bar.setBackground(Views.rounded(Views.color(c, R.color.outline), Views.dp(c, 2)));
        row.addView(bar, new LinearLayout.LayoutParams(Views.dp(c, 3), LinearLayout.LayoutParams.MATCH_PARENT));
        TextView t = Views.text(c, 15, R.color.textSecondary);
        t.setText(emphasize(sentence, word, Views.color(c, R.color.textPrimary)));
        t.setLineSpacing(Views.dp(c, 2), 1f);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tp.setMarginStart(Views.dp(c, 10));
        row.addView(t, tp);
        return row;
    }

    /** Bolds words in the sentence that start with the headword ("run" → "running"). */
    static CharSequence emphasize(String sentence, String word, int color) {
        SpannableString span = new SpannableString(sentence);
        if (word.isEmpty()) {
            return span;
        }
        String stem = word.length() > 4 && word.endsWith("e") ? word.substring(0, word.length() - 1) : word;
        Matcher m = Pattern.compile("(?<![\\p{L}])" + Pattern.quote(stem) + "[\\p{L}]*",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(sentence);
        while (m.find()) {
            span.setSpan(new StyleSpan(Typeface.BOLD), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new ForegroundColorSpan(color), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return span;
    }

    private View chipRow(String label, List<String> words) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Views.label(c, label));
        col.addView(chips(words), gap(6));
        return col;
    }

    /** Tappable word chips; tapping one looks it up. */
    private View chips(List<String> words) {
        FlowLayout flow = new FlowLayout(c, Views.dp(c, 8), Views.dp(c, 8));
        float r = Views.dp(c, 999);
        for (String w : words.subList(0, Math.min(words.size(), CHIP_LIMIT))) {
            TextView chip = Views.text(c, 14, R.color.accent);
            chip.setText(w);
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setPadding(Views.dp(c, 12), Views.dp(c, 6), Views.dp(c, 12), Views.dp(c, 7));
            chip.setBackground(Views.pressable(c, Views.rounded(Views.color(c, R.color.accentSoft), r), r));
            chip.setOnClickListener(v -> listener.onWordTapped(w));
            flow.addView(chip);
        }
        return flow;
    }

    private View note(String label, String text) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Views.dp(c, 14);
        box.setPadding(pad, pad, pad, pad);
        box.setBackground(Views.rounded(Views.color(c, R.color.surfaceMuted), Views.dp(c, 14)));
        box.addView(Views.label(c, label));
        box.addView(body(c, text, 15, R.color.textPrimary), gap(6));
        return box;
    }

    private TextView pillButton(String text) {
        TextView b = Views.text(c, 15, R.color.bg);
        b.setText(text);
        b.setTypeface(Views.medium());
        b.setPadding(Views.dp(c, 20), Views.dp(c, 10), Views.dp(c, 20), Views.dp(c, 11));
        float r = Views.dp(c, 999);
        b.setBackground(Views.pressable(c, Views.rounded(Views.color(c, R.color.textPrimary), r), r));
        return b;
    }

    /** Body text with comfortable line spacing. */
    static TextView body(Context c, String text, float sp, int colorRes) {
        TextView t = Views.text(c, sp, colorRes);
        t.setText(text);
        t.setLineSpacing(Views.dp(c, 3), 1f);
        return t;
    }

    /** Full-width layout params with a top margin. */
    private LinearLayout.LayoutParams gap(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Views.dp(c, topDp);
        return lp;
    }
}
