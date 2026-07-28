(() => {
  const releasesUrl = "https://github.com/digitalesIch/open-babyphone/releases";
  const releaseApiUrl = "https://api.github.com/repos/digitalesIch/open-babyphone/releases?per_page=10";
  const allowedUrl = /^https:\/\/github\.com\/digitalesIch\/open-babyphone\/releases(?:\/|$)/;

  const elements = {
    card: document.getElementById("release-card"),
    badge: document.getElementById("release-badge"),
    date: document.getElementById("release-date"),
    title: document.getElementById("release-title"),
    tag: document.getElementById("release-tag"),
    download: document.getElementById("release-download"),
    link: document.getElementById("release-link"),
    status: document.getElementById("release-status")
  };

  const setHref = (element, value) => {
    if (element && allowedUrl.test(value)) element.href = value;
  };

  const formatDate = (value) => new Intl.DateTimeFormat("en", {
    year: "numeric",
    month: "short",
    day: "numeric"
  }).format(new Date(value));

  const finishLoading = () => {
    elements.card?.setAttribute("aria-busy", "false");
  };

  const renderRelease = (release) => {
    const apk = release.assets?.find((asset) => asset.name.toLowerCase().endsWith(".apk"));
    const releaseUrl = allowedUrl.test(release.html_url) ? release.html_url : releasesUrl;
    const downloadUrl = apk && allowedUrl.test(apk.browser_download_url) ? apk.browser_download_url : releaseUrl;
    const isPreview = Boolean(release.prerelease);

    elements.badge.textContent = isPreview ? "Preview" : "Stable";
    elements.badge.classList.toggle("preview", isPreview);
    elements.date.textContent = release.published_at ? `Published ${formatDate(release.published_at)}` : "Published release";
    elements.title.textContent = release.name || release.tag_name || "Open Babyphone release";
    elements.tag.textContent = release.tag_name || "GitHub release";
    elements.download.textContent = apk ? "Download APK" : "View downloads";
    elements.link.textContent = "Release notes";
    elements.status.textContent = isPreview
      ? "This is a preview build. Review the release notes before installing."
      : "Review the release notes before installing the APK.";

    setHref(elements.download, downloadUrl);
    setHref(elements.link, releaseUrl);
    finishLoading();
  };

  const renderError = () => {
    elements.card?.classList.add("error");
    elements.badge.textContent = "Releases";
    elements.date.textContent = "GitHub release information unavailable";
    elements.title.textContent = "Open Babyphone downloads";
    elements.tag.textContent = "Open the releases page to view available builds.";
    elements.download.textContent = "View downloads";
    elements.link.textContent = "All releases";
    elements.status.textContent = "The live release check failed. Download links remain available on GitHub.";
    setHref(elements.download, releasesUrl);
    setHref(elements.link, releasesUrl);
    finishLoading();
  };

  fetch(releaseApiUrl, { headers: { Accept: "application/vnd.github+json" } })
    .then((response) => {
      if (!response.ok) throw new Error("Release request failed");
      return response.json();
    })
    .then((releases) => {
      const published = releases.filter((release) => !release.draft);
      const release = published.find((candidate) => !candidate.prerelease) || published[0];
      if (!release) throw new Error("No published release found");
      renderRelease(release);
    })
    .catch(renderError);
})();
