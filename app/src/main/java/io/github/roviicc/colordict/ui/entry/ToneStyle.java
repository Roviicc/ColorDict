package io.github.roviicc.colordict.ui.entry;

import io.github.roviicc.colordict.R;
import io.github.roviicc.colordict.data.Entry;

/** Colors and label for each connotation tone. */
enum ToneStyle {
    POSITIVE(R.color.tonePositive, R.color.tonePositiveBg, R.string.tone_positive),
    NEGATIVE(R.color.toneNegative, R.color.toneNegativeBg, R.string.tone_negative),
    NEUTRAL(R.color.toneNeutral, R.color.toneNeutralBg, R.string.tone_neutral),
    MIXED(R.color.toneMixed, R.color.toneMixedBg, R.string.tone_mixed);

    final int foreground;
    final int background;
    final int label;

    ToneStyle(int foreground, int background, int label) {
        this.foreground = foreground;
        this.background = background;
        this.label = label;
    }

    static ToneStyle of(Entry.Tone tone) {
        switch (tone) {
            case POSITIVE:
                return POSITIVE;
            case NEGATIVE:
                return NEGATIVE;
            case MIXED:
                return MIXED;
            default:
                return NEUTRAL;
        }
    }
}
