package com.reex.idex

import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import com.reex.idex.core.DartSourceAnalyzer
import org.eclipse.tm4e.core.registry.IThemeSource

class MainActivity : ComponentActivity() {
    private var editor: CodeEditor? = null
    private var currentFile = "main.dart"
    private var arabic = true

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            val source = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            editor?.setText(source)
            currentFile = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "main.dart" } ?: "main.dart"
        }
    }

    private val saveFile = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.use {
                it.write(editor?.text?.toString()?.toByteArray(Charsets.UTF_8) ?: ByteArray(0))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupTextMate()
        setContent {
            ReexTheme {
                ReexIdeScreen(
                    activity = this,
                    initialFileName = currentFile,
                    isArabic = arabic,
                    onLanguageToggle = { arabic = !arabic },
                    onOpen = { openFile.launch(arrayOf("text/*", "application/octet-stream", "*/*")) },
                    onSave = { saveFile.launch(currentFile) },
                    onEditorReady = { editor = it }
                )
            }
        }
    }

    private fun setupTextMate() {
        runCatching {
            FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(applicationContext.assets))
            val themes = ThemeRegistry.getInstance()
            themes.loadTheme(
                ThemeModel(
                    IThemeSource.fromInputStream(
                        FileProviderRegistry.getInstance().tryGetInputStream("textmate/darcula.json"),
                        "textmate/darcula.json",
                        null
                    ),
                    "darcula"
                ).apply { isDark = true }
            )
            themes.setTheme("darcula")
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        }
    }
}

private val ReexDark = darkColorScheme(
    background = Color(0xFF080C12),
    surface = Color(0xFF0E141D),
    surfaceContainer = Color(0xFF121A25),
    surfaceContainerHigh = Color(0xFF182232),
    primary = Color(0xFF8AB4F8),
    secondary = Color(0xFFB9C7DC),
    onBackground = Color(0xFFE6ECF4),
    onSurface = Color(0xFFE6ECF4)
)

@Composable
private fun ReexTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ReexDark, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReexIdeScreen(
    activity: MainActivity,
    initialFileName: String,
    isArabic: Boolean,
    onLanguageToggle: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onEditorReady: (CodeEditor) -> Unit
) {
    var panel by remember { mutableStateOf("problems") }
    var showPreview by remember { mutableStateOf(false) }
    var showSnippets by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf(DartSourceAnalyzer.analyze(defaultTemplate())) }
    var fileName by remember { mutableStateOf(initialFileName) }
    var codeSnapshot by remember { mutableStateOf(defaultTemplate()) }

    fun readCode(): String = activity.editor?.text?.toString() ?: codeSnapshot

    fun analyze() {
        codeSnapshot = readCode()
        diagnostics = DartSourceAnalyzer.analyze(codeSnapshot)
        panel = "problems"
    }

    fun insert(code: String) {
        activity.editor?.insertText(code, code.length)
        codeSnapshot = readCode()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("REEX IDE X", fontSize = 20.sp)
                        Text(
                            "${{fileName}  •  Dart / Flutter",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                actions = {
                    TextButton(onClick = {
                        activity.editor?.setText(defaultTemplate())
                        fileName = "main.dart"
                        codeSnapshot = defaultTemplate()
                    }) { Text("NEW") }
                    TextButton(onClick = onOpen) { Text("OPEN") }
                    TextButton(onClick = onSave) { Text("SAVE") }
                    TextButton(onClick = {
                        activity.editor?.let { it.text.undoManager.undo(it.text) }
                    }) { Text("UNDO") }
                    TextButton(onClick = {
                        activity.editor?.let { it.text.undoManager.redo(it.text) }
                    }) { Text("REDO") }
                    TextButton(onClick = onLanguageToggle) {
                        Text(if (isArabic) "EN" else "ع")
                    }
                }
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    NavigationBarItem(
                        selected = panel == "problems",
                        onClick = { analyze() },
                        icon = { Text("!") },
                        label = { Text(if (isArabic) "المشاكل" else "Problems") }
                    )
                    NavigationBarItem(
                        selected = panel == "tree",
                        onClick = { codeSnapshot = readCode(); panel = "tree" },
                        icon = { Text("⌘") },
                        label = { Text(if (isArabic) "الشجرة" else "Widget Tree") }
                    )
                    NavigationBarItem(
                        selected = panel == "console",
                        onClick = { panel = "console" },
                        icon = { Text(">_") },
                        label = { Text(if (isArabic) "السجل" else "Console") }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(selected = false, onClick = { analyze() }, label = { Text("ANALYZE") })
                FilterChip(selected = false, onClick = { showPreview = true }, label = { Text("PREVIEW") })
                FilterChip(selected = false, onClick = { showSnippets = true }, label = { Text("SNIPPETS") })
                FilterChip(selected = false, onClick = {
                    val fixed = readCode().lines().joinToString("
") { line ->
                        if (line.trim().startsWith("import ") && !line.trim().endsWith(";")) "$line;" else line
                    }
                    activity.editor?.setText(
                        if ((fixed.contains("Widget") || fixed.contains("runApp(")) && !fixed.contains("package:flutter/")) {
                            "import 'package:flutter/material.dart';

$fixed"
                        } else fixed
                    )
                    analyze()
                }, label = { Text("FIX SAFE") })
                FilterChip(selected = false, onClick = { panel = "console" }, label = { Text("OFFLINE") })
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = Color(0xFF242424)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        CodeEditor(context).apply {
                            editor = this
                            onEditorReady(this)
                            typefaceText = Typeface.MONOSPACE
                            props.stickyScroll = true
                            setLineSpacing(1.2f, 1.0f)
                            setText(defaultTemplate())
                            colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
                            setEditorLanguage(TextMateLanguage.create("source.dart", true))
                            subscribeAlways<ContentChangeEvent> {
                                codeSnapshot = text.toString()
                            }
                        }
                    },
                    update = { view -> editor = view }
                )
            }

            BottomPanel(
                mode = panel,
                isArabic = isArabic,
                diagnostics = diagnostics,
                code = codeSnapshot
            )
        }
    }

    if (showPreview) {
        Dialog(
            onDismissRequest = { showPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            OfflineFlutterSimulator(
                code = readCode(),
                isArabic = isArabic,
                onClose = { showPreview = false }
            )
        }
    }

    if (showSnippets) {
        SnippetDialog(
            isArabic = isArabic,
            onDismiss = { showSnippets = false },
            onInsert = { insert(it); showSnippets = false }
        )
    }
}

@Composable
private fun BottomPanel(
    mode: String,
    isArabic: Boolean,
    diagnostics: List<com.reex.idex.core.Diagnostic>,
    code: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(118.dp),
        color = Color(0xFF0A1018)
    ) {
        when (mode) {
            "problems" -> LazyColumn(Modifier.padding(12.dp)) {
                item { Text(if (isArabic) "Problems / الأخطاء" else "Problems", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) }
                items(diagnostics) {
                    Text(
                        "• ${{it.severity}  ${{if (it.line > 0) "L${{it.line}: " else ""}${{it.message}",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            "tree" -> LazyColumn(Modifier.padding(12.dp)) {
                item { Text("Widget Tree", color = MaterialTheme.colorScheme.primary) }
                val names = listOf(
                    "MaterialApp","Scaffold","AppBar","SafeArea","Column","Row",
                    "Container","Center","Text","Padding","Expanded","ListView",
                    "GridView","Stack","Card","ElevatedButton","TextField"
                ).filter { code.contains("${$it(") }
                items(names) { Text("└─ ${$it", fontSize = 12.sp) }
                if (names.isEmpty()) item { Text("└─ No recognized widgets") }
            }
            else -> Column(Modifier.padding(12.dp)) {
                Text("REEX IDE X • offline", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text("Editor, highlighting, completion, snippets and structural analysis are local.")
                Text("Flutter execution uses the simulator unless an embedded Flutter runtime is supplied.")
            }
        }
    }
}

@Composable
private fun OfflineFlutterSimulator(code: String, isArabic: Boolean, onClose: () -> Unit) {
    val title = Regex("""AppBars*([^)]*title:s*(?:consts*)?Text(['"]([^'"]+)""")
        .find(code)?.groupValues?.getOrNull(1) ?: "REEX IDE X"
    val text = Regex("""Text(['"]([^'"]+)""")
        .findAll(code).map { it.groupValues[1] }.firstOrNull() ?: "Hello Flutter"
    val hasButton = code.contains("ElevatedButton(") || code.contains("FilledButton(") || code.contains("TextButton(")

    Surface(Modifier.fillMaxSize(), color = Color(0xFF05080D)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("OFFLINE FLUTTER SIMULATOR", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    Text("Dart widget rendering • local", fontSize = 11.sp)
                }
                TextButton(onClick = onClose) { Text(if (isArabic) "إغلاق" else "Close") }
            }
            Surface(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp),
                color = Color(0xFFF8F9FB),
                tonalElevation = 8.dp
            ) {
                Column(Modifier.fillMaxSize()) {
                    Surface(color = Color(0xFF1F4B8F)) {
                        Text(title, Modifier.fillMaxWidth().padding(18.dp), color = Color.White, fontSize = 19.sp)
                    }
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text, color = Color(0xFF17202B), fontSize = 28.sp)
                        if (hasButton) {
                            Spacer(Modifier.height(22.dp))
                            Button(onClick = {}) { Text("Flutter Button") }
                        }
                    }
                }
            }
            Text(
                "Preview is parsed locally; arbitrary Flutter execution requires a Flutter engine/toolchain.",
                Modifier.padding(14.dp),
                fontSize = 11.sp,
                color = Color(0xFF9AA8BA)
            )
        }
    }
}

@Composable
private fun SnippetDialog(
    isArabic: Boolean,
    onDismiss: () -> Unit,
    onInsert: (String) -> Unit
) {
    val snippets = listOf(
        "main()" to "void main() {
  runApp(const ReexApp());
}
",
        "StatelessWidget" to "class Example extends StatelessWidget {
  const Example({super.key});
  @override
  Widget build(BuildContext context) => const Text('Hello');
}
",
        "StatefulWidget" to "class Example extends StatefulWidget {
  const Example({super.key});
  @override State<Example> createState() => _ExampleState();
}

class _ExampleState extends State<Example> {
  @override Widget build(BuildContext context) => const Text('Hello');
}
",
        "Scaffold" to "Scaffold(
  appBar: AppBar(title: const Text('Title')),
  body: const Center(child: Text('Hello')),
)",
        "Future" to "Future<void> load() async {
  await Future<void>.delayed(const Duration(seconds: 1));
}
",
        "ListView" to "ListView(
  children: const [
    Text('Item 1'),
    Text('Item 2'),
  ],
)"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isArabic) "قوالب Dart / Flutter" else "Dart / Flutter snippets") },
        text = {
            Column {
                snippets.forEach { (name, code) ->
                    TextButton(onClick = { onInsert(code) }, modifier = Modifier.fillMaxWidth()) {
                        Text(name, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (isArabic) "إغلاق" else "Close") } }
    )
}

private fun defaultTemplate() = """import 'package:flutter/material.dart';

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
