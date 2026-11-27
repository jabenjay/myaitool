package com.jaben.tools.tools.qr

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast

object QrRouter {

    /**
     * Handles a scanned QR string:
     * - Custom app schemes (alipays://, weixin://, wxp://, taobao://, etc.) -> launch target app directly
     * - Known app HTTPS URLs (qr.alipay.com, u.wechat.com, etc.) or native app links (API 30+ FLAG_ACTIVITY_REQUIRE_NON_BROWSER) -> launch target app directly
     * - Everything else -> open inside built-in Lightweight Browser
     */
    fun openScannedResult(
        context: Context,
        scanned: String,
        openInBuiltInBrowser: (String) -> Unit
    ) {
        val text = scanned.trim()
        if (text.isEmpty()) return

        val lower = text.lowercase()

        // 1. Alipay HTTPS QR -> launch Alipay directly via alipays:// scheme
        if (lower.startsWith("https://qr.alipay.com/") || lower.startsWith("http://qr.alipay.com/")) {
            val alipayUri = Uri.parse("alipays://platformapi/startapp?saId=10000007&qrcode=${Uri.encode(text)}")
            if (tryStartActivity(context, Intent(Intent.ACTION_VIEW, alipayUri))) {
                return
            }
        }

        // 2. Non-HTTP custom app scheme (alipays://, weixin://, wxp://, mqqapi://, taobao://, etc.)
        if (!lower.startsWith("http://") && !lower.startsWith("https://") && text.contains("://")) {
            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(text))
            if (tryStartActivity(context, appIntent)) {
                return
            } else {
                Toast.makeText(context, "改用浏览器打开", Toast.LENGTH_SHORT).show()
                openInBuiltInBrowser(text)
                return
            }
        }

        // 3. HTTP/HTTPS URL: on Android 11+, try native non-browser app handler first (e.g. WeChat, QQ, Bilibili, Taobao)
        if ((lower.startsWith("http://") || lower.startsWith("https://")) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val nonBrowserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(text)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
            }
            val openedNative = try {
                context.startActivity(nonBrowserIntent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            } catch (_: Exception) {
                false
            }
            if (openedNative) return
        }

        // 4. Fallback: open inside built-in Lightweight Browser
        openInBuiltInBrowser(text)
    }

    private fun tryStartActivity(context: Context, intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
