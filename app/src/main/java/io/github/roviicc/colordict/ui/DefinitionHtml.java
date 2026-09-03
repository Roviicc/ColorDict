package io.github.roviicc.colordict.ui;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.DictRepository;
import io.github.roviicc.colordict.engine.DefinitionRenderer;

/**
 * Adapts a {@link DictRepository.DefineResult} to {@link DefinitionRenderer},
 * supplying the localized strings from resources.
 */
public final class DefinitionHtml {

    private DefinitionHtml() {
    }

    /** The aggregated result page for a define query. */
    public static String page(Context context, DictRepository.DefineResult result,
                              boolean night) {
        List<DefinitionRenderer.Section> sections = new ArrayList<>(result.hits.size());
        for (DictRepository.DictHit hit : result.hits) {
            List<DefinitionRenderer.Entry> entries = new ArrayList<>(hit.entries.size());
            for (DictRepository.RenderedEntry e : hit.entries) {
                entries.add(new DefinitionRenderer.Entry(e.headword, e.html, e.formLine));
            }
            sections.add(new DefinitionRenderer.Section(
                    hit.dict.name(), hit.dict.color, entries));
        }
        DefinitionRenderer.Labels labels = new DefinitionRenderer.Labels(
                context.getString(R.string.no_results_for, result.word),
                context.getString(R.string.similar_words));
        return DefinitionRenderer.page(DefinitionRenderer.defaultCss(night),
                sections, result.similar, labels);
    }
}
