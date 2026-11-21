package vn.launcher.tv.data

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File

/** Runs local ADB shell commands against the TV's own adbd on localhost:5555. */
object AdbLocal {
    @Synchronized
    fun shell(ctx: Context, command: String): String {
        val home = ctx.filesDir
        File(home, ".android").mkdirs()
        System.setProperty("user.home", home.absolutePath)
        val priv = File(home, "adbkey")
        val pub = File(home, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) {
            AdbKeyPair.generate(priv, pub)
        }
        val keyPair = AdbKeyPair.read(priv, pub)
        val hosts = listOf("127.0.0.1", "localhost")
        var lastConnErr: Exception? = null
        for (host in hosts) {
            try {
                return Dadb.create(host, 5555, keyPair).use { client ->
                    client.shell(command).output
                }
            } catch (e: java.net.ConnectException) {
                lastConnErr = e
            } catch (e: java.net.UnknownHostException) {
                lastConnErr = e
            }
        }
        throw lastConnErr ?: java.net.ConnectException("Unable to connect to local ADB on port 5555")
    }
}
