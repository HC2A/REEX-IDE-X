package com.reex.idex

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var editor: EditText
    private lateinit var lineNumbers: TextView
    private lateinit var console: TextView
    private lateinit var preview: TextView
    private lateinit var status: TextView
    private var popup: ListPopupWindow? = null

    private val completions = arrayOf(
        "abstract", "async", "await", "bool", "break", "catch", "class", "const", "continue",
        "double", "else", "enum", "extends", "factory", "false", "final", "finally", "for", "Future",
        "if", "implements", "import", "in", "int", "late", "List", "Map", "mixin", "null", "num",
        "override", "required", "return", "Set", "static", "String", "super", "switch", "this",
        "throw", "true", "try", "typedef", "var", "void", "while", "with", "Widget", "BuildContext",
        "StatelessWidget", "StatefulWidget", "MaterialApp", "Scaffold", "AppBar", "Container", "Column",
        "Row", "Center", "Text", "Padding", "Expanded", "ListView", "FutureBuilder", "StreamBuilder"
    )

    private val snippets = arrayOf(
        "Flutter StatelessWidget", "Flutter StatefulWidget", "Dart main function", "Flutter Scaffold", "Dart class"
    )

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            editor.setText(contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "")
            log("Opened: ${uri.lastPathSegment ?: "source.dart"}")
        } catch (e: Exception) { log("Open error: ${e.message}") }
    }

    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(editor.text.toString().toByteArray(Charsets.UTF_8)) }
            log("Saved: ${uri.lastPathSegment ?: "main.dart"}")
        } catch (e: Exception) { log("Save error: ${e.message}") }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = Color.rgb(9, 12, 18)
        window.navigationBarColor = Color.rgb(9, 12, 18)
        buildInterface()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 15, 22))
        }
        root.addView(header("REEX IDE X  •  DART / FLUTTER EDITOR", 19f, 58))
        root.addView(header("FINAL EDITION 1.1  |  offline mobile workspace", 11f, 30))

        val top = horizontalBar()
        top.addView(button("NEW") { editor.setText(dartTemplate()); log("New Flutter document created") })
        top.addView(button("OPEN") { openDocument.launch(arrayOf("text/*", "application/octet-stream", "*/*")) })
        top.addView(button("SAVE") { createDocument.launch("main.dart") })
        top.addView(button("FORMAT") { formatCode() })
        top.addView(button("PREVIEW") { updatePreview() })
        root.addView(top)

        val tools = horizontalBar()
        tools.addView(button("ANALYZE") { analyze() })
        tools.addView(button("COMPLETE") { showCompletions() })
        tools.addView(button("SNIPPETS") { showSnippets() })
        tools.addView(button("SEARCH") { searchText() })
        tools.addView(button("CLEAR") { editor.setText(""); log("Editor cleared") })
        root.addView(tools)

        status = TextView(this).apply {
            text = "  DART MODE  •  UTF-8  •  ${editorState()}"
            textSize = 11f
            setTextColor(Color.rgb(150, 190, 230))
            setPadding(8, 5, 8, 5)
            setBackgroundColor(Color.rgb(21, 28, 40))
        }
        root.addView(status)

        val editorFrame = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(17, 21, 30))
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        lineNumbers = TextView(this).apply {
            setTextColor(Color.rgb(83, 101, 127))
            textSize = 12f
            gravity = Gravity.TOP or Gravity.END
            typeface = Typeface.MONOSPACE
            setPadding(7, 15, 9, 15)
            setBackgroundColor(Color.rgb(22, 27, 38))
            text = "1"
        }
        editor = EditText(this).apply {
            setTextColor(Color.rgb(235, 240, 249))
            setHintTextColor(Color.rgb(105, 119, 140))
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.TOP or Gravity.START
            setPadding(10, 15, 12, 15)
            setBackgroundColor(Color.TRANSPARENT)
            hint = "Write Dart or Flutter code..."
            setText(dartTemplate())
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
        }
        editorFrame.addView(lineNumbers, LinearLayout.LayoutParams(48, -1))
        editorFrame.addView(editor)
        root.addView(editorFrame)

        root.addView(header("LIVE WIDGET STRUCTURE", 11f, 30))
        preview = TextView(this).apply {
            setTextColor(Color.rgb(180, 220, 255))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(10, 6, 10, 6)
            setBackgroundColor(Color.rgb(7, 11, 17))
            text = "Press PREVIEW to inspect the Flutter widget tree."
        }
        root.addView(preview, LinearLayout.LayoutParams(-1, 66))

        console = TextView(this).apply {
            setTextColor(Color.rgb(145, 225, 175))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(10, 6, 10, 6)
            text = "DIAGNOSTICS\n> REEX editor 1.1 initialized\n> Offline editing enabled"
            setBackgroundColor(Color.rgb(6, 9, 13))
        }
        root.addView(ScrollView(this).apply { addView(console) }, LinearLayout.LayoutParams(-1, 82))
        setContentView(root)

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateLineNumbers()
                status.text = "  DART MODE  •  UTF-8  •  ${editorState()}"
                if (s?.lastOrNull() == '.') showCompletions()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateLineNumbers()
    }

    private fun editorState(): String = "${editor.text?.length ?: 0} chars"

    private fun horizontalBar() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(2, 2, 2, 2)
        setBackgroundColor(Color.rgb(24, 30, 42))
    }

    private fun button(title: String, action: () -> Unit) = Button(this).apply {
        text = title
        textSize = 9f
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(0, 43, 1f)
    }

    private fun header(text: String, size: Float, height: Int) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(11, 0, 11, 0)
        setBackgroundColor(Color.rgb(28, 35, 49))
        layoutParams = LinearLayout.LayoutParams(-1, height)
    }

    private fun updateLineNumbers() {
        if (!::editor.isInitialized) return
        val count = editor.text.toString().count { it == '\n' } + 1
        lineNumbers.text = (1..count).joinToString("\n") { it.toString() }
    }

    private fun formatCode() {
        val result = editor.text.toString().lines().joinToString("\n") { it.trimEnd() }.trimEnd() + "\n"
        editor.setText(result)
        editor.setSelection(editor.length())
        log("Basic whitespace formatting applied")
    }

    private fun analyze() {
        val source = editor.text.toString()
        val errors = mutableListOf<String>()
        if (source.isBlank()) errors += "Source is empty"
        if (source.count { it == '{' } != source.count { it == '}' }) errors += "Unbalanced curly braces"
        if (source.count { it == '(' } != source.count { it == ')' }) errors += "Unbalanced parentheses"
        if (source.count { it == '[' } != source.count { it == ']' }) errors += "Unbalanced brackets"
        if (source.contains("runApp") && !source.contains("void main")) errors += "runApp found without void main"
        if (!source.contains("package:flutter/") && source.contains("Widget")) errors += "Flutter import may be missing"
        if (errors.isEmpty()) log("Analysis passed: no basic structural errors") else errors.forEach { log("ERROR: $it") }
    }

    private fun searchText() {
        val input = EditText(this).apply { hint = "Text to find" }
        AlertDialog.Builder(this).setTitle("Search in document").setView(input)
            .setPositiveButton("FIND") { _, _ ->
                val query = input.text.toString()
                val index = editor.text.indexOf(query, editor.selectionStart.coerceAtLeast(0))
                    .let { if (it < 0) editor.text.indexOf(query) else it }
                if (query.isNotEmpty() && index >= 0) {
                    editor.requestFocus(); editor.setSelection(index, index + query.length); log("Found: $query")
                } else log("Not found: $query")
            }.setNegativeButton("CANCEL", null).show()
    }

    private fun showCompletions() {
        val list = popup ?: ListPopupWindow(this).also { popup = it }
        list.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, completions))
        list.anchorView = editor
        list.width = 340
        list.height = ViewGroup.LayoutParams.WRAP_CONTENT
        list.setOnItemClickListener { _, _, position, _ ->
            editor.text.insert(editor.selectionStart.coerceAtLeast(0), completions[position])
            list.dismiss()
        }
        list.show()
    }

    private fun showSnippets() {
        AlertDialog.Builder(this).setTitle("Insert Dart / Flutter snippet")
            .setItems(snippets) { _, which ->
                val value = when (which) {
                    0 -> "class Example extends StatelessWidget {\n  const Example({super.key});\n  @override\n  Widget build(BuildContext context) => const Text('Hello');\n}\n"
                    1 -> "class Example extends StatefulWidget {\n  const Example({super.key});\n  @override State<Example> createState() => _ExampleState();\n}\nclass _ExampleState extends State<Example> {\n  @override Widget build(BuildContext context) => const Text('Hello');\n}\n"
                    2 -> "void main() {\n  runApp(const MyApp());\n}\n"
                    3 -> "Scaffold(\n  appBar: AppBar(title: const Text('Title')),\n  body: const Center(child: Text('Hello')),\n)"
                    else -> "class Example {\n  Example();\n}\n"
                }
                editor.text.insert(editor.selectionStart.coerceAtLeast(0), value); log("Snippet inserted")
            }.show()
    }

    private fun updatePreview() {
        val source = editor.text.toString()
        val widgets = listOf("MaterialApp", "Scaffold", "AppBar", "Column", "Row", "Container", "Center", "Text", "ListView", "Padding", "Expanded")
            .filter { source.contains(it) }
        preview.text = if (widgets.isEmpty()) "No Flutter widgets detected" else "LIVE PREVIEW TREE\n" + widgets.joinToString("\n") { "└─ $it" }
        log("Widget structure preview refreshed")
    }

    private fun log(message: String) {
        if (::console.isInitialized) console.append("\n> $message")
    }

    private fun dartTemplate() = """import 'package:flutter/material.dart';

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
