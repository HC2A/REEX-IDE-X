package com.reex.idex.core

data class Diagnostic(
    val severity: Severity,
    val message: String,
    val line: Int = 1,
    val column: Int = 1
) {
    enum class Severity { INFO, WARNING, ERROR }
}

object DartSourceAnalyzer {
    fun analyze(source: String): List<Diagnostic> {
        val out = mutableListOf<Diagnostic>()
        if (source.isBlank()) return listOf(Diagnostic(Diagnostic.Severity.WARNING,"Document is empty"))
        pairs(source,'{','}',"curly braces",out)
        pairs(source,'(',')',"parentheses",out)
        pairs(source,'[',']',"square brackets",out)
        source.lines().forEachIndexed { index, line ->
            val t = line.trim()
            if (t.count { it == '"' } % 2 != 0 && !t.startsWith("//")) {
                out += Diagnostic(Diagnostic.Severity.WARNING,"Possibly unterminated string",index+1,1)
            }
            if (t.startsWith("import ") && !t.endsWith(";")) {
                out += Diagnostic(Diagnostic.Severity.WARNING,"Import statement usually ends with ';'",index+1,1)
            }
        }
        if (source.contains("runApp(") && !Regex("\\bvoid\\s+main\\s*\\(").containsMatchIn(source)) {
            out += Diagnostic(Diagnostic.Severity.ERROR,"runApp() is present but void main() was not found")
        }
        if (Regex("\\b(Widget|BuildContext|StatelessWidget|StatefulWidget)\\b").containsMatchIn(source)
            && !source.contains("package:flutter/")) {
            out += Diagnostic(Diagnostic.Severity.WARNING,"Flutter symbols detected without a Flutter package import")
        }
        if (out.isEmpty()) out += Diagnostic(Diagnostic.Severity.INFO,"No structural issues detected")
        return out.distinctBy { Triple(it.severity,it.message,it.line) }
    }

    private fun pairs(source:String, open:Char, close:Char, label:String, out:MutableList<Diagnostic>) {
        var depth=0
        source.forEachIndexed { i,c ->
            when(c) {
                open -> depth++
                close -> {
                    depth--
                    if(depth < 0) {
                        out += Diagnostic(Diagnostic.Severity.ERROR,"Unexpected closing $label",source.take(i).count{it=='\\n'}+1,1)
                        depth=0
                    }
                }
            }
        }
        if(depth != 0) out += Diagnostic(Diagnostic.Severity.ERROR,"Unbalanced $label",source.count{it=='\\n'}+1,1)
    }
}
