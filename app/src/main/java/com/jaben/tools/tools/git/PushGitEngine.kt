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
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

data class SavedGitToken(
    val id: String,
    val username: String,
    val token: String
)

data class PushFileEntry(
    val relativePath: String,
    val bytes: ByteArray
) {
    val sizeBytes: Long get() = bytes.size.toLong()
}

data class GitDiffSummary(
    val headSha: String,
    val baseTreeSha: String,
    val addedFiles: List<PushFileEntry>,
    val modifiedFiles: List<PushFileEntry>,
    val deletedPaths: List<String>,
    val unchangedCount: Int
) {
    val uploadFiles: List<PushFileEntry> get() = addedFiles + modifiedFiles
    val hasChanges: Boolean get() = addedFiles.isNotEmpty() || modifiedFiles.isNotEmpty() || deletedPaths.isNotEmpty()
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

    fun getSavedTokens(): List<SavedGitToken> {
        val raw = prefs.getString(KEY_SAVED_TOKENS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val token = obj.optString("token").trim()
                    if (token.isNotEmpty()) {
                        add(
                            SavedGitToken(
                                id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                                username = obj.optString("username").ifBlank { "GitHub User" },
                                token = token
                            )
                        )
                    }
                }
            }
        }.getOrElse { emptyList() }
    }

    fun saveTokenEntry(username: String, token: String): List<SavedGitToken> {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return getSavedTokens()
        val existing = getSavedTokens().filterNot { it.token == cleanToken }
        val updated = listOf(
            SavedGitToken(
                id = UUID.randomUUID().toString(),
                username = username.trim().ifBlank { "GitHub User" },
                token = cleanToken
            )
        ) + existing
        persistTokens(updated)
        return updated
    }

    fun deleteTokenEntry(id: String): List<SavedGitToken> {
        val updated = getSavedTokens().filterNot { it.id == id }
        persistTokens(updated)
        return updated
    }

    private fun persistTokens(list: List<SavedGitToken>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("username", item.username)
                    put("token", item.token)
                }
            )
        }
        prefs.edit().putString(KEY_SAVED_TOKENS, arr.toString()).apply()
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
            val resp = apiRequest(
                "GET",
                "https://api.github.com/user/repos?affiliation=owner,collaborator,organization_member&per_page=100&sort=updated",
                pat,
                null
            )
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

    suspend fun inspectChanges(
        pat: String,
        ownerRepoInput: String,
        branchInput: String,
        localFiles: List<PushFileEntry>
    ): Result<GitDiffSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = parseOwnerRepo(ownerRepoInput)
            val branch = branchInput.trim().ifBlank { "main" }
            require(repo.contains("/")) { "仓库格式应为 owner/repo" }
            require(pat.isNotBlank()) { "请填写 GitHub Token" }
            require(localFiles.isNotEmpty()) { "请先选择要上传的文件" }

            val refResp = apiRequest("GET", "https://api.github.com/repos/$repo/git/ref/heads/$branch", pat, null)
            val headSha = JSONObject(refResp).getJSONObject("object").getString("sha")

            val commitResp = apiRequest("GET", "https://api.github.com/repos/$repo/git/commits/$headSha", pat, null)
            val baseTreeSha = JSONObject(commitResp).getJSONObject("tree").getString("sha")

            val treeResp = apiRequest("GET", "https://api.github.com/repos/$repo/git/trees/$baseTreeSha?recursive=1", pat, null)
            val treeArr = JSONObject(treeResp).optJSONArray("tree") ?: JSONArray()
            val remoteBlobs = HashMap<String, String>(treeArr.length())
            for (i in 0 until treeArr.length()) {
                val item = treeArr.optJSONObject(i) ?: continue
                if (item.optString("type") == "blob") {
                    remoteBlobs[item.optString("path")] = item.optString("sha")
                }
            }

            val added = ArrayList<PushFileEntry>()
            val modified = ArrayList<PushFileEntry>()
            var unchanged = 0
            val localPaths = HashSet<String>(localFiles.size)

            for (file in localFiles) {
                localPaths.add(file.relativePath)
                val remoteSha = remoteBlobs[file.relativePath]
                if (remoteSha == null) {
                    added.add(file)
                } else {
                    val localSha = computeGitBlobSha1(file.bytes)
                    if (localSha != remoteSha) {
                        modified.add(file)
                    } else {
                        unchanged++
                    }
                }
            }

            val deleted = remoteBlobs.keys.filter { it !in localPaths }.sorted()
            GitDiffSummary(
                headSha = headSha,
                baseTreeSha = baseTreeSha,
                addedFiles = added,
                modifiedFiles = modified,
                deletedPaths = deleted,
                unchangedCount = unchanged
            )
        }
    }

    suspend fun pushDiff(
        pat: String,
        ownerRepoInput: String,
        branchInput: String,
        commitMessageInput: String,
        diff: GitDiffSummary,
        syncDeletions: Boolean,
        onProgress: (done: Int, total: Int, currentPath: String) -> Unit
    ): Result<PushGitResult> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = parseOwnerRepo(ownerRepoInput)
            val branch = branchInput.trim().ifBlank { "main" }
            val message = commitMessageInput.trim().ifBlank { "Sync project files via PushGit" }
            val filesToUpload = diff.uploadFiles
            require(filesToUpload.isNotEmpty() || (syncDeletions && diff.deletedPaths.isNotEmpty())) {
                "没有需要推送的变动文件"
            }

            val treeArray = JSONArray()
            val total = filesToUpload.size
            filesToUpload.forEachIndexed { idx, file ->
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

            if (syncDeletions) {
                diff.deletedPaths.forEach { delPath ->
                    treeArray.put(
                        JSONObject().apply {
                            put("path", delPath)
                            put("mode", "100644")
                            put("type", "blob")
                            put("sha", JSONObject.NULL)
                        }
                    )
                }
            }

            onProgress(total, total, "正在提交...")

            val treeBody = JSONObject().apply {
                put("base_tree", diff.baseTreeSha)
                put("tree", treeArray)
            }.toString()
            val newTreeResp = apiRequest("POST", "https://api.github.com/repos/$repo/git/trees", pat, treeBody)
            val newTreeSha = JSONObject(newTreeResp).getString("sha")

            val commitBody = JSONObject().apply {
                put("message", message)
                put("tree", newTreeSha)
                put("parents", JSONArray().put(diff.headSha))
            }.toString()
            val newCommitResp = apiRequest("POST", "https://api.github.com/repos/$repo/git/commits", pat, commitBody)
            val newCommitSha = JSONObject(newCommitResp).getString("sha")

            val patchBody = JSONObject().apply {
                put("sha", newCommitSha)
                put("force", false)
            }.toString()
            apiRequest("PATCH", "https://api.github.com/repos/$repo/git/refs/heads/$branch", pat, patchBody)

            PushGitResult(
                commitSha = newCommitSha,
                fileCount = filesToUpload.size + if (syncDeletions) diff.deletedPaths.size else 0,
                commitUrl = "https://github.com/$repo/commit/$newCommitSha"
            )
        }
    }

    private fun computeGitBlobSha1(bytes: ByteArray): String {
        val header = "blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-1")
        md.update(header)
        md.update(bytes)
        return md.digest().joinToString("") { "%02x".format(it) }
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
        private const val KEY_SAVED_TOKENS = "saved_tokens"
    }
}
