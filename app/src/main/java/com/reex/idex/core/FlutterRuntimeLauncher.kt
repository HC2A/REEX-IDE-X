package com.reex.idex.core

import android.content.Context
import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import java.net.URLEncoder

object FlutterRuntimeLauncher {
    fun launch(context: Context, projectPath: String, sourcePath: String) {
        val route = "/reex-preview?project=" + encode(projectPath) + "&source=" + encode(sourcePath)
        val intent: Intent = FlutterActivity
            .withNewEngine()
            .initialRoute(route)
            .build(context)
        context.startActivity(intent)
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
