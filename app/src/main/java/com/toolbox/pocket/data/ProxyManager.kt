package com.toolbox.pocket.data

import android.content.Context
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ProxyProtocol(val label: String, val scheme: String?) {
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
    val username: String = "",
    val passwordOrSecret: String = ""
) {
    fun summary(): String = "${protocol.label} · $host:$port"
}

data class ProxyState(
    val enabled: Boolean = false,
    val selectedId: String? = null,
    val nodes: List<ProxyNode> = emptyList(),
    val statusMessage: String = "未启用代理"
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
                    username = obj.optString("username", ""),
                    passwordOrSecret = obj.optString("secret", "")
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
                    put("username", node.username)
                    put("secret", node.passwordOrSecret)
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
                runCatching {
                    ProxyController.getInstance().clearProxyOverride(Runnable::run) {}
                }
            }
            _state.value = state.copy(statusMessage = "未启用代理（直连）")
            return
        }

        if (activeNode.protocol == ProxyProtocol.MTPROXY) {
            if (supportsOverride) {
                runCatching {
                    ProxyController.getInstance().clearProxyOverride(Runnable::run) {}
                }
            }
            _state.value = state.copy(
                statusMessage = "已保存 MTProxy（${activeNode.host}:${activeNode.port}）；内置浏览器仅支持 SOCKS/HTTP 协议"
            )
            return
        }

        if (!supportsOverride) {
            _state.value = state.copy(statusMessage = "当前系统 WebView 不支持应用内代理覆盖")
            return
        }

        val scheme = activeNode.protocol.scheme ?: "socks5"
        val proxyUrl = "$scheme://${activeNode.host}:${activeNode.port}"
        val config = ProxyConfig.Builder()
            .addProxyRule(proxyUrl)
            .build()

        val applied = runCatching {
            ProxyController.getInstance().setProxyOverride(config, Runnable::run) {}
            true
        }.getOrDefault(false)

        _state.value = state.copy(
            statusMessage = if (applied) {
                "已启用：${activeNode.name} (${activeNode.protocol.label})"
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
