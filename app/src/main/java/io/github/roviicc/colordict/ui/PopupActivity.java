package io.github.roviicc.colordict.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
 */
public class PopupActivity extends BaseActivity implements EntryController.Host {

    /** Space taken by the header and footer; the entry scrolls in the rest. */
    private static final int CHROME_DP = 130;

    private View scrim;
    private LinearLayout sheet;
    private MaxHeightScrollView scroll;
    private View openFull;
    private EntryController entry;
    private PopupRequest request;
    private boolean dismissing;

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
        scrim = findViewById(R.id.scrim);
        sheet = findViewById(R.id.sheet);
        scroll = findViewById(R.id.scroll);
        openFull = findViewById(R.id.openFull);
        entry = new EntryController(this, repository(), findViewById(R.id.content), scroll, true, this);

        styleSheet();
        applyGeometry();

        scrim.setOnClickListener(v -> dismiss());
        findViewById(R.id.closeButton).setOnClickListener(v -> dismiss());
        findViewById(R.id.expandButton).setOnClickListener(v -> openInApp());
        openFull.setOnClickListener(v -> openInApp());
        entry.bindSaveButton(findViewById(R.id.saveButton));
        if (request.anchoredBottom()) {
            DragToDismiss.attach(findViewById(R.id.dragArea), sheet, this::dismiss);
        }

        show(request.query);
        animateIn();
    }

    private void show(String word) {
        openFull.setVisibility(View.GONE);
        entry.load(word);
    }

    // ------------------------------------------------------- EntryController.Host

    @Override
    public void onEntryShown(Entry e) {
        openFull.setVisibility(e.hasSenses() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onWordTapped(String word) {
        show(word);
    }

    @Override
    public void openSettings() {
        super.openSettings();
        finish();
    }

    private void openInApp() {
        startActivity(WordActivity.intent(this, entry.headword()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
        overridePendingTransition(0, 0);
    }

    // ---------------------------------------------------------------- layout

    private void styleSheet() {
        findViewById(R.id.handle).setBackground(
                Views.rounded(Views.color(this, R.color.outline), Views.dp(this, 2)));
        float r = Views.dp(this, 999);
        openFull.setBackground(Views.pressable(this, Views.rounded(Views.color(this, R.color.surfaceMuted), r), r));
        ((ImageView) findViewById(R.id.openFullChevron)).setImageTintList(
                ColorStateList.valueOf(Views.color(this, R.color.textPrimary)));
    }

    /** Applies the caller's requested size, gravity and margins. */
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
        lp.topMargin = request.marginTop;
        lp.bottomMargin = request.marginBottom;
        sheet.setLayoutParams(lp);

        // Rounded top corners when docked to the bottom edge; a card otherwise.
        boolean flushBottom = docked && request.marginBottom == 0;
        float r = Views.dp(this, 24);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Views.color(this, R.color.surface));
        bg.setCornerRadii(flushBottom
                ? new float[]{r, r, r, r, 0, 0, 0, 0}
                : new float[]{r, r, r, r, r, r, r, r});
        sheet.setBackground(bg);
        sheet.setClipToOutline(true);

        int maxSheet = request.height > 0 ? Math.min(request.height, dm.heightPixels) : dm.heightPixels * 62 / 100;
        Views.onInsets(findViewById(R.id.root), (left, top, right, bottom) -> {
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) sheet.getLayoutParams();
            if (flushBottom) {
                sheet.setPadding(0, 0, 0, bottom); // extend behind the navigation bar
            } else {
                p.bottomMargin = request.marginBottom + bottom + (docked ? 0 : Views.dp(this, 16));
            }
            if (!docked) {
                p.topMargin = request.marginTop + top + Views.dp(this, 16);
            }
            sheet.setLayoutParams(p);

            int chrome = Views.dp(this, CHROME_DP) + bottom;
            scroll.setMaxHeight(Math.max(Views.dp(this, 120),
                    Math.min(maxSheet, dm.heightPixels - top - Views.dp(this, 48)) - chrome));
            if (request.height > 0) {
                scroll.getLayoutParams().height = maxSheet - chrome;
            }
        });
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
        dismiss();
    }
}
