package io.github.roviicc.colordict.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.Prefs;

/**
 * The floating lookup window. Implements the intent API popularized by
 * ColorDict, so e-book readers and other apps can request an in-place
 * definition popup:
 *
 * <pre>
 *   action  colordict.intent.action.SEARCH
 *   extras  EXTRA_QUERY       String  word to look up
 *           EXTRA_FULLSCREEN  boolean open the full app instead of a popup
 *           EXTRA_WIDTH / EXTRA_HEIGHT          window size in px
 *           EXTRA_GRAVITY                       android.view.Gravity value
 *           EXTRA_MARGIN_LEFT / _TOP / _RIGHT / _BOTTOM  offsets in px
 * </pre>
 *
 * Also registered for ACTION_PROCESS_TEXT, so selected text anywhere gets a
 * "define" option that opens this popup.
 */
public class PopupActivity extends BaseActivity {

    public static final String EXTRA_FULLSCREEN = "EXTRA_FULLSCREEN";
    public static final String EXTRA_HEIGHT = "EXTRA_HEIGHT";
    public static final String EXTRA_WIDTH = "EXTRA_WIDTH";
    public static final String EXTRA_GRAVITY = "EXTRA_GRAVITY";
    public static final String EXTRA_MARGIN_LEFT = "EXTRA_MARGIN_LEFT";
    public static final String EXTRA_MARGIN_TOP = "EXTRA_MARGIN_TOP";
    public static final String EXTRA_MARGIN_RIGHT = "EXTRA_MARGIN_RIGHT";
    public static final String EXTRA_MARGIN_BOTTOM = "EXTRA_MARGIN_BOTTOM";

    private TextView popupWord;
    private DefinitionWebView webView;
    private String currentWord;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String query = extractQuery(intent);

        if (intent != null && intent.getBooleanExtra(EXTRA_FULLSCREEN, false)) {
            Intent full = new Intent(this, MainActivity.class)
                    .setAction(MainActivity.ACTION_COLORDICT_SEARCH)
                    .putExtra(MainActivity.EXTRA_QUERY, query)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(full);
            finish();
            return;
        }

        setContentView(R.layout.activity_popup);
        popupWord = findViewById(R.id.popupWord);
        webView = findViewById(R.id.popupWebView);
        ImageButton expand = findViewById(R.id.popupExpandButton);
        ImageButton close = findViewById(R.id.popupCloseButton);

        webView.setOnWordLinkListener(this::define);
        webView.setOnReportListener(this::onReportWord);
        close.setOnClickListener(v -> finish());
        expand.setOnClickListener(v -> {
            if (currentWord != null) {
                Intent full = new Intent(this, MainActivity.class)
                        .setAction(MainActivity.ACTION_COLORDICT_SEARCH)
                        .putExtra(MainActivity.EXTRA_QUERY, currentWord)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(full);
            }
            finish();
        });

        applyWindowSpec(intent);

        if (query == null || query.isEmpty()) {
            finish();
            return;
        }
        define(query);
    }

    private String extractQuery(Intent intent) {
        if (intent == null) {
            return null;
        }
        String q = intent.getStringExtra(MainActivity.EXTRA_QUERY);
        if (q == null && Intent.ACTION_PROCESS_TEXT.equals(intent.getAction())) {
            CharSequence cs = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            q = cs == null ? null : cs.toString();
        }
        if (q == null) {
            q = intent.getStringExtra(Intent.EXTRA_TEXT);
        }
        if (q == null) {
            q = intent.getStringExtra("query");
        }
        if (q != null) {
            q = q.trim();
            if (q.length() > 128) {
                q = q.substring(0, 128).trim();
            }
        }
        return q;
    }

    /** Applies the caller-requested window geometry, with sensible defaults. */
    private void applyWindowSpec(Intent intent) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int defaultWidth = dm.widthPixels * 94 / 100;
        int defaultHeight = dm.heightPixels * 45 / 100;

        WindowManager.LayoutParams lp = getWindow().getAttributes();
        int width = intent != null ? intent.getIntExtra(EXTRA_WIDTH, 0) : 0;
        int height = intent != null ? intent.getIntExtra(EXTRA_HEIGHT, 0) : 0;
        lp.width = width > 0 ? Math.min(width, dm.widthPixels) : defaultWidth;
        lp.height = height > 0 ? Math.min(height, dm.heightPixels) : defaultHeight;
        lp.gravity = intent != null
                ? intent.getIntExtra(EXTRA_GRAVITY, Gravity.BOTTOM) : Gravity.BOTTOM;

        if (intent != null) {
            int left = intent.getIntExtra(EXTRA_MARGIN_LEFT, 0);
            int top = intent.getIntExtra(EXTRA_MARGIN_TOP, 0);
            int right = intent.getIntExtra(EXTRA_MARGIN_RIGHT, 0);
            int bottom = intent.getIntExtra(EXTRA_MARGIN_BOTTOM, 0);
            if ((lp.gravity & Gravity.LEFT) == Gravity.LEFT) {
                lp.x = left;
            } else if ((lp.gravity & Gravity.RIGHT) == Gravity.RIGHT) {
                lp.x = right;
            }
            if ((lp.gravity & Gravity.TOP) == Gravity.TOP) {
                lp.y = top;
            } else if ((lp.gravity & Gravity.BOTTOM) == Gravity.BOTTOM) {
                lp.y = bottom;
            }
        }
        getWindow().setAttributes(lp);
    }

    private void define(String word) {
        currentWord = word;
        popupWord.setText(word);
        repo().define(word, result -> {
            if (!word.equals(currentWord) || isFinishing()) {
                return;
            }
            webView.showPage(DefinitionHtml.page(this, result, isNightMode()));
            if (!result.hits.isEmpty()) {
                repo().history().addHistory(word, Prefs.historyLimit(this));
            }
        });
    }

    /**
     * A reader tapped "not recorded - report this word". Append it locally and
     * confirm; nothing is sent anywhere, now or later, without an explicit
     * export from Settings.
     */
    private void onReportWord(String sense, String lemma, String gloss, String reason) {
        io.github.roviicc.colordict.data.ReportLog log =
                new io.github.roviicc.colordict.data.ReportLog(this);
        if (log.contains(sense, lemma)) {
            android.widget.Toast.makeText(this,
                    getString(R.string.report_already, lemma),
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        boolean ok = log.add(sense, lemma, gloss, reason);
        android.widget.Toast.makeText(this,
                ok ? getString(R.string.report_added, lemma)
                   : getString(R.string.report_failed),
                android.widget.Toast.LENGTH_SHORT).show();
    }

}
