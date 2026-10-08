# CORE MDM

An Android Mobile Device Management (MDM) app with a Firebase-powered cloud remote-control web console. Remotely manage enrolled Android devices — lock screens, apply security policies, trigger alarms, enforce DNS, block apps, manage kiosk mode, and more — all from a browser.

> **Status: Beta.** Still under active development — expect rough edges and breaking changes between releases.

**Live Web Console:** [https://coremdm.web.app](https://coremdm.web.app)
**Latest release:** [v0.4.2](https://github.com/yybam/coremdm/releases/latest) (app version 40.0)

---

## Features

### Android App
- Device Owner / Device Admin mode via Android Device Policy Manager
- Firebase Auth — **email/password and Google Sign-In**
- Auto-enrolls device to Firestore on sign-in (works for brand-new devices)
- Optional **enrollment-token** provisioning flow (pre-register a device by IMEI)
- Foreground service (`MdmCommandService`) keeps a persistent Firestore connection and
  **re-attaches automatically** if the connection drops
- Executes all remote commands and policy changes in real time
- **Mandatory PIN lock** on every app open, with an admin-configurable minimum length (4/6/8)
- In-app dashboard with local policy controls
- **Content filter via DNS VPN** (`DnsVpnService`) — on a Device Owner device it
  self-authorizes (registers as always-on VPN), so the console toggle works with no
  on-device consent tap
- Per-app management: hide / suspend individual apps; one-tap **social-media block**
- Installed-app inventory synced to the console
- Kiosk / lock-task mode management
- Telemetry screen
- Dark-themed Jetpack Compose UI

### Web Admin Console
- Real-time device list with online/offline status
- **Rename** any device to a friendly name
- Per-device management modal with 7 tabs:

| Tab | What you can control |
|-----|----------------------|
| **Commands** | Sound alarm, Lock screen, Reboot, Full lockdown — queued commands show as **Pending** with a **Cancel** button until the device runs them |
| **App Policies** | Block installs/uninstalls/sideloading, hide Play Store & browsers, **block social media**, disable camera & screen capture |
| **Installed Apps** | Every app the device reports (name, package, version), each set to **Allowed / Blocked** (suspended — visible but won't open) **/ Hidden** (removed from the launcher), with search and status counts |
| **System** | Prevent safe boot / factory reset / debugging, block add-user & user-switch, lock status bar, **set minimum lock-screen PIN length** |
| **Hardware** | Lock Wi-Fi/Bluetooth/cellular/VPN settings, block USB transfer, disable Bluetooth radio, block outgoing calls, block SD card, protect MDM from uninstall |
| **Network** | Enforce Private DNS hostname, toggle content filter VPN, manage kiosk allowed packages |
| **Danger** | Wipe device (with confirmation) |

---

## Architecture

```
Browser (coremdm.web.app)
    │  writes Firestore documents / queues commands
    ▼
Firebase Firestore  /devices/{deviceId}  (+ /devices/{deviceId}/commands)
    │  snapshot listeners (real-time)
    ▼
MdmCommandService (Android foreground service)
    │  claims & executes commands, applies policies
    ▼
DevicePolicyHelper → DevicePolicyManager (Android OS)
```

**Command pattern (queued, cancellable):**
Web adds a doc to `/devices/{id}/commands` with `state: "pending"`. The device claims it in
a transaction (flipping it to `"executed"`) and runs it — `lock`, `reboot`, `wipe`, or
`lockdown`. If the admin presses **Cancel** first, the state becomes `"cancelled"` and the
device skips it. Alarm stays a simple boolean field (instant toggle).

**Policy pattern (persistent):**
Web writes `policies.cameraDisabled: true` → device applies `setCameraDisabled(true)` → the
field stays set as the source of truth and is re-applied on reconnect and reboot.

---

## Project Structure

```
coremdm/
├── app/
│   └── src/main/kotlin/com/core/mdm/
│       ├── firebase/
│       │   ├── DeviceRegistry.kt        # Firestore CRUD, command queue, app inventory
│       │   ├── EnrollmentManager.kt     # Device enrollment on auth restore
│       │   └── MdmFirebaseMessagingService.kt
│       ├── service/
│       │   └── MdmCommandService.kt     # Foreground service — executes all commands
│       ├── policy/
│       │   ├── DevicePolicyHelper.kt    # Wraps Android DevicePolicyManager
│       │   ├── AppPolicyManager.kt      # Per-app hide / suspend
│       │   └── KioskModeManager.kt
│       ├── installer/
│       │   └── SilentInstaller.kt       # PackageInstaller-based silent install/uninstall
│       ├── security/
│       │   └── PinManager.kt            # PBKDF2-hashed in-app PIN, configurable length
│       ├── vpn/
│       │   └── DnsVpnService.kt         # Content filter DNS VPN
│       └── ui/
│           ├── dashboard/  login/  pin/  apps/  kiosk/  filter/
│           ├── provisioning/            # Enrollment-token UI
│           └── telemetry/
├── public/
│   └── index.html                       # Web admin console (single-file SPA)
├── admin-backend/                       # Kotlin + Spring Boot 3 service (JPA, Spring
│                                         #   Security, Firebase Admin SDK); H2 for dev
├── backend/                             # Node.js + Express/WebSocket server for live
│   └── public/index.html                #   WebRTC screen remote-control
├── .github/workflows/
│   ├── build-apk.yml                    # Builds + publishes a signed release APK as a
│   │                                     #   GitHub Release on a pushed vX.Y.Z tag
│   ├── deploy-hosting.yml               # Deploys Firebase Hosting on push to public/**
│   └── deploy-firestore.yml             # Deploys firestore.rules on change
├── firebase.json                        # Firebase Hosting config (site: coremdm)
├── firestore.rules                      # Per-user ownership + command-queue rules
├── seed.js                              # Firestore seed helper (firebase-admin)
└── .firebaserc                          # Firebase project selection
```

---

## Installing a release

The simplest path is the prebuilt APK from the latest GitHub Release:

1. Download `app-release.apk` from **[Releases](https://github.com/yybam/coremdm/releases/latest)**.
2. Install it on the device (sideload, or `adb install -r app-release.apk`).
3. (Optional, for full control) set Device Owner — see below.

> **Signing:** from **v0.3.1** onward every release is signed with the same permanent key, so
> updates install straight over the previous version. Builds **before v0.3.1** were signed with
> a throwaway CI key — to move to v0.3.1+ you must uninstall the old app first (one time).
> Google Sign-In only works on releases signed with that permanent key (its SHA-1/SHA-256 are
> registered in Firebase).

---

## Building from source

### Prerequisites
- JDK 17 and the Android SDK (Android Studio bundles both)
- Android device or emulator running Android 8.0+ (API 26+)
- Firebase project with Firestore + Auth enabled
- Node.js + Firebase CLI (`npm install -g firebase-tools`) to deploy the console

### 1. Clone and build

```bash
git clone https://github.com/yybam/coremdm.git
cd coremdm
export ANDROID_HOME=~/AppData/Local/Android/Sdk   # your SDK path
export JAVA_HOME=/path/to/jdk17
./gradlew assembleRelease
# APK → app/build/outputs/apk/release/app-release.apk
```

Local release builds fall back to the Android debug keystore. CI signs with the permanent
release key, supplied via the `RELEASE_KEYSTORE_BASE64` / `RELEASE_KEYSTORE_PASSWORD` repo
secrets (see `app/build.gradle.kts` and `.github/workflows/build-apk.yml`).

### 2. Install and set Device Owner

```bash
adb install -r app/build/outputs/apk/release/app-release.apk

# Set as Device Owner (required for full policy control).
# Only works with NO accounts on the device — do it before signing in.
adb shell dpm set-device-owner com.core.mdm/.MdmDeviceAdmin
```

> **Note:** Device Owner can only be set on a device with no user accounts, or via NFC/QR
> provisioning on a fresh device. On a phone that already has Google accounts you must remove
> them (or factory reset) first. Without Device Owner the app still runs as a **device admin**
> — Lock, Wipe, Alarm, rename, the command queue and the app inventory all work; the
> system-wide restrictions (app hide/suspend, enforced PIN, reboot, kiosk, content filter
> auto-start) need Device Owner.

### 3. Sign in

Open CORE MDM and sign in with your Firebase account (email/password or Google). The app
enrolls the device under your account and starts `MdmCommandService`.

### 4. Deploy the web console & rules

```bash
firebase login
firebase deploy --only hosting:coremdm        # web console
firebase deploy --only firestore:rules        # security rules
```

In this repo both deploy automatically via GitHub Actions (`deploy-hosting.yml`,
`deploy-firestore.yml`) using the `FIREBASE_SERVICE_ACCOUNT` secret.

### 5. Use the web console

Open [https://coremdm.web.app](https://coremdm.web.app), sign in with the **same** account
used on the device. Your enrolled devices appear immediately. Click **Manage** on any device.

---

## Firestore Security Rules

Devices are scoped per user (you only see your own), the device itself may update its own
record and acknowledge commands, and a super-admin has full read. Commands live in a
per-device subcollection. See [`firestore.rules`](firestore.rules) for the full policy.

```javascript
match /devices/{deviceId} {
  allow create: if request.auth != null
    && request.resource.data.ownerId == request.auth.uid;
  allow read: if request.auth != null
    && (resource.data.ownerId == request.auth.uid
        || isSuperAdmin()
        || resource.data.deviceUid == request.auth.uid);
  // owner / super-admin / the device itself may update; see file for field guards
}

match /devices/{deviceId}/commands/{cmdId} {
  // owner & super-admin create/cancel; the device reads and marks executed
}
```

---

## Firestore Device Document

```
/devices/{hardwareId}
  model, manufacturer, osVersion   — device info
  customName: string                — friendly name set in the console
  status: "online" | "offline"
  ownerId: string                   — Firebase Auth UID of the admin
  deviceUid: string                 — Firebase Auth UID the device signed in as
  lastSeen: Timestamp
  imei, serial                      — hardware IDs (requires Device Owner)
  alarmActive: boolean              — persistent alarm state
  installedApps: [{pkg,label,version,system}]   — synced app inventory
  policies: {
    installAppsBlocked, uninstallAppsBlocked, unknownSourcesBlocked,
    playStoreHidden, browsersHidden, socialMediaBlocked, cameraDisabled,
    screenCaptureDisabled, safeBootBlocked, factoryResetBlocked, debuggingBlocked,
    addUserBlocked, userSwitchBlocked, statusBarDisabled, wifiConfigBlocked,
    mobileNetworksBlocked, bluetoothConfigBlocked, vpnBlocked, networkResetBlocked,
    usbTransferBlocked, bluetoothDisabled, outgoingCallsBlocked, physicalMediaBlocked,
    mdmUninstallProtected, pinMinLength: number,
    blockedApps: string[],      // hidden from the launcher
    suspendedApps: string[],    // blocked — visible but can't open
    privateDnsHost: string, filterRunning: boolean, kioskPackages: string[]
  }

/devices/{hardwareId}/commands/{cmdId}
  type: "lock" | "reboot" | "wipe" | "lockdown"
  state: "pending" | "executed" | "cancelled"
  createdAt, executedAt: Timestamp
  issuedBy: string                  — admin email
```

---

## Releases

Releases are built and published automatically by `.github/workflows/build-apk.yml`: pushing a
`vX.Y.Z` tag (or a manual workflow run) builds the signed release APK and attaches it to a
GitHub Release. Older entries (v37/v38) were committed directly as files under `releases/` and
remain there for history.

| Version | Notes |
|---------|-------|
| [v0.4.2](https://github.com/yybam/coremdm/releases/tag/v0.4.2) | **Current.** Per-app Allow / Block (suspend) / Hide in the Installed Apps tab; full front-end site at coremdm.web.app with the console at /console.html; centered logo |
| [v0.4.1](https://github.com/yybam/coremdm/releases/tag/v0.4.1) | Content filter auto-authorizes its VPN on Device Owner devices (toggle works from the console with no on-device tap) |
| [v0.4.0](https://github.com/yybam/coremdm/releases/tag/v0.4.0) | Cancellable command queue, device rename, installed-apps tab, social-media block, per-app hide, configurable minimum PIN length |
| [v0.3.2](https://github.com/yybam/coremdm/releases/tag/v0.3.2) | Fixed enrollment of brand-new devices (PERMISSION_DENIED now falls through to create) |
| [v0.3.1](https://github.com/yybam/coremdm/releases/tag/v0.3.1) | First build signed with the permanent release key; Google Sign-In working |
| [v0.3.0](https://github.com/yybam/coremdm/releases/tag/v0.3.0) | Merged Google Sign-In, mandatory PIN lock, enrollment tokens, reliable command delivery |
| [v0.2.1](https://github.com/yybam/coremdm/releases/tag/v0.2.1) | Dropped `android:testOnly="true"` — Device Owner can no longer be removed via plain `adb` without root |
| v37.0 | Fixed auth timing bug — Firestore listener starts after Firebase Auth restores |
| v36.0 | Full policy remote control, web console overhaul |
| v35.0 | Core MDM branding, lock/wipe/reboot commands |

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Android app | Kotlin, Jetpack Compose, Android Device Policy Manager |
| Backend | Firebase Firestore (real-time), Firebase Auth |
| Web console | Vanilla JS ES modules, Firebase JS SDK 10.12.2 |
| Hosting | Firebase Hosting (`coremdm.web.app`) |
| Build / CI | Gradle 8, Android Gradle Plugin, GitHub Actions |

---

## Reporting a Vulnerability

Found a security issue? Please report it by [opening a GitHub issue](https://github.com/yybam/coremdm/issues/new). See [`SECURITY.md`](SECURITY.md).

---

## License

MIT
