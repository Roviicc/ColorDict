package io.github.roviicc.colordict.ui.entry;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.ui.widget.Views;

/** Pulsing grey placeholder bars shown while content loads. */
final class Skeleton {

    private Skeleton() {
    }

    /** Adds one bar; widthDp &lt; 0 means full width. The pulse runs only while attached. */
    static void addBar(LinearLayout into, int widthDp, int heightDp, int radiusDp, int topDp) {
        Context c = into.getContext();
        View bar = new View(c);
        bar.setBackground(Views.rounded(Views.color(c, R.color.skeleton), Views.dp(c, radiusDp)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                widthDp < 0 ? LinearLayout.LayoutParams.MATCH_PARENT : Views.dp(c, widthDp),
                Views.dp(c, heightDp));
        lp.topMargin = Views.dp(c, topDp);

        ObjectAnimator pulse = ObjectAnimator.ofFloat(bar, View.ALPHA, 1f, 0.45f);
        pulse.setDuration(750);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        bar.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                pulse.start();
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                pulse.cancel();
            }
        });
        into.addView(bar, lp);
    }
}
