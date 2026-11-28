package com.jaben.tools.tools.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import java.util.Vector

data class SshRemoteFile(
    val name: String,
    val fullPath: String,
    val isDir: Boolean,
    val isLink: Boolean,
    val size: Long,
    val permissions: String,
    val modifiedAtMs: Long
)

data class SshServerStatus(
    val osInfo: String = "未知系统",
    val uptime: String = "--",
    val memoryInfo: String = "--",
    val diskInfo: String = "--"
)

data class SshCommandResult(
    val exitCode: Int,
    val output: String
)

object SshEngine {

    suspend fun connect(profile: SshServerProfile, resolvedPrivateKey: String = ""): Session =
        withContext(Dispatchers.IO) {
            val jsch = JSch()
            val keyPem = resolvedPrivateKey.ifBlank { profile.privateKey }.trim()
            if (profile.authType == SshAuthType.PRIVATE_KEY && keyPem.isNotEmpty()) {
                val keyBytes = keyPem.toByteArray(Charsets.UTF_8)
                val passBytes = if (profile.keyPassphrase.isNotEmpty()) {
                    profile.keyPassphrase.toByteArray(Charsets.UTF_8)
                } else null
                jsch.addIdentity("jools_${profile.id}", keyBytes, null, passBytes)
            }

            val session = jsch.getSession(profile.username.trim(), profile.host.trim(), profile.port)
            if (profile.authType == SshAuthType.PASSWORD) {
                session.setPassword(profile.password)
            }
            val config = Properties().apply {
                put("StrictHostKeyChecking", "no")
                put("PreferredAuthentications", "publickey,password,keyboard-interactive")
            }
            session.setConfig(config)
            session.timeout = 12_000
            session.connect(12_000)
            session
        }

    fun generateRsaKeyPair(): Pair<String, String> {
        val jsch = JSch()
        val kpair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048)
        val privOut = ByteArrayOutputStream()
        val pubOut = ByteArrayOutputStream()
        kpair.writePrivateKey(privOut)
        kpair.writePublicKey(pubOut, "jools@android")
        kpair.dispose()
        return privOut.toString("UTF-8") to pubOut.toString("UTF-8").trim()
    }

    suspend fun getDefaultHomeDirectory(session: Session): String = withContext(Dispatchers.IO) {
        withSftp(session) { sftp ->
            sftp.pwd().ifBlank { "/" }
        }
    }

    suspend fun fetchServerStatus(session: Session): SshServerStatus = withContext(Dispatchers.IO) {
        val cmd = """
            echo "OS=$(uname -sr 2>/dev/null || echo Linux)";
            echo "UP=$(uptime -p 2>/dev/null || uptime | sed 's/.*up \([^,]*\),.*/\1/')";
            echo "MEM=$(free -m 2>/dev/null | awk 'NR==2{printf "%s/%s MB (%.0f%%)", $3,$2,$3*100/$2}')";
            echo "DISK=$(df -h / 2>/dev/null | awk 'NR==2{printf "%s/%s (%s)", $3,$2,$5}')"
        """.trimIndent().replace("\n", " ")
        val res = runCommandRaw(session, cmd)
        var os = "Linux"
        var up = "--"
        var mem = "--"
        var disk = "--"
        res.output.lines().forEach { line ->
            when {
                line.startsWith("OS=") -> os = line.removePrefix("OS=").trim().ifEmpty { "Linux" }
                line.startsWith("UP=") -> up = line.removePrefix("UP=").trim().ifEmpty { "--" }
                line.startsWith("MEM=") -> mem = line.removePrefix("MEM=").trim().ifEmpty { "--" }
                line.startsWith("DISK=") -> disk = line.removePrefix("DISK=").trim().ifEmpty { "--" }
            }
        }
        SshServerStatus(osInfo = os, uptime = up, memoryInfo = mem, diskInfo = disk)
    }

    suspend fun runCommand(session: Session, command: String, workDir: String? = null): SshCommandResult =
        withContext(Dispatchers.IO) {
            val wrapped = if (!workDir.isNullOrBlank()) {
                val escapedDir = workDir.replace("'", "'\\''")
                "cd '$escapedDir' 2>/dev/null; $command"
            } else {
                command
            }
            runCommandRaw(session, wrapped)
        }

    private fun runCommandRaw(session: Session, command: String): SshCommandResult {
        val channel = session.openChannel("exec") as ChannelExec
        return try {
            channel.setCommand(command)
            val out = ByteArrayOutputStream()
            val err = ByteArrayOutputStream()
            channel.outputStream = null
            channel.setErrStream(err)
            val input = channel.inputStream
            channel.connect(10_000)

            val buf = ByteArray(4096)
            while (true) {
                while (input.available() > 0) {
                    val read = input.read(buf, 0, buf.size)
                    if (read < 0) break
                    out.write(buf, 0, read)
                }
                if (channel.isClosed) {
                    if (input.available() > 0) continue
                    break
                }
                Thread.sleep(40)
            }
            val stdOut = out.toString("UTF-8")
            val stdErr = err.toString("UTF-8")
            val combined = buildString {
                if (stdOut.isNotEmpty()) append(stdOut)
                if (stdErr.isNotEmpty()) {
                    if (isNotEmpty() && !endsWith("\n")) append("\n")
                    append(stdErr)
                }
            }.trimEnd()
            SshCommandResult(channel.exitStatus, combined)
        } finally {
            channel.disconnect()
        }
    }

    private inline fun <T> withSftp(session: Session, block: (ChannelSftp) -> T): T {
        val sftp = session.openChannel("sftp") as ChannelSftp
        try {
            sftp.connect(10_000)
            return block(sftp)
        } finally {
            sftp.disconnect()
        }
    }

    suspend fun listFiles(session: Session, remotePath: String): List<SshRemoteFile> = withContext(Dispatchers.IO) {
        withSftp(session) { sftp ->
            val cleanDir = if (remotePath.isBlank()) sftp.pwd() else remotePath
            @Suppress("UNCHECKED_CAST")
            val vec = sftp.ls(cleanDir) as Vector<ChannelSftp.LsEntry>
            vec.mapNotNull { entry ->
                val name = entry.filename
                if (name == "." || name == "..") return@mapNotNull null
                val attrs = entry.attrs
                val full = if (cleanDir == "/") "/$name" else "${cleanDir.trimEnd('/')}/$name"
                SshRemoteFile(
                    name = name,
                    fullPath = full,
                    isDir = attrs.isDir,
                    isLink = attrs.isLink,
                    size = attrs.size,
                    permissions = attrs.permissionsString ?: "",
                    modifiedAtMs = attrs.mTime.toLong() * 1000L
                )
            }.sortedWith(compareBy<SshRemoteFile> { !it.isDir }.thenBy { it.name.lowercase() })
        }
    }

    suspend fun uploadFile(session: Session, inputStream: InputStream, remotePath: String) = withContext(Dispatchers.IO) {
        withSftp(session) { sftp ->
            sftp.put(inputStream, remotePath, ChannelSftp.OVERWRITE)
        }
    }

    suspend fun downloadFile(session: Session, remotePath: String, outputStream: OutputStream) = withContext(Dispatchers.IO) {
        withSftp(session) { sftp ->
            sftp.get(remotePath, outputStream)
        }
    }

    suspend fun createDirectory(session: Session, remotePath: String) = withContext(Dispatchers.IO) {
        withSftp(session) { sftp ->
            sftp.mkdir(remotePath)
        }
    }

    suspend fun renameRemote(session: Session, oldPath: String, newPath: String) = withContext(Dispatchers.IO) {
        withSftp(session) { sftp ->
            sftp.rename(oldPath, newPath)
        }
    }

    suspend fun deleteRemote(session: Session, item: SshRemoteFile) = withContext(Dispatchers.IO) {
        withSftp(session) { sftp ->
            if (item.isDir) {
                deleteRecursive(sftp, item.fullPath)
            } else {
                sftp.rm(item.fullPath)
            }
        }
    }

    private fun deleteRecursive(sftp: ChannelSftp, path: String) {
        @Suppress("UNCHECKED_CAST")
        val entries = sftp.ls(path) as Vector<ChannelSftp.LsEntry>
        for (e in entries) {
            if (e.filename == "." || e.filename == "..") continue
            val childPath = "${path.trimEnd('/')}/${e.filename}"
            if (e.attrs.isDir) {
                deleteRecursive(sftp, childPath)
            } else {
                sftp.rm(childPath)
            }
        }
        sftp.rmdir(path)
    }

    suspend fun readTextFile(session: Session, remotePath: String, maxBytes: Int = 256 * 1024): String =
        withContext(Dispatchers.IO) {
            withSftp(session) { sftp ->
                val out = ByteArrayOutputStream()
                sftp.get(remotePath).use { input ->
                    val buf = ByteArray(4096)
                    var total = 0
                    while (total < maxBytes) {
                        val r = input.read(buf, 0, minOf(buf.size, maxBytes - total))
                        if (r <= 0) break
                        out.write(buf, 0, r)
                        total += r
                    }
                }
                out.toString("UTF-8")
            }
        }

    suspend fun writeTextFile(session: Session, remotePath: String, content: String) =
        withContext(Dispatchers.IO) {
            withSftp(session) { sftp ->
                content.toByteArray(Charsets.UTF_8).inputStream().use { input ->
                    sftp.put(input, remotePath, ChannelSftp.OVERWRITE)
                }
            }
        }
}
