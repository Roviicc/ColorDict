package io.github.roviicc.colordict.ui.widget;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.roviicc.colordict.R;

/**
 * Grouped list in a rounded, outlined card: rows separated by inset
 * dividers. Used by Home (word lists) and Settings.
 */
public final class CardList {

    private CardList() {
    }

    /** An empty card; add rows with {@link #addRow}. */
    public static LinearLayout card(Context c) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Views.outlined(Views.color(c, R.color.surface),
                Views.color(c, R.color.outline), Views.dp(c, 1), Views.dp(c, 16)));
        card.setClipToOutline(true);
        return card;
    }

    /** Appends a row, with a divider above it when it isn't the first. */
    public static void addRow(LinearLayout card, View row) {
        if (card.getChildCount() > 0) {
            View line = new View(card.getContext());
            line.setBackgroundColor(Views.color(card.getContext(), R.color.outline));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
            lp.setMarginStart(Views.dp(card.getContext(), 16));
            card.addView(line, lp);
        }
        card.addView(row);
    }

    /**
     * A tappable row: optional leading icon, title, optional one-line
     * subtitle, and an optional trailing chevron.
     */
    public static View row(Context c, String title, String subtitle, int leadingIcon,
                           boolean chevron, boolean singleLineSubtitle) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Views.dp(c, 56));
        row.setPadding(Views.dp(c, 16), Views.dp(c, 12), Views.dp(c, chevron ? 10 : 16), Views.dp(c, 12));
        row.setBackgroundResource(Views.selectableBackground(c, false));

        if (leadingIcon != 0) {
            ImageView icon = new ImageView(c);
            icon.setImageResource(leadingIcon);
            int s = Views.dp(c, 20);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(s, s);
            ip.setMarginEnd(Views.dp(c, 12));
            row.addView(icon, ip);
        }

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView t = Views.text(c, 16.5f, R.color.textPrimary);
        t.setText(title);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        text.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = Views.text(c, 14, R.color.textSecondary);
            s.setText(subtitle);
            if (singleLineSubtitle) {
                s.setSingleLine(true);
                s.setEllipsize(TextUtils.TruncateAt.END);
            }
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            sp.topMargin = Views.dp(c, 2);
            text.addView(s, sp);
        }
        row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        if (chevron) {
            ImageView icon = new ImageView(c);
            icon.setImageResource(R.drawable.ic_chevron_right);
            icon.setAlpha(0.5f);
            int s = Views.dp(c, 20);
            row.addView(icon, new LinearLayout.LayoutParams(s, s));
        }
        return row;
    }
}
