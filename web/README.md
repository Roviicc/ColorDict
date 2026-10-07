# Download site

The download page for **Pop Up Dictionary**, offering the latest APK. It is plain HTML, CSS and one small
script — no framework, no build step, nothing to install.

The APK is served from this folder, so the site is the download, not a
pointer to GitHub. `colordict-debug.apk` sits beside the page and
`release.json` (version, size, date, sha256) fills in the button at page load.
If `release.json` cannot be read, the button falls back to the repository's
releases page, and the rest of the page is unaffected.

**After every release**, run

```bash
python3 tools/web_release.py            # fetches the latest release's debug APK
git add web/colordict-debug.apk web/release.json
```

and commit; Vercel redeploys from `main` and serves the new file. The unsigned
release APK and the full notes stay on GitHub Releases, linked under the button.

## Deploying to Vercel

The site needs no build. A `vercel.json` at the repository root already
points Vercel's output directory at this folder, so importing the repo with
default settings works:

1. Go to [vercel.com/new](https://vercel.com/new) and import
   `Roviicc/ColorDict` (authorise the GitHub app if prompted).
2. Leave every setting at its default and click **Deploy**.

(Setting **Root Directory** to `web` in the project settings also works —
this folder's own `vercel.json` then applies instead.)

Vercel then rebuilds on every push. Check **Settings → Git → Production Branch**
points at the branch you want live (`main` unless you decide otherwise).

`vercel.json` in this folder sets clean URLs, cache headers, and a few
security headers.

### Custom domain

**Settings → Domains → Add**, then create the DNS record Vercel shows you.
HTTPS is provisioned automatically.

## Alternative: GitHub Pages

If you would rather not use a second service, this folder can be served by
GitHub Pages instead — **Settings → Pages → Deploy from a branch**, pick the
branch and set the folder to `/web`. The page works identically; only the URL
differs.

## Local preview

```bash
cd web
python3 -m http.server 8000
# then open http://localhost:8000
```
