package io.github.roviicc.colordict.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.view.View;

import androidx.activity.ComponentActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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

    /** Keeps the content clear of the system bars and of the action bar.
     *
     *  <p>targetSdk is 35, which makes the window edge-to-edge from Android 15
     *  on, and these screens draw their chrome with the framework ActionBar.
     *  The content view then starts at the top of the <em>window</em> and both
     *  bars are painted over it. Measured on the Dictionaries screen, Android
     *  17: the status bar occupied y=0..156 and the action bar y=156..324,
     *  while the first list row sat at y=37..155 - wholly beneath the status
     *  bar, which swallows the touches, so no dictionary could be enabled,
     *  reordered, inspected or deleted at all. Settings had its first row at
     *  y=24..143 for the same reason.
     *
     *  <p>The insets arriving here already account for the action bar:
     *  ActionBarOverlayLayout folds its own height into the top inset it
     *  dispatches, so top is 324 - the 156 of status bar plus the 168 of bar -
     *  and not the 156 a plain systemBars() inset would suggest. Adding
     *  actionBarSize on top of it, which is the obvious-looking thing to do,
     *  pushes the first row to y=549 and was measured doing exactly that.
     *
     *  <p>Two activities are deliberately left alone, and the test is simply
     *  whether there is an action bar to be covered by: MainActivity and
     *  PopupActivity have none, and MainChrome already applies its own
     *  statusBarsPadding. Below API 35 the decor still insets the content
     *  itself, so this does nothing and the old behaviour stands. */
    @Override
    public void onContentChanged() {
        super.onContentChanged();
        if (Build.VERSION.SDK_INT < 35 || getActionBar() == null) {
            return;
        }
        View content = findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(content);
    }

    protected DictRepository repo() {
        return App.get(this).repo();
    }

    protected boolean isNightMode() {
        return (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
}
