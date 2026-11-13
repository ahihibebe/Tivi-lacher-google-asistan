package vn.launcher.tv.data

import kotlin.math.sin

/** Vietnamese Lunar Calendar conversion (UTC+7), Hồ Ngọc Đức's algorithm. */
object LunarCalendar {
    private const val TZ = 7.0
    private val CAN = arrayOf("Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý")
    private val CHI = arrayOf("Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi")

    fun format(dd: Int, mm: Int, yy: Int, en: Boolean = false): String {
        val l = solarToLunar(dd, mm, yy)
        val year = "${CAN[(l[2] + 6) % 10]} ${CHI[(l[2] + 8) % 12]}"
        val prefix = if (en) "Lunar: " else "Âm lịch: "
        val leap = if (l[3] == 1) (if (en) " leap" else " nhuận") else ""
        return "[ $prefix${l[0]}/${l[1]}$leap - $year ]"
    }

    /** @return IntArray {day, month, year, leap(0/1)} */
    fun solarToLunar(dd: Int, mm: Int, yy: Int): IntArray {
        val dayNumber = jdFromDate(dd, mm, yy)
        val k = ((dayNumber - 2415021.076998695) / 29.530588853).toInt()
        var monthStart = newMoonDay(k + 1)
        if (monthStart > dayNumber) monthStart = newMoonDay(k)
        var a11 = lunarMonth11(yy)
        var b11 = a11
        var lunarYear: Int
        if (a11 >= monthStart) {
            lunarYear = yy
            a11 = lunarMonth11(yy - 1)
        } else {
            lunarYear = yy + 1
            b11 = lunarMonth11(yy + 1)
        }
        val lunarDay = dayNumber - monthStart + 1
        val diff = (monthStart - a11) / 29
        var leap = 0
        var lunarMonth = diff + 11
        if (b11 - a11 > 365) {
            val leapDiff = leapMonthOffset(a11)
            if (diff >= leapDiff) {
                lunarMonth = diff + 10
                if (diff == leapDiff) leap = 1
            }
        }
        if (lunarMonth > 12) lunarMonth -= 12
        if (lunarMonth >= 11 && diff < 4) lunarYear -= 1
        return intArrayOf(lunarDay, lunarMonth, lunarYear, leap)
    }

    private fun jdFromDate(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        var jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (jd < 2299161) {
            jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        }
        return jd
    }

    private fun newMoon(k: Int): Double {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val dr = Math.PI / 180.0
        var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd1 += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * dr)
        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val mPr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
        var c1 = (0.1734 - 0.000393 * t) * sin(m * dr) + 0.0021 * sin(2 * dr * m)
        c1 -= 0.4068 * sin(mPr * dr) - 0.0161 * sin(dr * 2 * mPr)
        c1 -= 0.0004 * sin(dr * 3 * mPr)
        c1 += 0.0104 * sin(dr * 2 * f) - 0.0051 * sin(dr * (m + mPr))
        c1 -= 0.0074 * sin(dr * (m - mPr)) - 0.0004 * sin(dr * (2 * f + m))
        c1 -= 0.0004 * sin(dr * (2 * f - m)) + 0.0006 * sin(dr * (2 * f + mPr))
        c1 += 0.0010 * sin(dr * (2 * f - mPr)) + 0.0005 * sin(dr * (2 * mPr + m))
        val deltaT = if (t < -11) {
            0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        } else {
            -0.000278 + 0.000265 * t + 0.000262 * t2
        }
        return jd1 + c1 - deltaT
    }

    private fun sunLongitude(jdn: Double): Double {
        val t = (jdn - 2451545.0) / 36525.0
        val t2 = t * t
        val dr = Math.PI / 180.0
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(dr * m)
        dl += (0.019993 - 0.000101 * t) * sin(dr * 2 * m) + 0.000290 * sin(dr * 3 * m)
        var l = (l0 + dl) * dr
        l -= Math.PI * 2 * (l / (Math.PI * 2)).toInt()
        return l
    }

    private fun sunLongitudeIndex(dayNumber: Int): Int {
        return (sunLongitude(dayNumber - 0.5 - TZ / 24.0) / Math.PI * 6).toInt()
    }

    private fun newMoonDay(k: Int): Int {
        return (newMoon(k) + 0.5 + TZ / 24.0).toInt()
    }

    private fun lunarMonth11(yy: Int): Int {
        val off = jdFromDate(31, 12, yy) - 2415021.0
        val k = (off / 29.530588853).toInt()
        var nm = newMoonDay(k)
        if (sunLongitudeIndex(nm) >= 9) nm = newMoonDay(k - 1)
        return nm
    }

    private fun leapMonthOffset(a11: Int): Int {
        val k = ((a11 - 2415021.076998695) / 29.530588853 + 0.5).toInt()
        var last: Int
        var i = 1
        var arc = sunLongitudeIndex(newMoonDay(k + i))
        do {
            last = arc
            i++
            arc = sunLongitudeIndex(newMoonDay(k + i))
        } while (arc != last && i < 14)
        return i - 1
    }
}
