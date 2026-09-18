package com.reex.idex.core

data class ToolchainCapabilities(
    val dartAnalyzer: Boolean = false,
    val dartFormatter: Boolean = false,
    val flutterSdk: Boolean = false,
    val androidSdk: Boolean = false,
    val nativeBuild: Boolean = false,
    val backendDescription: String = "No execution backend configured"
)

object ToolchainPolicy {
    fun describe(c: ToolchainCapabilities): String = buildString {
        appendLine("Toolchain capability report")
        appendLine("Dart analyzer: ${if (c.dartAnalyzer) "available" else "unavailable"}")
        appendLine("Dart formatter: ${if (c.dartFormatter) "available" else "unavailable"}")
        appendLine("Flutter SDK: ${if (c.flutterSdk) "available" else "unavailable"}")
        appendLine("Android SDK: ${if (c.androidSdk) "available" else "unavailable"}")
        appendLine("Native build: ${if (c.nativeBuild) "available" else "unavailable"}")
        append("Backend: ").append(c.backendDescription)
    }
}
