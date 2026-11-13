package vn.launcher.tv.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** Vietnamese 34 provinces/cities + ward lookup & Nominatim geocoding for weather location. */
object AdminData {
    data class Province(val name: String, val code: Int, val lat: Double, val lon: Double) {
        fun shortName(): String = name.replaceFirst(Regex("^(Thành phố|Tỉnh) "), "")
    }

    data class Ward(val name: String, val code: Int = 0)

    val PROVINCES = listOf(
        Province("Thành phố Hà Nội", 1, 21.03, 105.85),
        Province("Tỉnh Cao Bằng", 4, 22.67, 106.26),
        Province("Tỉnh Tuyên Quang", 8, 21.82, 105.21),
        Province("Tỉnh Điện Biên", 11, 21.39, 103.02),
        Province("Tỉnh Lai Châu", 12, 22.40, 103.46),
        Province("Tỉnh Sơn La", 14, 21.33, 103.91),
        Province("Tỉnh Lào Cai", 15, 22.48, 103.97),
        Province("Tỉnh Thái Nguyên", 19, 21.59, 105.84),
        Province("Tỉnh Lạng Sơn", 20, 21.85, 106.76),
        Province("Tỉnh Quảng Ninh", 22, 20.95, 107.08),
        Province("Tỉnh Bắc Ninh", 24, 21.19, 106.07),
        Province("Tỉnh Phú Thọ", 25, 21.32, 105.40),
        Province("Thành phố Hải Phòng", 31, 20.86, 106.68),
        Province("Tỉnh Hưng Yên", 33, 20.65, 106.05),
        Province("Tỉnh Ninh Bình", 37, 20.25, 105.97),
        Province("Tỉnh Thanh Hóa", 38, 19.81, 105.78),
        Province("Tỉnh Nghệ An", 40, 18.68, 105.69),
        Province("Tỉnh Hà Tĩnh", 42, 18.34, 105.91),
        Province("Tỉnh Quảng Trị", 44, 17.47, 106.62),
        Province("Thành phố Huế", 46, 16.46, 107.59),
        Province("Thành phố Đà Nẵng", 48, 16.05, 108.21),
        Province("Tỉnh Quảng Ngãi", 51, 15.12, 108.80),
        Province("Tỉnh Gia Lai", 52, 13.78, 109.22),
        Province("Tỉnh Khánh Hòa", 56, 12.24, 109.19),
        Province("Tỉnh Đắk Lắk", 66, 12.67, 108.04),
        Province("Tỉnh Lâm Đồng", 68, 11.94, 108.44),
        Province("Tỉnh Đồng Nai", 75, 10.96, 106.84),
        Province("Thành phố Hồ Chí Minh", 79, 10.78, 106.70),
        Province("Tỉnh Tây Ninh", 80, 11.31, 106.10),
        Province("Tỉnh Đồng Tháp", 82, 10.46, 105.63),
        Province("Tỉnh Vĩnh Long", 86, 10.25, 105.97),
        Province("Tỉnh An Giang", 91, 10.37, 105.43),
        Province("Thành phố Cần Thơ", 92, 10.03, 105.78),
        Province("Tỉnh Cà Mau", 96, 9.18, 105.15)
    )

    fun byName(name: String?): Province? {
        if (name.isNullOrEmpty()) return null
        return PROVINCES.firstOrNull { it.name == name }
    }

    fun norm(s: String?): String {
        val t = Normalizer.normalize(s ?: "", Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace('đ', 'd')
        return t.replace(Regex("^(thanh pho|tinh)\\s+"), "").trim()
    }

    fun loadWards(ctx: Context, p: Province): List<Ward> {
        val cache = File(ctx.filesDir, "wards_${p.code}.json")
        var arr: JSONArray? = null
        if (cache.exists()) {
            try {
                arr = JSONArray(cache.readText(Charsets.UTF_8))
            } catch (_: Exception) {
            }
        }
        if (arr == null || arr.length() == 0) {
            arr = fetchWards(p.code, p.name)
            if (arr == null || arr.length() == 0) {
                val c2 = findCodeByName(p.name)
                if (c2 > 0 && c2 != p.code) arr = fetchWards(c2, p.name)
            }
            if (arr == null || arr.length() == 0) throw IOException("no wards")
            try {
                cache.writeText(arr.toString(), Charsets.UTF_8)
            } catch (_: Exception) {
            }
        }
        val out = ArrayList<Ward>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("name", "")
            val code = o.optInt("code")
            if (name.isNotEmpty()) out.add(Ward(name, code))
        }
        val col = Collator.getInstance(Locale.forLanguageTag("vi"))
        out.sortWith { a, b -> col.compare(a.name, b.name) }
        return out
    }

    private fun fetchWards(code: Int, expectName: String): JSONArray? {
        val o = JSONObject(get("https://provinces.open-api.vn/api/v2/p/$code?depth=2"))
        if (norm(o.optString("name")) != norm(expectName)) return null
        val wards = o.optJSONArray("wards") ?: return null
        val slim = JSONArray()
        for (i in 0 until wards.length()) {
            val w = wards.getJSONObject(i)
            val n = JSONObject()
            n.put("name", w.optString("name", ""))
            n.put("code", w.optInt("code"))
            slim.put(n)
        }
        return slim
    }

    private fun findCodeByName(name: String): Int {
        try {
            val a = JSONArray(get("https://provinces.open-api.vn/api/v2/"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                if (norm(o.optString("name")) == norm(name)) return o.optInt("code")
            }
        } catch (_: Exception) {
        }
        return -1
    }

    data class ResolvedLocation(
        val displayLabel: String,
        val provinceHint: String?,
        val lat: Double?,
        val lon: Double?
    )

    private val LEGACY_PROVINCE_ALIASES = mapOf(
        "dak nong" to "Đắk Nông",
        "kon tum" to "Kon Tum",
        "binh phuoc" to "Bình Phước",
        "ba ria vung tau" to "Bà Rịa - Vũng Tàu",
        "vung tau" to "Bà Rịa - Vũng Tàu",
        "buon ma thuot" to "Đắk Lắk",
        "da lat" to "Lâm Đồng",
        "bao loc" to "Lâm Đồng",
        "pleiku" to "Gia Lai",
        "nha trang" to "Khánh Hòa",
        "quy nhon" to "Gia Lai",
        "sai gon" to "Thành phố Hồ Chí Minh",
        "tphcm" to "Thành phố Hồ Chí Minh",
        "tp hcm" to "Thành phố Hồ Chí Minh",
        "ho chi minh" to "Thành phố Hồ Chí Minh",
        "ha noi" to "Thành phố Hà Nội",
        "da nang" to "Thành phố Đà Nẵng",
        "can tho" to "Thành phố Cần Thơ",
        "hai phong" to "Thành phố Hải Phòng",
        "hue" to "Thành phố Huế"
    )

    fun findProvinceFuzzy(query: String): String? {
        val nq = norm(query).replace(Regex("[^a-z0-9 ]+"), " ").replace(Regex("\\s+"), " ").trim()
        if (nq.isEmpty()) return null
        for ((alias, full) in LEGACY_PROVINCE_ALIASES) {
            if (nq == alias || " $nq ".contains(" $alias ")) return full
        }
        val sorted = PROVINCES.sortedByDescending { norm(it.shortName()).length }
        for (p in sorted) {
            val ps = norm(p.shortName())
            if (nq == ps || " $nq ".contains(" $ps ")) return p.shortName()
        }
        return null
    }

    fun geocode(query: String): Pair<Double, Double>? {
        val r = resolveLocation(query) ?: return null
        return if (r.lat != null && r.lon != null) Pair(r.lat, r.lon) else null
    }

    fun resolveLocation(query: String): ResolvedLocation? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        val fuzzyProv = findProvinceFuzzy(trimmed)
        return try {
            val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=1&countrycodes=vn" +
                "&accept-language=vi&q=" + URLEncoder.encode("$trimmed, Việt Nam", "UTF-8")
            val a = JSONArray(get(url))
            if (a.length() > 0) {
                val o = a.getJSONObject(0)
                val lat = o.optString("lat").toDoubleOrNull()
                val lon = o.optString("lon").toDoubleOrNull()
                val addr = o.optJSONObject("address")
                val state = addr?.optString("state")?.ifEmpty { null }
                    ?: addr?.optString("province")?.ifEmpty { null }
                    ?: addr?.optString("city")?.ifEmpty { null }
                val cleanState = state?.replaceFirst(Regex("^(Thành phố|Tỉnh)\\s+"), "")
                val village = addr?.optString("village")?.ifEmpty { null }
                    ?: addr?.optString("suburb")?.ifEmpty { null }
                    ?: addr?.optString("town")?.ifEmpty { null }
                    ?: addr?.optString("city_district")?.ifEmpty { null }
                    ?: addr?.optString("county")?.ifEmpty { null }
                val label = when {
                    village != null && cleanState != null -> "$village, $cleanState"
                    cleanState != null -> cleanState
                    else -> trimmed
                }
                ResolvedLocation(
                    displayLabel = label,
                    provinceHint = fuzzyProv ?: cleanState,
                    lat = lat,
                    lon = lon
                )
            } else {
                fuzzyProv?.let { p ->
                    val match = PROVINCES.firstOrNull { norm(it.shortName()) == norm(p) }
                    ResolvedLocation(trimmed, p, match?.lat, match?.lon)
                }
            }
        } catch (_: Exception) {
            fuzzyProv?.let { p ->
                val match = PROVINCES.firstOrNull { norm(it.shortName()) == norm(p) }
                ResolvedLocation(trimmed, p, match?.lat, match?.lon)
            }
        }
    }

    private fun get(u: String): String {
        val c = (URL(u).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("User-Agent", "VietLauncher/3.0 (Android TV launcher)")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
