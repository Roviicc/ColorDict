package io.github.roviicc.colordict.ui;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.Entry;
import io.github.roviicc.colordict.ui.entry.EntryController;
import io.github.roviicc.colordict.ui.widget.DragToDismiss;
import io.github.roviicc.colordict.ui.widget.MaxHeightScrollView;
import io.github.roviicc.colordict.ui.widget.Views;

/**
 * The quick-lookup popup: a bottom sheet (or floating card) over whatever app
 * the user is in. See {@link PopupRequest} for how other apps open it.
 *
 * A docked sheet opens short, with the top meanings. Scrolling or dragging up
 * expands it to nearly full screen with the full entry, still over the other
 * app; dragging the handle down shrinks it again, then closes it. The entry
 * ends with a "See full entry" link to the word page.
 */
public class PopupActivity extends BaseActivity implements EntryController.Host {

    /** Height of the handle and header row above the entry. */
    private static final int HEADER_DP = 64;
    /** Scrim left visible above an expanded sheet, so it still reads as a popup. */
    private static final int EXPANDED_GAP_DP = 24;

    private View root;
    private View scrim;
    private LinearLayout sheet;
    private MaxHeightScrollView scroll;
    private EntryController entry;
    private PopupRequest request;
    private boolean expanded;
    private boolean dismissing;
    private int insetTop;
    private int insetBottom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        request = PopupRequest.from(getIntent());
        if (request.query == null) {
            finish();
            return;
        }
        if (request.fullscreen) {
            startActivity(WordActivity.intent(this, request.query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
            return;
        }

        setContentView(R.layout.activity_popup);
        root = findViewById(R.id.root);
        scrim = findViewById(R.id.scrim);
        sheet = findViewById(R.id.sheet);
        scroll = findViewById(R.id.scroll);
        entry = new EntryController(this, repository(), findViewById(R.id.content), scroll, true, this);
        entry.showFullEntryLink(true);
        entry.bindSaveButton(findViewById(R.id.saveButton));

        findViewById(R.id.handle).setBackground(
                Views.rounded(Views.color(this, R.color.outline), Views.dp(this, 2)));
        applyGeometry();

        scrim.setOnClickListener(v -> dismiss());
        findViewById(R.id.closeButton).setOnClickListener(v -> dismiss());
        findViewById(R.id.expandButton).setOnClickListener(v -> onOpenFullEntry());
        if (request.anchoredBottom()) {
            DragToDismiss.attach(findViewById(R.id.dragArea), sheet, this::onDraggedDown, this::expand);
            scroll.setOnPullUpListener(this::expand);
        }

        entry.load(request.query);
        animateIn();
    }

    // ------------------------------------------------------- EntryController.Host

    @Override
    public void onEntryShown(Entry e) {
    }

    @Override
    public void onWordTapped(String word) {
        entry.load(word);
    }

    @Override
    public void onOpenFullEntry() {
        startActivity(WordActivity.intent(this, entry.headword()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    public void openSettings() {
        super.openSettings();
        finish();
    }

    // ------------------------------------------------------- expand / collapse

    /** Grows the sheet to nearly full screen and shows the full entry. */
    private void expand() {
        if (expanded || dismissing) {
            return;
        }
        expanded = true;
        scroll.setOnPullUpListener(null);
        animateResize();
        entry.setCompact(false);
        applyHeights();
    }

    private void collapse() {
        expanded = false;
        scroll.setOnPullUpListener(this::expand);
        sheet.animate().translationY(0).setDuration(180).start();
        animateResize();
        entry.setCompact(true);
        applyHeights();
        scroll.scrollTo(0, 0);
    }

    /** Dragging the handle down shrinks an expanded sheet; a short one closes. */
    private void onDraggedDown() {
        if (expanded) {
            collapse();
        } else {
            dismiss();
        }
    }

    private void animateResize() {
        TransitionManager.beginDelayedTransition((ViewGroup) root, new ChangeBounds().setDuration(220));
    }

    // ---------------------------------------------------------------- layout

    /** Applies the caller's requested width, gravity and margins, and the sheet's shape. */
    private void applyGeometry() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        boolean docked = request.anchoredBottom();

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sheet.getLayoutParams();
        if (request.width > 0) {
            lp.width = Math.min(request.width, dm.widthPixels);
            lp.gravity = docked ? (request.gravity & Gravity.HORIZONTAL_GRAVITY_MASK) | Gravity.BOTTOM : request.gravity;
        } else if (docked) {
            lp.width = Math.min(dm.widthPixels, Views.dp(this, 640));
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        } else {
            lp.width = Math.min(dm.widthPixels - Views.dp(this, 32), Views.dp(this, 520));
            lp.gravity = request.gravity;
        }
        lp.leftMargin = request.marginLeft;
        lp.rightMargin = request.marginRight;
        sheet.setLayoutParams(lp);

        // Rounded top corners when docked to the bottom edge; a card otherwise.
        float r = Views.dp(this, 24);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Views.color(this, R.color.surface));
        bg.setCornerRadii(flushBottom()
                ? new float[]{r, r, r, r, 0, 0, 0, 0}
                : new float[]{r, r, r, r, r, r, r, r});
        sheet.setBackground(bg);
        sheet.setClipToOutline(true);

        Views.onInsets(root, (left, top, right, bottom) -> {
            insetTop = top;
            insetBottom = bottom;
            applyHeights();
        });
    }

    private boolean flushBottom() {
        return request.anchoredBottom() && request.marginBottom == 0;
    }

    /** Sizes the sheet for its state (short or expanded) and the system bars. */
    private void applyHeights() {
        int screen = getResources().getDisplayMetrics().heightPixels;
        boolean docked = request.anchoredBottom();
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) sheet.getLayoutParams();
        if (flushBottom()) {
            sheet.setPadding(0, 0, 0, insetBottom); // extend behind the navigation bar
            p.bottomMargin = 0;
        } else {
            p.bottomMargin = request.marginBottom + insetBottom + (docked ? 0 : Views.dp(this, 16));
        }
        p.topMargin = docked ? 0 : request.marginTop + insetTop + Views.dp(this, 16);
        sheet.setLayoutParams(p);

        int chrome = Views.dp(this, HEADER_DP) + insetBottom;
        int available = screen - insetTop - Views.dp(this, EXPANDED_GAP_DP) - chrome;
        ViewGroup.LayoutParams sp = scroll.getLayoutParams();
        if (expanded) {
            scroll.setMaxHeight(available);
            sp.height = available; // fill the screen even when the entry is short
        } else {
            int maxSheet = request.height > 0 ? Math.min(request.height, screen) : screen * 62 / 100;
            int shortHeight = Math.max(Views.dp(this, 120), Math.min(maxSheet - chrome, available));
            scroll.setMaxHeight(shortHeight);
            sp.height = request.height > 0 ? shortHeight : ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        scroll.setLayoutParams(sp);
    }

    // ------------------------------------------------------------ animations

    private void animateIn() {
        boolean docked = request.anchoredBottom();
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(200).start();
        sheet.setVisibility(View.INVISIBLE);
        sheet.post(() -> {
            sheet.setTranslationY(docked ? sheet.getHeight() : -Views.dp(this, 24));
            sheet.setAlpha(docked ? 1f : 0f);
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
        boolean docked = request.anchoredBottom();
        scrim.animate().alpha(0f).setDuration(180).start();
        sheet.animate()
                .translationY(docked ? sheet.getHeight() + Views.dp(this, 16) : -Views.dp(this, 24))
                .alpha(docked ? 1f : 0f)
                .setDuration(200)
                .withEndAction(() -> {
                    finish();
                    overridePendingTransition(0, 0);
                }).start();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (expanded) {
            collapse();
        } else {
            dismiss();
        }
    }
}
