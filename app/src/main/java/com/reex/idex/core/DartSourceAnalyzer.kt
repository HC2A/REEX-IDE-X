package com.reex.idex.core

object DartSourceAnalyzer {
    fun analyze(source: String): List<Diagnostic> {
        val out = mutableListOf<Diagnostic>()

        if (source.isBlank()) {
            return listOf(Diagnostic(Severity.WARNING, "Document is empty", 1, 1))
        }

        pairs(source, '{', '}', "curly braces", out)
        pairs(source, '(', ')', "parentheses", out)
        pairs(source, '[', ']', "square brackets", out)

        source.lines().forEachIndexed { index, line ->
            val t = line.trim()
            if (t.count { it == '"' } % 2 != 0 && !t.startsWith("//")) {
                out += Diagnostic(Severity.WARNING, "Possibly unterminated string", index + 1, 1)
            }
            if (t.startsWith("import ") && !t.endsWith(";")) {
                out += Diagnostic(Severity.WARNING, "Import statement usually ends with ';'", index + 1, 1)
            }
        }

        val hasMain = source.lines().any { line ->
            line.trim().startsWith("void main(") || line.trim().startsWith("void main (")
        }
        if (source.contains("runApp(") && !hasMain) {
            out += Diagnostic(Severity.ERROR, "runApp() is present but void main() was not found")
        }

        val hasFlutterSymbol = listOf(
            "Widget", "BuildContext", "StatelessWidget", "StatefulWidget"
        ).any { source.contains(it) }

        if (hasFlutterSymbol && !source.contains("package:flutter/")) {
            out += Diagnostic(
                Severity.WARNING,
                "Flutter symbols detected without a Flutter package import"
            )
        }

        if (out.isEmpty()) {
            out += Diagnostic(Severity.INFO, "No structural issues detected")
        }

        return out.distinctBy { Triple(it.severity, it.message, it.line) }
    }

    private fun pairs(
        source: String,
        open: Char,
        close: Char,
        label: String,
        out: MutableList<Diagnostic>
    ) {
        var depth = 0

        source.forEachIndexed { index, c ->
            when (c) {
                open -> depth++
                close -> {
                    depth--
                    if (depth < 0) {
                        val line = source.take(index).count { it.code == 10 } + 1
                        out += Diagnostic(
                            Severity.ERROR,
                            "Unexpected closing $label",
                            line,
                            1
                        )
                        depth = 0
                    }
                }
            }
        }

        if (depth != 0) {
            val line = source.count { it.code == 10 } + 1
            out += Diagnostic(
                Severity.ERROR,
                "Unbalanced $label",
                line,
                1
            )
        }
    }
}
