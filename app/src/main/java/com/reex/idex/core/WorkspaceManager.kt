package com.reex.idex.core

import android.content.Context
import android.net.Uri
import java.io.File

/** Persistent workspace layer for REEX IDE X.
 * Uses app-private storage and SAF tree URIs; no root is required.
 */
class WorkspaceManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("reex_workspace", Context.MODE_PRIVATE)

    val workspaceRoot: File
        get() = File(context.filesDir, "workspaces").also { it.mkdirs() }

    fun createWorkspace(name: String): File {
        val safe = name.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "workspace" }
        return File(workspaceRoot, safe).also { it.mkdirs() }
    }

    fun listWorkspaces(): List<File> = workspaceRoot.listFiles()
        ?.filter { it.isDirectory }
        ?.sortedBy { it.name.lowercase() }
        .orEmpty()

    fun setActiveTreeUri(uri: Uri?) {
        prefs.edit().putString("active_tree_uri", uri?.toString()).apply()
    }

    fun activeTreeUri(): Uri? = prefs.getString("active_tree_uri", null)?.let(Uri::parse)

    fun setActiveWorkspace(path: String?) {
        prefs.edit().putString("active_workspace", path).apply()
    }

    fun activeWorkspace(): File? = prefs.getString("active_workspace", null)
        ?.let(::File)
        ?.takeIf { it.exists() }

    fun readText(file: File): String = file.readText(Charsets.UTF_8)
    fun writeText(file: File, content: String) {
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
    }
}
