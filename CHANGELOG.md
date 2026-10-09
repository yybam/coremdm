# Changelog

All notable changes to CORE MDM. This project uses `vX.Y.Z` release tags; the Android app has
its own `versionCode`/`versionName`. See the [Releases page](https://github.com/yybam/coremdm/releases)
for downloadable APKs.

## [v0.4.7](https://github.com/yybam/coremdm/releases/tag/v0.4.7) — app 0.4.7 (versionCode 44)
The complete, fully-unified build: everything from both work streams in one APK,
built from `main`. Supersedes the parallel `v0.4.5`/`v0.4.6` tags (versionCode 43)
that were cut mid-merge and were each missing some features.
### Added (now all in one build)
- **Screen-off auto-lock** — the app locks the instant the screen turns off
  (`ACTION_SCREEN_OFF`), on top of the existing lock-on-background.
- **Prominent Wipe Device** button in the console's Commands tab (also still in Danger).
- **Delete a device entry from the console** — removes the device doc + command
  history so a phone re-enrolls cleanly.
- **In-app update notifications over FCM.** An `update_available` push shows a
  high-priority notification with a **Download** action that opens the installer
  (`coremdm.web.app/install`). Ported from the second work stream, with branding
  normalized to "CORE MDM".
- **`scripts/broadcast-update.js`** — pushes an update ping to every enrolled device
  (reads the project id from `FIREBASE_PROJECT_ID`, needs a `firebase login:ci` token).
- **Delete a device entry from the console** (Danger tab) — permanently removes the
  device document and its command history so a phone can be re-enrolled cleanly.
### Fixed
- Hosting now serves **clean URLs** (`/install`, `/console`, …) like the old Vercel
  site, and allows **WebUSB** (`usb=(self)`) so the USB installer works.

## [v0.4.3](https://github.com/yybam/coremdm/releases/tag/v0.4.3) — app 41.0
### Fixed
- **Endless reboot loop.** The field-based `rebootCommand` cleared the flag *after*
  `dpm.reboot()`, so the write never reached Firestore and the device re-triggered the
  reboot on every boot. The flag is now cleared and **server-confirmed before** rebooting;
  if the clear can't be written (e.g. offline) the device does not reboot. (The newer
  command-queue path was already safe — it marks the command `executed` on the server
  before executing.)

## [v0.4.2](https://github.com/yybam/coremdm/releases/tag/v0.4.2) — app 40.0
### Added
- **Per-app Allow / Block / Hide** in the console's Installed Apps tab, with status badges,
  search and counts. "Blocked" suspends an app (visible but won't open, via a new
  `suspendedApps` policy); "Hidden" removes it from the launcher (`blockedApps`).
- **Full front-end site** at coremdm.web.app — landing, Features, Demo, FAQ, About and legal
  pages (Terms, Privacy, Consent). The device console moved to `/console.html` (reached via
  **Manage**); the installer is at `/install.html`.
### Fixed
- Nav logo is now a centered SVG; demo brand text normalized to "CORE MDM".
- Installer's bundled APK + `version.json` now refresh automatically on each release
  (from build-apk.yml).

## [v0.4.1](https://github.com/yybam/coremdm/releases/tag/v0.4.1) — app 39.0
### Fixed
- **Content filter VPN** now works from the web console on **Device Owner** devices. The app
  registers itself as the always-on VPN, which pre-authorizes and starts the tunnel with no
  on-device consent tap, and it survives reboot. Non-Device-Owner devices keep the manual-consent
  fallback.

## [v0.4.0](https://github.com/yybam/coremdm/releases/tag/v0.4.0) — app 38.0
### Added
- **Cancellable command queue** — Lock/Reboot/Wipe/Full Lockdown are queued; pending commands
  show in the console with a **Cancel** button and are claimed via a transaction so a cancelled
  command never runs.
- **Device rename** from the console.
- **Installed Apps tab** — device reports its apps; hide any app per-package.
- **Block Social Media** toggle (suspends a preset app list).
- **Configurable minimum PIN length** (4/6/8), enforced on the lock screen and in-app.
### Changed
- Commands moved from boolean fields to a `devices/{id}/commands` subcollection; Firestore rules updated.

## [v0.3.2](https://github.com/yybam/coremdm/releases/tag/v0.3.2) — app 37.0
### Fixed
- Enrollment of **brand-new devices** (first-time `PERMISSION_DENIED` now falls through to create),
  so new devices appear in the console and receive commands.

## [v0.3.1](https://github.com/yybam/coremdm/releases/tag/v0.3.1) — app 36.0
### Changed
- First release signed with the **permanent release key** — updates now install in place.
- **Google Sign-In** works (signing certificate registered in Firebase).

## [v0.3.0](https://github.com/yybam/coremdm/releases/tag/v0.3.0) — app 36.0
### Added
- **Google Sign-In** alongside email/password.
- **Mandatory PIN lock** on every app open.
- **Enrollment tokens** wired to the Android app.
- Reliable command delivery — the device re-attaches its Firestore listener automatically.
> Signed with a throwaway key; prefer v0.3.1+.

## Earlier
- **v0.2.1** — dropped `android:testOnly` so Device Owner can't be removed via plain ADB without root.
- **v37.0** — fixed auth timing so the Firestore listener starts after Firebase Auth restores.
- **v36.0** — full policy remote control, web console overhaul.
- **v35.0** — Core MDM branding; lock/wipe/reboot commands.
