package com.reex.idex.core

import android.content.Context
import java.io.File

data class BuildResult(
    val success: Boolean,
    val apk: File?,
    val sha256: String?,
    val log: String
)

class FlutterBuildService(context: Context) {
    private val toolchain = OfflineToolchainManager(context)

    fun build(project: File): BuildResult {
        val status = toolchain.status()
        if (!status.active) {
            return BuildResult(false, null, null, "Verified offline Flutter toolchain is not active.")
        }

        val flutter = File(status.flutter, "bin/flutter")
        if (!flutter.isFile) {
            return BuildResult(false, null, null, "Flutter executable is missing: " + flutter.absolutePath)
        }

        val env = mapOf(
            "FLUTTER_ROOT" to status.flutter.absolutePath,
            "ANDROID_HOME" to status.androidSdk.absolutePath,
            "ANDROID_SDK_ROOT" to status.androidSdk.absolutePath,
            "PUB_CACHE" to status.pubCache.absolutePath,
            "PATH" to listOf(
                File(status.flutter, "bin").absolutePath,
                File(status.androidSdk, "platform-tools").absolutePath,
                File(status.androidSdk, "cmdline-tools/latest/bin").absolutePath,
                System.getenv("PATH").orEmpty()
            ).joinToString(File.pathSeparator)
        )

        val result = LocalCommandRunner(env).run(
            project,
            listOf(flutter.absolutePath, "build", "apk", "--release", "--target-platform", "android-arm64"),
            timeoutSeconds = 1_800
        )

        val apk = project.resolve("build/app/outputs/flutter-apk/app-release.apk")
            .takeIf { it.isFile && it.length() > 0 }

        val hash = apk?.let { Hashing.sha256(it) }
        return BuildResult(result.exitCode == 0 && apk != null, apk, hash, result.output)
    }
}

object Hashing {
    fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
