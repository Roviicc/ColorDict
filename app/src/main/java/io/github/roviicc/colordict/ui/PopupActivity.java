package io.github.roviicc.colordict.ui;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.Dictionary;
import io.github.roviicc.colordict.data.Entry;

/**
 * The quick-lookup popup: a bottom sheet over whatever app the user is in.
 *
 * Opened by selecting text and choosing "Define" (ACTION_PROCESS_TEXT), or by
 * other apps through the intent API popularized by ColorDict, which e-book
 * readers already support:
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
public class PopupActivity extends BaseActivity implements EntryRenderer.Listener {

    public static final String EXTRA_QUERY = "EXTRA_QUERY";
    public static final String EXTRA_FULLSCREEN = "EXTRA_FULLSCREEN";
    public static final String EXTRA_WIDTH = "EXTRA_WIDTH";
    public static final String EXTRA_HEIGHT = "EXTRA_HEIGHT";
    public static final String EXTRA_GRAVITY = "EXTRA_GRAVITY";
    public static final String EXTRA_MARGIN_LEFT = "EXTRA_MARGIN_LEFT";
    public static final String EXTRA_MARGIN_TOP = "EXTRA_MARGIN_TOP";
    public static final String EXTRA_MARGIN_RIGHT = "EXTRA_MARGIN_RIGHT";
    public static final String EXTRA_MARGIN_BOTTOM = "EXTRA_MARGIN_BOTTOM";

    private static final int MAX_QUERY = 64;

    private View scrim;
    private LinearLayout sheet;
    private LinearLayout content;
    private MaxHeightScrollView scroll;
    private ImageButton saveButton;
    private View openFull;
    private EntryRenderer renderer;
    private boolean anchoredBottom = true;
    private boolean dismissing;

    private String word;
    private Entry entry;
    private int requestId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String query = extractQuery(intent);
        if (query == null || query.isEmpty()) {
            finish();
            return;
        }
        if (intent.getBooleanExtra(EXTRA_FULLSCREEN, false)) {
            startActivity(WordActivity.intent(this, query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
            return;
        }

        setContentView(R.layout.activity_popup);
        scrim = findViewById(R.id.scrim);
        sheet = findViewById(R.id.sheet);
        content = findViewById(R.id.content);
        scroll = findViewById(R.id.scroll);
        saveButton = findViewById(R.id.saveButton);
        openFull = findViewById(R.id.openFull);
        renderer = new EntryRenderer(this, this);

        styleSheet();
        applyGeometry(intent);

        scrim.setOnClickListener(v -> dismiss());
        findViewById(R.id.closeButton).setOnClickListener(v -> dismiss());
        findViewById(R.id.expandButton).setOnClickListener(v -> openInApp());
        openFull.setOnClickListener(v -> openInApp());
        saveButton.setOnClickListener(v -> {
            dictionary().saved().toggle(savedWord());
            updateSaveIcon();
        });
        enableDragToDismiss(findViewById(R.id.dragArea));

        define(query);
        animateIn();
    }

    static String extractQuery(Intent intent) {
        if (intent == null) {
            return null;
        }
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
        if (q == null) {
            return null;
        }
        // Trim surrounding punctuation from selections like "word," or "(word)".
        q = q.trim().replaceAll("\\s+", " ")
                .replaceAll("^[\\p{Punct}“”‘’«»]+|[\\p{Punct}“”‘’«»]+$", "");
        if (q.length() > MAX_QUERY) {
            q = q.substring(0, MAX_QUERY).trim();
        }
        return q;
    }

    // ---------------------------------------------------------------- lookup

    private void define(String query) {
        final int id = ++requestId;
        word = query;
        entry = null;
        openFull.setVisibility(View.GONE);
        scroll.scrollTo(0, 0);
        updateSaveIcon();
        if (dictionary().cached(query) == null) {
            renderer.renderLoading(content, query, true);
        }
        dictionary().define(query, new Dictionary.Callback() {
            @Override
            public void onEntry(Entry e) {
                if (id != requestId || isDestroyed()) {
                    return;
                }
                entry = e;
                renderer.renderEntry(content, e, true);
                openFull.setVisibility(e.hasSenses() ? View.VISIBLE : View.GONE);
                updateSaveIcon();
            }

            @Override
            public void onError(String message, boolean retryable) {
                if (id != requestId || isDestroyed()) {
                    return;
                }
                renderer.renderError(content, query, message, retryable, () -> define(query));
            }
        });
    }

    private String savedWord() {
        return entry != null && entry.hasSenses() && !entry.word.isEmpty() ? entry.word : word;
    }

    private void updateSaveIcon() {
        boolean saved = word != null && dictionary().saved().contains(savedWord());
        Ui.icon(saveButton, saved ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark,
                Ui.color(this, saved ? R.color.accent : R.color.textSecondary));
        saveButton.setContentDescription(getString(saved ? R.string.unsave : R.string.save));
    }

    private void openInApp() {
        startActivity(WordActivity.intent(this, savedWord()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    public void onWordTapped(String tapped) {
        define(tapped);
    }

    @Override
    public void onSpeak(String w) {
        speak(w);
    }

    // ---------------------------------------------------------------- layout

    private void styleSheet() {
        View handle = findViewById(R.id.handle);
        handle.setBackground(Ui.rounded(Ui.color(this, R.color.outline), Ui.dp(this, 2)));
        LinearLayout openFullBox = (LinearLayout) openFull;
        float r = Ui.dp(this, 999);
        openFullBox.setBackground(Ui.pressable(this,
                Ui.rounded(Ui.color(this, R.color.surfaceMuted), r), r));
        ((ImageView) findViewById(R.id.openFullChevron)).setImageTintList(
                android.content.res.ColorStateList.valueOf(Ui.color(this, R.color.textPrimary)));
    }

    /** Honors the caller's requested size, gravity and margins. */
    private void applyGeometry(Intent intent) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int gravity = intent.getIntExtra(EXTRA_GRAVITY, Gravity.BOTTOM);
        int vertical = gravity & Gravity.VERTICAL_GRAVITY_MASK;
        anchoredBottom = vertical == Gravity.BOTTOM || vertical == 0;
        int width = intent.getIntExtra(EXTRA_WIDTH, 0);
        int height = intent.getIntExtra(EXTRA_HEIGHT, 0);

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sheet.getLayoutParams();
        lp.gravity = anchoredBottom
                ? (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) | Gravity.BOTTOM
                : gravity;
        if (width > 0) {
            lp.width = Math.min(width, dm.widthPixels);
        } else if (!anchoredBottom) {
            lp.width = Math.min(dm.widthPixels - Ui.dp(this, 32), Ui.dp(this, 520));
        }
        if (lp.gravity == Gravity.BOTTOM && width <= 0) {
            lp.width = Math.min(dm.widthPixels, Ui.dp(this, 640));
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        }
        lp.leftMargin = intent.getIntExtra(EXTRA_MARGIN_LEFT, 0);
        lp.rightMargin = intent.getIntExtra(EXTRA_MARGIN_RIGHT, 0);
        lp.topMargin = intent.getIntExtra(EXTRA_MARGIN_TOP, 0);
        lp.bottomMargin = intent.getIntExtra(EXTRA_MARGIN_BOTTOM, 0);
        sheet.setLayoutParams(lp);

        // Rounded top corners for a docked sheet; a floating card elsewhere.
        float r = Ui.dp(this, 24);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.color(this, R.color.surface));
        bg.setCornerRadii(anchoredBottom && lp.bottomMargin == 0
                ? new float[]{r, r, r, r, 0, 0, 0, 0}
                : new float[]{r, r, r, r, r, r, r, r});
        sheet.setBackground(bg);
        sheet.setClipToOutline(true);

        int maxSheet = height > 0 ? Math.min(height, dm.heightPixels) : dm.heightPixels * 62 / 100;
        View root = findViewById(R.id.root);
        Ui.onInsets(root, (l, t, rt, b) -> {
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) sheet.getLayoutParams();
            int base = intent.getIntExtra(EXTRA_MARGIN_BOTTOM, 0);
            int topBase = intent.getIntExtra(EXTRA_MARGIN_TOP, 0);
            if (anchoredBottom && base == 0) {
                // Docked: extend the sheet behind the navigation bar.
                sheet.setPadding(0, 0, 0, b);
            } else {
                p.bottomMargin = base + b + (anchoredBottom ? 0 : Ui.dp(this, 16));
            }
            if (!anchoredBottom) {
                p.topMargin = topBase + t + Ui.dp(this, 16);
            }
            sheet.setLayoutParams(p);
            // Header + footer take about 130dp; the rest scrolls.
            int chrome = Ui.dp(this, 130) + b;
            int fixed = height > 0 ? maxSheet - chrome : -1;
            scroll.setMaxHeight(Math.max(Ui.dp(this, 120),
                    Math.min(maxSheet, dm.heightPixels - t - Ui.dp(this, 48)) - chrome));
            if (fixed > 0) {
                scroll.getLayoutParams().height = fixed;
            }
        });
    }

    // ------------------------------------------------------------ animations

    private void animateIn() {
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(200).start();
        sheet.setVisibility(View.INVISIBLE);
        sheet.post(() -> {
            float from = anchoredBottom ? sheet.getHeight() : -Ui.dp(this, 24);
            sheet.setTranslationY(from);
            sheet.setAlpha(anchoredBottom ? 1f : 0f);
            sheet.setVisibility(View.VISIBLE);
            sheet.animate().translationY(0).alpha(1f).setDuration(260)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        });
    }

    private void dismiss() {
        if (dismissing) {
            return;
        }
        dismissing = true;
        scrim.animate().alpha(0f).setDuration(180).start();
        float to = anchoredBottom ? sheet.getHeight() + Ui.dp(this, 16) : -Ui.dp(this, 24);
        sheet.animate().translationY(to).alpha(anchoredBottom ? 1f : 0f).setDuration(200)
                .withEndAction(() -> {
                    finish();
                    overridePendingTransition(0, 0);
                }).start();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        dismiss();
    }

    /** Swipe the header down to close a bottom sheet. */
    private void enableDragToDismiss(View dragArea) {
        dragArea.setOnTouchListener(new View.OnTouchListener() {
            private float startY;
            private VelocityTracker velocity;

            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                if (!anchoredBottom) {
                    return false;
                }
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = ev.getRawY();
                        velocity = VelocityTracker.obtain();
                        velocity.addMovement(ev);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (velocity != null) {
                            velocity.addMovement(ev);
                        }
                        sheet.setTranslationY(Math.max(0, ev.getRawY() - startY));
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        float vy = 0;
                        if (velocity != null) {
                            velocity.addMovement(ev);
                            velocity.computeCurrentVelocity(1000);
                            vy = velocity.getYVelocity();
                            velocity.recycle();
                            velocity = null;
                        }
                        float dy = sheet.getTranslationY();
                        if (dy > sheet.getHeight() * 0.25f || vy > Ui.dp(PopupActivity.this, 1000)) {
                            dismiss();
                        } else {
                            sheet.animate().translationY(0).setDuration(180).start();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }
}
