package com.reex.idex.core

data class LanguageInfo(val id: String, val label: String, val extensions: Set<String>)

object LanguageRegistry {
    private val languages = listOf(
        LanguageInfo("dart", "Dart", setOf("dart")),
        LanguageInfo("kotlin", "Kotlin", setOf("kt", "kts")),
        LanguageInfo("java", "Java", setOf("java")),
        LanguageInfo("python", "Python", setOf("py")),
        LanguageInfo("javascript", "JavaScript", setOf("js", "jsx")),
        LanguageInfo("typescript", "TypeScript", setOf("ts", "tsx")),
        LanguageInfo("html", "HTML", setOf("html", "htm")),
        LanguageInfo("css", "CSS", setOf("css")),
        LanguageInfo("json", "JSON", setOf("json")),
        LanguageInfo("xml", "XML", setOf("xml")),
        LanguageInfo("c", "C/C++", setOf("c", "h", "cpp", "hpp")),
        LanguageInfo("markdown", "Markdown", setOf("md"))
    )
    fun detect(fileName: String): LanguageInfo =
        languages.firstOrNull { fileName.substringAfterLast('.', "").lowercase() in it.extensions }
            ?: LanguageInfo("text", "Text", emptySet())
}
