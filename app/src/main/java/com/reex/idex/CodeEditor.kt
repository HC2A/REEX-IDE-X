package com.reex.idex

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

class CodeEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    private val gutterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 12f * resources.displayMetrics.scaledDensity
        color = Color.rgb(68, 84, 106)
    }
    private val gutterBg = Paint().apply { color = Color.rgb(13, 19, 28) }
    private val divider = Paint().apply { color = Color.rgb(25, 34, 47) }

    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var restoring = false

    init {
        setWillNotDraw(false)
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!restoring) {
                    undoStack.addLast(s?.toString().orEmpty())
                    while (undoStack.size > 40) undoStack.removeFirst()
                    redoStack.clear()
                }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = text.toString()
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(current)
        restoring = true
        setText(previous)
        setSelection(length())
        restoring = false
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = text.toString()
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(current)
        restoring = true
        setText(next)
        setSelection(length())
        restoring = false
    }

    override fun getText(): Editable = super.getText() ?: Editable.Factory.getInstance().newEditable("")

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val gutterWidth = 58f * d
        canvas.drawRect(0f, 0f, gutterWidth, height.toFloat(), gutterBg)
        canvas.drawRect(gutterWidth, 0f, gutterWidth + d, height.toFloat(), divider)

        val l = layout
        if (l != null) {
            val first = l.getLineForVertical(scrollY).coerceAtLeast(0)
            val last = l.getLineForVertical(scrollY + height).coerceAtMost(l.lineCount - 1)
            for (line in first..last) {
                val baseline = l.getLineBaseline(line).toFloat()
                val number = (line + 1).toString()
                canvas.drawText(
                    number,
                    gutterWidth - 8f * d - gutterPaint.measureText(number),
                    baseline,
                    gutterPaint
                )
            }
        }
        super.onDraw(canvas)
    }
}
