package io.github.roviicc.colordict.engine;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders parsed article blocks to a common HTML fragment, so every StarDict
 * content type ends up displayable in one WebView. Cross-references become
 * {@code bword://<word>} links, matching the convention used by StarDict
 * HTML dictionaries.
 */
public final class ArticleHtml {

    /** Tags allowed to survive xdxf/pango conversion. */
    private static final Pattern NON_WHITELIST_TAG = Pattern.compile(
            "(?is)</?(?!(?:b|i|u|s|em|strong|code|sub|sup|big|small|br|hr|p|div|span|a|blockquote|pre|ul|ol|li|font)\\b)[a-zA-Z][^>]*>");

    private static final Pattern XDXF_K = Pattern.compile("(?is)<k>(.*?)</k>");
    private static final Pattern XDXF_KREF = Pattern.compile("(?is)<kref[^>]*>(.*?)</kref>");
    private static final Pattern XDXF_C_COLOR =
            Pattern.compile("(?is)<c\\s+c=[\"']?([#a-zA-Z0-9]+)[\"']?\\s*>");
    private static final Pattern XDXF_RREF = Pattern.compile("(?is)<rref[^>]*>(.*?)</rref>");
    private static final Pattern PANGO_SPAN = Pattern.compile("(?is)<span([^>]*)>");
    private static final Pattern PANGO_FOREGROUND =
            Pattern.compile("(?is)(?:foreground|color)=[\"']([^\"']+)[\"']");
    private static final Pattern WIKI_BOLD = Pattern.compile("'''(.+?)'''");
    private static final Pattern WIKI_ITALIC = Pattern.compile("''(.+?)''");
    private static final Pattern WIKI_LINK = Pattern.compile("\\[\\[([^\\]|]+)(?:\\|([^\\]]+))?\\]\\]");

    private ArticleHtml() {
    }

    public static String render(Article article) {
        StringBuilder sb = new StringBuilder();
        for (Article.Block block : article.blocks) {
            String html = renderBlock(block);
            if (!html.isEmpty()) {
                sb.append("<div class=\"blk blk-").append(block.type).append("\">")
                        .append(html).append("</div>\n");
            }
        }
        return sb.toString();
    }

    public static String renderBlock(Article.Block block) {
        switch (block.type) {
            case 'm':
            case 'l':
            case 'y':
            case 'k':
                return textToHtml(block.text());
            case 't':
                return "<span class=\"phon\">/" + escape(block.text()) + "/</span>";
            case 'g':
                return pangoToHtml(block.text());
            case 'x':
                return xdxfToHtml(block.text());
            case 'h':
                return block.text();
            case 'w':
                return wikiToHtml(block.text());
            case 'n':
                return "<pre class=\"wn\">" + escape(block.text()) + "</pre>";
            case 'r':
                return resourcesToHtml(block.text());
            case 'W':
                return "<span class=\"res\">[audio]</span>";
            case 'P':
                return "<span class=\"res\">[image]</span>";
            default:
                return "<span class=\"res\">[unsupported block type '"
                        + escape(String.valueOf(block.type)) + "']</span>";
        }
    }

    public static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String textToHtml(String s) {
        return escape(s).replace("\n", "<br>");
    }

    /** Percent-encodes a word for use inside a {@code bword://} href. */
    public static String hrefEncode(String word) {
        StringBuilder sb = new StringBuilder();
        for (byte b : word.getBytes(StandardCharsets.UTF_8)) {
            int v = b & 0xFF;
            boolean safe = (v >= 'A' && v <= 'Z') || (v >= 'a' && v <= 'z')
                    || (v >= '0' && v <= '9') || v == '-' || v == '_' || v == '.' || v == '~';
            if (safe) {
                sb.append((char) v);
            } else {
                sb.append('%').append(String.format("%02X", v));
            }
        }
        return sb.toString();
    }

    public static String bwordLink(String word, String label) {
        return "<a href=\"bword://" + hrefEncode(word) + "\">" + escape(label) + "</a>";
    }

    static String xdxfToHtml(String s) {
        s = s.replaceAll("(?s)<\\?.*?\\?>", "");
        s = XDXF_K.matcher(s).replaceAll("<div class=\"xk\">$1</div>");
        s = s.replaceAll("(?is)<tr>", "<span class=\"phon\">[")
                .replaceAll("(?is)</tr>", "]</span>");
        s = XDXF_C_COLOR.matcher(s).replaceAll("<span style=\"color:$1\">");
        s = s.replaceAll("(?is)<c>", "<span class=\"xc\">")
                .replaceAll("(?is)</c>", "</span>");
        s = s.replaceAll("(?is)<abr>|<abbr>", "<span class=\"xabr\">")
                .replaceAll("(?is)</abr>|</abbr>", "</span>");
        s = s.replaceAll("(?is)<ex>", "<span class=\"xex\">")
                .replaceAll("(?is)</ex>", "</span>");
        s = replaceWithLinks(XDXF_KREF, s);
        s = XDXF_RREF.matcher(s).replaceAll("<span class=\"res\">[resource: $1]</span>");
        return NON_WHITELIST_TAG.matcher(s).replaceAll("");
    }

    /** Replaces every match's inner text with a bword link to that text. */
    private static String replaceWithLinks(Pattern pattern, String s) {
        Matcher m = pattern.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String word = m.group(1).replaceAll("(?s)<[^>]*>", "").trim();
            m.appendReplacement(sb, Matcher.quoteReplacement(bwordLink(word, word)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String pangoToHtml(String s) {
        Matcher m = PANGO_SPAN.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String attrs = m.group(1);
            Matcher fg = PANGO_FOREGROUND.matcher(attrs);
            String repl = fg.find()
                    ? "<span style=\"color:" + fg.group(1) + "\">" : "<span>";
            m.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        m.appendTail(sb);
        s = sb.toString().replaceAll("(?is)<tt>", "<code>").replaceAll("(?is)</tt>", "</code>");
        s = NON_WHITELIST_TAG.matcher(s).replaceAll("");
        return s.replace("\n", "<br>");
    }

    static String wikiToHtml(String s) {
        // Escape everything except apostrophes — they ARE the wiki markup.
        s = s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
        s = WIKI_BOLD.matcher(s).replaceAll("<b>$1</b>");
        s = WIKI_ITALIC.matcher(s).replaceAll("<i>$1</i>");
        Matcher m = WIKI_LINK.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String target = m.group(1).trim();
            String label = m.group(2) != null ? m.group(2).trim() : target;
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    "<a href=\"bword://" + hrefEncode(target) + "\">" + label + "</a>"));
        }
        m.appendTail(sb);
        return sb.toString().replace("\n", "<br>");
    }

    private static String resourcesToHtml(String s) {
        StringBuilder sb = new StringBuilder();
        for (String line : s.split("\n")) {
            if (!line.trim().isEmpty()) {
                sb.append("<div class=\"res\">[resource: ").append(escape(line.trim()))
                        .append("]</div>");
            }
        }
        return sb.toString();
    }
}
