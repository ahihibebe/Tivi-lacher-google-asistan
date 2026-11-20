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
          function pseudoNum(el,p){
            try{
              var c=getComputedStyle(el,p).content;
              if(c&&c!=='none'&&c!=='normal'){
                c=c.replace(/^["']|["']$/g,'').trim();
                if(/\d/.test(c)&&c.length<=32) return c;
              }
            }catch(e){}
            return '';
          }
          function txt(el){
            if(!el) return '';
            var direct=clean(el.innerText||el.textContent||'');
            if(/\d/.test(direct)) return direct;
            var acc=[];
            var eb=pseudoNum(el,'::before'); if(eb) acc.push(eb);
            var nodes=el.querySelectorAll('*');
            for(var k=0;k<nodes.length;k++){
              var b=pseudoNum(nodes[k],'::before'); if(b) acc.push(b);
              var a=pseudoNum(nodes[k],'::after'); if(a) acc.push(a);
            }
            var ea=pseudoNum(el,'::after'); if(ea) acc.push(ea);
            if(acc.length>0) return clean(acc.join(' ')+(direct?' '+direct:''));
            return direct;
          }
          function isProvinceLike(s){
            var t=clean(s).toLowerCase();
            return /đắk lắk|dak lak|lâm đồng|lam dong|gia lai|đắk nông|dak nong|kon tum|đồng nai|dong nai|bình phước|binh phuoc|hồ chí minh|tp\.?\s*hcm/.test(t);
          }
          function isDomesticTerm(s){
            var t=clean(s).toLowerCase();
            return /đắk lắk|dak lak|lâm đồng|lam dong|gia lai|đắk nông|dak nong|kon tum|đồng nai|dong nai|bình phước|binh phuoc|hồ chí minh|tp\.?\s*hcm|buôn ma thuột|buon ma thuot|buôn hồ|cư m'?gar|ea h'?leo|krông|cư kuin|bảo lộc|di linh|lâm hà|đà lạt|đức trọng|đơn dương|pleiku|chư sê|chư prông|ia grai|gia nghĩa|đắk mil|đắk song|đắk r'?lấp|tuy đức|cư jút|đắk hà|trung bình/.test(t);
          }
          function findHeading(el){
            var cur=el;
            for(var step=0;step<4&&cur;step++){
              var prev=cur.previousElementSibling;
              while(prev){
                if(/^(H[1-4]|CAPTION|STRONG|P|DIV)$/i.test(prev.tagName)){
                  var t=txt(prev);
                  if(t.length>=4&&t.length<=90) return t;
                }
                prev=prev.previousElementSibling;
              }
              cur=cur.parentElement;
            }
            return '';
          }
          var docs=[document];
          var iframes=document.querySelectorAll('iframe');
          for(var f=0;f<iframes.length;f++){
            try{if(iframes[f].contentDocument) docs.push(iframes[f].contentDocument);}catch(e){}
          }
          var out=[];
          for(var d=0;d<docs.length;d++){
            var doc=docs[d];
            var tables=doc.querySelectorAll('table');
            for(var i=0;i<tables.length&&out.length<48;i++){
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
                  for(var c=0;c<ths.length;c++) headers.push(txt(ths[c]));
                } else {
                  var cells=tr.querySelectorAll('th,td');
                  var row=[];
                  var hasNum=false;
                  for(var c=0;c<cells.length;c++){
                    row.push(txt(cells[c]));
                  }
                  if(row.length>=3&&(row[0]===''||/^\d{1,2}\.?$/.test(row[0]))&&!/^\d+$/.test(row[1])){
                    row.shift();
                  }
                  for(var c=1;c<row.length;c++){
                    if(/\d{2,}/.test(row[c])) hasNum=true;
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
                var title=cap?txt(cap):findHeading(t);
                out.push({title:title,headers:headers,rows:body});
              }
            }
            // Fallback: scan div/ul price lists if tables missed card/flex layouts
            var listContainers=doc.querySelectorAll('ul,ol,div,section');
            for(var lc=0;lc<listContainers.length&&out.length<80;lc++){
              var box=listContainers[lc];
              if(box.closest&&box.closest('table')) continue;
              var children=box.children;
              if(!children||children.length<2||children.length>40) continue;
              var listRows=[];
              for(var ch=0;ch<children.length;ch++){
                var item=children[ch];
                if(item.querySelector('table')) continue;
                var parts=[];
                var sub=item.children;
                if(sub&&sub.length>=2&&sub.length<=6){
                  for(var s=0;s<sub.length;s++){
                    var sv=txt(sub[s]);
                    if(sv) parts.push(sv);
                  }
                  if(parts.length>=3&&(parts[0]===''||/^\d{1,2}\.?$/.test(parts[0]))) parts.shift();
                } else {
                  var rawTxt=txt(item);
                  var pm=rawTxt.match(/^([^\d]{2,48}?)\s+(\d{2,3}(?:[.,]\d{1,3})+)(?:\s*(?:đ|₫|vnđ|vnd)(?:\/kg)?)?(?:\s+([+\-]?\d[\d.,]*))?/i);
                  if(pm){
                    parts.push(clean(pm[1]).replace(/[:\-]+$/,'').trim());
                    parts.push(pm[2]);
                    if(pm[3]) parts.push(pm[3]);
                  }
                }
                var tailStr=parts.slice(1).join(' ');
                if(parts.length>=2&&parts[0].length>=2&&parts[0].length<=48&&!/^\d/.test(parts[0])&&(/\d{2,3}[.,]\d{1,3}/.test(tailStr)||/\b\d{5,6}\b/.test(tailStr))){
                  listRows.push(parts);
                }
              }
              if(listRows.length>=2){
                out.push({title:findHeading(box),headers:[],rows:listRows});
              }
            }
            // Rendered text fallback specifically for Vietnamese domestic coffee regions/districts (strict clean area labels only)
            function isCleanDomLabel(s){
              var t=clean(s);
              if(t.length<3||t.length>34) return false;
              var openP=(t.match(/\(/g)||[]).length, closeP=(t.match(/\)/g)||[]).length;
              if(openP!==closeP) return false;
              if(/được|ghi nhận|ở mức|dao động|giao dịch|hôm nay|hôm qua|so với|khoảng|quanh|triệu|tấn|kỳ hạn|như sau|khảo sát|trước đó|thị trường/i.test(t)) return false;
              if(t.split(/\s+/).length>5) return false;
              return isDomesticTerm(t);
            }
            if(doc.body){
              var rawText=(doc.body.innerText||doc.body.textContent||'');
              var lines=rawText.split(/\r?\n+/).map(clean).filter(function(x){return x.length>=2;});
              var domRows=[];
              var seenArea={};
              var inlineRe=/^([^\d]{2,36}?)\s+(\d{2,3}[.,]\d{3})(?:\s*(?:đ|₫|vnđ|vnd)(?:\/kg)?)?(?:\s+([+\-]?\d[\d.,]*))?$/i;
              var priceLineRe=/^(\d{2,3}[.,]\d{3})(?:\s*(?:đ|₫|vnđ|vnd)(?:\/kg)?)?(?:\s+([+\-]?\d[\d.,]*))?$/i;
              for(var li=0;li<lines.length;li++){
                var ln=lines[li];
                var m1=ln.match(inlineRe);
                if(m1){
                  var area=clean(m1[1]).replace(/[:\-]+$/,'').trim();
                  if(isCleanDomLabel(area)&&!seenArea[area.toLowerCase()]){
                    seenArea[area.toLowerCase()]=true;
                    var r=[area,m1[2]];
                    if(m1[3]) r.push(m1[3]);
                    domRows.push(r);
                    continue;
                  }
                }
                if(ln.length<=34&&!/\d{2,}/.test(ln)&&isCleanDomLabel(ln)&&!seenArea[ln.toLowerCase()]){
                  for(var ahead=1;ahead<=2&&li+ahead<lines.length;ahead++){
                    var m2=lines[li+ahead].match(priceLineRe);
                    if(m2){
                      seenArea[ln.toLowerCase()]=true;
                      var r2=[ln,m2[1]];
                      if(m2[2]) r2.push(m2[2]);
                      domRows.push(r2);
                      li+=ahead;
                      break;
                    }
                  }
                }
              }
              if(domRows.length>=3){
                out.push({title:'Bảng giá cà phê nội địa',headers:['Khu vực','Giá (VNĐ/kg)'],rows:domRows});
              }
            }
          }
          var text=txt(document.body);
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
                                .sumOf { sec -> sec.rows.count { isValidDomesticCoffeeRow(it) } }
                            domesticCount >= 4
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
                addIfAbsent(list, "https://giacaphe.com/gia-ca-phe-noi-dia/")
                addIfAbsent(list, "https://giacaphe.com/gia-ca-phe-hom-nay/")
                addIfAbsent(list, "https://giacaphe.com/")
                addIfAbsent(list, "https://webgia.com/gia-ca-phe/")
                addIfAbsent(list, "https://webgia.com/gia-ca-phe/trong-nuoc/")
                addIfAbsent(list, "https://chocaphe.vn/gia-ca-phe-hom-nay/")
            }
            PriceKind.PEPPER -> {
                addIfAbsent(list, "https://giacaphe.com/gia-tieu-hom-nay/")
                addIfAbsent(list, "https://giacaphe.com/gia-tieu/")
                addIfAbsent(list, "https://webgia.com/gia-tieu/")
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
        val cellMatcher = Regex("<(th|td)[^>]*>(.*?)(?:</\\1>|(?=<th|<td|</tr|$))", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
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
                val normalizedCells = if (cells.size >= 3 && (cells[0].isBlank() || cells[0].trim('.').all { it.isDigit() } && cells[0].length <= 3)) {
                    cells.drop(1)
                } else {
                    cells
                }
                if (allTh && headers.isEmpty()) {
                    headers.addAll(normalizedCells)
                } else if (headers.isEmpty() && rows.isEmpty() && (1 until normalizedCells.size).none { hasDigits(normalizedCells[it]) }) {
                    headers.addAll(normalizedCells)
                } else {
                    val rowCells = normalizedCells.toMutableList()
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
            "^\\s*([\\p{L}][\\p{L}0-9 .,'’\\-/()]{2,36}?)\\s+(\\d{2,3}[.,]\\d{3}(?:\\s*(?:đ|₫|vnđ|vnd)(?:/kg)?)?)(?:\\s+([+\\-]?\\d[\\d.,]*))?\\s*$",
            RegexOption.IGNORE_CASE
        )
        val priceOnlyRegex = Regex("^(\\d{2,3}[.,]\\d{3}(?:\\s*(?:đ|₫|vnđ|vnd)(?:/kg)?)?)(?:\\s+([+\\-]?\\d[\\d.,]*))?\\s*$", RegexOption.IGNORE_CASE)
        val lines = text.lines().map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }
        val rows = ArrayList<List<String>>()
        val seenAreas = HashSet<String>()
        var i = 0
        while (i < lines.size) {
            val cleaned = lines[i]
            val m = if (cleaned.length in 8..72) rowRegex.matchEntire(cleaned) else null
            if (m != null) {
                val area = m.groupValues[1].trim().trimEnd(':', '-')
                val normArea = VoiceCommand.norm(area)
                if (isCleanCoffeeAreaLabel(area, normArea) && seenAreas.add(normArea)) {
                    val price = m.groupValues[2].trim()
                    val delta = m.groupValues[3].trim()
                    rows.add(if (delta.isNotEmpty()) listOf(area, price, delta) else listOf(area, price))
                }
                i++
                continue
            }
            if (cleaned.length in 3..34 && !hasDigits(cleaned)) {
                val normArea = VoiceCommand.norm(cleaned)
                if (isCleanCoffeeAreaLabel(cleaned, normArea) && !seenAreas.contains(normArea)) {
                    var matchedStep = 0
                    for (ahead in 1..2) {
                        if (i + ahead >= lines.size) break
                        val pm = priceOnlyRegex.matchEntire(lines[i + ahead])
                        if (pm != null && seenAreas.add(normArea)) {
                            val price = pm.groupValues[1].trim()
                            val delta = pm.groupValues[2].trim()
                            rows.add(if (delta.isNotEmpty()) listOf(cleaned, price, delta) else listOf(cleaned, price))
                            matchedStep = ahead + 1
                            break
                        }
                    }
                    if (matchedStep > 0) {
                        i += matchedStep
                        continue
                    }
                }
            }
            i++
        }
        val nonAvgCount = rows.count { !VoiceCommand.norm(it[0]).contains("trung binh") }
        return if (rows.size >= 3 && nonAvgCount >= 2) {
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
            normText.contains("cu jut") || normText.contains("dak to") ||
            normText.contains("duc co") || normText.contains("mang yang") ||
            normText.contains("chu pah") || normText.contains("bu dang") ||
            normText.contains("phuoc long") || normText.contains("dinh quan") ||
            normText.contains("xuan loc") || normText.contains("cam my") ||
            normText.contains("trung binh") || normText.contains("ho chi minh") ||
            normText.contains("tp hcm")
    }

    private fun isCleanCoffeeAreaLabel(rawArea: String, normArea: String = VoiceCommand.norm(rawArea)): Boolean {
        val trimmed = rawArea.trim()
        if (trimmed.length !in 3..38) return false
        if (trimmed.count { it == '(' } != trimmed.count { it == ')' }) return false
        if (trimmed.split(Regex("\\s+")).size > 6) return false
        val proseReject = listOf(
            "duoc", "ghi nhan", "o muc", "dao dong", "giao dich",
            "hom nay", "hom qua", "so voi", "khoang", "quanh",
            "trieu", "ky han", "nhu sau", "khao sat", "truoc do", "thi truong"
        )
        if (proseReject.any { normArea.contains(it) }) return false
        return isDomesticCoffeeName(normArea)
    }

    private fun isValidDomesticCoffeeRow(row: List<String>): Boolean {
        if (row.size < 2) return false
        val hasPrice = (1 until row.size).any { hasDigits(row[it]) }
        if (!hasPrice) return false
        val areaRaw = row.firstOrNull().orEmpty()
        return isCleanCoffeeAreaLabel(areaRaw) && !isNonCoffeeTickerRow(row)
    }

    private fun isNonCoffeeTickerRow(row: List<String>): Boolean {
        val text = VoiceCommand.norm(row.firstOrNull())
        return text.contains("london") || text.contains("new york") ||
            text.contains("robusta") || text.contains("arabica") ||
            text.contains("usd vnd") || text.contains("ty gia") ||
            text.contains("ho tieu") || text.contains("brazil") ||
            text.contains("b3 ")
    }

    fun isDomesticCoffeeSection(s: PriceSection): Boolean {
        val valid = s.rows.filter { isValidDomesticCoffeeRow(it) }
        if (valid.size < 3) {
            val distinctProvinces = valid
                .map { VoiceCommand.norm(it.firstOrNull()) }
                .filter { !it.contains("trung binh") }
                .distinct()
            return valid.size >= 2 && distinctProvinces.size >= 2
        }
        return true
    }

    private fun isWorldFuturesSection(s: PriceSection): Boolean {
        if (isDomesticCoffeeSection(s)) return false
        val text = VoiceCommand.norm(s.title) + " " +
            s.headers.joinToString(" ") { VoiceCommand.norm(it) } + " " +
            s.rows.joinToString(" ") { r -> r.joinToString(" ") { VoiceCommand.norm(it) } }
        return text.contains("london") || text.contains("new york") ||
            text.contains("robusta") || text.contains("arabica") ||
            text.contains("the gioi") || text.contains("truc tuyen") ||
            text.contains("ky han") || text.contains("brazil") ||
            text.contains("b3") || text.contains("ice ")
    }

    private fun postProcessSections(kind: PriceKind, raw: List<PriceSection>): List<PriceSection> {
        if (raw.isEmpty()) return emptyList()
        if (kind == PriceKind.COFFEE) {
            val cleanedDomestic = raw
                .filter { isDomesticCoffeeSection(it) }
                .mapNotNull { sec ->
                    val validRows = sec.rows.filter { r ->
                        r.size >= 2 && (1 until r.size).any { hasDigits(r[it]) } && !isNonCoffeeTickerRow(r)
                    }
                    if (validRows.count { isValidDomesticCoffeeRow(it) } >= 2) {
                        sec.copy(
                            title = sec.title.trim().ifEmpty { "Bảng giá cà phê nội địa" },
                            rows = validRows
                        )
                    } else {
                        null
                    }
                }
                .sortedByDescending { it.rows.size }
                .distinctBy { it.rows }

            // Drop smaller strict subsets when a fuller table already contains the exact same rows
            val filtered = ArrayList<PriceSection>()
            for (sec in cleanedDomestic) {
                val isSubset = filtered.any { larger ->
                    larger.rows.size > sec.rows.size && sec.rows.all { r -> larger.rows.contains(r) }
                }
                if (!isSubset) filtered.add(sec)
            }

            if (filtered.isNotEmpty()) return filtered.take(8)

            // Reject world-futures-only or empty-cell ticker payloads so fallback loads real domestic prices
            return emptyList()
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
                connectTimeout = 4000
                readTimeout = 5000
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
