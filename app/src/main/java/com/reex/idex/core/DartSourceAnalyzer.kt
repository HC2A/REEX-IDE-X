package com.reex.idex.core

object DartSourceAnalyzer {
    fun analyze(source: String): List<Diagnostic> {
        val result = mutableListOf<Diagnostic>()
        if (source.isBlank()) return listOf(Diagnostic(Severity.WARNING, "The document is empty"))
        checkBalance(source, '{', '}', "curly braces", result)
        checkBalance(source, '(', ')', "parentheses", result)
        checkBalance(source, '[', ']', "square brackets", result)
        val lines = source.lines()
        if (source.contains("runApp(") && !Regex("\\bvoid\\s+main\\s*\\(").containsMatchIn(source)) {
            result += Diagnostic(Severity.ERROR, "runApp() requires a void main() entry point")
        }
        if (Regex("\\b(Widget|BuildContext|StatelessWidget|StatefulWidget)\\b").containsMatchIn(source) &&
            !source.contains("package:flutter/")) {
            result += Diagnostic(Severity.WARNING, "Flutter symbols detected without a Flutter package import")
        }
        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.count { it == '"' } % 2 != 0 && !trimmed.startsWith("//")) {
                result += Diagnostic(Severity.WARNING, "Possibly unterminated string", index + 1, 1)
            }
        }
        if (result.isEmpty()) result += Diagnostic(Severity.INFO, "No structural issues detected")
        return result
    }

    private fun checkBalance(text: String, open: Char, close: Char, label: String, out: MutableList<Diagnostic>) {
        var depth = 0
        text.forEachIndexed { index, ch ->
            when (ch) { open -> depth++; close -> depth-- }
            if (depth < 0) {
                out += Diagnostic(Severity.ERROR, "Unexpected closing $label", text.take(index).count { it == '\n' } + 1, 1)
                depth = 0
            }
        }
        if (depth != 0) out += Diagnostic(Severity.ERROR, "Unbalanced $label")
    }
}
