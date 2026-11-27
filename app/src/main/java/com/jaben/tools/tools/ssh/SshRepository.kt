package com.jaben.tools.tools.ssh

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class SshAuthType(val label: String) {
    PASSWORD("密码认证"),
    PRIVATE_KEY("私钥认证")
}

data class SshServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String = "root",
    val authType: SshAuthType = SshAuthType.PASSWORD,
    val password: String = "",
    val privateKey: String = "",
    val keyPassphrase: String = "",
    val defaultPath: String = "/",
    val lastConnectedAtMs: Long = 0L
)

class SshRepository(context: Context) {
    private val prefs = context.getSharedPreferences("jools_ssh_servers", Context.MODE_PRIVATE)

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
                            privateKey = o.optString("privateKey"),
                            keyPassphrase = o.optString("keyPassphrase"),
                            defaultPath = o.optString("defaultPath", "/").ifBlank { "/" },
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
                    put("privateKey", s.privateKey)
                    put("keyPassphrase", s.keyPassphrase)
                    put("defaultPath", s.defaultPath)
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

    private companion object {
        const val KEY_SERVERS = "ssh_servers_json"
    }
}
