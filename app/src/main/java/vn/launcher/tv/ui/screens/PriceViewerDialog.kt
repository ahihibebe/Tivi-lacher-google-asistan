package vn.launcher.tv.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import vn.launcher.tv.data.PriceRepository
import vn.launcher.tv.data.VoiceCommand
import vn.launcher.tv.model.ActiveDialog
import vn.launcher.tv.model.LauncherUiState
import vn.launcher.tv.model.PriceKind
import vn.launcher.tv.ui.components.MacWindowDialog
import vn.launcher.tv.ui.theme.LauncherColors
import vn.launcher.tv.viewmodel.LauncherViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun PriceViewerDialog(
    dialog: ActiveDialog.PriceViewer,
    state: LauncherUiState,
    vm: LauncherViewModel
) {
    val en = state.isEnglish
    val title = when (dialog.kind) {
        PriceKind.GOLD -> if (en) "Gold & Gold Ring Prices" else "Bảng giá Vàng & Vàng Nhẫn"
        PriceKind.COFFEE -> if (en) "Domestic Coffee Prices" else "Bảng giá Cà Phê Nội Địa"
        PriceKind.PEPPER -> if (en) "Pepper Prices (giacaphe.com)" else "Bảng giá Hồ Tiêu hôm nay"
        PriceKind.FUEL -> if (en) "Petrolimex Fuel Prices" else "Bảng giá Xăng Dầu"
    }
    val h = (LocalConfiguration.current.screenHeightDp * 0.68f).dp
    var showWebMode by remember(dialog.kind) { mutableStateOf(false) }
    var webLoading by remember(dialog.kind) { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var tries by remember(dialog.kind) { mutableIntStateOf(0) }

    MacWindowDialog(
        title = title,
        theme = state.theme,
        maxWidth = 840.dp,
        scrollable = false,
        onDismiss = vm::closeDialog,
        headerActions = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderChipButton(Icons.AutoMirrored.Filled.VolumeUp, if (en) "Read" else "Đọc") {
                    vm.speakCurrentPrices()
                }
                HeaderChipButton(
                    icon = if (showWebMode) Icons.Filled.TableChart else Icons.Filled.Language,
                    label = if (showWebMode) {
                        if (en) "Table" else "Xem bảng"
                    } else {
                        if (en) "Web" else "Xem web"
                    }
                ) {
                    showWebMode = !showWebMode
                }
                HeaderChipButton(Icons.Filled.Refresh, if (en) "Reload" else "Làm mới") {
                    tries = 0
                    webLoading = true
                    vm.refreshCurrentPrice()
                    webViewRef?.reload()
                }
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dateText = dialog.updatedDate.ifEmpty { PriceRepository.todayDateFormatted() }
            val src = dialog.sourceLabel.ifEmpty {
                if (dialog.kind == PriceKind.COFFEE || dialog.kind == PriceKind.PEPPER) "giacaphe.com" else "webgia.com"
            }
            Text(
                text = if (en) "Updated: $dateText · Source: $src" else "Cập nhật ngày $dateText · Nguồn: $src",
                color = Color(0xB3FFFFFF),
                fontSize = 12.sp
            )
            if (!dialog.spokenLocation.isNullOrBlank()) {
                Text(
                    text = (if (en) "Area: " else "Khu vực: ") + dialog.spokenLocation,
                    color = LauncherColors.accent(state.theme),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(h)
                .clip(RoundedCornerShape(12.dp))
                .background(if (showWebMode) Color.White else Color(0xFF1A1D24))
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        isFocusable = showWebMode
                        isFocusableInTouchMode = showWebMode
                        tag = dialog.url
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                webLoading = false
                                val scheduledTag = view.tag
                                fun extractLater(delayMs: Long) {
                                    view.postDelayed({
                                        if (view.tag != scheduledTag) return@postDelayed
                                        view.evaluateJavascript(PriceRepository.EXTRACT_ALL_TABLES_JS) { value ->
                                            if (view.tag != scheduledTag) return@evaluateJavascript
                                            val ok = vm.onPriceJsonExtracted(value ?: "", dialog.speakOnLoad, url)
                                            if (!ok) {
                                                if (tries < 2) {
                                                    tries++
                                                    extractLater(1500L)
                                                } else {
                                                    vm.onPriceExtractionFailed()
                                                }
                                            }
                                        }
                                    }, delayMs)
                                }
                                extractLater(1000L)
                            }
                        }
                        loadUrl(dialog.url)
                        webViewRef = this
                    }
                },
                update = { view ->
                    view.isFocusable = showWebMode
                    view.isFocusableInTouchMode = showWebMode
                    if (view.tag != dialog.url) {
                        view.tag = dialog.url
                        tries = 0
                        webLoading = true
                        view.loadUrl(dialog.url)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (showWebMode) 1f else 0f)
            )

            if (!showWebMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1A1D24))
                ) {
                    if (dialog.sections.isEmpty()) {
                        if (dialog.loading) {
                            Row(
                                modifier = Modifier.align(Alignment.Center),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = LauncherColors.accent(state.theme), modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = if (en) "Loading today's price table…" else "Đang tải bảng giá cập nhật hôm nay…",
                                    color = Color(0xE6FFFFFF),
                                    fontSize = 15.sp
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (en) "Could not extract table automatically. Tap \"Web\" above or \"Reload\"." else "Chưa trích xuất được bảng tự động. Hãy bấm \"Xem web\" hoặc \"Làm mới\" ở góc trên.",
                                    color = Color(0xE6FFFFFF),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        val tableScroll = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(tableScroll)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!dialog.spokenLocation.isNullOrBlank()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x280A84FF))
                                        .border(1.dp, LauncherColors.accent(state.theme), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Place, contentDescription = null, tint = LauncherColors.accent(state.theme), modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        val areaLabel = buildString {
                                            append(if (en) "Requested location: " else "Địa phương bạn hỏi: ")
                                            append(dialog.spokenLocation)
                                            if (!dialog.resolvedProvince.isNullOrBlank() &&
                                                !dialog.spokenLocation.contains(dialog.resolvedProvince, ignoreCase = true)
                                            ) {
                                                append(" (${dialog.resolvedProvince})")
                                            }
                                        }
                                        Text(areaLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    if (dialog.matchedRows.isNotEmpty()) {
                                        Spacer(Modifier.height(8.dp))
                                        dialog.matchedRows.forEach { m ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 3.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(m.area, color = Color(0xEBFFFFFF), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                                Text(
                                                    text = m.values.joinToString("  |  "),
                                                    color = Color(0xFF34C759),
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = if (en) "Showing full regional table below." else "Chưa có dòng riêng, xem bảng giá toàn vùng bên dưới.",
                                            color = Color(0xCCFFFFFF),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            dialog.sections.forEach { sec ->
                                val isRingSection = dialog.kind == PriceKind.GOLD &&
                                    VoiceCommand.norm(sec.title).contains("nhan")
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x1E000000))
                                        .border(
                                            1.dp,
                                            if (isRingSection) Color(0x88FFD60A) else Color(0x28FFFFFF),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(10.dp)
                                ) {
                                    if (sec.title.isNotBlank()) {
                                        Text(
                                            text = sec.title,
                                            color = if (isRingSection) Color(0xFFFFD60A) else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                    }

                                    if (sec.headers.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0x24FFFFFF), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            sec.headers.take(4).forEachIndexed { idx, hd ->
                                                Text(
                                                    text = hd,
                                                    color = Color(0xCCFFFFFF),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = if (idx == 0) TextAlign.Start else TextAlign.End,
                                                    modifier = Modifier.weight(if (idx == 0) 1.5f else 1f)
                                                )
                                            }
                                        }
                                    }

                                    sec.rows.forEachIndexed { rIdx, row ->
                                        val isHighlight = (dialog.kind == PriceKind.GOLD && VoiceCommand.norm(row.firstOrNull()).contains("nhan")) ||
                                            dialog.matchedRows.any { it.area.equals(row.firstOrNull(), ignoreCase = true) }
                                        var rowFocused by remember(sec.title, rIdx, row) { mutableStateOf(false) }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    when {
                                                        rowFocused -> LauncherColors.accent(state.theme).copy(alpha = 0.32f)
                                                        isHighlight -> Color(0x26FFD60A)
                                                        rIdx % 2 == 1 -> Color(0x0CFFFFFF)
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .then(
                                                    if (rowFocused) {
                                                        Modifier.border(1.5.dp, Color.White, RoundedCornerShape(8.dp))
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                .onFocusChanged {
                                                    rowFocused = it.isFocused
                                                    if (it.isFocused) vm.playMove()
                                                }
                                                .clickable { vm.speakPriceRow(row) }
                                                .padding(horizontal = 8.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            row.take(4).forEachIndexed { cIdx, cell ->
                                                Text(
                                                    text = cell,
                                                    color = when {
                                                        isHighlight && cIdx == 0 -> Color(0xFFFFD60A)
                                                        cIdx == 0 -> Color(0xF0FFFFFF)
                                                        else -> Color(0xFF64D2FF)
                                                    },
                                                    fontSize = 13.sp,
                                                    fontWeight = if (cIdx == 0 || isHighlight || rowFocused) FontWeight.SemiBold else FontWeight.Medium,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = if (cIdx == 0) TextAlign.Start else TextAlign.End,
                                                    modifier = Modifier.weight(if (cIdx == 0) 1.5f else 1f)
                                                )
                                            }
                                        }
                                        if (rIdx < sec.rows.lastIndex) {
                                            HorizontalDivider(color = Color(0x14FFFFFF), thickness = 0.5.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (webLoading) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = Color(0xFF0A84FF), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (en) "Loading web page…" else "Đang tải trang web…",
                        color = Color(0xFF333333),
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
