package vn.launcher.tv.data

import vn.launcher.tv.model.AppItem

/** Voice channel matching rules and default Vietnamese TV channel link lists. */
object VoiceChannelDirectory {

    val CHANNEL_REGEX = Regex("(vtv|htv)\\d{1,2}|(thvl|vtc|antv|vov)\\d{0,2}|vtvcantho|quochoi|nhandan")
    val VOICE_ON = Regex("^(.*?)\\s+(?:tren ung dung|tren|trong|bang|qua|on|in|using|with)\\s+(.+)$")

    const val DEFAULT_CHANNEL_LIST =
        "THVL1|https://fptplay.vn/xem-truyen-hinh/thvl1, THVL2|https://fptplay.vn/xem-truyen-hinh/thvl2, " +
            "VTV1|https://fptplay.vn/xem-truyen-hinh/vtv1-hd, VTV2|https://fptplay.vn/xem-truyen-hinh/vtv2-hd, " +
            "VTV3|https://fptplay.vn/xem-truyen-hinh/vtv3-hd, VTV4|https://fptplay.vn/xem-truyen-hinh/vtv4-hd, " +
            "VTV5|https://fptplay.vn/xem-truyen-hinh/vtv5-hd, VTV6|https://fptplay.vn/xem-truyen-hinh/vtv6-hd, " +
            "VTV7|https://fptplay.vn/xem-truyen-hinh/vtv7-hd, VTV8|https://fptplay.vn/xem-truyen-hinh/vtv8-hd, " +
            "VTV9|https://fptplay.vn/xem-truyen-hinh/vtv9-hd, HTV7|https://fptplay.vn/xem-truyen-hinh/htv7-hd, " +
            "HTV9|https://fptplay.vn/xem-truyen-hinh/htv9-hd"

    fun defaultVtvgoList(): String = (1..9).joinToString(", ") { i ->
        "VTV$i|https://vtvgo.vn/xem-truc-tuyen-kenh-vtv$i-$i.html"
    }

    fun defaultVieonList(): String = (1..9).joinToString(", ") { i ->
        "VTV$i|https://vieon.vn/truyen-hinh-truc-tuyen/vtv$i-hd"
    }

    fun lookupChannel(list: String, normalizedKey: String): Pair<String, String>? {
        for (e in list.split(",")) {
            val bar = e.indexOf('|')
            if (bar <= 0) continue
            val name = e.substring(0, bar).trim()
            val link = e.substring(bar + 1).trim()
            if (link.isNotEmpty() && VoiceCommand.key(VoiceCommand.norm(name)) == normalizedKey) {
                return name to link
            }
        }
        return null
    }

    fun voiceScore(app: AppItem, qk: String): Int {
        val lk = VoiceCommand.key(VoiceCommand.norm(app.label))
        var sc = VoiceCommand.scoreKeys(lk, qk)
        if (VoiceCommand.canonApp(lk) == qk) sc = maxOf(sc, 100)
        if (qk == "googleplay" && (app.pkg == "com.android.vending" || lk.contains("googleplay") ||
                lk.contains("chplay") || lk.contains("cuahangplay") || lk.contains("playstore"))
        ) {
            sc = maxOf(sc, 100)
        }
        return sc
    }
}
