package vn.launcher.tv.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.launcher.tv.data.WeatherService
import vn.launcher.tv.model.AppItem
import vn.launcher.tv.model.ClimateDataSource
import vn.launcher.tv.model.DockTab
import vn.launcher.tv.model.LauncherUiState
import vn.launcher.tv.ui.components.GlassPill
import vn.launcher.tv.ui.components.LedFocusTile
import vn.launcher.tv.ui.components.barSurfaceBrush
import vn.launcher.tv.ui.theme.LauncherColors
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
private object LockedPageBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

/** Keeps row-0 and in-viewport tiles completely still when moving horizontally so All Apps never jitters. */
@OptIn(ExperimentalFoundationApi::class)
private object TvAllAppsBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (containerSize <= 0f || size <= 0f) return 0f
        val margin = 28f
        val topBound = margin
        val bottomBound = (containerSize - margin).coerceAtLeast(topBound + 1f)
        val itemEnd = offset + size
        return when {
            offset < topBound -> offset - topBound
            itemEnd > bottomBound && size <= (bottomBound - topBound) -> itemEnd - bottomBound
            else -> 0f
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: LauncherUiState,
    scrollState: ScrollState,
    onTileFocused: (String) -> Unit,
    onMoveSound: () -> Unit,
    onAppClick: (AppItem) -> Unit,
    onAppLongClick: (AppItem) -> Unit,
    onChannelClick: (Int) -> Unit,
    onChannelLongClick: (Int) -> Unit,
    onVoiceClick: () -> Unit,
    onVoiceLongClick: () -> Unit,
    onUsbClick: () -> Unit,
    onWifiClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNowPlayingClick: () -> Unit,
    onSlideshowClick: () -> Unit,
    onSlideshowLongClick: () -> Unit,
    onTabSelected: (DockTab) -> Unit
) {
    val scope = rememberCoroutineScope()
    val dockFocusRequesters = remember { List(4) { FocusRequester() } }
    val firstDockFocus = dockFocusRequesters[0]
    val gridFirstRowRequesters = remember { List(4) { FocusRequester() } }
    val settingsPillFocus = remember { FocusRequester() }
    val en = state.isEnglish

    var isAllAppsActive by remember { mutableStateOf(scrollState.value > 40) }
    var suspendBringIntoView by remember { mutableStateOf(false) }
    var prevScrollValue by remember { mutableIntStateOf(scrollState.value) }
    var allAppsTopPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect { cur ->
            if (cur > 40) {
                isAllAppsActive = true
            } else if (prevScrollValue > 40 && cur <= 8 && isAllAppsActive && !suspendBringIntoView) {
                isAllAppsActive = false
                try {
                    firstDockFocus.requestFocus()
                } catch (_: Exception) {
                }
            }
            prevScrollValue = cur
        }
    }

    LaunchedEffect(state.dockApps.firstOrNull()?.pkg) {
        if (state.activeDialog == null && !state.slideshow.fullscreenOpen && !isAllAppsActive) {
            try {
                firstDockFocus.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    fun returnToDockTop() {
        scope.launch {
            suspendBringIntoView = true
            isAllAppsActive = false
            val movedToDock = try {
                firstDockFocus.requestFocus()
                true
            } catch (_: Exception) {
                false
            }
            if (!movedToDock) {
                try {
                    settingsPillFocus.requestFocus()
                } catch (_: Exception) {
                }
            }
            delay(40L)
            try {
                scrollState.animateScrollTo(0)
            } finally {
                if (scrollState.value > 0) {
                    scrollState.scrollTo(0)
                }
                suspendBringIntoView = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LauncherBackground(
            theme = state.theme,
            bgSpec = state.bgSpec,
            alphaPercent = state.bgAlphaPercent,
            customVersion = state.customWallpaperVersion
        )

        val safePadding = WindowInsets.safeDrawing.asPaddingValues()

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(safePadding)
        ) {
            val viewportHeight = maxHeight
            val isCompactWidth = maxWidth < 680.dp
            val hPad = if (isCompactWidth) 20.dp else 44.dp
            val activeBringIntoView = if (isAllAppsActive && !suspendBringIntoView) {
                TvAllAppsBringIntoViewSpec
            } else {
                LockedPageBringIntoViewSpec
            }

            CompositionLocalProvider(LocalBringIntoViewSpec provides activeBringIntoView) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = hPad, vertical = 14.dp)
                ) {
                    // First viewport page (Header + TV frame + Dock anchored at bottom)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = (viewportHeight - 36.dp).coerceAtLeast(360.dp))
                            .padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.Start),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val showWeatherCard = state.weather.enabled
                                val showHumidityCard = state.display.humidityOn
                                val showSysCards = state.sysBars.enabled
                                val weatherCard: MiniCardSpec? = if (showWeatherCard) {
                                    val w = state.weather
                                    val desc = if (w.hasData) WeatherService.describe(w.code, en) else null
                                    val locLocale = Locale.forLanguageTag(state.lang)
                                    val fallbackLoc = when {
                                        state.wxWardLabel.isNotBlank() &&
                                            state.wxWardLabel != "—" &&
                                            !state.wxWardLabel.equals("Whole province/city", ignoreCase = true) &&
                                            !state.wxWardLabel.equals("Cả tỉnh/thành phố", ignoreCase = true) -> {
                                            "${state.wxWardLabel}, ${state.wxProvinceLabel}"
                                        }
                                        state.wxProvinceLabel.isNotBlank() &&
                                            !state.wxProvinceLabel.equals("Automatic", ignoreCase = true) &&
                                            !state.wxProvinceLabel.equals("Tự động", ignoreCase = true) -> state.wxProvinceLabel
                                        else -> ""
                                    }
                                    val rawLocationTitle = w.city.trim().ifEmpty { fallbackLoc }
                                    val locationTitle = rawLocationTitle
                                        .ifEmpty { if (en) "WEATHER" else "THỜI TIẾT" }
                                        .uppercase(locLocale)
                                    MiniCardSpec(
                                        dotColor = Color(0xFFFF9F0A),
                                        label = locationTitle,
                                        badge = if (w.hasData) "${w.rain}%☂" else "",
                                        mainValue = if (w.hasData) "${Math.round(w.temp)}°" else "…",
                                        subValue = if (w.hasData) "${desc!!.first} ${Math.round(w.min)}/${Math.round(w.max)}°" else if (en) "Loading" else "Đang tải",
                                        progress = if (w.hasData) ((w.temp.toFloat() / 42f).coerceIn(0.12f, 1f)) else 0.15f,
                                        expanded = true,
                                        mainIcon = Icons.Filled.DeviceThermostat,
                                        mainIconTint = Color(0xFFFF9F0A)
                                    )
                                } else null

                                val bottomCards = buildList {
                                    if (showHumidityCard) {
                                        val w = state.weather
                                        val dc = state.deviceClimate
                                        val src = state.display.climateSource
                                        val hasSensorReading = dc.hasAnySensor || dc.tempC != null || dc.humidity != null
                                        val useSensorTemp = src != ClimateDataSource.WEB && dc.tempC != null
                                        val useSensorHum = src != ClimateDataSource.WEB && dc.humidity != null

                                        val displayTemp: Double? = when (src) {
                                            ClimateDataSource.SENSOR -> dc.tempC
                                            ClimateDataSource.WEB -> if (w.hasData) w.temp else null
                                            ClimateDataSource.AUTO -> dc.tempC ?: if (w.hasData) w.temp else null
                                        }
                                        val displayHum: Int? = when (src) {
                                            ClimateDataSource.SENSOR -> dc.humidity
                                            ClimateDataSource.WEB -> if (w.hasData) w.humidity else null
                                            ClimateDataSource.AUTO -> dc.humidity ?: if (w.hasData) w.humidity else null
                                        }
                                        val showingSensor = when (src) {
                                            ClimateDataSource.SENSOR -> true
                                            ClimateDataSource.WEB -> false
                                            ClimateDataSource.AUTO -> useSensorTemp || useSensorHum || hasSensorReading
                                        }
                                        val tempText = when {
                                            displayTemp != null && useSensorTemp -> String.format(Locale.US, "%.1f°", displayTemp)
                                            displayTemp != null -> "${Math.round(displayTemp)}°"
                                            src == ClimateDataSource.SENSOR -> "—"
                                            else -> "…"
                                        }
                                        val humText = when {
                                            displayHum != null -> "$displayHum%"
                                            src == ClimateDataSource.SENSOR && displayTemp == null && !hasSensorReading -> {
                                                if (en) "No sensor" else "Chưa có CB"
                                            }
                                            src == ClimateDataSource.SENSOR -> "—"
                                            else -> "…"
                                        }
                                        val noSensorInSensorMode = src == ClimateDataSource.SENSOR && displayTemp == null && displayHum == null && !hasSensorReading
                                        add(
                                            MiniCardSpec(
                                                dotColor = if (showingSensor) Color(0xFF30D158) else Color(0xFF38BDF8),
                                                label = if (en) "TEMP·HUM" else "NHIỆT·ẨM",
                                                badge = "",
                                                mainValue = tempText,
                                                subValue = humText,
                                                progress = when {
                                                    displayHum != null -> (displayHum / 100f).coerceIn(0.08f, 1f)
                                                    displayTemp != null -> (displayTemp.toFloat() / 45f).coerceIn(0.12f, 1f)
                                                    else -> 0.12f
                                                },
                                                sourceIcon = if (showingSensor) Icons.Filled.Sensors else Icons.Filled.Cloud,
                                                sourceIconTint = when {
                                                    noSensorInSensorMode -> Color(0xFFFF9F0A)
                                                    showingSensor -> Color(0xFF30D158)
                                                    else -> Color(0xFF38BDF8)
                                                },
                                                sourceContentDescription = if (showingSensor) {
                                                    if (en) "Device sensor" else "Cảm biến thiết bị"
                                                } else {
                                                    if (en) "Web weather data" else "Dữ liệu web"
                                                },
                                                mainIcon = Icons.Filled.DeviceThermostat,
                                                mainIconTint = Color(0xFFFF9F0A),
                                                subIcon = if (noSensorInSensorMode) null else Icons.Filled.WaterDrop,
                                                subIconTint = Color(0xFF38BDF8)
                                            )
                                        )
                                    }
                                    if (showSysCards) {
                                        val s = state.sysBars
                                        add(
                                            MiniCardSpec(
                                                dotColor = Color(0xFF8B5CF6),
                                                label = "RAM",
                                                badge = "",
                                                mainValue = "${s.ramPercent}%",
                                                subValue = String.format(Locale.US, "%.1f/%.0fGB", s.ramUsedGb, s.ramTotalGb),
                                                progress = (s.ramPercent / 100f).coerceIn(0.06f, 1f)
                                            )
                                        )
                                        add(
                                            MiniCardSpec(
                                                dotColor = Color(0xFF14B8A6),
                                                label = if (en) "STORAGE" else "BỘ NHỚ",
                                                badge = "",
                                                mainValue = "${s.storagePercent}%",
                                                subValue = String.format(Locale.US, "%.0f/%.0fGB", s.storageUsedGb, s.storageTotalGb),
                                                progress = (s.storagePercent / 100f).coerceIn(0.06f, 1f)
                                            )
                                        )
                                    }
                                }
                                val showMiniGrid = weatherCard != null || bottomCards.isNotEmpty()
                                Column(
                                    modifier = Modifier
                                        .wrapContentWidth(Alignment.Start)
                                        .widthIn(max = if (showMiniGrid && !isCompactWidth) 488.dp else 330.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(barSurfaceBrush(state.theme), RoundedCornerShape(20.dp))
                                        .border(1.dp, Color(0x3DFFFFFF), RoundedCornerShape(20.dp))
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    @Composable
                                    fun ClockTextColumn(modifier: Modifier = Modifier) {
                                        Column(modifier = modifier) {
                                            Text(
                                                text = state.header.greeting,
                                                color = LauncherColors.TextSecondary,
                                                fontSize = 11.5.sp,
                                                letterSpacing = 0.8.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Spacer(Modifier.height(1.dp))
                                            ClockTimeText(
                                                timeText = state.header.timeText.ifEmpty { "00:00" },
                                                fontStyle = state.display.clockFont,
                                                bold = state.display.clockBold,
                                                dashed = state.display.clockDashed,
                                                fontSize = if (isCompactWidth) 34.sp else 42.sp
                                            )

                                            Spacer(Modifier.height(1.dp))
                                            Text(
                                                text = state.header.dateText,
                                                color = LauncherColors.TextSecondary,
                                                fontSize = 11.5.sp,
                                                letterSpacing = 0.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (state.header.lunarText.isNotEmpty()) {
                                                Text(
                                                    text = state.header.lunarText,
                                                    color = LauncherColors.TextMuted,
                                                    fontSize = 10.5.sp,
                                                    letterSpacing = 0.2.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(top = 1.dp)
                                                )
                                            }

                                            if (!state.header.holidayText.isNullOrEmpty()) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Favorite,
                                                        contentDescription = null,
                                                        tint = LauncherColors.HeartRed,
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        text = state.header.holidayText,
                                                        color = LauncherColors.TextSecondary,
                                                        fontSize = 10.5.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            if (!state.header.lunarAlertText.isNullOrEmpty()) {
                                                Text(
                                                    text = state.header.lunarAlertText,
                                                    color = LauncherColors.AlertRed,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(top = 3.dp)
                                                )
                                            }
                                        }
                                    }

                                    @Composable
                                    fun MiniCardsGrid(
                                        maxTotalWidth: androidx.compose.ui.unit.Dp,
                                        modifier: Modifier = Modifier
                                    ) {
                                        Column(
                                            modifier = modifier.widthIn(max = maxTotalWidth),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            when {
                                                weatherCard != null && (bottomCards.size == 1 || bottomCards.size >= 3) -> {
                                                    val wxW = (128 + (weatherCard.label.length - 8).coerceIn(0, 16) * 3)
                                                        .dp
                                                        .coerceAtMost(maxTotalWidth - 96.dp)
                                                    val humCard = bottomCards[0]
                                                    val rowTotalW = (wxW + 5.dp + 88.dp).coerceAtMost(maxTotalWidth)
                                                    Row(
                                                        modifier = Modifier
                                                            .width(rowTotalW)
                                                            .height(IntrinsicSize.Min),
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                    ) {
                                                        MiniStatCard(
                                                            spec = weatherCard,
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxHeight()
                                                        )
                                                        MiniStatCard(
                                                            spec = humCard,
                                                            modifier = Modifier
                                                                .width(IntrinsicSize.Max)
                                                                .fillMaxHeight()
                                                        )
                                                    }
                                                    if (bottomCards.size > 1) {
                                                        Row(
                                                            modifier = Modifier
                                                                .width(rowTotalW)
                                                                .height(IntrinsicSize.Min),
                                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                        ) {
                                                            bottomCards.drop(1).forEach { card ->
                                                                MiniStatCard(
                                                                    spec = card,
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .fillMaxHeight()
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                weatherCard != null -> {
                                                    val minNeeded = if (bottomCards.isNotEmpty()) 188 else 128
                                                    val wxW = (minNeeded + (weatherCard.label.length - 8).coerceIn(0, 20) * 3)
                                                        .dp
                                                        .coerceAtMost(maxTotalWidth)
                                                    MiniStatCard(
                                                        spec = weatherCard,
                                                        modifier = Modifier.width(wxW)
                                                    )
                                                    if (bottomCards.isNotEmpty()) {
                                                        Row(
                                                            modifier = Modifier
                                                                .width(wxW)
                                                                .height(IntrinsicSize.Min),
                                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                        ) {
                                                            bottomCards.forEach { card ->
                                                                MiniStatCard(
                                                                    spec = card,
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .fillMaxHeight()
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                bottomCards.isNotEmpty() -> {
                                                    Row(
                                                        modifier = Modifier.height(IntrinsicSize.Min),
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                    ) {
                                                        bottomCards.forEach { card ->
                                                            MiniStatCard(
                                                                spec = card,
                                                                modifier = Modifier
                                                                    .width(IntrinsicSize.Max)
                                                                    .fillMaxHeight()
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (showMiniGrid && !isCompactWidth) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            ClockTextColumn(modifier = Modifier.widthIn(max = 200.dp))
                                            MiniCardsGrid(maxTotalWidth = 266.dp)
                                        }
                                    } else {
                                        ClockTextColumn(modifier = Modifier.widthIn(max = 290.dp))
                                        if (showMiniGrid) {
                                            Spacer(Modifier.height(8.dp))
                                            MiniCardsGrid(maxTotalWidth = 280.dp)
                                        }
                                    }
                                }

                                if (state.nowPlayingPill.visible) {
                                    Spacer(Modifier.height(8.dp))
                                    GlassPill(
                                        theme = state.theme,
                                        testTag = "now_playing_pill",
                                        onFocused = onMoveSound,
                                        onClick = onNowPlayingClick
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .widthIn(max = 300.dp)
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.MusicNote,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = state.nowPlayingPill.text,
                                                color = Color(0xE6FFFFFF),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            // Top-Right: Status Pills + Photo Slideshow Card
                            Column(horizontalAlignment = Alignment.End) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (state.display.voiceOn) {
                                        GlassPill(
                                            theme = state.theme,
                                            testTag = "pill_voice",
                                            onFocused = onMoveSound,
                                            onClick = onVoiceClick,
                                            onLongClick = onVoiceLongClick
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Mic,
                                                    contentDescription = if (en) "Voice" else "Giọng nói",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = if (en) "Voice" else "Giọng nói",
                                                    color = Color.White,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }

                                    if (state.usbConnected) {
                                        GlassPill(
                                            theme = state.theme,
                                            testTag = "pill_usb",
                                            onFocused = onMoveSound,
                                            onClick = onUsbClick
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Usb,
                                                    contentDescription = "USB",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(text = "USB", color = Color.White, fontSize = 13.sp)
                                            }
                                        }
                                    }

                                    if (state.networkPill.visible) {
                                        GlassPill(
                                            theme = state.theme,
                                            testTag = "pill_wifi",
                                            onFocused = onMoveSound,
                                            onClick = onWifiClick
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .widthIn(max = 160.dp)
                                                    .padding(horizontal = 16.dp, vertical = 9.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (state.networkPill.isEthernet) {
                                                        Icons.Filled.SettingsEthernet
                                                    } else {
                                                        Icons.Filled.Wifi
                                                    },
                                                    contentDescription = state.networkPill.label,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = state.networkPill.label,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    GlassPill(
                                        modifier = Modifier.focusRequester(settingsPillFocus),
                                        theme = state.theme,
                                        testTag = "pill_settings",
                                        onFocused = onMoveSound,
                                        onClick = onSettingsClick
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Settings,
                                                contentDescription = if (en) "Settings" else "Cài đặt",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = if (en) "Settings" else "Cài đặt",
                                                color = Color.White,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }

                                if (state.display.photoFrameOn) {
                                    Spacer(Modifier.height(10.dp))
                                    LedFocusTile(
                                        modifier = Modifier.size(if (isCompactWidth) 114.dp else 148.dp),
                                        theme = state.theme,
                                        cornerRadius = 18.dp,
                                        focusScale = 1.05f,
                                        showOuterRing = true,
                                        testTag = "photo_slideshow_card",
                                        onFocused = {
                                            onTileFocused(if (en) "Photo slideshow" else "Trình chiếu ảnh")
                                        },
                                        onClick = onSlideshowClick,
                                        onLongClick = onSlideshowLongClick
                                    ) {
                                        Crossfade(
                                            targetState = state.slideshow.cardBitmap,
                                            animationSpec = tween(500),
                                            label = "card_photo"
                                        ) { bm ->
                                            if (bm != null) {
                                                Image(
                                                    bitmap = bm.asImageBitmap(),
                                                    contentDescription = if (en) "Photo slideshow" else "Trình chiếu ảnh",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                        if (!state.slideshow.hintText.isNullOrEmpty() && state.slideshow.cardBitmap == null) {
                                            Text(
                                                text = state.slideshow.hintText,
                                                color = Color(0xB3FFFFFF),
                                                fontSize = 11.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .padding(10.dp)
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomCenter)
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(Color.Transparent, Color(0xB8000000))
                                                    )
                                                )
                                                .padding(horizontal = 10.dp, vertical = 7.dp)
                                        ) {
                                            Text(
                                                text = if (en) "Photo slideshow" else "Trình chiếu ảnh",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Watch TV section
                        if (state.display.tvOn) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                Text(
                                    text = (if (en) "WATCH TV" else "XEM TV"),
                                    color = Color(0xB3FFFFFF),
                                    fontSize = 12.sp,
                                    letterSpacing = 1.0.sp,
                                    modifier = Modifier.padding(start = 10.dp, bottom = 4.dp)
                                )
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(barSurfaceBrush(state.theme), RoundedCornerShape(22.dp))
                                        .border(1.dp, Color(0x3DFFFFFF), RoundedCornerShape(22.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    state.tvChannels.forEach { ch ->
                                        LedFocusTile(
                                            modifier = Modifier
                                                .width(if (isCompactWidth) 128.dp else 160.dp)
                                                .aspectRatio(16f / 9f),
                                            theme = state.theme,
                                            cornerRadius = 14.dp,
                                            focusScale = 1.05f,
                                            testTag = "tv_channel_${ch.slot}",
                                            onFocused = { onTileFocused(ch.name) },
                                            onClick = { onChannelClick(ch.slot) },
                                            onLongClick = { onChannelLongClick(ch.slot) }
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(8.dp),
                                                verticalArrangement = Arrangement.Center,
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = ch.name,
                                                    color = Color.White,
                                                    fontSize = if (isCompactWidth) 18.sp else 22.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "FPT Play",
                                                    color = Color(0x99FFFFFF),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Dock Section (82% width centered)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .padding(start = 12.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = state.focusedLabel,
                                    color = Color.White,
                                    fontSize = if (isCompactWidth) 20.sp else 24.sp,
                                    fontWeight = FontWeight.Light,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = when (state.dockTab) {
                                        DockTab.APPS -> if (en) "Apps" else "Ứng dụng"
                                        DockTab.RECENTS -> if (en) "Recently opened" else "Mở gần đây nhất"
                                        DockTab.MOST_USED -> if (en) "Most used" else "Thường dùng"
                                    },
                                    color = Color(0xB3FFFFFF),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(end = 10.dp)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DockTab.entries.forEachIndexed { idx, tab ->
                                        DockTabDot(
                                            color = LauncherColors.TabDots[idx],
                                            selected = state.dockTab == tab,
                                            testTag = "dock_tab_${tab.name.lowercase(Locale.ROOT)}",
                                            onFocused = onMoveSound,
                                            onClick = { onTabSelected(tab) }
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(barSurfaceBrush(state.theme), RoundedCornerShape(26.dp))
                                    .border(1.dp, Color(0x3DFFFFFF), RoundedCornerShape(26.dp))
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                if (state.dockApps.isEmpty()) {
                                    Text(
                                        text = if (state.dockTab == DockTab.RECENTS) {
                                            if (en) "No recently opened apps yet" else "Chưa có ứng dụng nào được mở gần đây"
                                        } else {
                                            if (en) "No data yet" else "Chưa có dữ liệu"
                                        },
                                        color = Color(0x99FFFFFF),
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 28.dp)
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        for (i in 0 until 4) {
                                            val app = state.dockApps.getOrNull(i)
                                            if (app != null) {
                                                AppTileCard(
                                                    app = app,
                                                    theme = state.theme,
                                                    focusScale = 1.06f,
                                                    showOuterRing = false,
                                                    isMoving = state.movingPkg == app.pkg,
                                                    testTag = "dock_tile_$i",
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .aspectRatio(16f / 9f)
                                                        .focusRequester(dockFocusRequesters[i])
                                                        .onPreviewKeyEvent { ev ->
                                                            if (ev.type == KeyEventType.KeyDown &&
                                                                ev.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN
                                                            ) {
                                                                if (state.gridApps.isNotEmpty()) {
                                                                    suspendBringIntoView = true
                                                                    isAllAppsActive = true
                                                                    val targetCol = i.coerceAtMost(
                                                                        (state.gridApps.size - 1).coerceAtLeast(0)
                                                                    ).coerceAtMost(3)
                                                                    scope.launch {
                                                                        try {
                                                                            delay(16L)
                                                                            try {
                                                                                gridFirstRowRequesters[targetCol].requestFocus()
                                                                            } catch (_: Exception) {
                                                                                try {
                                                                                    gridFirstRowRequesters[0].requestFocus()
                                                                                } catch (_: Exception) {
                                                                                }
                                                                            }
                                                                            val scrollTarget = (allAppsTopPx - 8).coerceAtLeast(0)
                                                                            if (scrollTarget > 0) {
                                                                                scrollState.animateScrollTo(scrollTarget)
                                                                            }
                                                                        } finally {
                                                                            suspendBringIntoView = false
                                                                        }
                                                                    }
                                                                }
                                                                true
                                                            } else {
                                                                false
                                                            }
                                                        },
                                                    onFocused = { onTileFocused(app.label) },
                                                    onClick = { onAppClick(app) },
                                                    onLongClick = { onAppLongClick(app) }
                                                )
                                            } else {
                                                Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Below the fold: Up Arrow pill + All Apps 4-column grid
                    Spacer(Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                allAppsTopPx = coords.positionInParent().y.toInt()
                            }
                            .focusProperties { canFocus = isAllAppsActive }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            GlassPill(
                                modifier = Modifier.onPreviewKeyEvent { ev ->
                                    if (ev.type == KeyEventType.KeyDown &&
                                        ev.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP
                                    ) {
                                        returnToDockTop()
                                        true
                                    } else {
                                        false
                                    }
                                },
                                theme = state.theme,
                                testTag = "up_arrow_button",
                                onFocused = onMoveSound,
                                onClick = { returnToDockTop() }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(72.dp)
                                        .height(48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowUp,
                                        contentDescription = if (en) "Back to top" else "Lên đầu trang",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        if (state.gridApps.isNotEmpty()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp, bottom = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = if (en) "All apps" else "Tất cả ứng dụng",
                                        color = Color(0xCCFFFFFF),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = "${state.gridApps.size} " + (if (en) "apps" else "ứng dụng"),
                                        color = Color(0x88FFFFFF),
                                        fontSize = 13.sp
                                    )
                                }

                                val rows = state.gridApps.chunked(4)
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(18.dp)
                                ) {
                                    rows.forEachIndexed { rowIdx, rowApps ->
                                        var rowHasFocus by remember(rowIdx) { mutableStateOf(false) }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .zIndex(if (rowHasFocus) 20f else 0f)
                                                .onFocusChanged { rowHasFocus = it.hasFocus },
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            for (colIdx in 0 until 4) {
                                                val app = rowApps.getOrNull(colIdx)
                                                val flatIdx = rowIdx * 4 + colIdx
                                                if (app != null) {
                                                    AppTileCard(
                                                        app = app,
                                                        theme = state.theme,
                                                        focusScale = 1.08f,
                                                        showOuterRing = true,
                                                        showFocusedLabelOnTop = true,
                                                        isMoving = state.movingPkg == app.pkg,
                                                        testTag = "grid_tile_$flatIdx",
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .aspectRatio(16f / 9f)
                                                            .then(
                                                                if (rowIdx == 0) {
                                                                    Modifier.focusRequester(gridFirstRowRequesters[colIdx])
                                                                } else {
                                                                    Modifier
                                                                }
                                                            ),
                                                        onFocused = { onTileFocused(app.label) },
                                                        onClick = { onAppClick(app) },
                                                        onLongClick = { onAppLongClick(app) }
                                                    )
                                                } else {
                                                    Spacer(Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(32.dp))
                        }
                    }
                }
            }
        }
    }
}
