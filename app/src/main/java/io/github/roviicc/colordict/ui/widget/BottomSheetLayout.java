package io.github.roviicc.colordict.ui.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ScrollView;

/**
 * A full-screen container that turns one child into a draggable bottom sheet
 * with three resting positions: hidden, collapsed (a peek of the top) and
 * expanded (as tall as the sheet, or its content if shorter).
 *
 * <p>Every vertical drag on the sheet is handled here, so the sheet and the
 * scrolling content inside it move as one continuous gesture:
 * <ul>
 *   <li>Finger up: the sheet rises until expanded, then the content scrolls.</li>
 *   <li>Finger down: the content scrolls back to its top, then the sheet
 *       lowers. A drag that began expanded stops at collapsed; a drag that
 *       began collapsed can carry on and close the sheet.</li>
 * </ul>
 * On release the sheet settles to the nearest position, following fling
 * direction, and an expanded sheet passes the fling on to the content.
 */
public final class BottomSheetLayout extends FrameLayout {

    public interface Listener {
        /** The sheet finished sliding off screen. */
        void onHidden();

        /** How much of the scrim should show: 1 at collapsed or above, 0 when hidden. */
        void onScrimFraction(float fraction);
    }

    private enum State { HIDDEN, COLLAPSED, EXPANDED }

    private final int touchSlop;
    private final float minFlingVelocity;

    private View sheet;
    private ScrollView scroll;
    private Listener listener;
    private int peekHeight;

    private float expandedOffset;
    private float collapsedOffset;
    private float hiddenOffset;
    private State state = State.HIDDEN;
    private boolean showPending;

    private ValueAnimator settling;
    private VelocityTracker velocity;
    private float downX;
    private float downY;
    private float lastY;
    private boolean downInSheet;
    private boolean dragging;
    private boolean startedExpanded;
    private boolean scrolledContent;
    private float scrollRemainder;

    public BottomSheetLayout(Context context) {
        this(context, null);
    }

    public BottomSheetLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();
        minFlingVelocity = Views.dp(context, 600);
    }

    /**
     * Makes {@code sheet} (a child, gravity bottom, fixed height) draggable;
     * {@code scroll} is the scrolling content inside it.
     */
    public void attach(View sheet, ScrollView scroll, Listener listener) {
        this.sheet = sheet;
        this.scroll = scroll;
        this.listener = listener;
        // Re-measure the resting positions whenever the content changes size.
        View content = scroll.getChildAt(0);
        if (content != null) {
            content.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (b - t != ob - ot) {
                    post(this::updateOffsets);
                }
            });
        }
    }

    /** The most of the sheet (in px, from its top) shown when collapsed. */
    public void setPeekHeight(int px) {
        peekHeight = px;
        updateOffsets();
    }

    /** Slides the sheet in to collapsed once it has been laid out. */
    public void show() {
        showPending = true;
        requestLayout();
    }

    /** Slides the sheet out, then calls {@link Listener#onHidden()}. */
    public void hide() {
        settleTo(State.HIDDEN, 0);
    }

    public boolean isExpanded() {
        return state == State.EXPANDED && expandedOffset < collapsedOffset;
    }

    /** Returns to collapsed with the content scrolled back to its top. */
    public void collapse() {
        scroll.smoothScrollTo(0, 0);
        settleTo(State.COLLAPSED, 0);
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateOffsets();
        if (showPending && sheet != null && sheet.getHeight() > 0) {
            showPending = false;
            setOffset(hiddenOffset);
            sheet.setVisibility(VISIBLE);
            settleTo(State.COLLAPSED, 0);
        }
    }

    /** Recomputes the three resting offsets (translationY of the sheet). */
    private void updateOffsets() {
        if (sheet == null || sheet.getHeight() == 0) {
            return;
        }
        float height = sheet.getHeight();
        View content = scroll.getChildAt(0);
        int contentHeight = content == null ? 0 : content.getHeight();
        // Height the sheet needs to show everything: its chrome plus all content.
        float needed = height - scroll.getHeight() + contentHeight;
        expandedOffset = Math.max(0, height - needed);
        collapsedOffset = Math.max(expandedOffset, height - (peekHeight > 0 ? peekHeight : height));
        hiddenOffset = height;
        if (!dragging && (settling == null || !settling.isRunning()) && !showPending) {
            setOffset(offsetFor(state));
        }
    }

    private float offsetFor(State s) {
        switch (s) {
            case EXPANDED:
                return expandedOffset;
            case COLLAPSED:
                return collapsedOffset;
            default:
                return hiddenOffset;
        }
    }

    private void setOffset(float offset) {
        sheet.setTranslationY(offset);
        if (listener != null) {
            float range = hiddenOffset - collapsedOffset;
            float fraction = offset <= collapsedOffset || range <= 0 ? 1f
                    : 1f - (offset - collapsedOffset) / range;
            listener.onScrimFraction(Math.max(0f, Math.min(1f, fraction)));
        }
    }

    // ---------------------------------------------------------------- touch

    /** Children (requested by the scroll view) can't stop us taking over a drag. */
    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallow) {
        // Ignored on purpose: this layout decides between sheet and content.
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (sheet == null) {
            return false;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startGesture(ev);
                return false;
            case MotionEvent.ACTION_MOVE:
                trackVelocity(ev);
                if (downInSheet && isVerticalDrag(ev)) {
                    beginDrag(ev);
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (sheet == null) {
            return false;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // Reached here only when no child took the touch (empty sheet area).
                startGesture(ev);
                return downInSheet;
            case MotionEvent.ACTION_MOVE:
                trackVelocity(ev);
                if (!dragging) {
                    if (!isVerticalDrag(ev)) {
                        return true;
                    }
                    beginDrag(ev);
                }
                float y = ev.getY();
                applyDelta(y - lastY);
                lastY = y;
                return true;
            case MotionEvent.ACTION_UP:
                trackVelocity(ev);
                if (dragging) {
                    velocity.computeCurrentVelocity(1000);
                    release(velocity.getYVelocity());
                }
                endGesture();
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    release(0);
                }
                endGesture();
                return true;
            default:
                return true;
        }
    }

    private void startGesture(MotionEvent ev) {
        downX = ev.getX();
        downY = ev.getY();
        lastY = downY;
        dragging = false;
        downInSheet = ev.getY() >= sheet.getTop() + sheet.getTranslationY();
        if (velocity != null) {
            velocity.recycle();
        }
        velocity = VelocityTracker.obtain();
        velocity.addMovement(ev);
        if (downInSheet && settling != null && settling.isRunning()) {
            settling.cancel(); // catch the sheet mid-animation
        }
    }

    private void trackVelocity(MotionEvent ev) {
        if (velocity != null) {
            velocity.addMovement(ev);
        }
    }

    private boolean isVerticalDrag(MotionEvent ev) {
        float dy = Math.abs(ev.getY() - downY);
        return dy > touchSlop && dy > Math.abs(ev.getX() - downX);
    }

    private void beginDrag(MotionEvent ev) {
        dragging = true;
        scrollRemainder = 0;
        scrolledContent = false;
        startedExpanded = sheet.getTranslationY() <= expandedOffset + 1 && expandedOffset < collapsedOffset;
        // Catch up with the distance moved before the drag was recognised, so
        // the sheet stays exactly under the finger.
        applyDelta(ev.getY() - downY);
        lastY = ev.getY();
    }

    private void endGesture() {
        dragging = false;
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
    }

    /** Spends a finger movement on the sheet or the content (dy &gt; 0 is downward). */
    private void applyDelta(float dy) {
        float offset = sheet.getTranslationY();
        if (dy < 0) {
            float rise = Math.min(-dy, offset - expandedOffset);
            if (rise > 0) {
                setOffset(offset - rise);
            }
            scrollContent(-dy - Math.max(0, rise));
        } else if (dy > 0) {
            float back = Math.min(dy, scroll.getScrollY());
            if (back > 0) {
                scrollContent(-back);
            }
            float rest = dy - back;
            if (rest > 0 && scroll.getScrollY() == 0) {
                // A drag that began expanded rests at collapsed; a new drag can close.
                float lowest = startedExpanded ? collapsedOffset : hiddenOffset;
                setOffset(Math.min(lowest, offset + rest));
            }
        }
    }

    private void scrollContent(float px) {
        if (px == 0) {
            return;
        }
        scrollRemainder += px;
        int whole = (int) scrollRemainder;
        if (whole != 0) {
            scroll.scrollBy(0, whole);
            scrollRemainder -= whole;
            scrolledContent = true;
        }
    }

    /** Picks where to settle from position and fling velocity (vy &gt; 0 is downward). */
    private void release(float vy) {
        float offset = sheet.getTranslationY();
        if (offset <= expandedOffset + 0.5f) {
            state = State.EXPANDED;
            if (scrolledContent || scroll.getScrollY() > 0) {
                scroll.fling((int) -vy);
            }
            return;
        }
        State target;
        if (!startedExpanded && offset > collapsedOffset) {
            boolean farEnough = offset - collapsedOffset > (hiddenOffset - collapsedOffset) * 0.25f;
            target = vy < -minFlingVelocity ? State.COLLAPSED
                    : (vy > minFlingVelocity || farEnough) ? State.HIDDEN : State.COLLAPSED;
        } else if (vy < -minFlingVelocity) {
            target = State.EXPANDED;
        } else if (vy > minFlingVelocity) {
            target = State.COLLAPSED;
        } else {
            float middle = (expandedOffset + collapsedOffset) / 2f;
            target = offset < middle ? State.EXPANDED : State.COLLAPSED;
        }
        settleTo(target, vy);
    }

    private void settleTo(State target, float velocityY) {
        if (sheet == null) {
            return;
        }
        if (settling != null) {
            settling.cancel();
        }
        float from = sheet.getTranslationY();
        float to = offsetFor(target);
        state = target;
        float distance = Math.abs(to - from);
        long duration = (long) Math.max(150, Math.min(320,
                distance / Math.max(1f, Math.abs(velocityY)) * 1000f * 1.5f));
        if (Math.abs(velocityY) < minFlingVelocity) {
            duration = (long) Math.max(180, Math.min(300, distance / Views.dp(getContext(), 2.5f)));
        }
        settling = ValueAnimator.ofFloat(from, to);
        settling.setDuration(duration);
        settling.setInterpolator(new DecelerateInterpolator(1.6f));
        settling.addUpdateListener(a -> setOffset((float) a.getAnimatedValue()));
        settling.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) {
                    return;
                }
                // The content may have changed size meanwhile: land on the current spot.
                setOffset(offsetFor(target));
                if (target == State.HIDDEN && listener != null) {
                    listener.onHidden();
                }
            }
        });
        settling.start();
    }
}
