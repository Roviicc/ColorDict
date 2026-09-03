package io.github.roviicc.colordict.ui;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.Prefs;

/** App settings, kept deliberately small: theme, text size, list limits. */
public class SettingsActivity extends BaseActivity {

    private static final String REPO_URL = "https://github.com/roviicc/colordict";

    private static final int[] ZOOM_VALUES = {85, 100, 115, 130, 150};
    private static final int[] SUGGESTION_VALUES = {20, 50, 100, 200};
    private static final int[] HISTORY_VALUES = {50, 200, 500, 1000};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle(R.string.settings);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        findViewById(R.id.rowTheme).setOnClickListener(v -> pickTheme());
        findViewById(R.id.rowTextSize).setOnClickListener(v -> pickFromValues(
                R.string.pref_text_size, ZOOM_VALUES, Prefs.textZoom(this),
                value -> Prefs.setTextZoom(this, value)));
        findViewById(R.id.rowSuggestions).setOnClickListener(v -> pickFromValues(
                R.string.pref_max_suggestions, SUGGESTION_VALUES, Prefs.maxSuggestions(this),
                value -> Prefs.setMaxSuggestions(this, value)));
        findViewById(R.id.rowHistoryLimit).setOnClickListener(v -> pickFromValues(
                R.string.pref_history_limit, HISTORY_VALUES, Prefs.historyLimit(this),
                value -> Prefs.setHistoryLimit(this, value)));
        findViewById(R.id.rowClearHistory).setOnClickListener(v -> confirmClear(
                R.string.clear_history_confirm, () -> repo().history().clearHistory()));
        findViewById(R.id.rowClearBookmarks).setOnClickListener(v -> confirmClear(
                R.string.clear_bookmarks_confirm, () -> repo().history().clearBookmarks()));
        findViewById(R.id.rowReports).setOnClickListener(
                v -> startActivity(new android.content.Intent(this, ReportsActivity.class)));
        findViewById(R.id.rowAbout).setOnClickListener(v -> showAbout());

        refreshSummaries();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The reported-words count changes on the Reports screen, so re-read it
        // on the way back rather than showing the number we had on entry.
        refreshSummaries();
    }

    private void refreshSummaries() {
        String[] themes = getResources().getStringArray(R.array.theme_names);
        ((TextView) findViewById(R.id.summaryTheme)).setText(themes[Prefs.themeMode(this)]);
        ((TextView) findViewById(R.id.summaryTextSize)).setText(
                getString(R.string.percent_value, Prefs.textZoom(this)));
        ((TextView) findViewById(R.id.summarySuggestions)).setText(
                String.valueOf(Prefs.maxSuggestions(this)));
        ((TextView) findViewById(R.id.summaryHistoryLimit)).setText(
                String.valueOf(Prefs.historyLimit(this)));
        int reported = new io.github.roviicc.colordict.data.ReportLog(this).count();
        ((TextView) findViewById(R.id.summaryReports)).setText(reported == 0
                ? getString(R.string.pref_reported_summary)
                : getString(R.string.reported_words_n, reported));
        ((TextView) findViewById(R.id.summaryAbout)).setText(
                getString(R.string.version_value, versionName()));
    }

    private String versionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "?" : info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private void pickTheme() {
        String[] themes = getResources().getStringArray(R.array.theme_names);
        new AlertDialog.Builder(this)
                .setTitle(R.string.pref_theme)
                .setSingleChoiceItems(themes, Prefs.themeMode(this), (dialog, which) -> {
                    Prefs.setThemeMode(this, which);
                    dialog.dismiss();
                    recreate(); // other activities catch up in BaseActivity.onResume
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private interface IntSetter {
        void set(int value);
    }

    private void pickFromValues(int titleRes, int[] values, int current, IntSetter setter) {
        String[] labels = new String[values.length];
        int checked = -1;
        for (int i = 0; i < values.length; i++) {
            labels[i] = String.valueOf(values[i]);
            if (values[i] == current) {
                checked = i;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    setter.set(values[which]);
                    dialog.dismiss();
                    refreshSummaries();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmClear(int messageRes, Runnable action) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_clear)
                .setMessage(messageRes)
                .setPositiveButton(R.string.action_clear, (d, w) -> {
                    action.run();
                    Toast.makeText(this, R.string.cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.app_name))
                .setMessage(getString(R.string.about_text, versionName()))
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.source_code, (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)));
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }
}
