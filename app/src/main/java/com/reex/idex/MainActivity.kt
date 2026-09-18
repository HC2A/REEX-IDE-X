package com.reex.idex

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {
    private lateinit var console: TextView
    private lateinit var editor: EditText
    private var openedUri: Uri? = null

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            openedUri = uri
            editor.setText(contentResolver.openInputStream(uri)?.bufferedReader()?.use(BufferedReader::readText) ?: "")
            appendLog("Opened: ${uri.lastPathSegment ?: "document"}")
        } catch (e: Exception) { appendLog("Open failed: ${e.message}") }
    }

    private val createFile = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { it.write(editor.text.toString().toByteArray()) }
            openedUri = uri
            appendLog("Saved: ${uri.lastPathSegment ?: "document"}")
        } catch (e: Exception) { appendLog("Save failed: ${e.message}") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 14, 18)
        window.navigationBarColor = Color.rgb(12, 14, 18)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 17, 22))
        }
        root.addView(TextView(this).apply {
            text = "  REEX IDE X  •  LOCAL NATIVE WORKSPACE"
            textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(25, 29, 37)); layoutParams = LinearLayout.LayoutParams(-1, 64)
        })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(6, 6, 6, 6) }
        listOf("PROJECT", "EDITOR", "TERMINAL", "TOOLS").forEach { label ->
            actions.addView(Button(this).apply {
                text = label; textSize = 10f
                setOnClickListener { appendLog("$label panel selected") }
            }, LinearLayout.LayoutParams(0, 52, 1f))
        }
        root.addView(actions)

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 2, 12, 2) }
        body.addView(TextView(this).apply {
            text = "WORKSPACE  •  Storage Access Framework\nOpen or create files without root permissions"
            textSize = 14f; setTextColor(Color.LTGRAY); setPadding(8, 8, 8, 8)
        })
        val fileActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fileActions.addView(Button(this).apply { text = "OPEN FILE"; setOnClickListener { openFile.launch(arrayOf("text/*", "application/json", "*/*")) } }, LinearLayout.LayoutParams(0, 52, 1f))
        fileActions.addView(Button(this).apply { text = "SAVE AS"; setOnClickListener { createFile.launch("reex_source.txt") } }, LinearLayout.LayoutParams(0, 52, 1f))
        body.addView(fileActions)
        editor = EditText(this).apply {
            hint = "Write code here..."; setText("// REEX IDE X\nfun main() {\n    println(\"Hello from REEX\")\n}")
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); textSize = 14f; gravity = Gravity.TOP
            setPadding(16, 16, 16, 16); setBackgroundColor(Color.rgb(22, 25, 32))
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        body.addView(editor)
        body.addView(Button(this).apply {
            text = "▶  LOCAL CHECK / ANALYZE"
            setOnClickListener {
                val text = editor.text.toString(); val lines = text.lines().size
                appendLog("Source checked: $lines lines, ${text.length} chars")
                if (text.isBlank()) appendLog("Warning: editor is empty") else appendLog("No basic editor errors detected")
            }
        })
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))

        console = TextView(this).apply {
            text = "DIAGNOSTICS CONSOLE\n> Native workspace initialized\n> No root required\n> SDK/toolchain integration is staged"
            textSize = 12f; setTextColor(Color.rgb(150, 220, 170)); setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.rgb(8, 11, 14))
        }
        root.addView(ScrollView(this).apply { addView(console) }, LinearLayout.LayoutParams(-1, 170))
        setContentView(root)
    }

    private fun appendLog(message: String) { console.append("\n> $message") }
}
