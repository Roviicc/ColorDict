package io.github.roviicc.colordict;

import android.app.Application;
import android.content.Context;

import io.github.roviicc.colordict.data.DictRepository;

public class App extends Application {

    private DictRepository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        repository = new DictRepository(this);
        repository.initAsync();
    }

    public DictRepository repo() {
        return repository;
    }

    public static App get(Context context) {
        return (App) context.getApplicationContext();
    }
}
