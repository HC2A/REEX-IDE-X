package com.reex.idex.core

import android.content.Context
import java.io.File
import java.security.MessageDigest

data class ToolchainStatus(
    val flutterVersion: String,
    val primaryAbi: String,
    val flutterHome: File,
    val dartHome: File,
    val androidSdk: File,
    val pubCache: File,
    val ready: Boolean
)

class ToolchainManager(private val context: Context) {
    private val root = File(context.filesDir, "toolchain").also { it.mkdirs() }

    fun status(): ToolchainStatus {
        val flutter = File(root, "flutter")
        val dart = File(flutter, "bin/cache/dart-sdk")
        val android = File(root, "android-sdk")
        val pub = File(root, "pub-cache")
        val ready = File(flutter, "bin/flutter").canExecute() &&
            File(dart, "bin/dart").canExecute() &&
            android.isDirectory &&
            pub.isDirectory
        return ToolchainStatus("3.47.3", "arm64-v8a", flutter, dart, android, pub, ready)
    }

    fun environmentRoot(): File = root

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifySha256(file: File, expected: String): Boolean =
        file.isFile && sha256(file).equals(expected.trim(), ignoreCase = true)

    fun prepareDirectories() {
        listOf(
            File(root, "flutter"),
            File(root, "flutter/bin/cache"),
            File(root, "android-sdk"),
            File(root, "pub-cache"),
            File(root, "gradle-cache")
        ).forEach { it.mkdirs() }
    }
}
