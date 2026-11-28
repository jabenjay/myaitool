package com.jaben.tools.tools.git

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
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

data class WorkflowInfo(
    val id: Long,
    val name: String,
    val path: String
)

data class WorkflowInputField(
    val key: String,
    val description: String,
    val required: Boolean,
    val defaultValue: String,
    val options: List<String> = emptyList()
)

data class RepoContentItem(
    val name: String,
    val path: String,
    val type: String,
    val sha: String,
    val size: Long
)

data class WorkflowRunInfo(
    val id: Long,
    val runNumber: Int,
    val name: String,
    val status: String,
    val conclusion: String,
    val branch: String,
    val createdAt: String,
    val htmlUrl: String
)

class PushGitEngine(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSavedToken(): String = prefs.getString(KEY_PAT, "").orEmpty()
    fun loadSavedRepo(): String = prefs.getString(KEY_REPO, "jabenjay/myaitool").orEmpty()
    fun loadSavedBranch(): String = prefs.getString(KEY_BRANCH, "main").orEmpty()
    fun loadSavedMessage(): String = prefs.getString(KEY_MSG, "Sync project files via PushGit").orEmpty()
    fun loadLastPickerMode(): String = prefs.getString(KEY_PICKER_MODE, "zip").orEmpty().ifBlank { "zip" }
    fun saveLastPickerMode(mode: String) {
        prefs.edit().putString(KEY_PICKER_MODE, mode).apply()
    }

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
        overrideFiles: List<PushFileEntry>? = null,
        onProgress: (done: Int, total: Int, currentPath: String) -> Unit
    ): Result<PushGitResult> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = parseOwnerRepo(ownerRepoInput)
            val branch = branchInput.trim().ifBlank { "main" }
            val message = commitMessageInput.trim().ifBlank { "Sync project files via PushGit" }
            val filesToUpload = overrideFiles ?: diff.uploadFiles
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

    suspend fun listWorkflows(pat: String, ownerRepoInput: String): Result<List<WorkflowInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = parseOwnerRepo(ownerRepoInput)
            val resp = apiRequest("GET", "https://api.github.com/repos/$repo/actions/workflows", pat, null)
            val arr = JSONObject(resp).optJSONArray("workflows") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (obj.optString("state") == "active") {
                        add(
                            WorkflowInfo(
                                id = obj.optLong("id"),
                                name = obj.optString("name"),
                                path = obj.optString("path")
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun fetchWorkflowInputs(
        pat: String,
        ownerRepoInput: String,
        workflowPath: String,
        branch: String
    ): Result<List<WorkflowInputField>> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = parseOwnerRepo(ownerRepoInput)
            val resp = apiRequest("GET", "https://api.github.com/repos/$repo/contents/$workflowPath?ref=${branch.trim()}", pat, null)
            val b64 = JSONObject(resp).optString("content").replace("\n", "")
            val yamlText = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            parseWorkflowDispatchInputs(yamlText)
        }
    }

    private fun parseWorkflowDispatchInputs(yamlText: String): List<WorkflowInputField> {
        val lines = yamlText.lines()
        val result = ArrayList<WorkflowInputField>()
        var inDispatch = false
        var inInputs = false
        var inputsIndent = -1
        var currentKey: String? = null
        var keyIndent = -1
        var desc = ""
        var req = false
        var defVal = ""
        val opts = ArrayList<String>()
        var inOptions = false
        var optionsIndent = -1

        fun flushCurrent() {
            val k = currentKey ?: return
            result.add(WorkflowInputField(k, desc, req, defVal, opts.toList()))
            currentKey = null
            desc = ""
            req = false
            defVal = ""
            opts.clear()
            inOptions = false
            optionsIndent = -1
        }

        for (raw in lines) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val indent = raw.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }

            if (trimmed.startsWith("workflow_dispatch:")) {
                inDispatch = true
                inInputs = false
                continue
            }
            if (!inDispatch) continue

            if (trimmed.startsWith("inputs:") && !inInputs) {
                inInputs = true
                inputsIndent = indent
                continue
            }
            if (inInputs && indent <= inputsIndent) {
                flushCurrent()
                break
            }
            if (!inInputs) continue

            if (keyIndent == -1 || indent == keyIndent) {
                if (trimmed.endsWith(":")) {
                    flushCurrent()
                    keyIndent = indent
                    currentKey = trimmed.removeSuffix(":").trim()
                }
            } else if (indent > keyIndent && currentKey != null) {
                if (inOptions) {
                    if (indent > optionsIndent && trimmed.startsWith("-")) {
                        val opt = trimmed.removePrefix("-").trim().trim('\'', '"')
                        if (opt.isNotEmpty()) opts.add(opt)
                        continue
                    } else {
                        inOptions = false
                    }
                }
                when {
                    trimmed.startsWith("description:") -> {
                        desc = trimmed.removePrefix("description:").trim().trim('\'', '"')
                    }
                    trimmed.startsWith("required:") -> {
                        req = trimmed.removePrefix("required:").trim().equals("true", ignoreCase = true)
                    }
                    trimmed.startsWith("default:") -> {
                        defVal = trimmed.removePrefix("default:").trim().trim('\'', '"')
                    }
                    trimmed.startsWith("options:") -> {
                        val inline = trimmed.removePrefix("options:").trim()
                        if (inline.startsWith("[") && inline.endsWith("]")) {
                            inline.removeSurrounding("[", "]")
                                .split(",")
                                .map { it.trim().trim('\'', '"') }
                                .filter { it.isNotEmpty() }
                                .forEach { opts.add(it) }
                            inOptions = false
                        } else {
                            inOptions = true
                            optionsIndent = indent
                        }
                    }
                }
            }
        }
        flushCurrent()
        return result
    }

    suspend fun dispatchWorkflow(
        pat: String,
        ownerRepoInput: String,
        workflowId: Long,
        branch: String,
        inputs: Map<String, String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = parseOwnerRepo(ownerRepoInput)
            val inputsJson = JSONObject()
            inputs.forEach { (k, v) -> inputsJson.put(k, v) }
            val body = JSONObject().apply {
                put("ref", branch.trim().ifBlank { "main" })
                put("inputs", inputsJson)
            }.toString()
            apiRequest(
                "POST",
                "https://api.github.com/repos/$repo/actions/workflows/$workflowId/dispatches",
                pat,
                body
            )
            Unit
        }
    }

    suspend fun fetchChangelogContent(
        pat: String,
        ownerRepoInput: String,
        branch: String,
        fileName: String = "CHANGELOG_ANDROID.md"
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val repo = parseOwnerRepo(ownerRepoInput)
            val resp = apiRequest("GET", "https://api.github.com/repos/$repo/contents/$fileName?ref=${branch.trim()}", pat, null)
            val b64 = JSONObject(resp).optString("content").replace("\n", "")
            val raw = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            raw.lines()
                .dropWhile { val t = it.trim(); t.isEmpty() || t.startsWith("# ") }
                .joinToString("\n")
                .trim()
                .ifBlank { null }
        }.getOrNull()
    }

    suspend fun fetchNextReleaseVersion(pat: String, ownerRepoInput: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val repo = parseOwnerRepo(ownerRepoInput)
            val latestTag = runCatching {
                val resp = apiRequest("GET", "https://api.github.com/repos/$repo/releases/latest", pat, null)
                JSONObject(resp).optString("tag_name")
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: run {
                val tagsResp = apiRequest("GET", "https://api.github.com/repos/$repo/tags?per_page=1", pat, null)
                JSONArray(tagsResp).optJSONObject(0)?.optString("name").orEmpty()
            }
            val clean = latestTag.trim().removePrefix("v").removePrefix("V")
            val parts = clean.split(".").mapNotNull { it.toIntOrNull() }.toMutableList()
            if (parts.isEmpty()) return@runCatching null
            while (parts.size < 3) parts.add(0)
            parts[parts.lastIndex] = parts.last() + 1
            parts.joinToString(".")
        }.getOrNull()
    }

    suspend fun cancelWorkflowRun(
        pat: String,
        ownerRepoInput: String,
        runId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = parseOwnerRepo(ownerRepoInput)
            apiRequest("POST", "https://api.github.com/repos/$repo/actions/runs/$runId/cancel", pat, "{}")
            Unit
        }
    }

    suspend fun listWorkflowRuns(
        pat: String,
        ownerRepoInput: String,
        page: Int = 1,
        perPage: Int = 15
    ): Result<List<WorkflowRunInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = parseOwnerRepo(ownerRepoInput)
            val resp = apiRequest("GET", "https://api.github.com/repos/$repo/actions/runs?per_page=$perPage&page=$page", pat, null)
            val arr = JSONObject(resp).optJSONArray("workflow_runs") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        WorkflowRunInfo(
                            id = obj.optLong("id"),
                            runNumber = obj.optInt("run_number"),
                            name = obj.optString("name"),
                            status = obj.optString("status"),
                            conclusion = obj.optString("conclusion"),
                            branch = obj.optString("head_branch"),
                            createdAt = obj.optString("created_at"),
                            htmlUrl = obj.optString("html_url")
                        )
                    )
                }
            }
        }
    }

    suspend fun fetchFailedRunLog(
        pat: String,
        ownerRepoInput: String,
        runId: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = parseOwnerRepo(ownerRepoInput)
            val jobsResp = apiRequest("GET", "https://api.github.com/repos/$repo/actions/runs/$runId/jobs", pat, null)
            val jobsArr = JSONObject(jobsResp).optJSONArray("jobs") ?: JSONArray()
            var failedJobId: Long? = null
            for (i in 0 until jobsArr.length()) {
                val job = jobsArr.optJSONObject(i) ?: continue
                val c = job.optString("conclusion")
                if (c == "failure" || c == "cancelled" || c == "startup_failure" || c == "timed_out") {
                    failedJobId = job.optLong("id")
                    break
                }
            }
            val jobId = failedJobId ?: (jobsArr.optJSONObject(0)?.optLong("id") ?: 0L)
            require(jobId > 0L) { "未找到失败的任务日志" }

            // GitHub returns 302 redirect to a pre-signed Azure blob URL (must not send Authorization header to Azure)
            val conn = (URL("https://api.github.com/repos/$repo/actions/jobs/$jobId/logs").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Authorization", "Bearer ${pat.trim()}")
                setRequestProperty("User-Agent", "Jools-PushGit/1.0")
            }
            val fullLog = try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val redirectUrl = conn.getHeaderField("Location")
                        ?: throw IllegalStateException("日志重定向地址为空")
                    val blobConn = (URL(redirectUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 30000
                    }
                    try {
                        blobConn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } finally {
                        blobConn.disconnect()
                    }
                } else if (code in 200..299) {
                    conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else {
                    throw IllegalStateException("获取日志失败: HTTP $code")
                }
            } finally {
                conn.disconnect()
            }
            fullLog
        }
    }


    private fun encodeGitHubPath(path: String): String {
        return path.trim('/').split('/').filter { it.isNotEmpty() }.joinToString("/") { seg ->
            URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
        }
    }

    suspend fun listRepoContents(
        pat: String,
        ownerRepoInput: String,
        branch: String,
        path: String
    ): Result<List<RepoContentItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = parseOwnerRepo(ownerRepoInput)
            val encodedPath = encodeGitHubPath(path)
            val ref = URLEncoder.encode(branch.trim().ifBlank { "main" }, "UTF-8")
            val url = if (encodedPath.isEmpty()) {
                "https://api.github.com/repos/$repo/contents?ref=$ref"
            } else {
                "https://api.github.com/repos/$repo/contents/$encodedPath?ref=$ref"
            }
            val resp = apiRequest("GET", url, pat, null)
            val arr = JSONArray(resp)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        RepoContentItem(
                            name = obj.optString("name"),
                            path = obj.optString("path"),
                            type = obj.optString("type"),
                            sha = obj.optString("sha"),
                            size = obj.optLong("size")
                        )
                    )
                }
            }.sortedWith(compareBy<RepoContentItem> { it.type != "dir" }.thenBy { it.name.lowercase() })
        }
    }


    suspend fun fetchDirectoryLastModifiedMap(
        pat: String,
        ownerRepoInput: String,
        branch: String,
        paths: List<String>
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext emptyMap()
        val repo = parseOwnerRepo(ownerRepoInput)
        val ref = URLEncoder.encode(branch.trim().ifBlank { "main" }, "UTF-8")
        val sem = Semaphore(6)
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).apply { timeZone = TimeZone.getDefault() }
        coroutineScope {
            paths.map { itemPath ->
                async {
                    sem.withPermit {
                        val dateStr = runCatching {
                            val encoded = URLEncoder.encode(itemPath, "UTF-8")
                            val resp = apiRequest(
                                "GET",
                                "https://api.github.com/repos/$repo/commits?sha=$ref&path=$encoded&per_page=1",
                                pat,
                                null
                            )
                            val arr = JSONArray(resp)
                            val iso = arr.optJSONObject(0)
                                ?.optJSONObject("commit")
                                ?.optJSONObject("committer")
                                ?.optString("date")
                                .orEmpty()
                            if (iso.isNotBlank()) {
                                val parsed = synchronized(parser) { parser.parse(iso) }
                                if (parsed != null) synchronized(formatter) { formatter.format(parsed) } else ""
                            } else ""
                        }.getOrDefault("")
                        itemPath to dateStr
                    }
                }
            }.awaitAll().filter { it.second.isNotBlank() }.toMap()
        }
    }

    suspend fun fetchRepoFileText(
        pat: String,
        ownerRepoInput: String,
        branch: String,
        path: String
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = parseOwnerRepo(ownerRepoInput)
            val encodedPath = encodeGitHubPath(path)
            val ref = URLEncoder.encode(branch.trim().ifBlank { "main" }, "UTF-8")
            val resp = apiRequest("GET", "https://api.github.com/repos/$repo/contents/$encodedPath?ref=$ref", pat, null)
            val obj = JSONObject(resp)
            val size = obj.optLong("size")
            require(size <= 512L * 1024L) { "文件过大 (>512KB)，暂不支持在线编辑" }
            val sha = obj.optString("sha")
            val b64 = obj.optString("content").replace("\n", "")
            val text = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
            sha to text
        }
    }

    suspend fun updateRepoFile(
        pat: String,
        ownerRepoInput: String,
        branch: String,
        path: String,
        sha: String?,
        content: String,
        message: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = parseOwnerRepo(ownerRepoInput)
            val encodedPath = encodeGitHubPath(path)
            val encodedContent = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            val body = JSONObject().apply {
                put("message", message.trim().ifBlank { "Update $path via PushGit" })
                put("content", encodedContent)
                put("branch", branch.trim().ifBlank { "main" })
                if (!sha.isNullOrBlank()) put("sha", sha)
            }.toString()
            val resp = apiRequest("PUT", "https://api.github.com/repos/$repo/contents/$encodedPath", pat, body)
            JSONObject(resp).optJSONObject("commit")?.optString("sha").orEmpty().ifBlank { "committed" }
        }
    }

    suspend fun deleteRepoFile(
        pat: String,
        ownerRepoInput: String,
        branch: String,
        path: String,
        sha: String,
        message: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val repo = parseOwnerRepo(ownerRepoInput)
            val encodedPath = encodeGitHubPath(path)
            val body = JSONObject().apply {
                put("message", message.trim().ifBlank { "Delete $path via PushGit" })
                put("sha", sha)
                put("branch", branch.trim().ifBlank { "main" })
            }.toString()
            apiRequest("DELETE", "https://api.github.com/repos/$repo/contents/$encodedPath", pat, body)
            Unit
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
        private const val KEY_PICKER_MODE = "last_picker_mode"
    }
}
