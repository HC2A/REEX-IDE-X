package com.reex.idex.core

/** Fast dependency-free structural analyzer used before a real Dart analyzer is available. */
object SourceAnalyzer {
    data class Diagnostic(val severity: Severity, val message: String, val line: Int = 1)
    enum class Severity { INFO, WARNING, ERROR }

    fun analyze(source: String): List<Diagnostic> {
        val result = mutableListOf<Diagnostic>()
        if (source.isBlank()) return listOf(Diagnostic(Severity.WARNING, "Document is empty"))
        checkBalance(source, '{', '}', "curly braces", result)
        checkBalance(source, '(', ')', "parentheses", result)
        checkBalance(source, '[', ']', "square brackets", result)
        if (source.contains("runApp(") && !Regex("(?m)\\bvoid\\s+main\\s*\\(").containsMatchIn(source)) {
            result += Diagnostic(Severity.ERROR, "runApp() is present but void main() was not found")
        }
        if (source.contains("package:flutter/") && !source.contains("import ")) {
            result += Diagnostic(Severity.WARNING, "Flutter source should contain imports")
        }
        if (result.isEmpty()) result += Diagnostic(Severity.INFO, "No structural problems detected")
        return result
    }

    private fun checkBalance(source: String, open: Char, close: Char, label: String, out: MutableList<Diagnostic>) {
        var depth = 0
        source.forEachIndexed { index, c ->
            when (c) {
                open -> depth++
                close -> if (--depth < 0) {
                    out += Diagnostic(Severity.ERROR, "Unexpected $close in $label", source.substring(0, index).count { it == '\n' } + 1)
                    depth = 0
                }
            }
        }
        if (depth != 0) out += Diagnostic(Severity.ERROR, "Unclosed $label", source.count { it == '\n' } + 1)
    }
}
