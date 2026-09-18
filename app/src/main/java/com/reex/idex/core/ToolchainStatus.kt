package com.reex.idex.core

/** Explicit capability model; prevents the UI from claiming a toolchain exists when it does not. */
data class ToolchainStatus(
    val dartInstalled: Boolean = false,
    val flutterInstalled: Boolean = false,
    val androidSdkAvailable: Boolean = false,
    val message: String = "Toolchains not configured"
) {
    val canAnalyze: Boolean get() = dartInstalled
    val canBuildFlutter: Boolean get() = flutterInstalled && androidSdkAvailable
}
