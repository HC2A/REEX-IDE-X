package com.reex.idex.core

object DartSourceAnalyzer {
    private val flutterSymbols = setOf(
        "Widget","BuildContext","StatelessWidget","StatefulWidget","State",
        "MaterialApp","Scaffold","AppBar","Container","Column","Row","Center",
        "Text","Padding","Expanded","ListView","GridView","SafeArea","Theme",
        "Navigator","FutureBuilder","StreamBuilder","TextField","ElevatedButton"
    )

    fun analyze(source: String): List<Diagnostic> {
        if (source.isBlank()) {
            return listOf(Diagnostic(Severity.WARNING, "Document is empty", 1, 1))
        }

        val out = mutableListOf<Diagnostic>()
        val code = maskStringsAndComments(source)
        checkBrackets(code, out)

        source.lines().forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("import ") && !trimmed.endsWith(";")) {
                out += Diagnostic(Severity.WARNING, "Import statement should end with ';'", index + 1, 1)
            }
        }

        val hasMain = Regex("""\bvoid\s+main\s*\(""").containsMatchIn(code)
        if (Regex("""\brunApp\s*\(""").containsMatchIn(code) && !hasMain) {
            out += Diagnostic(Severity.ERROR, "runApp() is present but void main() was not found", 1, 1)
        }

        val usesFlutter = flutterSymbols.any { Regex("""\b${{Regex.escape(it)}\b""").containsMatchIn(code) }
        if (usesFlutter && !Regex("""package:flutter/""").containsMatchIn(source)) {
            out += Diagnostic(Severity.ERROR, "Flutter symbols are used without a package:flutter import", 1, 1)
        }

        if (source.contains("package:flutter/") && !source.contains("package:flutter/material.dart")) {
            out += Diagnostic(
                Severity.INFO,
                "Flutter package detected; specialized package imports may be preferable",
                1,
                1
            )
        }

        return out.distinctBy { Triple(it.severity, it.message, it.line) }
            .ifEmpty { listOf(Diagnostic(Severity.INFO, "No structural issues detected", 1, 1)) }
    }

    private fun maskStringsAndComments(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        var quote: Char? = null
        var triple = false
        var lineComment = false
        var blockCommentDepth = 0

        fun appendMasked(c: Char) {
            out.append(if (c == '\n') '\n' else ' ')
        }

        while (i < source.length) {
            val c = source[i]
            val next = if (i + 1 < source.length) source[i + 1] else '\u0000'

            if (lineComment) {
                appendMasked(c)
                if (c == '\n') lineComment = false
                i++
                continue
            }

            if (blockCommentDepth > 0) {
                if (c == '/' && next == '*') {
                    blockCommentDepth++
                    appendMasked(c); appendMasked(next); i += 2
                } else if (c == '*' && next == '/') {
                    blockCommentDepth--
                    appendMasked(c); appendMasked(next); i += 2
                } else {
                    appendMasked(c); i++
                }
                continue
            }

            if (quote != null) {
                if (triple && c == quote && i + 2 < source.length &&
                    source[i + 1] == quote && source[i + 2] == quote) {
                    appendMasked(c); appendMasked(source[i + 1]); appendMasked(source[i + 2])
                    i += 3; quote = null; triple = false
                } else if (!triple && c == '\\') {
                    appendMasked(c)
                    if (i + 1 < source.length) appendMasked(source[i + 1])
                    i += 2
                } else if (!triple && c == quote) {
                    appendMasked(c); i++; quote = null
                } else {
                    appendMasked(c); i++
                }
                continue
            }

            if (c == '/' && next == '/') {
                appendMasked(c); appendMasked(next); i += 2; lineComment = true; continue
            }

            if (c == '/' && next == '*') {
                appendMasked(c); appendMasked(next); i += 2; blockCommentDepth = 1; continue
            }

            if ((c == 'r' || c == 'R') && i + 1 < source.length &&
                (source[i + 1] == '\'' || source[i + 1] == '"')) {
                out.append(c); i++
                quote = source[i]
                triple = i + 2 < source.length && source[i + 1] == quote && source[i + 2] == quote
                appendMasked(quote); i++
                if (triple) {
                    appendMasked(source[i]); appendMasked(source[i + 1]); i += 2
                }
                continue
            }

            if (c == '\'' || c == '"') {
                quote = c
                triple = i + 2 < source.length && source[i + 1] == c && source[i + 2] == c
                appendMasked(c); i++
                if (triple) {
                    appendMasked(source[i]); appendMasked(source[i + 1]); i += 2
                }
                continue
            }

            out.append(c); i++
        }
        return out.toString()
    }

    private fun checkBrackets(source: String, out: MutableList<Diagnostic>) {
        val stack = ArrayDeque<Pair<Char, Int>>()
        val openToClose = mapOf('(' to ')', '[' to ']', '{' to '}')
        val closing = setOf(')', ']', '}')

        source.forEachIndexed { index, c ->
            if (openToClose.containsKey(c)) {
                stack.addLast(c to index)
            } else if (c in closing) {
                val expected = stack.removeLastOrNull()
                if (expected == null || openToClose[expected.first] != c) {
                    val line = source.take(index).count { it == '\n' } + 1
                    out += Diagnostic(Severity.ERROR, "Unexpected closing '$c'", line, 1)
                    if (expected != null) stack.clear()
                }
            }
        }

        while (stack.isNotEmpty()) {
            val (open, index) = stack.removeLast()
            val line = source.take(index).count { it == '\n' } + 1
            out += Diagnostic(Severity.ERROR, "Unclosed '$open'", line, 1)
        }
    }
}
