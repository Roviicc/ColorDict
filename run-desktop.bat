@echo off
REM Build and run the ColorDict desktop harness on Windows.
REM Requires only a JDK 17+ (no Android SDK, no Gradle, no emulator).
REM
REM   run-desktop.bat                    open the window (bundled sample glossary)
REM   run-desktop.bat --dict C:\dicts    also load your own dictionaries
REM   run-desktop.bat --lookup serene    print one definition to the terminal

setlocal
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    echo error: no JDK found. Install a JDK 17 or newer from https://adoptium.net/
    exit /b 1
)

set OUT=build\desktop-classes
if not exist "%OUT%" mkdir "%OUT%"

set SOURCES=%TEMP%\colordict-sources.txt
dir /s /b app\src\main\java\io\github\roviicc\colordict\engine\*.java > "%SOURCES%"
echo app\src\main\java\io\github\roviicc\colordict\data\Palette.java >> "%SOURCES%"
dir /s /b desktop\src\*.java >> "%SOURCES%"

echo Compiling the ColorDict engine and desktop harness...
javac -encoding UTF-8 -nowarn -d "%OUT%" "@%SOURCES%"
if errorlevel 1 exit /b 1

java -cp "%OUT%" io.github.roviicc.colordict.desktop.DesktopApp %*
