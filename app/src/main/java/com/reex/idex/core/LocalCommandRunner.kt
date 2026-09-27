package com.reex.idex.core

import java.io.File
import java.util.concurrent.TimeUnit

data class CommandResult(
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean = false
)

class LocalCommandRunner(private val environment: Map<String, String> = emptyMap()) {
    fun run(
        workingDirectory: File,
        command: List<String>,
        timeoutSeconds: Long = 300
    ): CommandResult {
        if (!workingDirectory.isDirectory) {
            return CommandResult(-1, "Working directory does not exist")
        }
        return runCatching {
            val process = ProcessBuilder(command)
                .directory(workingDirectory)
                .redirectErrorStream(true)
                .apply {
                    environment().putAll(environment)
                }
                .start()

            val output = StringBuilder()
            val reader = process.inputStream.bufferedReader(Charsets.UTF_8)
            val thread = Thread {
                reader.forEachLine { line ->
                    output.append(line).append('\n')
                }
            }.apply { isDaemon = true; start() }

            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return CommandResult(-1, output.toString() + "\nTIMEOUT", true)
            }

            thread.join(2_000)
            CommandResult(process.exitValue(), output.toString())
        }.getOrElse { error ->
            CommandResult(-1, error.stackTraceToString())
        }
    }
}
