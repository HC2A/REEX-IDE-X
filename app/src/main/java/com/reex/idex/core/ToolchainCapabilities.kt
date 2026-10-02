package com.reex.idex.core

import android.content.Context

data class ToolchainCapabilities(
    val dartEditor: Boolean,
    val dartSyntaxHighlighting: Boolean,
    val dartCompletion: Boolean,
    val dartDiagnostics: Boolean,
    val dartFormatter: Boolean,
    val flutterEngineRuntime: Boolean,
    val flutterSdkLocal: Boolean,
    val androidSdkLocal: Boolean,
    val cloudFlutterBuild: Boolean,
    val nativeBuildLocal: Boolean,
    val backendDescription: String
)

object ToolchainCapabilitiesProvider {
    fun current(context: Context): ToolchainCapabilities {
        val status = OfflineToolchainManager(context).status()
        val dart = FileProbe.executable(status.dart, "bin/dart")
        val flutter = FileProbe.executable(status.flutter, "bin/flutter")
        return ToolchainCapabilities(
            dartEditor = true,
            dartSyntaxHighlighting = true,
            dartCompletion = status.active && dart,
            dartDiagnostics = status.active && dart,
            dartFormatter = status.active && dart,
            flutterEngineRuntime = true,
            flutterSdkLocal = status.active && flutter,
            androidSdkLocal = status.active && status.androidSdk.isDirectory,
            cloudFlutterBuild = true,
            nativeBuildLocal = status.active && flutter && status.androidSdk.isDirectory,
            backendDescription = if (status.active) {
                "Verified local Dart/Flutter toolchain + embedded Flutter Engine; remote build remains available as fallback."
            } else {
                "Embedded Flutter Engine is available. Local Dart/Flutter toolchain is not activated; remote build is the fallback."
            }
        )
    }

    fun summary(context: Context): String {
        val c = current(context)
        fun yes(v: Boolean) = if (v) "available" else "unavailable"
        return buildString {
            appendLine("REEX IDE X toolchain")
            appendLine("Dart editor: " + yes(c.dartEditor))
            appendLine("Dart syntax highlighting: " + yes(c.dartSyntaxHighlighting))
            appendLine("Dart LSP completion: " + yes(c.dartCompletion))
            appendLine("Dart diagnostics: " + yes(c.dartDiagnostics))
            appendLine("Dart formatter: " + yes(c.dartFormatter))
            appendLine("Flutter Engine runtime: " + if (c.flutterEngineRuntime) "embedded" else "unavailable")
            appendLine("Local Flutter SDK: " + yes(c.flutterSdkLocal))
            appendLine("Local Android SDK: " + yes(c.androidSdkLocal))
            appendLine("GitHub cloud Flutter build: " + yes(c.cloudFlutterBuild))
            appendLine("Local native build: " + yes(c.nativeBuildLocal))
            append("Backend: ").append(c.backendDescription)
        }
    }
}

private object FileProbe {
    fun executable(root: java.io.File, relative: String): Boolean =
        java.io.File(root, relative).isFile
}

object ToolchainPolicy {
    fun describe(c: ToolchainCapabilities): String = buildString {
        fun yes(v: Boolean) = if (v) "available" else "unavailable"
        appendLine("Toolchain capability report")
        appendLine("Dart editor: " + yes(c.dartEditor))
        appendLine("Dart syntax highlighting: " + yes(c.dartSyntaxHighlighting))
        appendLine("Dart LSP completion: " + yes(c.dartCompletion))
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
