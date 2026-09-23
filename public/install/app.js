// CoreMDM WebUSB installer — ya-webadb (Tango) 2.x
// Flow: connect, then everything else runs automatically in one pipeline —
// scan -> clear account blockers -> install APK -> set device owner -> done.
// The pipeline only ever stops to ask for something software can't do itself
// (pick an APK file, or remove an account by hand) or to report a failure.
// Rescue: re-enable disabled apps + remove-active-admin (works on testOnly builds).

// Self-built single bundle — not esm.sh. esm.sh's split build of @yume-chan/stream-extra
// ships two copies of the Consumable class, so every USB write went out as 0 bytes and the
// ADB handshake hung forever. Rebuild recipe is in the bundle's banner comment.
import {
  Adb,
  AdbDaemonTransport,
  AdbDaemonWebUsbDeviceManager,
  AdbWebCredentialStore,
  PackageManager,
} from "./vendor/tango.js";

const DO_COMPONENT = "com.core.mdm/.MdmDeviceAdmin";
const DO_PACKAGE   = "com.core.mdm";
const LS_DISABLED  = "coremdm_disabled_pkgs";

// Account types we cannot drop by disabling an app — user must remove in Settings.
const MANUAL_TYPES = [/^com\.google/i, /google/i, /samsung/i, /com\.osp/i, /com\.sec\./i, /knox/i];

// ---------- tiny DOM helpers ----------
const $  = (s) => document.querySelector(s);
const $$ = (s) => Array.from(document.querySelectorAll(s));
const esc = (s) => String(s).replace(/[&<>]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" }[c]));

function log(line) {
  const el = $("#log");
  el.textContent += line + "\n";
  el.scrollTop = el.scrollHeight;
}
function setStatus(sel, msg, kind = "") {
  const el = $(sel);
  el.className = "status " + kind;
  el.textContent = msg;
}
function findingHtml(kind, title, body) {
  return `<div class="finding ${kind}"><div class="t">${esc(title)}</div><div class="muted">${body}</div></div>`;
}

// run-status line + the three mutually-exclusive outcome blocks
function setRun(msg) {
  const el = $("#run-status");
  el.hidden = false;
  el.className = "status busy";
  el.textContent = msg;
}
function hideAllOutcomes() {
  $("#need-accounts").hidden = true;
  $("#need-apk").hidden = true;
  $("#outcome-done").hidden = true;
  $("#outcome-failed").hidden = true;
}
function showDone() {
  hideAllOutcomes();
  $("#run-status").hidden = true;
  $("#outcome-done").hidden = false;
  $("#done-summary").innerHTML =
    `<b>Owner</b><span>${esc(DO_COMPONENT)}</span>` +
    `<b>Device</b><span>${esc(deviceFacts.model || "")}</span>`;
}
function showFailed(reasonHtml) {
  hideAllOutcomes();
  $("#run-status").hidden = true;
  $("#outcome-failed").hidden = false;
  $("#failed-reason").innerHTML = reasonHtml;
  log("FAILED: " + reasonHtml.replace(/<[^>]+>/g, ""));
}

// ---------- ADB session ----------
let adb = null;
let pm = null;
let deviceFacts = {};
let isSamsung = false;
let scanAccounts = [];

async function shell(cmd) {
  // one-shot command, returns trimmed stdout+stderr text
  const out = await adb.subprocess.noneProtocol.spawnWaitText(cmd);
  log("$ " + cmd + "\n" + (out.trim() ? out.trim() : "(no output)"));
  return out;
}

// device.connect() / AdbDaemonTransport.authenticate() wait on the phone's own ADB RSA-key
// handshake, which never resolves at all if the phone's screen is locked or its "Allow USB
// debugging?" prompt is dismissed/ignored — with no timeout that looked exactly like a hang
// ("Authorising…" forever, nothing to click, no error). Bound it and say what to do.
function withTimeout(promise, ms, message) {
  let timer;
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error(message)), ms);
  });
  return Promise.race([promise, timeout]).finally(() => clearTimeout(timer));
}

async function connect() {
  const manager = AdbDaemonWebUsbDeviceManager.BROWSER || new AdbDaemonWebUsbDeviceManager(navigator.usb);
  if (!manager) throw new Error("WebUSB unavailable");

  $("#btn-connect").disabled = true;
  setRun("Select your phone in the popup…");
  const device = await manager.requestDevice();
  if (!device) { setRun(""); $("#run-status").hidden = true; $("#btn-connect").disabled = false; return; }

  setRun("Authorising — tap “Allow USB debugging” on the phone…");
  const AUTH_TIMEOUT_MS = 25_000;
  const AUTH_TIMEOUT_MSG =
    "Timed out waiting for the phone. Make sure its screen is unlocked, then tap “Allow” on the " +
    "“Allow USB debugging?” prompt (check the phone — it's easy to miss) and try again.";
  const connection = await withTimeout(device.connect(), AUTH_TIMEOUT_MS, AUTH_TIMEOUT_MSG);
  const credentialStore = new AdbWebCredentialStore("CoreMDM Installer");
  const transport = await withTimeout(
    AdbDaemonTransport.authenticate({
      serial: device.serial,
      connection,
      credentialStore,
    }),
    AUTH_TIMEOUT_MS,
    AUTH_TIMEOUT_MSG
  );
  adb = new Adb(transport);
  pm = new PackageManager(adb);

  $("#conn-pill").textContent = "Connected";
  $("#conn-pill").className = "pill pill-on";
  $("#connect-hints").hidden = true;
  $("#btn-connect").hidden = true;
  $('[data-panel="restore"]').hidden = false;
  log("Connected to " + device.serial);
  await runPipeline();
}

// ---------- device facts / policy checks ----------
async function getprop(name) {
  return (await shell(`getprop ${name}`)).trim();
}

async function detectDeviceOwner() {
  const dump = await shell("dumpsys device_policy");
  // Look for an owner admin component that is NOT ours.
  const owned = /Device Owner:\s*[\s\S]*?admin=ComponentInfo\{([^}]+)\}/i.exec(dump);
  const profileOwners = [...dump.matchAll(/Profile Owner[\s\S]*?admin=ComponentInfo\{([^}]+)\}/gi)].map((m) => m[1]);
  return {
    deviceOwner: owned ? owned[1].split("/")[0] : null,
    profileOwners: profileOwners.map((c) => c.split("/")[0]),
  };
}

function parseAccounts(dump) {
  // dumpsys account lists:  Account {name=foo@bar, type=com.google}
  const set = new Map();
  for (const m of dump.matchAll(/Account\s*\{name=([^,]+),\s*type=([^}]+)\}/g)) {
    const name = m[1].trim(), type = m[2].trim();
    set.set(name + "|" + type, { name, type });
  }
  return [...set.values()];
}
const isManualAccount = (type) => MANUAL_TYPES.some((re) => re.test(type));

// ---------- the pipeline ----------
async function runPipeline() {
  hideAllOutcomes();
  try {
    setRun("Reading device info…");
    const [manufacturer, model, release, sdk] = await Promise.all([
      getprop("ro.product.manufacturer"),
      getprop("ro.product.model"),
      getprop("ro.build.version.release"),
      getprop("ro.build.version.sdk"),
    ]);
    deviceFacts = { manufacturer, model, release, sdk };
    isSamsung = /samsung/i.test(manufacturer);
    $("#device-facts").innerHTML =
      `<b>Model</b><span>${esc(model)} (${esc(manufacturer)})</span>` +
      `<b>Android</b><span>${esc(release)} — API ${esc(sdk)}</span>`;

    setRun("Checking device policy…");
    const owner = await detectDeviceOwner();
    if (owner.deviceOwner && owner.deviceOwner !== DO_PACKAGE) {
      return showFailed(`A different Device Owner is already set: <code>${esc(owner.deviceOwner)}</code>. ` +
        `You cannot swap Device Owner without a <b>factory reset</b> — no ADB command can remove another app's Device Owner.`);
    }
    if (owner.profileOwners.filter((p) => p !== DO_PACKAGE).length) {
      return showFailed(`A work profile / profile owner exists: <code>${esc(owner.profileOwners.join(", "))}</code>. ` +
        `Remove the work profile in Settings first, then retry.`);
    }

    if (isSamsung) {
      setRun("Checking for Samsung Knox Guard…");
      const kg = (await shell("pm list packages")).toLowerCase();
      const hasKG = kg.includes("com.samsung.android.kgclient") || kg.includes("knoxguard");
      const kgEnabled = hasKG && !(await shell("pm list packages -d")).toLowerCase().includes("kgclient");
      if (hasKG && kgEnabled) {
        return showFailed(`This device looks carrier/financed-locked (<b>Samsung Knox Guard</b> present and active). ` +
          `Provisioning may be remotely blocked or the device re-locked. Stop unless you know it's unenrolled.`);
      }
    }

    setRun("Checking accounts…");
    scanAccounts = parseAccounts(await shell("dumpsys account"));
    if (scanAccounts.length > 0) {
      await handleAccounts();
      return; // handleAccounts() either resolves (calls back into the pipeline) or shows need-accounts and waits
    }

    await proceedToInstall();
  } catch (e) {
    log("pipeline error: " + (e.stack || e.message));
    showFailed("Unexpected error: " + esc(e.message));
  }
}

// ---------- accounts ----------
async function handleAccounts() {
  // Non-Samsung: try a silent, reversible auto-disable of the apps that plainly match an
  // account type before ever bothering the user. Samsung: skip auto-disable entirely (RKP/Knox
  // can react to package-state changes) and go straight to asking.
  if (!isSamsung) {
    const auto = await autoMatchPackages(scanAccounts);
    if (auto.size > 0) {
      setRun(`Disabling ${auto.size} account app(s)…`);
      const disabled = new Set(JSON.parse(localStorage.getItem(LS_DISABLED) || "[]"));
      for (const p of auto) {
        const r = await shell(`pm disable-user --user 0 ${p}`);
        if (/new state: disabled/i.test(r) || /disabled-user/i.test(r)) disabled.add(p);
      }
      localStorage.setItem(LS_DISABLED, JSON.stringify([...disabled]));
      scanAccounts = parseAccounts(await shell("dumpsys account"));
      if (scanAccounts.length === 0) {
        await proceedToInstall();
        return;
      }
    }
  }
  await showNeedAccounts();
}

async function autoMatchPackages(accounts) {
  const raw = await shell("pm list packages -3");
  const pkgs = raw.split("\n").map((l) => l.replace("package:", "").trim()).filter(Boolean);
  const autopick = new Set();
  for (const a of accounts) {
    if (isManualAccount(a.type)) continue;
    const base = a.type.split(/[.@]/).filter(Boolean);
    const hit = pkgs.find((p) => p === a.type || base.every((b) => p.includes(b)));
    if (hit) autopick.add(hit);
  }
  return autopick;
}

async function showNeedAccounts() {
  hideAllOutcomes();
  $("#run-status").hidden = true;
  $("#need-accounts").hidden = false;
  $("#samsung-warn").hidden = !isSamsung;

  $("#accounts-list").innerHTML = scanAccounts.map((a) => {
    const manual = isManualAccount(a.type);
    return `<div class="acct"><div><div>${esc(a.name)}</div><div class="type">${esc(a.type)}</div></div>
      <span class="tag ${manual ? "manual" : "app"}">${manual ? "remove in Settings" : "disable app"}</span></div>`;
  }).join("");

  const raw = await shell("pm list packages -3");
  const pkgs = raw.split("\n").map((l) => l.replace("package:", "").trim()).filter(Boolean).sort();
  const autopick = await autoMatchPackages(scanAccounts);
  renderPkgList(pkgs, autopick);
}

function renderPkgList(pkgs, checked) {
  $("#pkg-list").innerHTML = pkgs.map((p) =>
    `<label><input type="checkbox" value="${esc(p)}" ${checked.has(p) ? "checked" : ""}/> ${esc(p)}</label>`
  ).join("") || `<div class="muted">No third-party apps.</div>`;
}

async function disableSelected() {
  const sel = $$("#pkg-list input:checked").map((c) => c.value);
  if (!sel.length) return;
  setRun(`Disabling ${sel.length} app(s)…`);
  $("#need-accounts").hidden = true;
  const disabled = new Set(JSON.parse(localStorage.getItem(LS_DISABLED) || "[]"));
  for (const p of sel) {
    const r = await shell(`pm disable-user --user 0 ${p}`);
    if (/new state: disabled/i.test(r) || /disabled-user/i.test(r)) disabled.add(p);
  }
  localStorage.setItem(LS_DISABLED, JSON.stringify([...disabled]));
  await recheckAccounts();
}

async function recheckAccounts() {
  setRun("Re-checking accounts…");
  scanAccounts = parseAccounts(await shell("dumpsys account"));
  if (scanAccounts.length === 0) {
    await proceedToInstall();
  } else {
    await showNeedAccounts();
  }
}

// ---------- install ----------
let apkFromFolder = null;
async function tryPreloadedApk() {
  try {
    // .bin, not .apk: Firebase Hosting's free Spark plan hard-blocks serving any file whose
    // name ends in .apk/.exe/.ipa (checked by extension, not content) -- upload fails outright.
    // Neither installStream() below nor the phone's own QR/provisioning downloader care what
    // the source filename was, only that the bytes are a valid APK, so renaming costs nothing.
    const res = await fetch("apks/coremdm.apk.bin", { method: "HEAD" });
    // res.ok alone isn't enough to prove a real file is there: a catch-all SPA-style rewrite
    // (present here before, now removed at the firebase.json level too — this is belt and
    // suspenders) turns a 404 into a 200 serving index.html, and installApk() would then try
    // to install that HTML page as an "APK" with no useful error until the OS parse fails.
    const type = res.headers.get("content-type") || "";
    if (res.ok && !type.includes("html")) apkFromFolder = "apks/coremdm.apk.bin";
  } catch { /* no bundled apk */ }
  return apkFromFolder;
}

async function proceedToInstall() {
  hideAllOutcomes();
  await tryPreloadedApk();
  if (apkFromFolder) {
    await installApk(apkFromFolder);
    return;
  }
  $("#run-status").hidden = true;
  $("#need-apk").hidden = false;
}

async function installApk(preloadedPath) {
  hideAllOutcomes();
  let size, stream, label;
  if (preloadedPath) {
    const res = await fetch(preloadedPath);
    const blob = await res.blob();
    size = blob.size; stream = blob.stream(); label = preloadedPath;
  } else {
    const file = $("#apk-file").files[0];
    if (!file) { $("#need-apk").hidden = false; return; }
    size = file.size; stream = file.stream(); label = file.name;
  }

  $("#need-apk").hidden = false;
  $("#install-prog").hidden = false;
  $("#install-prog").removeAttribute("value"); // indeterminate
  setRun(`Installing ${label} (${(size / 1e6).toFixed(1)} MB)…`);
  log(`installStream: ${label} (${size} bytes), allowTest=true`);
  try {
    await pm.installStream(size, stream, { allowTest: true, grantRuntimePermissions: true });
    $("#install-prog").hidden = true;
    $("#need-apk").hidden = true;
    await setOwner();
  } catch (e) {
    $("#install-prog").hidden = true;
    log("install error: " + (e.stack || e.message));
    showFailed("APK install failed: " + esc(e.message));
  }
}

// ---------- set device owner ----------
async function setOwner() {
  setRun("Setting Device Owner…");
  const r = await shell(`dpm set-device-owner ${DO_COMPONENT}`);
  if (/Success/i.test(r)) {
    await shell(`pm grant ${DO_PACKAGE} android.permission.WRITE_SECURE_SETTINGS`).catch(() => {});
    showDone();
  } else {
    let why = esc(r.trim()) || "See log.";
    if (/already some accounts|account/i.test(r)) why = "An account still exists on the device.";
    else if (/already set|already exists/i.test(r)) why = "A Device Owner is already set.";
    showFailed("Couldn't set Device Owner: " + why);
  }
}

// ---------- restore / rescue ----------
function paintRestore() {
  const disabled = JSON.parse(localStorage.getItem(LS_DISABLED) || "[]");
  $("#restore-list").innerHTML = disabled.length
    ? disabled.map((p) => `<div class="acct"><span>${esc(p)}</span><span class="type">disabled</span></div>`).join("")
    : `<div class="muted">No apps were disabled by this tool on this browser.</div>`;
}
async function reEnable() {
  const disabled = JSON.parse(localStorage.getItem(LS_DISABLED) || "[]");
  if (!disabled.length) { setStatus("#restore-status", "Nothing to re-enable.", ""); return; }
  setStatus("#restore-status", `Re-enabling ${disabled.length} app(s)…`, "busy");
  for (const p of disabled) await shell(`pm enable ${p}`);
  localStorage.setItem(LS_DISABLED, "[]");
  paintRestore();
  setStatus("#restore-status", "Re-enabled. Re-add accounts in Settings as needed.", "ok");
}
async function removeOwner() {
  setStatus("#restore-status", "Removing Device Owner…", "busy");
  const r = await shell(`dpm remove-active-admin ${DO_COMPONENT}`);
  if (/Success/i.test(r)) setStatus("#restore-status", "Device Owner removed.", "ok");
  else setStatus("#restore-status",
    "Failed — this only works on a testOnly build. Non-test owners must remove themselves from inside the app. Log has details.", "err");
}

// ---------- wire up ----------
function main() {
  if (!("usb" in navigator)) {
    $("#unsupported").hidden = false;
    $("#mode-adb").disabled = true;
    $("#mode-adb").title = "WebUSB isn't available in this browser — use Chrome/Edge, or pick QR code.";
  }

  $("#btn-connect").onclick = () => connect().catch((e) => {
    log("connect error: " + (e.stack || e.message));
    showFailed("Connect failed: " + esc(e.message));
    $("#btn-connect").disabled = false;
  });
  $("#btn-disable").onclick = () => disableSelected().catch((e) => showFailed(esc(e.message)));
  $("#btn-resolve-rescan").onclick = () => recheckAccounts().catch((e) => showFailed(esc(e.message)));
  $("#pkg-filter").oninput = (e) => {
    const q = e.target.value.toLowerCase();
    $$("#pkg-list label").forEach((l) => (l.style.display = l.textContent.toLowerCase().includes(q) ? "" : "none"));
  };
  $("#apk-file").onchange = () => installApk().catch((e) => showFailed(esc(e.message)));
  // If the original connect attempt failed before `adb` was ever set (e.g. "device already
  // in use"), retrying the pipeline directly crashes on adb.subprocess being null — retry the
  // whole connection in that case instead.
  $("#btn-retry").onclick = () => {
    const attempt = adb ? runPipeline() : connect();
    attempt.catch((e) => {
      log("retry error: " + (e.stack || e.message));
      showFailed("Retry failed: " + esc(e.message));
    });
  };
  $("#btn-reenable").onclick = () => reEnable();
  $("#btn-remove-owner").onclick = () => removeOwner();

  paintRestore();
  wireModeSelect();
}
main();

// ---------- method chooser ----------
function wireModeSelect() {
  const select = (mode) => {
    $("#mode-select").hidden = true;
    $("#adb-flow").hidden = mode !== "adb";
    $("#qr-flow").hidden = mode !== "qr";
    if (mode === "qr") renderQr();
  };
  $("#mode-adb").onclick = () => select("adb");
  $("#mode-qr").onclick = () => select("qr");
  $$("[data-back]").forEach((btn) => (btn.onclick = () => {
    $("#adb-flow").hidden = true;
    $("#qr-flow").hidden = true;
    $("#mode-select").hidden = false;
  }));
}

// ---------- QR provisioning ----------
// Same file the ADB-over-USB flow streams from apks/coremdm.apk.bin (see tryPreloadedApk
// below) — one APK, self-hosted here so both paths install byte-identical builds and there's
// a single place to update. GitHub Releases' download URL doesn't send CORS headers, so it
// can't be fetch()'d by installStream() for the USB path — that's the whole reason this lives
// here instead: QR provisioning (the phone's own OS downloader, not a browser fetch) would
// have been fine with either, but USB needs same-origin.
// Filename is .apk.bin, not .apk: Firebase Hosting's free Spark plan hard-blocks serving any
// file whose name ends in .apk/.exe/.ipa (checked by extension, not content) — the phone's
// downloader doesn't care what the URL's extension is, only that the bytes are a valid APK.
//   QR_APK_URL:      https:// location the phone downloads the APK from.
//   QR_APK_CHECKSUM: SHA-256 of that exact APK file, base64url-encoded, no padding.
//                    Compute it with: certutil -hashfile coremdm.apk.bin SHA256   (Windows)
//                    or: sha256sum coremdm.apk.bin | ...                          (see README)
// To ship a new build: download the release APK from GitHub, overwrite
// public/install/apks/coremdm.apk.bin with it, recompute the checksum, update both constants
// below, commit, push (auto-deploys).
const QR_APK_URL = "https://coremdm.web.app/install/apks/coremdm.apk.bin";
const QR_APK_CHECKSUM = "RC_8QJLEjP7uy1TnQCnKUZNgAp1AIaLqcnjZxt1uXO4";

// Builds the standard Android "QR code provisioning" JSON payload (the same one the
// Settings-app QR scanner reads at the SetupWizard "tap 6x" screen) and renders it as a QR.
// Reference: DevicePolicyManager EXTRA_PROVISIONING_* extras.
const PROV = {
  admin: "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME",
  download: "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION",
  pkgChecksum: "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM",
  leaveSystemApps: "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED",
};

function renderQr() {
  if (!QR_APK_URL || !QR_APK_CHECKSUM) {
    $("#qr-notconfigured").hidden = false;
    $("#qr-ready").hidden = true;
    return;
  }
  $("#qr-notconfigured").hidden = true;
  $("#qr-ready").hidden = false;

  // Sensible default baked in, not exposed as an option: leave system apps enabled (safest).
  // No Wi-Fi pre-fill — the setup wizard just prompts for it normally.
  const payload = {
    [PROV.admin]: DO_COMPONENT,
    [PROV.download]: QR_APK_URL,
    [PROV.pkgChecksum]: QR_APK_CHECKSUM,
    [PROV.leaveSystemApps]: true,
  };

  $("#qr-canvas").innerHTML = "";
  // eslint-disable-next-line no-undef -- QRCode loaded globally via cdnjs <script>
  new QRCode($("#qr-canvas"), {
    text: JSON.stringify(payload),
    width: 260, height: 260,
    correctLevel: QRCode.CorrectLevel.M,
  });
}
