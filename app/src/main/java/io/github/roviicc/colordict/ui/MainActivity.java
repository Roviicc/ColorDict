package io.github.roviicc.colordict.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.Entry;
import io.github.roviicc.colordict.data.WordList;
import io.github.roviicc.colordict.data.dictionary.Words;
import io.github.roviicc.colordict.ui.widget.CardList;
import io.github.roviicc.colordict.ui.widget.Views;

/** Home: search with live suggestions, then the Recent / Saved word lists. */
public class MainActivity extends BaseActivity {

    private static final int MAX_QUERY = 64;
    private static final int MAX_MATCHES = 10;
    /** Recent/saved words shown first among the matches. */
    private static final int MAX_OWN_MATCHES = 3;

    private EditText input;
    private View clearButton;
    private TextView tabRecent;
    private TextView tabSaved;
    private TextView listTitle;
    private TextView clearAll;
    private LinearLayout list;
    private View listScroll;
    private View empty;
    private TextView emptyTitle;
    private TextView emptyBody;
    private boolean showingSaved;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        styleViews();

        findViewById(R.id.settingsButton).setOnClickListener(v -> openSettings());
        clearButton.setOnClickListener(v -> input.setText(""));
        tabRecent.setOnClickListener(v -> selectTab(false));
        tabSaved.setOnClickListener(v -> selectTab(true));
        clearAll.setOnClickListener(v -> confirmClear());
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                clearButton.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                refresh();
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                lookUp(input.getText().toString());
                return true;
            }
            return false;
        });

        // Open the built-in dictionary early so previews and lookups are instant.
        repository().warmUp(this::refresh);
        selectTab(false);
        handleShare(getIntent());
    }

    private void bindViews() {
        input = findViewById(R.id.searchInput);
        clearButton = findViewById(R.id.clearButton);
        tabRecent = findViewById(R.id.tabRecent);
        tabSaved = findViewById(R.id.tabSaved);
        listTitle = findViewById(R.id.listTitle);
        clearAll = findViewById(R.id.clearAll);
        list = findViewById(R.id.list);
        listScroll = findViewById(R.id.listScroll);
        empty = findViewById(R.id.empty);
        emptyTitle = findViewById(R.id.emptyTitle);
        emptyBody = findViewById(R.id.emptyBody);
    }

    private void styleViews() {
        View root = findViewById(R.id.root);
        Views.onInsets(root, (l, t, r, b) -> root.setPadding(l, t, r, b));
        ((TextView) findViewById(R.id.title)).setTypeface(Views.serifBold());
        ((TextView) findViewById(R.id.emptyGlyph)).setTypeface(Views.serifBold());
        float pill = Views.dp(this, 999);
        findViewById(R.id.searchBox).setBackground(Views.outlined(Views.color(this, R.color.surface),
                Views.color(this, R.color.outline), Views.dp(this, 1), pill));
        findViewById(R.id.tabs).setBackground(Views.rounded(Views.color(this, R.color.surfaceMuted), pill));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShare(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /** Text shared to the app ("Share → ColorDict") opens that word. */
    private void handleShare(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                lookUp(text);
            }
        }
    }

    private void lookUp(String raw) {
        String word = raw.trim().replaceAll("\\s+", " ");
        if (word.length() > MAX_QUERY) {
            word = word.substring(0, MAX_QUERY).trim();
        }
        if (!word.isEmpty()) {
            startActivity(WordActivity.intent(this, word));
        }
    }

    // ---------------------------------------------------------------- tabs

    private void selectTab(boolean saved) {
        showingSaved = saved;
        styleTab(tabRecent, !saved);
        styleTab(tabSaved, saved);
        refresh();
    }

    private void styleTab(TextView tab, boolean selected) {
        tab.setBackground(selected ? Views.rounded(Views.color(this, R.color.surface), Views.dp(this, 999)) : null);
        tab.setElevation(selected ? Views.dp(this, 1) : 0);
        tab.setTextColor(Views.color(this, selected ? R.color.textPrimary : R.color.textSecondary));
        tab.setTypeface(Views.medium());
    }

    private WordList currentList() {
        return showingSaved ? repository().saved() : repository().recent();
    }

    // ---------------------------------------------------------------- list

    private void refresh() {
        String query = input.getText().toString().trim();
        if (query.isEmpty()) {
            showWordList();
        } else {
            showMatches(query);
        }
    }

    private void showWordList() {
        WordList source = currentList();
        List<String> words = source.all();
        listTitle.setText(showingSaved ? R.string.tab_saved : R.string.tab_recent);
        clearAll.setVisibility(words.isEmpty() ? View.GONE : View.VISIBLE);
        empty.setVisibility(words.isEmpty() ? View.VISIBLE : View.GONE);
        listScroll.setVisibility(words.isEmpty() ? View.GONE : View.VISIBLE);
        list.removeAllViews();
        if (words.isEmpty()) {
            emptyTitle.setText(showingSaved ? R.string.empty_saved_title : R.string.empty_recent_title);
            emptyBody.setText(showingSaved ? R.string.empty_saved_body : R.string.empty_recent_body);
            return;
        }
        LinearLayout card = CardList.card(this);
        for (String word : words) {
            View row = wordRow(word);
            row.setOnLongClickListener(v -> {
                source.remove(word);
                Toast.makeText(this, getString(R.string.removed, word), Toast.LENGTH_SHORT).show();
                refresh();
                return true;
            });
            CardList.addRow(card, row);
        }
        list.addView(card);
    }

    /** While typing: recent and saved words first, then built-in dictionary matches. */
    private void showMatches(String query) {
        empty.setVisibility(View.GONE);
        listScroll.setVisibility(View.VISIBLE);
        clearAll.setVisibility(View.GONE);
        listTitle.setText(R.string.matches);

        String key = Words.key(query);
        Map<String, String> matches = new LinkedHashMap<>();
        for (WordList own : new WordList[]{repository().recent(), repository().saved()}) {
            for (String w : own.all()) {
                if (matches.size() < MAX_OWN_MATCHES && Words.key(w).startsWith(key)) {
                    matches.putIfAbsent(Words.key(w), w);
                }
            }
        }
        renderMatches(query, matches);
        repository().suggest(query, MAX_MATCHES, words -> {
            if (!query.equals(input.getText().toString().trim())) {
                return; // the user kept typing
            }
            Map<String, String> all = new LinkedHashMap<>(matches);
            for (String w : words) {
                if (all.size() < MAX_MATCHES) {
                    all.putIfAbsent(Words.key(w), w);
                }
            }
            renderMatches(query, all);
        });
    }

    private void renderMatches(String query, Map<String, String> matches) {
        list.removeAllViews();
        LinearLayout card = CardList.card(this);
        if (!matches.containsKey(Words.key(query))) {
            View lookUp = CardList.row(this, getString(R.string.look_up_query, query), null,
                    R.drawable.ic_search, true, true);
            lookUp.setOnClickListener(v -> lookUp(query));
            CardList.addRow(card, lookUp);
        }
        for (String word : matches.values()) {
            CardList.addRow(card, wordRow(word));
        }
        list.addView(card);
    }

    /** A word with its first meaning as a preview; tapping opens it. */
    private View wordRow(String word) {
        Entry preview = repository().peek(word);
        View row = CardList.row(this, word, preview == null ? null : preview.preview(), 0, true, true);
        row.setOnClickListener(v -> lookUp(word));
        return row;
    }

    private void confirmClear() {
        WordList source = currentList();
        new AlertDialog.Builder(this)
                .setMessage(showingSaved ? R.string.clear_saved_confirm : R.string.clear_recent_confirm)
                .setPositiveButton(R.string.clear, (d, w) -> {
                    source.clear();
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
