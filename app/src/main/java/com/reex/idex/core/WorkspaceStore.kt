package com.reex.idex.core

import android.content.Context
import java.io.File

data class WorkspaceEntry(
    val relativePath: String,
    val file: File,
    val isDirectory: Boolean
)

/** Real, app-private project filesystem used by the IDE.
 * No synthetic widget tree is involved: every node maps to an actual file/directory.
 */
class WorkspaceStore(private val context: Context) {
    private val root: File
        get() = File(context.filesDir, "workspaces").also { it.mkdirs() }

    fun workspace(name: String): File {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "default" }
        return File(root, safe).also { it.mkdirs() }
    }

    fun ensureDefaultProject(): File {
        val project = workspace("MyFlutterApp")
        val main = File(project, "lib/main.dart")
        if (!main.exists()) {
            main.parentFile?.mkdirs()
            main.writeText(DEFAULT_MAIN_DART, Charsets.UTF_8)
        }
        val pubspec = File(project, "pubspec.yaml")
        if (!pubspec.exists()) {
            pubspec.writeText(DEFAULT_PUBSPEC, Charsets.UTF_8)
        }
        val analysis = File(project, "analysis_options.yaml")
        if (!analysis.exists()) {
            analysis.writeText(DEFAULT_ANALYSIS_OPTIONS, Charsets.UTF_8)
        }
        File(project, ".gitignore").takeUnless { it.exists() }?.writeText(
            "build/\n.dart_tool/\n.idea/\n",
            Charsets.UTF_8
        )
        File(project, "test/widget_test.dart").takeUnless { it.exists() }?.apply {
            parentFile?.mkdirs()
            writeText(
                "import 'package:flutter_test/flutter_test.dart';\n\nvoid main() {\n  testWidgets('REEX smoke test', (tester) async {\n    expect(1 + 1, 2);\n  });\n}\n",
                Charsets.UTF_8
            )
        }
        return project
    }

    fun listEntries(project: File): List<WorkspaceEntry> {
        val base = project.canonicalFile
        if (!base.isDirectory) return emptyList()
        return base.walkTopDown()
            .filter { it != base && it.name !in setOf(".dart_tool", "build") }
            .map { file ->
                WorkspaceEntry(
                    relativePath = file.relativeTo(base).path.replace(File.separatorChar, '/'),
                    file = file,
                    isDirectory = file.isDirectory
                )
            }
            .sortedWith(compareBy<WorkspaceEntry> { !it.isDirectory }.thenBy { it.relativePath.lowercase() })
            .toList()
    }

    fun saveText(project: File, relativePath: String, text: String): File {
        val target = safeTarget(project, relativePath)
        target.parentFile?.mkdirs()
        target.writeText(text, Charsets.UTF_8)
        return target
    }

    fun readText(project: File, relativePath: String): String? {
        val target = safeTarget(project, relativePath)
        return if (target.isFile) runCatching { target.readText(Charsets.UTF_8) }.getOrNull() else null
    }

    fun createFile(project: File, relativePath: String): File {
        val target = safeTarget(project, relativePath)
        require(!target.exists()) { "File already exists" }
        target.parentFile?.mkdirs()
        target.createNewFile()
        return target
    }

    fun createDirectory(project: File, relativePath: String): File {
        val target = safeTarget(project, relativePath)
        require(!target.exists()) { "Directory already exists" }
        require(target.mkdirs()) { "Could not create directory" }
        return target
    }

    fun delete(project: File, relativePath: String): Boolean {
        val target = safeTarget(project, relativePath)
        require(target != project.canonicalFile) { "Cannot delete workspace root" }
        return target.deleteRecursively()
    }

    fun rename(project: File, from: String, to: String): File {
        val source = safeTarget(project, from)
        val target = safeTarget(project, to)
        require(source.exists()) { "Source does not exist" }
        require(!target.exists()) { "Target already exists" }
        target.parentFile?.mkdirs()
        require(source.renameTo(target)) { "Rename failed" }
        return target
    }

    fun deleteWorkspace(name: String): Boolean = workspace(name).deleteRecursively()

    private fun safeTarget(project: File, relativePath: String): File {
        val base = project.canonicalFile
        val normalized = relativePath.replace('\\', '/').trimStart('/')
        require(normalized.isNotBlank() && normalized != ".") { "Invalid workspace path" }
        val target = File(base, normalized).canonicalFile
        require(target.path == base.path || target.path.startsWith(base.path + File.separator)) {
            "Invalid workspace path"
        }
        return target
    }

    companion object {
        private val DEFAULT_MAIN_DART = """
import 'package:flutter/material.dart';

void main() {
  runApp(const ReexApp());
}

class ReexApp extends StatelessWidget {
  const ReexApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      home: Scaffold(
        appBar: AppBar(title: const Text('REEX IDE X')),
        body: const Center(child: Text('Hello Flutter')),
      ),
    );
  }
}
""".trimIndent() + "\n"

        private val DEFAULT_PUBSPEC = """
name: reex_project
description: A Flutter project created by REEX IDE X.
publish_to: 'none'
version: 1.0.0+1

environment:
  sdk: ">=3.3.0 <4.0.0"

dependencies:
  flutter:
    sdk: flutter

dev_dependencies:
  flutter_test:
    sdk: flutter
  flutter_lints: ^6.0.0

flutter:
  uses-material-design: true
""".trimIndent() + "\n"

        private val DEFAULT_ANALYSIS_OPTIONS = """
include: package:flutter_lints/flutter.yaml
""".trimIndent() + "\n"
    }
}
