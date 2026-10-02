package com.reex.idex.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class LspCompletion(
    val label: String,
    val detail: String = "",
    val insertText: String? = null,
    val kind: Int? = null
)

/**
 * Real Dart Language Server Protocol client.
 * It speaks JSON-RPC over dart language-server stdin/stdout; no canned completion list is used.
 */
class DartLanguageServer(private val toolchain: OfflineToolchainManager) {
    private var process: Process? = null
    private var output: OutputStream? = null
    private val ids = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableFuture<JSONObject>>()
    @Volatile private var initialized = false

    fun start(project: File): Boolean {
        if (initialized && process?.isAlive == true) return true
        stop()
        val status = toolchain.status()
        val dart = File(status.dart, "bin/dart")
        if (!status.active || !dart.isFile) return false
        return runCatching {
            val env = mapOf(
                "PUB_CACHE" to status.pubCache.absolutePath,
                "PATH" to listOf(
                    dart.parentFile.absolutePath,
                    File(status.flutter, "bin").absolutePath,
                    System.getenv("PATH").orEmpty()
                ).joinToString(File.pathSeparator)
            )
            process = ProcessBuilder(dart.absolutePath, "language-server", "--protocol=lsp")
                .directory(project)
                .redirectErrorStream(false)
                .apply { environment().putAll(env) }
                .start()
            output = process!!.outputStream
            Thread { readLoop(process!!.inputStream) }.apply {
                isDaemon = true
                name = "reex-dart-lsp-reader"
                start()
            }
            val init = request(
                "initialize",
                JSONObject()
                    .put("processId", android.os.Process.myPid())
                    .put("rootUri", project.toURI().toString())
                    .put("capabilities", JSONObject()
                        .put("textDocument", JSONObject()
                            .put("completion", JSONObject()
                                .put("completionItem", JSONObject().put("snippetSupport", false))))
                    )
            )
            if (init.getInt("errorCodeOrZero") != 0) return false
            sendNotification("initialized", JSONObject())
            initialized = true
            true
        }.getOrDefault(false)
    }

    fun open(project: File, file: File, text: String): Boolean {
        if (!start(project)) return false
        sendNotification(
            "textDocument/didOpen",
            JSONObject().put("textDocument", JSONObject()
                .put("uri", file.toURI().toString())
                .put("languageId", "dart")
                .put("version", 1)
                .put("text", text))
        )
        return true
    }

    fun change(file: File, text: String, version: Int = 2): Boolean {
        if (!initialized) return false
        sendNotification(
            "textDocument/didChange",
            JSONObject().put("textDocument", JSONObject()
                .put("uri", file.toURI().toString())
                .put("version", version))
                .put("contentChanges", JSONArray()
                    .put(JSONObject().put("text", text)))
        )
        return true
    }

    fun completion(file: File, line: Int, character: Int): List<LspCompletion> {
        if (!initialized) return emptyList()
        val response = runCatching {
            request(
                "textDocument/completion",
                JSONObject()
                    .put("textDocument", JSONObject().put("uri", file.toURI().toString()))
                    .put("position", JSONObject().put("line", line).put("character", character))
            )
        }.getOrNull() ?: return emptyList()

        val result = response.opt("result")
        val items = when (result) {
            is JSONObject -> result.optJSONArray("items") ?: JSONArray()
            is JSONArray -> result
            else -> JSONArray()
        }
        return buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                add(
                    LspCompletion(
                        label = item.optString("label"),
                        detail = item.optString("detail"),
                        insertText = item.optString("insertText").ifBlank {
                            item.optJSONObject("textEdit")?.optString("newText")
                        },
                        kind = item.optInt("kind", 0).takeIf { it != 0 }
                    )
                )
            }
        }.filter { it.label.isNotBlank() }.take(80)
    }

    fun stop() {
        initialized = false
        pending.values.forEach { it.completeExceptionally(IllegalStateException("LSP stopped")) }
        pending.clear()
        runCatching { output?.close() }
        runCatching { process?.destroyForcibly() }
        output = null
        process = null
    }

    private fun request(method: String, params: JSONObject): JSONObject {
        val id = ids.getAndIncrement()
        val future = CompletableFuture<JSONObject>()
        pending[id] = future
        sendMessage(JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method).put("params", params))
        return future.get(20, TimeUnit.SECONDS)
    }

    private fun sendNotification(method: String, params: JSONObject) {
        sendMessage(JSONObject().put("jsonrpc", "2.0").put("method", method).put("params", params))
    }

    @Synchronized
    private fun sendMessage(message: JSONObject) {
        val bytes = message.toString().toByteArray(StandardCharsets.UTF_8)
        val header = "Content-Length: ${bytes.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII)
        output?.apply {
            write(header)
            write(bytes)
            flush()
        } ?: throw IllegalStateException("Dart LSP process is not running")
    }

    private fun readLoop(input: InputStream) {
        try {
            while (process?.isAlive == true) {
                var length = -1
                var line = readAsciiLine(input) ?: return
                while (line.isNotEmpty()) {
                    if (line.startsWith("Content-Length:", true)) {
                        length = line.substringAfter(":").trim().toIntOrNull() ?: -1
                    }
                    line = readAsciiLine(input) ?: return
                }
                if (length <= 0) continue
                val bytes = input.readNBytes(length)
                if (bytes.size != length) return
                handleMessage(JSONObject(String(bytes, StandardCharsets.UTF_8)))
            }
        } catch (_: Throwable) {
            stop()
        }
    }

    private fun readAsciiLine(input: InputStream): String? {
        val buffer = java.io.ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buffer.size() == 0) null else buffer.toString("US-ASCII")
            if (b == 10) return buffer.toString("US-ASCII").removeSuffix("\r")
            buffer.write(b)
        }
    }

    private fun handleMessage(message: JSONObject) {
        val id = message.optInt("id", -1)
        val method = message.optString("method", "")
        if (id > 0 && method.isNotBlank()) {
            val result = when (method) {
                "workspace/configuration" -> JSONArray()
                "client/registerCapability", "client/unregisterCapability" -> JSONObject()
                "workspace/applyEdit" -> JSONObject().put("applied", false)
                else -> JSONObject()
            }
            runCatching {
                sendMessage(JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result))
            }
            return
        }
        if (id > 0) {
            val future = pending.remove(id) ?: return
            if (message.has("error")) {
                future.complete(JSONObject().put("errorCodeOrZero", message.optJSONObject("error")?.optInt("code", -1) ?: -1))
            } else {
                future.complete(message.put("errorCodeOrZero", 0))
            }
        }
    }
}
