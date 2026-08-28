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

## Installing

Grab the ready-to-install **debug APK** from the
[Releases page](https://github.com/roviicc/colordict/releases) (or from any
CI run's artifacts), allow installing from unknown sources, and open it.
Pushing a `v*` tag builds and publishes a new release automatically.

## Building

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
            dictzip random access, article parsing and HTML rendering
  data/     Repository, registry (order/color/enabled), history store, prefs
  ui/       Activities and views (Android framework only, no libraries)
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
