package com.reex.idex.core

import android.content.Context
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

data class CloudBuildResult(val runId: Long, val artifactId: Long, val apk: File, val runUrl: String?)

class GitHubCloudBuilder(
    private val context: Context,
    private val token: String
) {
    companion object {
        private const val API = "https://api.github.com"
        private const val API_VERSION = "2022-11-28"
        private const val WORKFLOW = ".github/workflows/reex-cloud-build.yml"
        private const val PROJECT_ROOT = ".reex/project"
        private const val ARTIFACT = "reex-flutter-apk"
    }

    suspend fun build(repository: String, source: String, architecture: String, onProgress: (String) -> Unit = {}): CloudBuildResult =
        withContext(Dispatchers.IO) {
            require(repository.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "Repository must be owner/name" }

            onProgress("Checking GitHub repository…")
            val repo = get("/repos/" + repository)
            val branch = repo.optString("default_branch").ifBlank { "main" }

            onProgress("Preparing cloud builder…")
            ensureWorkflow(repository, branch)

            onProgress("Uploading Flutter project…")
            val ref = get("/repos/" + repository + "/git/ref/heads/" + branch)
            val parentSha = ref.getJSONObject("object").getString("sha")
            val parentCommit = get("/repos/" + repository + "/git/commits/" + parentSha)
            val baseTree = parentCommit.getJSONObject("tree").getString("sha")

            val pubspec = """
name: reex_cloud_project
description: Flutter project built by REEX IDE X
publish_to: 'none'
version: 1.0.0+1
environment:
  sdk: ">=3.0.0 <4.0.0"
dependencies:
  flutter:
    sdk: flutter
flutter:
  uses-material-design: true
""".trimIndent() + "\n"

            val tree = post(
                "/repos/" + repository + "/git/trees",
                JSONObject()
                    .put("base_tree", baseTree)
                    .put("tree", JSONArray()
                        .put(fileEntry(PROJECT_ROOT + "/pubspec.yaml", pubspec))
                        .put(fileEntry(PROJECT_ROOT + "/lib/main.dart", source)))
            )
            val commit = post(
                "/repos/" + repository + "/git/commits",
                JSONObject()
                    .put("message", "REEX Cloud Build")
                    .put("tree", tree.getString("sha"))
                    .put("parents", JSONArray().put(parentSha))
            )
            val commitSha = commit.getString("sha")
            put("/repos/" + repository + "/git/refs/heads/" + branch, JSONObject().put("sha", commitSha))

            onProgress("Starting GitHub Actions…")
            post(
                "/repos/" + repository + "/actions/workflows/reex-cloud-build.yml/dispatches",
                JSONObject().put("ref", branch).put("inputs", JSONObject().put("architecture", architecture))
            )

            var run: JSONObject? = null
            for (attempt in 0 until 120) {
                delay(5000)
                val runs = get("/repos/" + repository + "/actions/runs?event=workflow_dispatch&branch=" + branch + "&per_page=10")
                    .optJSONArray("workflow_runs") ?: continue
                for (i in 0 until runs.length()) {
                    val candidate = runs.getJSONObject(i)
                    if (candidate.optString("head_sha") == commitSha) {
                        run = candidate
                        break
                    }
                }
                val current = run
                if (current != null) {
                    val conclusion = current.optString("conclusion")
                    onProgress("GitHub: " + current.optString("status") + if (conclusion.isNotBlank()) " • " + conclusion else "")
                    if (current.optString("status") == "completed") break
                } else {
                    onProgress("Waiting for runner…")
                }
            }

            val completed = run ?: error("Timed out waiting for GitHub Actions")
            if (completed.optString("status") != "completed" || completed.optString("conclusion") != "success") {
                error("GitHub build failed: " + (completed.optString("conclusion").ifBlank { completed.optString("status") }))
            }

            onProgress("Downloading APK…")
            val artifacts = get("/repos/" + repository + "/actions/runs/" + completed.getLong("id") + "/artifacts")
                .optJSONArray("artifacts") ?: error("No build artifact returned")
            val artifact = (0 until artifacts.length()).map { artifacts.getJSONObject(it) }
                .firstOrNull { it.optString("name") == ARTIFACT && !it.optBoolean("expired") }
                ?: error("APK artifact not found")

            val zip = File(context.cacheDir, "reex-cloud-artifact.zip")
            download(artifact.getString("archive_download_url"), zip)
            val apk = unzipApk(zip, File(context.cacheDir, "reex-build-" + completed.getLong("id")))
            zip.delete()

            CloudBuildResult(
                runId = completed.getLong("id"),
                artifactId = artifact.getLong("id"),
                apk = apk,
                runUrl = completed.optString("html_url").ifBlank { null }
            )
        }

    private fun ensureWorkflow(repository: String, branch: String) {
        if (runCatching { get("/repos/" + repository + "/contents/" + WORKFLOW + "?ref=" + branch) }.isSuccess) return

        val ref = get("/repos/" + repository + "/git/ref/heads/" + branch)
        val parentSha = ref.getJSONObject("object").getString("sha")
        val parentCommit = get("/repos/" + repository + "/git/commits/" + parentSha)
        val baseTree = parentCommit.getJSONObject("tree").getString("sha")
        val workflowText = context.assets.open("reex-cloud-build.yml").bufferedReader().use { it.readText() }

        val tree = post(
            "/repos/" + repository + "/git/trees",
            JSONObject().put("base_tree", baseTree).put("tree", JSONArray().put(fileEntry(WORKFLOW, workflowText)))
        )
        val commit = post(
            "/repos/" + repository + "/git/commits",
            JSONObject().put("message", "Add REEX cloud Flutter builder")
                .put("tree", tree.getString("sha")).put("parents", JSONArray().put(parentSha))
        )
        put("/repos/" + repository + "/git/refs/heads/" + branch, JSONObject().put("sha", commit.getString("sha")))
    }

    private fun fileEntry(path: String, content: String) =
        JSONObject().put("path", path).put("mode", "100644").put("type", "blob").put("content", content)

    private fun get(path: String): JSONObject = request("GET", path, null)
    private fun post(path: String, body: JSONObject): JSONObject = request("POST", path, body)
    private fun put(path: String, body: JSONObject): JSONObject = request("PATCH", path, body)

    private fun request(method: String, path: String, body: JSONObject?): JSONObject {
        val connection = (URL(API + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000
            readTimeout = 60000
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
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) error("GitHub API " + code + ": " + text)
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun download(url: String, target: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20000
            readTimeout = 120000
            setRequestProperty("Authorization", "Bearer " + token)
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", API_VERSION)
            setRequestProperty("User-Agent", "REEX-IDE-X")
        }
        if (connection.responseCode !in 200..299) error("Artifact download failed: HTTP " + connection.responseCode)
        target.parentFile?.mkdirs()
        BufferedInputStream(connection.inputStream).use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
        connection.disconnect()
    }

    private fun unzipApk(zip: File, outputDir: File): File {
        outputDir.deleteRecursively()
        outputDir.mkdirs()
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val target = File(outputDir, entry.name).canonicalFile
                require(target.path.startsWith(outputDir.canonicalPath + File.separator)) { "Unsafe artifact path" }
                if (entry.isDirectory) target.mkdirs()
                else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
            }
        }
        return outputDir.walkTopDown().firstOrNull { it.isFile && it.extension == "apk" }
            ?: error("Artifact ZIP did not contain an APK")
    }
}
