# REEX IDE X

Native Android IDE for mobile development, designed for ARM64 devices and no root requirement.

## Current engineering milestone

- Native Kotlin Android application shell
- Persistent app-private workspaces
- SAF-compatible workspace URI persistence
- Dependency-free structural source diagnostics
- Explicit toolchain capability model (no fake build/run claims)
- Dart/Flutter-oriented editor, snippets, completion and widget inspection
- Reproducible GitHub Actions APK verification

## Architecture direction

The project is being developed in layers:

1. Workspace and file-system abstraction
2. Editor/document model and diagnostics
3. Toolchain discovery and installation adapters
4. Terminal/process execution abstraction
5. Dart/Flutter project lifecycle
6. Build/export and APK verification
7. Device/preview integration where Android platform constraints permit

Important: desktop Flutter SDK binaries cannot simply be executed on Android ARM64. The implementation therefore separates UI/editor functionality from toolchain backends and will only expose build/run capabilities when a compatible backend is actually available.

## Build

Open in Android Studio or AIDE, or use the repository GitHub Actions workflow. The verified workflow produces debug and unsigned release APK artifacts.
