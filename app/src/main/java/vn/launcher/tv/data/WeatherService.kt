package vn.launcher.tv.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Fetches location (GPS/network if permitted, else IP) and weather forecast from Open-Meteo. */
object WeatherService {
    data class Data(
        val temp: Double,
        val min: Double,
        val max: Double,
        val code: Int,
        val rain: Int,
        val city: String
    )

    fun fetch(
        ctx: Context,
        en: Boolean,
        manual: Pair<Double, Double>? = null,
        manualLabel: String? = null
    ): Data {
        var lat = Double.NaN
        var lon = Double.NaN
        var city: String? = null

        if (manual != null) {
            lat = manual.first
            lon = manual.second
            city = manualLabel
        } else {
            val loc = lastLocation(ctx)
            if (loc != null) {
                lat = loc.latitude
                lon = loc.longitude
                city = reverse(ctx, lat, lon, en)
            }
            if (lat.isNaN() || city == null) {
                val ip = try {
                    ipLocation()
                } catch (e: Exception) {
                    if (lat.isNaN()) throw e
                    null
                }
                if (ip != null) {
                    if (lat.isNaN()) {
                        lat = ip.getDouble("latitude")
                        lon = ip.getDouble("longitude")
                    }
                    if (city == null) city = ip.optString("city", "")
                }
            }
        }

        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=auto&forecast_days=1",
            lat,
            lon
        )
        val j = JSONObject(get(url))
        val cur = j.getJSONObject("current")
        val daily = j.getJSONObject("daily")
        val rainArr = daily.optJSONArray("precipitation_probability_max")
        val rain = if (rainArr != null && !rainArr.isNull(0)) rainArr.getInt(0) else 0
        return Data(
            temp = cur.getDouble("temperature_2m"),
            code = cur.getInt("weather_code"),
            min = daily.getJSONArray("temperature_2m_min").getDouble(0),
            max = daily.getJSONArray("temperature_2m_max").getDouble(0),
            rain = rain,
            city = city ?: ""
        )
    }

    private fun lastLocation(ctx: Context): Location? {
        return try {
            val fine = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!fine && !coarse) return null
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            var best: Location? = null
            for (p in lm.getProviders(true)) {
                try {
                    val l = lm.getLastKnownLocation(p)
                    if (l != null && (best == null || l.time > best.time)) best = l
                } catch (_: SecurityException) {
                }
            }
            best
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun reverse(ctx: Context, lat: Double, lon: Double, en: Boolean): String? {
        return try {
            if (!Geocoder.isPresent()) return null
            val g = Geocoder(ctx, if (en) Locale.ENGLISH else Locale.forLanguageTag("vi"))
            val list = g.getFromLocation(lat, lon, 1)
            if (!list.isNullOrEmpty()) {
                val a = list[0]
                a.subAdminArea ?: a.locality ?: a.adminArea
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun ipLocation(): JSONObject {
        try {
            val o = JSONObject(get("https://ipwho.is/"))
            if (o.has("latitude") && o.optBoolean("success", true)) return o
        } catch (_: Exception) {
        }
        val o = JSONObject(get("https://ipapi.co/json/"))
        if (!o.has("latitude")) throw IOException("no ip location")
        return o
    }

    private fun get(u: String): String {
        val c = (URL(u).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "VietLauncher/3.0")
        }
        return try {
            c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** @return Pair(emoji, description) for WMO weather code */
    fun describe(c: Int, en: Boolean): Pair<String, String> = when {
        c == 0 -> "☀️" to if (en) "Sunny" else "Nắng"
        c == 1 || c == 2 -> "⛅" to if (en) "Partly cloudy" else "Ít mây"
        c == 3 -> "☁️" to if (en) "Cloudy" else "Nhiều mây"
        c == 45 || c == 48 -> "🌫️" to if (en) "Fog" else "Sương mù"
        c in 51..57 -> "🌦️" to if (en) "Drizzle" else "Mưa phùn"
        c in 61..67 -> "🌧️" to if (en) "Rain" else "Mưa"
        c in 71..77 -> "❄️" to if (en) "Snow" else "Tuyết"
        c in 80..82 -> "🌧️" to if (en) "Showers" else "Mưa rào"
        c == 85 || c == 86 -> "❄️" to if (en) "Snow showers" else "Mưa tuyết"
        c >= 95 -> "⛈️" to if (en) "Thunderstorm" else "Giông bão"
        else -> "🌡️" to "—"
    }
}
