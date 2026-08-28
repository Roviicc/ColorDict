package io.github.roviicc.colordict.engine;

import java.util.List;

/**
 * Builds the color-coded definition page: one card per dictionary, tagged
 * with that dictionary's label color — the look that gives the app its name.
 *
 * <p>Pure Java with no Android imports, so the Android WebView and the
 * desktop harness render results from the same code.
 */
public final class DefinitionRenderer {

    /** One article inside a dictionary's card. */
    public static final class Entry {
        public final String headword;
        /** Article body, already converted to HTML by {@link ArticleHtml}. */
        public final String html;

        public Entry(String headword, String html) {
            this.headword = headword;
            this.html = html;
        }
    }

    /** One dictionary's contribution to the page. */
    public static final class Section {
        public final String title;
        public final int color;
        public final List<Entry> entries;

        public Section(String title, int color, List<Entry> entries) {
            this.title = title;
            this.color = color;
            this.entries = entries;
        }
    }

    /** Localized display strings, supplied by the caller. */
    public static final class Labels {
        /** Already-formatted "No results for X" message. */
        public final String noResults;
        public final String similarWords;

        public Labels(String noResults, String similarWords) {
            this.noResults = noResults;
            this.similarWords = similarWords;
        }
    }

    private DefinitionRenderer() {
    }

    public static String hexColor(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    /** The stylesheet used by the Android WebView. */
    public static String defaultCss(boolean night) {
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

    /**
     * Renders a complete HTML document.
     *
     * @param css      stylesheet to inline (see {@link #defaultCss})
     * @param sections one per dictionary that had a hit; empty for "no results"
     * @param similar  alphabetical neighbours offered when {@code sections} is empty
     */
    public static String page(String css, List<Section> sections, List<String> similar,
                              Labels labels) {
        StringBuilder sb = new StringBuilder(
                "<!doctype html><html><head><meta charset=\"utf-8\">"
                        + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                        + "<style>" + css + "</style></head><body>");
        if (sections.isEmpty()) {
            sb.append("<div class=\"note\">").append(ArticleHtml.escape(labels.noResults))
                    .append("</div>");
            if (!similar.isEmpty()) {
                sb.append("<div class=\"note\">")
                        .append(ArticleHtml.escape(labels.similarWords))
                        .append("</div><div>");
                for (String w : similar) {
                    sb.append("<a class=\"simword\" href=\"bword://")
                            .append(ArticleHtml.hrefEncode(w)).append("\">")
                            .append(ArticleHtml.escape(w)).append("</a>");
                }
                sb.append("</div>");
            }
        } else {
            for (Section section : sections) {
                String hex = hexColor(section.color);
                sb.append("<div class=\"card\" style=\"border-left-color:").append(hex)
                        .append("\"><div class=\"dictname\" style=\"color:").append(hex)
                        .append("\">").append(ArticleHtml.escape(section.title))
                        .append("</div>");
                for (int i = 0; i < section.entries.size(); i++) {
                    if (i > 0) {
                        sb.append("<hr class=\"sep\">");
                    }
                    Entry e = section.entries.get(i);
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
