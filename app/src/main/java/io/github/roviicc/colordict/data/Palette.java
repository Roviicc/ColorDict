package io.github.roviicc.colordict.data;

/** The distinct label colors dictionaries can be tagged with. */
public final class Palette {

    /**
     * Hues are interleaved rather than following the color wheel, so the
     * first few dictionaries a user adds get colors that are easy to tell
     * apart at a glance — the whole point of the color coding.
     */
    public static final int[] COLORS = {
            0xFFE53935, // red
            0xFF1E88E5, // blue
            0xFF43A047, // green
            0xFFF4511E, // deep orange
            0xFF8E24AA, // purple
            0xFF00897B, // teal
            0xFFD81B60, // pink
            0xFF3949AB, // indigo
            0xFF7CB342, // light green
            0xFF00ACC1, // cyan
            0xFF5E35B1, // deep purple
            0xFF6D4C41, // brown
    };

    private Palette() {
    }

    /** Auto-assigned color for the n-th registered dictionary. */
    public static int auto(int index) {
        return COLORS[Math.floorMod(index, COLORS.length)];
    }
}
