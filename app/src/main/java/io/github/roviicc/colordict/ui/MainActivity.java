package io.github.roviicc.colordict.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.Entry;
import io.github.roviicc.colordict.data.Words;
import io.github.roviicc.colordict.data.WordList;

/** Home: search field, then Recent / Saved words in a grouped card list. */
public class MainActivity extends BaseActivity {

    private EditText input;
    private ImageButton clearButton;
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

        View root = findViewById(R.id.root);
        Ui.onInsets(root, (l, t, r, b) -> root.setPadding(l, t, r, b));

        TextView title = findViewById(R.id.title);
        title.setTypeface(Ui.serifBold());

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
        ((TextView) findViewById(R.id.emptyGlyph)).setTypeface(Ui.serifBold());

        float pill = Ui.dp(this, 999);
        findViewById(R.id.searchBox).setBackground(Ui.outlined(
                Ui.color(this, R.color.surface), Ui.color(this, R.color.outline),
                Ui.dp(this, 1), pill));
        findViewById(R.id.tabs).setBackground(Ui.rounded(Ui.color(this, R.color.surfaceMuted), pill));

        findViewById(R.id.settingsButton).setOnClickListener(
                v -> startActivity(new Intent(this, SettingsActivity.class)));
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
        dictionary().warmUp(this::refresh);

        selectTab(false);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /** Text shared to the app ("Share → ColorDict") opens that word. */
    private void handleIntent(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null && !text.trim().isEmpty()) {
                lookUp(text);
            }
        }
    }

    private void lookUp(String raw) {
        String word = raw.trim().replaceAll("\\s+", " ");
        if (word.isEmpty()) {
            return;
        }
        if (word.length() > 64) {
            word = word.substring(0, 64).trim();
        }
        startActivity(WordActivity.intent(this, word));
    }

    private void selectTab(boolean saved) {
        showingSaved = saved;
        float r = Ui.dp(this, 999);
        styleTab(tabRecent, !saved, r);
        styleTab(tabSaved, saved, r);
        refresh();
    }

    private void styleTab(TextView tab, boolean selected, float radius) {
        tab.setBackground(selected ? Ui.rounded(Ui.color(this, R.color.surface), radius) : null);
        tab.setElevation(selected ? Ui.dp(this, 1) : 0);
        tab.setTextColor(Ui.color(this, selected ? R.color.textPrimary : R.color.textSecondary));
        tab.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    private void refresh() {
        String query = input.getText().toString().trim();
        list.removeAllViews();
        if (!query.isEmpty()) {
            showMatches(query);
            return;
        }
        WordList source = showingSaved ? dictionary().saved() : dictionary().recent();
        List<String> words = source.all();
        listTitle.setText(showingSaved ? R.string.tab_saved : R.string.tab_recent);
        clearAll.setVisibility(words.isEmpty() ? View.GONE : View.VISIBLE);
        if (words.isEmpty()) {
            emptyTitle.setText(showingSaved ? R.string.empty_saved_title : R.string.empty_recent_title);
            emptyBody.setText(showingSaved ? R.string.empty_saved_body : R.string.empty_recent_body);
            empty.setVisibility(View.VISIBLE);
            listScroll.setVisibility(View.GONE);
            return;
        }
        empty.setVisibility(View.GONE);
        listScroll.setVisibility(View.VISIBLE);
        LinearLayout card = card();
        for (String w : words) {
            addRow(card, w, true, source);
        }
        list.addView(card);
    }

    /** While typing: matching words from the built-in dictionary, recent and saved first. */
    private void showMatches(String query) {
        empty.setVisibility(View.GONE);
        listScroll.setVisibility(View.VISIBLE);
        clearAll.setVisibility(View.GONE);
        listTitle.setText(R.string.matches);

        String key = Words.key(query);
        Map<String, String> mine = new LinkedHashMap<>();
        List<String> pool = new ArrayList<>(dictionary().recent().all());
        pool.addAll(dictionary().saved().all());
        for (String w : pool) {
            String k = Words.key(w);
            if (k.startsWith(key) && !mine.containsKey(k) && mine.size() < 3) {
                mine.put(k, w);
            }
        }
        renderMatches(query, mine, new ArrayList<>());
        dictionary().suggest(query, 10, words -> {
            if (!query.equals(input.getText().toString().trim())) {
                return; // the user kept typing
            }
            renderMatches(query, mine, words);
        });
    }

    private void renderMatches(String query, Map<String, String> mine, List<String> fromDictionary) {
        list.removeAllViews();
        LinearLayout card = card();
        Map<String, String> all = new LinkedHashMap<>(mine);
        for (String w : fromDictionary) {
            if (all.size() >= 10) {
                break;
            }
            all.putIfAbsent(Words.key(w), w);
        }
        if (!all.containsKey(Words.key(query))) {
            View lookUpRow = row(getString(R.string.look_up_query, query), null, R.drawable.ic_search);
            lookUpRow.setOnClickListener(v -> lookUp(query));
            card.addView(lookUpRow);
        }
        for (String w : all.values()) {
            addRow(card, w, false, null);
        }
        list.addView(card);
    }

    private void addRow(LinearLayout card, String word, boolean removable, WordList source) {
        if (card.getChildCount() > 0) {
            addDivider(card);
        }
        Entry cached = dictionary().peek(word);
        View row = row(word, cached == null ? null : cached.preview(), 0);
        row.setOnClickListener(v -> lookUp(word));
        if (removable) {
            row.setOnLongClickListener(v -> {
                source.remove(word);
                Toast.makeText(this, getString(R.string.removed, word), Toast.LENGTH_SHORT).show();
                refresh();
                return true;
            });
        }
        card.addView(row);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        float r = Ui.dp(this, 16);
        card.setBackground(Ui.outlined(Ui.color(this, R.color.surface),
                Ui.color(this, R.color.outline), Ui.dp(this, 1), r));
        card.setClipToOutline(true);
        return card;
    }

    private View row(String title, String subtitle, int leadingIcon) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(this, 56));
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        row.setBackgroundResource(resolveSelectableBackground());

        if (leadingIcon != 0) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(leadingIcon);
            int s = Ui.dp(this, 20);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(s, s);
            ip.setMarginEnd(Ui.dp(this, 12));
            row.addView(icon, ip);
        }

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, 17, R.color.textPrimary);
        t.setText(title);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = Ui.text(this, 14, R.color.textSecondary);
            s.setText(subtitle);
            s.setSingleLine(true);
            s.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            sp.topMargin = Ui.dp(this, 2);
            col.addView(s, sp);
        }
        row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setAlpha(0.5f);
        int s = Ui.dp(this, 20);
        row.addView(chevron, new LinearLayout.LayoutParams(s, s));
        return row;
    }

    private void addDivider(LinearLayout card) {
        View line = new View(this);
        line.setBackgroundColor(Ui.color(this, R.color.outline));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.setMarginStart(Ui.dp(this, 16));
        card.addView(line, lp);
    }

    private int resolveSelectableBackground() {
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return tv.resourceId;
    }

    private void confirmClear() {
        boolean saved = showingSaved;
        new AlertDialog.Builder(this)
                .setMessage(saved ? R.string.clear_saved_confirm : R.string.clear_recent_confirm)
                .setPositiveButton(R.string.clear, (d, w) -> {
                    (saved ? dictionary().saved() : dictionary().recent()).clear();
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
