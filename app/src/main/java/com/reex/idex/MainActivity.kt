package com.reex.idex

import android.os.Bundle
import android.os.Build
import android.net.Uri
import kotlinx.coroutines.launch
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
import com.reex.idex.core.LanguageRegistry
import com.reex.idex.core.ProjectTree
import com.reex.idex.core.TextMateEditorSupport
import com.reex.idex.core.OfflineSessionStore
import com.reex.idex.core.WorkspaceStore
import com.reex.idex.core.FlutterRuntimeLauncher
import com.reex.idex.core.OfflineToolchainManager
import com.reex.idex.core.FlutterBuildService
import com.reex.idex.core.BuildResult
import com.reex.idex.core.LocalCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.OutlinedTextField

class MainActivity : ComponentActivity() {
    internal var editor: CodeEditor? = null
    private var currentFile by mutableStateOf("lib/main.dart")
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
                    onSave = { saveFile.launch(currentFile) },
                    onRunFlutter = { FlutterRuntimeLauncher.launch(this, projectRootPath(this), currentFile) },
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

private fun projectRootPath(activity: MainActivity): String = WorkspaceStore(activity).ensureDefaultProject().absolutePath

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
    onRunFlutter: () -> Unit,
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
    var showToolchain by remember { mutableStateOf(false) }
    var showBuild by remember { mutableStateOf(false) }
    var showTerminal by remember { mutableStateOf(false) }
    var terminalCommand by remember { mutableStateOf("flutter --version") }
    var terminalOutput by remember { mutableStateOf("") }
    var terminalRunning by remember { mutableStateOf(false) }
    var building by remember { mutableStateOf(false) }
    var buildResult by remember { mutableStateOf<BuildResult?>(null) }
    val scope = rememberCoroutineScope()
    val toolchain = remember { OfflineToolchainManager(activity) }
    var toolchainStatus by remember { mutableStateOf(toolchain.status()) }

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
                    TextButton(onClick = onRunFlutter) { Text("RUN FLUTTER") }
                    TextButton(onClick = { showBuild = true }) { Text("BUILD") }
                    TextButton(onClick = { showProject = true }) { Text("EXPLORER") }
                    TextButton(onClick = { showCompletion = true }) { Text("AI") }
                    TextButton(onClick = { toolchainStatus = toolchain.status(); showToolchain = true }) { Text("SDK") }
                    TextButton(onClick = { showTerminal = true }) { Text("TERMINAL") }
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
                FilterChip(selected = false, onClick = { showPreview = true }, label = { Text("SIMULATOR") })
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
                FilterChip(selected = false, onClick = { panel = "console" }, label = { Text("OFFLINE") })
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
                        Text("REEX IDE X • OFFLINE EDITOR")
                        Text("Structural analysis and editor actions run locally.", fontSize = 12.sp)
                        Text("Editor runtime: local • no cloud build", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showPreview) {
        Dialog(
            onDismissRequest = { showPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(Modifier.fillMaxSize(), color = Color(0xFF050914)) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("REEX AIDE • LOCAL SIMULATOR", fontWeight = FontWeight.Bold)
                            Text(
                                if (arabic) "محاكاة واجهة محلية"
                                else "Compose-only local simulator",
                                fontSize = 11.sp
                            )
                        }
                        TextButton(onClick = { showPreview = false }) {
                            Text(if (arabic) "إغلاق" else "CLOSE")
                        }
                    }
                    Box(
                        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            Modifier.fillMaxWidth().fillMaxHeight(0.88f),
                            shape = MaterialTheme.shapes.large,
                            color = Color(0xFFF8FAFC)
                        ) {
                            Column(
                                Modifier.fillMaxSize().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "REEX AIDE",
                                    fontSize = 28.sp,
                                    color = Color(0xFF0F172A),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    Regex("""Text\(['"]([^'"]+)""").find(code)?.groupValues?.getOrNull(1)
                                        ?: "Hello Flutter",
                                    fontSize = 22.sp,
                                    color = Color(0xFF111827)
                                )
                                Spacer(Modifier.height(20.dp))
                                Text("LOCAL SIMULATOR • USE RUN FLUTTER FOR THE REAL ENGINE", color = Color(0xFF64748B), fontSize = 11.sp)
                            }
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
        val prefix = code.substringAfterLast("\n").trim().substringAfterLast(" ")
        val suggestions = CompletionEngine.suggest(prefix, code)
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





    if (showTerminal) {
        AlertDialog(
            onDismissRequest = { if (!terminalRunning) showTerminal = false },
            title = { Text(if (arabic) "طرفية المشروع" else "Project Terminal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = terminalCommand,
                        onValueChange = { terminalCommand = it },
                        enabled = !terminalRunning,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Command") }
                    )
                    Surface(
                        Modifier.fillMaxWidth().height(260.dp),
                        color = Color(0xFF05070A)
                    ) {
                        Text(
                            terminalOutput.ifBlank { "Ready." },
                            fontSize = 9.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        enabled = !terminalRunning && terminalCommand.isNotBlank(),
                        onClick = {
                            terminalRunning = true
                            terminalOutput = "$ " + terminalCommand + "\n"
                            scope.launch(Dispatchers.IO) {
                                val parts = terminalCommand.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                                val result = if (parts.isEmpty()) {
                                    BuildResult(false, null, null, "Empty command")
                                    null
                                } else {
                                    LocalCommandRunner().run(projectRoot, parts, 300)
                                }
                                withContext(Dispatchers.Main) {
                                    terminalOutput += result?.output.orEmpty()
                                    terminalRunning = false
                                }
                            }
                        }
                    ) { Text(if (terminalRunning) "RUNNING…" else "RUN") }
                    TextButton(enabled = !terminalRunning, onClick = { showTerminal = false }) {
                        Text(if (arabic) "إغلاق" else "Close")
                    }
                }
            }
        )
    }

    if (showBuild) {
        AlertDialog(
            onDismissRequest = { if (!building) showBuild = false },
            title = { Text(if (arabic) "بناء APK محلي" else "Local APK Build") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (building) "Building arm64-v8a Flutter release…" 
                        else "Builds the current workspace with the verified local Flutter toolchain."
                    )
                    buildResult?.let { result ->
                        Text(if (result.success) "BUILD SUCCESS" else "BUILD FAILED", fontWeight = FontWeight.Bold)
                        result.sha256?.let { Text("SHA-256: $it", fontSize = 10.sp) }
                        Text(result.log.takeLast(5000), fontSize = 9.sp)
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        enabled = !building,
                        onClick = {
                            building = true
                            buildResult = null
                            scope.launch(Dispatchers.IO) {
                                val result = FlutterBuildService(activity).build(projectRoot)
                                withContext(Dispatchers.Main) {
                                    buildResult = result
                                    building = false
                                }
                            }
                        }
                    ) { Text(if (building) "BUILDING…" else "BUILD") }
                    TextButton(enabled = !building, onClick = { showBuild = false }) {
                        Text(if (arabic) "إغلاق" else "Close")
                    }
                }
            }
        )
    }

    if (showToolchain) {
        val status = toolchainStatus
        AlertDialog(
            onDismissRequest = { showToolchain = false },
            title = { Text(if (arabic) "بيئة العمل المحلية" else "Offline Toolchain") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (status.active) "ACTIVE" else "NOT READY", fontWeight = FontWeight.Bold)
                    Text("Verification: " + status.verification, fontSize = 12.sp)
                    Text("Flutter: " + status.flutter.absolutePath, fontSize = 10.sp)
                    Text("Dart: " + status.dart.absolutePath, fontSize = 10.sp)
                    Text("Android SDK: " + status.androidSdk.absolutePath, fontSize = 10.sp)
                    Text("Pub cache: " + status.pubCache.absolutePath, fontSize = 10.sp)
                    Text(
                        if (arabic)
                            "لن يتم تفعيل Toolchain إلا بعد التحقق من SHA-256."
                        else
                            "The toolchain is activated only after SHA-256 verification.",
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showToolchain = false }) {
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
