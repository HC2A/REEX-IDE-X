package com.reex.idex.core

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import android.util.Base64
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class RuntimeBundleResult(
    val runId: Long,
    val artifactId: Long,
    val bundleDir: File,
    val runUrl: String?
)

/** Compiles the complete user Flutter project to a real JIT kernel on GitHub,
 * then returns the runtime bundle for the embedded Flutter JIT engine.
 */
class GitHubRuntimeBuilder(
    private val context: Context,
    private val token: String
) {
    companion object {
        private const val API = "https://api.github.com"
        private const val API_VERSION = "2022-11-28"
        private const val WORKFLOW = ".github/workflows/reex-runtime-build.yml"
        private const val PROJECT_ROOT = ".reex/project"
        private const val ARTIFACT_PREFIX = "reex-flutter-runtime-"
        private const val MAX_FILE_BYTES = 20L * 1024L * 1024L
    }

    private var activeRepository: String = ""

    suspend fun compile(
        repository: String,
        project: File,
        architecture: String,
        onProgress: (String) -> Unit = {}
    ): RuntimeBundleResult = withContext(Dispatchers.IO) {
        require(repository.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "Repository must be owner/name" }
        require(project.isDirectory) { "Flutter project directory does not exist" }
        require(architecture in setOf("arm64-v8a", "armeabi-v7a", "x86_64")) { "Unsupported architecture" }
        activeRepository = repository

        onProgress("Checking GitHub repository…")
        val repo = get("/repos/$repository")
        val defaultBranch = repo.optString("default_branch").ifBlank { "main" }
        val ref = get("/repos/$repository/git/ref/heads/$defaultBranch")
        val parentSha = ref.getJSONObject("object").getString("sha")
        val parentCommit = get("/repos/$repository/git/commits/$parentSha")
        val baseTree = parentCommit.getJSONObject("tree").getString("sha")
        val branch = "reex-runtime/" + System.currentTimeMillis()

        try {
            onProgress("Creating isolated build branch…")
            post(
                "/repos/$repository/git/refs",
                JSONObject().put("ref", "refs/heads/$branch").put("sha", parentSha)
            )

            onProgress("Uploading complete Flutter project…")
            val entries = uploadProject(project, PROJECT_ROOT)
            val tree = post(
                "/repos/$repository/git/trees",
                JSONObject().put("base_tree", baseTree).put("tree", JSONArray(entries))
            )
            val commit = post(
                "/repos/$repository/git/commits",
                JSONObject()
                    .put("message", "REEX runtime compile")
                    .put("tree", tree.getString("sha"))
                    .put("parents", JSONArray().put(parentSha))
            )
            val commitSha = commit.getString("sha")
            patch("/repos/$repository/git/refs/heads/$branch", JSONObject().put("sha", commitSha))

            onProgress("Compiling Dart to real Flutter kernel…")
            // The runtime workflow is also triggered by the isolated branch push.
            // This avoids requiring Actions:write just to start a user build.

            var run: JSONObject? = null
            for (attempt in 0 until 180) {
                delay(4000)
                val runs = get(
                    "/repos/$repository/actions/runs?event=workflow_dispatch&branch=$branch&per_page=10"
                ).optJSONArray("workflow_runs") ?: continue
                for (i in 0 until runs.length()) {
                    val candidate = runs.getJSONObject(i)
                    if (candidate.optString("head_sha") == commitSha) {
                        run = candidate
                        break
                    }
                }
                val current = run ?: continue
                val status = current.optString("status")
                val conclusion = current.optString("conclusion")
                onProgress("GitHub runtime: $status" + if (conclusion.isNotBlank()) " • $conclusion" else "")
                if (status == "completed") break
            }

            val completed = run ?: error("Timed out waiting for the runtime compiler")
            if (completed.optString("status") != "completed" || completed.optString("conclusion") != "success") {
                error("Runtime compilation failed: " + completed.optString("conclusion").ifBlank { "unknown" })
            }

            onProgress("Downloading compiled Flutter kernel…")
            val artifacts = get(
                "/repos/$repository/actions/runs/" + completed.getLong("id") + "/artifacts"
            ).optJSONArray("artifacts") ?: error("No runtime artifact returned")
            val artifact = (0 until artifacts.length())
                .map { artifacts.getJSONObject(it) }
                .firstOrNull {
                    it.optString("name") == ARTIFACT_PREFIX + architecture && !it.optBoolean("expired")
                } ?: error("Runtime artifact not found")

            val zip = File(context.cacheDir, "reex-runtime-" + completed.getLong("id") + ".zip")
            download(artifact.getString("archive_download_url"), zip)
            val bundle = unzip(zip, File(context.cacheDir, "reex-runtime-" + completed.getLong("id")))
            zip.delete()

            RuntimeBundleResult(
                runId = completed.getLong("id"),
                artifactId = artifact.getLong("id"),
                bundleDir = bundle,
                runUrl = completed.optString("html_url").ifBlank { null }
            )
        } finally {
            runCatching { delete("/repos/$repository/git/refs/heads/$branch") }
            activeRepository = ""
        }
    }

    private fun uploadProject(project: File, root: String): List<JSONObject> {
        val base = project.canonicalFile
        return base.walkTopDown()
            .filter { it.isFile }
            .filterNot {
                it.relativeTo(base).path.split(File.separatorChar).firstOrNull() in setOf(".dart_tool", "build")
            }
            .map { file ->
                require(file.length() <= MAX_FILE_BYTES) {
                    "Project file is too large: " + file.relativeTo(base).path
                }
                val blob = post(
                    "/repos/$activeRepository/git/blobs",
                    JSONObject()
                        .put("content", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
                        .put("encoding", "base64")
                )
                JSONObject()
                    .put("path", root + "/" + file.relativeTo(base).path.replace(File.separatorChar, '/'))
                    .put("mode", "100644")
                    .put("type", "blob")
                    .put("sha", blob.getString("sha"))
            }
            .toList()
    }

    private fun get(path: String): JSONObject = request("GET", path, null)
    private fun post(path: String, body: JSONObject): JSONObject = request("POST", path, body)
    private fun patch(path: String, body: JSONObject): JSONObject = request("PATCH", path, body)
    private fun delete(path: String): JSONObject = request("DELETE", path, null)

    private fun request(method: String, path: String, body: JSONObject?): JSONObject {
        val connection = (URL(API + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20000
            readTimeout = 120000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
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
        val response = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) error("GitHub API $code: $response")
        return if (response.isBlank()) JSONObject() else JSONObject(response)
    }

    private fun download(url: String, target: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20000
            readTimeout = 120000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", API_VERSION)
            setRequestProperty("User-Agent", "REEX-IDE-X")
        }
        if (connection.responseCode !in 200..299) error("Artifact download failed: HTTP " + connection.responseCode)
        target.parentFile?.mkdirs()
        BufferedInputStream(connection.inputStream).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        connection.disconnect()
    }

    private fun unzip(zip: File, outputDir: File): File {
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
        return outputDir
    }
}
