package com.reex.idex.core

import java.io.File

data class ProjectNode(
    val name: String,
    val depth: Int,
    val isFolder: Boolean,
    val relativePath: String = name
)

/** Project tree backed by the actual workspace filesystem. */
object ProjectTree {
    fun fromWorkspace(project: File): List<ProjectNode> {
        val base = project.canonicalFile
        if (!base.isDirectory) return emptyList()
        val result = mutableListOf<ProjectNode>()

        fun visit(directory: File, depth: Int) {
            val children = directory.listFiles()
                ?.filter { it.name !in setOf(".dart_tool", "build") }
                ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                .orEmpty()

            for (child in children) {
                val relative = child.relativeTo(base).path.replace(File.separatorChar, '/')
                result += ProjectNode(child.name, depth, child.isDirectory, relative)
                if (child.isDirectory) visit(child, depth + 1)
            }
        }

        visit(base, 0)
        return result
    }

    /** Kept for compatibility with older callers; this is no longer used as the real project tree. */
    fun fromDart(source: String): List<ProjectNode> = listOf(
        ProjectNode("lib", 0, true, "lib"),
        ProjectNode("main.dart", 1, false, "lib/main.dart")
    )
}
