package com.jaben.tools.tools.git

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

data class PushFileEntry(
    val relativePath: String,
    val bytes: ByteArray
) {
    val sizeBytes: Long get() = bytes.size.toLong()
}

data class PushGitResult(
    val commitSha: String,
    val fileCount: Int,
    val commitUrl: String
)

class PushGitEngine(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSavedToken(): String = prefs.getString(KEY_PAT, "").orEmpty()
    fun loadSavedRepo(): String = prefs.getString(KEY_REPO, "jabenjay/myaitool").orEmpty()
    fun loadSavedBranch(): String = prefs.getString(KEY_BRANCH, "main").orEmpty()
    fun loadSavedMessage(): String = prefs.getString(KEY_MSG, "Sync project files via PushGit").orEmpty()

    fun saveConfig(pat: String, repo: String, branch: String, message: String) {
        prefs.edit()
            .putString(KEY_PAT, pat.trim())
            .putString(KEY_REPO, repo.trim())
            .putString(KEY_BRANCH, branch.trim())
            .putString(KEY_MSG, message.trim())
            .apply()
    }

    fun parseOwnerRepo(input: String): String {
        val clean = input.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removeSuffix(".git")
            .trim('/')
        val parts = clean.split('/').filter { it.isNotBlank() }
        return if (parts.size >= 2) "${parts[0]}/${parts[1]}" else clean
    }

    suspend fun verifyToken(pat: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = apiRequest("GET", "https://api.github.com/user", pat, null)
            val json = JSONObject(resp)
            json.optString("login").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("无法获取用户名")
        }
    }

    suspend fun listRepos(pat: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = apiRequest("GET", "https://api.github.com/user/repos?per_page=100&sort=updated", pat, null)
            val arr = JSONArray(resp)
            buildList {
                for (i in 0 until arr.length()) {
                    val fullName = arr.optJSONObject(i)?.optString("full_name").orEmpty()
                    if (fullName.isNotBlank()) add(fullName)
                }
            }
        }
    }

    suspend fun listBranches(pat: String, ownerRepo: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = parseOwnerRepo(ownerRepo)
            val resp = apiRequest("GET", "https://api.github.com/repos/$repo/branches?per_page=100", pat, null)
            val arr = JSONArray(resp)
            buildList {
                for (i in 0 until arr.length()) {
                    val name = arr.optJSONObject(i)?.optString("name").orEmpty()
                    if (name.isNotBlank()) add(name)
                }
            }
        }
    }

    suspend fun readZipUri(uri: Uri): Result<List<PushFileEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val list = ArrayList<PushFileEntry>()
            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name.trimStart('/')
                        if (!entry.isDirectory && name.isNotBlank() &&
                            !name.startsWith("__MACOSX/") &&
                            !name.endsWith(".DS_Store") &&
                            !name.startsWith(".git/")
                        ) {
                            val baos = ByteArrayOutputStream()
                            val buf = ByteArray(8192)
                            var len: Int
                            while (zis.read(buf).also { len = it } != -1) {
                                baos.write(buf, 0, len)
                            }
                            list.add(PushFileEntry(name, baos.toByteArray()))
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: throw IllegalStateException("无法读取压缩包")
            list
        }
    }

    suspend fun readFolderUri(treeUri: Uri): Result<List<PushFileEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: throw IllegalStateException("无法访问所选文件夹")
            val list = ArrayList<PushFileEntry>()
            collectFolder(root, "", list)
            list
        }
    }

    private fun collectFolder(dir: DocumentFile, prefix: String, out: MutableList<PushFileEntry>) {
        for (child in dir.listFiles()) {
            val name = child.name ?: continue
            if (name == ".git" || name == ".gradle" || name == "build" || name == ".DS_Store") continue
            val rel = if (prefix.isEmpty()) name else "$prefix/$name"
            if (child.isDirectory) {
                collectFolder(child, rel, out)
            } else if (child.isFile) {
                context.contentResolver.openInputStream(child.uri)?.use { ins ->
                    out.add(PushFileEntry(rel, ins.readBytes()))
                }
            }
        }
    }

    suspend fun readFileUris(uris: List<Uri>, targetPrefix: String): Result<List<PushFileEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanPrefix = targetPrefix.trim().trim('/')
            uris.mapNotNull { uri ->
                val name = resolveFileName(uri) ?: return@mapNotNull null
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@mapNotNull null
                val rel = if (cleanPrefix.isEmpty()) name else "$cleanPrefix/$name"
                PushFileEntry(rel, bytes)
            }
        }
    }

    private fun resolveFileName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return c.getString(idx)
            }
        }
        return DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment
    }

    suspend fun pushFiles(
        pat: String,
        ownerRepoInput: String,
        branchInput: String,
        commitMessageInput: String,
        files: List<PushFileEntry>,
        onProgress: (done: Int, total: Int, currentPath: String) -> Unit
    ): Result<PushGitResult> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = parseOwnerRepo(ownerRepoInput)
            val branch = branchInput.trim().ifBlank { "main" }
            val message = commitMessageInput.trim().ifBlank { "Sync project files via PushGit" }
            require(repo.contains("/")) { "仓库格式应为 owner/repo" }
            require(pat.isNotBlank()) { "请填写 GitHub Token" }
            require(files.isNotEmpty()) { "请先选择要上传的文件" }

            // 1. Get branch HEAD commit SHA
            val refResp = apiRequest("GET", "https://api.github.com/repos/$repo/git/ref/heads/$branch", pat, null)
            val headSha = JSONObject(refResp).getJSONObject("object").getString("sha")

            // 2. Get base tree SHA
            val commitResp = apiRequest("GET", "https://api.github.com/repos/$repo/git/commits/$headSha", pat, null)
            val baseTreeSha = JSONObject(commitResp).getJSONObject("tree").getString("sha")

            // 3. Create blobs for all files
            val treeArray = JSONArray()
            val total = files.size
            files.forEachIndexed { idx, file ->
                onProgress(idx, total, file.relativePath)
                val b64 = Base64.encodeToString(file.bytes, Base64.NO_WRAP)
                val blobBody = JSONObject().apply {
                    put("content", b64)
                    put("encoding", "base64")
                }.toString()
                val blobResp = apiRequest("POST", "https://api.github.com/repos/$repo/git/blobs", pat, blobBody)
                val blobSha = JSONObject(blobResp).getString("sha")

                treeArray.put(
                    JSONObject().apply {
                        put("path", file.relativePath)
                        put("mode", "100644")
                        put("type", "blob")
                        put("sha", blobSha)
                    }
                )
            }
            onProgress(total, total, "正在提交...")

            // 4. Create new tree
            val treeBody = JSONObject().apply {
                put("base_tree", baseTreeSha)
                put("tree", treeArray)
            }.toString()
            val newTreeResp = apiRequest("POST", "https://api.github.com/repos/$repo/git/trees", pat, treeBody)
            val newTreeSha = JSONObject(newTreeResp).getString("sha")

            // 5. Create new commit
            val commitBody = JSONObject().apply {
                put("message", message)
                put("tree", newTreeSha)
                put("parents", JSONArray().put(headSha))
            }.toString()
            val newCommitResp = apiRequest("POST", "https://api.github.com/repos/$repo/git/commits", pat, commitBody)
            val newCommitSha = JSONObject(newCommitResp).getString("sha")

            // 6. Update branch ref
            val patchBody = JSONObject().apply {
                put("sha", newCommitSha)
                put("force", false)
            }.toString()
            apiRequest("PATCH", "https://api.github.com/repos/$repo/git/refs/heads/$branch", pat, patchBody)

            PushGitResult(
                commitSha = newCommitSha,
                fileCount = files.size,
                commitUrl = "https://github.com/$repo/commit/$newCommitSha"
            )
        }
    }

    private fun apiRequest(method: String, urlStr: String, pat: String, jsonBody: String?): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = if (method == "PATCH") "POST" else method
            if (method == "PATCH") {
                setRequestProperty("X-HTTP-Method-Override", "PATCH")
            }
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer ${pat.trim()}")
            setRequestProperty("User-Agent", "Jools-PushGit/1.0")
            if (jsonBody != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (jsonBody != null) {
                conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).optString("message") }.getOrNull().orEmpty()
                throw IllegalStateException("GitHub API $code: ${msg.ifBlank { text.take(120) }}")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val PREFS_NAME = "jools_pushgit_prefs"
        private const val KEY_PAT = "pat"
        private const val KEY_REPO = "repo"
        private const val KEY_BRANCH = "branch"
        private const val KEY_MSG = "commit_msg"
    }
}
