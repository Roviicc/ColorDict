package io.github.roviicc.colordict.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import io.github.roviicc.colordict.BuildConfig;
import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.Dictionary;

/** Theme, server address, and data controls. Built in code: it is a short list. */
public class SettingsActivity extends BaseActivity {

    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setContentDescription(getString(R.string.back));
        android.util.TypedValue bg = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, bg, true);
        back.setBackgroundResource(bg.resourceId);
        back.setOnClickListener(v -> finish());
        int s = Ui.dp(this, 48);
        bar.addView(back, new LinearLayout.LayoutParams(s, s));
        TextView title = Ui.text(this, 22, R.color.textPrimary);
        title.setTypeface(Ui.serifBold());
        title.setText(R.string.settings);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.setMarginStart(Ui.dp(this, 4));
        bar.addView(title, tp);
        root.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 56)));

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 32));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        Ui.onInsets(root, (l, t, r, b) -> root.setPadding(l, t, r, b));
        build();
    }

    private void build() {
        list.removeAllViews();
        Dictionary d = dictionary();

        section(R.string.settings_appearance);
        LinearLayout appearance = card();
        String[] themes = {getString(R.string.theme_system), getString(R.string.theme_light),
                getString(R.string.theme_dark)};
        row(appearance, getString(R.string.settings_theme), themes[d.theme()], v ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.settings_theme)
                        .setSingleChoiceItems(themes, d.theme(), (dialog, which) -> {
                            dialog.dismiss();
                            if (which != d.theme()) {
                                d.setTheme(which);
                                recreate();
                            }
                        })
                        .show());

        section(R.string.settings_server);
        LinearLayout server = card();
        String custom = d.customServerUrl();
        String summary = !custom.isEmpty() ? custom
                : BuildConfig.API_BASE_URL.isEmpty() ? getString(R.string.settings_server_none)
                : getString(R.string.settings_server_default, BuildConfig.API_BASE_URL);
        row(server, getString(R.string.settings_server_url), summary, v -> editServer());

        section(R.string.settings_data);
        LinearLayout data = card();
        row(data, getString(R.string.settings_clear_recent), null, v -> {
            d.recent().clear();
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show();
        });
        divider(data);
        row(data, getString(R.string.settings_clear_cache),
                getString(R.string.settings_clear_cache_summary), v -> {
                    int n = d.store().clear();
                    Toast.makeText(this, getString(R.string.settings_cache_cleared, n),
                            Toast.LENGTH_SHORT).show();
                });

        section(R.string.settings_about);
        TextView about = Ui.text(this, 14, R.color.textSecondary);
        about.setText(getString(R.string.settings_about_body, BuildConfig.VERSION_NAME));
        about.setLineSpacing(Ui.dp(this, 3), 1f);
        about.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        list.addView(about);
    }

    private void editServer() {
        Dictionary d = dictionary();
        EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        field.setSingleLine(true);
        field.setHint(R.string.settings_server_hint);
        field.setText(d.customServerUrl());
        FrameLayout wrap = new FrameLayout(this);
        int p = Ui.dp(this, 20);
        wrap.setPadding(p, Ui.dp(this, 8), p, 0);
        wrap.addView(field);
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_server_url)
                .setView(wrap)
                .setPositiveButton(R.string.done, (dialog, w) -> {
                    d.setCustomServerUrl(field.getText().toString());
                    build();
                })
                .setNeutralButton(R.string.settings_server_reset, (dialog, w) -> {
                    d.setCustomServerUrl("");
                    build();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void section(int title) {
        TextView label = Ui.label(this, getString(title));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 24);
        lp.bottomMargin = Ui.dp(this, 8);
        label.setPadding(Ui.dp(this, 4), 0, 0, 0);
        list.addView(label, lp);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.outlined(Ui.color(this, R.color.surface),
                Ui.color(this, R.color.outline), Ui.dp(this, 1), Ui.dp(this, 16)));
        card.setClipToOutline(true);
        list.addView(card);
        return card;
    }

    private void row(LinearLayout card, String title, String summary, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);
        row.setOnClickListener(click);
        TextView t = Ui.text(this, 16, R.color.textPrimary);
        t.setText(title);
        row.addView(t);
        if (summary != null) {
            TextView s = Ui.text(this, 14, R.color.textSecondary);
            s.setText(summary);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(this, 2);
            row.addView(s, lp);
        }
        card.addView(row);
    }

    private void divider(LinearLayout card) {
        View line = new View(this);
        line.setBackgroundColor(Ui.color(this, R.color.outline));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.setMarginStart(Ui.dp(this, 16));
        card.addView(line, lp);
    }
}
