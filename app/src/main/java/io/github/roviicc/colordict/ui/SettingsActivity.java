package io.github.roviicc.colordict.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
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

import java.util.Arrays;

import io.github.roviicc.colordict.BuildConfig;
import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.Settings;
import io.github.roviicc.colordict.data.connotation.ConnotationClient;
import io.github.roviicc.colordict.ui.widget.CardList;
import io.github.roviicc.colordict.ui.widget.Views;

/** Theme, the Claude API key and model, and data controls. Built in code: it is a short list. */
public class SettingsActivity extends BaseActivity {

    private static final String KEYS_URL = "https://platform.claude.com/settings/keys";

    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(toolbar(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Views.dp(this, 56)));

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Views.dp(this, 16), 0, Views.dp(this, 16), Views.dp(this, 32));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
        Views.onInsets(root, (l, t, r, b) -> root.setPadding(l, t, r, b));
        build();
    }

    private View toolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Views.dp(this, 4), 0, Views.dp(this, 4), 0);
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setContentDescription(getString(R.string.back));
        back.setBackgroundResource(Views.selectableBackground(this, true));
        back.setOnClickListener(v -> finish());
        int size = Views.dp(this, 48);
        bar.addView(back, new LinearLayout.LayoutParams(size, size));
        TextView title = Views.text(this, 22, R.color.textPrimary);
        title.setTypeface(Views.serifBold());
        title.setText(R.string.settings);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.setMarginStart(Views.dp(this, 4));
        bar.addView(title, tp);
        return bar;
    }

    private void build() {
        list.removeAllViews();
        Settings settings = settings();

        LinearLayout appearance = section(R.string.settings_appearance);
        String[] themes = getResources().getStringArray(R.array.themes);
        addRow(appearance, R.string.settings_theme, themes[settings.theme()], v ->
                choose(R.string.settings_theme, themes, settings.theme(), which -> {
                    if (which != settings.theme()) {
                        settings.setTheme(which);
                        recreate();
                    }
                }));

        LinearLayout claude = section(R.string.settings_claude);
        addRow(claude, R.string.settings_api_key,
                settings.hasApiKey() ? settings.maskedApiKey() : getString(R.string.settings_api_key_none),
                v -> editApiKey());
        String[] models = getResources().getStringArray(R.array.models);
        int model = Arrays.asList(ConnotationClient.MODELS).indexOf(settings.model());
        addRow(claude, R.string.settings_model, model >= 0 ? models[model] : settings.model(), v ->
                choose(R.string.settings_model, models, model, which -> {
                    settings.setModel(ConnotationClient.MODELS[which]);
                    build();
                }));
        addNote(R.string.settings_api_key_note, 13, R.color.textTertiary, 8);

        LinearLayout data = section(R.string.settings_data);
        addRow(data, R.string.settings_clear_recent, null, v -> {
            repository().recent().clear();
            Toast.makeText(this, R.string.done, Toast.LENGTH_SHORT).show();
        });
        addRow(data, R.string.settings_clear_cache, getString(R.string.settings_clear_cache_summary), v -> {
            int n = repository().clearConnotations();
            Toast.makeText(this, getString(R.string.settings_cache_cleared, n), Toast.LENGTH_SHORT).show();
        });

        list.addView(sectionLabel(R.string.settings_about));
        TextView about = addNote(0, 14, R.color.textSecondary, 0);
        about.setText(getString(R.string.settings_about_body, BuildConfig.VERSION_NAME));
    }

    private void editApiKey() {
        EditText field = new EditText(this);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setSingleLine(true);
        field.setHint(R.string.settings_api_key_hint);
        field.setText(settings().apiKey());
        field.setSelection(field.getText().length());
        FrameLayout wrap = new FrameLayout(this);
        int p = Views.dp(this, 20);
        wrap.setPadding(p, Views.dp(this, 8), p, 0);
        wrap.addView(field);
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_api_key)
                .setMessage(R.string.settings_api_key_help)
                .setView(wrap)
                .setPositiveButton(R.string.save_key, (dialog, w) -> {
                    String key = field.getText().toString().trim();
                    settings().setApiKey(key);
                    if (!key.isEmpty() && !key.startsWith("sk-ant-")) {
                        Toast.makeText(this, R.string.settings_api_key_odd, Toast.LENGTH_LONG).show();
                    }
                    build();
                })
                .setNeutralButton(R.string.settings_get_key,
                        (dialog, w) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(KEYS_URL))))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------------------------------------------------------------- building blocks

    private interface Choice {
        void onChosen(int which);
    }

    private void choose(int title, String[] options, int selected, Choice choice) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(options, selected, (dialog, which) -> {
                    dialog.dismiss();
                    choice.onChosen(which);
                })
                .show();
    }

    /** A labelled section; returns its card for rows. */
    private LinearLayout section(int title) {
        list.addView(sectionLabel(title));
        LinearLayout card = CardList.card(this);
        list.addView(card);
        return card;
    }

    private TextView sectionLabel(int title) {
        TextView label = Views.label(this, getString(title));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Views.dp(this, 24);
        lp.bottomMargin = Views.dp(this, 8);
        label.setLayoutParams(lp);
        label.setPadding(Views.dp(this, 4), 0, 0, 0);
        return label;
    }

    private void addRow(LinearLayout card, int title, String summary, View.OnClickListener click) {
        View row = CardList.row(this, getString(title), summary, 0, false, false);
        row.setOnClickListener(click);
        CardList.addRow(card, row);
    }

    /** Small text below a card (or on its own); text 0 leaves it empty for the caller. */
    private TextView addNote(int text, float sp, int color, int topDp) {
        TextView note = Views.text(this, sp, color);
        if (text != 0) {
            note.setText(text);
        }
        note.setLineSpacing(Views.dp(this, 2), 1f);
        note.setPadding(Views.dp(this, 4), Views.dp(this, topDp), Views.dp(this, 4), 0);
        list.addView(note);
        return note;
    }
}
