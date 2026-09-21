package com.reex.idex.core

import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.widget.Toast
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.dart.DartExecutor
import java.io.File
import java.io.FileOutputStream

/**
 * Starts a real FlutterEngine from a debug/JIT kernel bundle.
 *
 * The editor never interprets Dart itself. A real Flutter toolchain produces
 * kernel_blob.bin + the matching debug VM snapshots; this class installs that
 * bundle locally and asks the embedded engine to execute main().
 */
object FlutterRuntimeBridge {
    private const val ENGINE_ID = "reex-user-code-engine"

    fun isAvailable(): Boolean = runCatching {
        Class.forName("io.flutter.embedding.engine.FlutterEngine")
        true
    }.getOrDefault(false)

    fun launchCompiled(context: Context, compiledBundle: File, arabic: Boolean) {
        runCatching {
            require(compiledBundle.isDirectory) { "Runtime bundle is missing" }
            require(File(compiledBundle, "kernel_blob.bin").isFile) { "kernel_blob.bin is missing" }
            require(File(compiledBundle, "vm_snapshot_data").isFile) { "vm_snapshot_data is missing" }
            require(File(compiledBundle, "isolate_snapshot_data").isFile) { "isolate_snapshot_data is missing" }

            val bundle = File(context.filesDir, "reex/runtime/active/flutter_assets")
            bundle.deleteRecursively()
            bundle.mkdirs()

            // Keep the framework/fonts/assets shipped with the embedded Flutter module.
            copyAssetTree(context.assets, "flutter_assets", bundle)
            // Overlay the freshly compiled user project kernel and its assets.
            copyDirectory(compiledBundle, bundle)

            FlutterEngineCache.getInstance().get(ENGINE_ID)?.let {
                runCatching { it.destroy() }
                FlutterEngineCache.getInstance().remove(ENGINE_ID)
            }

            val engine = FlutterEngine(context.applicationContext)
            engine.navigationChannel.setInitialRoute("/")
            val entrypoint = DartExecutor.DartEntrypoint(bundle.absolutePath, "main")
            engine.dartExecutor.executeDartEntrypoint(entrypoint)
            FlutterEngineCache.getInstance().put(ENGINE_ID, engine)

            val intent = FlutterActivity.withCachedEngine(ENGINE_ID)
                .destroyEngineWithActivity(true)
                .build(context)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(
                context,
                (if (arabic) "تعذر تشغيل كود Flutter الحقيقي: " else "Real Flutter execution failed: ") +
                    (it.message ?: "unknown error"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun copyAssetTree(assets: AssetManager, assetPath: String, target: File) {
        val children = assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            assets.open(assetPath).use { input ->
                target.parentFile?.mkdirs()
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            return
        }
        target.mkdirs()
        for (child in children) {
            copyAssetTree(assets, "$assetPath/$child", File(target, child))
        }
    }

    private fun copyDirectory(source: File, target: File) {
        source.walkTopDown().forEach { sourceFile ->
            val relative = sourceFile.relativeTo(source)
            val targetFile = File(target, relative.path)
            if (sourceFile.isDirectory) targetFile.mkdirs()
            else {
                targetFile.parentFile?.mkdirs()
                sourceFile.inputStream().use { input ->
                    FileOutputStream(targetFile).use { output -> input.copyTo(output) }
                }
            }
        }
    }
}
