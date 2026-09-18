package com.reex.idex

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/**
 * REEX IDE X - native mobile Dart/Flutter workspace.
 * The editor is deliberately dependency-light so it remains fast and buildable
 * on a wide range of Android devices, including ARM64 phones.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var editor: EditText
    private lateinit var lineNumbers: TextView
    private lateinit var diagnostics: TextView
    private lateinit var preview: TextView
    private lateinit var status: TextView
    private lateinit var fileLabel: TextView
    private var currentFileName = "main.dart"
    private var popup: ListPopupWindow? = null

    private val keywords = arrayOf(
        "abstract", "as", "assert", "async", "await", "break", "case", "catch", "class", "const",
        "continue", "covariant", "default", "deferred", "do", "dynamic", "else", "enum", "export",
        "extends", "extension", "external", "factory", "false", "final", "finally", "for", "Function",
        "if", "implements", "import", "in", "interface", "is", "late", "library", "mixin", "new",
        "null", "on", "operator", "part", "required", "rethrow", "return", "sealed", "set", "show",
        "static", "super", "switch", "sync", "this", "throw", "true", "try", "typedef", "var",
        "void", "while", "with", "yield", "int", "double", "num", "bool", "String", "List", "Map",
        "Set", "Future", "Stream", "Widget", "BuildContext", "StatelessWidget", "StatefulWidget",
        "State", "MaterialApp", "Scaffold", "AppBar", "Container", "Column", "Row", "Center", "Text",
        "Padding", "Expanded", "ListView", "GridView", "SafeArea", "Theme", "Navigator", "Provider"
    )

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            editor.setText(text)
            currentFileName = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "main.dart" } ?: "main.dart"
            fileLabel.text = currentFileName
            appendDiagnostic("Opened $currentFileName")
        }.onFailure { appendDiagnostic("Open failed: ${it.message}") }
    }

    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(editor.text.toString().toByteArray(Charsets.UTF_8)) }
            appendDiagnostic("Saved ${uri.lastPathSegment ?: currentFileName}")
        }.onFailure { appendDiagnostic("Save failed: ${it.message}") }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = Color.rgb(8, 11, 17)
        window.navigationBarColor = Color.rgb(8, 11, 17)
        buildInterface()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(11, 15, 23))
        }

        root.addView(textBar("REEX IDE X", 20f, Color.WHITE, 54))
        val subtitle = textBar("NATIVE DART / FLUTTER WORKSPACE  •  ARM64 READY", 10f, Color.rgb(145, 178, 215), 28)
        root.addView(subtitle)

        val commandBar = horizontalBar()
        addCommand(commandBar, "NEW") { editor.setText(dartTemplate()); currentFileName = "main.dart"; fileLabel.text = currentFileName; appendDiagnostic("New Dart/Flutter document") }
        addCommand(commandBar, "OPEN") { openDocument.launch(arrayOf("text/*", "application/dart", "application/octet-stream", "*/*")) }
        addCommand(commandBar, "SAVE") { createDocument.launch(currentFileName) }
        addCommand(commandBar, "RUN") { runProjectCheck() }
        addCommand(commandBar, "PREVIEW") { refreshPreview() }
        root.addView(commandBar)

        val toolBar = horizontalBar()
        addCommand(toolBar, "ANALYZE") { analyzeSource() }
        addCommand(toolBar, "FORMAT") { formatSource() }
        addCommand(toolBar, "COMPLETE") { showCompletions() }
        addCommand(toolBar, "SNIPPET") { showSnippets() }
        addCommand(toolBar, "FIND") { findInSource() }
        root.addView(toolBar)

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(22, 30, 43))
            gravity = Gravity.CENTER_VERTICAL
        }
        fileLabel = textBar(currentFileName, 11f, Color.rgb(225, 235, 248), 34)
        fileLabel.layoutParams = LinearLayout.LayoutParams(0, 34, 1f)
        info.addView(fileLabel)
        status = textBar("DART  •  UTF-8  •  0 chars", 10f, Color.rgb(143, 190, 231), 34)
        status.gravity = Gravity.CENTER
        info.addView(status, LinearLayout.LayoutParams(180, 34))
        root.addView(info)

        val editorFrame = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(14, 19, 29))
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        lineNumbers = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            gravity = Gravity.TOP or Gravity.END
            setTextColor(Color.rgb(76, 98, 128))
            setBackgroundColor(Color.rgb(19, 25, 37))
            setPadding(7, 15, 10, 15)
            text = "1"
        }
        editor = EditText(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 14f
            gravity = Gravity.TOP or Gravity.START
            setTextColor(Color.rgb(232, 239, 249))
            setHintTextColor(Color.rgb(94, 112, 137))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(10, 15, 12, 18)
            hint = "Write Dart or Flutter code here..."
            setSingleLine(false)
            setText(dartTemplate())
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
        }
        editorFrame.addView(lineNumbers, LinearLayout.LayoutParams(48, -1))
        editorFrame.addView(editor)
        root.addView(editorFrame)

        root.addView(textBar("WIDGET PREVIEW / STRUCTURE", 10f, Color.rgb(143, 190, 231), 28))
        preview = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(Color.rgb(173, 219, 255))
            setBackgroundColor(Color.rgb(7, 11, 17))
            setPadding(10, 7, 10, 7)
            text = "Press PREVIEW to inspect the detected Flutter widget tree."
        }
        root.addView(preview, LinearLayout.LayoutParams(-1, 76))

        diagnostics = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(Color.rgb(149, 226, 181))
            setBackgroundColor(Color.rgb(6, 10, 15))
            setPadding(10, 7, 10, 7)
            text = "DIAGNOSTICS\n> REEX native editor initialized\n> No external runtime dependency"
        }
        root.addView(ScrollView(this).apply { addView(diagnostics) }, LinearLayout.LayoutParams(-1, 82))
        setContentView(root)

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateLineNumbers()
                status.text = "DART  •  UTF-8  •  ${s?.length ?: 0} chars"
                if (s?.lastOrNull() == '.') showCompletions()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateLineNumbers()
    }

    private fun textBar(value: String, size: Float, color: Int, height: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(11, 0, 11, 0)
        setBackgroundColor(Color.rgb(27, 36, 51))
        layoutParams = LinearLayout.LayoutParams(-1, height)
    }

    private fun horizontalBar() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(2, 2, 2, 2)
        setBackgroundColor(Color.rgb(21, 28, 41))
    }

    private fun addCommand(parent: LinearLayout, title: String, action: () -> Unit) {
        parent.addView(Button(this).apply {
            text = title
            textSize = 9f
            setAllCaps(false)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(0, 42, 1f)
        })
    }

    private fun updateLineNumbers() {
        if (!::editor.isInitialized) return
        val count = editor.text.toString().count { it == '\n' } + 1
        lineNumbers.text = (1..count).joinToString("\n") { it.toString() }
    }

    private fun formatSource() {
        val formatted = editor.text.toString().lines().joinToString("\n") { it.trimEnd() }.trimEnd() + "\n"
        editor.setText(formatted)
        editor.setSelection(editor.length())
        appendDiagnostic("Formatter: trailing whitespace normalized")
    }

    private fun analyzeSource() {
        val source = editor.text.toString()
        val errors = mutableListOf<String>()
        if (source.isBlank()) errors += "Source is empty"
        if (!balanced(source, '{', '}')) errors += "Unbalanced curly braces"
        if (!balanced(source, '(', ')')) errors += "Unbalanced parentheses"
        if (!balanced(source, '[', ']')) errors += "Unbalanced square brackets"
        if (source.contains("runApp") && !source.contains("void main")) errors += "runApp requires a main entry point"
        if (source.contains("Widget") && !source.contains("package:flutter/")) errors += "Flutter widget detected but Flutter import is missing"
        if (errors.isEmpty()) appendDiagnostic("Analyzer: no basic structural errors detected")
        else errors.forEach { appendDiagnostic("ERROR: $it") }
    }

    private fun balanced(value: String, open: Char, close: Char) = value.count { it == open } == value.count { it == close }

    private fun runProjectCheck() {
        analyzeSource()
        appendDiagnostic("Run pipeline: source validation complete")
        appendDiagnostic("Native execution requires an installed Dart/Flutter toolchain")
    }

    private fun refreshPreview() {
        val source = editor.text.toString()
        val names = listOf("MaterialApp", "Scaffold", "AppBar", "SafeArea", "Column", "Row", "Container", "Center", "Text", "Padding", "Expanded", "ListView", "GridView")
            .filter(source::contains)
        preview.text = if (names.isEmpty()) "No known Flutter widgets detected" else "ROOT\n" + names.joinToString("\n") { "└─ $it" }
        appendDiagnostic("Preview tree refreshed (${names.size} widgets)")
    }

    private fun findInSource() {
        val input = EditText(this).apply { hint = "Search text" }
        AlertDialog.Builder(this).setTitle("Find in document").setView(input)
            .setPositiveButton("FIND") { _, _ ->
                val query = input.text.toString()
                val from = editor.selectionStart.coerceAtLeast(0)
                val index = editor.text.indexOf(query, from).let { if (it < 0) editor.text.indexOf(query) else it }
                if (query.isNotEmpty() && index >= 0) {
                    editor.requestFocus(); editor.setSelection(index, index + query.length); appendDiagnostic("Found: $query")
                } else appendDiagnostic("Not found: $query")
            }.setNegativeButton("CANCEL", null).show()
    }

    private fun showCompletions() {
        val list = popup ?: ListPopupWindow(this).also { popup = it }
        val query = editor.text.substring(0, editor.selectionStart.coerceAtLeast(0)).takeLastWhile { it.isLetterOrDigit() || it == '_' }.lowercase(Locale.ROOT)
        val filtered = keywords.filter { query.isBlank() || it.lowercase(Locale.ROOT).startsWith(query) }.take(35)
        list.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, filtered))
        list.anchorView = editor
        list.width = 360
        list.setOnItemClickListener { _, _, position, _ ->
            val word = filtered[position]
            val start = editor.selectionStart.coerceAtLeast(0)
            val prefix = editor.text.substring(0, start).takeLastWhile { it.isLetterOrDigit() || it == '_' }
            editor.text.replace(start - prefix.length, start, word)
            list.dismiss()
        }
        list.show()
    }

    private fun showSnippets() {
        val names = arrayOf("StatelessWidget", "StatefulWidget", "main()", "Scaffold", "Dart class")
        AlertDialog.Builder(this).setTitle("Insert snippet").setItems(names) { _, which ->
            val snippet = when (which) {
                0 -> "class Example extends StatelessWidget {\n  const Example({super.key});\n  @override\n  Widget build(BuildContext context) => const Text('Hello');\n}\n"
                1 -> "class Example extends StatefulWidget {\n  const Example({super.key});\n  @override State<Example> createState() => _ExampleState();\n}\nclass _ExampleState extends State<Example> {\n  @override Widget build(BuildContext context) => const Text('Hello');\n}\n"
                2 -> "void main() {\n  runApp(const ReexApp());\n}\n"
                3 -> "Scaffold(\n  appBar: AppBar(title: const Text('Title')),\n  body: const Center(child: Text('Hello')),\n)"
                else -> "class Example {\n  Example();\n}\n"
            }
            editor.text.insert(editor.selectionStart.coerceAtLeast(0), snippet)
            appendDiagnostic("Snippet inserted")
        }.show()
    }

    private fun appendDiagnostic(message: String) {
        if (::diagnostics.isInitialized) diagnostics.append("\n> $message")
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
