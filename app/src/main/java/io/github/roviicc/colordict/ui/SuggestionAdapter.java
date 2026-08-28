package io.github.roviicc.colordict.ui;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.DictRepository;

/**
 * Rows for the main list: either live suggestions (word + one colored dot
 * per dictionary that has it) or recent-history entries (clock icon).
 */
public final class SuggestionAdapter extends BaseAdapter {

    private static final class Row {
        final String word;
        final List<Integer> colors; // null for history rows

        Row(String word, List<Integer> colors) {
            this.word = word;
            this.colors = colors;
        }
    }

    private final LayoutInflater inflater;
    private final List<Row> rows = new ArrayList<>();
    private boolean historyMode;

    public SuggestionAdapter(Context context) {
        inflater = LayoutInflater.from(context);
    }

    public void setSuggestions(List<DictRepository.Suggestion> suggestions) {
        rows.clear();
        historyMode = false;
        for (DictRepository.Suggestion s : suggestions) {
            rows.add(new Row(s.word, s.colors));
        }
        notifyDataSetChanged();
    }

    public void setHistory(List<String> words) {
        rows.clear();
        historyMode = true;
        for (String w : words) {
            rows.add(new Row(w, null));
        }
        notifyDataSetChanged();
    }

    public boolean isHistoryMode() {
        return historyMode;
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public String getItem(int position) {
        return rows.get(position).word;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView != null
                ? convertView : inflater.inflate(R.layout.item_word_row, parent, false);
        Row row = rows.get(position);

        TextView word = view.findViewById(R.id.rowWord);
        word.setText(row.word);
        word.setCompoundDrawablesRelativeWithIntrinsicBounds(
                row.colors == null ? R.drawable.ic_history : 0, 0, 0, 0);

        TextView dots = view.findViewById(R.id.rowDots);
        if (row.colors == null || row.colors.isEmpty()) {
            dots.setText("");
        } else {
            SpannableStringBuilder sb = new SpannableStringBuilder();
            for (int color : row.colors) {
                int start = sb.length();
                sb.append("●");
                sb.setSpan(new ForegroundColorSpan(color), start, sb.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            dots.setText(sb);
        }
        return view;
    }
}
