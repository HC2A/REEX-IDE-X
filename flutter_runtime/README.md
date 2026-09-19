# REEX Flutter Runtime Module

This directory defines the source-level contract for the embedded Flutter runtime used by REEX IDE X.

CI generates the Android module with the official Flutter SDK and builds its AAR into a local Maven repository before assembling the Android host.

The embedded runtime is a real Flutter Engine experience. It renders a preview route from REEX IDE X, rather than the old Compose-only simulator.

Arbitrary Dart source is not dynamically compiled inside the host APK: true arbitrary-project execution still requires a Flutter SDK/toolchain. The architecture keeps the editor independent and uses the official Flutter module/AAR boundary for the on-device runtime.
