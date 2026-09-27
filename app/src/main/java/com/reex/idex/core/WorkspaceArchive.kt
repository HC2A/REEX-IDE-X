package com.reex.idex.core

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object WorkspaceArchive {
    fun export(project: File, destination: File): File {
        require(project.isDirectory)
        destination.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(destination)).use { zip ->
            project.walkTopDown()
                .filter { it.isFile && it.name !in setOf(".DS_Store") }
                .forEach { file ->
                    val relative = file.relativeTo(project).path.replace(File.separatorChar, '/')
                    zip.putNextEntry(ZipEntry(relative))
                    FileInputStream(file).use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        return destination
    }

    fun importZip(zipFile: File, destination: File) {
        destination.mkdirs()
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val safe = File(destination, entry.name).canonicalFile
                require(safe.path == destination.canonicalPath || safe.path.startsWith(destination.canonicalPath + File.separator)) {
                    "Unsafe archive entry"
                }
                if (entry.isDirectory) {
                    safe.mkdirs()
                } else {
                    safe.parentFile?.mkdirs()
                    safe.outputStream().use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
            }
        }
    }
}
