package vn.launcher.tv.data

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.Locale

/**
 * Reads ambient temperature and relative humidity from Android TV box OEM ContentProvider
 * (`content://sei.sensor.provider/`), such as SEI Robotics / FPT Play Box S.
 */
object SensorWidgetProvider {

    const val AUTHORITY = "sei.sensor.provider"
    val BASE_URI: Uri = Uri.parse("content://$AUTHORITY/")

    private val CANDIDATE_URIS = listOf(
        Uri.parse("content://$AUTHORITY/"),
        Uri.parse("content://$AUTHORITY/sensor"),
        Uri.parse("content://$AUTHORITY/sensors"),
        Uri.parse("content://$AUTHORITY/data"),
        Uri.parse("content://$AUTHORITY/latest"),
        Uri.parse("content://$AUTHORITY/temperature"),
        Uri.parse("content://$AUTHORITY/humidity"),
        Uri.parse("content://$AUTHORITY/temp"),
        Uri.parse("content://$AUTHORITY/humi")
    )

    private val TEMP_COL_NAMES = listOf(
        "temperature", "temp", "temp_c", "celsius", "ambient_temp", "ambient_temperature",
        "env_temp", "room_temp", "temperature_value", "temp_value", "t"
    )

    private val HUM_COL_NAMES = listOf(
        "humidity", "humi", "hum", "rh", "relative_humidity", "ambient_humidity",
        "env_humidity", "room_humidity", "humidity_value", "humi_value", "h"
    )

    private val TYPE_COL_NAMES = listOf(
        "type", "sensor_type", "name", "sensor_name", "sensor", "key", "code", "id", "title", "label"
    )

    private val VALUE_COL_NAMES = listOf(
        "value", "val", "data", "reading", "state", "status", "num", "cur", "current", "result"
    )

    data class SensorReading(
        val available: Boolean = false,
        val tempC: Double? = null,
        val humidity: Int? = null,
        val diagnostic: String = ""
    )

    fun isProviderInstalled(context: Context): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.resolveContentProvider(AUTHORITY, 0) != null
    } catch (_: Throwable) {
        false
    }

    fun registerObserver(context: Context, onChanged: () -> Unit): ContentObserver? {
        return try {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    onChanged()
                }

                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    onChanged()
                }
            }
            context.contentResolver.registerContentObserver(BASE_URI, true, observer)
            observer
        } catch (_: Throwable) {
            null
        }
    }

    fun unregisterObserver(context: Context, observer: ContentObserver?) {
        if (observer == null) return
        try {
            context.contentResolver.unregisterContentObserver(observer)
        } catch (_: Throwable) {
        }
    }

    fun read(context: Context): SensorReading {
        val resolver = context.contentResolver
        var providerFound = isProviderInstalled(context)
        var bestTemp: Double? = null
        var bestHum: Int? = null
        var lastError: String? = null
        val seenCols = LinkedHashSet<String>()

        for (uri in CANDIDATE_URIS) {
            if (bestTemp != null && bestHum != null) break
            try {
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    providerFound = true
                    cursor.columnNames?.forEach { if (!it.isNullOrBlank()) seenCols.add(it) }
                    val r = parseCursor(cursor, uri.lastPathSegment.orEmpty())
                    if (bestTemp == null && r.tempC != null) bestTemp = r.tempC
                    if (bestHum == null && r.humidity != null) bestHum = r.humidity
                }
            } catch (se: SecurityException) {
                providerFound = true
                lastError = "SecurityException: ${se.message ?: "permission denied"}"
            } catch (t: Throwable) {
                if (lastError == null && t !is IllegalArgumentException) {
                    lastError = t.javaClass.simpleName
                }
            }
        }

        // Fallback: try ContentResolver.call() if Cursor queries yielded incomplete data
        if (bestTemp == null || bestHum == null) {
            for (method in listOf("getSensorData", "getTemperature", "getHumidity", "get_data", "get")) {
                if (bestTemp != null && bestHum != null) break
                try {
                    val bundle: Bundle? = resolver.call(BASE_URI, method, null, null)
                    if (bundle != null) {
                        providerFound = true
                        val parsed = parseBundle(bundle)
                        if (bestTemp == null && parsed.first != null) bestTemp = parsed.first
                        if (bestHum == null && parsed.second != null) bestHum = parsed.second
                    }
                } catch (_: Throwable) {
                }
            }
        }

        val diag = buildString {
            append("sei.sensor.provider: ")
            when {
                bestTemp != null || bestHum != null -> {
                    append("OK (")
                    if (bestTemp != null) append("${bestTemp}°C")
                    if (bestTemp != null && bestHum != null) append(", ")
                    if (bestHum != null) append("${bestHum}%")
                    append(")")
                }
                lastError != null -> append(lastError)
                providerFound && seenCols.isNotEmpty() -> append("cols=[${seenCols.joinToString(",")}]")
                providerFound -> append("trống (0 bản ghi)")
                else -> append("chưa tìm thấy provider")
            }
        }

        return SensorReading(
            available = providerFound || bestTemp != null || bestHum != null,
            tempC = bestTemp,
            humidity = bestHum,
            diagnostic = diag
        )
    }

    private fun parseCursor(cursor: Cursor, uriHint: String): Pair<Double?, Int?> {
        var temp: Double? = null
        var hum: Int? = null

        // Check cursor extras bundle if present
        try {
            val extras = cursor.extras
            if (extras != null && extras != Bundle.EMPTY) {
                val b = parseBundle(extras)
                if (b.first != null) temp = b.first
                if (b.second != null) hum = b.second
            }
        } catch (_: Throwable) {
        }

        if (!cursor.moveToFirst()) return temp to hum

        val cols = cursor.columnNames ?: emptyArray()
        val lowerCols = cols.map { it.lowercase(Locale.ROOT) }
        val hintLower = uriHint.lowercase(Locale.ROOT)

        do {
            val rowValues = HashMap<String, String>(cols.size)
            for (i in cols.indices) {
                val str = readCellAsString(cursor, i) ?: continue
                rowValues[lowerCols[i]] = str
            }

            // 1. Named temperature / humidity columns
            for ((k, v) in rowValues) {
                if (temp == null && (k in TEMP_COL_NAMES || (k.contains("temp") && !k.contains("cpu") && !k.contains("soc")))) {
                    temp = parseTempValue(v)
                }
                if (hum == null && (k in HUM_COL_NAMES || k.contains("humid") || k.contains("humi"))) {
                    hum = parseHumValue(v)
                }
            }

            // 2. JSON string inside any cell
            if (temp == null || hum == null) {
                for (v in rowValues.values) {
                    val trimmed = v.trim()
                    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                        val jsonRes = parseJson(trimmed)
                        if (temp == null) temp = jsonRes.first
                        if (hum == null) hum = jsonRes.second
                    }
                }
            }

            // 3. Key/Type + Value row pattern (e.g. type="temperature", value="28.5")
            if (temp == null || hum == null) {
                val typeStr = TYPE_COL_NAMES.firstNotNullOfOrNull { rowValues[it] }?.lowercase(Locale.ROOT).orEmpty()
                val rawVal = VALUE_COL_NAMES.firstNotNullOfOrNull { rowValues[it] }
                if (rawVal != null) {
                    when {
                        typeStr.contains("temp") || typeStr == "t" || typeStr == "13" ||
                            (typeStr.isEmpty() && hintLower.contains("temp")) -> {
                            if (temp == null) temp = parseTempValue(rawVal)
                        }
                        typeStr.contains("hum") || typeStr == "rh" || typeStr == "h" || typeStr == "12" ||
                            (typeStr.isEmpty() && hintLower.contains("hum")) -> {
                            if (hum == null) hum = parseHumValue(rawVal)
                        }
                    }
                }
            }

            // 4. Single-column URI fallback (e.g., query .../temperature returns 1 numeric column)
            if (rowValues.size == 1) {
                val onlyVal = rowValues.values.first()
                if (temp == null && hintLower.contains("temp")) {
                    temp = parseTempValue(onlyVal)
                } else if (hum == null && hintLower.contains("hum")) {
                    hum = parseHumValue(onlyVal)
                }
            }
        } while (cursor.moveToNext() && (temp == null || hum == null))

        return temp to hum
    }

    private fun parseBundle(bundle: Bundle): Pair<Double?, Int?> {
        var temp: Double? = null
        var hum: Int? = null
        for (key in bundle.keySet()) {
            val k = key.lowercase(Locale.ROOT)
            @Suppress("DEPRECATION")
            val raw = bundle.get(key)?.toString() ?: continue
            if (temp == null && (k in TEMP_COL_NAMES || k.contains("temp"))) {
                temp = parseTempValue(raw)
            }
            if (hum == null && (k in HUM_COL_NAMES || k.contains("humid") || k.contains("humi"))) {
                hum = parseHumValue(raw)
            }
            if (raw.trim().startsWith("{")) {
                val j = parseJson(raw.trim())
                if (temp == null) temp = j.first
                if (hum == null) hum = j.second
            }
        }
        return temp to hum
    }

    private fun parseJson(raw: String): Pair<Double?, Int?> = try {
        val obj = JSONObject(raw)
        var temp: Double? = null
        var hum: Int? = null
        val keys = obj.keys()
        while (keys.hasNext()) {
            val orig = keys.next()
            val k = orig.lowercase(Locale.ROOT)
            val v = obj.optString(orig, "")
            if (temp == null && (k in TEMP_COL_NAMES || k.contains("temp"))) {
                temp = parseTempValue(v)
            }
            if (hum == null && (k in HUM_COL_NAMES || k.contains("humid") || k.contains("humi"))) {
                hum = parseHumValue(v)
            }
        }
        temp to hum
    } catch (_: Throwable) {
        null to null
    }

    private fun readCellAsString(cursor: Cursor, colIndex: Int): String? = try {
        when (cursor.getType(colIndex)) {
            Cursor.FIELD_TYPE_NULL -> null
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(colIndex).toString()
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(colIndex).toString()
            Cursor.FIELD_TYPE_STRING -> cursor.getString(colIndex)
            Cursor.FIELD_TYPE_BLOB -> null
            else -> cursor.getString(colIndex)
        }
    } catch (_: Throwable) {
        null
    }

    private fun parseTempValue(raw: String): Double? {
        val num = extractNumber(raw) ?: return null
        return DeviceClimateReader.normalizeTemp(num)
    }

    private fun parseHumValue(raw: String): Int? {
        val num = extractNumber(raw) ?: return null
        return DeviceClimateReader.normalizeHumidity(num)
    }

    private fun extractNumber(raw: String): Double? {
        val match = Regex("-?\\d+(?:[.,]\\d+)?").find(raw) ?: return null
        return match.value.replace(',', '.').toDoubleOrNull()
    }
}

private val Pair<Double?, Int?>.tempC: Double? get() = first
private val Pair<Double?, Int?>.humidity: Int? get() = second
