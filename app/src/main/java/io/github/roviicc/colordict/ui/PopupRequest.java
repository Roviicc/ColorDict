package io.github.roviicc.colordict.ui;

import android.content.Intent;
import android.view.Gravity;

/**
 * What the caller asked the popup for. Supports the intent API popularized
 * by ColorDict, which e-book readers already use, plus "Define" on selected
 * text (ACTION_PROCESS_TEXT) and plain shared text:
 *
 * <pre>
 *   action  colordict.intent.action.SEARCH
 *   extras  EXTRA_QUERY       String   word to look up
 *           EXTRA_FULLSCREEN  boolean  open the full entry instead of a popup
 *           EXTRA_WIDTH / EXTRA_HEIGHT           popup size in px
 *           EXTRA_GRAVITY                        android.view.Gravity value
 *           EXTRA_MARGIN_LEFT / _TOP / _RIGHT / _BOTTOM  offsets in px
 * </pre>
 */
final class PopupRequest {

    static final String EXTRA_QUERY = "EXTRA_QUERY";
    static final String EXTRA_FULLSCREEN = "EXTRA_FULLSCREEN";
    static final String EXTRA_WIDTH = "EXTRA_WIDTH";
    static final String EXTRA_HEIGHT = "EXTRA_HEIGHT";
    static final String EXTRA_GRAVITY = "EXTRA_GRAVITY";
    static final String EXTRA_MARGIN_LEFT = "EXTRA_MARGIN_LEFT";
    static final String EXTRA_MARGIN_TOP = "EXTRA_MARGIN_TOP";
    static final String EXTRA_MARGIN_RIGHT = "EXTRA_MARGIN_RIGHT";
    static final String EXTRA_MARGIN_BOTTOM = "EXTRA_MARGIN_BOTTOM";

    private static final int MAX_QUERY = 64;

    /** The word to look up, or null when the intent carried none. */
    final String query;
    final boolean fullscreen;
    /** Requested size in px; 0 means "use the default". */
    final int width;
    final int height;
    final int gravity;
    final int marginLeft;
    final int marginTop;
    final int marginRight;
    final int marginBottom;

    private PopupRequest(Intent intent) {
        query = cleanQuery(rawQuery(intent));
        fullscreen = intent.getBooleanExtra(EXTRA_FULLSCREEN, false);
        width = intent.getIntExtra(EXTRA_WIDTH, 0);
        height = intent.getIntExtra(EXTRA_HEIGHT, 0);
        gravity = intent.getIntExtra(EXTRA_GRAVITY, Gravity.BOTTOM);
        marginLeft = intent.getIntExtra(EXTRA_MARGIN_LEFT, 0);
        marginTop = intent.getIntExtra(EXTRA_MARGIN_TOP, 0);
        marginRight = intent.getIntExtra(EXTRA_MARGIN_RIGHT, 0);
        marginBottom = intent.getIntExtra(EXTRA_MARGIN_BOTTOM, 0);
    }

    static PopupRequest from(Intent intent) {
        return new PopupRequest(intent != null ? intent : new Intent());
    }

    /** Docked to the bottom edge (the default) rather than floating as a card. */
    boolean anchoredBottom() {
        int vertical = gravity & Gravity.VERTICAL_GRAVITY_MASK;
        return vertical == Gravity.BOTTOM || vertical == 0;
    }

    private static String rawQuery(Intent intent) {
        String q = intent.getStringExtra(EXTRA_QUERY);
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
        return q;
    }

    /** Trims whitespace and surrounding punctuation from selections like "word," or "(word)". */
    static String cleanQuery(String q) {
        if (q == null) {
            return null;
        }
        q = q.trim().replaceAll("\\s+", " ")
                .replaceAll("^[\\p{Punct}“”‘’«»]+|[\\p{Punct}“”‘’«»]+$", "");
        if (q.length() > MAX_QUERY) {
            q = q.substring(0, MAX_QUERY).trim();
        }
        return q.isEmpty() ? null : q;
    }
}
