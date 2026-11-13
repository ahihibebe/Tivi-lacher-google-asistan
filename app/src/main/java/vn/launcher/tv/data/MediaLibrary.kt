package vn.launcher.tv.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.Locale

/** Scans audio tracks and photo albums from MediaStore and USB/external storage. */
object MediaLibrary {
    data class Track(
        val title: String,
        val artist: String,
        val uri: Uri,
        val duration: Long
    )

    data class Album(
        val id: String,
        val name: String,
        val count: Int
    )

    private val AUDIO_EXT = arrayOf(".mp3", ".m4a", ".aac", ".flac", ".wav", ".ogg", ".opus", ".wma")
    private val IMAGE_EXT = arrayOf(".jpg", ".jpeg", ".png", ".webp", ".bmp", ".gif")

    @Suppress("DEPRECATION")
    fun loadTracks(ctx: Context, deep: Boolean = false): List<Track> {
        val out = ArrayList<Track>()
        val known = HashSet<String>()
        try {
            ctx.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATA
                ),
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { c ->
                while (c.moveToNext() && out.size < 3000) {
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                    val title = c.getString(1) ?: ""
                    val rawArtist = c.getString(2)
                    val artist = if (rawArtist == null || rawArtist == "<unknown>") "" else rawArtist
                    val duration = c.getLong(3)
                    val data = c.getString(4)
                    if (data != null) known.add(data)
                    out.add(Track(title, artist, uri, duration))
                }
            }
        } catch (_: Throwable) {
        }

        if (out.isEmpty() || deep) {
            for (f in scan(AUDIO_EXT, 3000)) {
                if (out.size >= 3000) break
                if (known.contains(f.absolutePath)) continue
                val n = f.name
                val dot = n.lastIndexOf('.')
                val title = if (dot > 0) n.substring(0, dot) else n
                out.add(Track(title, "", Uri.fromFile(f), 0L))
            }
            out.sortWith { x, y -> x.title.compareTo(y.title, ignoreCase = true) }
        }
        return out
    }

    fun loadCover(ctx: Context, uri: Uri): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            val art = r.embeddedPicture ?: return null
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(art, 0, art.size, o)
            var s = 1
            while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= 512) s *= 2
            o.inJustDecodeBounds = false
            o.inSampleSize = s
            BitmapFactory.decodeByteArray(art, 0, art.size, o)
        } catch (_: Throwable) {
            null
        } finally {
            try {
                r.release()
            } catch (_: Throwable) {
            }
        }
    }

    fun formatTime(ms: Long): String {
        val s = maxOf(0L, ms) / 1000
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }

    fun loadAlbums(ctx: Context): List<Album> {
        val counts = LinkedHashMap<String, Pair<String, Int>>()
        try {
            ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: "?"
                    val prev = counts[id]
                    counts[id] = (prev?.first ?: name) to ((prev?.second ?: 0) + 1)
                }
            }
        } catch (_: Throwable) {
        }
        return counts.entries.map { Album(it.key, it.value.first, it.value.second) }
            .sortedWith { x, y -> x.name.compareTo(y.name, ignoreCase = true) }
    }

    fun loadImages(ctx: Context, ids: Set<String>?): List<Uri> {
        val out = ArrayList<Uri>()
        var sel: String? = null
        var args: Array<String>? = null
        if (!ids.isNullOrEmpty()) {
            args = ids.toTypedArray()
            val placeholders = args.indices.joinToString(",") { "?" }
            sel = "${MediaStore.Images.Media.BUCKET_ID} IN ($placeholders)"
        }
        try {
            ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                sel,
                args,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { c ->
                while (c.moveToNext() && out.size < 1500) {
                    out.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)))
                }
            }
        } catch (_: Throwable) {
        }
        if (out.isEmpty() && ids.isNullOrEmpty()) {
            for (f in scan(IMAGE_EXT, 1500)) out.add(Uri.fromFile(f))
        }
        return out
    }

    fun loadScaled(ctx: Context, uri: Uri, maxDim: Int): Bitmap? {
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open(ctx, uri).use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxDim) s *= 2
            o.inJustDecodeBounds = false
            o.inSampleSize = s
            var b = open(ctx, uri).use { BitmapFactory.decodeStream(it, null, o) } ?: return null
            var rot = 0
            try {
                open(ctx, uri).use { stream ->
                    val ori = ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    rot = when (ori) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                }
            } catch (_: Throwable) {
            }
            if (rot != 0) {
                val m = Matrix().apply { postRotate(rot.toFloat()) }
                b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
            }
            b
        } catch (_: Throwable) {
            null
        }
    }

    private fun open(ctx: Context, uri: Uri): InputStream {
        if (uri.scheme == "file") {
            val path = uri.path ?: throw FileNotFoundException()
            return FileInputStream(path)
        }
        return ctx.contentResolver.openInputStream(uri) ?: throw FileNotFoundException()
    }

    @Suppress("DEPRECATION")
    private fun roots(): List<File> {
        val r = ArrayList<File>()
        try {
            r.add(Environment.getExternalStorageDirectory())
        } catch (_: Throwable) {
        }
        val vols = File("/storage").listFiles()
        if (vols != null) {
            for (v in vols) {
                val n = v.name
                if (n == "emulated" || n == "self") continue
                r.add(v)
            }
        }
        return r
    }

    private fun scan(exts: Array<String>, max: Int): List<File> {
        val out = ArrayList<File>()
        for (root in roots()) walk(root, exts, 0, max, out)
        return out
    }

    private fun walk(dir: File?, exts: Array<String>, depth: Int, max: Int, out: MutableList<File>) {
        if (dir == null || depth > 4 || out.size >= max) return
        val list = dir.listFiles() ?: return
        for (f in list) {
            if (out.size >= max) return
            val n = f.name
            if (n.startsWith(".") || n.equals("Android", ignoreCase = true)) continue
            if (f.isDirectory) {
                walk(f, exts, depth + 1, max, out)
            } else {
                val l = n.lowercase(Locale.ROOT)
                if (exts.any { l.endsWith(it) }) out.add(f)
            }
        }
    }
}
