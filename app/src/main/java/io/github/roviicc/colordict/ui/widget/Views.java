package io.github.roviicc.colordict.ui.widget;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import io.github.roviicc.colordict.R;

/** Small view helpers shared by every screen (the app has no UI libraries). */
public final class Views {

    private Views() {
    }

    public static int dp(Context c, float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, c.getResources().getDisplayMetrics()));
    }

    public static int color(Context c, int res) {
        return c.getColor(res);
    }

    public static GradientDrawable rounded(int fill, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static GradientDrawable outlined(int fill, int stroke, int strokePx, float radiusPx) {
        GradientDrawable d = rounded(fill, radiusPx);
        d.setStroke(strokePx, stroke);
        return d;
    }

    /** Wraps a background in a ripple clipped to the same shape. */
    public static Drawable pressable(Context c, Drawable content, float radiusPx) {
        int ripple = (color(c, R.color.textPrimary) & 0x00FFFFFF) | 0x1F000000;
        return new RippleDrawable(ColorStateList.valueOf(ripple), content, rounded(0xFF000000, radiusPx));
    }

    public static TextView text(Context c, float sp, int colorRes) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color(c, colorRes));
        return t;
    }

    /** Small all-caps section label, e.g. "EXAMPLES". */
    public static TextView label(Context c, String s) {
        TextView t = text(c, 11, R.color.textTertiary);
        t.setText(s.toUpperCase(Locale.ROOT));
        t.setLetterSpacing(0.1f);
        t.setTypeface(medium());
        return t;
    }

    public static Typeface serifBold() {
        return Typeface.create(Typeface.SERIF, Typeface.BOLD);
    }

    /** The medium-weight sans-serif used for labels and buttons. */
    public static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    /** The theme's ripple background for tappable rows (borderless for icon buttons). */
    public static int selectableBackground(Context c, boolean borderless) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(borderless
                ? android.R.attr.selectableItemBackgroundBorderless
                : android.R.attr.selectableItemBackground, tv, true);
        return tv.resourceId;
    }

    public static void copyToClipboard(Context c, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(text, text));
            Toast.makeText(c, R.string.copied, Toast.LENGTH_SHORT).show();
        }
    }

    /** Sets an icon button's image and tint. */
    public static void icon(ImageButton b, int drawable, int tint) {
        b.setImageResource(drawable);
        b.setImageTintList(ColorStateList.valueOf(tint));
    }

    /**
     * Draws the window behind the system bars on every Android version, so
     * layouts look the same with or without Android 15's enforced
     * edge-to-edge mode. Callers pad their root view with {@link #onInsets}.
     */
    @SuppressWarnings("deprecation")
    public static void edgeToEdge(Window window) {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        } else {
            View decor = window.getDecorView();
            decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    public interface InsetsListener {
        void onInsets(int left, int top, int right, int bottom);
    }

    /** Reports system bar + keyboard insets (keyboard included in bottom). */
    @SuppressWarnings("deprecation")
    public static void onInsets(View root, InsetsListener listener) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                listener.onInsets(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            } else {
                listener.onInsets(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        root.requestApplyInsets();
    }
}
