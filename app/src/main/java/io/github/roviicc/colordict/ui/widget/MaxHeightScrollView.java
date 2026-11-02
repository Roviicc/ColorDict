package io.github.roviicc.colordict.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ScrollView;

/**
 * A ScrollView that wraps its content but never grows past a maximum height.
 * It can also report an upward drag (even when the content is too short to
 * scroll), which the popup uses to expand.
 */
public final class MaxHeightScrollView extends ScrollView {

    private int maxHeight = Integer.MAX_VALUE;
    private Runnable onPullUp;
    private float downY;
    private boolean pullUpReported;

    public MaxHeightScrollView(Context context) {
        super(context);
    }

    public MaxHeightScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setMaxHeight(int px) {
        maxHeight = px;
        requestLayout();
    }

    /** Runs once per gesture when the user drags upward past a small threshold; null to stop. */
    public void setOnPullUpListener(Runnable listener) {
        onPullUp = listener;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (onPullUp != null) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = ev.getRawY();
                    pullUpReported = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!pullUpReported && downY - ev.getRawY() > Views.dp(getContext(), 24)) {
                        pullUpReported = true;
                        onPullUp.run();
                    }
                    break;
                default:
                    break;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int mode = MeasureSpec.getMode(heightMeasureSpec);
        int size = MeasureSpec.getSize(heightMeasureSpec);
        int limit = mode == MeasureSpec.UNSPECIFIED ? maxHeight : Math.min(size, maxHeight);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(limit,
                mode == MeasureSpec.EXACTLY ? MeasureSpec.EXACTLY : MeasureSpec.AT_MOST));
    }
}
