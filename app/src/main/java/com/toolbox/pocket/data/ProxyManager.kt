package com.toolbox.pocket.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class ProxyProtocol(val label: String, val scheme: String?) {
    VLESS("VLESS (Xray/sing-box)", null),
    VMESS("VMess (Xray/sing-box)", null),
    SOCKS5("SOCKS5", "socks5"),
    SOCKS4("SOCKS4", "socks4"),
    HTTP("HTTP", "http"),
    MTPROXY("MTProxy", null)
}

data class ProxyNode(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: ProxyProtocol,
    val host: String,
    val port: Int,
    val uuidOrUser: String = "",
    val passwordOrSecret: String = "",
    val extraParams: String = ""
) {
    fun summary(): String = "${protocol.label} · $host:$port"
}

data class ProxyState(
    val enabled: Boolean = false,
    val selectedId: String? = null,
    val nodes: List<ProxyNode> = emptyList(),
    val statusMessage: String = "未启用代理（直连）"
)

class ProxyManager(context: Context) {
    private val prefs = context.getSharedPreferences("toolbox_proxy", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<ProxyState> = _state.asStateFlow()

    init {
        applyProxyOverride(_state.value)
    }

    private fun loadState(): ProxyState {
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val selectedId = prefs.getString(KEY_SELECTED_ID, null)
        val rawJson = prefs.getString(KEY_NODES, "[]") ?: "[]"
        val nodes = runCatching {
            val arr = JSONArray(rawJson)
            List(arr.length()) { idx ->
                val obj = arr.getJSONObject(idx)
                ProxyNode(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    protocol = runCatching {
                        ProxyProtocol.valueOf(obj.getString("protocol"))
                    }.getOrDefault(ProxyProtocol.SOCKS5),
                    host = obj.getString("host"),
                    port = obj.getInt("port"),
                    uuidOrUser = obj.optString("username", ""),
                    passwordOrSecret = obj.optString("secret", ""),
                    extraParams = obj.optString("extra", "")
                )
            }
        }.getOrDefault(emptyList())

        return ProxyState(
            enabled = enabled,
            selectedId = selectedId,
            nodes = nodes
        )
    }

    private fun persist(state: ProxyState) {
        val arr = JSONArray()
        state.nodes.forEach { node ->
            arr.put(
                JSONObject().apply {
                    put("id", node.id)
                    put("name", node.name)
                    put("protocol", node.protocol.name)
                    put("host", node.host)
                    put("port", node.port)
                    put("username", node.uuidOrUser)
                    put("secret", node.passwordOrSecret)
                    put("extra", node.extraParams)
                }
            )
        }
        prefs.edit()
            .putBoolean(KEY_ENABLED, state.enabled)
            .putString(KEY_SELECTED_ID, state.selectedId)
            .putString(KEY_NODES, arr.toString())
            .apply()
    }

    fun addNode(node: ProxyNode) {
        val updatedNodes = _state.value.nodes + node
        val next = _state.value.copy(
            nodes = updatedNodes,
            selectedId = _state.value.selectedId ?: node.id
        )
        persist(next)
        applyProxyOverride(next)
    }

    fun importShareLink(link: String): ProxyNode? {
        val trimmed = link.trim()
        return when {
            trimmed.startsWith("vless://", ignoreCase = true) -> parseVlessUri(trimmed)
            trimmed.startsWith("vmess://", ignoreCase = true) -> parseVmessUri(trimmed)
            else -> null
        }?.also { addNode(it) }
    }

    private fun parseVlessUri(uriStr: String): ProxyNode? = runCatching {
        val uri = Uri.parse(uriStr)
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val uuid = uri.userInfo.orEmpty()
        val remark = uri.fragment?.takeIf { it.isNotBlank() } ?: "VLESS $host"
        ProxyNode(
            name = Uri.decode(remark),
            protocol = ProxyProtocol.VLESS,
            host = host,
            port = port,
            uuidOrUser = uuid,
            extraParams = uri.query.orEmpty()
        )
    }.getOrNull()

    private fun parseVmessUri(uriStr: String): ProxyNode? = runCatching {
        val b64 = uriStr.removePrefix("vmess://").trim()
        val jsonText = String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8)
        val obj = JSONObject(jsonText)
        val host = obj.getString("add")
        val port = obj.optInt("port", 443)
        val uuid = obj.optString("id", "")
        val ps = obj.optString("ps", "VMess $host").ifBlank { "VMess $host" }
        ProxyNode(
            name = ps,
            protocol = ProxyProtocol.VMESS,
            host = host,
            port = port,
            uuidOrUser = uuid,
            extraParams = obj.optString("net", "tcp")
        )
    }.getOrNull()

    fun deleteNode(id: String) {
        val updatedNodes = _state.value.nodes.filterNot { it.id == id }
        val nextSelected = if (_state.value.selectedId == id) {
            updatedNodes.firstOrNull()?.id
        } else {
            _state.value.selectedId
        }
        val nextEnabled = _state.value.enabled && nextSelected != null
        val next = _state.value.copy(
            nodes = updatedNodes,
            selectedId = nextSelected,
            enabled = nextEnabled
        )
        persist(next)
        applyProxyOverride(next)
    }

    fun selectNode(id: String) {
        val next = _state.value.copy(selectedId = id)
        persist(next)
        applyProxyOverride(next)
    }

    fun setEnabled(enabled: Boolean) {
        val next = _state.value.copy(enabled = enabled)
        persist(next)
        applyProxyOverride(next)
    }

    private fun applyProxyOverride(state: ProxyState) {
        val activeNode = state.nodes.find { it.id == state.selectedId }
        val supportsOverride = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)

        if (!state.enabled || activeNode == null) {
            if (supportsOverride) {
                runCatching { ProxyController.getInstance().clearProxyOverride(Runnable::run) {} }
            }
            _state.value = state.copy(statusMessage = "未启用代理（直连）")
            return
        }

        if (activeNode.protocol == ProxyProtocol.VLESS ||
            activeNode.protocol == ProxyProtocol.VMESS ||
            activeNode.protocol == ProxyProtocol.MTPROXY
        ) {
            if (supportsOverride) {
                runCatching { ProxyController.getInstance().clearProxyOverride(Runnable::run) {} }
            }
            _state.value = state.copy(
                statusMessage = "已保存 ${activeNode.protocol.label} 节点；当前未内置 Xray/sing-box 原生内核，仅 SOCKS/HTTP 可直接代理内置浏览器"
            )
            return
        }

        if (!supportsOverride) {
            _state.value = state.copy(statusMessage = "当前系统 WebView 不支持应用内代理覆盖")
            return
        }

        val scheme = activeNode.protocol.scheme ?: "socks5"
        val proxyUrl = "$scheme://${activeNode.host}:${activeNode.port}"
        // Bypass common domestic/LAN domains by default so non-GFW traffic goes direct
        val config = ProxyConfig.Builder()
            .addProxyRule(proxyUrl)
            .addBypassRule("*.cn")
            .addBypassRule("*.baidu.com")
            .addBypassRule("*.qq.com")
            .addBypassRule("*.taobao.com")
            .addBypassRule("*.jd.com")
            .addBypassRule("*.163.com")
            .addBypassRule("127.0.0.1")
            .addBypassRule("192.168.*")
            .addBypassRule("10.*")
            .build()

        val applied = runCatching {
            ProxyController.getInstance().setProxyOverride(config, Runnable::run) {}
            true
        }.getOrDefault(false)

        _state.value = state.copy(
            statusMessage = if (applied) {
                "已启用：${activeNode.name}（仅代理 GFW 封锁目标）"
            } else {
                "代理配置失败：请检查地址与端口"
            }
        )
    }

    private companion object {
        const val KEY_ENABLED = "proxy_enabled"
        const val KEY_SELECTED_ID = "proxy_selected_id"
        const val KEY_NODES = "proxy_nodes"
    }
}
