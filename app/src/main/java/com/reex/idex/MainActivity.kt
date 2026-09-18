package com.reex.idex

import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 18, 22))
            setPadding(32, 48, 32, 32)
        }

        val title = TextView(this).apply {
            text = "REEX IDE X"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val subtitle = TextView(this).apply {
            text = "Native Android IDE • Phase A Foundation"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 16, 0, 48)
        }

        val status = TextView(this).apply {
            text = "Workspace ready\n\nEditor: Foundation ready\nTerminal: Engine pending\nFlutter SDK: Not installed\nDart Analyzer: Phase 2\nBuild Engine: Phase 3"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(24, 24, 24, 24)
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(status)
        setContentView(root)
    }
}
