package com.jaben.tools.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

enum class NoticeType {
    USAGE_NOTICE,
    ANNOUNCEMENT
}

data class PendingNoticeItem(
    val type: NoticeType,
    val title: String,
    val content: String,
    val contentHash: String
)

object RemoteNoticeManager {
    private const val PREFS_NAME = "jools_remote_notices_prefs"
    private const val KEY_NOTICE_HASH = "ack_usage_notice_hash"
    private const val KEY_ANNOUNCEMENT_HASH = "ack_announcement_hash"

    private const val RAW_NOTICE_URL = "https://raw.githubusercontent.com/jabenjay/myaitool/main/NOTICE.md"
    private const val RAW_ANNOUNCEMENT_URL = "https://raw.githubusercontent.com/jabenjay/myaitool/main/ANNOUNCEMENT.md"

    private const val BUNDLED_DEFAULT_NOTICE = """# Jools 使用须知

在使用本应用前，请仔细阅读并遵守以下条款：

## 1. 非商业用途声明
- 本应用为开源免费工具软件，严禁任何形式的商业售卖、付费代下、捆绑收费或盈利性分发。

## 2. 分享与二次构建规范
- 欢迎在遵守开源协议的前提下进行技术交流、分享或基于源码进行二次构建。
- 任何形式的分享、转载或二次发布，均须在显眼位置注明出处、原仓库地址（github.com/jabenjay/myaitool）及原作者信息，不得隐瞒、篡改或冒充原作者。

## 3. 免责声明
- 本应用按“现状（AS IS）”提供，不对其在所有设备或网络环境下的绝对可用性、准确性作任何明示或暗示的担保。
- 应用内提供的浏览器、媒体播放、网络规则订阅及 Git 推送等工具仅作为辅助手段，请妥善保管个人账号、Token 凭据与本地文件；因使用本应用、第三方网页内容或操作失误导致的任何直接或间接损失，开发者不承担相应责任。

## 4. 条款更新
- 如本须知内容发生变更，应用将在冷启动时自动弹出最新版本，确认后方可继续使用。"""

    suspend fun checkPendingNotices(context: Context): List<PendingNoticeItem> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedNoticeHash = prefs.getString(KEY_NOTICE_HASH, "").orEmpty()
        val savedAnnHash = prefs.getString(KEY_ANNOUNCEMENT_HASH, "").orEmpty()

        val result = ArrayList<PendingNoticeItem>(2)

        // 1. Check NOTICE.md (always show on first launch even if offline via bundled fallback)
        val remoteNotice = fetchTextWithMirror(context, RAW_NOTICE_URL)
        val noticeText = remoteNotice ?: if (savedNoticeHash.isEmpty()) BUNDLED_DEFAULT_NOTICE else null
        if (!noticeText.isNullOrBlank()) {
            val hash = sha256(noticeText)
            if (hash != savedNoticeHash) {
                result.add(
                    PendingNoticeItem(
                        type = NoticeType.USAGE_NOTICE,
                        title = "使用须知",
                        content = noticeText,
                        contentHash = hash
                    )
                )
            }
        }

        // 2. Check ANNOUNCEMENT.md
        val remoteAnn = fetchTextWithMirror(context, RAW_ANNOUNCEMENT_URL)
        if (!remoteAnn.isNullOrBlank()) {
            val hash = sha256(remoteAnn)
            if (hash != savedAnnHash) {
                result.add(
                    PendingNoticeItem(
                        type = NoticeType.ANNOUNCEMENT,
                        title = "应用公告",
                        content = remoteAnn,
                        contentHash = hash
                    )
                )
            }
        }

        result
    }

    fun markAcknowledged(context: Context, item: PendingNoticeItem) {
        val key = when (item.type) {
            NoticeType.USAGE_NOTICE -> KEY_NOTICE_HASH
            NoticeType.ANNOUNCEMENT -> KEY_ANNOUNCEMENT_HASH
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key, item.contentHash)
            .apply()
    }

    private fun fetchTextWithMirror(context: Context, rawUrl: String): String? {
        val candidates = GitHubMirrorHelper.buildCandidateUrls(context, rawUrl)
        for (urlStr in candidates) {
            val res = runCatching {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Jools-Notice/1.0")
                }
                try {
                    if (conn.responseCode in 200..299) {
                        conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().trim() }
                    } else null
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
            if (!res.isNullOrBlank()) return res
        }
        return null
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.trim().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
