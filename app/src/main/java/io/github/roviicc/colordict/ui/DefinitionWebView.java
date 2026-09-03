package io.github.roviicc.colordict.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.util.AttributeSet;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.Prefs;

/**
 * The WebView that renders aggregated definitions. Intercepts
 * {@code bword://}-style cross-reference links (and relative links resolved
 * against the local base URL) and turns them into new lookups; external
 * http(s) links open in the browser.
 */
public class DefinitionWebView extends WebView {

    /** Fake base URL so relative cross-reference links become interceptable. */
    private static final String BASE_URL = "https://colordict.local/";

    public interface OnWordLinkListener {
        void onWordLink(String word);
    }

    private OnWordLinkListener wordLinkListener;
    private OnReportListener reportListener;

    public DefinitionWebView(Context context) {
        super(context);
        init();
    }

    public DefinitionWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DefinitionWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        getSettings().setJavaScriptEnabled(false);
        getSettings().setTextZoom(Prefs.textZoom(getContext()));
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setBackgroundColor(night ? 0xFF121212 : 0xFFFAFAFA);
        setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl());
            }
        });
    }

    public void setOnWordLinkListener(OnWordLinkListener listener) {
        wordLinkListener = listener;
    }

    /** Notified when a reader reports a sense as missing a connotation note. */
    public interface OnReportListener {
        void onReport(String sense, String lemma, String gloss, String reason);
    }

    public void setOnReportListener(OnReportListener listener) {
        reportListener = listener;
    }

    /** The path of a scheme-specific URI: "report" out of "colordict:report?x=1". */
    private static String stripQuery(Uri uri) {
        String ssp = uri.getSchemeSpecificPart();
        if (ssp == null) {
            return "";
        }
        int q = ssp.indexOf('?');
        return q < 0 ? ssp : ssp.substring(0, q);
    }

    /**
     * Reads one query parameter. Uri.getQueryParameter() returns null for an
     * opaque URI like colordict:report?..., which has no hierarchical part, so
     * the query is parsed off the scheme-specific part by hand.
     */
    private static String param(Uri uri, String key) {
        String ssp = uri.getSchemeSpecificPart();
        if (ssp == null) {
            return "";
        }
        int q = ssp.indexOf('?');
        if (q < 0) {
            return "";
        }
        for (String pair : ssp.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(key)) {
                return Uri.decode(pair.substring(eq + 1));
            }
        }
        return "";
    }

    /** Renders a complete HTML document built by {@link DefinitionHtml}. */
    public void showPage(String html) {
        getSettings().setTextZoom(Prefs.textZoom(getContext()));
        loadDataWithBaseURL(BASE_URL, html, "text/html", "utf-8", null);
    }

    private boolean handleUrl(Uri uri) {
        if (uri == null) {
            return true;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String url = uri.toString();
        // The empty state is a link: an unannotated sense renders "not recorded -
        // report this word" (tools/dict_build.py), and tapping it lands here.
        // Deliberately a URL handler and an append, nothing more - if reporting
        // ever needs more than this, the fallback is a long-press on the
        // headword rather than a feature.
        if (scheme.equals("colordict") && "report".equals(stripQuery(uri))) {
            if (reportListener != null) {
                reportListener.onReport(
                        param(uri, "sense"), param(uri, "lemma"),
                        param(uri, "gloss"), param(uri, "reason"));
            }
            return true;
        }
        if (scheme.equals("bword") || scheme.equals("entry")) {
            String word = Uri.decode(url.substring(url.indexOf("://") + 3));
            deliverWord(word);
            return true;
        }
        if (url.startsWith(BASE_URL)) {
            // A relative href inside an article, resolved against our base.
            String word = Uri.decode(url.substring(BASE_URL.length()));
            if (!word.isEmpty()) {
                deliverWord(word);
            }
            return true;
        }
        if (scheme.equals("http") || scheme.equals("https")) {
            try {
                getContext().startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getContext(), R.string.no_browser, Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return true; // swallow everything else (audio/image resources, etc.)
    }

    private void deliverWord(String word) {
        String trimmed = word.trim();
        if (wordLinkListener != null && !trimmed.isEmpty()) {
            wordLinkListener.onWordLink(trimmed);
        }
    }
}
