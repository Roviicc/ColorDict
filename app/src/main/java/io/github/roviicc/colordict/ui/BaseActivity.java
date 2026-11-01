package io.github.roviicc.colordict.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;

import io.github.roviicc.colordict.App;
import io.github.roviicc.colordict.data.DictionaryRepository;
import io.github.roviicc.colordict.data.Settings;
import io.github.roviicc.colordict.ui.widget.Speaker;
import io.github.roviicc.colordict.ui.widget.Views;

/** Common base for every screen: applies the theme setting and edge-to-edge drawing. */
abstract class BaseActivity extends Activity {

    private Speaker speaker;

    @Override
    protected void attachBaseContext(Context base) {
        int theme = App.from(base).settings().theme();
        if (theme == Settings.THEME_SYSTEM) {
            super.attachBaseContext(base);
            return;
        }
        Configuration config = new Configuration(base.getResources().getConfiguration());
        int night = theme == Settings.THEME_DARK
                ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        super.attachBaseContext(base.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Views.edgeToEdge(getWindow());
    }

    DictionaryRepository repository() {
        return App.from(this).repository();
    }

    Settings settings() {
        return App.from(this).settings();
    }

    public void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    public void speak(String word) {
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
