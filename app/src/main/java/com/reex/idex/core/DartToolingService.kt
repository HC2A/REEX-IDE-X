package com.reex.idex.core

import org.json.JSONArray
import java.io.File

data class DartToolingResult(
    val success: Boolean,
    val diagnostics: List<Diagnostic>,
    val output: String
)

class DartToolingService(private val toolchain: OfflineToolchainManager) {
    fun analyze(project: File): DartToolingResult {
        val status = toolchain.status()
        val dart = File(status.dart, "bin/dart")
        if (!status.active || !dart.isFile) {
            return DartToolingResult(false, emptyList(), "Real Dart analyzer unavailable; using structural fallback.")
        }

        val env = mapOf(
            "PUB_CACHE" to status.pubCache.absolutePath,
            "PATH" to listOf(
                dart.parentFile.absolutePath,
                File(status.flutter, "bin").absolutePath,
                System.getenv("PATH").orEmpty()
            ).joinToString(File.pathSeparator)
        )
        val result = LocalCommandRunner(env).run(
            project,
            listOf(dart.absolutePath, "analyze", "--format=json"),
            600
        )
        return DartToolingResult(
            success = result.exitCode == 0,
            diagnostics = parseDiagnostics(result.output),
            output = result.output
        )
    }

    fun format(project: File, file: File): CommandResult {
        val status = toolchain.status()
        val dart = File(status.dart, "bin/dart")
        if (!status.active || !dart.isFile) return CommandResult(-1, "Real Dart formatter unavailable.")
        return LocalCommandRunner(
            mapOf(
                "PUB_CACHE" to status.pubCache.absolutePath,
                "PATH" to listOf(dart.parentFile.absolutePath, File(status.flutter, "bin").absolutePath, System.getenv("PATH").orEmpty())
                    .joinToString(File.pathSeparator)
            )
        ).run(project, listOf(dart.absolutePath, "format", file.absolutePath), 300)
    }

    private fun parseDiagnostics(output: String): List<Diagnostic> {
        val jsonStart = output.indexOf('{')
        if (jsonStart < 0) return emptyList()
        return runCatching {
            val root = org.json.JSONObject(output.substring(jsonStart))
            val issues = root.optJSONArray("diagnostics") ?: JSONArray()
            buildList {
                for (i in 0 until issues.length()) {
                    val d = issues.getJSONObject(i)
                    val sev = when (d.optString("severity").lowercase()) {
                        "error" -> Severity.ERROR
                        "warning" -> Severity.WARNING
                        else -> Severity.INFO
                    }
                    val loc = d.optJSONObject("location")
                    val range = loc?.optJSONObject("range")
                    val start = range?.optJSONObject("start")
                    add(
                        Diagnostic(
                            severity = sev,
                            message = d.optString("problemMessage", d.optString("message", "Dart analyzer diagnostic")),
                            line = start?.optInt("line", 1) ?: 1,
                            column = start?.optInt("column", 1) ?: 1
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
