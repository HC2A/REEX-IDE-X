# REEX IDE X

Native Kotlin Android IDE/editor for Dart and Flutter-oriented development.

## v3 rebuild
- Organized IDE-style interface instead of a raw code dump
- Dart/Flutter syntax highlighting
- Line numbers, search-ready editor, snippets and structural diagnostics
- Project/file open and save through Android Storage Access Framework
- Arabic/English UI toggle
- Widget-tree inspection preview
- Offline-first editing
- ARM64-compatible Android application

## Important
The editor can support Dart/Flutter source text and offline diagnostics without the Flutter SDK. Arbitrary Dart execution, Flutter compilation and pixel-perfect live Flutter rendering require a compatible Dart/Flutter runtime. This build does not falsely label a widget-tree inspection as the real Flutter engine.

## Build
GitHub Actions builds and verifies debug plus a CI-signed release artifact.