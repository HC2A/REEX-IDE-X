package com.reex.idex.core

data class ToolchainCapabilities(
    val dartEditor: Boolean = true,
    val dartSyntaxHighlighting: Boolean = true,
    val dartCompletion: Boolean = true,
    val dartDiagnostics: Boolean = true,
    val dartFormatter: Boolean = true,
    val flutterEngineRuntime: Boolean = true,
    val flutterSdkLocal: Boolean = false,
    val androidSdkLocal: Boolean = false,
    val cloudFlutterBuild: Boolean = true,
    val nativeBuildLocal: Boolean = false,
    val backendDescription: String =
        "Offline IDE/editor + embedded Flutter Engine runtime; Android APK compilation is delegated to GitHub Actions."
)

object ToolchainCapabilitiesProvider {
    fun current(): ToolchainCapabilities = ToolchainCapabilities()

    fun summary(): String {
        val c = current()
        fun yes(v: Boolean) = if (v) "available" else "unavailable"
        return buildString {
            appendLine("REEX IDE X toolchain")
            appendLine("Dart editor: " + yes(c.dartEditor))
            appendLine("Dart syntax highlighting: " + yes(c.dartSyntaxHighlighting))
            appendLine("Dart completion: " + yes(c.dartCompletion))
            appendLine("Offline diagnostics: " + yes(c.dartDiagnostics))
            appendLine("Offline formatter: " + yes(c.dartFormatter))
            appendLine("Flutter Engine runtime: " + if (c.flutterEngineRuntime) "embedded" else "unavailable")
            appendLine("Local Flutter SDK: " + yes(c.flutterSdkLocal))
            appendLine("Local Android SDK: " + yes(c.androidSdkLocal))
            appendLine("GitHub cloud APK build: " + yes(c.cloudFlutterBuild))
            appendLine("Local native build: " + yes(c.nativeBuildLocal))
            append("Backend: ").append(c.backendDescription)
        }
    }
}

object ToolchainPolicy {
    fun describe(c: ToolchainCapabilities): String = buildString {
        fun yes(v: Boolean) = if (v) "available" else "unavailable"
        appendLine("Toolchain capability report")
        appendLine("Dart editor: " + yes(c.dartEditor))
        appendLine("Dart syntax highlighting: " + yes(c.dartSyntaxHighlighting))
        appendLine("Dart completion: " + yes(c.dartCompletion))
        appendLine("Dart diagnostics: " + yes(c.dartDiagnostics))
        appendLine("Dart formatter: " + yes(c.dartFormatter))
        appendLine("Flutter Engine runtime: " + if (c.flutterEngineRuntime) "available" else "unavailable")
        appendLine("Local Flutter SDK: " + yes(c.flutterSdkLocal))
        appendLine("Local Android SDK: " + yes(c.androidSdkLocal))
        appendLine("Cloud Flutter build: " + yes(c.cloudFlutterBuild))
        appendLine("Local native build: " + yes(c.nativeBuildLocal))
        append("Backend: ").append(c.backendDescription)
    }
}
