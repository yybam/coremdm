// The version of the app this site hands out, from version.json (written from the latest
// release by .github/workflows/deploy-hosting.yml). Fills every [data-app-version] with "v0.8.0";
// other scripts can await window.appVersion. Elements keep their fallback text if it fails.
// Falls back to Firebase Hosting, where the deploy writes version.json, when this copy lacks it.
const fetchVersion = url => fetch(url, { cache: 'no-cache' }).then(r => (r.ok ? r.json() : null));
window.appVersion = fetchVersion('version.json')
  .catch(() => null)
  .then(j => j || fetchVersion('https://coremdm.web.app/version.json'))
  .then(j => (j && j.version) || null)
  .catch(() => null);
window.appVersion.then(v => {
  if (v) document.querySelectorAll('[data-app-version]').forEach(el => { el.textContent = 'v' + v; });
});
