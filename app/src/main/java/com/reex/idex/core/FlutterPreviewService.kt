package com.reex.idex.core

import android.content.Context
import android.content.Intent
import java.io.File

data class PreviewResult(val success: Boolean, val bundle: File?, val log: String)

class FlutterPreviewService(private val context: Context) {
    private val toolchain = OfflineToolchainManager(context)

    fun prepare(project: File, sourceRelativePath: String = "lib/main.dart"): PreviewResult {
        if (!project.isDirectory) return PreviewResult(false, null, "Project directory does not exist.")
        val status = toolchain.status()
        if (!status.active) return PreviewResult(false, null, "A verified local Flutter toolchain is required for real offline preview.")
        val flutter = File(status.flutter, "bin/flutter")
        if (!flutter.isFile) return PreviewResult(false, null, "Flutter executable missing: " + flutter.absolutePath)
        val source = File(project, sourceRelativePath)
        if (!source.isFile) return PreviewResult(false, null, "Flutter entrypoint not found: " + source.absolutePath)

        val env = mapOf(
            "FLUTTER_ROOT" to status.flutter.absolutePath,
            "ANDROID_HOME" to status.androidSdk.absolutePath,
            "ANDROID_SDK_ROOT" to status.androidSdk.absolutePath,
            "PUB_CACHE" to status.pubCache.absolutePath,
            "PATH" to listOf(
                File(status.flutter, "bin").absolutePath,
                File(status.flutter, "bin/cache/dart-sdk/bin").absolutePath,
                File(status.androidSdk, "platform-tools").absolutePath,
                File(status.androidSdk, "cmdline-tools/latest/bin").absolutePath,
                System.getenv("PATH").orEmpty()
            ).joinToString(File.pathSeparator)
        )

        val pub = LocalCommandRunner(env).run(project, listOf(flutter.absolutePath, "pub", "get", "--offline"), 600)
        if (pub.exitCode != 0) return PreviewResult(false, null, "flutter pub get --offline failed.
" + pub.output)

        val build = LocalCommandRunner(env).run(
            project,
            listOf(flutter.absolutePath, "build", "bundle", "--debug", "--target-platform", "android-arm64", "--target", sourceRelativePath),
            1800
        )
        val bundle = project.resolve("build/flutter_assets")
        val kernel = bundle.resolve("kernel_blob.bin")
        if (build.exitCode != 0 || !bundle.isDirectory || !kernel.isFile || kernel.length() == 0L) {
            return PreviewResult(false, null, "Flutter debug bundle compilation failed.
" + build.output)
        }
        return PreviewResult(true, bundle, build.output)
    }

    fun launch(bundle: File, route: String = "/") {
        val intent = Intent(context, DynamicFlutterPreviewActivity::class.java)
            .putExtra(DynamicFlutterPreviewActivity.EXTRA_BUNDLE_PATH, bundle.absolutePath)
            .putExtra(DynamicFlutterPreviewActivity.EXTRA_INITIAL_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun prepareAndLaunch(project: File, sourceRelativePath: String = "lib/main.dart"): PreviewResult {
        val result = prepare(project, sourceRelativePath)
        if (result.success && result.bundle != null) launch(result.bundle)
        return result
    }
}
