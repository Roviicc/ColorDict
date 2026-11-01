package io.github.roviicc.colordict.ui.entry;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.Entry;
import io.github.roviicc.colordict.ui.widget.FlowLayout;
import io.github.roviicc.colordict.ui.widget.Views;

/**
 * The connotation area of an entry. Shows the saved connotation card, or the
 * "Check connotation" button, which switches to a loading state when tapped
 * and to an inline error if the request fails.
 */
final class ConnotationSlot {

    private final Context c;
    private final FrameLayout slot;
    private final Runnable onRequested;

    ConnotationSlot(Context context, Runnable onRequested) {
        this.c = context;
        this.slot = new FrameLayout(context);
        this.onRequested = onRequested;
    }

    View view() {
        return slot;
    }

    void showCard(Entry.Connotation con) {
        replaceWith(card(con));
    }

    void showButton() {
        LinearLayout button = new LinearLayout(c);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(Views.dp(c, 14), Views.dp(c, 12), Views.dp(c, 10), Views.dp(c, 12));
        float r = Views.dp(c, 14);
        button.setBackground(Views.pressable(c, Views.outlined(Views.color(c, R.color.surface),
                Views.color(c, R.color.outline), Views.dp(c, 1), r), r));
        button.setContentDescription(c.getString(R.string.check_connotation));

        TextView glyph = Views.text(c, 18, R.color.accent);
        glyph.setText("✦");
        glyph.setGravity(Gravity.CENTER);
        int g = Views.dp(c, 36);
        glyph.setBackground(Views.rounded(Views.color(c, R.color.accentSoft), g / 2f));
        button.addView(glyph, new LinearLayout.LayoutParams(g, g));

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView title = Views.text(c, 15, R.color.textPrimary);
        title.setText(R.string.check_connotation);
        title.setTypeface(Views.medium());
        text.addView(title);
        TextView hint = Views.text(c, 13, R.color.textSecondary);
        hint.setText(R.string.check_connotation_hint);
        text.addView(hint);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tp.setMarginStart(Views.dp(c, 12));
        button.addView(text, tp);

        ImageView chevron = new ImageView(c);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        int s = Views.dp(c, 20);
        button.addView(chevron, new LinearLayout.LayoutParams(s, s));

        button.setOnClickListener(v -> request());
        replaceWith(button);
    }

    /**
     * Shows the request failure. With an action (e.g. "Open Settings") the
     * link runs it; without one the link retries the request.
     */
    void showError(String message, String actionLabel, Runnable action) {
        LinearLayout box = box(R.color.surface, true);
        box.addView(EntryRenderer.body(c, message, 14, R.color.textSecondary));
        TextView link = Views.text(c, 14, R.color.accent);
        link.setText(actionLabel != null ? actionLabel : c.getString(R.string.try_again));
        link.setTypeface(Views.medium());
        link.setPadding(0, Views.dp(c, 8), 0, 0);
        link.setOnClickListener(v -> {
            if (action != null) {
                action.run();
            } else {
                request();
            }
        });
        box.addView(link);
        replaceWith(box);
    }

    private void request() {
        LinearLayout box = box(R.color.surfaceMuted, false);
        TextView label = Views.text(c, 12, R.color.textTertiary);
        label.setText(c.getString(R.string.checking_connotation).toUpperCase(Locale.ROOT));
        label.setLetterSpacing(0.08f);
        box.addView(label);
        Skeleton.addBar(box, -1, 12, 6, 10);
        Skeleton.addBar(box, 180, 12, 6, 8);
        replaceWith(box);
        onRequested.run();
    }

    private View card(Entry.Connotation con) {
        ToneStyle tone = ToneStyle.of(con.tone);
        int fg = Views.color(c, tone.foreground);
        LinearLayout box = box(tone.background, false);

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(c);
        dot.setBackground(Views.rounded(fg, Views.dp(c, 4)));
        int d = Views.dp(c, 8);
        head.addView(dot, new LinearLayout.LayoutParams(d, d));
        TextView title = Views.text(c, 12, tone.foreground);
        title.setText(c.getString(R.string.connotation_title, c.getString(tone.label))
                .toUpperCase(Locale.ROOT));
        title.setLetterSpacing(0.08f);
        title.setTypeface(Views.medium());
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.setMarginStart(Views.dp(c, 8));
        head.addView(title, tp);
        box.addView(head);

        box.addView(EntryRenderer.body(c, con.summary, 15, R.color.textPrimary), topGap(6));

        if (!con.feelsLike.isEmpty()) {
            FlowLayout tags = new FlowLayout(c, Views.dp(c, 6), Views.dp(c, 6));
            for (String f : con.feelsLike.subList(0, Math.min(4, con.feelsLike.size()))) {
                TextView tag = Views.text(c, 12, tone.foreground);
                tag.setText(f);
                tag.setPadding(Views.dp(c, 10), Views.dp(c, 3), Views.dp(c, 10), Views.dp(c, 4));
                tag.setBackground(Views.outlined(0, (fg & 0x00FFFFFF) | 0x55000000,
                        Views.dp(c, 1), Views.dp(c, 999)));
                tags.addView(tag);
            }
            box.addView(tags, topGap(10));
        }
        if (!con.caution.isEmpty()) {
            box.addView(EntryRenderer.body(c, c.getString(R.string.connotation_caution, con.caution),
                    13, R.color.textSecondary), topGap(10));
        }
        return box;
    }

    /** A rounded container: filled, or white with an outline. */
    private LinearLayout box(int fillColor, boolean outlined) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Views.dp(c, 14);
        box.setPadding(pad, Views.dp(c, 12), pad, Views.dp(c, 14));
        float r = Views.dp(c, 14);
        box.setBackground(outlined
                ? Views.outlined(Views.color(c, fillColor), Views.color(c, R.color.outline), Views.dp(c, 1), r)
                : Views.rounded(Views.color(c, fillColor), r));
        return box;
    }

    private void replaceWith(View v) {
        slot.removeAllViews();
        slot.addView(v);
    }

    private LinearLayout.LayoutParams topGap(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Views.dp(c, dp);
        return lp;
    }
}
