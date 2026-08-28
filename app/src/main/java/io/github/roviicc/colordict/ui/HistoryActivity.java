package io.github.roviicc.colordict.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import io.github.roviicc.colordict.R;

/** Recent searches and bookmarked words; tap to look one up again. */
public class HistoryActivity extends BaseActivity {

    private static final int MAX_ROWS = 500;

    private boolean showingBookmarks;
    private ArrayAdapter<String> adapter;
    private final List<String> words = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
        if (savedInstanceState != null) {
            showingBookmarks = savedInstanceState.getBoolean("bookmarks");
        }

        ListView list = findViewById(R.id.historyList);
        adapter = new ArrayAdapter<>(this, R.layout.item_history_row, R.id.rowWord, words);
        list.setAdapter(adapter);
        TextView empty = findViewById(R.id.historyEmpty);
        list.setEmptyView(empty);

        list.setOnItemClickListener((parent, view, position, id) -> {
            Intent lookup = new Intent(this, MainActivity.class)
                    .setAction(MainActivity.ACTION_COLORDICT_SEARCH)
                    .putExtra(MainActivity.EXTRA_QUERY, adapter.getItem(position))
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(lookup);
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            String word = adapter.getItem(position);
            if (showingBookmarks) {
                repo().history().removeBookmark(word);
            } else {
                repo().history().removeHistory(word);
            }
            reload();
            return true;
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("bookmarks", showingBookmarks);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        setTitle(showingBookmarks ? R.string.bookmarks : R.string.history);
        words.clear();
        words.addAll(showingBookmarks
                ? repo().history().bookmarks(MAX_ROWS)
                : repo().history().history(MAX_ROWS));
        adapter.notifyDataSetChanged();
        TextView empty = findViewById(R.id.historyEmpty);
        empty.setText(showingBookmarks ? R.string.no_bookmarks : R.string.no_history);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_history, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_toggle_bookmarks).setTitle(
                showingBookmarks ? R.string.show_history : R.string.show_bookmarks);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
        } else if (id == R.id.action_toggle_bookmarks) {
            showingBookmarks = !showingBookmarks;
            invalidateOptionsMenu();
            reload();
        } else if (id == R.id.action_clear) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.action_clear)
                    .setMessage(showingBookmarks
                            ? R.string.clear_bookmarks_confirm : R.string.clear_history_confirm)
                    .setPositiveButton(R.string.action_clear, (d, w) -> {
                        if (showingBookmarks) {
                            repo().history().clearBookmarks();
                        } else {
                            repo().history().clearHistory();
                        }
                        reload();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }
}
