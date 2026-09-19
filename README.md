# REEX AIDE v3

Modern offline-first Android IDE/editor focused on Flutter and Dart.

## Current rebuild branch

`reex-aide-v3-rebuild`

The rebuild follows the square UI concept: dark glass panels, cyan/purple accents, editor-first layout, project tree, smart completion, diagnostics, snippets, local preview, and bilingual UI.

## Implemented foundation

- Kotlin + Jetpack Compose Android UI
- Sora editor 0.24.4
- Dart structural diagnostics
- Safe local fixes
- Project tree model
- Offline completion engine
- Language registry for Dart, Kotlin, Java, Python, JavaScript, TypeScript, HTML, CSS, JSON, XML, C/C++ and Markdown
- Storage Access Framework open/save
- Arabic/English UI
- Local preview simulator with explicit toolchain limitation
- GitHub Actions release verification
- ARM64-compatible Android architecture through the standard Android build

## Important architecture boundary

The current preview is a simulator. A true pixel-accurate Flutter runtime requires an embedded/available Flutter toolchain and is a separate integration phase. The project must not claim arbitrary Flutter execution until that integration is verified.

## Build

Use JDK 17 and the repository's GitHub Actions workflow. Release verification includes APK existence, package-name verification, zipalign and apksigner checks.
