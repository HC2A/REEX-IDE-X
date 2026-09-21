package com.reex.idex.core

import android.content.Context
import android.util.Base64
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GitHubProjectBuilder(private val context: Context, private val token: String) {
    companion object {
        private const val API = "https://api.github.com"
        private const val API_VERSION = "2022-11-28"
        private const val WORKFLOW = ".github/workflows/reex-cloud-build.yml"
        private const val ROOT = ".reex/project"
        private const val PREFIX = "reex-flutter-apk-"
        private const val MAX = 20L * 1024L * 1024L
    }

    private var repository = ""

    suspend fun build(project: File, architecture: String, onProgress: (String) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            require(project.isDirectory) { "Flutter project does not exist" }
            require(architecture in setOf("arm64-v8a", "armeabi-v7a", "x86_64"))
            onProgress("Checking repository…")
            val info = get("/repos/$repository")
            val branchName = info.optString("default_branch").ifBlank { "main" }
            val parent = get("/repos/$repository/git/ref/heads/$branchName")
                .getJSONObject("object").getString("sha")
            val baseTree = get("/repos/$repository/git/commits/$parent")
                .getJSONObject("tree").getString("sha")
            val buildBranch = "reex/build/" + System.currentTimeMillis()

            try {
                post("/repos/$repository/git/refs",
                    JSONObject().put("ref", "refs/heads/$buildBranch").put("sha", parent))
                onProgress("Uploading complete project…")
                val entries = upload(project)
                val tree = post("/repos/$repository/git/trees",
                    JSONObject().put("base_tree", baseTree).put("tree", JSONArray(entries)))
                val commit = post("/repos/$repository/git/commits",
                    JSONObject().put("message", "REEX complete Flutter cloud build")
                        .put("tree", tree.getString("sha"))
                        .put("parents", JSONArray().put(parent)))
                val sha = commit.getString("sha")
                patch("/repos/$repository/git/refs/heads/$buildBranch", JSONObject().put("sha", sha))
                onProgress("Starting real Flutter release build…")
                post("/repos/$repository/actions/workflows/reex-cloud-build.yml/dispatches",
                    JSONObject().put("ref", buildBranch)
                        .put("inputs", JSONObject().put("architecture", architecture)))

                var run: JSONObject? = null
                for (attempt in 0 until 180) {
                    delay(4000)
                    val runs = get("/repos/$repository/actions/runs?event=workflow_dispatch&branch=$buildBranch&per_page=10")
                        .optJSONArray("workflow_runs") ?: continue
                    for (i in 0 until runs.length()) {
                        val candidate = runs.getJSONObject(i)
                        if (candidate.optString("head_sha") == sha) {
                            run = candidate
                            break
                        }
                    }
                    val current = run ?: continue
                    val status = current.optString("status")
                    onProgress("GitHub build: $status")
                    if (status == "completed") break
                }
                val done = run ?: error("Timed out waiting for GitHub Actions")
                if (done.optString("status") != "completed" || done.optString("conclusion") != "success") {
                    error("Cloud build failed: " + done.optString("conclusion").ifBlank { "unknown" })
                }

                val artifacts = get("/repos/$repository/actions/runs/" + done.getLong("id") + "/artifacts")
                    .optJSONArray("artifacts") ?: error("No artifact returned")
                val artifact = (0 until artifacts.length()).map { artifacts.getJSONObject(it) }
                    .firstOrNull { it.optString("name") == PREFIX + architecture && !it.optBoolean("expired") }
                    ?: error("APK artifact not found")
                val zip = File(context.cacheDir, "reex-apk-" + done.getLong("id") + ".zip")
                download(artifact.getString("archive_download_url"), zip)
                val out = File(context.cacheDir, "reex-apk-" + done.getLong("id"))
                out.deleteRecursively()
                out.mkdirs()
                unzip(zip, out)
                zip.delete()
                out.walkTopDown().firstOrNull { it.isFile && it.extension == "apk" }
                    ?: error("Downloaded artifact does not contain an APK")
            } finally {
                runCatching { delete("/repos/$repository/git/refs/heads/$buildBranch") }
            }
        }

    fun configure(repo: String) {
        require(repo.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")))
        repository = repo
    }

    private fun upload(project: File): List<JSONObject> {
        val base = project.canonicalFile
        return base.walkTopDown().filter { it.isFile }.filterNot {
            it.relativeTo(base).path.split(File.separatorChar).firstOrNull() in setOf(".dart_tool", "build")
        }.map { file ->
            require(file.length() <= MAX) { "Project file is too large: " + file.name }
            val blob = post("/repos/$repository/git/blobs",
                JSONObject().put("content", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
                    .put("encoding", "base64"))
            JSONObject().put("path", ROOT + "/" + file.relativeTo(base).path.replace(File.separatorChar, '/'))
                .put("mode", "100644").put("type", "blob").put("sha", blob.getString("sha"))
        }.toList()
    }

    private fun request(method: String, path: String, body: JSONObject?): JSONObject {
        val c = (URL(API + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000
            readTimeout = 120000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer " + token)
            setRequestProperty("X-GitHub-Api-Version", API_VERSION)
            setRequestProperty("User-Agent", "REEX-IDE-X")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
        }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        c.disconnect()
        if (code !in 200..299) error("GitHub API $code: $text")
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun get(path: String) = request("GET", path, null)
    private fun post(path: String, body: JSONObject) = request("POST", path, body)
    private fun patch(path: String, body: JSONObject) = request("PATCH", path, body)
    private fun delete(path: String) = request("DELETE", path, null)

    private fun download(url: String, target: File) {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20000
            readTimeout = 120000
            setRequestProperty("Authorization", "Bearer " + token)
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", API_VERSION)
            setRequestProperty("User-Agent", "REEX-IDE-X")
        }
        if (c.responseCode !in 200..299) error("Artifact download failed: HTTP " + c.responseCode)
        target.parentFile?.mkdirs()
        BufferedInputStream(c.inputStream).use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
        c.disconnect()
    }

    private fun unzip(zip: File, out: File) {
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val target = File(out, entry.name).canonicalFile
                require(target.path.startsWith(out.canonicalPath + File.separator)) { "Unsafe artifact path" }
                if (entry.isDirectory) target.mkdirs()
                else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
            }
        }
    }
}
