package io.github.roviicc.colordict.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;

import io.github.roviicc.colordict.App;
import io.github.roviicc.colordict.R;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w393dp-h852dp-xhdpi")
/**
 * Drives real touch events through the popup's bottom sheet and checks that
 * the sheet and its content move as one gesture (see BottomSheetLayout).
 */
public class SheetGestureTest {

    private long t;

    private void ready() throws Exception {
        final boolean[] done = {false};
        App.from(RuntimeEnvironment.getApplication()).repository().warmUp(() -> done[0] = true);
        long until = System.currentTimeMillis() + 60000;
        while (!done[0] && System.currentTimeMillis() < until) { Thread.sleep(50); ShadowLooper.idleMainLooper(); }
    }

    private void settle() {
        for (int i = 0; i < 40; i++) ShadowLooper.idleMainLooper(25, TimeUnit.MILLISECONDS);
    }


    private View root;

    private void down(float y) { t = SystemClock.uptimeMillis(); send(MotionEvent.ACTION_DOWN, y); }
    private void send(int action, float y) {
        t += 16;
        MotionEvent e = MotionEvent.obtain(t, t, action, root.getWidth() / 2f, y, 0);
        root.dispatchTouchEvent(e);
        e.recycle();
    }
    /** A slow drag (no fling) from y by total dy, in 10px steps, then lift. */
    private void drag(float y, float dy, boolean lift) {
        down(y);
        int steps = (int) Math.abs(dy / 10);
        for (int i = 1; i <= steps; i++) {
            send(MotionEvent.ACTION_MOVE, y + Math.signum(dy) * i * 10);
            t += 40; // slow
        }
        if (lift) { t += 200; send(MotionEvent.ACTION_MOVE, y + dy); t += 200; send(MotionEvent.ACTION_UP, y + dy); }
    }

    @Test public void sheetAndContentMoveAsOneGesture() throws Exception {
        ready();
        ActivityController<PopupActivity> c = Robolectric.buildActivity(PopupActivity.class,
                new Intent("colordict.intent.action.SEARCH").putExtra("EXTRA_QUERY", "set")).setup();
        PopupActivity a = c.get();
        settle();
        root = a.findViewById(R.id.root);
        View sheet = a.findViewById(R.id.sheet);
        ScrollView scroll = a.findViewById(R.id.scroll);
        int screen = root.getHeight();
        float collapsed = sheet.getTranslationY();
        assertTrue("opens collapsed (part of the sheet below the screen)", collapsed > 100);
        assertEquals(0, scroll.getScrollY());

        // 1. Collapsed, finger up 200px: the sheet follows, content does not scroll.
        float sheetTop = sheet.getTop() + collapsed;
        drag(sheetTop + 300, -200, false);
        float mid = sheet.getTranslationY();
        assertEquals(collapsed - 200, mid, 25);
        assertEquals(0, scroll.getScrollY());
        // ...continue the SAME gesture past the top: sheet reaches expanded, then content scrolls.
        float y = sheetTop + 300 - 200;
        for (int i = 1; i <= (int) (collapsed / 10) + 40; i++) { send(MotionEvent.ACTION_MOVE, y - i * 10); t += 40; }
        float yEnd = y - ((int) (collapsed / 10) + 40) * 10;
        t += 200; send(MotionEvent.ACTION_MOVE, yEnd); t += 200; send(MotionEvent.ACTION_UP, yEnd);
        settle();
        assertEquals("expanded", 0f, sheet.getTranslationY(), 1);
        assertTrue("content scrolled in the same gesture", scroll.getScrollY() > 200);

        // 2. Expanded with content scrolled, finger down: content scrolls back first...
        int scrolled = scroll.getScrollY();
        drag(400, 150, false);
        assertEquals("sheet stays put while content scrolls back", 0f, sheet.getTranslationY(), 1);
        assertEquals(scrolled - 150, scroll.getScrollY(), 15);
        // ...then the sheet lowers, stopping at collapsed even if the finger keeps going.
        float yy = 550;
        for (int i = 1; i <= (scrolled + (int) collapsed + 400) / 10; i++) { send(MotionEvent.ACTION_MOVE, yy + i * 10); t += 40; }
        assertEquals(0, scroll.getScrollY());
        assertEquals("stops at collapsed", collapsed, sheet.getTranslationY(), 2);
        t += 300; send(MotionEvent.ACTION_UP, yy + 2000);
        settle();
        assertEquals("rests collapsed", collapsed, sheet.getTranslationY(), 2);
        assertTrue("still open", !a.isFinishing());

        // 3. A new drag down from collapsed closes the popup.
        float top2 = sheet.getTop() + sheet.getTranslationY();
        drag(top2 + 200, 400, true);
        settle();
        assertTrue("closed", a.isFinishing());
    }

    @Test public void shortEntryHugsContent() throws Exception {
        ready();
        PopupActivity a = Robolectric.buildActivity(PopupActivity.class,
                new Intent("colordict.intent.action.SEARCH").putExtra("EXTRA_QUERY", "rizz")).setup().get();
        settle();
        root = a.findViewById(R.id.root);
        View sheet = a.findViewById(R.id.sheet);
        float before = sheet.getTranslationY();
        float top = sheet.getTop() + before;
        drag(top + 150, -300, true); // can't expand past its content
        settle();
        assertEquals(before, sheet.getTranslationY(), 2);
    }

    @Test public void shortDragsSpringBack() throws Exception {
        ready();
        PopupActivity a = Robolectric.buildActivity(PopupActivity.class,
                new Intent("colordict.intent.action.SEARCH").putExtra("EXTRA_QUERY", "set")).setup().get();
        settle();
        root = a.findViewById(R.id.root);
        View sheet = a.findViewById(R.id.sheet);
        float collapsed = sheet.getTranslationY();
        float top = sheet.getTop() + collapsed;
        drag(top + 300, -60, true);   // small slow lift: back to collapsed
        settle();
        assertEquals(collapsed, sheet.getTranslationY(), 2);
        drag(top + 300, 60, true);    // small slow pull down: back to collapsed, not closed
        settle();
        assertEquals(collapsed, sheet.getTranslationY(), 2);
        assertTrue(!a.isFinishing());
    }
}
