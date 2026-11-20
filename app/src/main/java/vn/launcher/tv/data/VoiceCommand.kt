package vn.launcher.tv.data

import vn.launcher.tv.model.PriceKind
import java.text.Normalizer
import java.util.Locale

/** Voice command normalization, verb stripping, digit conversion, intent parsing, and fuzzy matching. */
object VoiceCommand {

    data class SearchIntent(val query: String, val onYouTube: Boolean, val explicitBrowser: Boolean = false)

    data class PriceIntent(
        val kind: PriceKind,
        val zone: Int = 0,
        val spokenLocation: String? = null
    )

    private val VERBS = arrayOf(
        "cho toi hoi", "cho hoi", "mo ung dung", "mo kenh", "bat kenh", "chuyen sang kenh",
        "chuyen sang", "chuyen den", "chuyen kenh", "xem kenh", "khoi dong",
        "mo", "bat", "chay", "xem", "kenh",
        "open app", "open", "launch", "start", "go to", "switch to", "watch channel",
        "watch", "play", "channel"
    )

    private val WORDS = arrayOf(
        "khong", "mot", "hai", "ba", "bon", "nam", "sau", "bay", "tam", "chin", "muoi",
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"
    )
    private val VALUES = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

    private val APP_ALIASES = arrayOf(
        arrayOf("tizentube", "tizen", "tizentube"),
        arrayOf("getout", "getout"),
        arrayOf("googleplay", "googleplay", "chplay", "cuahangplay", "playstore", "cuahanggoogleplay", "googleplaystore"),
        arrayOf("vietmitv", "vietmitv", "vietmi"),
        arrayOf("vtvgo", "vtvgo", "vtvgotv"),
        arrayOf("fptplay", "fptplay", "fpt"),
        arrayOf("tv360", "tv360"),
        arrayOf("cliptv", "cliptv"),
        arrayOf("zingmp3", "zingmp3", "zing"),
        arrayOf("netflix", "netflix"),
        arrayOf("youtube", "youtube", "duatup", "dutube")
    )

    private val VINH_LONG = Regex("^(?:truyen hinh |tv |dai |kenh )?(?:vinh long|thvl)\\s*(\\d)$")

    fun norm(s: String?): String {
        val t = Normalizer.normalize(s ?: "", Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace('đ', 'd')
        return t.replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    fun stripVerb(n: String): String {
        var t = n.trim()
        var changed = true
        while (changed) {
            changed = false
            for (v in VERBS) {
                if (t == v) return ""
                if (t.startsWith("$v ")) {
                    t = t.substring(v.length + 1).trim()
                    changed = true
                    break
                }
            }
        }
        return t
    }

    fun digitWords(n: String): String {
        val w = n.split(" ")
        val sb = StringBuilder()
        for (i in w.indices) {
            var x = w[i]
            if (i > 0) {
                for (k in WORDS.indices) {
                    if (x == WORDS[k]) {
                        x = VALUES[k].toString()
                        break
                    }
                }
            }
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(x)
        }
        return sb.toString()
    }

    fun key(n: String): String = n.replace(" ", "")

    fun has(full: String, vararg phrases: String): Boolean {
        val f = " $full "
        return phrases.any { f.contains(" $it ") }
    }

    fun canonApp(key: String): String {
        for (a in APP_ALIASES) {
            for (i in 1 until a.size) {
                if (a[i] == key) return a[0]
            }
        }
        return key
    }

    fun scoreKeys(lk: String, qk: String): Int {
        if (lk.isEmpty() || qk.isEmpty()) return 0
        if (lk == qk) return 100
        if (qk.length >= 3 && lk.startsWith(qk)) return 85
        if (lk.length >= 3 && qk.startsWith(lk)) return 80
        if (qk.length >= 4 && lk.contains(qk)) return 65
        if (lk.length >= 4 && qk.contains(lk)) return 60
        return 0
    }

    fun canonChannel(n: String): String {
        val t = n.trim()
        val m = VINH_LONG.matchEntire(t)
        if (m != null) return "thvl${m.groupValues[1]}"
        return when {
            t == "vtv can tho" || t == "can tho" -> "vtvcantho"
            t == "quoc hoi" || t == "truyen hinh quoc hoi" -> "quochoi"
            t == "nhan dan" || t == "truyen hinh nhan dan" -> "nhandan"
            else -> t
        }
    }

    fun parseAiPrompt(raw: String, full: String): String? {
        val explicitAiRegex = Regex(
            "^(?:hoi google ai|google ai|hoi ai|tro ly ai|gemini|ai tra loi|tim kiem ai|tim bang ai|ask google ai|ask ai|ask gemini)\\s+(.+)$"
        )
        explicitAiRegex.matchEntire(full)?.let { m ->
            val q = extractRawTail(raw, m.groupValues[1])
            if (q.isNotBlank()) return q
        }
        val questionRegex = Regex(
            "^(?:cho toi hoi|cho hoi|giai thich|tom tat|cach lam|lam sao|tai sao|vi sao|khi nao|o dau|bao nhieu|la gi|nhu the nao|ai la|what is|who is|why|how to|explain)\\s+(.+)$"
        )
        questionRegex.matchEntire(full)?.let { _ ->
            return raw.trim()
        }
        return null
    }

    fun parseSearchIntent(raw: String, full: String): SearchIntent? {
        val ytRegex = Regex(
            "^(?:tim kiem tren|tim tren|mo youtube tim|tim kiem youtube|youtube tim|search youtube(?: for)?|youtube)\\s+(.+)$"
        )
        ytRegex.matchEntire(full)?.let { m ->
            val q = extractRawTail(raw, m.groupValues[1])
            return SearchIntent(q, onYouTube = true, explicitBrowser = true)
        }

        val browserRegex = Regex(
            "^(?:mo google tim|tim tren google|tim tren web|google search(?: for)?)\\s+(.+)$"
        )
        browserRegex.matchEntire(full)?.let { m ->
            val q = extractRawTail(raw, m.groupValues[1])
            if (q.isNotBlank()) return SearchIntent(q, onYouTube = false, explicitBrowser = true)
        }

        val searchRegex = Regex(
            "^(?:tim kiem|tim|tra cuu|hoi google|search(?: for)?|find|google)\\s+(.+)$"
        )
        searchRegex.matchEntire(full)?.let { m ->
            val tailNorm = m.groupValues[1].trim()
            val onYt = tailNorm.startsWith("tren youtube ") || tailNorm.endsWith(" tren youtube")
            val cleaned = tailNorm
                .removePrefix("tren youtube ")
                .removeSuffix(" tren youtube")
                .trim()
            val q = if (onYt) cleaned else extractRawTail(raw, cleaned)
            if (q.isNotBlank()) return SearchIntent(q, onYouTube = onYt, explicitBrowser = onYt)
        }

        if (full == "tim kiem" || full == "search" || full == "google assistant" || full == "tro ly google") {
            return SearchIntent("", onYouTube = false, explicitBrowser = true)
        }
        return null
    }

    fun parseWeatherLocation(raw: String, full: String): String? {
        if (!has(full, "thoi tiet", "weather", "nhiet do", "troi co mua")) return null
        val after = full.replace(
            Regex("^.*?(?:thoi tiet|weather|nhiet do)\\s*"),
            ""
        ).replace(
            Regex("\\b(?:hom nay|ngay mai|bay gio|hien tai|la bao nhieu|nhu the nao|ra sao|co mua khong|today|now|forecast)\\b"),
            " "
        ).replace(Regex("^(?:o|tai|khu vuc|in|at|for)\\s+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (after.length < 2) return null
        return extractRawTail(raw, after)
    }

    fun parsePriceIntent(raw: String, full: String): PriceIntent? {
        // 1. Gold & ring gold
        if (has(full, "gia vang", "vang nhan", "gia vang nhan", "vang sjc", "vang 9999", "vang mieng", "bang gia vang", "gold price", "price of gold")) {
            return PriceIntent(PriceKind.GOLD)
        }
        // 2. Fuel
        if (has(full, "gia xang", "gia dau", "gia xang dau", "xang dau", "xang hom nay", "bang gia xang", "fuel price", "petrol price", "gas price")) {
            val z = Regex("vung\\s*(\\d)").find(full)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            return PriceIntent(PriceKind.FUEL, zone = z)
        }
        // 3. Pepper
        if (has(full, "gia tieu", "gia ho tieu", "ho tieu", "pepper price")) {
            val loc = extractLocationAfter(
                raw = raw,
                full = full,
                anchorRegex = Regex("^.*?(?:gia ho tieu|gia tieu|ho tieu|pepper price)\\s*")
            )
            return PriceIntent(PriceKind.PEPPER, spokenLocation = loc)
        }
        // 4. Coffee ("gia ca phe", colloquial "gia ca", "ca phe")
        val isCoffee = has(full, "gia ca phe", "ca phe", "coffee price", "gia ca hom nay", "hoi gia ca", "xem gia ca") ||
            Regex("\\bgia ca\\s+(?:o|tai|khu vuc|hom nay|tinh|xa|huyen|phuong)\\b").containsMatchIn(full) ||
            full == "gia ca"
        if (isCoffee) {
            val loc = extractLocationAfter(
                raw = raw,
                full = full,
                anchorRegex = Regex("^.*?(?:gia ca phe|ca phe|gia ca|coffee price)\\s*")
            )
            return PriceIntent(PriceKind.COFFEE, spokenLocation = loc)
        }
        return null
    }

    private fun extractLocationAfter(raw: String, full: String, anchorRegex: Regex): String? {
        var tail = anchorRegex.replace(full, "")
        tail = tail
            .replace(Regex("\\b(?:hom nay|hien tai|moi nhat|cap nhat|theo ngay|giacaphe com|giacaphe|la bao nhieu|bao nhieu|nhu the nao|o dau|today|now)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replace(Regex("^(?:o|tai|khu vuc|vung|cua|cho|in|at|for)\\s+"), "")
            .replace(Regex("\\s+(?:hom nay|hien tai|moi nhat)$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (tail.length < 2) return null
        return extractRawTail(raw, tail)
    }

    /** Preserves Vietnamese diacritics from raw text when tail tokens match normalized tail. */
    private fun extractRawTail(raw: String, normalizedFallback: String): String {
        val rawWords = raw.trim().split(Regex("\\s+"))
        val tailWords = normalizedFallback.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tailWords.isEmpty() || rawWords.size < tailWords.size) return normalizedFallback
        // Find contiguous slice in raw whose norm matches tailWords
        for (start in 0..(rawWords.size - tailWords.size)) {
            val slice = rawWords.subList(start, start + tailWords.size).joinToString(" ")
            if (digitWords(norm(slice)) == tailWords.joinToString(" ")) {
                return slice.replace(Regex("[?.!,]+$"), "").trim()
            }
        }
        return normalizedFallback
    }
}
