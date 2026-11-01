package io.github.roviicc.colordict;

import android.app.Application;
import android.content.Context;

import io.github.roviicc.colordict.data.DictionaryRepository;
import io.github.roviicc.colordict.data.Settings;

/** Creates the app's long-lived objects once and hands them to screens. */
public final class App extends Application {

    private Settings settings;
    private DictionaryRepository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        settings = new Settings(this);
        repository = new DictionaryRepository(this, settings);
    }

    public static App from(Context context) {
        return (App) context.getApplicationContext();
    }

    public Settings settings() {
        return settings;
    }

    public DictionaryRepository repository() {
        return repository;
    }
}
