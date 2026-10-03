package com.reex.idex.core

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceStoreTest {
    @Test
    fun pathTraversalIsRejected() {
        val project = createTempDirectory(prefix = "reex-workspace-").toFile()
        try {
            val store = WorkspaceStoreFake(project)
            var rejected = false
            try {
                store.save("../outside.txt", "x")
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertTrue(rejected)
        } finally {
            project.deleteRecursively()
        }
    }
}

private class WorkspaceStoreFake(private val root: File) {
    fun save(path: String, value: String) {
        val base = root.canonicalFile
        val target = File(base, path).canonicalFile
        require(target.path.startsWith(base.path + File.separator)) { "Invalid workspace path" }
        target.parentFile?.mkdirs()
        target.writeText(value)
        assertEquals(value, target.readText())
    }
}
