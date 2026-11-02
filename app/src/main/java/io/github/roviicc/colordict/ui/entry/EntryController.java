package io.github.roviicc.colordict.ui.entry;

import android.app.Activity;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.DictionaryRepository;
import io.github.roviicc.colordict.data.Entry;
import io.github.roviicc.colordict.data.LookupError;
import io.github.roviicc.colordict.ui.widget.Views;

/**
 * Shows one word in a screen (the full word page or the popup): looks it up,
 * renders the entry or an error, runs the Claude requests ("Check
 * connotation", "Look up with Claude"), and keeps the bookmark button in sync. Results for a word the user has already moved on
 * from are ignored.
 */
public final class EntryController implements EntryRenderer.Listener {

    /** What differs between the screens that show an entry. */
    public interface Host {
        /** An entry (or "not found" page) was rendered. */
        void onEntryShown(Entry entry);

        /** A synonym, suggestion or related word was tapped. */
        void onWordTapped(String word);

        void speak(String word);

        void openSettings();

        /** The "See full entry" link was tapped (enable it with {@link #showFullEntryLink}). */
        default void onOpenFullEntry() {
        }
    }

    private final Activity activity;
    private final DictionaryRepository repository;
    private final LinearLayout content;
    private final ScrollView scroll;
    private final Host host;
    private final EntryRenderer renderer;
    private ImageButton saveButton;

    private String word;
    private Entry entry;
    /** Incremented per lookup so late results for an earlier word are dropped. */
    private int generation;

    public EntryController(Activity activity, DictionaryRepository repository, LinearLayout content,
                           ScrollView scroll, boolean compact, Host host) {
        this.activity = activity;
        this.repository = repository;
        this.content = content;
        this.scroll = scroll;
        this.host = host;
        this.renderer = new EntryRenderer(activity, this, compact);
    }

    /** Ends entries with a "See full entry" link that calls {@link Host#onOpenFullEntry}. */
    public void showFullEntryLink(boolean show) {
        renderer.setFullEntryLink(show);
    }

    /** Switches between the short and full layout, re-rendering in place. */
    public void setCompact(boolean compact) {
        renderer.setCompact(compact);
        if (entry != null) {
            rerenderInPlace(entry);
        }
    }

    /** The word as the user asked for it. */
    public String word() {
        return word;
    }

    /** The shown entry, or null while loading or after an error. */
    public Entry entry() {
        return entry;
    }

    /** The word to bookmark or open: the dictionary's headword once known, else the query. */
    public String headword() {
        return entry != null && entry.hasSenses() && !entry.word.isEmpty() ? entry.word : word;
    }

    public void load(String query) {
        final int id = ++generation;
        word = query;
        entry = null;
        scroll.scrollTo(0, 0);
        refreshSaveButton();
        if (!repository.isReady()) {
            renderer.renderLoading(content, query);
        }
        repository.define(query, new DictionaryRepository.Callback<Entry>() {
            @Override
            public void onResult(Entry e) {
                if (isCurrent(id)) {
                    show(e);
                }
            }

            @Override
            public void onError(LookupError error) {
                if (isCurrent(id)) {
                    showError(error);
                }
            }
        });
    }

    private void show(Entry e) {
        entry = e;
        renderer.renderEntry(content, e);
        refreshSaveButton();
        host.onEntryShown(e);
    }

    private void showError(LookupError error) {
        switch (error.fix) {
            case OPEN_SETTINGS:
                renderer.renderError(content, word, error.message,
                        activity.getString(R.string.open_settings), host::openSettings);
                break;
            case RETRY:
                renderer.renderError(content, word, error.message,
                        activity.getString(R.string.try_again), () -> load(word));
                break;
            default:
                renderer.renderError(content, word, error.message, null, null);
        }
    }

    private boolean isCurrent(int id) {
        return id == generation && !activity.isDestroyed();
    }

    // ------------------------------------------------------------ connotation

    @Override
    public void onConnotationRequested() {
        if (entry == null) {
            return;
        }
        final int id = generation;
        repository.checkConnotation(word, entry, new DictionaryRepository.Callback<Entry>() {
            @Override
            public void onResult(Entry e) {
                if (isCurrent(id)) {
                    rerenderInPlace(e);
                }
            }

            @Override
            public void onError(LookupError error) {
                if (isCurrent(id)) {
                    showClaudeError(error);
                }
            }
        });
    }

    /** A word the dictionary doesn't have: ask Claude for its meaning and connotation. */
    @Override
    public void onLookUpWithClaude() {
        final int id = generation;
        repository.lookUpWithClaude(word, new DictionaryRepository.Callback<Entry>() {
            @Override
            public void onResult(Entry e) {
                if (isCurrent(id)) {
                    show(e);
                }
            }

            @Override
            public void onError(LookupError error) {
                if (isCurrent(id)) {
                    showClaudeError(error);
                }
            }
        });
    }

    private void showClaudeError(LookupError error) {
        boolean settings = error.fix == LookupError.Fix.OPEN_SETTINGS;
        renderer.showClaudeError(error.message,
                settings ? activity.getString(R.string.open_settings) : null,
                settings ? host::openSettings : null);
    }

    /** Re-renders the entry keeping the reader's scroll position. */
    private void rerenderInPlace(Entry e) {
        int y = scroll.getScrollY();
        entry = e;
        renderer.renderEntry(content, e);
        scroll.post(() -> scroll.scrollTo(0, y));
        host.onEntryShown(e);
    }

    @Override
    public void onOpenFullEntry() {
        host.onOpenFullEntry();
    }

    @Override
    public void onWordTapped(String tapped) {
        host.onWordTapped(tapped);
    }

    @Override
    public void onSpeak(String w) {
        host.speak(w);
    }

    // ------------------------------------------------------------ bookmark

    /** Makes the button toggle the current word in Saved and show its state. */
    public void bindSaveButton(ImageButton button) {
        saveButton = button;
        button.setOnClickListener(v -> {
            if (word != null) {
                repository.saved().toggle(headword());
                refreshSaveButton();
            }
        });
        refreshSaveButton();
    }

    /** Updates the bookmark icon (e.g. after returning to the screen). */
    public void refreshSaveButton() {
        if (saveButton == null) {
            return;
        }
        boolean saved = word != null && repository.saved().contains(headword());
        Views.icon(saveButton, saved ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark,
                Views.color(activity, saved ? R.color.accent : R.color.textSecondary));
        saveButton.setContentDescription(activity.getString(saved ? R.string.unsave : R.string.save));
    }
}
