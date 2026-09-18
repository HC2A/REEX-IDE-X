package com.reex.idex

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var console: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 14, 18)
        window.navigationBarColor = Color.rgb(12, 14, 18)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 17, 22))
        }
        root.addView(TextView(this).apply {
            text = "  REEX IDE X    • LOCAL WORKSPACE"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(25, 29, 37))
            layoutParams = LinearLayout.LayoutParams(-1, 64)
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
        }
        listOf("PROJECT", "EDITOR", "TERMINAL", "TOOLS").forEach { label ->
            actions.addView(Button(this).apply {
                text = label
                textSize = 10f
                setOnClickListener { appendLog("$label panel selected") }
            }, LinearLayout.LayoutParams(0, 52, 1f))
        }
        root.addView(actions)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 4, 14, 4)
        }
        body.addView(TextView(this).apply {
            text = "WORKSPACE\n  No project opened\n\nQUICK ACTIONS"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            setPadding(8, 8, 8, 8)
        })
        body.addView(Button(this).apply {
            text = "＋  Create local project"
            setOnClickListener { appendLog("Project wizard ready") }
        })
        body.addView(EditText(this).apply {
            hint = "Write code here..."
            setText("// REEX IDE X\nfun main() {\n    println(\"Hello from REEX\")\n}")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            textSize = 14f
            gravity = Gravity.TOP
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.rgb(22, 25, 32))
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        })
        body.addView(Button(this).apply {
            text = "▶  Run / Analyze"
            setOnClickListener { appendLog("Pipeline queued; toolchain installation required") }
        })
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))

        console = TextView(this).apply {
            text = "DIAGNOSTICS CONSOLE\n> REEX IDE X initialized\n> ARM64 / no-root mode\n> Ready"
            textSize = 12f
            setTextColor(Color.rgb(150, 220, 170))
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.rgb(8, 11, 14))
        }
        root.addView(ScrollView(this).apply { addView(console) }, LinearLayout.LayoutParams(-1, 150))
        setContentView(root)
    }

    private fun appendLog(message: String) { console.append("\n> $message") }
}
