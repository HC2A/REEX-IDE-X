package com.reex.idex

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.*
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.reex.idex.core.DartSourceAnalyzer

class MainActivity : AppCompatActivity() {
    private lateinit var editor: EditText
    private lateinit var lines: TextView
    private lateinit var panel: TextView
    private lateinit var fileName: TextView
    private var currentFile = "main.dart"
    private var busy = false
    private var arabic = true

    private val keywords = setOf(
        "abstract","as","assert","async","await","break","case","catch","class","const","continue",
        "dynamic","else","enum","extends","false","final","finally","for","if","implements","import",
        "in","is","late","mixin","new","null","on","operator","return","static","super","this",
        "throw","true","try","typedef","var","void","while","with","yield","int","double","num","bool",
        "String","List","Map","Set","Iterable","Future","Stream","Object","Widget","BuildContext",
        "StatelessWidget","StatefulWidget","State","MaterialApp","Scaffold","AppBar","Container",
        "Column","Row","Center","Text","Padding","Expanded","ListView","GridView","SafeArea","Theme","Navigator"
    )

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            editor.setText(contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty())
            currentFile = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "main.dart" } ?: "main.dart"
            fileName.text = currentFile
            analyze()
        }.onFailure { show("Open failed: " + it.message) }
    }

    private val saveFile = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(editor.text.toString().toByteArray(Charsets.UTF_8)) }
            panel.text = "Saved: " + (uri.lastPathSegment ?: currentFile)
        }.onFailure { show("Save failed: " + it.message) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9,13,20)
        window.navigationBarColor = Color.rgb(9,13,20)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10,14,21))
            layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12,0,8,0)
            setBackgroundColor(Color.rgb(19,27,39))
        }
        header.addView(TextView(this).apply {
            text = "REEX IDE X"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0,56,1f))
        header.addView(button("NEW",52) { newFile() })
        header.addView(button("OPEN",58) { openFile.launch(arrayOf("*/*")) })
        header.addView(button("SAVE",58) { saveFile.launch(currentFile) })
        header.addView(button("RUN",52) { runCheck() })
        header.addView(button("ع/EN",58) { arabic = !arabic; panel.text = if (arabic) "العربية مفعّلة" else "English enabled" })
        root.addView(header)

        val projectScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.rgb(25,34,49))
        }
        val project = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8,0,8,0)
        }
        fileName = TextView(this).apply {
            text = currentFile
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(230,238,248))
            gravity = Gravity.CENTER_VERTICAL
        }
        project.addView(fileName, LinearLayout.LayoutParams(150,44))
        project.addView(button("ANALYZE",78) { analyze() })
        project.addView(button("PREVIEW",78) { preview() })
        project.addView(button("FIX",54) { autoFix() })
        project.addView(button("SNIPPET",78) { snippets() })
        projectScroll.addView(project)
        root.addView(projectScroll, LinearLayout.LayoutParams(-1,44))

        val editorFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(12,17,26))
            layoutParams = LinearLayout.LayoutParams(-1,0,1f)
            layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR
        }
        editor = CodeEditor(this).apply {
            layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR
            textDirection = android.view.View.TEXT_DIRECTION_LTR
            typeface = Typeface.MONOSPACE
            textSize = 14f
            gravity = Gravity.TOP or Gravity.START
            setTextColor(Color.rgb(226,234,244))
            setHintTextColor(Color.rgb(90,108,132))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(58,14,14,22)
            hint = "Dart / Flutter code..."
            setSingleLine(false)
            setHorizontallyScrolling(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(template())
        }
        editorFrame.addView(editor, FrameLayout.LayoutParams(-1,-1))
        root.addView(editorFrame)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(23,31,44))
        }
        tabs.addView(button("Problems",0) { analyze() }, LinearLayout.LayoutParams(0,40,1f))
        tabs.addView(button("Widget Tree",0) { preview() }, LinearLayout.LayoutParams(0,40,1f))
        tabs.addView(button("Console",0) { panel.text = "REEX IDE X\nOffline editor ready." }, LinearLayout.LayoutParams(0,40,1f))
        root.addView(tabs)
        panel = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(Color.rgb(176,210,239))
            setBackgroundColor(Color.rgb(8,12,18))
            setPadding(12,8,12,12)
            text = "Dart/Flutter editor ready • offline"
        }
        root.addView(ScrollView(this).apply { addView(panel) }, LinearLayout.LayoutParams(-1,92))
        setContentView(root)

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                editor.invalidate()
                highlight()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateLines()
        highlight()
    }

    private fun button(text: String, width: Int, action: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 9f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(221,231,243))
        setPadding(3,0,3,0)
        setOnClickListener { action() }
        if (width > 0) layoutParams = LinearLayout.LayoutParams(width,42)
    }

    private fun updateLines() {
        if (!::editor.isInitialized) return
        editor.invalidate()
    }

    private fun highlight() {
        if (busy || !::editor.isInitialized) return
        busy = true
        val e = editor.text
        val cursor = editor.selectionStart.coerceIn(0,e.length)
        e.getSpans(0,e.length,ForegroundColorSpan::class.java).forEach { e.removeSpan(it) }
        val s = e.toString()
        val keywordColor = Color.rgb(190,150,255)
        val typeColor = Color.rgb(90,190,255)
        val stringColor = Color.rgb(220,190,110)
        val commentColor = Color.rgb(105,155,112)
        Regex("\\b[A-Za-z_][A-Za-z0-9_]*\\b").findAll(s).forEach {
            if (keywords.contains(it.value)) {
                val c = if (it.value.first().isUpperCase()) typeColor else keywordColor
                e.setSpan(ForegroundColorSpan(c),it.range.first,it.range.last+1,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'").findAll(s).forEach {
            e.setSpan(ForegroundColorSpan(stringColor),it.range.first,it.range.last+1,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        Regex("//.*").findAll(s).forEach {
            e.setSpan(ForegroundColorSpan(commentColor),it.range.first,it.range.last+1,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        editor.setSelection(cursor)
        busy = false
    }

    private fun analyze() {
        val r = DartSourceAnalyzer.analyze(editor.text.toString())
        panel.text = r.joinToString("\n") {
            "[" + it.severity + "] line " + it.line + ": " + it.message
        }
    }

    private fun autoFix() {
        var source = editor.text.toString()
        source = source.lines().joinToString("\n") { line ->
            if (line.trim().startsWith("import ") && !line.trim().endsWith(";")) line + ";" else line
        }
        if ((source.contains("Widget") || source.contains("runApp(")) && !source.contains("package:flutter/")) {
            source = "import 'package:flutter/material.dart';\n\n$source"
        }
        editor.setText(source)
        analyze()
        panel.append("\n✓ Safe fixes applied")
    }

    private fun runCheck() {
        analyze()
        panel.append("\n\nRun mode: structural/offline check only.\nA real Flutter SDK/runtime is required for arbitrary Flutter execution.")
    }

    private fun preview() {
        val names = listOf("MaterialApp","Scaffold","AppBar","SafeArea","Column","Row","Container","Center","Text","Padding","Expanded","ListView","GridView","Stack","Card")
            .filter { editor.text.toString().contains(it) }
        panel.text = if (names.isEmpty()) "No recognized Flutter widgets." else "Widget tree\n" + names.joinToString("\n") { "└─ " + it }
    }

    private fun snippets() {
        val items = arrayOf("main()", "StatelessWidget", "StatefulWidget", "Scaffold", "Future", "Dart class")
        AlertDialog.Builder(this).setTitle(if (arabic) "قوالب Dart / Flutter" else "Dart / Flutter snippets")
            .setItems(items) { _, i ->
                val code = when(i) {
                    0 -> "void main() {\n  runApp(const ReexApp());\n}\n"
                    1 -> "class Example extends StatelessWidget {\n  const Example({super.key});\n  @override\n  Widget build(BuildContext context) => const Text('Hello');\n}\n"
                    2 -> "class Example extends StatefulWidget {\n  const Example({super.key});\n  @override State<Example> createState() => _ExampleState();\n}\nclass _ExampleState extends State<Example> {\n  @override Widget build(BuildContext context) => const Text('Hello');\n}\n"
                    3 -> "Scaffold(\n  appBar: AppBar(title: const Text('Title')),\n  body: const Center(child: Text('Hello')),\n)"
                    4 -> "Future<void> load() async {\n  await Future<void>.delayed(const Duration(seconds: 1));\n}\n"
                    else -> "class Example {\n  Example();\n}\n"
                }
                val p = editor.selectionStart.coerceIn(0,editor.length())
                editor.text.insert(p,code)
                editor.setSelection((p + code.length).coerceAtMost(editor.length()))
            }.show()
    }

    private fun newFile() {
        editor.setText(template())
        currentFile = "main.dart"
        fileName.text = currentFile
    }

    private fun show(message: String) {
        AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK",null).show()
    }

    private fun template() = """import 'package:flutter/material.dart';

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
}
