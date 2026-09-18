package com.reex.idex.core

enum class Severity { INFO, WARNING, ERROR }

data class Diagnostic(val severity: Severity, val message: String, val line: Int = 0, val column: Int = 0) {
    override fun toString(): String = buildString {
        append(severity.name)
        if (line > 0) append(" [$line:$column]")
        append(": ").append(message)
    }
}
