package io.github.roviicc.colordict.ui.widget;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import java.util.Locale;

import io.github.roviicc.colordict.R;

/** Lazily started text-to-speech for pronouncing headwords. */
public final class Speaker implements TextToSpeech.OnInitListener {

    private final Context context;
    private final TextToSpeech tts;
    private boolean ready;
    private boolean failed;
    private String pending;

    public Speaker(Context context) {
        this.context = context.getApplicationContext();
        tts = new TextToSpeech(this.context, this);
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            failed = true;
            Toast.makeText(context, R.string.tts_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        int lang = tts.setLanguage(Locale.US);
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.ENGLISH);
        }
        tts.setSpeechRate(0.9f);
        ready = true;
        if (pending != null) {
            speak(pending);
            pending = null;
        }
    }

    public void speak(String word) {
        if (failed) {
            Toast.makeText(context, R.string.tts_unavailable, Toast.LENGTH_SHORT).show();
        } else if (!ready) {
            pending = word;
        } else {
            tts.speak(word, TextToSpeech.QUEUE_FLUSH, null, "word");
        }
    }

    public void shutdown() {
        tts.stop();
        tts.shutdown();
    }
}
