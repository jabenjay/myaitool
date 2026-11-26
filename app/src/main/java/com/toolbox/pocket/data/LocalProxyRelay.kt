package com.toolbox.pocket.data

import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Local loopback SOCKS5 relay so WebView can seamlessly use authenticated upstream
 * SOCKS5/SOCKS4/HTTP proxies and enforce GFW-only domain routing per connection.
 */
class LocalProxyRelay(
    private val gfwRuleManager: GfwRuleManager
) {
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var pool: ExecutorService? = null

    @Volatile
    private var running = false

    @Volatile
    private var upstreamHost: String = ""

    @Volatile
    private var upstreamPort: Int = 0

    @Volatile
    private var upstreamProtocol: ProxyProtocol = ProxyProtocol.SOCKS5

    @Volatile
    private var upstreamUser: String = ""

    @Volatile
    private var upstreamPass: String = ""

    val localPort: Int
        get() = serverSocket?.localPort ?: -1

    @Synchronized
    fun startOrUpdate(
        host: String,
        port: Int,
        protocol: ProxyProtocol,
        username: String,
        password: String
    ): Int {
        upstreamHost = host
        upstreamPort = port
        upstreamProtocol = protocol
        upstreamUser = username
        upstreamPass = password

        if (running && serverSocket != null && serverSocket?.isClosed == false) {
            return localPort
        }

        val ss = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
        serverSocket = ss
        pool = Executors.newCachedThreadPool()
        running = true

        acceptThread = Thread {
            while (running && !ss.isClosed) {
                try {
                    val client = ss.accept()
                    pool?.execute { handleClient(client) }
                } catch (_: Exception) {
                    if (!running) break
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        return ss.localPort
    }

    @Synchronized
    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        pool?.shutdownNow()
        pool = null
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 30000
            val cIn = client.getInputStream()
            val cOut = client.getOutputStream()

            // 1. Local SOCKS5 greeting from WebView
            val ver = cIn.read()
            val nMethods = cIn.read()
            if (ver != 0x05 || nMethods <= 0) {
                client.close()
                return
            }
            val methods = ByteArray(nMethods)
            readFully(cIn, methods)
            // Accept no-auth locally
            cOut.write(byteArrayOf(0x05, 0x00))
            cOut.flush()

            // 2. Read SOCKS5 request from WebView
            val reqVer = cIn.read()
            val cmd = cIn.read()
            cIn.read() // rsv
            val atyp = cIn.read()
            if (reqVer != 0x05 || cmd != 0x01) {
                sendSocks5Reply(cOut, 0x07)
                client.close()
                return
            }

            val targetHost: String = when (atyp) {
                0x01 -> {
                    val addr = ByteArray(4)
                    readFully(cIn, addr)
                    InetAddress.getByAddress(addr).hostAddress ?: ""
                }
                0x03 -> {
                    val len = cIn.read()
                    val domain = ByteArray(len)
                    readFully(cIn, domain)
                    String(domain, StandardCharsets.US_ASCII)
                }
                0x04 -> {
                    val addr = ByteArray(16)
                    readFully(cIn, addr)
                    InetAddress.getByAddress(addr).hostAddress ?: ""
                }
                else -> {
                    sendSocks5Reply(cOut, 0x08)
                    client.close()
                    return
                }
            }
            val portHi = cIn.read()
            val portLo = cIn.read()
            val targetPort = (portHi shl 8) or portLo

            // 3. Route via upstream proxy ONLY if host matches GFW rules; otherwise direct
            val upstream = if (gfwRuleManager.isGfwBlockedHost(targetHost)) {
                connectViaUpstream(targetHost, targetPort)
            } else {
                Socket().apply {
                    connect(InetSocketAddress(targetHost, targetPort), 8000)
                }
            }

            if (upstream == null) {
                sendSocks5Reply(cOut, 0x05)
                client.close()
                return
            }

            sendSocks5Reply(cOut, 0x00)
            upstream.soTimeout = 0
            client.soTimeout = 0

            val t1 = Thread { pipe(cIn, upstream.getOutputStream(), client, upstream) }
            val t2 = Thread { pipe(upstream.getInputStream(), cOut, upstream, client) }
            t1.start()
            t2.start()
        } catch (_: Exception) {
            runCatching { client.close() }
        }
    }

    private fun connectViaUpstream(targetHost: String, targetPort: Int): Socket? {
        return try {
            val s = Socket()
            s.soTimeout = 10000
            s.connect(InetSocketAddress(upstreamHost, upstreamPort), 10000)
            val uIn = s.getInputStream()
            val uOut = s.getOutputStream()

            val ok = when (upstreamProtocol) {
                ProxyProtocol.SOCKS5 -> handshakeSocks5(uIn, uOut, targetHost, targetPort)
                ProxyProtocol.SOCKS4 -> handshakeSocks4(uIn, uOut, targetHost, targetPort)
                ProxyProtocol.HTTP -> handshakeHttp(uIn, uOut, targetHost, targetPort)
            }

            if (ok) s else {
                s.close()
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun handshakeSocks5(
        uIn: InputStream,
        uOut: OutputStream,
        targetHost: String,
        targetPort: Int
    ): Boolean {
        val hasAuth = upstreamUser.isNotEmpty()
        if (hasAuth) {
            uOut.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
        } else {
            uOut.write(byteArrayOf(0x05, 0x01, 0x00))
        }
        uOut.flush()

        val ver = uIn.read()
        val method = uIn.read()
        if (ver != 0x05 || method == 0xFF) return false

        if (method == 0x02) {
            val uBytes = upstreamUser.toByteArray(StandardCharsets.UTF_8)
            val pBytes = upstreamPass.toByteArray(StandardCharsets.UTF_8)
            val authReq = ByteArray(3 + uBytes.size + pBytes.size)
            authReq[0] = 0x01
            authReq[1] = uBytes.size.toByte()
            uBytes.copyInto(authReq, 2)
            authReq[2 + uBytes.size] = pBytes.size.toByte()
            pBytes.copyInto(authReq, 3 + uBytes.size)
            uOut.write(authReq)
            uOut.flush()
            if (uIn.read() != 0x01 || uIn.read() != 0x00) return false
        }

        val target = targetHost.toByteArray(StandardCharsets.US_ASCII)
        val req = ByteArray(7 + target.size)
        req[0] = 0x05
        req[1] = 0x01
        req[2] = 0x00
        req[3] = 0x03
        req[4] = target.size.toByte()
        target.copyInto(req, 5)
        req[5 + target.size] = (targetPort shr 8).toByte()
        req[6 + target.size] = (targetPort and 0xFF).toByte()
        uOut.write(req)
        uOut.flush()

        val repVer = uIn.read()
        val repCode = uIn.read()
        uIn.read() // rsv
        val repAtyp = uIn.read()
        when (repAtyp) {
            0x01 -> readFully(uIn, ByteArray(4))
            0x03 -> {
                val len = uIn.read()
                if (len > 0) readFully(uIn, ByteArray(len))
            }
            0x04 -> readFully(uIn, ByteArray(16))
        }
        readFully(uIn, ByteArray(2)) // bind port
        return repVer == 0x05 && repCode == 0x00
    }

    private fun handshakeSocks4(
        uIn: InputStream,
        uOut: OutputStream,
        targetHost: String,
        targetPort: Int
    ): Boolean {
        val target = targetHost.toByteArray(StandardCharsets.US_ASCII)
        val req = ByteArray(9 + target.size + 1)
        req[0] = 0x04
        req[1] = 0x01
        req[2] = (targetPort shr 8).toByte()
        req[3] = (targetPort and 0xFF).toByte()
        req[7] = 0x01
        target.copyInto(req, 9)
        uOut.write(req)
        uOut.flush()
        val resp = ByteArray(8)
        readFully(uIn, resp)
        return resp[1] == 0x5A.toByte()
    }

    private fun handshakeHttp(
        uIn: InputStream,
        uOut: OutputStream,
        targetHost: String,
        targetPort: Int
    ): Boolean {
        val req = "CONNECT $targetHost:$targetPort HTTP/1.1\r\nHost: $targetHost:$targetPort\r\n\r\n"
            .toByteArray(StandardCharsets.US_ASCII)
        uOut.write(req)
        uOut.flush()
        val headerBuf = StringBuilder()
        var prev = 0
        while (true) {
            val b = uIn.read()
            if (b == -1) return false
            headerBuf.append(b.toChar())
            if (headerBuf.endsWith("\r\n\r\n")) break
            if (headerBuf.length > 4096) return false
            prev = b
        }
        return headerBuf.toString().contains(" 200")
    }

    private fun sendSocks5Reply(out: OutputStream, code: Int) {
        out.write(byteArrayOf(0x05, code.toByte(), 0x00, 0x01, 0, 0, 0, 0, 0, 0))
        out.flush()
    }

    private fun readFully(inp: InputStream, buf: ByteArray) {
        var offset = 0
        while (offset < buf.size) {
            val r = inp.read(buf, offset, buf.size - offset)
            if (r <= 0) throw IllegalStateException("EOF")
            offset += r
        }
    }

    private fun pipe(inp: InputStream, out: OutputStream, s1: Socket, s2: Socket) {
        val buf = ByteArray(8192)
        try {
            while (true) {
                val n = inp.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
                out.flush()
            }
        } catch (_: Exception) {
        } finally {
            runCatching { s1.close() }
            runCatching { s2.close() }
        }
    }
}
