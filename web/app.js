/* Fills in the download button from the newest GitHub release, so publishing a
   release updates this page with no redeploy. Everything degrades to a plain
   link to the releases page if the API is unreachable or rate-limited. */
(function () {
    "use strict";

    var REPO = "Roviicc/ColorDict";
    var RELEASES_URL = "https://github.com/" + REPO + "/releases";
    var API_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";

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

    function isDebug(name) {
        return /debug/i.test(name);
    }

    function fallback(message) {
        button.href = RELEASES_URL;
        button.textContent = "Download from GitHub";
        meta.textContent = message;
        meta.className = "meta error";
    }

    function render(release) {
        var apks = (release.assets || []).filter(function (a) {
            return /\.apk$/i.test(a.name);
        });
        if (apks.length === 0) {
            fallback("No APK attached to " + (release.tag_name || "the latest release") + ".");
            return;
        }

        // Prefer the debug build: it is signed and installs directly.
        var primary = apks.filter(function (a) { return isDebug(a.name); })[0] || apks[0];

        button.href = primary.browser_download_url;
        button.textContent = "Download " + (release.tag_name || "APK");
        button.setAttribute("download", "");

        var parts = [];
        if (release.tag_name) parts.push(release.tag_name);
        parts.push(formatSize(primary.size));
        var published = formatDate(release.published_at);
        if (published) parts.push("released " + published);
        meta.textContent = parts.join(" · ");
        meta.className = "meta";

        // List any other builds (the unsigned release APK) underneath.
        var others = apks.filter(function (a) { return a !== primary; });
        if (others.length > 0) {
            var list = document.createElement("ul");
            list.className = "assets";
            others.forEach(function (a) {
                var li = document.createElement("li");
                var link = document.createElement("a");
                link.href = a.browser_download_url;
                link.textContent = a.name;
                link.setAttribute("download", "");
                var size = document.createElement("span");
                size.className = "size";
                size.textContent = " (" + formatSize(a.size) + ")";
                li.appendChild(link);
                li.appendChild(size);
                list.appendChild(li);
            });
            container.appendChild(list);
        }
    }

    if (!window.fetch) {
        fallback("Open the releases page to download.");
        return;
    }

    fetch(API_URL, { headers: { Accept: "application/vnd.github+json" } })
        .then(function (response) {
            if (!response.ok) throw new Error("HTTP " + response.status);
            return response.json();
        })
        .then(render)
        .catch(function () {
            fallback("Could not reach GitHub just now — open the releases page.");
        });
}());
