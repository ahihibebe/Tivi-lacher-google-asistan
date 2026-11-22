package vn.launcher.tv.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import vn.launcher.tv.R
import java.io.InputStream
import kotlin.math.roundToInt

/** Helpers for QR pairing, LAN upload HTML pages, and bitmap scaling. */
object QrUploadHelper {

    fun makeQrBitmap(text: String, sizePx: Int): Bitmap? = try {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val w = matrix.width
        val h = matrix.height
        val px = IntArray(w * h) { idx ->
            if (matrix.get(idx % w, idx / w)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        createBitmap(w, h).apply { setPixels(px, 0, w, 0, 0, w, h) }
    } catch (_: Exception) {
        null
    }

    fun decodeCoverBitmap(opener: () -> InputStream, targetW: Int, targetH: Int): Bitmap? {
        val op = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        opener().use { BitmapFactory.decodeStream(it, null, op) }
        var sample = 1
        while (op.outWidth / (sample * 2) >= targetW && op.outHeight / (sample * 2) >= targetH) {
            sample *= 2
        }
        op.inJustDecodeBounds = false
        op.inSampleSize = sample
        val src = opener().use { BitmapFactory.decodeStream(it, null, op) } ?: return null
        val scale = maxOf(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
        val cw = minOf(src.width, (targetW / scale).roundToInt())
        val ch = minOf(src.height, (targetH / scale).roundToInt())
        val x = maxOf(0, (src.width - cw) / 2)
        val y = maxOf(0, (src.height - ch) / 2)
        val crop = Bitmap.createBitmap(src, x, y, cw, ch)
        return crop.scale(targetW, targetH)
    }

    fun buildWallpaperUploadHtml(context: Context, token: String, en: Boolean): String = try {
        context.resources.openRawResource(R.raw.upload_page).bufferedReader(Charsets.UTF_8).use { it.readText() }
            .replace("@TITLE@", if (en) "Send a wallpaper to the TV" else "Gửi ảnh nền cho TV")
            .replace("@HINT@", if (en) "Pick a photo from your phone, then tap Send." else "Chọn một ảnh trong điện thoại rồi bấm Gửi.")
            .replace("@BTN@", if (en) "Send" else "Gửi")
            .replace("@PICK@", if (en) "Pick a photo first" else "Chưa chọn ảnh")
            .replace("@DONE@", if (en) "Sent! Check the TV." else "Đã gửi! Xem trên TV.")
            .replace("@ERR@", if (en) "Failed, please try again." else "Gửi thất bại, hãy thử lại.")
            .replace("@TOKEN@", token)
    } catch (_: Exception) {
        "<html><body>Error</body></html>"
    }

    fun buildApiKeyUploadHtml(token: String, en: Boolean): String {
        val title = if (en) "Send Gemini API Key to TV" else "Nhập Gemini API Key cho TV"
        val hint = if (en) {
            "Get a key at aistudio.google.com/apikey, paste it below and tap Send."
        } else {
            "Lấy key miễn phí tại aistudio.google.com/apikey, dán vào ô dưới rồi bấm Gửi."
        }
        val btn = if (en) "Send to TV" else "Gửi lên TV"
        val emptyMsg = if (en) "Please paste your Gemini API key first" else "Hãy dán Gemini API Key trước"
        val doneMsg = if (en) "Sent! Check your TV screen." else "Đã gửi thành công! Xem trên màn hình TV."
        val errMsg = if (en) "Failed, please try again." else "Gửi thất bại, hãy thử lại."
        return """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>$title</title>
            <style>
            body{font-family:-apple-system,Helvetica,Arial,sans-serif;background:#1c1c1e;color:#fff;margin:0;padding:24px;text-align:center}
            h1{font-size:20px;margin-bottom:8px}
            p{color:#c7c7cc;font-size:14px;line-height:1.5}
            a{color:#64d2ff}
            input{width:100%;max-width:420px;box-sizing:border-box;margin-top:14px;padding:14px;border-radius:12px;border:1px solid #48484a;background:#2c2c2e;color:#fff;font-size:15px}
            button{background:#0a84ff;color:#fff;border:0;border-radius:12px;font-size:17px;padding:14px 28px;margin-top:16px;width:100%;max-width:420px}
            #s{margin-top:16px;font-size:15px;color:#30d158}
            </style></head><body>
            <h1>$title</h1>
            <p>$hint<br><a href="https://aistudio.google.com/apikey" target="_blank">aistudio.google.com/apikey</a></p>
            <input type="text" id="k" placeholder="AIzaSy..." autocomplete="off" autocapitalize="off" spellcheck="false">
            <button onclick="send()">$btn</button>
            <div id="s"></div>
            <script>
            function send(){
              var v=document.getElementById('k').value.trim();
              if(v.length<12){alert('$emptyMsg');return;}
              var s=document.getElementById('s');
              s.innerText='...';
              var r=new XMLHttpRequest();
              r.open('POST','/$token/up');
              r.setRequestHeader('Content-Type','text/plain;charset=UTF-8');
              r.onload=function(){
                if(r.status==200){s.style.color='#30d158';s.innerText='$doneMsg';}
                else{s.style.color='#ff453a';s.innerText='$errMsg';}
              };
              r.onerror=function(){s.style.color='#ff453a';s.innerText='$errMsg';};
              r.send(v);
            }
            </script></body></html>
        """.trimIndent()
    }
}
