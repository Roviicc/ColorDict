package io.github.roviicc.colordict.ui;

import android.content.Context;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.DictRepository;
import io.github.roviicc.colordict.data.Palette;
import io.github.roviicc.colordict.engine.ArticleHtml;

/**
 * Builds the full HTML document shown in the definition WebView: one card
 * per dictionary, each tagged with the dictionary's label color — the look
 * that gives the app its name.
 */
public final class DefinitionHtml {

    private DefinitionHtml() {
    }

    private static String css(boolean night) {
        String bg = night ? "#121212" : "#FAFAFA";
        String fg = night ? "#E4E4E4" : "#1F2328";
        String card = night ? "#1E1E1E" : "#FFFFFF";
        String line = night ? "#3A3A3A" : "#E0E0E0";
        String link = night ? "#82B1FF" : "#1565C0";
        String muted = night ? "#9E9E9E" : "#6A6F75";
        String example = night ? "#A5D6A7" : "#2E7D32";
        return "body{margin:8px;background:" + bg + ";color:" + fg
                + ";font-family:sans-serif;word-wrap:break-word}"
                + "a{color:" + link + ";text-decoration:none}"
                + ".card{background:" + card + ";border:1px solid " + line
                + ";border-left-width:6px;border-left-style:solid;border-radius:10px;"
                + "padding:10px 12px;margin:0 0 12px 0}"
                + ".dictname{font-size:78%;font-weight:bold;letter-spacing:.06em;"
                + "text-transform:uppercase;margin-bottom:4px}"
                + ".hw{font-size:117%;font-weight:bold;margin:2px 0 6px 0}"
                + "hr.sep{border:none;border-top:1px dashed " + line + ";margin:10px 0}"
                + ".phon{color:" + muted + "}"
                + ".xex{color:" + example + ";font-style:italic}"
                + ".xabr{font-style:italic;color:" + muted + "}"
                + ".xk{font-weight:bold}"
                + ".res{color:" + muted + ";font-size:90%}"
                + ".wn{white-space:pre-wrap;font-family:sans-serif}"
                + ".note{color:" + muted + ";margin:16px 4px}"
                + ".simword{display:inline-block;margin:4px 6px 4px 0;padding:6px 12px;"
                + "background:" + card + ";border:1px solid " + line + ";border-radius:16px}"
                + "img{max-width:100%;height:auto}"
                + "pre{overflow-x:auto}";
    }

    private static String head(boolean night) {
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>" + css(night) + "</style></head><body>";
    }

    /** The aggregated result page for a define query. */
    public static String page(Context context, DictRepository.DefineResult result,
                              boolean night) {
        StringBuilder sb = new StringBuilder(head(night));
        if (result.hits.isEmpty()) {
            sb.append("<div class=\"note\">")
                    .append(ArticleHtml.escape(context.getString(
                            R.string.no_results_for, result.word)))
                    .append("</div>");
            if (!result.similar.isEmpty()) {
                sb.append("<div class=\"note\">")
                        .append(ArticleHtml.escape(context.getString(R.string.similar_words)))
                        .append("</div><div>");
                for (String w : result.similar) {
                    sb.append("<a class=\"simword\" href=\"bword://")
                            .append(ArticleHtml.hrefEncode(w)).append("\">")
                            .append(ArticleHtml.escape(w)).append("</a>");
                }
                sb.append("</div>");
            }
        } else {
            for (DictRepository.DictHit hit : result.hits) {
                String hex = Palette.hex(hit.dict.color);
                sb.append("<div class=\"card\" style=\"border-left-color:").append(hex)
                        .append("\"><div class=\"dictname\" style=\"color:").append(hex)
                        .append("\">").append(ArticleHtml.escape(hit.dict.name()))
                        .append("</div>");
                for (int i = 0; i < hit.entries.size(); i++) {
                    if (i > 0) {
                        sb.append("<hr class=\"sep\">");
                    }
                    DictRepository.RenderedEntry e = hit.entries.get(i);
                    sb.append("<div class=\"hw\">").append(ArticleHtml.escape(e.headword))
                            .append("</div><div class=\"body\">").append(e.html)
                            .append("</div>");
                }
                sb.append("</div>");
            }
        }
        return sb.append("</body></html>").toString();
    }
}
