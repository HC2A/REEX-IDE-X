package com.reex.idex.core

import android.content.Context
import android.content.Intent
import android.widget.Toast
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object FlutterRuntimeBridge {
    fun isAvailable(): Boolean = runCatching {
        Class.forName("io.flutter.embedding.android.FlutterActivity")
        true
    }.getOrDefault(false)

    fun launch(context: Context, source: String, arabic: Boolean) {
        runCatching {
            val flutterActivity = Class.forName("io.flutter.embedding.android.FlutterActivity")
            val builder = flutterActivity.getMethod("withNewEngine").invoke(null)
            val route = buildRoute(source, arabic)
            builder.javaClass.getMethod("initialRoute", String::class.java).invoke(builder, route)
            val intent = builder.javaClass.getMethod("build", Context::class.java).invoke(builder, context) as Intent
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(
                context,
                if (arabic) "Flutter Runtime غير مضمّن في هذه النسخة" else "Flutter Runtime is not packaged in this build",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun buildRoute(source: String, arabic: Boolean): String {
        val text = Regex("""Text\(['"]([^'"]+)""").find(source)?.groupValues?.getOrNull(1)?.take(120)
            ?: if (arabic) "معاينة Flutter حقيقية" else "Real Flutter runtime preview"
        val title = Regex("""AppBar\([^\n]*title:\s*(?:const\s*)?Text\(['"]([^'"]+)""")
            .find(source)?.groupValues?.getOrNull(1)?.take(80) ?: "REEX IDE X"
        val widgetCount = Regex("""\b(?:Scaffold|Container|Column|Row|Center|Text|ListView|ElevatedButton|TextField|Padding)\s*\(""")
            .findAll(source).count().coerceAtMost(99)
        fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
        return "/reex_preview?title=${enc(title)}&text=${enc(text)}&widgets=$widgetCount"
    }
}
