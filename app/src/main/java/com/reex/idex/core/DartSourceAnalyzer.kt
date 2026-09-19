package com.reex.idex.core

enum class Severity { ERROR, WARNING, INFO }

data class Diagnostic(
    val severity: Severity,
    val message: String,
    val line: Int,
    val column: Int
)

object DartSourceAnalyzer {
    private val flutterSymbols = setOf(
        "Widget", "BuildContext", "StatelessWidget", "StatefulWidget", "State",
        "MaterialApp", "Scaffold", "AppBar", "Container", "Column", "Row", "Center",
        "Text", "Padding", "Expanded", "ListView", "GridView", "SafeArea", "Theme",
        "Navigator", "FutureBuilder", "StreamBuilder", "TextField", "ElevatedButton"
    )

    fun analyze(source: String): List<Diagnostic> {
        if (source.isBlank()) {
            return listOf(Diagnostic(Severity.WARNING, "Document is empty", 1, 1))
        }

        val masked = maskStringsAndComments(source)
        val result = mutableListOf<Diagnostic>()
        checkBrackets(masked, result)

        source.lines().forEachIndexed { index, line ->
            val t = line.trim()
            if (t.startsWith("import ") && !t.endsWith(";")) {
                result += Diagnostic(Severity.WARNING, "Import statement should end with ';'", index + 1, 1)
            }
        }

        val hasMain = Regex("""\bvoid\s+main\s*\(""").containsMatchIn(masked)
        if (Regex("""\brunApp\s*\(""").containsMatchIn(masked) && !hasMain) {
            result += Diagnostic(Severity.ERROR, "runApp() is present but void main() was not found", 1, 1)
        }

        val usesFlutter = flutterSymbols.any {
            Regex("\\b" + Regex.escape(it) + "\\b").containsMatchIn(masked)
        }
        if (usesFlutter && !Regex("""package:flutter/""").containsMatchIn(source)) {
            result += Diagnostic(Severity.ERROR, "Flutter symbols are used without a package:flutter import", 1, 1)
        }

        if (source.contains("package:flutter/") && !source.contains("package:flutter/material.dart")) {
            result += Diagnostic(
                Severity.INFO,
                "Flutter package detected; specialized package imports may be preferable",
                1,
                1
            )
        }

        return result.distinctBy { Triple(it.severity, it.message, it.line) }
            .ifEmpty { listOf(Diagnostic(Severity.INFO, "No structural issues detected", 1, 1)) }
    }

    private fun maskStringsAndComments(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        var quote: Char? = null
        var triple = false
        var lineComment = false
        var blockDepth = 0

        fun mask(c: Char) {
            out.append(if (c == '\n') '\n' else ' ')
        }

        while (i < source.length) {
            val c = source[i]
            val n = if (i + 1 < source.length) source[i + 1] else '\u0000'

            if (lineComment) {
                mask(c)
                if (c == '\n') lineComment = false
                i++
                continue
            }

            if (blockDepth > 0) {
                if (c == '/' && n == '*') {
                    mask(c); mask(n); i += 2; blockDepth++
                } else if (c == '*' && n == '/') {
                    mask(c); mask(n); i += 2; blockDepth--
                } else {
                    mask(c); i++
                }
                continue
            }

            if (quote != null) {
                if (triple && c == quote && i + 2 < source.length &&
                    source[i + 1] == quote && source[i + 2] == quote) {
                    mask(c); mask(source[i + 1]); mask(source[i + 2])
                    i += 3; quote = null; triple = false
                } else if (!triple && c == '\\') {
                    mask(c)
                    if (i + 1 < source.length) mask(source[i + 1])
                    i += 2
                } else if (!triple && c == quote) {
                    mask(c); i++; quote = null
                } else {
                    mask(c); i++
                }
                continue
            }

            if (c == '/' && n == '/') {
                mask(c); mask(n); i += 2; lineComment = true
                continue
            }

            if (c == '/' && n == '*') {
                mask(c); mask(n); i += 2; blockDepth = 1
                continue
            }

            if (c == '\'' || c == '"') {
                quote = c
                triple = i + 2 < source.length && source[i + 1] == c && source[i + 2] == c
                mask(c); i++
                if (triple) {
                    mask(source[i]); mask(source[i + 1]); i += 2
                }
                continue
            }

            out.append(c)
            i++
        }

        return out.toString()
    }

    private fun checkBrackets(source: String, result: MutableList<Diagnostic>) {
        val stack = ArrayDeque<Pair<Char, Int>>()
        val pairs = mapOf('(' to ')', '[' to ']', '{' to '}')

        source.forEachIndexed { index, c ->
            if (c in pairs.keys) {
                stack.addLast(c to index)
            } else if (c in pairs.values) {
                val top = stack.removeLastOrNull()
                if (top == null || pairs[top.first] != c) {
                    val line = source.take(index).count { it == '\n' } + 1
                    result += Diagnostic(Severity.ERROR, "Unexpected closing '$c'", line, 1)
                    if (top != null) stack.clear()
                }
            }
        }

        while (stack.isNotEmpty()) {
            val (open, index) = stack.removeLast()
            val line = source.take(index).count { it == '\n' } + 1
            result += Diagnostic(Severity.ERROR, "Unclosed '$open'", line, 1)
        }
    }
}
