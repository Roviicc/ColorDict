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
