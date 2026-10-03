package com.reex.idex.core

import java.io.File

data class TerminalResult(val exitCode: Int, val output: String)

class TerminalService {
    fun execute(workingDirectory: File, command: String, timeoutSeconds: Long = 120): TerminalResult {
        if (!workingDirectory.isDirectory) return TerminalResult(-1, "Working directory does not exist")
        val result = LocalCommandRunner().run(
            workingDirectory,
            listOf("/system/bin/sh", "-c", command),
            timeoutSeconds
        )
        return TerminalResult(result.exitCode, result.output)
    }
}
