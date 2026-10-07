/* Fills in the download button from release.json, written beside the APK by
   tools/web_release.py, so the APK is served from this site's own origin.
   If release.json cannot be read, the button falls back to the GitHub
   releases page, and the rest of the page is unaffected. */
(function () {
    "use strict";

    var REPO = "Roviicc/ColorDict";
    var RELEASES_URL = "https://github.com/" + REPO + "/releases";
    var META_URL = "release.json";

    var button = document.getElementById("primary-btn");
    var meta = document.getElementById("release-meta");
    var container = document.getElementById("download");

    function formatSize(bytes) {
        if (!bytes && bytes !== 0) return "";
        if (bytes < 1024 * 1024) return Math.round(bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)).toFixed(1) + " MB";
    }

    function formatDate(iso) {
        if (!iso) return "";
        var d = new Date(iso);
        if (isNaN(d.getTime())) return "";
        return d.toLocaleDateString(undefined,
            { year: "numeric", month: "long", day: "numeric" });
    }

    function fallback(message) {
        button.href = RELEASES_URL;
        button.textContent = "Download from GitHub";
        meta.textContent = message;
        meta.className = "meta error";
    }

    function render(release) {
        if (!release || !release.file) {
            fallback("Open the releases page to download.");
            return;
        }

        button.href = release.file;
        button.textContent = "Download " + (release.tag || "APK");
        button.setAttribute("download", "");

        var parts = [];
        if (release.tag) parts.push(release.tag);
        parts.push(formatSize(release.size));
        var published = formatDate(release.published_at);
        if (published) parts.push("released " + published);
        meta.textContent = parts.join(" · ");
        meta.className = "meta";

        // The unsigned release APK and the full notes stay on GitHub.
        if (release.releases_url) {
            var list = document.createElement("ul");
            list.className = "assets";
            var li = document.createElement("li");
            var link = document.createElement("a");
            link.href = release.releases_url;
            link.rel = "noopener";
            link.textContent = "Release notes and the unsigned APK on GitHub";
            li.appendChild(link);
            list.appendChild(li);
            container.appendChild(list);
        }
    }

    if (!window.fetch) {
        fallback("Open the releases page to download.");
        return;
    }

    fetch(META_URL, { cache: "no-cache" })
        .then(function (response) {
            if (!response.ok) throw new Error("HTTP " + response.status);
            return response.json();
        })
        .then(render)
        .catch(function () {
            fallback("Could not read the release just now — open the releases page.");
        });
}());
