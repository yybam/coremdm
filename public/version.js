// The version of the app this site hands out, from version.json (kept equal to the latest
// release by .github/workflows/sync-apk.yml). Fills every [data-app-version] with "v0.0.45";
// other scripts can await window.appVersion. Elements keep their fallback text if it fails.
window.appVersion = fetch('version.json', { cache: 'no-cache' })
  .then(r => (r.ok ? r.json() : null))
  .then(j => (j && j.version) || null)
  .catch(() => null);
window.appVersion.then(v => {
  if (v) document.querySelectorAll('[data-app-version]').forEach(el => { el.textContent = 'v' + v; });
});
