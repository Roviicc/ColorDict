package io.github.roviicc.colordict.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Lays children out left-to-right, wrapping onto new lines (for chips). */
final class FlowLayout extends ViewGroup {

    private final int hGap;
    private final int vGap;

    FlowLayout(Context context, int hGapPx, int vGapPx) {
        super(context);
        hGap = hGapPx;
        vGap = vGapPx;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxWidth = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        boolean unbounded = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED;
        int x = 0;
        int lineHeight = 0;
        int height = 0;
        int widest = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            child.measure(MeasureSpec.makeMeasureSpec(Math.max(maxWidth, 0),
                            unbounded ? MeasureSpec.UNSPECIFIED : MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int w = child.getMeasuredWidth();
            int h = child.getMeasuredHeight();
            if (x > 0 && !unbounded && x + w > maxWidth) {
                height += lineHeight + vGap;
                x = 0;
                lineHeight = 0;
            }
            x += w + hGap;
            widest = Math.max(widest, x - hGap);
            lineHeight = Math.max(lineHeight, h);
        }
        height += lineHeight;
        int width = unbounded ? widest : MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width + (unbounded ? getPaddingLeft() + getPaddingRight() : 0),
                resolveSize(height + getPaddingTop() + getPaddingBottom(), heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int maxWidth = r - l - getPaddingLeft() - getPaddingRight();
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int x = 0;
        int y = getPaddingTop();
        int lineHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            int w = child.getMeasuredWidth();
            int h = child.getMeasuredHeight();
            if (x > 0 && x + w > maxWidth) {
                y += lineHeight + vGap;
                x = 0;
                lineHeight = 0;
            }
            int left = rtl ? r - l - getPaddingRight() - x - w : getPaddingLeft() + x;
            child.layout(left, y, left + w, y + h);
            x += w + hGap;
            lineHeight = Math.max(lineHeight, h);
        }
    }
}
