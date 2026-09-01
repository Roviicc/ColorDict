#!/usr/bin/env sh
# Build and run the ColorDict desktop harness.
#
# Requires only a JDK 17+ (no Android SDK, no Gradle, no emulator). It runs
# the very same StarDict engine the Android app uses, so dictionary parsing,
# lookup and the color-coded rendering can be tried directly on a PC.
#
#   ./run-desktop.sh                     open the window (bundled sample glossary)
#   ./run-desktop.sh --dict ~/dicts      also load your own dictionaries
#   ./run-desktop.sh --lookup serene     print one definition to the terminal
#   ./run-desktop.sh --list              list the dictionaries that loaded
set -eu

cd "$(dirname "$0")"

JAVAC="javac"
JAVA="java"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAVA="$JAVA_HOME/bin/java"
fi

if ! command -v "$JAVAC" >/dev/null 2>&1 && [ ! -x "$JAVAC" ]; then
    echo "error: no JDK found. Install a JDK 17 or newer (javac must be on PATH)." >&2
    echo "  Debian/Ubuntu: sudo apt install openjdk-17-jdk" >&2
    echo "  macOS:         brew install openjdk@17" >&2
    echo "  Windows:       https://adoptium.net/" >&2
    exit 1
fi

OUT="build/desktop-classes"
mkdir -p "$OUT"

# Recompile only when a source file is newer than the last build.
STAMP="$OUT/.build-stamp"
NEEDS_BUILD=1
if [ -f "$STAMP" ]; then
    NEEDS_BUILD=0
    for src in $(find app/src/main/java/io/github/roviicc/colordict/engine \
                      app/src/main/java/io/github/roviicc/colordict/data/Palette.java \
                      desktop/src -name '*.java'); do
        if [ "$src" -nt "$STAMP" ]; then
            NEEDS_BUILD=1
            break
        fi
    done
fi

if [ "$NEEDS_BUILD" -eq 1 ]; then
    echo "Compiling the ColorDict engine and desktop harness…"
    "$JAVAC" -encoding UTF-8 -nowarn -d "$OUT" \
        $(find app/src/main/java/io/github/roviicc/colordict/engine -name '*.java') \
        app/src/main/java/io/github/roviicc/colordict/data/Palette.java \
        $(find desktop/src -name '*.java')
    touch "$STAMP"
fi

exec "$JAVA" -cp "$OUT" io.github.roviicc.colordict.desktop.DesktopApp "$@"
