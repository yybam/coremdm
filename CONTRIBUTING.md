# Contributing to CORE MDM

Thanks for your interest! CORE MDM is a beta Android MDM system (Android app + Firebase web console). This guide covers how to build, test, and submit changes.

## Before you start
- Read the [Wiki](https://github.com/yybam/coremdm/wiki) — especially [Architecture](https://github.com/yybam/coremdm/wiki/Architecture), [Building from Source](https://github.com/yybam/coremdm/wiki/Building-from-Source), and [Firestore Data Model](https://github.com/yybam/coremdm/wiki/Firestore-Data-Model).
- Search [existing issues](https://github.com/yybam/coremdm/issues) before opening a new one.

## Dev setup
- **JDK 17** and the **Android SDK** (Android Studio bundles both).
- Build: `./gradlew assembleDebug` (or `assembleRelease`).
- The web console is `public/index.html` — plain ES-module JS, no build step; edit and deploy.
- You'll need your own Firebase project + `app/google-services.json` to run end-to-end. See [Building from Source](https://github.com/yybam/coremdm/wiki/Building-from-Source).

## Testing your change
- **Device Owner features** are easiest to test on an emulator (a fresh AVD has no accounts). See [Testing with an Emulator](https://github.com/yybam/coremdm/wiki/Testing-with-an-Emulator).
- At minimum, verify the app builds (`./gradlew assembleRelease`) and the console still loads.
- For console JS, check the browser console for errors after your change.

## Pull requests
1. Branch off `main`.
2. Keep each PR focused; write a clear description of **what** changed and **why**.
3. If you change the data model or rules, update `firestore.rules`, the Wiki, and the README together.
4. If you add a user-facing feature, bump `versionCode`/`versionName` in `app/build.gradle.kts` only when cutting a release (maintainers usually handle release tagging).
5. Make sure CI (APK build) is green.

## Commit style
- Imperative, present tense: "Add social-media block toggle".
- Reference issues where relevant (`Fixes #12`).

## Reporting bugs / requesting features
Use the issue templates (New issue → pick a template). For security issues, follow [SECURITY.md](SECURITY.md) instead of a public issue.

## Code areas
| Area | Where |
|---|---|
| Command execution, policy apply | `app/.../service/MdmCommandService.kt` |
| Firestore I/O, command queue, inventory | `app/.../firebase/DeviceRegistry.kt`, `EnrollmentManager.kt` |
| DevicePolicyManager wrapper | `app/.../policy/DevicePolicyHelper.kt`, `AppPolicyManager.kt` |
| Content filter VPN | `app/.../vpn/DnsVpnService.kt` |
| In-app PIN | `app/.../security/PinManager.kt` |
| Web console | `public/index.html` |
| Security rules | `firestore.rules` |

By contributing you agree your contributions are licensed under the repository's [MIT License](LICENSE).
