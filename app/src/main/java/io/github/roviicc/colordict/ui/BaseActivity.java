package io.github.roviicc.colordict.ui;

import android.content.Context;
import android.content.res.Configuration;

import androidx.activity.ComponentActivity;

import io.github.roviicc.colordict.App;
import io.github.roviicc.colordict.data.DictRepository;
import io.github.roviicc.colordict.data.Prefs;

/**
 * Applies the user's light/dark override by patching the configuration's
 * night mode before resources are created — works on every supported API
 * level without any support library.
 */
public abstract class BaseActivity extends ComponentActivity {

    private int appliedThemeMode = Prefs.THEME_SYSTEM;

    @Override
    protected void attachBaseContext(Context newBase) {
        appliedThemeMode = Prefs.themeMode(newBase);
        if (appliedThemeMode != Prefs.THEME_SYSTEM) {
            Configuration override =
                    new Configuration(newBase.getResources().getConfiguration());
            int night = appliedThemeMode == Prefs.THEME_DARK
                    ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
            override.uiMode = night | (override.uiMode & ~Configuration.UI_MODE_NIGHT_MASK);
            newBase = newBase.createConfigurationContext(override);
        }
        super.attachBaseContext(newBase);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appliedThemeMode != Prefs.themeMode(this)) {
            recreate();
        }
    }

    protected DictRepository repo() {
        return App.get(this).repo();
    }

    protected boolean isNightMode() {
        return (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
}
