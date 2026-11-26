package com.jaben.tools.data

import android.content.Context
import android.os.SystemClock
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class ProxyProtocol(val label: String, val scheme: String) {
    SOCKS5("SOCKS5", "socks5"),
    SOCKS4("SOCKS4", "socks4"),
    HTTP("HTTP", "http")
}

data class ProxyNode(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: ProxyProtocol,
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val isBuiltIn: Boolean = false
) {
    fun summary(): String = if (isBuiltIn) "" else "${protocol.label} · $host:$port"
}

sealed class ProxyLatency {
    data object Testing : ProxyLatency()
    data class Success(val ms: Long) : ProxyLatency()
    data class Failed(val reason: String) : ProxyLatency()
}

data class ProxyState(
    val enabled: Boolean = false,
    val selectedId: String? = null,
    val nodes: List<ProxyNode> = emptyList(),
    val latencies: Map<String, ProxyLatency> = emptyMap(),
    val isTestingAll: Boolean = false,
    val statusMessage: String = "未启用（直连）"
)

private object BuiltInVault {
    private const val BUILTIN_ID = "builtin_default_proxy"
    private val MASK = byteArrayOf(-89, -6, 9, 32, 14, 21, -16, 98, 10, -36, -66, 28, 120, -124, 120, 15, -103, -118, -62, -70, 108, 6, -102, 43, -98, 106, -59, 51, -109, 80, -100, -92)
    private val MKEY = byteArrayOf(103, -44, -65, -45, -27, -5, 97, 79, -117, 68, 89, -54, 105, 28, 56, -15, 94, -73, -104, -24, -38, 83, 5, -74, 78, -30, -71, -23, 114, 91, -120, 75)
    private val MIV = byteArrayOf(-121, 8, 113, 108, -126, -45, -32, -93, 14, -32, 74, -81, -6, -14, -75, -102)
    private val CIPHER = byteArrayOf(-106, -2, -72, 68, -50, 62, -94, -51, 103, -85, -23, 8, 5, 61, 48, -83, 3, -113, 40, -112, 119, 119, 92, 122, -24, 23, -74, -17, 27, 86, 75, 1, -85, 66, 117, 18, -92, -33, -83, 89, -121, -60, -94, -47, 101, 90, -13, -75, -32, 48, 95, -117, 89, 16, -85, -102, 12, -39, 85, 10, 75, -102, -98, -18, 17, 118, -66, 94, -49, 47, -84, -114, -116, -104, -31, 1, -109, -5, -4, -40)

    fun resolveDefaultNode(): ProxyNode? {
        return runCatching {
            val key = ByteArray(MKEY.size) { i -> (MKEY[i].toInt() xor MASK[i].toInt()).toByte() }
            val iv = ByteArray(MIV.size) { i -> (MIV[i].toInt() xor MASK[i].toInt()).toByte() }
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val plain = String(cipher.doFinal(CIPHER), StandardCharsets.UTF_8)
            val obj = JSONObject(plain)
            ProxyNode(
                id = BUILTIN_ID,
                name = "默认代理",
                protocol = ProxyProtocol.SOCKS5,
                host = obj.getString("h"),
                port = obj.getInt("p"),
                username = obj.getString("u"),
                password = obj.getString("w"),
                isBuiltIn = true
            )
        }.getOrNull()
    }
}

class ProxyManager(
    context: Context,
    gfwRuleManager: GfwRuleManager
) {
    private val prefs = context.getSharedPreferences("toolbox_proxy", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val relay = LocalProxyRelay(gfwRuleManager)

    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<ProxyState> = _state.asStateFlow()

    init {
        applyProxyOverride(_state.value)
    }

    private fun loadState(): ProxyState {
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val selectedId = prefs.getString(KEY_SELECTED_ID, null)
        val rawJson = prefs.getString(KEY_NODES, "[]") ?: "[]"
        val userNodes = runCatching {
            val arr = JSONArray(rawJson)
            val list = mutableListOf<ProxyNode>()
            for (idx in 0 until arr.length()) {
                val obj = arr.getJSONObject(idx)
                val proto = runCatching {
                    ProxyProtocol.valueOf(obj.getString("protocol"))
                }.getOrNull() ?: continue
                list.add(
                    ProxyNode(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        protocol = proto,
                        host = obj.getString("host"),
                        port = obj.getInt("port"),
                        username = obj.optString("username", ""),
                        password = obj.optString("secret", ""),
                        isBuiltIn = false
                    )
                )
            }
            list
        }.getOrDefault(emptyList())

        val builtIn = BuiltInVault.resolveDefaultNode()
        val allNodes = if (builtIn != null) listOf(builtIn) + userNodes else userNodes
        val effectiveSelected = selectedId ?: allNodes.firstOrNull()?.id

        return ProxyState(
            enabled = enabled,
            selectedId = effectiveSelected,
            nodes = allNodes
        )
    }

    private fun persist(state: ProxyState) {
        val arr = JSONArray()
        state.nodes.filterNot { it.isBuiltIn }.forEach { node ->
            arr.put(
                JSONObject().apply {
                    put("id", node.id)
                    put("name", node.name)
                    put("protocol", node.protocol.name)
                    put("host", node.host)
                    put("port", node.port)
                    put("username", node.username)
                    put("secret", node.password)
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
        val target = _state.value.nodes.find { it.id == id } ?: return
        if (target.isBuiltIn) return
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
            enabled = nextEnabled,
            latencies = _state.value.latencies - id
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

    fun testNode(nodeId: String) {
        val node = _state.value.nodes.find { it.id == nodeId } ?: return
        scope.launch {
            updateLatency(nodeId, ProxyLatency.Testing)
            val result = measureNodeLatency(node)
            updateLatency(nodeId, result)
        }
    }

    fun testAllNodes() {
        val nodes = _state.value.nodes
        if (nodes.isEmpty()) return
        scope.launch {
            val testingMap = _state.value.latencies.toMutableMap()
            nodes.forEach { testingMap[it.id] = ProxyLatency.Testing }
            _state.value = _state.value.copy(latencies = testingMap, isTestingAll = true)

            nodes.map { node ->
                async {
                    val res = measureNodeLatency(node)
                    updateLatency(node.id, res)
                }
            }.awaitAll()

            _state.value = _state.value.copy(isTestingAll = false)
        }
    }

    @Synchronized
    private fun updateLatency(nodeId: String, result: ProxyLatency) {
        val updated = _state.value.latencies.toMutableMap()
        updated[nodeId] = result
        _state.value = _state.value.copy(latencies = updated)
    }

    private fun measureNodeLatency(node: ProxyNode): ProxyLatency {
        val start = SystemClock.elapsedRealtime()
        return try {
            Socket().use { socket ->
                socket.soTimeout = 5000
                socket.connect(InetSocketAddress(node.host, node.port), 5000)
                val out = socket.getOutputStream()
                val inp = socket.getInputStream()

                val ok = when (node.protocol) {
                    ProxyProtocol.SOCKS5 -> testSocks5(inp, out, node)
                    ProxyProtocol.SOCKS4 -> testSocks4(inp, out)
                    ProxyProtocol.HTTP -> testHttpConnect(inp, out)
                }

                if (ok) {
                    val elapsed = (SystemClock.elapsedRealtime() - start).coerceAtLeast(1L)
                    ProxyLatency.Success(elapsed)
                } else {
                    ProxyLatency.Failed("失败")
                }
            }
        } catch (_: Exception) {
            ProxyLatency.Failed("超时")
        }
    }

    private fun testSocks5(inp: InputStream, out: OutputStream, node: ProxyNode): Boolean {
        val hasAuth = node.username.isNotEmpty()
        if (hasAuth) {
            out.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
        } else {
            out.write(byteArrayOf(0x05, 0x01, 0x00))
        }
        out.flush()

        val ver = inp.read()
        val method = inp.read()
        if (ver != 0x05 || method == 0xFF) return false

        if (method == 0x02) {
            val uBytes = node.username.toByteArray(StandardCharsets.UTF_8)
            val pBytes = node.password.toByteArray(StandardCharsets.UTF_8)
            val authReq = ByteArray(3 + uBytes.size + pBytes.size)
            authReq[0] = 0x01
            authReq[1] = uBytes.size.toByte()
            uBytes.copyInto(authReq, 2)
            authReq[2 + uBytes.size] = pBytes.size.toByte()
            pBytes.copyInto(authReq, 3 + uBytes.size)
            out.write(authReq)
            out.flush()
            val authVer = inp.read()
            val authStatus = inp.read()
            if (authVer != 0x01 || authStatus != 0x00) return false
        }

        val target = "www.google.com".toByteArray(StandardCharsets.US_ASCII)
        val req = ByteArray(7 + target.size)
        req[0] = 0x05
        req[1] = 0x01
        req[2] = 0x00
        req[3] = 0x03
        req[4] = target.size.toByte()
        target.copyInto(req, 5)
        req[5 + target.size] = 0x00
        req[6 + target.size] = 80.toByte()
        out.write(req)
        out.flush()

        val repVer = inp.read()
        val repCode = inp.read()
        return repVer == 0x05 && repCode == 0x00
    }

    private fun testSocks4(inp: InputStream, out: OutputStream): Boolean {
        val target = "www.google.com".toByteArray(StandardCharsets.US_ASCII)
        val req = ByteArray(9 + target.size + 1)
        req[0] = 0x04
        req[1] = 0x01
        req[2] = 0x00
        req[3] = 80.toByte()
        req[7] = 0x01
        target.copyInto(req, 9)
        out.write(req)
        out.flush()

        inp.read()
        val status = inp.read()
        return status == 0x5A
    }

    private fun testHttpConnect(inp: InputStream, out: OutputStream): Boolean {
        val req = "CONNECT www.google.com:80 HTTP/1.1\r\nHost: www.google.com:80\r\n\r\n"
            .toByteArray(StandardCharsets.US_ASCII)
        out.write(req)
        out.flush()
        val buf = ByteArray(64)
        val read = inp.read(buf)
        if (read <= 0) return false
        val line = String(buf, 0, read, StandardCharsets.US_ASCII)
        return line.contains(" 200")
    }

    private fun applyProxyOverride(state: ProxyState) {
        val activeNode = state.nodes.find { it.id == state.selectedId }
        val supportsOverride = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)

        if (!state.enabled || activeNode == null) {
            relay.stop()
            if (supportsOverride) {
                runCatching { ProxyController.getInstance().clearProxyOverride(Runnable::run) {} }
            }
            _state.value = state.copy(statusMessage = "未启用（直连）")
            return
        }

        if (!supportsOverride) {
            _state.value = state.copy(statusMessage = "当前系统 WebView 不支持应用内代理")
            return
        }

        val localPort = relay.startOrUpdate(
            host = activeNode.host,
            port = activeNode.port,
            protocol = activeNode.protocol,
            username = activeNode.username,
            password = activeNode.password
        )
        val config = ProxyConfig.Builder()
            .addProxyRule("socks5://127.0.0.1:$localPort")
            .build()

        val applied = runCatching {
            ProxyController.getInstance().setProxyOverride(config, Runnable::run) {}
            true
        }.getOrDefault(false)

        _state.value = state.copy(
            statusMessage = if (applied) {
                "已启用：${activeNode.name}"
            } else {
                "代理配置失败"
            }
        )
    }

    private companion object {
        const val KEY_ENABLED = "proxy_enabled"
        const val KEY_SELECTED_ID = "proxy_selected_id"
        const val KEY_NODES = "proxy_nodes"
    }
}
