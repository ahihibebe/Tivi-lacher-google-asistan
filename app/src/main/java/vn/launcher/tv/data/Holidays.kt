package vn.launcher.tv.data

import java.util.Calendar

/** Finds the next upcoming Vietnamese solar or lunar holiday. */
object Holidays {
    data class Result(val days: Int, val name: String)

    private data class HolidayEntry(val day: Int, val month: Int, val vi: String, val en: String)

    private val SOLAR = listOf(
        HolidayEntry(1, 1, "Tết Dương lịch", "New Year's Day"),
        HolidayEntry(8, 3, "Quốc tế Phụ nữ", "International Women's Day"),
        HolidayEntry(30, 4, "Giải phóng miền Nam", "Reunification Day"),
        HolidayEntry(1, 5, "Quốc tế Lao động", "Labour Day"),
        HolidayEntry(1, 6, "Quốc tế Thiếu nhi", "Children's Day"),
        HolidayEntry(2, 9, "Quốc khánh", "National Day"),
        HolidayEntry(20, 10, "Ngày Phụ nữ Việt Nam", "Vietnamese Women's Day"),
        HolidayEntry(20, 11, "Ngày Nhà giáo Việt Nam", "Teachers' Day"),
        HolidayEntry(24, 12, "Giáng sinh", "Christmas")
    )

    private val LUNAR = listOf(
        HolidayEntry(1, 1, "Tết Nguyên Đán", "Lunar New Year (Tết)"),
        HolidayEntry(15, 1, "Rằm tháng Giêng", "Lantern Festival"),
        HolidayEntry(10, 3, "Giỗ Tổ Hùng Vương", "Hùng Kings' Festival"),
        HolidayEntry(15, 4, "Lễ Phật Đản", "Buddha's Birthday"),
        HolidayEntry(5, 5, "Tết Đoan Ngọ", "Dragon Boat Festival"),
        HolidayEntry(15, 7, "Lễ Vu Lan", "Vu Lan Festival"),
        HolidayEntry(15, 8, "Tết Trung thu", "Mid-Autumn Festival"),
        HolidayEntry(23, 12, "Ông Công Ông Táo", "Kitchen Gods Day")
    )

    fun next(from: Calendar, en: Boolean): Result? {
        for (d in 0..400) {
            val c = (from.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, d) }
            val dd = c.get(Calendar.DAY_OF_MONTH)
            val mm = c.get(Calendar.MONTH) + 1
            val yy = c.get(Calendar.YEAR)

            for (h in SOLAR) {
                if (h.day == dd && h.month == mm) {
                    return Result(d, if (en) h.en else h.vi)
                }
            }

            val l = LunarCalendar.solarToLunar(dd, mm, yy)
            if (l[3] == 0) {
                for (h in LUNAR) {
                    if (h.day == l[0] && h.month == l[1]) {
                        return Result(d, if (en) h.en else h.vi)
                    }
                }
            }
        }
        return null
    }
}
