package io.github.roviicc.colordict.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.Dictionary;
import io.github.roviicc.colordict.data.Entry;

/** The full dictionary entry for one word. */
public class WordActivity extends BaseActivity implements EntryRenderer.Listener {

    private static final String EXTRA_WORD = "word";

    private String word;
    private Entry entry;
    private EntryRenderer renderer;
    private LinearLayout content;
    private ImageButton saveButton;
    private TextView toolbarTitle;
    private View toolbarDivider;
    private int requestId;

    static Intent intent(Context context, String word) {
        return new Intent(context, WordActivity.class).putExtra(EXTRA_WORD, word);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_word);

        word = getIntent().getStringExtra(EXTRA_WORD);
        if (word == null || word.trim().isEmpty()) {
            finish();
            return;
        }
        word = word.trim();

        View root = findViewById(R.id.root);
        Ui.onInsets(root, (l, t, r, b) -> root.setPadding(l, t, r, b));

        content = findViewById(R.id.content);
        saveButton = findViewById(R.id.saveButton);
        toolbarTitle = findViewById(R.id.toolbarTitle);
        toolbarTitle.setTypeface(Ui.serifBold());
        toolbarDivider = findViewById(R.id.toolbarDivider);
        renderer = new EntryRenderer(this, this);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> toggleSaved());
        findViewById(R.id.copyButton).setOnClickListener(v ->
                renderer.copy(entry != null ? entry.toPlainText() : word));
        findViewById(R.id.shareButton).setOnClickListener(v -> share());

        // Fade the headword into the toolbar once it scrolls away.
        ScrollView scroll = findViewById(R.id.scroll);
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            float p = Math.min(1f, Math.max(0f, (y - Ui.dp(this, 24)) / (float) Ui.dp(this, 32)));
            toolbarTitle.setAlpha(p);
            toolbarDivider.setAlpha(p);
        });

        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSaveIcon();
    }

    private void load() {
        final int id = ++requestId;
        toolbarTitle.setText(word);
        if (!dictionary().isReady()) {
            renderer.renderLoading(content, word, false);
        }
        dictionary().define(word, new Dictionary.Callback() {
            @Override
            public void onEntry(Entry e) {
                if (id != requestId || isDestroyed()) {
                    return;
                }
                entry = e;
                if (e.hasSenses() && !e.word.isEmpty()) {
                    toolbarTitle.setText(e.word);
                }
                renderer.renderEntry(content, e, false);
                updateSaveIcon();
            }

            @Override
            public void onError(String message, boolean retryable, boolean needsSettings) {
                if (id != requestId || isDestroyed()) {
                    return;
                }
                if (needsSettings) {
                    renderer.renderError(content, word, errorText(message),
                            getString(R.string.open_settings), WordActivity.this::openSettings);
                } else {
                    renderer.renderError(content, word, message,
                            retryable ? getString(R.string.try_again) : null, WordActivity.this::load);
                }
            }
        });
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        // Back from Settings with a key: retry a lookup that failed for lack of one.
        if (entry == null && dictionary().hasApiKey()) {
            load();
        }
    }

    @Override
    public void onConnotationRequested() {
        if (entry == null) {
            return;
        }
        final int id = requestId;
        dictionary().checkConnotation(word, entry, new Dictionary.Callback() {
            @Override
            public void onEntry(Entry e) {
                if (id != requestId || isDestroyed()) {
                    return;
                }
                entry = e;
                int y = ((ScrollView) findViewById(R.id.scroll)).getScrollY();
                renderer.renderEntry(content, e, false);
                findViewById(R.id.scroll).post(() -> findViewById(R.id.scroll).scrollTo(0, y));
            }

            @Override
            public void onError(String message, boolean retryable, boolean needsSettings) {
                if (id != requestId || isDestroyed()) {
                    return;
                }
                renderer.showConnotationError(errorText(message),
                        needsSettings ? getString(R.string.open_settings) : null,
                        needsSettings ? WordActivity.this::openSettings : null);
            }
        });
    }

    /** The word to save: the canonical headword once known, else what was typed. */
    private String savedWord() {
        return entry != null && entry.hasSenses() && !entry.word.isEmpty() ? entry.word : word;
    }

    private void toggleSaved() {
        dictionary().saved().toggle(savedWord());
        updateSaveIcon();
    }

    private void updateSaveIcon() {
        if (saveButton == null) {
            return;
        }
        boolean saved = dictionary().saved().contains(savedWord());
        Ui.icon(saveButton, saved ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark,
                Ui.color(this, saved ? R.color.accent : R.color.textSecondary));
        saveButton.setContentDescription(getString(saved ? R.string.unsave : R.string.save));
    }

    private void share() {
        String text = entry != null && entry.hasSenses() ? entry.toPlainText() : word;
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, getString(R.string.share)));
    }

    @Override
    public void onWordTapped(String tapped) {
        startActivity(intent(this, tapped));
    }

    @Override
    public void onSpeak(String w) {
        speak(w);
    }
}
