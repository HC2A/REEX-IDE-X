package com.reex.idex.core

data class CompletionItem(val label: String, val detail: String, val insertText: String)

object CompletionEngine {
    private val dart = listOf(
        CompletionItem("MaterialApp", "Flutter root widget", "MaterialApp()"),
        CompletionItem("Scaffold", "Material page structure", "Scaffold()"),
        CompletionItem("AppBar", "Top application bar", "AppBar()"),
        CompletionItem("Container", "Box layout widget", "Container()"),
        CompletionItem("Column", "Vertical layout", "Column()"),
        CompletionItem("Row", "Horizontal layout", "Row()"),
        CompletionItem("ListView", "Scrollable list", "ListView()"),
        CompletionItem("FutureBuilder", "Async UI builder", "FutureBuilder()"),
        CompletionItem("setState", "Update StatefulWidget state", "setState(() {})"),
        CompletionItem("const", "Compile-time constant", "const ")
    )
    fun suggest(prefix: String): List<CompletionItem> {
        val p = prefix.lowercase()
        return dart.filter { it.label.lowercase().startsWith(p) }.take(8).ifEmpty { dart.take(8) }
    }
}
