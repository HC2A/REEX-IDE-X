package com.reex.idex

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader

class MainActivity : AppCompatActivity() {
    private lateinit var editor: EditText
    private lateinit var lineNumbers: TextView
    private lateinit var console: TextView
    private lateinit var preview: TextView
    private var popup: ListPopupWindow? = null

    private val completions = arrayOf(
        "abstract", "async", "await", "bool", "break", "case", "catch", "class", "const", "continue",
        "double", "else", "enum", "extends", "factory", "false", "final", "finally", "for", "Future",
        "if", "implements", "import", "in", "int", "interface", "late", "library", "List", "Map",
        "mixin", "new", "null", "num", "on", "override", "part", "required", "return", "Set",
        "static", "String", "super", "switch", "this", "throw", "true", "try", "typedef", "var",
        "void", "while", "with", "Widget", "BuildContext", "StatelessWidget", "StatefulWidget",
        "MaterialApp", "Scaffold", "AppBar", "Container", "Column", "Row", "Center", "Text", "Padding"
    )

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            editor.setText(contentResolver.openInputStream(uri)?.bufferedReader()?.use(BufferedReader::readText) ?: "")
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
        window.statusBarColor = Color.rgb(10, 12, 18)
        window.navigationBarColor = Color.rgb(10, 12, 18)
        buildInterface()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(13, 16, 22))
        }
        root.addView(header("REEX IDE X  /  DART + FLUTTER", 20f, 64))

        val toolbar = horizontalBar()
        toolbar.addView(button("NEW") { editor.setText(dartTemplate()); log("New Flutter document") })
        toolbar.addView(button("OPEN") { openDocument.launch(arrayOf("text/*", "application/octet-stream", "*/*")) })
        toolbar.addView(button("SAVE") { createDocument.launch("main.dart") })
        toolbar.addView(button("FORMAT") { formatCode() })
        toolbar.addView(button("RUN VIEW") { updatePreview() })
        root.addView(toolbar)

        root.addView(header("EDITOR  •  Dart syntax assistance  •  offline", 12f, 36))
        val editorFrame = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(18, 21, 29))
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        lineNumbers = TextView(this).apply {
            setTextColor(Color.rgb(90, 103, 125))
            textSize = 13f
            gravity = Gravity.TOP or Gravity.END
            typeface = Typeface.MONOSPACE
            setPadding(8, 18, 10, 18)
            setBackgroundColor(Color.rgb(22, 26, 35))
            text = "1"
        }
        editor = EditText(this).apply {
            setTextColor(Color.rgb(235, 239, 247))
            setHintTextColor(Color.rgb(105, 115, 132))
            textSize = 14f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.TOP or Gravity.START
            setPadding(10, 18, 14, 18)
            setBackgroundColor(Color.TRANSPARENT)
            hint = "Write Dart / Flutter code here..."
            setText(dartTemplate())
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
        }
        editorFrame.addView(lineNumbers, LinearLayout.LayoutParams(48, -1))
        editorFrame.addView(editor)
        root.addView(editorFrame)

        val actions = horizontalBar()
        actions.addView(button("ANALYZE") { analyze() })
        actions.addView(button("COMPLETE") { showCompletions() })
        actions.addView(button("CLEAR LOG") { console.text = "DIAGNOSTICS" })
        root.addView(actions)

        root.addView(header("LIVE STRUCTURE PREVIEW", 12f, 34))
        preview = TextView(this).apply {
            setTextColor(Color.rgb(180, 220, 255))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.rgb(8, 12, 18))
            text = "Preview is ready. Press RUN VIEW."
        }
        root.addView(preview, LinearLayout.LayoutParams(-1, 74))

        console = TextView(this).apply {
            setTextColor(Color.rgb(150, 225, 175))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(12, 8, 12, 8)
            text = "DIAGNOSTICS\n> Final editor initialized\n> Offline mode enabled"
            setBackgroundColor(Color.rgb(7, 10, 14))
        }
        root.addView(ScrollView(this).apply { addView(console) }, LinearLayout.LayoutParams(-1, 94))
        setContentView(root)

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateLineNumbers()
                if (s?.lastOrNull() == '.') showCompletions()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateLineNumbers()
    }

    private fun horizontalBar() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(3, 3, 3, 3)
        setBackgroundColor(Color.rgb(25, 30, 40))
    }

    private fun button(title: String, action: () -> Unit) = Button(this).apply {
        text = title
        textSize = 10f
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(0, 46, 1f)
    }

    private fun header(text: String, size: Float, height: Int) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(12, 0, 12, 0)
        setBackgroundColor(Color.rgb(28, 34, 46))
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
        log("Whitespace formatting applied")
    }

    private fun analyze() {
        val source = editor.text.toString()
        val errors = mutableListOf<String>()
        if (source.isBlank()) errors += "Source is empty"
        if (source.count { it == '{' } != source.count { it == '}' }) errors += "Unbalanced curly braces"
        if (source.count { it == '(' } != source.count { it == ')' }) errors += "Unbalanced parentheses"
        if (source.count { it == '[' } != source.count { it == ']' }) errors += "Unbalanced brackets"
        if (!source.contains("void main") && source.contains("runApp")) errors += "runApp found without void main"
        if (errors.isEmpty()) log("Analysis passed: no basic structural errors") else errors.forEach { log("ERROR: $it") }
    }

    private fun showCompletions() {
        val list = popup ?: ListPopupWindow(this).also { popup = it }
        list.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, completions))
        list.anchorView = editor
        list.width = 330
        list.height = ViewGroup.LayoutParams.WRAP_CONTENT
        list.setOnItemClickListener { _, _, position, _ ->
            val value = completions[position]
            val cursor = editor.selectionStart.coerceAtLeast(0)
            editor.text.insert(cursor, value)
            list.dismiss()
        }
        list.show()
    }

    private fun updatePreview() {
        val source = editor.text.toString()
        val widgets = listOf("MaterialApp", "Scaffold", "AppBar", "Column", "Row", "Container", "Center", "Text")
            .filter { source.contains(it) }
        preview.text = if (widgets.isEmpty()) "No Flutter widget structure detected" else
            "LIVE PREVIEW TREE\n" + widgets.joinToString("\n") { "└─ $it" }
        log("Live structure preview updated")
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
