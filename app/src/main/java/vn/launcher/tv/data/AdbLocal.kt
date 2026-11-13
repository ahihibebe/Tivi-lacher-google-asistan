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
        val dadb = Dadb.create("localhost", 5555, keyPair)
        return dadb.use { client ->
            val resp = client.shell(command)
            resp.output
        }
    }
}
