import qrcode from "https://cdn.jsdelivr.net/npm/qrcode-generator@1.4.4/+esm";

// QR-code setup: a freshly reset phone reads this from its welcome screen, downloads the APK and
// makes CORE MDM device owner. The APK is always the latest public release on GitHub. The checksum is
// the SHA-256 of the signing certificate (base64url), so it stays valid for every build signed with our key.
const ADMIN_COMPONENT = "com.core.mdm/com.core.mdm.MdmDeviceAdmin";
const SIGNATURE_CHECKSUM = "QyhA-vfIAZvLxO453ZWU81aPXQQi-UJCsnvuRYBIN-o";
const APK_URL = "https://github.com/yybam/coremdm/releases/latest/download/app-release.apk";

const $ = (id) => document.getElementById(id);
const EXTRA = "android.app.extra.";

function payload() {
  const p = {
    [EXTRA + "PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME"]: ADMIN_COMPONENT,
    [EXTRA + "PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION"]: APK_URL,
    [EXTRA + "PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM"]: SIGNATURE_CHECKSUM,
    // Same result as the USB installer: keep the phone's own apps; CORE MDM decides what to block.
    [EXTRA + "PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED"]: true,
  };
  const ssid = $("wifiSsid").value.trim();
  if (ssid) {
    const security = $("wifiSecurity").value;
    p[EXTRA + "PROVISIONING_WIFI_SSID"] = ssid;
    p[EXTRA + "PROVISIONING_WIFI_SECURITY_TYPE"] = security;
    if (security !== "NONE") p[EXTRA + "PROVISIONING_WIFI_PASSWORD"] = $("wifiPassword").value;
    if ($("wifiHidden").checked) p[EXTRA + "PROVISIONING_WIFI_HIDDEN"] = true;
  }
  return JSON.stringify(p);
}

function render() {
  const qr = qrcode(0, "M");
  qr.addData(payload(), "Byte");
  qr.make();
  $("qrCode").innerHTML = qr.createSvgTag({ cellSize: 6, margin: 4, scalable: true });
  $("wifiPassword").disabled = $("wifiSecurity").value === "NONE";
}

// The phone downloads the APK itself, so this page has to be on the public internet.
const host = location.hostname;
if (location.protocol !== "https:" || host === "localhost" || /^(127\.|10\.|192\.168\.)/.test(host)) {
  $("qrWarn").hidden = false;
}
$("qrApkUrl").textContent = APK_URL;

["wifiSsid", "wifiPassword", "wifiSecurity", "wifiHidden"].forEach((id) => $(id).addEventListener("input", render));
render();

// ---------- USB / QR switch ----------
function showMethod(method) {
  $("usbMethod").hidden = method !== "usb";
  $("qrMethod").hidden = method !== "qr";
  document.querySelector(`input[name="method"][value="${method}"]`).checked = true;
}
document.querySelectorAll('input[name="method"]').forEach((r) => r.addEventListener("change", () => {
  showMethod(r.value);
  history.replaceState(null, "", r.value === "qr" ? "#qr" : location.pathname);
}));
showMethod(location.hash === "#qr" ? "qr" : "usb");
