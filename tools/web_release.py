#!/usr/bin/env python3
"""Put a release's APK on the download site, so Vercel serves it itself.

The site at web/ used to fill its button from the GitHub Releases API at page
load. Now the APK lives in web/ and is served from the site's own origin; the
page reads web/release.json for the version, size and date. Run this after
every release, then commit web/colordict-debug.apk and web/release.json.

Usage:
    python3 tools/web_release.py                # the latest release
    python3 tools/web_release.py --tag v1.4.1   # a named release
    python3 tools/web_release.py --apk path/to/app-debug.apk --tag v1.4.1
                                                # a file already on disk
"""

import argparse
import hashlib
import json
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WEB = ROOT / "web"
APK = WEB / "colordict-debug.apk"
META = WEB / "release.json"
REPO = "Roviicc/ColorDict"
API = f"https://api.github.com/repos/{REPO}/releases"


def fetch_json(url):
    req = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def download(url, dest):
    with urllib.request.urlopen(url, timeout=600) as r, open(dest, "wb") as f:
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            f.write(chunk)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--tag", help="release tag (default: the latest release)")
    ap.add_argument("--apk", type=Path, help="use this APK instead of downloading")
    args = ap.parse_args()

    published = None
    if args.apk:
        if not args.tag:
            sys.exit("--apk needs --tag")
        tag = args.tag
        APK.write_bytes(args.apk.read_bytes())
    else:
        release = fetch_json(f"{API}/tags/{args.tag}" if args.tag else f"{API}/latest")
        tag = release["tag_name"]
        published = release.get("published_at")
        debug = [a for a in release.get("assets", []) if a["name"] == "colordict-debug.apk"]
        if not debug:
            sys.exit(f"{tag} has no colordict-debug.apk asset")
        print(f"downloading {debug[0]['browser_download_url']}")
        download(debug[0]["browser_download_url"], APK)
        if APK.stat().st_size != debug[0]["size"]:
            sys.exit(f"size mismatch: got {APK.stat().st_size}, release says {debug[0]['size']}")

    meta = {
        "tag": tag,
        "file": APK.name,
        "size": APK.stat().st_size,
        "sha256": sha256(APK),
        "published_at": published or datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "releases_url": f"https://github.com/{REPO}/releases/tag/{tag}",
    }
    META.write_text(json.dumps(meta, indent=2) + "\n")
    print(f"{APK.relative_to(ROOT)}: {meta['size']:,} B, sha256 {meta['sha256'][:12]}...")
    print(f"{META.relative_to(ROOT)}: {tag}, published {meta['published_at']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
