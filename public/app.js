import { Adb, AdbDaemonTransport } from "https://cdn.jsdelivr.net/npm/@yume-chan/adb@2.6.4/+esm";
import { AdbDaemonWebUsbDeviceManager } from "https://cdn.jsdelivr.net/npm/@yume-chan/adb-daemon-webusb@2.3.2/+esm";
import AdbWebCredentialStore from "https://cdn.jsdelivr.net/npm/@yume-chan/adb-credential-web@2.1.0/+esm";

const PACKAGE = "com.core.mdm";
const ADMIN_COMPONENT = `${PACKAGE}/.MdmDeviceAdmin`;
const BUNDLED_APK = "coremdm.apk.bin";  // .bin so Firebase Hosting (Spark) will serve it
const REMOTE_PATH = "/data/local/tmp/coremdm.apk";

const $ = (id) => document.getElementById(id);
const manager = AdbDaemonWebUsbDeviceManager.BROWSER;
const credentialStore = new AdbWebCredentialStore("CORE MDM Installer");
let adb = null;
let busy = false;

// ---------- UI helpers ----------
function log(msg) {
  const line = `[${new Date().toLocaleTimeString()}] ${msg}\n`;
  $("log").textContent += line;
  $("log").scrollTop = $("log").scrollHeight;
  console.log(msg);
}
function setStep(name, state) {
  const li = document.querySelector(`[data-step="${name}"]`);
  li.className = state || "";
}
function resetSteps() { ["accounts", "install", "owner"].forEach((s) => setStep(s, "")); }
function showResult(kind, text) {
  const el = $("result");
  el.className = `alert ${kind}`;
  el.textContent = text;
  el.hidden = !text;
}
function setProgress(fraction) {
  $("progWrap").hidden = fraction == null;
  $("progBar").style.width = `${Math.round((fraction || 0) * 100)}%`;
}
function refreshButtons() {
  const connected = !!adb;
  $("btnConnect").hidden = connected;
  $("btnDisconnect").hidden = !connected;
  $("btnAll").disabled = !connected || busy;
  $("btnConnect").disabled = busy;
  $("deviceInfo").classList.toggle("on", connected);
}

// ---------- ADB ----------
async function shell(cmd) {
  log(`$ ${cmd}`);
  const out = (await adb.subprocess.noneProtocol.spawnWaitText(cmd)).trim();
  if (out) log(out);
  return out;
}

async function connect() {
  busy = true; refreshButtons(); showResult("", "");
  try {
    const device = await manager.requestDevice();
    if (!device) { log("No device selected."); return; }
    log(`Selected ${device.name || "device"} (${device.serial}). Connecting...`);
    let connection;
    try {
      connection = await device.connect();
    } catch (e) {
      throw new Error(
        "Couldn't open the USB connection. Another program is probably using the phone " +
        "(ADB, Android Studio, or another tab with this page). Run `adb kill-server`, unplug and replug the phone, then try again. " +
        "On a Samsung phone, installing the Samsung USB driver (developer.samsung.com/android-usb-driver) can also fix this.\n\n" + e.message);
    }
    log("Waiting for you to allow USB debugging on the phone...");
    showResult("warn", "Check the phone: tap \"Allow\" on the USB debugging prompt. Tick \"Always allow from this computer\" if you'll use it again.");
    const transport = await AdbDaemonTransport.authenticate({ serial: device.serial, connection, credentialStore });
    adb = new Adb(transport);
    transport.disconnected.then(() => {
      if (adb) { log("Device disconnected."); adb = null; $("deviceInfo").textContent = "Disconnected"; refreshButtons(); }
    });
    const [brand, model, release] = await Promise.all([
      adb.getProp("ro.product.brand"), adb.getProp("ro.product.model"), adb.getProp("ro.build.version.release"),
    ]);
    $("deviceInfo").textContent = `Connected: ${brand} ${model} · Android ${release}`;
    log(`Connected: ${brand} ${model}, Android ${release}`);
    showResult("", "");
  } catch (e) {
    log(`Error: ${e.message}`);
    showResult("bad", e.message);
    adb = null;
  } finally {
    busy = false; refreshButtons();
  }
}

async function disconnect() {
  const a = adb; adb = null;
  try { await a?.close(); } catch {}
  $("deviceInfo").textContent = "Not connected";
  log("Disconnected.");
  refreshButtons();
}

async function getApk() {
  log(`Loading ${BUNDLED_APK}...`);
  const res = await fetch(BUNDLED_APK);
  if (!res.ok) throw new Error(`Couldn't load ${BUNDLED_APK} (HTTP ${res.status}).`);
  const blob = await res.blob();
  return { name: BUNDLED_APK, size: blob.size, stream: blob.stream() };
}

async function checkAccounts() {
  setStep("accounts", "run");
  const out = await shell("dumpsys account");
  const m = out.match(/Accounts:\s*(\d+)/);
  const count = m ? parseInt(m[1], 10) : 0;
  if (count > 0) {
    setStep("accounts", "warn");
    log(`Found ${count} account(s) on the phone. Setting device owner will probably fail.`);
  } else {
    setStep("accounts", "ok");
  }
  return { count };
}

async function installApk() {
  setStep("install", "run");
  const apk = await getApk();
  log(`Sending ${apk.name} (${(apk.size / 1048576).toFixed(1)} MB) to the phone...`);
  let sent = 0;
  const counted = apk.stream.pipeThrough(new TransformStream({
    transform(chunk, ctrl) { sent += chunk.byteLength; setProgress(sent / apk.size); ctrl.enqueue(chunk); },
  }));
  setProgress(0);
  const sync = await adb.sync();
  try {
    await sync.write({ filename: REMOTE_PATH, file: counted, permission: 0o644 });
  } finally {
    await sync.dispose();
  }
  setProgress(null);
  log("Upload complete. Installing...");
  const out = await shell(`pm install -r -t "${REMOTE_PATH}"`);
  await shell(`rm -f "${REMOTE_PATH}"`);
  if (!/Success/.test(out)) {
    setStep("install", "bad");
    throw new Error(`Install failed:\n${out || "(no output)"}`);
  }
  setStep("install", "ok");
}

async function setDeviceOwner() {
  setStep("owner", "run");
  const out = await shell(`dpm set-device-owner ${ADMIN_COMPONENT}`);
  if (/Success/i.test(out)) { setStep("owner", "ok"); return out; }
  setStep("owner", "bad");
  let hint = "";
  if (/account/i.test(out)) hint = "\n\nThe phone still has accounts. Remove every account under Settings › Accounts (or factory reset), then try again.";
  else if (/already (set|provisioned)|several users|already has a device owner/i.test(out)) hint = "\n\nThe phone is already set up with a device owner or extra users. Factory reset it and skip account sign-in during setup.";
  throw new Error(`Setting device owner failed:\n${out || "(no output)"}${hint}`);
}

async function run(task) {
  if (busy || !adb) return;
  busy = true; refreshButtons(); showResult("", ""); resetSteps();
  $("doneRow").hidden = true;
  try {
    await task();
  } catch (e) {
    log(`Error: ${e.message}`);
    showResult("bad", e.message);
  } finally {
    setProgress(null);
    busy = false; refreshButtons();
  }
}

// ---------- wire up ----------
if (!manager || !window.isSecureContext) {
  $("unsupported").hidden = false;
  $("btnConnect").disabled = true;
} else {
  $("btnConnect").onclick = connect;
}
$("btnDisconnect").onclick = disconnect;

$("btnAll").onclick = () => run(async () => {
  const { count } = await checkAccounts();
  if (count > 0) {
    setStep("accounts", "bad");
    throw new Error(`The phone has ${count} account(s) on it, so it can't be made device owner. Nothing was installed.\n\n` +
      "Remove every account under Settings › Accounts (or factory reset the phone), then try again.");
  }
  await installApk();
  try {
    await setDeviceOwner();
  } catch (e) {
    // Don't leave CORE MDM installed without device owner.
    log("Removing CORE MDM because device owner couldn't be set...");
    await shell(`pm uninstall ${PACKAGE}`).catch(() => {});
    throw new Error(`${e.message}\n\nCORE MDM was removed from the phone so it isn't left half set up.`);
  }
  showResult("ok", "Done. CORE MDM is installed and set as device owner. Unplug the phone, open CORE MDM and sign in. Then manage it from the web console with the same account.");
  $("doneRow").hidden = false;
});

refreshButtons();
log("Ready.");
