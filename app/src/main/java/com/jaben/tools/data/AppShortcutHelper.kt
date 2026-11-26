package com.jaben.tools.data

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.jaben.tools.MainActivity
import com.jaben.tools.R

object AppShortcutHelper {
    const val EXTRA_SHORTCUT_ROUTE = "shortcut_route"

    fun updateDynamicShortcuts(context: Context, toolOrder: List<String>) {
        val toolMeta = mapOf(
            "browser" to Triple("轻量浏览器", "tool/browser", R.drawable.ic_launcher_monochrome),
            "base64" to Triple("Base64编码", "tool/base64", R.drawable.ic_launcher_monochrome),
            "qr" to Triple("二维码", "tool/qr", R.drawable.ic_launcher_monochrome)
        )

        val orderedIds = (toolOrder + listOf("browser", "base64", "qr"))
            .distinct()
            .filter { toolMeta.containsKey(it) }
            .take(3)

        val shortcuts = mutableListOf<ShortcutInfoCompat>()

        orderedIds.forEachIndexed { idx, id ->
            val (label, route, iconRes) = toolMeta.getValue(id)
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_SHORTCUT_ROUTE, route)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            shortcuts.add(
                ShortcutInfoCompat.Builder(context, "tool_$id")
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIcon(IconCompat.createWithResource(context, iconRes))
                    .setRank(idx)
                    .setIntent(intent)
                    .build()
            )
        }

        // 4th shortcut: Scan QR Code
        val scanIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_SHORTCUT_ROUTE, "tool/qr_scanner")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        shortcuts.add(
            ShortcutInfoCompat.Builder(context, "shortcut_qr_scanner")
                .setShortLabel("扫描二维码")
                .setLongLabel("扫描二维码")
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_launcher_monochrome))
                .setRank(3)
                .setIntent(scanIntent)
                .build()
        )

        runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }
}
