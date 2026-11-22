package vn.launcher.tv.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Discovers and parses ambient temperature and relative humidity from both Android SensorManager
 * (including vendor/private sensor types and combo sensors) and non-SoC Linux sysfs nodes
 * (IIO, hwmon, virtual sensor classes) used by smart TV boxes such as FPT Play Box S.
 */
object DeviceClimateReader {

    data class SysfsReading(
        val hasTempNode: Boolean = false,
        val hasHumidityNode: Boolean = false,
        val tempC: Double? = null,
        val humidity: Int? = null,
        val sourceLabel: String = ""
    )

    data class ParsedEvent(
        val tempC: Double? = null,
        val humidity: Int? = null
    )

    private val excludedKeywords = listOf(
        "cpu", "gpu", "soc", "ddr", "battery", "batt", "skin", "board",
        "pmic", "modem", "charger", "usb", "vpu", "npu", "pa_therm",
        "xo_therm", "quiet_therm", "backlight", "display", "panel", "wifi", "wlan",
        "tsens", "aml_thermal", "scpi", "thermal_zone"
    )

    private val envChipKeywords = listOf(
        "ambient", "room", "environment", "climate", "weather",
        "sht", "aht", "dht", "bme", "bmp", "hdc", "si70", "hts221",
        "ens210", "gxht", "cht8", "tmp1", "rogo", "fpt"
    )

    private val tempKeywords = listOf(
        "ambient_temperature", "temperature", "temp", "tmp"
    )

    private val humKeywords = listOf(
        "relative_humidity", "humidity", "humid", "humi", "rh_", "_rh"
    )

    fun isAmbientTempSensor(s: Sensor): Boolean {
        if (s.type == Sensor.TYPE_AMBIENT_TEMPERATURE) return true
        val meta = sensorMeta(s)
        @Suppress("DEPRECATION")
        if (s.type == Sensor.TYPE_TEMPERATURE) {
            return excludedKeywords.none { meta.contains(it) }
        }
        if (excludedKeywords.any { meta.contains(it) }) return false
        return envChipKeywords.any { meta.contains(it) } || tempKeywords.any { meta.contains(it) }
    }

    fun isHumiditySensor(s: Sensor): Boolean {
        if (s.type == Sensor.TYPE_RELATIVE_HUMIDITY) return true
        val meta = sensorMeta(s)
        if (excludedKeywords.any { meta.contains(it) }) return false
        return humKeywords.any { meta.contains(it) } ||
            (envChipKeywords.any { meta.contains(it) } && (meta.contains("sht") || meta.contains("aht") ||
                meta.contains("dht") || meta.contains("bme") || meta.contains("hdc") ||
                meta.contains("si70") || meta.contains("hts") || meta.contains("gxht") ||
                meta.contains("cht") || meta.contains("rogo") || meta.contains("humi")))
    }

    private fun sensorMeta(s: Sensor): String {
        val st = try { s.stringType.orEmpty() } catch (_: Throwable) { "" }
        val vendor = try { s.vendor.orEmpty() } catch (_: Throwable) { "" }
        return "${s.name} $st $vendor".lowercase(Locale.ROOT)
    }

    fun parseSensorEvent(
        ev: SensorEvent,
        isRegisteredTemp: Boolean,
        isRegisteredHum: Boolean
    ): ParsedEvent {
        val vals = ev.values
        if (vals.isEmpty()) return ParsedEvent()
        val s = ev.sensor
        val byTypeTemp = s.type == Sensor.TYPE_AMBIENT_TEMPERATURE ||
            @Suppress("DEPRECATION") (s.type == Sensor.TYPE_TEMPERATURE)
        val byTypeHum = s.type == Sensor.TYPE_RELATIVE_HUMIDITY

        var parsedTemp: Double? = null
        var parsedHum: Int? = null

        if ((isRegisteredTemp || byTypeTemp) && (isRegisteredHum || byTypeHum) && vals.size >= 2) {
            parsedTemp = normalizeTemp(vals[0].toDouble())
            parsedHum = normalizeHumidity(vals[1].toDouble())
        } else if (isRegisteredTemp || byTypeTemp) {
            parsedTemp = normalizeTemp(vals[0].toDouble())
            if (vals.size >= 2 && isHumiditySensor(s)) {
                parsedHum = normalizeHumidity(vals[1].toDouble())
            }
        } else if (isRegisteredHum || byTypeHum) {
            parsedHum = normalizeHumidity(vals[0].toDouble())
        }

        return ParsedEvent(tempC = parsedTemp, humidity = parsedHum)
    }

    fun normalizeTemp(raw: Double?): Double? {
        if (raw == null || raw.isNaN() || raw.isInfinite()) return null
        val c = when {
            raw in -40.0..85.0 -> raw
            raw > 15000.0 && raw <= 100000.0 -> raw / 1000.0
            raw > 150.0 && raw <= 10000.0 -> raw / 100.0
            raw > 85.0 && raw <= 150.0 -> {
                val div10 = raw / 10.0
                if (div10 in 5.0..60.0) div10 else return null
            }
            else -> return null
        }
        if (c !in -40.0..85.0) return null
        return (c * 10.0).roundToInt() / 10.0
    }

    fun normalizeHumidity(raw: Double?): Int? {
        if (raw == null || raw.isNaN() || raw.isInfinite()) return null
        val h = when {
            raw in 0.0..100.0 -> raw
            raw > 10000.0 && raw <= 100000.0 -> raw / 1000.0
            raw > 100.0 && raw <= 10000.0 -> raw / 100.0
            else -> return null
        }
        if (h !in 0.0..100.0) return null
        return h.roundToInt().coerceIn(0, 100)
    }

    fun buildSensorDiagnostic(
        sm: SensorManager?,
        tempSensors: List<Sensor>,
        humSensors: List<Sensor>,
        sysfsReading: SysfsReading?
    ): String {
        val parts = mutableListOf<String>()
        if (tempSensors.isNotEmpty() || humSensors.isNotEmpty()) {
            val names = (tempSensors + humSensors).distinctBy { "${it.type}:${it.name}" }
                .joinToString(", ") { "${it.name} (type=${it.type})" }
            parts += "SensorManager: $names"
        } else if (sm != null) {
            val all = try { sm.getSensorList(Sensor.TYPE_ALL) } catch (_: Throwable) { emptyList() }
            if (all.isEmpty()) {
                parts += "SensorManager: 0 sensors"
            } else {
                val sample = all.take(6).joinToString(", ") { "${it.name}[${it.type}]" }
                parts += "SensorManager (${all.size}): $sample"
            }
        }
        if (sysfsReading != null && (sysfsReading.hasTempNode || sysfsReading.hasHumidityNode)) {
            parts += "Sysfs: ${sysfsReading.sourceLabel}"
        }
        return parts.joinToString(" · ")
    }

    fun readSysfsAndProps(context: Context): SysfsReading {
        var hasTempNode = false
        var hasHumNode = false
        var bestTemp: Double? = null
        var bestHum: Int? = null
        val labels = mutableListOf<String>()

        // 1. Scan Linux IIO devices (/sys/bus/iio/devices/iio:device*)
        readIioDevices().let { r ->
            if (r.hasTempNode) hasTempNode = true
            if (r.hasHumidityNode) hasHumNode = true
            if (bestTemp == null) bestTemp = r.tempC
            if (bestHum == null) bestHum = r.humidity
            if (r.sourceLabel.isNotEmpty()) labels += r.sourceLabel
        }

        // 2. Scan hwmon (/sys/class/hwmon/hwmon*)
        if (bestTemp == null || bestHum == null) {
            readHwmonDevices().let { r ->
                if (r.hasTempNode) hasTempNode = true
                if (r.hasHumidityNode) hasHumNode = true
                if (bestTemp == null) bestTemp = r.tempC
                if (bestHum == null) bestHum = r.humidity
                if (r.sourceLabel.isNotEmpty()) labels += r.sourceLabel
            }
        }

        // 3. Scan OEM/virtual class directories matching climate/sensor names
        if (bestTemp == null || bestHum == null) {
            readCustomSysfsClasses().let { r ->
                if (r.hasTempNode) hasTempNode = true
                if (r.hasHumidityNode) hasHumNode = true
                if (bestTemp == null) bestTemp = r.tempC
                if (bestHum == null) bestHum = r.humidity
                if (r.sourceLabel.isNotEmpty()) labels += r.sourceLabel
            }
        }

        // 4. Check common OEM system properties (Rogo / FPT Smart Home)
        if (bestTemp == null || bestHum == null) {
            readSystemProperties(context).let { r ->
                if (r.hasTempNode) hasTempNode = true
                if (r.hasHumidityNode) hasHumNode = true
                if (bestTemp == null) bestTemp = r.tempC
                if (bestHum == null) bestHum = r.humidity
                if (r.sourceLabel.isNotEmpty()) labels += r.sourceLabel
            }
        }

        return SysfsReading(
            hasTempNode = hasTempNode,
            hasHumidityNode = hasHumNode,
            tempC = bestTemp,
            humidity = bestHum,
            sourceLabel = labels.distinct().joinToString(", ")
        )
    }

    private fun readIioDevices(): SysfsReading {
        val dir = File("/sys/bus/iio/devices")
        val devs = safeListFiles(dir)
        var hasTemp = false
        var hasHum = false
        var tempVal: Double? = null
        var humVal: Int? = null
        val labels = mutableListOf<String>()

        for (dev in devs) {
            if (!dev.isDirectory) continue
            val name = readTrimmed(File(dev, "name")).lowercase(Locale.ROOT)
            val hasHumFile = File(dev, "in_humidityrelative_input").canRead() ||
                File(dev, "in_humidity_input").canRead() ||
                File(dev, "in_humidityrelative_raw").canRead()
            val envMatch = hasHumFile || (envChipKeywords.any { name.contains(it) } && excludedKeywords.none { name.contains(it) })
            if (!envMatch) continue

            val t = readNumber(File(dev, "in_temp_input"))?.let(::normalizeTemp)
                ?: readIioScaled(dev, "in_temp_raw", "in_temp_scale", "in_temp_offset")?.let(::normalizeTemp)
            val h = readNumber(File(dev, "in_humidityrelative_input"))?.let(::normalizeHumidity)
                ?: readNumber(File(dev, "in_humidity_input"))?.let(::normalizeHumidity)
                ?: readIioScaled(dev, "in_humidityrelative_raw", "in_humidityrelative_scale", "in_humidityrelative_offset")?.let(::normalizeHumidity)

            if (File(dev, "in_temp_input").canRead() || File(dev, "in_temp_raw").canRead()) hasTemp = true
            if (hasHumFile) hasHum = true
            if (t != null && tempVal == null) tempVal = t
            if (h != null && humVal == null) humVal = h
            if (t != null || h != null || hasHumFile) {
                labels += (name.ifEmpty { dev.name })
            }
        }
        return SysfsReading(hasTemp, hasHum, tempVal, humVal, labels.joinToString(", "))
    }

    private fun readHwmonDevices(): SysfsReading {
        val dir = File("/sys/class/hwmon")
        val mons = safeListFiles(dir)
        var hasTemp = false
        var hasHum = false
        var tempVal: Double? = null
        var humVal: Int? = null
        val labels = mutableListOf<String>()

        for (mon in mons) {
            if (!mon.isDirectory) continue
            val name = readTrimmed(File(mon, "name")).ifEmpty {
                readTrimmed(File(mon, "device/name"))
            }.lowercase(Locale.ROOT)
            val humFile = listOf("humidity1_input", "humidity_input", "in_humidityrelative_input")
                .map { File(mon, it) }
                .firstOrNull { it.canRead() }
            val envMatch = humFile != null || (envChipKeywords.any { name.contains(it) } && excludedKeywords.none { name.contains(it) })
            if (!envMatch) continue

            val tempFile = listOf("temp1_input", "temp_input", "in_temp_input")
                .map { File(mon, it) }
                .firstOrNull { it.canRead() }

            if (tempFile != null) hasTemp = true
            if (humFile != null) hasHum = true

            val t = tempFile?.let(::readNumber)?.let(::normalizeTemp)
            val h = humFile?.let(::readNumber)?.let(::normalizeHumidity)
            if (t != null && tempVal == null) tempVal = t
            if (h != null && humVal == null) humVal = h
            if (t != null || h != null || humFile != null) {
                labels += (name.ifEmpty { mon.name })
            }
        }
        return SysfsReading(hasTemp, hasHum, tempVal, humVal, labels.joinToString(", "))
    }

    private fun readCustomSysfsClasses(): SysfsReading {
        val roots = listOf(File("/sys/class"), File("/sys/devices/virtual"))
        var hasTemp = false
        var hasHum = false
        var tempVal: Double? = null
        var humVal: Int? = null
        val labels = mutableListOf<String>()

        for (root in roots) {
            val entries = safeListFiles(root)
            for (entry in entries) {
                if (!entry.isDirectory) continue
                val n = entry.name.lowercase(Locale.ROOT)
                if (excludedKeywords.any { n.contains(it) }) continue
                val interesting = envChipKeywords.any { n.contains(it) } ||
                    n.contains("humi") || n.contains("temp_hum") || n.contains("sensor")
                if (!interesting) continue

                val filesToCheck = mutableListOf<File>()
                filesToCheck.addAll(safeListFiles(entry))
                safeListFiles(entry).filter { it.isDirectory }.take(4).forEach { sub ->
                    filesToCheck.addAll(safeListFiles(sub))
                }
                for (f in filesToCheck) {
                    if (!f.isFile || !f.canRead()) continue
                    val fn = f.name.lowercase(Locale.ROOT)
                    if (fn.contains("humid") || fn == "rh" || fn.contains("rh_")) {
                        hasHum = true
                        val h = readNumber(f)?.let(::normalizeHumidity)
                        if (h != null && humVal == null) {
                            humVal = h
                            labels += "${entry.name}/${f.name}"
                        }
                    } else if ((fn.contains("temp") || fn == "value") && excludedKeywords.none { fn.contains(it) }) {
                        val raw = readNumber(f) ?: continue
                        val t = normalizeTemp(raw)
                        if (t != null && tempVal == null) {
                            hasTemp = true
                            tempVal = t
                            labels += "${entry.name}/${f.name}"
                        }
                    }
                }
            }
        }
        return SysfsReading(hasTemp, hasHum, tempVal, humVal, labels.distinct().joinToString(", "))
    }

    private fun readSystemProperties(context: Context): SysfsReading {
        var hasTemp = false
        var hasHum = false
        var tempVal: Double? = null
        var humVal: Int? = null
        val labels = mutableListOf<String>()

        val tempKeys = listOf(
            "persist.sys.rogo.temp", "sys.rogo.temp", "persist.sys.ambient.temp",
            "sys.ambient.temp", "persist.sys.sensor.temp", "vendor.sensor.ambient_temp",
            "ro.fpt.ambient.temp", "sys.fpt.temp"
        )
        val humKeys = listOf(
            "persist.sys.rogo.humidity", "sys.rogo.humidity", "persist.sys.ambient.humidity",
            "sys.ambient.humidity", "persist.sys.sensor.humidity", "vendor.sensor.humidity",
            "ro.fpt.ambient.humidity", "sys.fpt.humidity"
        )

        for (k in tempKeys) {
            val v = getSysProp(k).toDoubleOrNull()?.let(::normalizeTemp)
            if (v != null) {
                hasTemp = true
                if (tempVal == null) {
                    tempVal = v
                    labels += k
                }
            }
        }
        for (k in humKeys) {
            val v = getSysProp(k).toDoubleOrNull()?.let(::normalizeHumidity)
            if (v != null) {
                hasHum = true
                if (humVal == null) {
                    humVal = v
                    labels += k
                }
            }
        }

        // Also check Settings.System / Settings.Global if OEM stores sensor values there
        val cr = context.contentResolver
        for (k in listOf("ambient_temperature", "rogo_temperature", "fpt_temperature")) {
            val s = try {
                android.provider.Settings.Global.getString(cr, k)
                    ?: android.provider.Settings.System.getString(cr, k)
            } catch (_: Throwable) {
                null
            }
            val v = s?.trim()?.toDoubleOrNull()?.let(::normalizeTemp)
            if (v != null && tempVal == null) {
                hasTemp = true
                tempVal = v
                labels += "settings:$k"
            }
        }
        for (k in listOf("relative_humidity", "ambient_humidity", "rogo_humidity", "fpt_humidity")) {
            val s = try {
                android.provider.Settings.Global.getString(cr, k)
                    ?: android.provider.Settings.System.getString(cr, k)
            } catch (_: Throwable) {
                null
            }
            val v = s?.trim()?.toDoubleOrNull()?.let(::normalizeHumidity)
            if (v != null && humVal == null) {
                hasHum = true
                humVal = v
                labels += "settings:$k"
            }
        }

        return SysfsReading(hasTemp, hasHum, tempVal, humVal, labels.joinToString(", "))
    }

    private fun readIioScaled(dev: File, rawName: String, scaleName: String, offsetName: String): Double? {
        val raw = readNumber(File(dev, rawName)) ?: return null
        val scale = readNumber(File(dev, scaleName)) ?: 1.0
        val offset = readNumber(File(dev, offsetName)) ?: 0.0
        return (raw + offset) * scale
    }

    private fun safeListFiles(dir: File): List<File> = try {
        if (dir.exists() && dir.isDirectory && dir.canRead()) {
            dir.listFiles()?.toList().orEmpty()
        } else {
            emptyList()
        }
    } catch (_: Throwable) {
        emptyList()
    }

    private fun readTrimmed(file: File): String = try {
        if (file.isFile && file.canRead()) file.readText().trim() else ""
    } catch (_: Throwable) {
        ""
    }

    private fun readNumber(file: File): Double? {
        val text = readTrimmed(file)
        if (text.isEmpty()) return null
        val token = text.split(Regex("\\s+")).firstOrNull()?.replace(",", ".") ?: return null
        return token.toDoubleOrNull()
    }

    @Suppress("PrivateApi")
    private fun getSysProp(key: String): String = try {
        val cls = Class.forName("android.os.SystemProperties")
        val m = cls.getMethod("get", String::class.java, String::class.java)
        (m.invoke(null, key, "") as? String).orEmpty().trim()
    } catch (_: Throwable) {
        ""
    }
}
