package com.jaben.tools.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.jaben.tools.MainActivity
import com.jaben.tools.R

object AppShortcutHelper {
    const val EXTRA_SHORTCUT_ROUTE = "shortcut_route"

    fun extractRoute(intent: Intent?): String? {
        if (intent == null) return null
        val fromExtra = intent.getStringExtra(EXTRA_SHORTCUT_ROUTE)
        if (!fromExtra.isNullOrBlank()) return fromExtra
        val data = intent.data
        if (data != null && data.scheme == "jools" && data.host == "shortcut") {
            return data.path?.removePrefix("/")?.takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun buildShortcutIntent(context: Context, route: String): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("jools://shortcut/$route")
            putExtra(EXTRA_SHORTCUT_ROUTE, route)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }

    fun updateDynamicShortcuts(context: Context, toolOrder: List<String>) {
        val toolMeta = mapOf(
            "browser" to Triple("轻量浏览器", "tool/browser", R.drawable.ic_shortcut_browser),
            "base64" to Triple("Base64编码", "tool/base64", R.drawable.ic_shortcut_base64),
            "qr" to Triple("二维码工具", "tool/qr", R.drawable.ic_shortcut_qr),
            "format_factory" to Triple("格式工厂", "tool/format_factory", R.drawable.ic_shortcut_format)
        )

        val orderedIds = (toolOrder + listOf("browser", "base64", "qr", "format_factory"))
            .distinct()
            .filter { toolMeta.containsKey(it) }
            .take(3)

        val shortcuts = mutableListOf<ShortcutInfoCompat>()

        // 1st shortcut (fixed): Scan QR Code
        shortcuts.add(
            ShortcutInfoCompat.Builder(context, "shortcut_qr_scanner")
                .setShortLabel("扫描二维码")
                .setLongLabel("扫描二维码")
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_scanner))
                .setRank(0)
                .setIntent(buildShortcutIntent(context, "tool/qr_scanner"))
                .build()
        )

        // 2nd - 4th shortcuts: Top 3 tools by home order
        orderedIds.forEachIndexed { idx, id ->
            val (label, route, iconRes) = toolMeta.getValue(id)
            shortcuts.add(
                ShortcutInfoCompat.Builder(context, "tool_$id")
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIcon(IconCompat.createWithResource(context, iconRes))
                    .setRank(idx + 1)
                    .setIntent(buildShortcutIntent(context, route))
                    .build()
            )
        }

        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }
}
