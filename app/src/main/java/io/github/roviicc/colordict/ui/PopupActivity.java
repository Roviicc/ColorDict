package io.github.roviicc.colordict.ui;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
import io.github.roviicc.colordict.ui.widget.BottomSheetLayout;
import io.github.roviicc.colordict.ui.widget.MaxHeightScrollView;
import io.github.roviicc.colordict.ui.widget.Views;

/**
 * The quick-lookup popup over whatever app the user is in. See
 * {@link PopupRequest} for how other apps open it.
 *
 * <p>By default it is a bottom sheet ({@link BottomSheetLayout}): it opens
 * collapsed showing the top of the entry; dragging up raises it to nearly full
 * screen and then scrolls the entry; dragging down scrolls back to the top,
 * lowers it to collapsed, and a further drag closes it. Callers that ask for a
 * top or centered popup get a floating card instead. The entry ends with a
 * "See full entry" link to the word page.
 */
public class PopupActivity extends BaseActivity implements EntryController.Host,
        BottomSheetLayout.Listener {

    /** Scrim left visible above an expanded sheet, so it still reads as a popup. */
    private static final int EXPANDED_GAP_DP = 24;
    /** Header (handle and buttons) above the entry. */
    private static final int HEADER_DP = 64;

    private BottomSheetLayout root;
    private View scrim;
    private LinearLayout sheet;
    private MaxHeightScrollView scroll;
    private EntryController entry;
    private PopupRequest request;
    private boolean closing;
    private boolean shown;

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
        boolean docked = request.anchoredBottom();
        entry = new EntryController(this, repository(), findViewById(R.id.content), scroll, !docked, this);
        entry.showFullEntryLink(true);
        entry.bindSaveButton(findViewById(R.id.saveButton));

        findViewById(R.id.handle).setBackground(
                Views.rounded(Views.color(this, R.color.outline), Views.dp(this, 2)));
        styleSheet();
        scrim.setOnClickListener(v -> close());
        findViewById(R.id.closeButton).setOnClickListener(v -> close());
        findViewById(R.id.expandButton).setOnClickListener(v -> onOpenFullEntry());

        if (docked) {
            setUpBottomSheet();
        } else {
            setUpFloatingCard();
        }
        entry.load(request.query);
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

    // ------------------------------------------------------- bottom sheet

    private void setUpBottomSheet() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sheet.getLayoutParams();
        if (request.width > 0) {
            lp.width = Math.min(request.width, dm.widthPixels);
            lp.gravity = (request.gravity & Gravity.HORIZONTAL_GRAVITY_MASK) | Gravity.BOTTOM;
        } else {
            lp.width = Math.min(dm.widthPixels, Views.dp(this, 640));
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        }
        lp.leftMargin = request.marginLeft;
        lp.rightMargin = request.marginRight;
        lp.bottomMargin = request.marginBottom;
        sheet.setLayoutParams(lp);
        // The entry fills the sheet; the sheet itself slides.
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        root.attach(sheet, scroll, this);
        Views.onInsets(root, (left, top, right, bottom) -> {
            int screen = dm.heightPixels;
            boolean flush = request.marginBottom == 0;
            sheet.setPadding(0, 0, 0, flush ? bottom : 0); // extend behind the navigation bar
            ViewGroup.LayoutParams p = sheet.getLayoutParams();
            p.height = screen - top - Views.dp(this, EXPANDED_GAP_DP) - (flush ? 0 : request.marginBottom + bottom);
            sheet.setLayoutParams(p);
            int peek = request.height > 0 ? request.height : screen * 62 / 100;
            root.setPeekHeight(Math.min(p.height, peek + (flush ? bottom : 0)));
            if (!shown) {
                shown = true;
                root.show(); // slide in once the sheet has its final size
            }
        });
        scrim.setAlpha(0f);
    }

    @Override
    public void onScrimFraction(float fraction) {
        scrim.setAlpha(fraction);
    }

    @Override
    public void onHidden() {
        finish();
        overridePendingTransition(0, 0);
    }

    // ------------------------------------------------------- floating card

    /** A card at the top or center (when a caller asks for that gravity). */
    private void setUpFloatingCard() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sheet.getLayoutParams();
        lp.width = request.width > 0 ? Math.min(request.width, dm.widthPixels)
                : Math.min(dm.widthPixels - Views.dp(this, 32), Views.dp(this, 520));
        lp.gravity = request.gravity;
        lp.leftMargin = request.marginLeft;
        lp.rightMargin = request.marginRight;
        sheet.setLayoutParams(lp);
        findViewById(R.id.handle).setVisibility(View.GONE);

        Views.onInsets(root, (left, top, right, bottom) -> {
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) sheet.getLayoutParams();
            p.topMargin = request.marginTop + top + Views.dp(this, 16);
            p.bottomMargin = request.marginBottom + bottom + Views.dp(this, 16);
            sheet.setLayoutParams(p);
            int screen = dm.heightPixels;
            int max = request.height > 0 ? Math.min(request.height, screen) : screen * 62 / 100;
            scroll.setMaxHeight(Math.max(Views.dp(this, 120),
                    Math.min(max, screen - top - bottom - Views.dp(this, 48)) - Views.dp(this, HEADER_DP)));
        });

        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(200).start();
        sheet.setAlpha(0f);
        sheet.setTranslationY(-Views.dp(this, 24));
        sheet.setVisibility(View.VISIBLE);
        sheet.animate().translationY(0).alpha(1f).setDuration(260)
                .setInterpolator(new DecelerateInterpolator(2f)).start();
    }

    // ---------------------------------------------------------------- shared

    /** Rounded top corners when docked to the bottom edge; a card otherwise. */
    private void styleSheet() {
        boolean flush = request.anchoredBottom() && request.marginBottom == 0;
        float r = Views.dp(this, 24);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Views.color(this, R.color.surface));
        bg.setCornerRadii(flush
                ? new float[]{r, r, r, r, 0, 0, 0, 0}
                : new float[]{r, r, r, r, r, r, r, r});
        sheet.setBackground(bg);
        sheet.setClipToOutline(true);
    }

    private void close() {
        if (closing) {
            return;
        }
        closing = true;
        if (request.anchoredBottom()) {
            root.hide();
            return;
        }
        scrim.animate().alpha(0f).setDuration(180).start();
        sheet.animate().translationY(-Views.dp(this, 24)).alpha(0f).setDuration(200)
                .withEndAction(this::onHidden).start();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (request.anchoredBottom() && root.isExpanded()) {
            root.collapse();
        } else {
            close();
        }
    }
}
