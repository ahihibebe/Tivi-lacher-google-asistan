package vn.launcher.tv.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import vn.launcher.tv.model.PriceMatchRow
import vn.launcher.tv.model.PriceKind
import vn.launcher.tv.model.PriceSection
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.zip.GZIPInputStream

/** Fetches, parses, filters (by spoken province/ward), and summarizes live price tables. */
object PriceRepository {

    data class PriceFetchResult(
        val sections: List<PriceSection>,
        val updatedDate: String,
        val sourceLabel: String
    )

    val EXTRACT_ALL_TABLES_JS: String = """
        (function(){
          function clean(s){return (s||'').replace(/\s+/g,' ').trim();}
          function isProvinceLike(s){
            var t=clean(s).toLowerCase();
            return /đắk lắk|dak lak|lâm đồng|lam dong|gia lai|đắk nông|dak nong|kon tum|đồng nai|dong nai|tây nguyên|nội địa|trong nước|hồ chí minh|tp\.?\s*hcm/.test(t);
          }
          function findHeading(el){
            var cur=el;
            for(var step=0;step<4&&cur;step++){
              var prev=cur.previousElementSibling;
              while(prev){
                if(/^(H[1-4]|CAPTION|STRONG|P|DIV)$/i.test(prev.tagName)){
                  var t=clean(prev.innerText);
                  if(t.length>=4&&t.length<=90) return t;
                }
                prev=prev.previousElementSibling;
              }
              cur=cur.parentElement;
            }
            return '';
          }
          var out=[];
          var tables=document.querySelectorAll('table');
          for(var i=0;i<tables.length&&out.length<24;i++){
            var t=tables[i];
            var rows=t.querySelectorAll('tr');
            var headers=[];
            var body=[];
            var numRows=0;
            var curGroup='';
            for(var r=0;r<rows.length&&body.length<80;r++){
              var tr=rows[r];
              var ths=tr.querySelectorAll('th');
              var tds=tr.querySelectorAll('td');
              if(ths.length>=2&&tds.length===0&&headers.length===0){
                for(var c=0;c<ths.length;c++) headers.push(clean(ths[c].innerText));
              } else {
                var cells=tr.querySelectorAll('th,td');
                var row=[];
                var hasNum=false;
                for(var c=0;c<cells.length;c++){
                  var v=clean(cells[c].innerText);
                  if(c>0&&/\d{2,}/.test(v)) hasNum=true;
                  row.push(v);
                }
                if(row.length===1&&row[0].length>=2&&row[0].length<=50&&!/\d{2,}/.test(row[0])){
                  if(isProvinceLike(row[0])) curGroup=row[0];
                  continue;
                }
                if(row.length>=2){
                  if(headers.length===0&&body.length===0&&!hasNum){
                    headers=row;
                  } else {
                    if(curGroup&&row[0]&&row[0].toLowerCase().indexOf(curGroup.toLowerCase())===-1&&!isProvinceLike(row[0])){
                      row[0]=row[0]+' ('+curGroup+')';
                    }
                    if(hasNum) numRows++;
                    body.push(row);
                  }
                }
              }
            }
            if(numRows>=1&&body.length>=1){
              var cap=t.querySelector('caption');
              var title=cap?clean(cap.innerText):findHeading(t);
              out.push({title:title,headers:headers,rows:body});
            }
          }
          // Fallback: scan div/ul price lists if no domestic table or extra regional div blocks exist
          var listContainers=document.querySelectorAll('ul,ol,div,section');
          for(var lc=0;lc<listContainers.length&&out.length<28;lc++){
            var box=listContainers[lc];
            if(box.closest('table')) continue;
            var children=box.children;
            if(!children||children.length<3||children.length>35) continue;
            var listRows=[];
            for(var ch=0;ch<children.length;ch++){
              var item=children[ch];
              if(item.querySelector('table')) continue;
              var parts=[];
              var sub=item.children;
              if(sub&&sub.length>=2&&sub.length<=5){
                for(var s=0;s<sub.length;s++){
                  var sv=clean(sub[s].innerText);
                  if(sv) parts.push(sv);
                }
              } else {
                var rawTxt=clean(item.innerText);
                var pm=rawTxt.match(/^([^\d]{3,42}?)\s+(\d{2,3}(?:[.,]\d{3})+)(?:\s+([+\-]?\d[\d.,]*))?/);
                if(pm){
                  parts.push(clean(pm[1]));
                  parts.push(pm[2]);
                  if(pm[3]) parts.push(pm[3]);
                }
              }
              if(parts.length>=2&&parts[0].length>=2&&parts[0].length<=48&&!/^\d/.test(parts[0])&&/\d{2,3}[.,]\d{3}/.test(parts.slice(1).join(' '))){
                listRows.push(parts);
              }
            }
            if(listRows.length>=2){
              out.push({title:findHeading(box),headers:[],rows:listRows});
            }
          }
          var text=(document.body&&document.body.innerText)?document.body.innerText:'';
          var m=text.match(/\b(\d{1,2}[\/\-]\d{1,2}[\/\-]20\d{2})\b/);
          return JSON.stringify({date:m?m[1]:'',sections:out});
        })()
    """.trimIndent()

    fun todayDateFormatted(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        return sdf.format(Calendar.getInstance().time)
    }

    fun fetch(kind: PriceKind, primaryUrl: String): PriceFetchResult {
        val urls = buildCandidateUrls(kind, primaryUrl)
        val mergedSections = ArrayList<PriceSection>()
        var detectedDate = ""
        var sourceHost = hostOf(primaryUrl)

        for (u in urls) {
            try {
                val html = httpGet(u)
                if (html.isBlank()) continue
                if (detectedDate.isEmpty()) {
                    detectedDate = extractDateFromHtml(html)
                }
                val parsed = ArrayList(parseHtmlTables(html))
                if (kind == PriceKind.COFFEE && parsed.none { isDomesticCoffeeSection(it) }) {
                    parsed.addAll(parseNonTableCoffeeRows(html))
                }
                if (parsed.isNotEmpty()) {
                    if (mergedSections.isEmpty()) sourceHost = hostOf(u)
                    for (sec in parsed) {
                        if (mergedSections.none { it.rows == sec.rows }) {
                            mergedSections.add(sec)
                        }
                    }
                    val ready = when (kind) {
                        PriceKind.GOLD -> mergedSections.any { s ->
                            s.title.lowercase(Locale.ROOT).contains("nhẫn") ||
                                s.rows.any { r -> r.any { it.lowercase(Locale.ROOT).contains("nhẫn") } }
                        }
                        PriceKind.COFFEE -> {
                            val domesticCount = mergedSections
                                .filter { isDomesticCoffeeSection(it) && !isWorldFuturesSection(it) }
                                .sumOf { it.rows.size }
                            domesticCount >= 6
                        }
                        else -> true
                    }
                    if (ready) break
                }
            } catch (_: Exception) {
            }
        }

        val normalizedSections = postProcessSections(kind, mergedSections)
        return PriceFetchResult(
            sections = normalizedSections,
            updatedDate = detectedDate.ifEmpty { todayDateFormatted() },
            sourceLabel = sourceHost
        )
    }

    fun parseJsonPayload(kind: PriceKind, jsonValue: String): PriceFetchResult? {
        return try {
            val raw = JSONTokener(jsonValue).nextValue() as? String ?: return null
            val root = JSONObject(raw)
            val date = root.optString("date", "").ifEmpty { todayDateFormatted() }
            val arr = root.optJSONArray("sections") ?: return null
            val out = ArrayList<PriceSection>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val title = obj.optString("title", "")
                val hArr = obj.optJSONArray("headers")
                val headers = ArrayList<String>()
                if (hArr != null) {
                    for (j in 0 until hArr.length()) headers.add(hArr.optString(j, ""))
                }
                val rArr = obj.optJSONArray("rows") ?: continue
                val rows = ArrayList<List<String>>()
                for (r in 0 until rArr.length()) {
                    val rowArr = rArr.optJSONArray(r) ?: continue
                    val cells = ArrayList<String>()
                    for (c in 0 until rowArr.length()) cells.add(rowArr.optString(c, ""))
                    if (cells.isNotEmpty()) rows.add(cells)
                }
                if (rows.isNotEmpty()) out.add(PriceSection(title, headers, rows))
            }
            val processed = postProcessSections(kind, out)
            if (processed.isEmpty()) null else PriceFetchResult(processed, date, "")
        } catch (_: Exception) {
            null
        }
    }

    private fun buildCandidateUrls(kind: PriceKind, primaryUrl: String): List<String> {
        val list = ArrayList<String>()
        if (primaryUrl.isNotBlank()) list.add(primaryUrl.trim())
        when (kind) {
            PriceKind.COFFEE -> {
                addIfAbsent(list, "https://giacaphe.com/")
                addIfAbsent(list, "https://giacaphe.com/gia-ca-phe-noi-dia/")
                addIfAbsent(list, "https://giacaphe.com/gia-ca-phe-hom-nay/")
            }
            PriceKind.PEPPER -> {
                addIfAbsent(list, "https://giacaphe.com/gia-tieu-hom-nay/")
                addIfAbsent(list, "https://giacaphe.com/gia-tieu/")
            }
            PriceKind.GOLD -> {
                addIfAbsent(list, "https://webgia.com/gia-vang/sjc/")
                addIfAbsent(list, "https://webgia.com/gia-vang/")
            }
            PriceKind.FUEL -> {
                addIfAbsent(list, "https://webgia.com/gia-xang-dau/petrolimex/")
            }
        }
        return list
    }

    private fun addIfAbsent(list: MutableList<String>, url: String) {
        if (list.none { it.equals(url, ignoreCase = true) }) list.add(url)
    }

    private fun hostOf(url: String): String = try {
        URL(url).host.removePrefix("www.")
    } catch (_: Exception) {
        "web"
    }

    private fun extractDateFromHtml(html: String): String {
        val text = stripTags(html)
        val m = Regex("\\b(\\d{1,2}[/-]\\d{1,2}[/-]20\\d{2})\\b").find(text)
        return m?.groupValues?.get(1)?.replace('-', '/') ?: ""
    }

    private fun parseHtmlTables(html: String): List<PriceSection> {
        val sections = ArrayList<PriceSection>()
        val tableMatcher = Regex("<table[^>]*>(.*?)</table>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val trMatcher = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val cellMatcher = Regex("<(th|td)[^>]*>(.*?)</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val headingMatcher = Regex("<(h[1-4]|caption)[^>]*>(.*?)</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        for (tm in tableMatcher.findAll(html)) {
            val before = html.substring(maxOf(0, tm.range.first - 600), tm.range.first)
            val heading = headingMatcher.findAll(before).lastOrNull()?.groupValues?.get(2)?.let { stripTags(it) }.orEmpty()

            val headers = ArrayList<String>()
            val rows = ArrayList<List<String>>()
            var numericRowCount = 0
            var currentGroup = ""

            for (rm in trMatcher.findAll(tm.groupValues[1])) {
                val trHtml = rm.groupValues[1]
                val cells = ArrayList<String>()
                var allTh = true
                for (cm in cellMatcher.findAll(trHtml)) {
                    if (!cm.groupValues[1].equals("th", ignoreCase = true)) allTh = false
                    cells.add(stripTags(cm.groupValues[2]))
                }
                if (cells.size == 1 && cells[0].length in 2..50 && !hasDigits(cells[0])) {
                    val g = cells[0].trim()
                    val ng = VoiceCommand.norm(g)
                    if (isDomesticCoffeeName(ng)) currentGroup = g
                    continue
                }
                if (cells.size < 2) continue
                if (allTh && headers.isEmpty()) {
                    headers.addAll(cells)
                } else if (headers.isEmpty() && rows.isEmpty() && (1 until cells.size).none { hasDigits(cells[it]) }) {
                    headers.addAll(cells)
                } else {
                    val rowCells = cells.toMutableList()
                    if (currentGroup.isNotEmpty() && rowCells[0].isNotBlank()) {
                        val areaNorm = VoiceCommand.norm(rowCells[0])
                        val grpNorm = VoiceCommand.norm(currentGroup)
                        if (!areaNorm.contains(grpNorm) && !isProvinceLevelName(areaNorm)) {
                            rowCells[0] = "${rowCells[0]} ($currentGroup)"
                        }
                    }
                    if ((1 until rowCells.size).any { hasDigits(rowCells[it]) }) numericRowCount++
                    rows.add(rowCells)
                }
            }
            if (numericRowCount >= 1 && rows.isNotEmpty()) {
                val caption = headingMatcher.find(tm.groupValues[1])?.groupValues?.get(2)?.let { stripTags(it) }.orEmpty()
                sections.add(PriceSection(title = caption.ifEmpty { heading }, headers = headers, rows = rows))
            }
        }
        return sections
    }

    /** Fallback extractor when a page renders domestic coffee prices in <div> or <li> rows instead of <table>. */
    private fun parseNonTableCoffeeRows(html: String): List<PriceSection> {
        val text = html
            .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "\n")
            .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "\n")
            .replace(Regex("</?(?:tr|li|p|div|section|br|h[1-6])[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
        val rowRegex = Regex(
            "^\\s*([\\p{L}][\\p{L}0-9 .,'’\\-/]{2,42}?)\\s+(\\d{2,3}[.,]\\d{3}(?:\\s*(?:đ|₫|vnđ|vnd)(?:/kg)?)?)(?:\\s+([+\\-]?\\d[\\d.,]*))?\\s*$",
            RegexOption.IGNORE_CASE
        )
        val rows = ArrayList<List<String>>()
        val seenAreas = HashSet<String>()
        for (line in text.lines()) {
            val cleaned = line.replace(Regex("\\s+"), " ").trim()
            if (cleaned.length !in 8..80) continue
            val m = rowRegex.matchEntire(cleaned) ?: continue
            val area = m.groupValues[1].trim()
            val normArea = VoiceCommand.norm(area)
            if (!isDomesticCoffeeName(normArea)) continue
            val price = m.groupValues[2].trim()
            val delta = m.groupValues[3].trim()
            if (seenAreas.add(normArea)) {
                rows.add(if (delta.isNotEmpty()) listOf(area, price, delta) else listOf(area, price))
            }
        }
        return if (rows.size >= 2) {
            listOf(PriceSection(title = "Bảng giá cà phê nội địa", headers = listOf("Khu vực", "Giá (VNĐ/kg)"), rows = rows))
        } else {
            emptyList()
        }
    }

    private fun isProvinceLevelName(normArea: String): Boolean =
        normArea.contains("dak lak") || normArea.contains("lam dong") ||
            normArea.contains("gia lai") || normArea.contains("dak nong") ||
            normArea.contains("kon tum") || normArea.contains("dong nai") ||
            normArea.contains("ho chi minh") || normArea.contains("tp hcm")

    private fun isDomesticCoffeeName(normText: String): Boolean {
        val compact = normText.replace(" ", "")
        return normText.contains("dak lak") || normText.contains("lam dong") ||
            normText.contains("gia lai") || normText.contains("dak nong") ||
            normText.contains("kon tum") || normText.contains("tay nguyen") ||
            normText.contains("noi dia") || normText.contains("trong nuoc") ||
            normText.contains("buon ma thuot") || normText.contains("buon ho") ||
            compact.contains("cumgar") || compact.contains("eahleo") ||
            normText.contains("krong") || compact.contains("cukuin") ||
            normText.contains("bao loc") || normText.contains("di linh") ||
            normText.contains("lam ha") || normText.contains("da lat") ||
            normText.contains("duc trong") || normText.contains("don duong") ||
            normText.contains("pleiku") || normText.contains("chu se") ||
            normText.contains("chu prong") || normText.contains("ia grai") ||
            normText.contains("gia nghia") || normText.contains("dak mil") ||
            normText.contains("dak song") || compact.contains("dakrlap") ||
            normText.contains("tuy duc") || normText.contains("dak ha") ||
            normText.contains("dong nai") || normText.contains("binh phuoc") ||
            normText.contains("trung binh") || normText.contains("ho chi minh") ||
            normText.contains("tp hcm")
    }

    private fun isDomesticCoffeeSection(s: PriceSection): Boolean {
        val text = VoiceCommand.norm(s.title) + " " +
            s.rows.joinToString(" ") { VoiceCommand.norm(it.firstOrNull()) }
        return isDomesticCoffeeName(text)
    }

    private fun isWorldFuturesSection(s: PriceSection): Boolean {
        if (isDomesticCoffeeSection(s)) return false
        val text = VoiceCommand.norm(s.title) + " " +
            s.rows.joinToString(" ") { VoiceCommand.norm(it.firstOrNull()) }
        return text.contains("london") || text.contains("new york") ||
            text.contains("robusta") || text.contains("arabica") ||
            text.contains("the gioi") || text.contains("truc tuyen") ||
            text.contains("ky han")
    }

    private fun postProcessSections(kind: PriceKind, raw: List<PriceSection>): List<PriceSection> {
        if (raw.isEmpty()) return emptyList()
        if (kind == PriceKind.COFFEE) {
            val domesticRaw = raw
                .filter { isDomesticCoffeeSection(it) && !isWorldFuturesSection(it) }
                .sortedByDescending { it.rows.size }
                .distinctBy { it.rows }

            // Drop smaller strict subsets when a fuller table already contains the exact same rows
            val filtered = ArrayList<PriceSection>()
            for (sec in domesticRaw) {
                val isSubset = filtered.any { larger ->
                    larger.rows.size > sec.rows.size && sec.rows.all { r -> larger.rows.contains(r) }
                }
                if (!isSubset) filtered.add(sec)
            }

            val domestic = filtered.map { sec ->
                val cleanTitle = sec.title.trim().ifEmpty { "Bảng giá cà phê nội địa" }
                sec.copy(title = cleanTitle)
            }
            if (domestic.isNotEmpty()) return domestic.take(8)

            val nonWorld = raw.filterNot { isWorldFuturesSection(it) }
                .sortedByDescending { it.rows.size }
                .distinctBy { it.rows }
            if (nonWorld.isNotEmpty()) return nonWorld.take(8)
        }
        if (kind == PriceKind.GOLD) {
            // Prioritize & label Vàng miếng vs Vàng nhẫn clearly
            val sorted = raw.sortedByDescending { s ->
                val t = VoiceCommand.norm(s.title) + " " + s.rows.joinToString(" ") { VoiceCommand.norm(it.firstOrNull()) }
                when {
                    t.contains("nhan") -> 3
                    t.contains("sjc") -> 2
                    else -> 1
                }
            }
            return sorted.distinctBy { it.rows }.take(8)
        }
        return raw.distinctBy { it.rows }.take(8)
    }

    /** Matches rows against the user's spoken province/commune or resolved parent province. */
    fun findMatchedRows(
        sections: List<PriceSection>,
        spokenLocation: String?,
        resolvedProvince: String?
    ): List<PriceMatchRow> {
        val targets = buildLocationCandidates(spokenLocation, resolvedProvince)
        if (targets.isEmpty()) return emptyList()

        val out = ArrayList<PriceMatchRow>()
        val seen = HashSet<String>()
        for (sec in sections) {
            for (row in sec.rows) {
                if (row.size < 2) continue
                val areaRaw = row[0]
                if ((1 until row.size).none { hasDigits(row[it]) }) continue
                val areaNorm = stripAdminPrefix(VoiceCommand.norm(areaRaw))
                if (areaNorm.isEmpty()) continue
                val areaCompact = areaNorm.replace(" ", "")
                val matched = targets.any { t ->
                    val tCompact = t.replace(" ", "")
                    areaNorm == t || areaNorm.contains(t) || (areaNorm.length >= 4 && t.contains(areaNorm)) ||
                        (areaCompact.length >= 4 && tCompact.length >= 4 &&
                            (areaCompact == tCompact || areaCompact.contains(tCompact) || tCompact.contains(areaCompact)))
                }
                if (matched && seen.add(areaNorm + "|" + row.drop(1).joinToString("|"))) {
                    out.add(PriceMatchRow(sec.title, areaRaw, row.drop(1)))
                }
            }
        }
        return out
    }

    private fun buildLocationCandidates(spokenLocation: String?, resolvedProvince: String?): List<String> {
        val candidates = LinkedHashSet<String>()
        fun addNormalized(raw: String?) {
            if (raw.isNullOrBlank()) return
            val n = stripAdminPrefix(VoiceCommand.norm(raw))
            if (n.length >= 3) candidates.add(n)
            // Expand merged/historical province equivalents and key coffee districts used by giacaphe.com
            when (n) {
                "lam dong" -> {
                    candidates.add("dak nong")
                    candidates.add("bao loc")
                    candidates.add("di linh")
                    candidates.add("lam ha")
                    candidates.add("da lat")
                    candidates.add("duc trong")
                    candidates.add("don duong")
                }
                "dak nong" -> {
                    candidates.add("lam dong")
                    candidates.add("gia nghia")
                    candidates.add("dak mil")
                    candidates.add("dak song")
                    candidates.add("dak r lap")
                    candidates.add("tuy duc")
                    candidates.add("cu jut")
                }
                "gia lai" -> {
                    candidates.add("pleiku")
                    candidates.add("chu se")
                    candidates.add("chu prong")
                    candidates.add("ia grai")
                    candidates.add("duc co")
                    candidates.add("mang yang")
                }
                "dak lak" -> {
                    candidates.add("buon ma thuot")
                    candidates.add("ea h leo")
                    candidates.add("cu m gar")
                    candidates.add("buon ho")
                    candidates.add("krong nang")
                    candidates.add("krong pak")
                    candidates.add("cu kuin")
                }
                "dong nai" -> candidates.add("binh phuoc")
                "binh phuoc" -> candidates.add("dong nai")
                "quang ngai" -> {
                    candidates.add("kon tum")
                    candidates.add("dak ha")
                    candidates.add("dak to")
                }
                "kon tum" -> {
                    candidates.add("quang ngai")
                    candidates.add("dak ha")
                    candidates.add("dak to")
                }
                "ho chi minh" -> candidates.add("ba ria vung tau")
                "ba ria vung tau" -> candidates.add("ho chi minh")
            }
        }
        addNormalized(spokenLocation)
        addNormalized(resolvedProvince)
        // Also add individual significant tokens if user spoke "xa hoa thang dak lak"
        if (!spokenLocation.isNullOrBlank()) {
            val clean = stripAdminPrefix(VoiceCommand.norm(spokenLocation))
            AdminData.findProvinceFuzzy(clean)?.let { addNormalized(it) }
        }
        return candidates.toList()
    }

    private fun stripAdminPrefix(s: String): String = s
        .replace(Regex("\\b(o|tai|khu vuc|tinh|thanh pho|tp|huyen|quan|thi xa|xa|phuong|thi tran)\\b"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun summarizeForSpeech(
        kind: PriceKind,
        zone: Int,
        sections: List<PriceSection>,
        matchedRows: List<PriceMatchRow>,
        spokenLocation: String?,
        resolvedProvince: String?,
        updatedDate: String,
        isEnglish: Boolean
    ): String {
        val datePhrase = if (updatedDate.isNotEmpty()) {
            if (isEnglish) " on $updatedDate" else " ngày $updatedDate"
        } else ""
        val kindTitle = when (kind) {
            PriceKind.GOLD -> if (isEnglish) "Gold and gold ring prices" else "Giá vàng và vàng nhẫn"
            PriceKind.COFFEE -> if (isEnglish) "Domestic coffee prices from giacaphe.com" else "Bảng giá cà phê nội địa"
            PriceKind.PEPPER -> if (isEnglish) "Pepper prices from giacaphe.com" else "Giá hồ tiêu từ giacaphe.com"
            PriceKind.FUEL -> if (isEnglish) "Petrolimex fuel prices" else "Giá xăng dầu Petrolimex"
        }

        if (sections.isEmpty() && matchedRows.isEmpty()) {
            return if (isEnglish) "The price table could not be read yet."
            else "Chưa đọc được bảng giá, hãy thử lại sau."
        }

        val sb = StringBuilder(kindTitle).append(datePhrase)

        // Coffee/Pepper with spoken province/ward match
        if ((kind == PriceKind.COFFEE || kind == PriceKind.PEPPER) && matchedRows.isNotEmpty()) {
            val locDisplay = spokenLocation?.takeIf { it.isNotBlank() } ?: resolvedProvince
            if (!locDisplay.isNullOrBlank()) {
                sb.append(if (isEnglish) " for $locDisplay" else " tại $locDisplay")
            }
            sb.append(". ")
            for (m in matchedRows.take(3)) {
                val p = m.values.firstOrNull { hasDigits(it) } ?: m.values.firstOrNull().orEmpty()
                sb.append(m.area).append(": ").append(p)
                if (m.values.size >= 2 && !hasDigits(m.values[1]) && m.values[1].isNotBlank()) {
                    sb.append(" (").append(m.values[1]).append(")")
                }
                sb.append(". ")
            }
            return sb.toString()
        }

        if ((kind == PriceKind.COFFEE || kind == PriceKind.PEPPER) && !spokenLocation.isNullOrBlank()) {
            sb.append(if (isEnglish) ". Reference table (no exact match for $spokenLocation): " else ". Chưa có dòng riêng cho $spokenLocation, xin đọc bảng chung: ")
        } else {
            var z = zone
            if (kind == PriceKind.FUEL) {
                if (z > 2) {
                    sb.append(if (isEnglish) ". Zone 2: " else ". Petrolimex chỉ có vùng 1 và vùng 2, đọc vùng 2")
                    z = 2
                }
                if (z > 0) sb.append(if (isEnglish) " zone $z" else " vùng $z")
            }
            sb.append(". ")
        }

        val isGold = kind == PriceKind.GOLD
        val isFuel = kind == PriceKind.FUEL
        var count = 0
        val max = if (isFuel || isGold) 6 else 4

        if (isGold) {
            // Try to pick at least 2 bar rows and 2 ring gold rows
            val barRows = ArrayList<List<String>>()
            val ringRows = ArrayList<List<String>>()
            for (s in sections) {
                val sIsRing = VoiceCommand.norm(s.title).contains("nhan")
                for (r in s.rows) {
                    if (r.size < 2 || r[0].isBlank() || (1 until r.size).none { hasDigits(r[it]) }) continue
                    val rIsRing = sIsRing || VoiceCommand.norm(r[0]).contains("nhan")
                    if (rIsRing) ringRows.add(r) else barRows.add(r)
                }
            }
            val picked = barRows.take(2) + ringRows.take(3)
            for (r in picked) {
                sb.append(r[0])
                if (r.size >= 3) {
                    sb.append(if (isEnglish) ", buy ${r[1]}, sell ${r[2]}" else ", mua vào ${r[1]}, bán ra ${r[2]}")
                } else {
                    sb.append(": ").append(r[1])
                }
                sb.append(". ")
                count++
            }
        } else {
            for (s in sections) {
                for (r in s.rows) {
                    if (count >= max) break
                    if (r.size < 2 || r[0].isBlank() || (1 until r.size).none { hasDigits(r[it]) }) continue
                    sb.append(r[0])
                    when {
                        isFuel && zone in 1 until r.size -> sb.append(": ").append(r[zone])
                        isFuel && r.size >= 3 -> sb.append(
                            if (isEnglish) ": zone 1 ${r[1]}, zone 2 ${r[2]}"
                            else ": vùng 1 ${r[1]}, vùng 2 ${r[2]}"
                        )
                        else -> sb.append(": ").append(r[1])
                    }
                    sb.append(". ")
                    count++
                }
                if (count >= max) break
            }
        }

        return if (count == 0) {
            if (isEnglish) "No figures could be read from this page." else "Chưa đọc được số liệu từ trang này."
        } else {
            sb.toString()
        }
    }

    private fun hasDigits(s: String): Boolean = s.count { it.isDigit() } >= 2

    private fun stripTags(html: String): String {
        return html
            .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace(Regex("&#(\\d+);")) { m ->
                m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: " "
            }
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun httpGet(url: String): String {
        var current = url
        var redirects = 0
        while (redirects < 4) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 10000
                readTimeout = 12000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                )
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8")
                setRequestProperty("Accept-Encoding", "gzip")
                setRequestProperty("Referer", "https://www.google.com/")
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: break
                    current = URL(URL(current), loc).toString()
                    redirects++
                    continue
                }
                if (code !in 200..299) return ""
                val rawStream = if (conn.contentEncoding?.contains("gzip", ignoreCase = true) == true) {
                    GZIPInputStream(conn.inputStream)
                } else {
                    conn.inputStream
                }
                return rawStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
        return ""
    }
}
