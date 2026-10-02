package com.reex.idex.core
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class AiAgentResult(val success: Boolean, val message: String, val changedFiles: List<String> = emptyList())

class AiProjectAgent(private val context: Context) {
    private val keys = AiKeyStore(context)
    fun configured(): Boolean = keys.readKey().isNotBlank()
    fun run(projectRoot: File, request: String,
            endpoint: String = "https://api.openai.com/v1/chat/completions",
            model: String = "gpt-5.6"): AiAgentResult {
        val key = keys.readKey()
        if (key.isBlank()) return AiAgentResult(false, "AI key is not configured.")
        return runCatching {
            val c = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 20_000; readTimeout = 120_000; doOutput = true
                setRequestProperty("Authorization", "Bearer " + key)
                setRequestProperty("Content-Type", "application/json")
            }
            val body = JSONObject().put("model", model).put("temperature", 0.1).put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content",
                    "You are REEX IDE X project agent. Return ONLY JSON with message and actions. " +
                    "Each action is op write/delete/mkdir, path workspace-relative, content for write. Never escape workspace."))
                .put(JSONObject().put("role", "user").put("content", buildPrompt(projectRoot, request)))).toString()
            c.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val status = c.responseCode
            val response = (if (status in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) return@runCatching AiAgentResult(false, "AI HTTP " + status + ": " + response.take(1000))
            val content = JSONObject(response).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
            applyPlan(projectRoot, content)
        }.getOrElse { AiAgentResult(false, "AI request failed: " + (it.message ?: it::class.simpleName)) }
    }
    private fun buildPrompt(root: File, request: String): String {
        val files = root.walkTopDown().filter { it.isFile }.filter {
            val p = it.relativeTo(root).path.replace(File.separatorChar, '/')
            !p.startsWith(".git/") && !p.startsWith("build/") && !p.startsWith(".dart_tool/") && it.length() <= 120_000
        }.take(80).joinToString("\n\n") {
            "===== FILE: " + it.relativeTo(root).path.replace(File.separatorChar, '/') + " =====\n" + it.readText(Charsets.UTF_8)
        }
        return "USER REQUEST:\n" + request + "\n\nPROJECT FILES:\n" + files
    }
    private fun applyPlan(root: File, raw: String): AiAgentResult {
        val json = JSONObject(raw.trim())
        val actions = json.optJSONArray("actions") ?: JSONArray()
        val changed = mutableListOf<String>()
        for (i in 0 until actions.length()) {
            val a = actions.optJSONObject(i) ?: continue
            val rel = a.optString("path").replace('\\', '/')
            if (rel.isBlank() || rel.startsWith("/") || rel.contains("..")) continue
            val target = File(root, rel)
            if (!target.canonicalPath.startsWith(root.canonicalPath + File.separator)) continue
            when (a.optString("op")) {
                "mkdir" -> target.mkdirs()
                "delete" -> if (target.isFile) target.delete()
                "write" -> { target.parentFile?.mkdirs(); target.writeText(a.optString("content"), Charsets.UTF_8); changed += rel }
            }
        }
        return AiAgentResult(true, json.optString("message", "AI project operation completed."), changed.distinct())
    }
}
