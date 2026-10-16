# CORE MDM

An Android Mobile Device Management (MDM) app with a Firebase-powered cloud remote-control web console. Remotely manage enrolled Android devices — lock screens, apply security policies, trigger alarms, enforce DNS, block apps, manage kiosk mode, and more — all from a browser.

> **Status: Beta.** Still under active development — expect rough edges and breaking changes between releases.

**Website & console:** [https://coremdm.org](https://coremdm.org) · console at [/console](https://coremdm.org/console)
**App version in this branch:** 0.8.0 (versionCode 57) · [latest release](https://github.com/yybam/coremdm/releases/latest)

---

## Features

### Android app
- Device Owner / Device Admin mode via Android Device Policy Manager; **QR provisioning**
  (Android 12+ setup screens included) or USB install from the web installer
- Firebase Auth — **email/password and Google Sign-In**; auto-enrolls on sign-in, optional
  enrollment-token provisioning (pre-register a device by IMEI)
- Foreground service (`MdmCommandService`) keeps a persistent Firestore connection, runs remote
  commands and applies policies in real time; re-attaches automatically if the connection drops
- **Content filter** (`vpn/`): local DNS VPN with 10 bundled blocklist groups
  (`app/src/main/assets/blocklists/`, built by `tools/build-blocklists.mjs`), IPv6-aware
  subdomain matching, Private DNS guard, always-on with no Stop button; on Device Owner phones the
  console switches it on with no on-device tap
- **Screen guard** (accessibility service): blocks parts of apps — WhatsApp Status/Channels,
  Instagram Reels/Stories/Explore, YouTube Shorts, TikTok feed… — and limits web pages inside apps
- **App blocking**: WhatsApp, social media and AI chats in one switch each (apps paused, sites
  blocked), any single app paused or hidden, "only chosen apps can install apps"
- **Daily app time limits**, kiosk / lock-task mode, screen-time window, scheduled lockdown
- **Location tracking and geofencing** (lock, alarm or message when a phone leaves an area)
- Battery, network and app-usage reporting; pop-up **messages** from the console
- **Organizations**: phones join with a code; an org can lock or default settings and brand the lock screen
- **Remote-only mode**: locks the app on the phone so settings change only from the console
- Mandatory **PIN lock** (admin-configurable minimum length 4/6/8)
- **Self-update**: checks this repo's GitHub releases, or installs an APK pushed from the console;
  only installs the same app, a higher version, signed with the same key
- Jetpack Compose UI: tile hub with the "Glass" look shared with the website

### Web console (`/console`)
Device cards with online status, what's switched on, and search across every setting. Per-device tabs:

| Tab | What you can control |
|-----|----------------------|
| **Commands** | Alarm, lock, reboot, full lockdown (pending until the phone runs them, cancellable); send a message |
| **Apps** | Quick blocks (WhatsApp, social media, AI chats, in-app browsers), parts of apps, every installed app — hide or block |
| **Time limits** | Daily minutes per app, with today's usage |
| **Internet** | Web pages inside apps (per-app allow list), Private DNS, content filter + site groups / allow & block lists, dummy browser, kiosk apps |
| **Device** | Installing apps (+ installer allow list), keep-rules-in-place (factory reset, safe boot, ADB, app settings, calls, SMS, hotspot, email…), hardware, lock-screen PIN length |
| **Location & schedule** | Map + location tracking, geofence, screen-time window, scheduled lockdown, battery/network/compliance, app usage |
| **Danger** | Wipe, remove from account |

Plus **Organizations** (`/org`) and a **Super Admin** view (all devices, activity log, device inventory,
fleet update notification, `/updater` for pushing an APK to the fleet).

---

## Architecture

```
Browser (coremdm.org)
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

Open [https://coremdm.org/console](https://coremdm.org/console), sign in with the **same** account
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
| 0.8.0 (unreleased) | Merge of the MainLoop321 app + site line — see [CHANGELOG](CHANGELOG.md) |
| [v0.4.2](https://github.com/yybam/coremdm/releases/tag/v0.4.2) | Per-app Allow / Block (suspend) / Hide in the Installed Apps tab; full front-end site with the console at /console.html; centered logo |
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
| Hosting | Firebase Hosting (`coremdm.org`, also `coremdm.web.app`) |
| Build / CI | Gradle 8, Android Gradle Plugin, GitHub Actions |

---

## Reporting a Vulnerability

Found a security issue? Please report it by [opening a GitHub issue](https://github.com/yybam/coremdm/issues/new). See [`SECURITY.md`](SECURITY.md).

---

## License

MIT
