package io.github.roviicc.colordict.data;

import java.io.File;

import io.github.roviicc.colordict.engine.StarDictDictionary;
import io.github.roviicc.colordict.engine.StarDictInfo;

/** A dictionary discovered on storage, plus its user settings and lazy-loaded engine. */
public final class InstalledDict {

    /** Stable id: storage root tag + relative path of the .ifo file. */
    public final String id;
    public final File ifoFile;
    public final StarDictInfo info;
    /** Short human-readable location, e.g. "internal/sample-glossary". */
    public final String location;

    public int color;
    public boolean enabled;
    public int order;

    public volatile StarDictDictionary engine;
    public volatile String loadError;

    public InstalledDict(String id, File ifoFile, StarDictInfo info, String location) {
        this.id = id;
        this.ifoFile = ifoFile;
        this.info = info;
        this.location = location;
    }

    public String name() {
        return info.bookname;
    }

    public int wordCount() {
        return engine != null ? engine.wordCount() : info.wordcount;
    }
}
