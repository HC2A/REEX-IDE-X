package com.reex.idex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.subscribeAlways
import com.reex.idex.core.DartSourceAnalyzer

class MainActivity : ComponentActivity() {
    internal var editor: CodeEditor? = null
    private var currentFile = "main.dart"
    private var arabic by mutableStateOf(true)

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val source = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                editor?.setText(source)
                currentFile = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "main.dart" } ?: "main.dart"
            }
        }
    }

    private val saveFile = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { output ->
                    output.write((editor?.text?.toString().orEmpty()).toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ReexIdeScreen(
                    activity = this,
                    fileName = currentFile,
                    arabic = arabic,
                    onToggleLanguage = { arabic = !arabic },
                    onOpen = { openFile.launch(arrayOf("text/*", "application/octet-stream", "*/*")) },
                    onSave = { saveFile.launch(currentFile) }
                )
            }
        }
    }
}

private const val DEFAULT_DART = """import 'package:flutter/material.dart';

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
"""

@Composable
private fun ReexIdeScreen(
    activity: MainActivity,
    fileName: String,
    arabic: Boolean,
    onToggleLanguage: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit
) {
    var panel by remember { mutableStateOf("problems") }
    var diagnostics by remember { mutableStateOf(DartSourceAnalyzer.analyze(DEFAULT_DART)) }
    var code by remember { mutableStateOf(DEFAULT_DART) }
    var showPreview by remember { mutableStateOf(false) }
    var showSnippets by remember { mutableStateOf(false) }

    fun analyze() {
        code = activity.editor?.text?.toString().orEmpty()
        diagnostics = DartSourceAnalyzer.analyze(code)
        panel = "problems"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("REEX IDE X", fontSize = 20.sp)
                        Text(fileName + "  •  Dart / Flutter", fontSize = 11.sp)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        activity.editor?.setText(DEFAULT_DART)
                        code = DEFAULT_DART
                        diagnostics = DartSourceAnalyzer.analyze(code)
                    }) { Text("NEW") }
                    TextButton(onClick = onOpen) { Text("OPEN") }
                    TextButton(onClick = onSave) { Text("SAVE") }
                    TextButton(onClick = onToggleLanguage) { Text(if (arabic) "EN" else "ع") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = panel == "problems",
                    onClick = { analyze() },
                    icon = { Text("!") },
                    label = { Text(if (arabic) "المشاكل" else "Problems") }
                )
                NavigationBarItem(
                    selected = panel == "tree",
                    onClick = { panel = "tree" },
                    icon = { Text("⌘") },
                    label = { Text(if (arabic) "الشجرة" else "Widget Tree") }
                )
                NavigationBarItem(
                    selected = panel == "console",
                    onClick = { panel = "console" },
                    icon = { Text(">_") },
                    label = { Text(if (arabic) "السجل" else "Console") }
                )
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).background(Color(0xFF080C12))
        ) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(selected = false, onClick = { analyze() }, label = { Text("ANALYZE") })
                FilterChip(selected = false, onClick = { showPreview = true }, label = { Text("PREVIEW") })
                FilterChip(selected = false, onClick = { showSnippets = true }, label = { Text("SNIPPETS") })
                FilterChip(selected = false, onClick = {
                    val current = activity.editor?.text?.toString().orEmpty()
                    val lines = current.lines().joinToString("\n") { line ->
                        if (line.trim().startsWith("import ") && !line.trim().endsWith(";")) line + ";" else line
                    }
                    val fixed = if (
                        (lines.contains("Widget") || lines.contains("runApp(")) &&
                        !lines.contains("package:flutter/")
                    ) {
                        "import 'package:flutter/material.dart';\n\n" + lines
                    } else {
                        lines
                    }
                    activity.editor?.setText(fixed)
                    code = fixed
                    diagnostics = DartSourceAnalyzer.analyze(fixed)
                }, label = { Text("FIX SAFE") })
                FilterChip(selected = false, onClick = { panel = "console" }, label = { Text("OFFLINE") })
            }

            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { context ->
                    CodeEditor(context).apply {
                        activity.editor = this
                        setText(DEFAULT_DART)
                        subscribeAlways<ContentChangeEvent> {
                            code = text.toString()
                        }
                    }
                }
            )

            Surface(
                Modifier.fillMaxWidth().height(120.dp),
                color = Color(0xFF0A1018)
            ) {
                when (panel) {
                    "problems" -> LazyColumn(Modifier.padding(10.dp)) {
                        items(diagnostics) { diagnostic ->
                            Text(
                                diagnostic.severity.toString() + ": " + diagnostic.message + " (L" + diagnostic.line + ")",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                    "tree" -> LazyColumn(Modifier.padding(10.dp)) {
                        val names = listOf(
                            "MaterialApp", "Scaffold", "AppBar", "Column", "Row",
                            "Center", "Container", "Text", "Padding", "ListView",
                            "ElevatedButton", "TextField"
                        ).filter { code.contains(it + "(") }
                        items(names) { Text("└─ " + it, fontSize = 12.sp) }
                        if (names.isEmpty()) item { Text("No recognized widgets") }
                    }
                    else -> Column(Modifier.padding(10.dp)) {
                        Text("REEX IDE X • offline")
                        Text("Structural analysis and editor actions run locally.", fontSize = 12.sp)
                        Text("Preview is a simulator, not a full Flutter runtime.", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showPreview) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            title = { Text(if (arabic) "المعاينة المحلية" else "Offline Preview") },
            text = {
                Column {
                    Text(
                        Regex("""Text\(['"]([^'"]+)""").find(code)?.groupValues?.getOrNull(1)
                            ?: "Hello Flutter"
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (arabic) "هذه محاكاة محلية وليست تشغيل Flutter فعلياً."
                        else "Local simulator only; arbitrary Flutter execution needs a Flutter toolchain."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPreview = false }) {
                    Text(if (arabic) "إغلاق" else "Close")
                }
            }
        )
    }

    if (showSnippets) {
        val snippets = listOf(
            "main()" to "void main() {\n  runApp(const ReexApp());\n}\n",
            "Scaffold" to "Scaffold(\n  appBar: AppBar(title: const Text('Title')),\n  body: const Center(child: Text('Hello')),\n)",
            "ListView" to "ListView(\n  children: const [\n    Text('Item 1'),\n    Text('Item 2'),\n  ],\n)"
        )
        AlertDialog(
            onDismissRequest = { showSnippets = false },
            title = { Text(if (arabic) "قوالب Dart / Flutter" else "Dart / Flutter snippets") },
            text = {
                Column {
                    snippets.forEach { (name, snippet) ->
                        TextButton(
                            onClick = {
                                activity.editor?.insertText(snippet, snippet.length)
                                showSnippets = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(name) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSnippets = false }) {
                    Text(if (arabic) "إغلاق" else "Close")
                }
            }
        )
    }
}
