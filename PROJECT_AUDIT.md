# REEX IDE X — Project Audit & Build Stability Notes

## Scope
This document records the build-stability audit for the current Android IDE/editor tree and prevents previously fixed classes of errors from being reintroduced.

## Current build stack
- Android Gradle Plugin: 8.13.0
- Kotlin: 2.4.20
- Gradle CI: 8.13
- compileSdk / targetSdk: 36
- Java: 17
- Jetpack Compose BOM: 2026.06.00
- Sora Editor: 0.24.4
- Minimum Android: API 24

## Latest verified failure and correction
The latest CI build (#113) reached the Android host compilation stage and failed in `TextMateEditorSupport.kt` because `FileProviderRegistry` was referenced without being imported/registered. Sora's TextMate integration requires an assets file provider before themes/grammars are loaded. The source has now been corrected to import `FileProviderRegistry`, register `AssetsFileResolver(context.applicationContext.assets)`, and fail clearly if the theme asset is missing.

## Build-environment warnings
GitHub Actions previously reported that `actions/checkout@v4` and `actions/setup-java@v4` target Node 20. The workflows now use current major versions (`checkout@v7`, `setup-java@v6`) to remove that maintenance warning while retaining Java 17.

## Source architecture rules
1. Keep one authoritative `Diagnostic` model. Do not recreate it in multiple files.
2. Keep the offline Dart analyzer explicitly structural/heuristic. It is not the Dart Analysis Server.
3. Keep Flutter preview clearly labeled as a local simulator until arbitrary user source can actually be compiled/executed by an embedded toolchain.
4. Keep Storage Access Framework for file open/save so the app works without broad filesystem permissions.
5. Keep Sora Editor APIs pinned to the verified 0.24.4 coordinates:
   - `io.github.rosemoe:editor`
   - `io.github.rosemoe:language-textmate`
   - `io.github.rosemoe:oniguruma-native`
6. Avoid deprecated Gradle DSL such as `resourceConfigurations`; use `androidResources.localeFilters`.
7. Keep Kotlin JVM target as `JvmTarget.JVM_17`, not a raw string.
8. Do not add arbitrary libraries merely to silence a compiler error. Prefer the smallest compatible fix.
9. Cloud artifact lookup must match the workflow's architecture-specific artifact name: `reex-flutter-apk-<architecture>`.

## Functional audit status
- Dart editor: present.
- Dart TextMate syntax highlighting: wired to the bundled Dart grammar/theme; must be validated by the release build and runtime smoke test.
- Offline completion: present, catalog-based rather than semantic/type-aware IntelliSense.
- Offline diagnostics: present, structural/heuristic rather than the official Dart analyzer.
- Offline formatter: only a safe heuristic fixer is present; it is not `dart format`.
- Flutter Engine runtime: embedded for a real Flutter-rendered preview route, but it does not compile arbitrary edited Dart source.
- Project tree: currently a source-derived synthetic tree, not a full filesystem-backed Flutter workspace.
- Cloud Flutter APK build: present; current builder uploads a minimal generated project and must be expanded to upload the full workspace before claiming complete arbitrary-project support.
- Local Android/Flutter SDK build: intentionally unavailable by architecture; APK compilation is delegated to GitHub Actions.

## Release gate
A release is considered verified only when all of these complete successfully:
1. `gradle clean assembleDebug assembleRelease`
2. release APK exists and is non-empty
3. `aapt2 dump badging` reports `com.reex.idex`
4. `zipalign -c` passes
5. signed release APK is produced
6. `apksigner verify --verbose` passes
7. the final signed ARM64 APK is uploaded as a single artifact
8. the cloud Flutter workflow, when exercised, uploads `reex-flutter-apk-<architecture>` and the app finds that exact artifact name

## Cleanup policy
Do not delete source files solely because they appear unused until repository-wide references are checked. Remove duplicates/dead code only after confirming they are not referenced by Kotlin, resources, manifest, or build configuration.
