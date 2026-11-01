package io.github.roviicc.colordict.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.Entry;
import io.github.roviicc.colordict.ui.entry.EntryController;
import io.github.roviicc.colordict.ui.widget.Views;

/** The full dictionary entry for one word, with copy, bookmark and share. */
public class WordActivity extends BaseActivity implements EntryController.Host {

    private static final String EXTRA_WORD = "word";

    private EntryController entry;
    private TextView toolbarTitle;

    static Intent intent(Context context, String word) {
        return new Intent(context, WordActivity.class).putExtra(EXTRA_WORD, word);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String word = getIntent().getStringExtra(EXTRA_WORD);
        if (word == null || word.trim().isEmpty()) {
            finish();
            return;
        }
        setContentView(R.layout.activity_word);

        View root = findViewById(R.id.root);
        Views.onInsets(root, (l, t, r, b) -> root.setPadding(l, t, r, b));

        ScrollView scroll = findViewById(R.id.scroll);
        toolbarTitle = findViewById(R.id.toolbarTitle);
        toolbarTitle.setTypeface(Views.serifBold());
        toolbarTitle.setText(word.trim());
        View toolbarDivider = findViewById(R.id.toolbarDivider);

        entry = new EntryController(this, repository(), findViewById(R.id.content), scroll, false, this);
        entry.bindSaveButton(findViewById(R.id.saveButton));

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.copyButton).setOnClickListener(v -> Views.copyToClipboard(this, shareText()));
        findViewById(R.id.shareButton).setOnClickListener(v -> share());

        // Fade the headword into the toolbar once it scrolls away.
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            float p = Math.min(1f, Math.max(0f, (y - Views.dp(this, 24)) / (float) Views.dp(this, 32)));
            toolbarTitle.setAlpha(p);
            toolbarDivider.setAlpha(p);
        });

        entry.load(word.trim());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (entry != null) {
            entry.refreshSaveButton(); // it may have changed in the popup or Home
        }
    }

    // ------------------------------------------------------- EntryController.Host

    @Override
    public void onEntryShown(Entry e) {
        toolbarTitle.setText(entry.headword());
    }

    @Override
    public void onWordTapped(String word) {
        startActivity(intent(this, word));
    }

    // ---------------------------------------------------------------- actions

    private String shareText() {
        Entry e = entry.entry();
        return e != null && e.hasSenses() ? e.toPlainText() : entry.word();
    }

    private void share() {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, shareText());
        startActivity(Intent.createChooser(send, getString(R.string.share)));
    }
}
