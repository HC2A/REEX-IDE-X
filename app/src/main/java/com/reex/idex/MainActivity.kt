package com.reex.idex

import android.os.Bundle
import android.os.Build
import android.net.Uri
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.subscribeAlways
import com.reex.idex.core.DartSourceAnalyzer
import com.reex.idex.core.CompletionEngine
import com.reex.idex.core.CompletionItem
import com.reex.idex.core.DartLanguageServer
import com.reex.idex.core.TerminalService
import com.reex.idex.core.LanguageRegistry
import com.reex.idex.core.ProjectTree
import com.reex.idex.core.TextMateEditorSupport
import com.reex.idex.core.OfflineSessionStore
import com.reex.idex.core.WorkspaceStore
import java.io.File
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.OutlinedTextField
import android.widget.Toast
import com.reex.idex.core.FlutterPreviewService
import com.reex.idex.core.DartToolingService
import com.reex.idex.core.OfflineToolchainManager
import com.reex.idex.core.Severity

class MainActivity : ComponentActivity() {
    internal var editor: CodeEditor? = null
    private var currentFile by mutableStateOf("lib/main.dart")
    private var arabic by mutableStateOf(true)

    override fun onDestroy() {
        editor?.release()
        editor = null
        super.onDestroy()
    }

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
                    onSave = { saveFile.launch(currentFile) },
                    onOpenWorkspaceFile = { file ->
                        runCatching {
                            editor?.setText(file.readText(Charsets.UTF_8))
                            currentFile = file.relativeTo(WorkspaceStore(this).ensureDefaultProject()).path.replace(File.separatorChar, '/')
                        }
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReexIdeScreen(
    activity: MainActivity,
    fileName: String,
    arabic: Boolean,
    onToggleLanguage: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onOpenWorkspaceFile: (File) -> Unit
) {
    var panel by remember { mutableStateOf("problems") }
    var diagnostics by remember { mutableStateOf(DartSourceAnalyzer.analyze(DEFAULT_DART)) }
    val offlineSession = remember { OfflineSessionStore(activity) }
    val workspaceStore = remember { WorkspaceStore(activity) }
    val projectRoot = remember { workspaceStore.ensureDefaultProject() }
    var activeRelativePath by remember { mutableStateOf("lib/main.dart") }
    val activePathForEditor by rememberUpdatedState(activeRelativePath)
    var code by remember {
        mutableStateOf(
            workspaceStore.readText(projectRoot, activeRelativePath)
                ?: offlineSession.sourceOrNull()
                ?: DEFAULT_DART
        )
    }
    var showPreview by remember { mutableStateOf(false) }
    var showSnippets by remember { mutableStateOf(false) }
    var showProject by remember { mutableStateOf(false) }
    var showCompletion by remember { mutableStateOf(false) }

    var completionItems by remember { mutableStateOf<List<CompletionItem>>(emptyList()) }
    var terminalInput by remember { mutableStateOf("") }
    var terminalOutput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun requestCompletion() {
        val source = activity.editor?.text?.toString().orEmpty()
        workspaceStore.saveText(projectRoot, activeRelativePath, source)
        showCompletion = true
        completionItems = emptyList()
        scope.launch(Dispatchers.IO) {
            val file = File(projectRoot, activeRelativePath)
            val server = DartLanguageServer(OfflineToolchainManager(activity))
            val lastLine = source.lines().lastOrNull().orEmpty()
            val lsp = if (server.open(projectRoot, file, source)) {
                server.completion(file, source.lines().lastIndex.coerceAtLeast(0), lastLine.length)
            } else emptyList()
            server.stop()
            val items = if (lsp.isNotEmpty()) {
                lsp.map { CompletionItem(it.label, it.detail.ifBlank { "Dart LSP" }, it.insertText ?: it.label) }
            } else {
                CompletionEngine.suggest(lastLine.substringAfterLast(Regex("[^A-Za-z0-9_]")), source)
            }
            withContext(Dispatchers.Main) { completionItems = items }
        }
    }

    fun runTerminal() {
        val command = terminalInput.trim()
        if (command.isBlank()) return
        terminalInput = ""
        scope.launch(Dispatchers.IO) {
            val result = TerminalService().execute(projectRoot, command)
            withContext(Dispatchers.Main) {
                terminalOutput = "\$ $command\n${result.output}\n[exit ${result.exitCode}]\n" + terminalOutput
                panel = "console"
            }
        }
    }

    fun analyze() {
        code = activity.editor?.text?.toString().orEmpty()
        workspaceStore.saveText(projectRoot, activeRelativePath, code)
        val tooling = DartToolingService(OfflineToolchainManager(activity)).analyze(projectRoot)
        diagnostics = if (tooling.success) {
            tooling.diagnostics.ifEmpty { listOf(com.reex.idex.core.Diagnostic(Severity.INFO, "Dart analyzer: no diagnostics", 1, 1)) }
        } else {
            DartSourceAnalyzer.analyze(code)
        }
        panel = "problems"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("REEX AIDE v3", fontSize = 20.sp)
                        Text(fileName + "  •  " + LanguageRegistry.detect(fileName).label, fontSize = 11.sp)
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
                    TextButton(onClick = { showProject = true }) { Text("EXPLORER") }
                    TextButton(onClick = { requestCompletion() }) { Text("LSP") }
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
                FilterChip(selected = false, onClick = {
                    val file = File(projectRoot, activeRelativePath)
                    val result = DartToolingService(OfflineToolchainManager(activity)).format(projectRoot, file)
                    if (result.exitCode == 0 && file.isFile) {
                        val formatted = file.readText(Charsets.UTF_8)
                        activity.editor?.setText(formatted)
                        code = formatted
                    } else {
                        Toast.makeText(activity, result.output.take(2000), Toast.LENGTH_LONG).show()
                    }
                }, label = { Text("FORMAT") })
                FilterChip(selected = false, onClick = {
                    val result = FlutterPreviewService(activity).prepareAndLaunch(projectRoot, activeRelativePath)
                    if (!result.success) {
                        Toast.makeText(activity, result.log.take(3000), Toast.LENGTH_LONG).show()
                    }
                }, label = { Text("RUN FLUTTER") })
                FilterChip(selected = false, onClick = { showSnippets = true }, label = { Text("SNIPPETS") })
                FilterChip(selected = false, onClick = { showProject = true }, label = { Text("PROJECT TREE") })
                FilterChip(selected = false, onClick = { showCompletion = true }, label = { Text("SMART COMPLETE") })
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
                FilterChip(selected = false, onClick = { panel = "console" }, label = { Text("TERMINAL") })
            }

            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { context ->
                    CodeEditor(context).apply {
                        activity.editor = this
                        TextMateEditorSupport.configureDart(context, this)
                        setText(code)
                        subscribeAlways<ContentChangeEvent> {
                            code = text.toString()
                            offlineSession.save(activeRelativePath, code)
                            workspaceStore.saveText(projectRoot, activePathForEditor, code)
                        }
                    }
                }
            )

            Surface(
                Modifier.fillMaxWidth().height(if (panel == "console") 190.dp else 120.dp),
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
                        items(ProjectTree.fromWorkspace(projectRoot)) { node ->
                            Text(
                                ("  ".repeat(node.depth)) + (if (node.isFolder) "▸ " else "• ") + node.relativePath,
                                fontSize = 12.sp
                            )
                        }
                    }
                    else -> Column(Modifier.padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = terminalInput,
                                onValueChange = { terminalInput = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("shell command") }
                            )
                            Button(onClick = { runTerminal() }) { Text("RUN") }
                        }
                        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                            item { Text(terminalOutput.ifBlank { "Local terminal ready • workspace: ${projectRoot.name}" }, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }

    if (showProject) {
        AlertDialog(
            onDismissRequest = { showProject = false },
            title = { Text(if (arabic) "شجرة المشروع" else "Project Tree") },
            text = {
                LazyColumn {
                    item { Text("my_app/", fontWeight = FontWeight.Bold) }
                    items(ProjectTree.fromWorkspace(projectRoot)) { node ->
                        TextButton(
                            onClick = {
                                if (!node.isFolder) {
                                    val file = File(projectRoot, node.relativePath)
                                    if (file.isFile) {
                                        activeRelativePath = node.relativePath
                                        onOpenWorkspaceFile(file)
                                    }
                                }
                            },
                            enabled = !node.isFolder,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                ("  ".repeat(node.depth)) +
                                    (if (node.isFolder) "▸ " else "• ") + node.name,
                                fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProject = false }) {
                    Text(if (arabic) "إغلاق" else "Close")
                }
            }
        )
    }

    if (showCompletion) {
        val suggestions = completionItems
        AlertDialog(
            onDismissRequest = { showCompletion = false },
            title = { Text(if (arabic) "الإكمال الذكي" else "Smart Completion") },
            text = {
                Column {
                    suggestions.forEach { item ->
                        TextButton(
                            onClick = {
                                val base = activity.editor?.text?.toString().orEmpty()
                                val separator = if (base.isEmpty() || base.endsWith("\n")) "" else "\n"
                                activity.editor?.setText(base + separator + item.insertText)
                                showCompletion = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(item.label, fontWeight = FontWeight.Bold)
                                Text(item.detail, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCompletion = false }) {
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
