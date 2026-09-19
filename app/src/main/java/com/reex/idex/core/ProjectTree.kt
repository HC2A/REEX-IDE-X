package com.reex.idex.core

data class ProjectNode(val name: String, val depth: Int, val isFolder: Boolean)

object ProjectTree {
    fun fromDart(source: String): List<ProjectNode> {
        val widgets = Regex("""\b(MaterialApp|Scaffold|AppBar|Column|Row|Center|Container|Text|Padding|ListView|ElevatedButton|TextField|FutureBuilder)\s*\(""")
            .findAll(source).map { it.groupValues[1] }.distinct().toList()
        val result = mutableListOf(
            ProjectNode("android", 0, true),
            ProjectNode("lib", 0, true),
            ProjectNode("main.dart", 1, false),
            ProjectNode("widgets", 1, true)
        )
        widgets.forEach { result += ProjectNode(it + ".dart", 2, false) }
        result += ProjectNode("test", 0, true)
        result += ProjectNode("pubspec.yaml", 0, false)
        result += ProjectNode("README.md", 0, false)
        return result
    }
}
