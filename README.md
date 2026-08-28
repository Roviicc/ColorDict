# ColorDict (open-source clone)

A free, open-source, offline multi-dictionary viewer for Android, inspired by
the classic **ColorDict** app: type a word once and see color-coded results
from *every* enabled dictionary at the same time.

> This is an independent re-implementation written from scratch. It is **not
> affiliated with or endorsed by** the authors of the original ColorDict
> application.

## Features

- **StarDict dictionary support** — the de-facto standard free dictionary
  format:
  - `.ifo` / `.idx` / `.dict` (and gzip-compressed `.idx.gz`)
  - **dictzip** `.dict.dz` files with true random access (no full decompression)
  - `.syn` synonym files, 32-bit and 64-bit index offsets
  - all common article types: plain text, HTML, XDXF, Pango markup, phonetics,
    MediaWiki text, WordNet, and typed multi-block articles
- **Aggregated, color-coded results** — every enabled dictionary answers at
  once; each result card carries its dictionary's color, and suggestions show
  a colored dot per dictionary that knows the word
- **Fast incremental search** with case-insensitive prefix matching, synonym
  matching, and "similar words" when nothing matches exactly
- **Dictionary manager** — import via the system file picker (single files or
  whole folders), enable/disable, reorder priority, recolor, inspect, delete
- **History & bookmarks**, with configurable history size
- **Cross-reference links** (`bword://`) inside articles jump to the linked word
- **Text-to-speech** pronunciation, clipboard, share, and Wikipedia/Wiktionary/
  web search shortcuts
- **Third-party lookup API** compatible with the well-known ColorDict intent
  (see below) — e-book readers that support ColorDict popups work out of the box
- **Light/dark theme**, adjustable definition text size
- **Zero dependencies, no ads, no network permission** — the app never touches
  the internet except when *you* tap a web link
- Ships with a tiny built-in sample glossary so the UI works on first launch

## Getting dictionaries

The app reads dictionaries in StarDict format. Many free dictionaries are
available on the web (e.g. conversions of FreeDict, Wiktionary, GCIDE, and
WordNet). A dictionary is a set of files sharing one base name:

```
mydict.ifo        metadata (required)
mydict.idx        word index (or mydict.idx.gz)
mydict.dict       articles  (or mydict.dict.dz)
mydict.syn        synonyms  (optional)
```

Add them in **⋮ → Dictionaries → Import dictionary files…** (or *Import
folder…*), or copy the files with `adb push` to the app's dictionary folder
(shown under *How to add dictionaries* in the app) and tap *Rescan storage*.

## Lookup API for other apps

Any app can request a floating definition popup:

```java
Intent intent = new Intent("colordict.intent.action.SEARCH");
intent.putExtra("EXTRA_QUERY", "hello");
// optional:
intent.putExtra("EXTRA_FULLSCREEN", false);   // true opens the full app
intent.putExtra("EXTRA_WIDTH", widthPx);      // popup geometry
intent.putExtra("EXTRA_HEIGHT", heightPx);
intent.putExtra("EXTRA_GRAVITY", Gravity.BOTTOM);
intent.putExtra("EXTRA_MARGIN_BOTTOM", marginPx);
startActivity(intent);
```

Selected text in any app also gets a **Define** entry (Android's
process-text menu), and plain text can be shared to the app.

## Installing on a phone

Grab the ready-to-install **debug APK** from the
[Releases page](https://github.com/roviicc/colordict/releases) (or from any
CI run's artifacts), allow installing from unknown sources, and open it.
Pushing a `v*` tag builds and publishes a new release automatically.

## Trying it on a computer

### With Android Studio (the full app)

1. **File → Open** and select this folder, then let Gradle sync (it downloads
   the Android Gradle Plugin and SDK pieces on first run).
2. If it asks for an SDK, install **Android SDK Platform 35** via
   *Tools → SDK Manager*.
3. Create an emulator in *Tools → Device Manager* (any device image, API 24+),
   then press **Run ▶**.
4. To test the popup API that other apps use, run this against the emulator:

   ```bash
   adb shell am start -a colordict.intent.action.SEARCH -e EXTRA_QUERY serene
   ```

Hardware acceleration matters: on Linux the emulator needs KVM
(`ls /dev/kvm`), and on Windows/macOS it uses the platform hypervisor. If the
emulator will not start, plug in a real phone with USB debugging instead —
`./gradlew installDebug` puts the app on it.

### Without the Android SDK (desktop harness)

`run-desktop.sh` runs the **same StarDict engine and color-coded renderer**
the app uses, in a small Swing window. It needs nothing but a **JDK 17+** —
no Android SDK, no Gradle, no emulator — so it is the quickest way to check
dictionary parsing and lookups, or to try a dictionary file before copying it
to your phone.

```bash
./run-desktop.sh                        # window, with the bundled sample glossary
./run-desktop.sh --dict ~/my-dicts      # also load your own dictionaries
./run-desktop.sh --lookup serene        # print one definition in the terminal
./run-desktop.sh --list                 # list the dictionaries that loaded
```

Windows: use `run-desktop.bat` with the same options.

## Building from the command line

```bash
./gradlew assembleDebug     # installable APK at app/build/outputs/apk/debug/
./gradlew test              # StarDict engine unit tests (pure JVM)
```

Requirements: JDK 17+ and the Android SDK (compileSdk 35). CI builds a
ready-to-install debug APK on every push — grab it from the workflow run's
artifacts. Release builds are unsigned; sign with your own key to distribute.

## Project layout

```
app/src/main/java/io/github/roviicc/colordict/
  engine/   Pure-Java StarDict engine (no Android imports, unit-tested):
            .ifo/.idx/.syn parsing, StarDict collation + binary search,
            dictzip random access, article parsing, and the color-coded
            definition renderer shared with the desktop harness
  data/     Repository, registry (order/color/enabled), history store, prefs
  ui/       Activities and views (Android framework only, no libraries)
desktop/    Swing harness that runs the engine on a PC with only a JDK
tools/      Python utilities: build/verify StarDict files, generate the
            bundled sample glossary, test fixtures, and launcher icons
```

Development conveniences:

```bash
python3 tools/stardict_make.py words.tsv out/ mydict --bookname "My Dict"
python3 tools/verify_stardict.py out/mydict.ifo --dump 10
python3 tools/gen_fixtures.py     # regenerate test fixtures + sample glossary
```

## License

[MIT](LICENSE). The bundled sample glossary was written for this project.
"StarDict" refers to the open dictionary file format; all trademarks belong
to their respective owners.
