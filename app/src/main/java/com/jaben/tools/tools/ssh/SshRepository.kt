package com.jaben.tools.tools.ssh

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class SshAuthType(val label: String) {
    PASSWORD("密码认证"),
    PRIVATE_KEY("私钥认证")
}


data class SshQuickCommand(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val command: String
)

data class SshKeyItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val privateKey: String,
    val publicKey: String = "",
    val createdAtMs: Long = System.currentTimeMillis()
)

data class SshServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String = "root",
    val authType: SshAuthType = SshAuthType.PASSWORD,
    val password: String = "",
    val keyId: String = "",
    val privateKey: String = "",
    val keyPassphrase: String = "",
    val lastConnectedAtMs: Long = 0L
)

class SshRepository(context: Context) {
    private val prefs = context.getSharedPreferences("jools_ssh_servers", Context.MODE_PRIVATE)

    fun loadKeys(): List<SshKeyItem> {
        val raw = prefs.getString(KEY_SSH_KEYS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val pem = o.optString("privateKey")
                    if (pem.isBlank()) continue
                    add(
                        SshKeyItem(
                            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                            name = o.optString("name").ifBlank { "未命名密钥" },
                            privateKey = pem,
                            publicKey = o.optString("publicKey", ""),
                            createdAtMs = o.optLong("createdAtMs", 0L)
                        )
                    )
                }
            }.sortedBy { it.createdAtMs }
        }.getOrDefault(emptyList())
    }

    fun saveKeys(list: List<SshKeyItem>) {
        val arr = JSONArray()
        list.forEach { k ->
            arr.put(
                JSONObject().apply {
                    put("id", k.id)
                    put("name", k.name)
                    put("privateKey", k.privateKey)
                    put("publicKey", k.publicKey)
                    put("createdAtMs", k.createdAtMs)
                }
            )
        }
        prefs.edit().putString(KEY_SSH_KEYS, arr.toString()).apply()
    }

    fun upsertKey(key: SshKeyItem): List<SshKeyItem> {
        val current = loadKeys().toMutableList()
        val idx = current.indexOfFirst { it.id == key.id }
        if (idx >= 0) {
            current[idx] = key
        } else {
            current.add(key)
        }
        saveKeys(current)
        return current.sortedBy { it.createdAtMs }
    }

    fun deleteKey(id: String): List<SshKeyItem> {
        val updated = loadKeys().filterNot { it.id == id }
        saveKeys(updated)
        return updated
    }

    fun resolvePrivateKey(profile: SshServerProfile): String {
        if (profile.keyId.isNotBlank()) {
            loadKeys().firstOrNull { it.id == profile.keyId }?.let { return it.privateKey }
        }
        return profile.privateKey
    }

    fun loadServers(): List<SshServerProfile> {
        val raw = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val authStr = o.optString("authType", SshAuthType.PASSWORD.name)
                    val auth = runCatching { SshAuthType.valueOf(authStr) }.getOrDefault(SshAuthType.PASSWORD)
                    add(
                        SshServerProfile(
                            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                            name = o.optString("name"),
                            host = o.optString("host"),
                            port = o.optInt("port", 22).coerceIn(1, 65535),
                            username = o.optString("username", "root"),
                            authType = auth,
                            password = o.optString("password"),
                            keyId = o.optString("keyId"),
                            privateKey = o.optString("privateKey"),
                            keyPassphrase = o.optString("keyPassphrase"),
                            lastConnectedAtMs = o.optLong("lastConnectedAtMs", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveServers(list: List<SshServerProfile>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("host", s.host)
                    put("port", s.port)
                    put("username", s.username)
                    put("authType", s.authType.name)
                    put("password", s.password)
                    put("keyId", s.keyId)
                    put("privateKey", s.privateKey)
                    put("keyPassphrase", s.keyPassphrase)
                    put("lastConnectedAtMs", s.lastConnectedAtMs)
                }
            )
        }
        prefs.edit().putString(KEY_SERVERS, arr.toString()).apply()
    }

    fun upsertServer(profile: SshServerProfile): List<SshServerProfile> {
        val current = loadServers().toMutableList()
        val idx = current.indexOfFirst { it.id == profile.id }
        if (idx >= 0) {
            current[idx] = profile
        } else {
            current.add(0, profile)
        }
        saveServers(current)
        return current
    }

    fun deleteServer(id: String): List<SshServerProfile> {
        val updated = loadServers().filterNot { it.id == id }
        saveServers(updated)
        return updated
    }

    fun touchLastConnected(id: String): List<SshServerProfile> {
        val now = System.currentTimeMillis()
        val updated = loadServers().map {
            if (it.id == id) it.copy(lastConnectedAtMs = now) else it
        }
        saveServers(updated)
        return updated
    }


    fun loadQuickCommands(): List<SshQuickCommand> {
        val raw = prefs.getString(KEY_QUICK_CMDS, null)
        if (!raw.isNullOrBlank()) {
            val list = runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val cmd = o.optString("command", "").trim()
                    if (cmd.isBlank()) return@mapNotNull null
                    SshQuickCommand(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        title = o.optString("title", cmd).ifBlank { cmd },
                        command = cmd
                    )
                }
            }.getOrNull()
            if (list != null) return list
        }
        return emptyList()
    }

    fun saveQuickCommands(items: List<SshQuickCommand>) {
        val arr = JSONArray()
        items.forEach { q ->
            arr.put(JSONObject().apply {
                put("id", q.id)
                put("title", q.title)
                put("command", q.command)
            })
        }
        prefs.edit().putString(KEY_QUICK_CMDS, arr.toString()).apply()
    }

    fun upsertQuickCommand(item: SshQuickCommand): List<SshQuickCommand> {
        val cur = loadQuickCommands().toMutableList()
        val idx = cur.indexOfFirst { it.id == item.id }
        if (idx >= 0) cur[idx] = item else cur.add(item)
        saveQuickCommands(cur)
        return cur
    }

    fun deleteQuickCommand(id: String): List<SshQuickCommand> {
        val updated = loadQuickCommands().filterNot { it.id == id }
        saveQuickCommands(updated)
        return updated
    }

    private companion object {
        const val KEY_SERVERS = "ssh_servers_json"
        const val KEY_SSH_KEYS = "ssh_keys_json"
        const val KEY_QUICK_CMDS = "ssh_quick_cmds_json"
    }
}
