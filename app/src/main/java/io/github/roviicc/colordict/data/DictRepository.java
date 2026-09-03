package io.github.roviicc.colordict.data;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import io.github.roviicc.colordict.engine.ArticleHtml;
import io.github.roviicc.colordict.engine.DictionaryScanner;
import io.github.roviicc.colordict.engine.IndexEntry;
import io.github.roviicc.colordict.engine.Morphology;
import io.github.roviicc.colordict.engine.StarDictCollation;
import io.github.roviicc.colordict.engine.StarDictDictionary;
import io.github.roviicc.colordict.engine.StarDictInfo;

/**
 * Owns the installed dictionaries: scans storage, loads engines in the
 * background, and answers aggregated suggestion/definition queries across
 * every enabled dictionary in the user's chosen order.
 */
public final class DictRepository {

    private static final String TAG = "DictRepository";
    private static final String BUNDLED_ASSET_ROOT = "dicts";
    /** Marker written by pre-popup-en builds; counts as sample-glossary installed. */
    private static final String LEGACY_SAMPLE_MARKER = ".sample-installed";

    public interface Listener {
        void onDictionariesChanged();
    }

    public interface Callback<T> {
        void onResult(T result);
    }

    /** One suggestion row: a word plus the label colors of the dictionaries holding it. */
    public static final class Suggestion {
        public final String word;
        public final List<Integer> colors;

        Suggestion(String word, List<Integer> colors) {
            this.word = word;
            this.colors = colors;
        }
    }

    /** One rendered article. */
    public static final class RenderedEntry {
        public final String headword;
        public final String html;
        /** "emerged — past tense or past participle of emerge", or null. */
        public final String formLine;

        RenderedEntry(String headword, String html, String formLine) {
            this.headword = headword;
            this.html = html;
            this.formLine = formLine;
        }
    }

    /** All articles one dictionary has for the query. */
    public static final class DictHit {
        public final InstalledDict dict;
        public final List<RenderedEntry> entries;

        DictHit(InstalledDict dict, List<RenderedEntry> entries) {
            this.dict = dict;
            this.entries = entries;
        }
    }

    /** The aggregated definition result across all enabled dictionaries. */
    public static final class DefineResult {
        public final String word;
        public final List<DictHit> hits;
        /** Alphabetical neighbours, filled when {@code hits} is empty. */
        public final List<String> similar;

        DefineResult(String word, List<DictHit> hits, List<String> similar) {
            this.word = word;
            this.hits = hits;
            this.similar = similar;
        }
    }

    private final Context app;
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "dict-io");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private final Handler main = new Handler(Looper.getMainLooper());
    private final DictRegistry registry;
    private final HistoryStore history;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong queryGeneration = new AtomicLong();

    private volatile List<InstalledDict> dictionaries = Collections.emptyList();
    private volatile boolean scanned;

    public DictRepository(Context context) {
        app = context.getApplicationContext();
        registry = new DictRegistry(app);
        history = new HistoryStore(app);
    }

    public void initAsync() {
        exec.execute(this::scanNow);
    }

    public void rescan() {
        exec.execute(this::scanNow);
    }

    public boolean isScanned() {
        return scanned;
    }

    public HistoryStore history() {
        return history;
    }

    public DictRegistry registry() {
        return registry;
    }

    public void addListener(Listener l) {
        listeners.addIfAbsent(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyChanged() {
        main.post(() -> {
            for (Listener l : listeners) {
                l.onDictionariesChanged();
            }
        });
    }

    /** Runs {@code task} on the repository's background thread. */
    public void runInBackground(Runnable task) {
        exec.execute(task);
    }

    // ------------------------------------------------------------ storage

    public File internalDictDir() {
        File dir = new File(app.getFilesDir(), "dictionaries");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    public File externalDictDir() {
        return app.getExternalFilesDir("dictionaries");
    }

    private void scanNow() {
        installBundledIfNeeded();

        List<InstalledDict> found = new ArrayList<>();
        scanRoot("int", internalDictDir(), "internal", found);
        File ext = externalDictDir();
        if (ext != null) {
            scanRoot("ext", ext, "sdcard", found);
        }

        List<String> ids = new ArrayList<>(found.size());
        for (InstalledDict d : found) {
            ids.add(d.id);
        }
        registry.register(ids);
        for (InstalledDict d : found) {
            DictRegistry.Entry e = registry.entryFor(d.id);
            d.order = e.order;
            d.color = e.color;
            d.enabled = e.enabled;
        }
        found.sort((a, b) -> Integer.compare(a.order, b.order));

        // Keep engines that were already loaded for unchanged files.
        List<InstalledDict> old = dictionaries;
        for (InstalledDict d : found) {
            for (InstalledDict o : old) {
                if (o.id.equals(d.id) && o.engine != null
                        && o.ifoFile.equals(d.ifoFile)) {
                    d.engine = o.engine;
                    break;
                }
            }
        }

        dictionaries = found;
        scanned = true;
        notifyChanged();
    }

    private void scanRoot(String tag, File root, String label, List<InstalledDict> out) {
        String rootPath = root.getAbsolutePath();
        for (File ifo : DictionaryScanner.findIfoFiles(root)) {
            String rel = ifo.getAbsolutePath().startsWith(rootPath)
                    ? ifo.getAbsolutePath().substring(rootPath.length() + 1)
                    : ifo.getAbsolutePath();
            String id = tag + ":" + rel;
            try {
                StarDictInfo info = StarDictInfo.parse(ifo);
                String parent = ifo.getParentFile() != null
                        && !ifo.getParentFile().equals(root)
                        ? "/" + ifo.getParentFile().getName() : "";
                out.add(new InstalledDict(id, ifo, info, label + parent));
            } catch (IOException e) {
                Log.w(TAG, "skipping bad .ifo " + ifo + ": " + e.getMessage());
            }
        }
    }

    /** Copies every dictionary bundled under assets/dicts/ into internal
     *  storage once, so new bundled dictionaries appear after an upgrade
     *  without disturbing ones the user deleted or recolored. */
    private void installBundledIfNeeded() {
        AssetManager assets = app.getAssets();
        String[] dirs;
        try {
            dirs = assets.list(BUNDLED_ASSET_ROOT);
        } catch (IOException e) {
            Log.w(TAG, "could not list bundled dictionaries", e);
            return;
        }
        if (dirs == null) {
            return;
        }
        boolean legacyMarker = new File(internalDictDir(), LEGACY_SAMPLE_MARKER).exists();
        for (String dir : dirs) {
            File marker = new File(internalDictDir(), ".installed-" + dir);
            if (marker.exists() || (legacyMarker && "sample-glossary".equals(dir))) {
                continue;
            }
            File target = new File(internalDictDir(), dir);
            //noinspection ResultOfMethodCallIgnored
            target.mkdirs();
            try {
                String[] names = assets.list(BUNDLED_ASSET_ROOT + "/" + dir);
                if (names != null) {
                    for (String name : names) {
                        try (InputStream in = assets.open(
                                BUNDLED_ASSET_ROOT + "/" + dir + "/" + name);
                             OutputStream outStream = new FileOutputStream(
                                     new File(target, name))) {
                            byte[] buf = new byte[16 * 1024];
                            int n;
                            while ((n = in.read(buf)) > 0) {
                                outStream.write(buf, 0, n);
                            }
                        }
                    }
                }
                try (FileOutputStream m = new FileOutputStream(marker)) {
                    m.write('1');
                }
            } catch (IOException e) {
                Log.w(TAG, "could not install bundled dictionary " + dir, e);
            }
        }
    }

    // ------------------------------------------------------------ dictionaries

    public List<InstalledDict> dictionaries() {
        return dictionaries;
    }

    public List<InstalledDict> enabledDictionaries() {
        List<InstalledDict> out = new ArrayList<>();
        for (InstalledDict d : dictionaries) {
            if (d.enabled) {
                out.add(d);
            }
        }
        return out;
    }

    public void setEnabled(InstalledDict d, boolean enabled) {
        d.enabled = enabled;
        registry.setEnabled(d.id, enabled);
        notifyChanged();
    }

    public void setColor(InstalledDict d, int color) {
        d.color = color;
        registry.setColor(d.id, color);
        notifyChanged();
    }

    /** Moves the dictionary up (-1) or down (+1) in priority order. */
    public void move(InstalledDict d, int delta) {
        List<InstalledDict> list = new ArrayList<>(dictionaries);
        int at = list.indexOf(d);
        int to = at + delta;
        if (at < 0 || to < 0 || to >= list.size()) {
            return;
        }
        Collections.swap(list, at, to);
        List<String> ids = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            list.get(i).order = i;
            ids.add(list.get(i).id);
        }
        registry.setOrder(ids);
        dictionaries = list;
        notifyChanged();
    }

    /** Deletes the dictionary's files (app-owned storage only) and rescans. */
    public void delete(InstalledDict d) {
        exec.execute(() -> {
            if (d.engine != null) {
                try {
                    d.engine.close();
                } catch (IOException ignored) {
                }
                d.engine = null;
            }
            File dir = d.ifoFile.getParentFile();
            String name = d.ifoFile.getName();
            String base = name.substring(0, name.length() - ".ifo".length());
            String[] exts = {".ifo", ".idx", ".idx.gz", ".dict", ".dict.dz", ".syn"};
            for (String ext : exts) {
                File f = new File(dir, base + ext);
                if (f.isFile()) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
            registry.remove(d.id);
            scanNow();
        });
    }

    private void ensureLoaded(InstalledDict d) {
        if (d.engine != null || d.loadError != null) {
            return;
        }
        try {
            d.engine = StarDictDictionary.load(d.ifoFile);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "failed to load " + d.ifoFile, e);
            d.loadError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            notifyChanged();
        }
    }

    // ------------------------------------------------------------ queries

    private static int collate(String a, String b) {
        return StarDictCollation.compare(a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    /** Asynchronously computes merged prefix suggestions across enabled dictionaries. */
    public void suggest(String prefix, int max, Callback<List<Suggestion>> callback) {
        long gen = queryGeneration.incrementAndGet();
        exec.execute(() -> {
            Map<String, Set<Integer>> merged = new TreeMap<>(DictRepository::collate);
            for (InstalledDict d : enabledDictionaries()) {
                ensureLoaded(d);
                if (d.engine == null) {
                    continue;
                }
                for (String w : d.engine.suggest(prefix, max)) {
                    merged.computeIfAbsent(w, k -> new LinkedHashSet<>()).add(d.color);
                }
            }
            List<Suggestion> out = new ArrayList<>(Math.min(max, merged.size()));
            for (Map.Entry<String, Set<Integer>> e : merged.entrySet()) {
                if (out.size() >= max) {
                    break;
                }
                out.add(new Suggestion(e.getKey(), new ArrayList<>(e.getValue())));
            }
            postIfCurrent(gen, callback, out);
        });
    }

    /** Asynchronously renders the aggregated definition of {@code word}. */
    public void define(String word, Callback<DefineResult> callback) {
        long gen = queryGeneration.incrementAndGet();
        exec.execute(() -> {
            List<DictHit> hits = new ArrayList<>();
            for (InstalledDict d : enabledDictionaries()) {
                ensureLoaded(d);
                if (d.engine == null) {
                    continue;
                }
                List<IndexEntry> entries = d.engine.lookup(word);
                if (entries.isEmpty()) {
                    continue;
                }
                List<RenderedEntry> rendered = new ArrayList<>(entries.size());
                for (IndexEntry entry : entries) {
                    String html;
                    try {
                        html = d.engine.articleHtml(entry);
                    } catch (IOException | RuntimeException e) {
                        html = "<i>" + ArticleHtml.escape("error reading article: "
                                + e.getMessage()) + "</i>";
                    }
                    // An inflected search resolves through the .syn index to a
                    // headword the reader may not know; say how it got there.
                    String formLine = Morphology.formLine(word, entry.word,
                            Morphology.partsOfSpeech(html));
                    rendered.add(new RenderedEntry(entry.word, html, formLine));
                }
                hits.add(new DictHit(d, rendered));
            }

            List<String> similar = Collections.emptyList();
            if (hits.isEmpty()) {
                Set<String> near = new TreeSet<>(DictRepository::collate);
                for (InstalledDict d : enabledDictionaries()) {
                    if (d.engine != null) {
                        near.addAll(d.engine.nearWords(word, 8));
                    }
                }
                similar = new ArrayList<>(near);
                if (similar.size() > 20) {
                    similar = similar.subList(0, 20);
                }
            }
            postIfCurrent(gen, callback, new DefineResult(word, hits, similar));
        });
    }

    private <T> void postIfCurrent(long gen, Callback<T> callback, T value) {
        if (gen == queryGeneration.get()) {
            main.post(() -> {
                if (gen == queryGeneration.get()) {
                    callback.onResult(value);
                }
            });
        }
    }
}
