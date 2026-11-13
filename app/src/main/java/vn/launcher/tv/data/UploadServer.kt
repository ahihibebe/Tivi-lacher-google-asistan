package vn.launcher.tv.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

/** Lightweight single-session HTTP server for phone-to-TV wallpaper QR upload. */
class UploadServer(
    private val token: String,
    private val page: String,
    private val onImage: (ByteArray) -> Unit
) {
    private var server: ServerSocket? = null
    @Volatile
    private var running = false

    fun start(): Int {
        val ss = ServerSocket(0, 4)
        server = ss
        running = true
        Thread {
            while (running) {
                try {
                    val s = ss.accept()
                    handle(s)
                } catch (_: IOException) {
                    if (!running) break
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
        return ss.localPort
    }

    fun stop() {
        running = false
        try {
            server?.close()
        } catch (_: IOException) {
        }
    }

    private fun handle(sock: Socket) {
        try {
            sock.soTimeout = 15000
            val input = sock.getInputStream()
            val out = sock.getOutputStream()
            val reqLine = readLine(input) ?: return
            val parts = reqLine.split(" ")
            val method = parts.getOrNull(0) ?: ""
            val path = parts.getOrNull(1) ?: ""
            var len = 0
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val c = line.indexOf(':')
                if (c > 0 && line.substring(0, c).trim().equals("content-length", ignoreCase = true)) {
                    len = line.substring(c + 1).trim().toIntOrNull() ?: 0
                }
            }
            when {
                method == "GET" && path == "/$token" -> {
                    respond(out, 200, "OK", "text/html; charset=utf-8", page.toByteArray(StandardCharsets.UTF_8))
                }
                method == "POST" && path == "/$token/up" -> {
                    if (len <= 0 || len > MAX_BYTES) {
                        respond(out, 413, "Payload Too Large", "text/plain", "too large".toByteArray(StandardCharsets.UTF_8))
                        return
                    }
                    val data = ByteArray(len)
                    var off = 0
                    while (off < len) {
                        val r = input.read(data, off, len - off)
                        if (r < 0) break
                        off += r
                    }
                    if (off != len) {
                        respond(out, 400, "Bad Request", "text/plain", "incomplete".toByteArray(StandardCharsets.UTF_8))
                        return
                    }
                    respond(out, 200, "OK", "text/plain", "OK".toByteArray(StandardCharsets.UTF_8))
                    onImage(data)
                }
                else -> {
                    respond(out, 404, "Not Found", "text/plain", "not found".toByteArray(StandardCharsets.UTF_8))
                }
            }
        } catch (_: IOException) {
        } finally {
            try {
                sock.close()
            } catch (_: IOException) {
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val bo = ByteArrayOutputStream()
        var b: Int
        while (true) {
            b = input.read()
            if (b == -1 || b == '\n'.code) break
            if (b != '\r'.code) bo.write(b)
            if (bo.size() > 8192) throw IOException("line too long")
        }
        if (b == -1 && bo.size() == 0) return null
        return bo.toString("UTF-8")
    }

    private fun respond(out: OutputStream, code: Int, text: String, type: String, body: ByteArray) {
        val head = "HTTP/1.1 $code $text\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(StandardCharsets.UTF_8))
        out.write(body)
        out.flush()
    }

    companion object {
        private const val MAX_BYTES = 30 * 1024 * 1024
    }
}
