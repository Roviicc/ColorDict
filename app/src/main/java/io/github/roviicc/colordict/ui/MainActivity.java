package io.github.roviicc.colordict.ui;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.DictRepository;
import io.github.roviicc.colordict.data.Prefs;

public class MainActivity extends BaseActivity implements DictRepository.Listener {

    /** The intent action third-party apps use for dictionary lookups. */
    public static final String ACTION_COLORDICT_SEARCH = "colordict.intent.action.SEARCH";
    public static final String EXTRA_QUERY = "EXTRA_QUERY";

    private EditText searchBox;
    private ImageButton clearButton;
    private TextView listLabel;
    private ListView list;
    private SuggestionAdapter adapter;
    private View definitionPanel;
    private TextView definitionWord;
    private ImageButton starButton;
    private DefinitionWebView webView;
    private TextView emptyView;

    private String currentWord;
    private boolean suppressWatcher;
    private TextToSpeech tts;
    private boolean ttsReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        searchBox = findViewById(R.id.searchBox);
        clearButton = findViewById(R.id.clearButton);
        ImageButton menuButton = findViewById(R.id.menuButton);
        listLabel = findViewById(R.id.listLabel);
        list = findViewById(R.id.suggestionList);
        definitionPanel = findViewById(R.id.definitionPanel);
        definitionWord = findViewById(R.id.definitionWord);
        starButton = findViewById(R.id.starButton);
        ImageButton speakButton = findViewById(R.id.speakButton);
        ImageButton definitionMenuButton = findViewById(R.id.definitionMenuButton);
        webView = findViewById(R.id.definitionWebView);
        emptyView = findViewById(R.id.emptyView);

        adapter = new SuggestionAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id)
                -> define(adapter.getItem(position)));

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!suppressWatcher) {
                    onQueryChanged(s.toString().trim());
                }
                clearButton.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
            }
        });
        searchBox.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE) {
                String q = searchBox.getText().toString().trim();
                if (!q.isEmpty()) {
                    define(q);
                }
                return true;
            }
            return false;
        });

        clearButton.setOnClickListener(v -> {
            searchBox.setText("");
            searchBox.requestFocus();
            showKeyboard();
        });
        menuButton.setOnClickListener(this::showMainMenu);
        starButton.setOnClickListener(v -> toggleBookmark());
        speakButton.setOnClickListener(v -> speakCurrentWord());
        definitionMenuButton.setOnClickListener(this::showDefinitionMenu);
        definitionWord.setOnClickListener(v -> speakCurrentWord());
        webView.setOnWordLinkListener(this::define);
        emptyView.setOnClickListener(v
                -> startActivity(new Intent(this, DictionariesActivity.class)));

        // Route the initial view after view-state restoration: restoring the
        // search box text fires the TextWatcher, which would otherwise cancel
        // a definition query started directly from onCreate.
        searchBox.post(() -> {
            String saved = savedInstanceState != null
                    ? savedInstanceState.getString("word") : null;
            if (saved != null) {
                define(saved);
            } else if (savedInstanceState != null || !handleLookupIntent(getIntent())) {
                showBrowseList(searchBox.getText().toString().trim());
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (definitionPanel.getVisibility() == View.VISIBLE && currentWord != null) {
            outState.putString("word", currentWord);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLookupIntent(intent);
    }

    /** Returns true if the intent carried a query that was looked up. */
    private boolean handleLookupIntent(Intent intent) {
        if (intent == null) {
            return false;
        }
        String query = intent.getStringExtra(EXTRA_QUERY);
        if (query == null && Intent.ACTION_SEND.equals(intent.getAction())) {
            query = intent.getStringExtra(Intent.EXTRA_TEXT);
        }
        if (query == null && Intent.ACTION_SEARCH.equals(intent.getAction())) {
            query = intent.getStringExtra("query");
        }
        if (query != null) {
            query = query.trim();
            if (query.length() > 128) {
                query = query.substring(0, 128).trim();
            }
            if (!query.isEmpty()) {
                define(query);
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        repo().addListener(this);
        refreshEmptyState();
        if (definitionPanel.getVisibility() != View.VISIBLE && adapter.isHistoryMode()) {
            showBrowseList(searchBox.getText().toString().trim());
        }
    }

    @Override
    protected void onStop() {
        repo().removeListener(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (definitionPanel.getVisibility() == View.VISIBLE) {
            exitDefinition();
        } else if (searchBox.getText().length() > 0) {
            searchBox.setText("");
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onDictionariesChanged() {
        refreshEmptyState();
        if (definitionPanel.getVisibility() == View.VISIBLE && currentWord != null) {
            queryDefinition(currentWord);
        } else {
            showBrowseList(searchBox.getText().toString().trim());
        }
    }

    // ------------------------------------------------------------ browse mode

    private void onQueryChanged(String query) {
        if (definitionPanel.getVisibility() == View.VISIBLE) {
            definitionPanel.setVisibility(View.GONE);
            list.setVisibility(View.VISIBLE);
            currentWord = null;
        }
        showBrowseList(query);
    }

    private void showBrowseList(String query) {
        if (query.isEmpty()) {
            adapter.setHistory(repo().history().history(50));
            listLabel.setText(R.string.recent_searches);
            listLabel.setVisibility(adapter.getCount() > 0 ? View.VISIBLE : View.GONE);
        } else {
            repo().suggest(query, Prefs.maxSuggestions(this), suggestions -> {
                // Only apply if the box still shows this query.
                if (query.equals(searchBox.getText().toString().trim())
                        && definitionPanel.getVisibility() != View.VISIBLE) {
                    adapter.setSuggestions(suggestions);
                    listLabel.setVisibility(View.GONE);
                }
            });
        }
    }

    // ------------------------------------------------------------ definitions

    private void define(String word) {
        String trimmed = word == null ? "" : word.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        currentWord = trimmed;
        suppressWatcher = true;
        searchBox.setText(trimmed);
        searchBox.setSelection(trimmed.length());
        suppressWatcher = false;
        clearButton.setVisibility(View.VISIBLE);
        hideKeyboard();
        queryDefinition(trimmed);
    }

    private void queryDefinition(String word) {
        repo().define(word, result -> {
            if (!word.equals(currentWord)) {
                return;
            }
            list.setVisibility(View.GONE);
            listLabel.setVisibility(View.GONE);
            definitionPanel.setVisibility(View.VISIBLE);
            definitionWord.setText(word);
            refreshStar();
            webView.showPage(DefinitionHtml.page(this, result, isNightMode()));
            if (!result.hits.isEmpty()) {
                repo().history().addHistory(word, Prefs.historyLimit(this));
            }
        });
    }

    private void exitDefinition() {
        definitionPanel.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);
        currentWord = null;
        showBrowseList(searchBox.getText().toString().trim());
    }

    private void refreshStar() {
        boolean starred = currentWord != null && repo().history().isBookmarked(currentWord);
        starButton.setImageResource(starred ? R.drawable.ic_star_filled : R.drawable.ic_star);
        starButton.setContentDescription(getString(
                starred ? R.string.remove_bookmark : R.string.add_bookmark));
    }

    private void toggleBookmark() {
        if (currentWord == null) {
            return;
        }
        boolean nowStarred = repo().history().toggleBookmark(currentWord);
        refreshStar();
        Toast.makeText(this, nowStarred ? R.string.bookmark_added : R.string.bookmark_removed,
                Toast.LENGTH_SHORT).show();
    }

    private void speakCurrentWord() {
        if (currentWord == null) {
            return;
        }
        if (tts == null) {
            tts = new TextToSpeech(this, status -> {
                ttsReady = status == TextToSpeech.SUCCESS;
                if (ttsReady) {
                    tts.setLanguage(Locale.getDefault());
                    if (currentWord != null) {
                        tts.speak(currentWord, TextToSpeech.QUEUE_FLUSH, null, "colordict");
                    }
                } else {
                    Toast.makeText(this, R.string.tts_unavailable, Toast.LENGTH_SHORT).show();
                }
            });
        } else if (ttsReady) {
            tts.speak(currentWord, TextToSpeech.QUEUE_FLUSH, null, "colordict");
        } else {
            Toast.makeText(this, R.string.tts_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------ menus

    private void showMainMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.inflate(R.menu.menu_main);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_dictionaries) {
                startActivity(new Intent(this, DictionariesActivity.class));
            } else if (id == R.id.action_history) {
                startActivity(new Intent(this, HistoryActivity.class));
            } else if (id == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else {
                return false;
            }
            return true;
        });
        menu.show();
    }

    private void showDefinitionMenu(View anchor) {
        if (currentWord == null) {
            return;
        }
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.inflate(R.menu.menu_definition);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_copy) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("word", currentWord));
                Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
            } else if (id == R.id.action_share) {
                Intent send = new Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, currentWord);
                startActivity(Intent.createChooser(send, getString(R.string.share_word)));
            } else if (id == R.id.action_wikipedia) {
                openUrl("https://" + language() + ".wikipedia.org/wiki/Special:Search?search="
                        + Uri.encode(currentWord));
            } else if (id == R.id.action_wiktionary) {
                openUrl("https://" + language() + ".wiktionary.org/w/index.php?search="
                        + Uri.encode(currentWord));
            } else if (id == R.id.action_web_search) {
                Intent search = new Intent(Intent.ACTION_WEB_SEARCH)
                        .putExtra("query", currentWord);
                try {
                    startActivity(search);
                } catch (ActivityNotFoundException e) {
                    openUrl("https://duckduckgo.com/?q=" + Uri.encode(currentWord));
                }
            } else {
                return false;
            }
            return true;
        });
        menu.show();
    }

    private String language() {
        String lang = Locale.getDefault().getLanguage();
        return lang.isEmpty() ? "en" : lang;
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------ helpers

    private void refreshEmptyState() {
        boolean empty = repo().isScanned() && repo().enabledDictionaries().isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchBox.getWindowToken(), 0);
    }
}
