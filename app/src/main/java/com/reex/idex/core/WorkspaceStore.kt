package com.reex.idex.core

import android.content.Context
import java.io.File

class WorkspaceStore(private val context: Context) {
    private val root: File get() = File(context.filesDir, "workspaces").also { it.mkdirs() }

    fun workspace(name: String): File = File(root, name.replace(Regex("[^A-Za-z0-9._-]"), "_")).also { it.mkdirs() }

    fun saveText(workspace: String, relativePath: String, text: String): File {
        val base = workspace(workspace)
        val target = File(base, relativePath)
        require(target.canonicalPath.startsWith(base.canonicalPath)) { "Invalid workspace path" }
        target.parentFile?.mkdirs()
        target.writeText(text, Charsets.UTF_8)
        return target
    }

    fun readText(workspace: String, relativePath: String): String? {
        val base = workspace(workspace)
        val target = File(base, relativePath)
        if (!target.canonicalPath.startsWith(base.canonicalPath) || !target.isFile) return null
        return target.readText(Charsets.UTF_8)
    }
}
