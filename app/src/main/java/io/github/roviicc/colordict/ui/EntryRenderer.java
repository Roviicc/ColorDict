package io.github.roviicc.colordict.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.Entry;

/**
 * Builds the word view shown in the full entry page and in the popup.
 * Layout follows the dictionary references we studied: a large serif
 * headword, a tappable pronunciation pill, a colored connotation chip, then
 * numbered senses grouped by part of speech with quoted examples and
 * synonym chips that look the tapped word up.
 */
final class EntryRenderer {

    interface Listener {
        /** A synonym, suggestion or related word was tapped. */
        void onWordTapped(String word);

        /** The pronunciation pill was tapped. */
        void onSpeak(String word);

        /** "Check connotation" was tapped (only shown while the entry has none). */
        void onConnotationRequested();
    }

    private static final int COMPACT_SENSES = 3;
    private static final int CHIP_LIMIT = 6;

    private final Context c;
    private final Listener listener;
    /** Holds the connotation card or the button that fetches it. */
    private FrameLayout connotationSlot;

    EntryRenderer(Context context, Listener listener) {
        this.c = context;
        this.listener = listener;
    }

    // ---------------------------------------------------------------- states

    void renderEntry(LinearLayout into, Entry e, boolean compact) {
        into.removeAllViews();
        if (!e.hasSenses()) {
            renderNotFound(into, e.word, e.suggestions, compact);
            return;
        }
        into.addView(headword(e.word, compact));
        into.addView(pronunciationRow(e), gap(compact ? 8 : 12));

        connotationSlot = new FrameLayout(c);
        if (e.connotation != null) {
            connotationSlot.addView(connotation(e.connotation));
        } else {
            showConnotationButton();
        }
        into.addView(connotationSlot, gap(compact ? 16 : 24));

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
            into.addView(sense(e.word, s, ++number, compact), gap(10));
            shown++;
        }

        if (compact) {
            List<String> synonyms = e.senses.get(0).synonyms;
            if (!synonyms.isEmpty()) {
                into.addView(Ui.label(c, c.getString(R.string.label_similar)), gap(18));
                into.addView(chips(synonyms), gap(8));
            }
            return;
        }

        if (!e.usageNote.isEmpty()) {
            into.addView(note(c.getString(R.string.label_usage), e.usageNote), gap(28));
        }
        if (!e.etymology.isEmpty()) {
            into.addView(Ui.label(c, c.getString(R.string.label_origin)), gap(28));
            TextView origin = body(e.etymology, 16, R.color.textSecondary);
            into.addView(origin, gap(6));
        }
        if (!e.relatedForms.isEmpty()) {
            into.addView(Ui.label(c, c.getString(R.string.label_related)), gap(28));
            into.addView(chips(e.relatedForms), gap(8));
        }
        TextView footer = Ui.text(c, 12, R.color.textTertiary);
        footer.setText(R.string.generated_note);
        into.addView(footer, gap(36));
    }

    /** Skeleton shown while the server writes the entry. */
    void renderLoading(LinearLayout into, String word, boolean compact) {
        into.removeAllViews();
        into.addView(headword(word, compact));
        addBar(into, 110, 28, 999, 12);
        addBar(into, 72, 12, 6, compact ? 22 : 30);
        addBar(into, -1, 14, 6, 10);
        addBar(into, 240, 14, 6, 8);
        addBar(into, -1, 14, 6, compact ? 20 : 28);
        addBar(into, 200, 14, 6, 8);
        if (!compact) {
            addBar(into, -1, 14, 6, 28);
            addBar(into, 160, 14, 6, 8);
        }
        TextView hint = Ui.text(c, 13, R.color.textTertiary);
        hint.setText(R.string.loading_hint);
        into.addView(hint, gap(compact ? 18 : 28));
    }

    /** Error state; actionLabel/action add a button (e.g. "Try again", "Open Settings"). */
    void renderError(LinearLayout into, String word, String message, String actionLabel,
                     Runnable action) {
        into.removeAllViews();
        into.addView(headword(word, true));
        TextView msg = body(message, 16, R.color.textSecondary);
        into.addView(msg, gap(12));
        if (actionLabel != null && action != null) {
            TextView button = pillButton(actionLabel);
            button.setOnClickListener(v -> action.run());
            into.addView(button, wrap(gap(18)));
        }
    }

    // ------------------------------------------------------- connotation slot

    private void showConnotationButton() {
        connotationSlot.removeAllViews();
        LinearLayout button = new LinearLayout(c);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(c, 14);
        button.setPadding(pad, Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12));
        float r = Ui.dp(c, 14);
        button.setBackground(Ui.pressable(c, Ui.outlined(Ui.color(c, R.color.surface),
                Ui.color(c, R.color.outline), Ui.dp(c, 1), r), r));
        button.setContentDescription(c.getString(R.string.check_connotation));

        TextView glyph = Ui.text(c, 18, R.color.accent);
        glyph.setText("\u2726");
        glyph.setGravity(Gravity.CENTER);
        int g = Ui.dp(c, 36);
        glyph.setBackground(Ui.rounded(Ui.color(c, R.color.accentSoft), g / 2f));
        button.addView(glyph, new LinearLayout.LayoutParams(g, g));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(c, 15, R.color.textPrimary);
        title.setText(R.string.check_connotation);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        col.addView(title);
        TextView sub = Ui.text(c, 13, R.color.textSecondary);
        sub.setText(R.string.check_connotation_hint);
        col.addView(sub);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        cp.setMarginStart(Ui.dp(c, 12));
        button.addView(col, cp);

        ImageView chevron = new ImageView(c);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        int s = Ui.dp(c, 20);
        button.addView(chevron, new LinearLayout.LayoutParams(s, s));

        button.setOnClickListener(v -> {
            showConnotationLoading();
            listener.onConnotationRequested();
        });
        connotationSlot.addView(button);
    }

    private void showConnotationLoading() {
        connotationSlot.removeAllViews();
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 14);
        box.setPadding(pad, Ui.dp(c, 14), pad, Ui.dp(c, 14));
        box.setBackground(Ui.rounded(Ui.color(c, R.color.surfaceMuted), Ui.dp(c, 14)));
        TextView t = Ui.text(c, 12, R.color.textTertiary);
        t.setText(c.getString(R.string.checking_connotation).toUpperCase(Locale.ROOT));
        t.setLetterSpacing(0.08f);
        box.addView(t);
        addBar(box, -1, 12, 6, 10);
        addBar(box, 180, 12, 6, 8);
        connotationSlot.addView(box);
    }

    /** Called by the screen when the connotation request failed. */
    void showConnotationError(String message, String actionLabel, Runnable action) {
        if (connotationSlot == null) {
            return;
        }
        connotationSlot.removeAllViews();
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 14);
        box.setPadding(pad, Ui.dp(c, 12), pad, Ui.dp(c, 12));
        box.setBackground(Ui.outlined(Ui.color(c, R.color.surface),
                Ui.color(c, R.color.outline), Ui.dp(c, 1), Ui.dp(c, 14)));
        box.addView(body(message, 14, R.color.textSecondary));
        TextView link = Ui.text(c, 14, R.color.accent);
        link.setText(actionLabel != null ? actionLabel : c.getString(R.string.try_again));
        link.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        link.setPadding(0, Ui.dp(c, 8), 0, 0);
        link.setOnClickListener(v -> {
            if (action != null) {
                action.run();
            } else {
                showConnotationLoading();
                listener.onConnotationRequested();
            }
        });
        box.addView(link);
        connotationSlot.addView(box);
    }

    private void renderNotFound(LinearLayout into, String word, List<String> suggestions,
                                boolean compact) {
        into.addView(headword(word, compact));
        TextView msg = body(c.getString(R.string.not_found), 16, R.color.textSecondary);
        into.addView(msg, gap(10));
        if (!suggestions.isEmpty()) {
            into.addView(Ui.label(c, c.getString(R.string.label_did_you_mean)), gap(22));
            into.addView(chips(suggestions), gap(8));
        }
    }

    // ---------------------------------------------------------------- pieces

    private TextView headword(String word, boolean compact) {
        TextView t = Ui.text(c, compact ? 28 : 38, R.color.textPrimary);
        t.setTypeface(Ui.serifBold());
        t.setText(word);
        t.setLineSpacing(0, 1.05f);
        t.setOnLongClickListener(v -> {
            copy(word);
            return true;
        });
        return t;
    }

    private View pronunciationRow(Entry e) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout pill = new LinearLayout(c);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        int h = Ui.dp(c, 12);
        pill.setPadding(h, Ui.dp(c, 5), Ui.dp(c, 10), Ui.dp(c, 5));
        float r = Ui.dp(c, 999);
        pill.setBackground(Ui.pressable(c,
                Ui.rounded(Ui.color(c, R.color.surfaceMuted), r), r));
        pill.setContentDescription(c.getString(R.string.pronounce, e.word));
        pill.setOnClickListener(v -> listener.onSpeak(e.word));

        if (!e.phonetic.isEmpty()) {
            TextView ipa = Ui.text(c, 15, R.color.textPrimary);
            ipa.setText(e.phonetic);
            pill.addView(ipa);
        }
        ImageView speaker = new ImageView(c);
        speaker.setImageResource(R.drawable.ic_speaker);
        speaker.setImageTintList(android.content.res.ColorStateList.valueOf(
                Ui.color(c, R.color.accent)));
        int s = Ui.dp(c, 18);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(s, s);
        sp.setMarginStart(e.phonetic.isEmpty() ? 0 : Ui.dp(c, 8));
        pill.addView(speaker, sp);
        row.addView(pill);

        if (!e.syllables.isEmpty()) {
            TextView syl = Ui.text(c, 14, R.color.textTertiary);
            syl.setText(e.syllables);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(Ui.dp(c, 12));
            row.addView(syl, lp);
        }
        return row;
    }

    private View connotation(Entry.Connotation con) {
        int fg;
        int bg;
        int label;
        switch (con.tone) {
            case POSITIVE:
                fg = R.color.tonePositive;
                bg = R.color.tonePositiveBg;
                label = R.string.tone_positive;
                break;
            case NEGATIVE:
                fg = R.color.toneNegative;
                bg = R.color.toneNegativeBg;
                label = R.string.tone_negative;
                break;
            case MIXED:
                fg = R.color.toneMixed;
                bg = R.color.toneMixedBg;
                label = R.string.tone_mixed;
                break;
            default:
                fg = R.color.toneNeutral;
                bg = R.color.toneNeutralBg;
                label = R.string.tone_neutral;
        }
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 14);
        box.setPadding(pad, Ui.dp(c, 12), pad, Ui.dp(c, 14));
        box.setBackground(Ui.rounded(Ui.color(c, bg), Ui.dp(c, 14)));

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(c);
        dot.setBackground(Ui.rounded(Ui.color(c, fg), Ui.dp(c, 4)));
        int d = Ui.dp(c, 8);
        head.addView(dot, new LinearLayout.LayoutParams(d, d));
        TextView title = Ui.text(c, 12, fg);
        title.setText(c.getString(R.string.connotation_title, c.getString(label))
                .toUpperCase(Locale.ROOT));
        title.setLetterSpacing(0.08f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.setMarginStart(Ui.dp(c, 8));
        head.addView(title, tp);
        box.addView(head);

        box.addView(body(con.summary, 15, R.color.textPrimary), gap(6));

        if (!con.feelsLike.isEmpty()) {
            FlowLayout tags = new FlowLayout(c, Ui.dp(c, 6), Ui.dp(c, 6));
            for (String f : con.feelsLike.subList(0, Math.min(4, con.feelsLike.size()))) {
                TextView tag = Ui.text(c, 12, fg);
                tag.setText(f);
                tag.setPadding(Ui.dp(c, 10), Ui.dp(c, 3), Ui.dp(c, 10), Ui.dp(c, 4));
                tag.setBackground(Ui.outlined(0, (Ui.color(c, fg) & 0x00FFFFFF) | 0x55000000,
                        Ui.dp(c, 1), Ui.dp(c, 999)));
                tags.addView(tag);
            }
            box.addView(tags, gap(10));
        }
        if (!con.caution.isEmpty()) {
            TextView caution = body(c.getString(R.string.connotation_caution, con.caution),
                    13, R.color.textSecondary);
            box.addView(caution, gap(10));
        }
        return box;
    }

    private View partOfSpeech(String pos, boolean divider) {
        LinearLayout wrap = new LinearLayout(c);
        wrap.setOrientation(LinearLayout.VERTICAL);
        if (divider) {
            View line = new View(c);
            line.setBackgroundColor(Ui.color(c, R.color.outline));
            wrap.addView(line, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 1) / 2 + 1)));
        }
        TextView t = Ui.text(c, 18, R.color.accent);
        t.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
        t.setText(pos.isEmpty() ? c.getString(R.string.pos_unknown) : pos);
        wrap.addView(t, gap(divider ? 18 : 0));
        return wrap;
    }

    private View sense(String word, Entry.Sense s, int number, boolean compact) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView num = Ui.text(c, 16, R.color.textTertiary);
        num.setText(String.format(Locale.ROOT, "%d", number));
        num.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        row.addView(num, new LinearLayout.LayoutParams(Ui.dp(c, 22),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        row.addView(col, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        if (!s.register.isEmpty()) {
            TextView reg = Ui.text(c, 11, R.color.textSecondary);
            reg.setText(s.register);
            reg.setPadding(Ui.dp(c, 8), Ui.dp(c, 1), Ui.dp(c, 8), Ui.dp(c, 2));
            reg.setBackground(Ui.outlined(0, Ui.color(c, R.color.outline), Ui.dp(c, 1),
                    Ui.dp(c, 999)));
            col.addView(reg, wrap(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)));
            ((LinearLayout.LayoutParams) reg.getLayoutParams()).bottomMargin = Ui.dp(c, 4);
        }

        TextView def = body(s.definition, compact ? 16 : 17, R.color.textPrimary);
        col.addView(def);

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
        bar.setBackground(Ui.rounded(Ui.color(c, R.color.outline), Ui.dp(c, 2)));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.dp(c, 3),
                LinearLayout.LayoutParams.MATCH_PARENT);
        row.addView(bar, bp);
        TextView t = Ui.text(c, 15, R.color.textSecondary);
        t.setText(emphasize(sentence, word));
        t.setLineSpacing(Ui.dp(c, 2), 1f);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tp.setMarginStart(Ui.dp(c, 10));
        row.addView(t, tp);
        return row;
    }

    /** Bolds words in the sentence that start with the headword ("run" → "running"). */
    CharSequence emphasize(String sentence, String word) {
        SpannableString span = new SpannableString(sentence);
        if (word.isEmpty()) {
            return span;
        }
        String stem = word.length() > 4 && word.endsWith("e")
                ? word.substring(0, word.length() - 1) : word;
        Matcher m = Pattern.compile("(?<![\\p{L}])" + Pattern.quote(stem) + "[\\p{L}]*",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(sentence);
        int fg = Ui.color(c, R.color.textPrimary);
        while (m.find()) {
            span.setSpan(new StyleSpan(Typeface.BOLD), m.start(), m.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            span.setSpan(new ForegroundColorSpan(fg), m.start(), m.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return span;
    }

    private View chipRow(String label, List<String> words) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(Ui.label(c, label));
        col.addView(chips(words), gap(6));
        return col;
    }

    private View chips(List<String> words) {
        FlowLayout flow = new FlowLayout(c, Ui.dp(c, 8), Ui.dp(c, 8));
        List<String> shown = new ArrayList<>(words.subList(0, Math.min(words.size(), CHIP_LIMIT)));
        float r = Ui.dp(c, 999);
        for (String w : shown) {
            TextView chip = Ui.text(c, 14, R.color.accent);
            chip.setText(w);
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 12), Ui.dp(c, 7));
            chip.setBackground(Ui.pressable(c, Ui.rounded(Ui.color(c, R.color.accentSoft), r), r));
            chip.setOnClickListener(v -> listener.onWordTapped(w));
            flow.addView(chip);
        }
        return flow;
    }

    private View note(String label, String text) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 14);
        box.setPadding(pad, pad, pad, pad);
        box.setBackground(Ui.rounded(Ui.color(c, R.color.surfaceMuted), Ui.dp(c, 14)));
        box.addView(Ui.label(c, label));
        box.addView(body(text, 15, R.color.textPrimary), gap(6));
        return box;
    }

    private TextView pillButton(String text) {
        TextView b = Ui.text(c, 15, R.color.bg);
        b.setText(text);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setPadding(Ui.dp(c, 20), Ui.dp(c, 10), Ui.dp(c, 20), Ui.dp(c, 11));
        float r = Ui.dp(c, 999);
        b.setBackground(Ui.pressable(c, Ui.rounded(Ui.color(c, R.color.textPrimary), r), r));
        return b;
    }

    /** Adds a pulsing placeholder bar; widthDp < 0 means full width. */
    private void addBar(LinearLayout into, int widthDp, int heightDp, int radiusDp, int topDp) {
        View v = new View(c);
        v.setBackground(Ui.rounded(Ui.color(c, R.color.skeleton), Ui.dp(c, radiusDp)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                widthDp < 0 ? LinearLayout.LayoutParams.MATCH_PARENT : Ui.dp(c, widthDp),
                Ui.dp(c, heightDp));
        lp.topMargin = Ui.dp(c, topDp);
        ObjectAnimator pulse = ObjectAnimator.ofFloat(v, View.ALPHA, 1f, 0.45f);
        pulse.setDuration(750);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                pulse.start();
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                pulse.cancel();
            }
        });
        into.addView(v, lp);
    }

    private TextView body(String text, float sp, int colorRes) {
        TextView t = Ui.text(c, sp, colorRes);
        t.setText(text);
        t.setLineSpacing(Ui.dp(c, 3), 1f);
        return t;
    }

    void copy(String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(text, text));
            Toast.makeText(c, R.string.copied, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- params

    /** Full-width params with a top margin, or the view's own params when it has some. */
    private LinearLayout.LayoutParams gap(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, topDp);
        return lp;
    }

    private static LinearLayout.LayoutParams wrap(LinearLayout.LayoutParams lp) {
        lp.width = LinearLayout.LayoutParams.WRAP_CONTENT;
        return lp;
    }
}
