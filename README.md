# REEX AIDE v3 / REEX IDE X

Modern Android IDE/editor focused on Dart and Flutter, designed for offline-first editing with a real embedded Flutter Engine runtime in verified CI builds.

## What changed in v3.2

The previous release used a Compose-only preview simulator. This version adds a real Flutter add-to-app runtime boundary:

- Official Flutter stable toolchain used in CI.
- Flutter module is generated and built as an Android AAR.
- The AAR is consumed from a local Maven repository during the host build.
- The final APK is checked for `libflutter.so` on ARM64, ARM32 and x86_64.
- The final AndroidManifest is checked for Flutter embedding metadata.
- `RUN FLUTTER` launches the embedded Flutter Engine through the official `FlutterActivity` NewEngine API.
- The editor passes a route containing the current source's preview metadata into the real Flutter UI.
- The old Compose simulator remains available as `SIMULATOR` for lightweight offline inspection.

Flutter's official add-to-app model supports embedding a Flutter module into an Android host, including AAR-based integration and `FlutterEngine`/`FlutterActivity` runtime execution. citeturn5search0turn7search0

## Architecture boundary

There are two distinct layers:

1. **Editor layer:** Kotlin/Compose + Sora editor + local diagnostics/completion/project models.
2. **Flutter runtime layer:** generated Flutter module + Flutter Engine AAR.

The embedded engine is real. However, an Android APK cannot magically compile arbitrary new Dart source without a Dart/Flutter compiler toolchain. Arbitrary-project compilation remains a separate toolchain operation; the embedded runtime currently renders a Flutter runtime preview based on the edited source metadata. Flutter's own tooling uses the frontend server to compile Dart into Kernel binaries before sending code to the runtime. citeturn4search0turn4search2

## Flutter version

CI is pinned to Flutter **3.47.3 stable** and verifies the downloaded archive SHA-256 before installation. Flutter 3.47.x is the 2026 stable line. citeturn2search1turn8search6

## Editor features

- Kotlin + Jetpack Compose UI
- Sora code editor
- Dart/TextMate syntax assets
- Dart structural diagnostics
- Safe local fixes
- Offline completion engine
- Project tree model
- Language registry
- Storage Access Framework open/save
- Arabic/English UI
- Real Flutter Engine launch action
- Local simulator
- GitHub Actions APK verification and signing

## Build verification

CI now verifies:

- Android package name
- APK existence and non-zero size
- zipalign
- APK signing
- `libflutter.so` for `arm64-v8a`
- `libflutter.so` for `armeabi-v7a`
- `libflutter.so` for `x86_64`
- Flutter embedding metadata
- Debug and signed Release APKs

Flutter's Android documentation identifies `libflutter.so` as the native Flutter runtime library and documents the add-to-app/AAR architecture. citeturn1search5turn5search0

## Important

A green Android build proves the package can be assembled and structurally verified. It does not replace physical-device testing. The next validation target is installation on an ARM64 Android phone and opening **RUN FLUTTER** to confirm the embedded engine renders correctly on-device.

Use JDK 17 for the Android host build.


<!-- CI verification hardened 2026-09-19 -->
