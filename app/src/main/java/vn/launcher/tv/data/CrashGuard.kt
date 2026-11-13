package vn.launcher.tv.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Records uncaught launcher crashes so safe mode and diagnostics can surface on next launch. */
object CrashGuard {
    private fun file(c: Context): File = File(c.filesDir, "crash.txt")

    fun install(app: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            record(app, e)
            prev?.uncaughtException(t, e)
        }
    }

    fun record(c: Context, e: Throwable) {
        try {
            PrintWriter(file(c)).use { w ->
                w.println(System.currentTimeMillis())
                e.printStackTrace(w)
            }
        } catch (_: Exception) {
        }
    }

    fun read(c: Context): String? {
        val f = file(c)
        if (!f.exists()) return null
        return try {
            val lines = f.readLines()
            if (lines.size <= 1) null else lines.drop(1).joinToString("\n").ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    fun recent(c: Context, withinMs: Long): Boolean {
        val f = file(c)
        if (!f.exists()) return false
        return try {
            val first = f.useLines { it.firstOrNull() }?.trim()?.toLongOrNull() ?: return false
            System.currentTimeMillis() - first < withinMs
        } catch (_: Exception) {
            false
        }
    }

    fun clear(c: Context) {
        try {
            file(c).delete()
        } catch (_: Exception) {
        }
    }

    fun firstLines(s: String, n: Int): String = s.lineSequence().take(n).joinToString("\n")

    fun trace(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }
}
