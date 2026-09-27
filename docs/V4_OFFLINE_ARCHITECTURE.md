# REEX IDE X v4 — Offline Foundation

## Release target
Android-first Flutter/Dart IDE foundation for arm64-v8a devices.

## Runtime layers
- Android host: Kotlin/Jetpack Compose.
- Editor: Sora.
- Embedded Flutter runtime: existing Flutter add-to-app/AAR integration.
- Toolchain manager: versioned local environment.
- Offline caches: Pub, Gradle, Flutter artifacts.
- Remote fallback: GitHub Actions.

## Offline contract
The IDE must never claim arbitrary-project offline compilation unless the required SDK, engine, Gradle and package artifacts are present locally.

Offline-ready checks:
1. Dart executable.
2. Flutter executable.
3. Flutter artifacts.
4. Android SDK/platform/build-tools.
5. Gradle dependencies.
6. Pub cache.
7. arm64-v8a build test.

## Storage
Use shared caches rather than copying SDKs into every project.

## Build fallback
When local compilation is unavailable, the UI may dispatch the project to GitHub Actions. Remote builds must return an APK plus SHA-256.

## Security
All downloaded environment artifacts must be hash-verified before activation. Secrets must use Android secure storage and never be committed.

## v4.0 foundation scope
- Android-only release target.
- arm64-v8a primary ABI.
- Existing real Flutter Engine integration preserved.
- Offline environment manifest introduced.
- CI validation path introduced.
- Future phases add executable local toolchain management without replacing the working runtime.
