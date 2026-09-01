package io.github.roviicc.colordict.ui;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import androidx.compose.ui.platform.ComposeView;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.DictRepository;
import io.github.roviicc.colordict.data.Prefs;
import io.github.roviicc.colordict.design.MainChromeBridge;
import io.github.roviicc.colordict.design.MainChromeController;
import io.github.roviicc.colordict.design.ComponentCatalog;
import io.github.roviicc.colordict.design.BrowseListBridge;
import io.github.roviicc.colordict.design.BrowseListController;

public class MainActivity extends BaseActivity implements DictRepository.Listener {

    /** The intent action third-party apps use for dictionary lookups. */
    public static final String ACTION_COLORDICT_SEARCH = "colordict.intent.action.SEARCH";
    public static final String EXTRA_QUERY = "EXTRA_QUERY";

    private MainChromeController mainChrome;
    private BrowseListController browseList;
    private View definitionPanel;
    private TextView definitionWord;
    private ImageButton starButton;
    private DefinitionWebView webView;
    private TextView emptyView;

    private String currentWord;
    private TextToSpeech tts;
    private boolean ttsReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ComposeView chromeView = findViewById(R.id.mainChrome);
        ComposeView browseListView = findViewById(R.id.browseList);
        definitionPanel = findViewById(R.id.definitionPanel);
        definitionWord = findViewById(R.id.definitionWord);
        starButton = findViewById(R.id.starButton);
        ImageButton speakButton = findViewById(R.id.speakButton);
        ImageButton definitionMenuButton = findViewById(R.id.definitionMenuButton);
        webView = findViewById(R.id.definitionWebView);
        emptyView = findViewById(R.id.emptyView);

        browseList = BrowseListBridge.attach(browseListView, this::define);

        mainChrome = MainChromeBridge.attach(chromeView, "", new MainChromeBridge.Callbacks() {
            @Override
            public void onQueryChanged(String query) {
                MainActivity.this.onQueryChanged(query);
            }

            @Override
            public void onSearch(String query) {
                if (!query.isEmpty()) {
                    define(query);
                }
            }

            @Override
            public void onMenu(View anchor) {
                showMainMenu(anchor);
            }
        });
        starButton.setOnClickListener(v -> toggleBookmark());
        speakButton.setOnClickListener(v -> speakCurrentWord());
        definitionMenuButton.setOnClickListener(this::showDefinitionMenu);
        definitionWord.setOnClickListener(v -> speakCurrentWord());
        webView.setOnWordLinkListener(this::define);
        emptyView.setOnClickListener(v
                -> startActivity(new Intent(this, DictionariesActivity.class)));

        chromeView.post(() -> {
            String saved = savedInstanceState != null
                    ? savedInstanceState.getString("word") : null;
            if (saved != null) {
                define(saved);
            } else if (savedInstanceState != null || !handleLookupIntent(getIntent())) {
                showBrowseList(mainChrome.query().trim());
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
        if (definitionPanel.getVisibility() != View.VISIBLE && browseList.isHistoryMode()) {
            showBrowseList(mainChrome.query().trim());
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
        } else if (!mainChrome.query().isEmpty()) {
            mainChrome.setQuery("");
            onQueryChanged("");
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
            showBrowseList(mainChrome.query().trim());
        }
    }

    // ------------------------------------------------------------ browse mode

    private void onQueryChanged(String query) {
        if (definitionPanel.getVisibility() == View.VISIBLE) {
            definitionPanel.setVisibility(View.GONE);
            browseList.setVisible(true);
            currentWord = null;
        }
        showBrowseList(query);
    }

    private void showBrowseList(String query) {
        if (query.isEmpty()) {
            browseList.setHistory(repo().history().history(50));
            mainChrome.setRecentVisible(browseList.count() > 0);
        } else {
            mainChrome.setRecentVisible(false);
            repo().suggest(query, Prefs.maxSuggestions(this), suggestions -> {
                // Only apply if the box still shows this query.
                if (query.equals(mainChrome.query().trim())
                        && definitionPanel.getVisibility() != View.VISIBLE) {
                    browseList.setSuggestions(suggestions);
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
        mainChrome.setQuery(trimmed);
        mainChrome.setRecentVisible(false);
        hideKeyboard();
        queryDefinition(trimmed);
    }

    private void queryDefinition(String word) {
        repo().define(word, result -> {
            if (!word.equals(currentWord)) {
                return;
            }
            browseList.setVisible(false);
            mainChrome.setRecentVisible(false);
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
        browseList.setVisible(true);
        currentWord = null;
        showBrowseList(mainChrome.query().trim());
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
        menu.getMenu().findItem(R.id.action_component_catalog)
                .setVisible(ComponentCatalog.isAvailable());
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_dictionaries) {
                startActivity(new Intent(this, DictionariesActivity.class));
            } else if (id == R.id.action_history) {
                startActivity(new Intent(this, HistoryActivity.class));
            } else if (id == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else if (id == R.id.action_component_catalog) {
                ComponentCatalog.open(this);
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
        mainChrome.requestSearchFocus();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View focus = getCurrentFocus();
        if (focus != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }
}
