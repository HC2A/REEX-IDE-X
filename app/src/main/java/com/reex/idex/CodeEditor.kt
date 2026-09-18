package com.reex.idex

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
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

    init {
        setWillNotDraw(false)
    }

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
