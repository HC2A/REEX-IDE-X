package com.reex.idex

import android.os.Bundle
import android.os.Build
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import com.reex.idex.core.FlutterRuntimeBridge
import com.reex.idex.core.TextMateEditorSupport
import com.reex.idex.core.OfflineSessionStore
import com.reex.idex.core.WorkspaceStore
import com.reex.idex.core.GitHubRuntimeBuilder
import com.reex.idex.core.GitHubProjectBuilder
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
                    onOpenWorkspaceFile = { file ->
                        runCatching {
                            editor?.setText(file.readText(Charsets.UTF_8))
                            currentFile = file.relativeTo(WorkspaceStore(this).ensureDefaultProject()).path.replace(File.separatorChar, "/")
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
    var showCloudBuild by remember { mutableStateOf(false) }
    var showGitHub by remember { mutableStateOf(false) }
    var githubToken by remember { mutableStateOf("") }
    var cloudMessage by remember { mutableStateOf("") }
    var cloudBusy by remember { mutableStateOf(false) }
    var cloudRepo by remember { mutableStateOf("HC2A/REEX-IDE-X") }
    var cloudArch by remember {
        mutableStateOf(
            when (Build.SUPPORTED_ABIS.firstOrNull()) {
                "armeabi-v7a" -> "armeabi-v7a"
                "x86_64" -> "x86_64"
                else -> "arm64-v8a"
            }
        )
    }

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
                    TextButton(onClick = { showProject = true }) { Text("TREE") }
                    TextButton(onClick = { showCompletion = true }) { Text("AI") }
                    TextButton(onClick = { showGitHub = true }) { Text("GITHUB") }
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
                FilterChip(
                    selected = false,
                    onClick = {
                        val store = com.reex.idex.core.GitHubCredentialStore(activity)
                        val token = store.token()
                        if (token.isNullOrBlank()) {
                            cloudMessage = if (arabic) "لتشغيل الكود الحقيقي: اربط GitHub أولاً." else "Connect GitHub first to compile and run real Flutter code."
                            showCloudBuild = true
                        } else {
                            cloudBusy = true
                            cloudMessage = if (arabic) "جاري ترجمة المشروع الحقيقي وتشغيله داخل Flutter Engine…" else "Compiling the real project and starting it inside Flutter Engine…"
                            CoroutineScope(Dispatchers.Main).launch {
                                runCatching {
                                    GitHubRuntimeBuilder(activity, token).compile(
                                        repository = cloudRepo.trim(),
                                        project = projectRoot,
                                        architecture = cloudArch,
                                        onProgress = { message -> cloudMessage = message }
                                    )
                                }.onSuccess { result ->
                                    cloudBusy = false
                                    FlutterRuntimeBridge.launchCompiled(activity, result.bundleDir, arabic)
                                }.onFailure { error ->
                                    cloudBusy = false
                                    cloudMessage = error.message ?: "Runtime compilation failed"
                                }
                            }
                        }
                    },
                    label = { Text("RUN FLUTTER • REAL") }
                )
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
                FilterChip(
                    selected = false,
                    onClick = { showCloudBuild = true },
                    label = { Text("CLOUD BUILD") }
                )
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
                            workspaceStore.saveText(projectRoot, activeRelativePath, code)
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
                        Text(if (FlutterRuntimeBridge.isAvailable()) "Flutter Engine runtime: packaged" else "Flutter Engine runtime: not packaged in this local source build", fontSize = 12.sp)
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
        val suggestions = CompletionEngine.suggest(prefix)
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



    if (showGitHub) {
        val store = com.reex.idex.core.GitHubCredentialStore(activity)
        AlertDialog(
            onDismissRequest = { showGitHub = false },
            title = { Text(if (arabic) "ربط GitHub" else "Connect GitHub") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (arabic)
                            "احفظ Fine-grained token محلياً في Android Keystore. REEX يستخدمه فقط لرفع المشروع وتشغيل GitHub Actions وتنزيل APK."
                        else
                            "The fine-grained token is stored locally using Android Keystore. REEX uses it only to upload the project, run Actions and download the APK.",
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = githubToken,
                        onValueChange = { githubToken = it },
                        label = { Text("GitHub token") },
                        singleLine = true
                    )
                    Text(
                        if (store.isConnected())
                            if (arabic) "الحساب متصل محلياً ✓" else "GitHub credential is stored locally ✓"
                        else
                            if (arabic) "غير متصل" else "Not connected",
                        fontSize = 12.sp
                    )
                    Text(
                        "Required: Contents write • Actions write • Workflows write",
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        store.clear()
                        githubToken = ""
                    }) { Text(if (arabic) "مسح" else "Clear") }
                    Button(onClick = {
                        runCatching { store.saveToken(githubToken.trim()) }
                        githubToken = ""
                        showGitHub = false
                    }, enabled = githubToken.isNotBlank()) {
                        Text(if (arabic) "حفظ" else "Save")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showGitHub = false }) {
                    Text(if (arabic) "إغلاق" else "Close")
                }
            }
        )
    }

    if (showCloudBuild) {
        AlertDialog(
            onDismissRequest = { if (!cloudBusy) showCloudBuild = false },
            title = { Text(if (arabic) "البناء السحابي • GitHub" else "Cloud Build • GitHub") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (arabic) "التحرير والتحليل والإكمال والمعاينة تعمل محلياً. الإنترنت يُستخدم هنا للبناء فقط."
                        else "Editing, analysis, completion and preview stay local. Internet is used here only for the build."
                    )
                    OutlinedTextField(
                        value = cloudRepo,
                        onValueChange = { cloudRepo = it },
                        enabled = !cloudBusy,
                        label = { Text("GitHub repository") },
                        singleLine = true
                    )
                    Text("Architecture: " + cloudArch, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("arm64-v8a", "armeabi-v7a", "x86_64").forEach { arch ->
                            FilterChip(
                                selected = cloudArch == arch,
                                onClick = { cloudArch = arch },
                                label = { Text(arch) },
                                enabled = !cloudBusy
                            )
                        }
                    }
                    if (cloudMessage.isNotBlank()) {
                        Text(cloudMessage, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !cloudBusy,
                    onClick = {
                        val store = com.reex.idex.core.GitHubCredentialStore(activity)
                        val token = store.token()
                        if (token.isNullOrBlank()) {
                            cloudMessage = if (arabic) {
                                "اربط GitHub أولاً من زر AI/الإعدادات بإدخال Fine-grained token بصلاحيات Contents:write و Actions:write و Workflows:write."
                            } else {
                                "Connect GitHub first with a fine-grained token: Contents:write, Actions:write and Workflows:write."
                            }
                        } else {
                            cloudBusy = true
                            cloudMessage = if (arabic) "جاري الرفع والبناء…" else "Uploading and building…"
                            CoroutineScope(Dispatchers.Main).launch {
                                runCatching {
                                    GitHubProjectBuilder(activity, token).apply {
                                        configure(cloudRepo.trim())
                                    }.build(
                                        project = projectRoot,
                                        architecture = cloudArch,
                                        onProgress = { message -> cloudMessage = message }
                                    )
                                }.onSuccess { result ->
                                    cloudBusy = false
                                    cloudMessage = if (arabic) "تم البناء ✓  • APK جاهز" else "Build succeeded ✓  • APK ready"
                                    val uri = FileProvider.getUriForFile(
                                        activity,
                                        "com.reex.idex.fileprovider",
                                        result.apk
                                    )
                                    activity.startActivity(
                                        Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "application/vnd.android.package-archive")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                    )
                                }.onFailure { error ->
                                    cloudBusy = false
                                    cloudMessage = error.message ?: "Cloud build failed"
                                }
                            }
                        }
                    }
                ) { Text(if (cloudBusy) "BUILDING…" else "BUILD") }
            },
            dismissButton = {
                TextButton(enabled = !cloudBusy, onClick = { showCloudBuild = false }) {
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
