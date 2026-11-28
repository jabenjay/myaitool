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
    val hostname: String = "--",
    val kernel: String = "--",
    val arch: String = "--",
    val uptime: String = "--",
    val cpuCores: Int = 0,
    val cpuPercent: Float = 0f,
    val loadAvg: String = "--",
    val memoryInfo: String = "--",
    val memoryPercent: Float = 0f,
    val swapInfo: String = "--",
    val swapPercent: Float = 0f,
    val diskInfo: String = "--",
    val diskPercent: Float = 0f,
    val ipAddress: String = "--",
    val netInterface: String = "--",
    val netRxBytes: Long = 0L,
    val netTxBytes: Long = 0L,
    val netInfo: String = "--",
    val tcpConnections: Int = 0,
    val processCount: Int = 0
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


    fun derivePublicKey(privateKey: String): String? {
        val trimmed = privateKey.trim()
        if (trimmed.isBlank()) return null
        // 1) Try JSch KeyPair.load (supports OpenSSH, RSA PEM, EC PEM, PPK, etc.)
        runCatching {
            val kp = KeyPair.load(JSch(), trimmed.toByteArray(Charsets.UTF_8), null)
            try {
                val out = ByteArrayOutputStream()
                kp.writePublicKey(out, "jools@android")
                val line = out.toString(Charsets.UTF_8.name()).trim()
                if (line.isNotBlank()) return line
            } finally {
                kp.dispose()
            }
        }
        // 2) Fallback for PKCS#8 RSA ("-----BEGIN PRIVATE KEY-----")
        runCatching {
            val b64 = trimmed
                .lines()
                .filterNot { it.trim().startsWith("-----") }
                .joinToString("")
                .replace(Regex("\\s+"), "")
            if (b64.isNotBlank()) {
                val der = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                val kf = java.security.KeyFactory.getInstance("RSA")
                val priv = kf.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(der))
                val crt = kf.getKeySpec(priv, java.security.spec.RSAPrivateCrtKeySpec::class.java)
                val sshPubOut = ByteArrayOutputStream()
                fun writeSshBytes(bytes: ByteArray) {
                    val len = bytes.size
                    sshPubOut.write((len ushr 24) and 0xFF)
                    sshPubOut.write((len ushr 16) and 0xFF)
                    sshPubOut.write((len ushr 8) and 0xFF)
                    sshPubOut.write(len and 0xFF)
                    sshPubOut.write(bytes)
                }
                writeSshBytes("ssh-rsa".toByteArray(Charsets.US_ASCII))
                writeSshBytes(crt.publicExponent.toByteArray())
                writeSshBytes(crt.modulus.toByteArray())
                val pubB64 = android.util.Base64.encodeToString(sshPubOut.toByteArray(), android.util.Base64.NO_WRAP)
                return "ssh-rsa $pubB64 jools@android"
            }
        }
        return null
    }

    fun generateRsaKeyPair(): Pair<String, String> {
        val kpg = java.security.KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        val privB64 = android.util.Base64.encodeToString(kp.private.encoded, android.util.Base64.NO_WRAP)
        val wrappedPriv = privB64.chunked(64).joinToString("\n")
        val privPem = "-----BEGIN PRIVATE KEY-----\n$wrappedPriv\n-----END PRIVATE KEY-----"

        val rsaPub = kp.public as java.security.interfaces.RSAPublicKey
        val sshPubOut = ByteArrayOutputStream()
        fun writeSshBytes(bytes: ByteArray) {
            val len = bytes.size
            sshPubOut.write((len ushr 24) and 0xFF)
            sshPubOut.write((len ushr 16) and 0xFF)
            sshPubOut.write((len ushr 8) and 0xFF)
            sshPubOut.write(len and 0xFF)
            sshPubOut.write(bytes)
        }
        writeSshBytes("ssh-rsa".toByteArray(Charsets.US_ASCII))
        writeSshBytes(rsaPub.publicExponent.toByteArray())
        writeSshBytes(rsaPub.modulus.toByteArray())
        val pubB64 = android.util.Base64.encodeToString(sshPubOut.toByteArray(), android.util.Base64.NO_WRAP)
        val pubLine = "ssh-rsa $pubB64 jools@android"

        return privPem to pubLine
    }

    suspend fun getDefaultHomeDirectory(session: Session): String = withContext(Dispatchers.IO) {
        withSftp(session) { sftp ->
            sftp.pwd().ifBlank { "/" }
        }
    }

    private fun formatTrafficBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(java.util.Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(java.util.Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(java.util.Locale.US, "%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    suspend fun fetchServerStatus(session: Session): SshServerStatus = withContext(Dispatchers.IO) {
        val cmd = """
            OS_NAME=$(grep -m1 '^PRETTY_NAME=' /etc/os-release 2>/dev/null | cut -d= -f2- | tr -d '"');
            [ -z "$OS_NAME" ] && OS_NAME=$(uname -s 2>/dev/null || echo Linux);
            echo "OS=$OS_NAME";
            echo "HOST=$(hostname 2>/dev/null || echo --)";
            echo "KERN=$(uname -r 2>/dev/null || echo --)";
            echo "ARCH=$(uname -m 2>/dev/null || echo --)";
            echo "UP=$(uptime -p 2>/dev/null | sed 's/^up //' || uptime | sed 's/.*up \([^,]*\),.*/\1/')";
            echo "CORES=$(nproc 2>/dev/null || grep -c ^processor /proc/cpuinfo 2>/dev/null || echo 1)";
            echo "CPU=$(awk '/^cpu /{u=$2+$4; t=$2+$3+$4+$5+$6+$7+$8; if(t>0) printf "%.1f", u*100/t; else print "0"}' /proc/stat 2>/dev/null || echo 0)";
            echo "LOAD=$(awk '{print $1", "$2", "$3}' /proc/loadavg 2>/dev/null || echo --)";
            echo "MEM=$(free -m 2>/dev/null | awk 'NR==2{printf "%s/%s MB (%.0f%%)", $3,$2,$3*100/$2}')";
            echo "MEMPCT=$(free -m 2>/dev/null | awk 'NR==2{if($2>0) printf "%.1f", $3*100/$2; else print "0"}')";
            echo "SWAP=$(free -m 2>/dev/null | awk 'NR==3{if($2>0) printf "%s/%s MB (%.0f%%)", $3,$2,$3*100/$2; else print "未启用"}')";
            echo "SWAPPCT=$(free -m 2>/dev/null | awk 'NR==3{if($2>0) printf "%.1f", $3*100/$2; else print "0"}')";
            echo "DISK=$(df -h / 2>/dev/null | awk 'NR==2{printf "%s/%s (%s)", $3,$2,$5}')";
            echo "DISKPCT=$(df -P / 2>/dev/null | awk 'NR==2{gsub("%","",$5); print $5}')";
            echo "IP=$(hostname -I 2>/dev/null | awk '{print $1}')";
            echo "NET=$(awk 'NR>2 && $1 !~ /^lo:/ {gsub(":","",$1); printf "%s|%s|%s\n",$1,$2,$10; exit}' /proc/net/dev 2>/dev/null)";
            echo "TCP=$(ss -tun 2>/dev/null | tail -n +2 | wc -l || echo 0)";
            echo "PROCS=$(ps -e --no-headers 2>/dev/null | wc -l || ps aux 2>/dev/null | wc -l || echo 0)"
        """.trimIndent().replace("\n", " ")
        val res = runCommandRaw(session, cmd)
        var os = "Linux"
        var host = "--"
        var kern = "--"
        var arch = "--"
        var up = "--"
        var cores = 0
        var cpuPct = 0f
        var load = "--"
        var mem = "--"
        var memPct = 0f
        var swap = "--"
        var swapPct = 0f
        var disk = "--"
        var diskPct = 0f
        var ip = "--"
        var netIf = "--"
        var rxBytes = 0L
        var txBytes = 0L
        var netInfo = "--"
        var tcp = 0
        var procs = 0

        res.output.lines().forEach { line ->
            when {
                line.startsWith("OS=") -> os = line.removePrefix("OS=").trim().ifEmpty { "Linux" }
                line.startsWith("HOST=") -> host = line.removePrefix("HOST=").trim().ifEmpty { "--" }
                line.startsWith("KERN=") -> kern = line.removePrefix("KERN=").trim().ifEmpty { "--" }
                line.startsWith("ARCH=") -> arch = line.removePrefix("ARCH=").trim().ifEmpty { "--" }
                line.startsWith("UP=") -> up = line.removePrefix("UP=").trim().ifEmpty { "--" }
                line.startsWith("CORES=") -> cores = line.removePrefix("CORES=").trim().toIntOrNull() ?: 0
                line.startsWith("CPU=") -> cpuPct = (line.removePrefix("CPU=").trim().toFloatOrNull() ?: 0f).coerceIn(0f, 100f)
                line.startsWith("LOAD=") -> load = line.removePrefix("LOAD=").trim().ifEmpty { "--" }
                line.startsWith("MEM=") -> mem = line.removePrefix("MEM=").trim().ifEmpty { "--" }
                line.startsWith("MEMPCT=") -> memPct = (line.removePrefix("MEMPCT=").trim().toFloatOrNull() ?: 0f).coerceIn(0f, 100f)
                line.startsWith("SWAP=") -> swap = line.removePrefix("SWAP=").trim().ifEmpty { "--" }
                line.startsWith("SWAPPCT=") -> swapPct = (line.removePrefix("SWAPPCT=").trim().toFloatOrNull() ?: 0f).coerceIn(0f, 100f)
                line.startsWith("DISK=") -> disk = line.removePrefix("DISK=").trim().ifEmpty { "--" }
                line.startsWith("DISKPCT=") -> diskPct = (line.removePrefix("DISKPCT=").trim().toFloatOrNull() ?: 0f).coerceIn(0f, 100f)
                line.startsWith("IP=") -> ip = line.removePrefix("IP=").trim().ifEmpty { "--" }
                line.startsWith("NET=") -> {
                    val parts = line.removePrefix("NET=").trim().split("|")
                    if (parts.size >= 3) {
                        netIf = parts[0].ifEmpty { "--" }
                        rxBytes = parts[1].toLongOrNull() ?: 0L
                        txBytes = parts[2].toLongOrNull() ?: 0L
                        netInfo = "$netIf · ↓${formatTrafficBytes(rxBytes)} / ↑${formatTrafficBytes(txBytes)}"
                    }
                }
                line.startsWith("TCP=") -> tcp = line.removePrefix("TCP=").trim().toIntOrNull() ?: 0
                line.startsWith("PROCS=") -> procs = line.removePrefix("PROCS=").trim().toIntOrNull() ?: 0
            }
        }
        SshServerStatus(
            osInfo = os,
            hostname = host,
            kernel = kern,
            arch = arch,
            uptime = up,
            cpuCores = cores,
            cpuPercent = cpuPct,
            loadAvg = load,
            memoryInfo = mem,
            memoryPercent = memPct,
            swapInfo = swap,
            swapPercent = swapPct,
            diskInfo = disk,
            diskPercent = diskPct,
            ipAddress = ip,
            netInterface = netIf,
            netRxBytes = rxBytes,
            netTxBytes = txBytes,
            netInfo = netInfo,
            tcpConnections = tcp,
            processCount = procs
        )
    }

    suspend fun tryElevateRoot(session: Session, password: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val noPassRes = runCommandRaw(session, "sudo -n id -u 2>/dev/null")
            if (noPassRes.exitCode == 0 && noPassRes.output.trim().lines().lastOrNull()?.trim() == "0") {
                return@withContext true to ""
            }
            if (password.isNotEmpty()) {
                val escapedPass = password.replace("'", "'\\''")
                val res = runCommandRaw(session, "printf '%s\n' '$escapedPass' | sudo -S -p '' id -u 2>&1")
                if (res.output.lines().any { it.trim() == "0" }) {
                    return@withContext true to ""
                }
                return@withContext false to res.output.trim().ifEmpty { "sudo 认证失败" }
            }
            false to "需要输入 sudo 密码"
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

    suspend fun runCommandAsRoot(
        session: Session,
        command: String,
        workDir: String? = null,
        sudoPassword: String = ""
    ): SshCommandResult = withContext(Dispatchers.IO) {
        val inner = if (!workDir.isNullOrBlank()) {
            val escapedDir = workDir.replace("'", "'\\''")
            "cd '$escapedDir' 2>/dev/null; $command"
        } else {
            command
        }
        val escapedInner = inner.replace("'", "'\\''")
        val fullCmd = if (sudoPassword.isNotEmpty()) {
            val escapedPass = sudoPassword.replace("'", "'\\''")
            "printf '%s\n' '$escapedPass' | sudo -S -p '' sh -c '$escapedInner'"
        } else {
            "sudo -n sh -c '$escapedInner'"
        }
        runCommandRaw(session, fullCmd)
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
