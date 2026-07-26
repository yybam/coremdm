# CORE MDM

An Android Mobile Device Management (MDM) app with a Firebase-powered cloud remote control web console. Remotely manage enrolled Android devices — lock screens, apply security policies, trigger alarms, enforce DNS, manage kiosk mode, and more — all from a browser.

**Live Web Console:** [https://coremdm.web.app](https://coremdm.web.app)

---

## Features

### Android App
- Device Owner / Device Admin mode via Android Device Policy Manager
- Firebase Auth — email/password sign-in
- Auto-enrolls device to Firestore on sign-in
- Foreground service (`MdmCommandService`) maintains persistent Firestore connection
- Executes all remote commands and policy changes in real time
- PIN lock screen protection
- In-app dashboard with local policy controls
- Content filter via DNS VPN (`DnsVpnService`)
- Kiosk / lock-task mode management
- Telemetry screen
- Dark-themed Jetpack Compose UI

### Web Admin Console
- Real-time device list with online/offline status
- Per-device management modal with 6 tabs:

| Tab | What you can control |
|-----|----------------------|
| **Commands** | Sound alarm, Lock screen, Reboot, Full lockdown |
| **App Policies** | Block installs/uninstalls/sideloading, hide Play Store & browsers, disable camera & screen capture |
| **System** | Prevent safe boot / factory reset / debugging, block add-user & user-switch, lock status bar |
| **Hardware** | Lock Wi-Fi/Bluetooth/cellular/VPN settings, block USB transfer, disable Bluetooth radio, block outgoing calls, block SD card, protect MDM from uninstall |
| **Network** | Enforce Private DNS hostname, toggle content filter VPN, manage kiosk allowed packages |
| **Danger** | Wipe device (with confirmation) |

---

## Architecture

```
Browser (coremdm.web.app)
    │  writes Firestore fields
    ▼
Firebase Firestore /devices/{deviceId}
    │  snapshot listener (real-time)
    ▼
MdmCommandService (Android foreground service)
    │  calls
    ▼
DevicePolicyHelper → DevicePolicyManager (Android OS)
```

**Command pattern (one-shot):**  
Web writes `lockCommand: true` → device executes `lockNow()` → device clears field back to `false`.

**Policy pattern (persistent):**  
Web writes `policies.cameraDisabled: true` → device applies `setCameraDisabled(true)` → field stays set as source of truth.

---

## Project Structure

```
coremdm/
├── app/
│   └── src/main/kotlin/com/core/mdm/
│       ├── firebase/
│       │   ├── DeviceRegistry.kt      # Firestore CRUD + real-time listeners
│       │   └── EnrollmentManager.kt   # Device enrollment on auth restore
│       ├── service/
│       │   └── MdmCommandService.kt   # Foreground service — executes all commands
│       ├── policy/
│       │   └── DevicePolicyHelper.kt  # Wraps Android DevicePolicyManager
│       ├── vpn/
│       │   └── DnsVpnService.kt       # Content filter DNS VPN
│       └── ui/
│           ├── dashboard/             # Main dashboard
│           ├── login/                 # Auth screen
│           ├── kiosk/                 # Kiosk management
│           ├── filter/                # DNS filter controls
│           └── telemetry/             # Device telemetry
├── public/
│   └── index.html                     # Web admin console (single-file SPA)
├── releases/
│   └── CoreMDM-v37.0.apk             # Latest debug build
├── firebase.json                      # Firebase Hosting config (site: coremdm)
├── firestore.rules                    # Per-user device ownership rules
└── .firebaserc                        # Firebase project: techeaz-core-mdm
```

---

## Setup

### Prerequisites
- Android Studio (for building the APK)
- Android device running Android 8.0+ (API 26+)
- Firebase project with Firestore + Auth enabled
- Node.js + Firebase CLI (`npm install -g firebase-tools`)

### 1. Clone and build the APK

```bash
git clone https://github.com/yybam/coremdm.git
cd coremdm

# Set your Android SDK path
export ANDROID_HOME=~/AppData/Local/Android/Sdk   # Windows: set in env vars
export JAVA_HOME=/path/to/jdk                      # Use Android Studio's bundled JDK

./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

### 2. Install and set Device Owner

```bash
# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Set as Device Owner (required for full policy control)
# Do this BEFORE signing in to the app — only works with no existing accounts on device
adb shell dpm set-device-owner com.core.mdm/.MdmDeviceAdmin
```

> **Note:** Device Owner can only be set on a device with no user accounts, or via NFC/QR provisioning on a fresh device. If the command fails, factory reset the device first.

### 3. Sign in

Open CORE MDM on the device and sign in with your Firebase account email + password.

The app will automatically:
- Enroll the device in Firestore under your account
- Start `MdmCommandService` to listen for remote commands

### 4. Deploy the web console

```bash
npm install -g firebase-tools
firebase login
firebase deploy --only hosting:coremdm
```

Web console will be live at `https://coremdm.web.app`

### 5. Use the web console

Open [https://coremdm.web.app](https://coremdm.web.app), sign in with the **same** account used on the device. Your enrolled devices appear immediately. Click **Manage** on any device to open the control panel.

---

## Firestore Security Rules

Devices are scoped per user — you only see devices enrolled under your account:

```javascript
match /devices/{deviceId} {
  allow create: if request.auth != null
    && request.resource.data.ownerId == request.auth.uid;
  allow read, update, delete: if request.auth != null
    && resource.data.ownerId == request.auth.uid;
}
```

---

## Firestore Device Document

```
/devices/{hardwareId}
  model, manufacturer, osVersion   — device info
  status: "online" | "offline"
  ownerId: string                   — Firebase Auth UID
  lastSeen: Timestamp
  imei, serial                      — hardware IDs (requires Device Owner)
  alarmActive: boolean              — persistent alarm state
  lockCommand: boolean              — one-shot lock
  wipeCommand: boolean              — one-shot wipe
  rebootCommand: boolean            — one-shot reboot
  fullLockdownCommand: boolean      — one-shot full lockdown
  policies: {
    installAppsBlocked, uninstallAppsBlocked, unknownSourcesBlocked,
    playStoreHidden, browsersHidden, cameraDisabled, screenCaptureDisabled,
    safeBootBlocked, factoryResetBlocked, debuggingBlocked, addUserBlocked,
    userSwitchBlocked, statusBarDisabled, wifiConfigBlocked,
    mobileNetworksBlocked, bluetoothConfigBlocked, vpnBlocked,
    networkResetBlocked, usbTransferBlocked, bluetoothDisabled,
    outgoingCallsBlocked, physicalMediaBlocked, mdmUninstallProtected,
    privateDnsHost: string,
    filterRunning: boolean,
    kioskPackages: string[]
  }
```

---

## Releases

| Version | Notes |
|---------|-------|
| [v37.0](releases/CoreMDM-v37.0.apk) | Fixed auth timing bug — Firestore listener now starts after Firebase Auth session restores; all remote commands work correctly |
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
| Build | Gradle 8, Android Gradle Plugin |

---

## License

MIT
