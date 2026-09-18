package com.reex.idex.core

import android.content.Context
import java.io.File

/** Secure app-private offline workspace storage. */
class WorkspaceStore(private val context: Context) {
    private val root: File
        get() = File(context.filesDir, "workspaces").also { it.mkdirs() }

    fun workspace(name: String): File {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "default" }
        return File(root, safe).also { it.mkdirs() }
    }

    fun saveText(workspace: String, relativePath: String, text: String): File {
        val base = workspace(workspace).canonicalFile
        val target = File(base, relativePath).canonicalFile
        require(target == base || target.path.startsWith(base.path + File.separator)) { "Invalid workspace path" }
        target.parentFile?.mkdirs()
        target.writeText(text, Charsets.UTF_8)
        return target
    }

    fun readText(workspace: String, relativePath: String): String? {
        val base = workspace(workspace).canonicalFile
        val target = File(base, relativePath).canonicalFile
        if (!(target == base || target.path.startsWith(base.path + File.separator)) || !target.isFile) return null
        return runCatching { target.readText(Charsets.UTF_8) }.getOrNull()
    }

    fun deleteWorkspace(name: String): Boolean = workspace(name).deleteRecursively()
}
