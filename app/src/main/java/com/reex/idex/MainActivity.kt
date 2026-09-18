package com.reex.idex

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
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
    private lateinit var console: TextView
    private var openedUri: Uri? = null
    private var completionPopup: ListPopupWindow? = null

    private val dartKeywords = arrayOf(
        "class", "extends", "implements", "with", "mixin", "abstract", "final", "const",
        "late", "var", "dynamic", "void", "int", "double", "num", "String", "bool",
        "List", "Map", "Set", "Future", "Stream", "async", "await", "return", "if",
        "else", "for", "while", "switch", "case", "break", "continue", "import", "part",
        "library", "Widget", "StatelessWidget", "StatefulWidget", "BuildContext", "build",
        "Scaffold", "MaterialApp", "Container", "Column", "Row", "Text", "Center", "main"
    )

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            openedUri = uri
            editor.setText(contentResolver.openInputStream(uri)?.bufferedReader()?.use(BufferedReader::readText) ?: "")
            appendLog("Opened Dart/Flutter file: ${uri.lastPathSegment ?: "document"}")
        } catch (e: Exception) { appendLog("Open failed: ${e.message}") }
    }

    private val saveFile = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri ?: return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(editor.text.toString().toByteArray(Charsets.UTF_8)) }
            openedUri = uri
            appendLog("Saved: ${uri.lastPathSegment ?: "source.dart"}")
        } catch (e: Exception) { appendLog("Save failed: ${e.message}") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(10, 12, 16)
        window.navigationBarColor = Color.rgb(10, 12, 16)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(14, 16, 21))
        }
        root.addView(label("REEX IDE X  •  DART / FLUTTER EDITOR", 18f, Color.WHITE, Color.rgb(27, 32, 42), 64))

        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(4, 4, 4, 4) }
        toolbar.addView(action("NEW DART") { loadTemplate() }, weightButton())
        toolbar.addView(action("OPEN") { openFile.launch(arrayOf("text/*", "application/dart", "application/octet-stream", "*/*")) }, weightButton())
        toolbar.addView(action("SAVE AS") { saveFile.launch("main.dart") }, weightButton())
        toolbar.addView(action("FORMAT") { formatSource() }, weightButton())
        root.addView(toolbar)

        root.addView(label("DART SOURCE  •  autocomplete enabled  •  local diagnostics", 12f, Color.LTGRAY, Color.rgb(20, 23, 30), 42))
        editor = EditText(this).apply {
            setTextColor(Color.rgb(235, 238, 245))
            setHintTextColor(Color.GRAY)
            textSize = 14f
            gravity = Gravity.TOP or Gravity.START
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(18, 18, 18, 18)
            setBackgroundColor(Color.rgb(18, 21, 28))
            hint = "Write Dart or Flutter code..."
            setText(dartTemplate())
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        root.addView(editor)

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(4, 4, 4, 4) }
        bottom.addView(action("ANALYZE") { analyzeDart() }, weightButton())
        bottom.addView(action("COMPLETE") { showCompletions() }, weightButton())
        bottom.addView(action("FLUTTER TEMPLATE") { loadFlutterTemplate() }, weightButton())
        root.addView(bottom)

        console = label("DIAGNOSTICS\n> Dart editor initialized\n> Offline editing enabled\n> No root required", 12f, Color.rgb(150, 225, 175), Color.rgb(7, 10, 13), 150)
        root.addView(ScrollView(this).apply { addView(console) }, LinearLayout.LayoutParams(-1, 150))
        setContentView(root)

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if ((s?.length ?: 0) > 0 && s!!.last() == '.') showCompletions()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun weightButton(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, 48, 1f)

    private fun action(text: String, click: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 10f
        setOnClickListener { click() }
    }

    private fun label(text: String, size: Float, color: Int, background: Int, height: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        setBackgroundColor(background)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(12, 0, 12, 0)
        layoutParams = LinearLayout.LayoutParams(-1, height)
    }

    private fun dartTemplate() = """import 'package:flutter/material.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

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

    private fun loadTemplate() { editor.setText(dartTemplate()); appendLog("New Dart source created") }
    private fun loadFlutterTemplate() { editor.setText(dartTemplate()); appendLog("Flutter starter template inserted") }

    private fun formatSource() {
        val formatted = editor.text.toString().lines().joinToString("\n") { it.trimEnd() }.trimEnd() + "\n"
        editor.setText(formatted)
        editor.setSelection(editor.length())
        appendLog("Basic formatting applied")
    }

    private fun analyzeDart() {
        val source = editor.text.toString()
        val errors = mutableListOf<String>()
        if (source.isBlank()) errors += "Source is empty"
        if (source.count { it == '{' } != source.count { it == '}' }) errors += "Unbalanced curly braces"
        if (source.count { it == '(' } != source.count { it == ')' }) errors += "Unbalanced parentheses"
        if (source.contains("import 'package:flutter/")) appendLog("Flutter import detected")
        if (source.contains("void main")) appendLog("Dart entry point detected")
        if (errors.isEmpty()) appendLog("Analysis passed: no basic syntax issues detected")
        else errors.forEach { appendLog("ERROR: $it") }
    }

    private fun showCompletions() {
        val popup = completionPopup ?: ListPopupWindow(this).also { completionPopup = it }
        popup.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, dartKeywords))
        popup.anchorView = editor
        popup.width = 360
        popup.height = ViewGroup.LayoutParams.WRAP_CONTENT
        popup.setOnItemClickListener { _, _, position, _ ->
            val word = dartKeywords[position]
            val start = editor.selectionStart.coerceAtLeast(0)
            editor.text.insert(start, word)
            popup.dismiss()
        }
        popup.show()
    }

    private fun appendLog(message: String) { console.append("\n> $message") }
}
