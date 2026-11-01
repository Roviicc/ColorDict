package io.github.roviicc.colordict.ui.widget;

import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

/**
 * Lets the user swipe a bottom sheet down by its handle area. Past a quarter
 * of the sheet's height (or on a quick fling) the sheet is dismissed;
 * otherwise it springs back.
 */
public final class DragToDismiss implements View.OnTouchListener {

    private final View sheet;
    private final Runnable onDismiss;
    private final float flingVelocityPx;
    private float startY;
    private VelocityTracker velocity;

    private DragToDismiss(View sheet, Runnable onDismiss) {
        this.sheet = sheet;
        this.onDismiss = onDismiss;
        this.flingVelocityPx = Views.dp(sheet.getContext(), 1000);
    }

    /** Makes dragging {@code handle} move {@code sheet}; {@code onDismiss} runs when let go far enough. */
    public static void attach(View handle, View sheet, Runnable onDismiss) {
        handle.setOnTouchListener(new DragToDismiss(sheet, onDismiss));
    }

    @Override
    public boolean onTouch(View v, MotionEvent ev) {
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
                if (sheet.getTranslationY() > sheet.getHeight() * 0.25f || vy > flingVelocityPx) {
                    onDismiss.run();
                } else {
                    sheet.animate().translationY(0).setDuration(180).start();
                }
                return true;
            default:
                return false;
        }
    }
}
