package io.github.roviicc.colordict.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

/** A ScrollView that wraps its content but never grows past a maximum height. */
public final class MaxHeightScrollView extends ScrollView {

    private int maxHeight = Integer.MAX_VALUE;

    public MaxHeightScrollView(Context context) {
        super(context);
    }

    public MaxHeightScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void setMaxHeight(int px) {
        maxHeight = px;
        requestLayout();
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
