package com.reex.idex.core

import android.content.Context
import java.io.File
import java.security.MessageDigest

data class ToolchainStatus(
    val root: File,
    val flutter: File,
    val dart: File,
    val androidSdk: File,
    val pubCache: File,
    val active: Boolean,
    val verification: String
)

class OfflineToolchainManager(context: Context) {
    private val base = File(context.filesDir, "toolchains")
    private val flutter = File(base, "flutter")
    private val dart = File(flutter, "bin/cache/dart-sdk")
    private val androidSdk = File(base, "android-sdk")
    private val pubCache = File(base, "pub-cache")
    private val marker = File(base, ".active.json")

    init {
        base.mkdirs()
    }

    fun status(): ToolchainStatus {
        val active = marker.isFile && flutter.isDirectory && dart.isDirectory &&
            androidSdk.isDirectory && pubCache.isDirectory
        val verification = if (!marker.isFile) {
            "NOT_INITIALIZED"
        } else {
            val text = runCatching { marker.readText(Charsets.UTF_8) }.getOrDefault("")
            if (text.contains(""verified":true")) "VERIFIED" else "UNVERIFIED"
        }
        return ToolchainStatus(base, flutter, dart, androidSdk, pubCache, active, verification)
    }

    fun activateVerified(root: File, expectedSha256: String): Boolean {
        if (!root.isDirectory) return false
        val manifest = File(root, "bin/cache/flutter_tools.stamp")
        if (!manifest.isFile) return false
        val actual = sha256(manifest)
        if (!actual.equals(expectedSha256, ignoreCase = true)) return false
        marker.writeText(
            """{"verified":true,"sha256":"$actual"}""",
            Charsets.UTF_8
        )
        return true
    }

    fun prepareDirectories() {\n        listOf(flutter, dart, androidSdk, pubCache, File(base, "gradle-cache")).forEach { it.mkdirs() }\n    }\n\n    fun invalidate() {
        marker.delete()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
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
