package io.github.roviicc.colordict.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import io.github.roviicc.colordict.data.Dictionary;

/** Applies the chosen light/dark theme and edge-to-edge drawing to every screen. */
abstract class BaseActivity extends Activity {

    private Speaker speaker;

    @Override
    protected void attachBaseContext(Context base) {
        int theme = Dictionary.get(base).theme();
        if (theme == Dictionary.THEME_SYSTEM) {
            super.attachBaseContext(base);
            return;
        }
        Configuration config = new Configuration(base.getResources().getConfiguration());
        int night = theme == Dictionary.THEME_DARK
                ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        super.attachBaseContext(base.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.edgeToEdge(getWindow());
    }

    Dictionary dictionary() {
        return Dictionary.get(this);
    }

    /** Message for a failed lookup; a null message means no API key is set yet. */
    String errorText(String message) {
        return message != null ? message : getString(io.github.roviicc.colordict.R.string.no_api_key);
    }

    void openSettings() {
        startActivity(new android.content.Intent(this, SettingsActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    void speak(String word) {
        if (speaker == null) {
            speaker = new Speaker(this);
        }
        speaker.speak(word);
    }

    @Override
    protected void onDestroy() {
        if (speaker != null) {
            speaker.shutdown();
        }
        super.onDestroy();
    }
}
