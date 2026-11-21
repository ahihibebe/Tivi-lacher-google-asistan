package vn.launcher.tv.ui.screens

import android.view.KeyEvent as AndroidKeyEvent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Usb
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.launcher.tv.R
import vn.launcher.tv.data.WeatherService
import vn.launcher.tv.model.AppItem
import vn.launcher.tv.model.ClockFontStyle
import vn.launcher.tv.model.DockTab
import vn.launcher.tv.model.LauncherTheme
import vn.launcher.tv.model.LauncherUiState
import vn.launcher.tv.ui.components.GlassPill
import vn.launcher.tv.ui.components.LedFocusTile
import vn.launcher.tv.ui.components.barSurfaceBrush
import vn.launcher.tv.ui.components.tvDialogClickable
import vn.launcher.tv.ui.theme.LauncherColors
import java.io.File
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
private object LockedPageBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
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
    var prevScrollValue by remember { mutableIntStateOf(scrollState.value) }
    var allAppsTopPx by remember { mutableIntStateOf(0) }
    var focusedGridAppLabel by remember { mutableStateOf("") }

    LaunchedEffect(scrollState.value) {
        if (scrollState.value > 40) {
            isAllAppsActive = true
        } else if (prevScrollValue > 40 && scrollState.value <= 8 && isAllAppsActive) {
            isAllAppsActive = false
            focusedGridAppLabel = ""
            try {
                firstDockFocus.requestFocus()
            } catch (_: Exception) {
            }
        }
        prevScrollValue = scrollState.value
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
            val defaultBringIntoView = LocalBringIntoViewSpec.current
            val activeBringIntoView = if (isAllAppsActive) defaultBringIntoView else LockedPageBringIntoViewSpec

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
                    // Header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.Start),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Left: Unified Clock / Date / Compact Weather, Temp-Humidity & System Mini-Cards
                        Column(modifier = Modifier.weight(1f)) {
                            val showWeatherCard = state.weather.enabled
                            val showHumidityCard = state.display.humidityOn
                            val showSysCards = state.sysBars.enabled
                            val miniCards = buildList {
                                if (showWeatherCard) {
                                    val w = state.weather
                                    val desc = if (w.hasData) WeatherService.describe(w.code, en) else null
                                    add(
                                        MiniCardSpec(
                                            dotColor = Color(0xFFFF9F0A),
                                            label = if (en) "WEATHER" else "THỜI TIẾT",
                                            badge = if (w.hasData) "${w.rain}%☂" else "",
                                            mainValue = if (w.hasData) "${Math.round(w.temp)}°" else "…",
                                            subValue = if (w.hasData) "${desc!!.first} ${Math.round(w.min)}/${Math.round(w.max)}°" else if (en) "Loading" else "Đang tải",
                                            progress = if (w.hasData) ((w.temp.toFloat() / 42f).coerceIn(0.12f, 1f)) else 0.15f
                                        )
                                    )
                                }
                                if (showHumidityCard) {
                                    val w = state.weather
                                    val dc = state.deviceClimate
                                    val exactTemp = dc.tempC
                                    val exactHum = dc.humidity
                                    val displayHum = exactHum ?: if (w.hasData) w.humidity else null
                                    val displayTemp = exactTemp ?: if (w.hasData) w.temp else null
                                    val hasSensor = dc.hasAnySensor
                                    add(
                                        MiniCardSpec(
                                            dotColor = if (hasSensor) Color(0xFF30D158) else Color(0xFF38BDF8),
                                            label = if (en) "TEMP · HUM" else "NHIỆT · ẨM",
                                            badge = "",
                                            mainValue = if (displayHum != null) "$displayHum%" else if (hasSensor) "—" else "…",
                                            subValue = if (displayTemp != null) {
                                                String.format(Locale.US, "%.1f°C", displayTemp)
                                            } else if (hasSensor) {
                                                if (en) "Sensor" else "Cảm biến"
                                            } else if (en) {
                                                "Loading"
                                            } else {
                                                "Đang tải"
                                            },
                                            progress = if (displayHum != null) (displayHum / 100f).coerceIn(0.08f, 1f) else 0.12f,
                                            sensorContentDescription = if (hasSensor) {
                                                if (en) "Device sensor" else "Cảm biến thiết bị"
                                            } else {
                                                null
                                            }
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
                                            subValue = String.format(Locale.US, "%.1f/%.0fG", s.ramUsedGb, s.ramTotalGb),
                                            progress = (s.ramPercent / 100f).coerceIn(0.06f, 1f)
                                        )
                                    )
                                    add(
                                        MiniCardSpec(
                                            dotColor = Color(0xFF14B8A6),
                                            label = if (en) "STORAGE" else "Ổ CỨNG",
                                            badge = "",
                                            mainValue = "${s.storagePercent}%",
                                            subValue = String.format(Locale.US, "%.0f/%.0fG", s.storageUsedGb, s.storageTotalGb),
                                            progress = (s.storagePercent / 100f).coerceIn(0.06f, 1f)
                                        )
                                    )
                                }
                            }
                            val showMiniGrid = miniCards.isNotEmpty()
                            Column(
                                modifier = Modifier
                                    .wrapContentWidth(Alignment.Start)
                                    .widthIn(max = if (showMiniGrid && !isCompactWidth) 460.dp else 320.dp)
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
                                fun MiniCardsGrid(modifier: Modifier = Modifier) {
                                    Column(
                                        modifier = modifier,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (miniCards.size == 3) {
                                            MiniStatCard(
                                                spec = miniCards[0],
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                MiniStatCard(spec = miniCards[1], modifier = Modifier.weight(1f))
                                                MiniStatCard(spec = miniCards[2], modifier = Modifier.weight(1f))
                                            }
                                        } else {
                                            miniCards.chunked(2).forEach { pair ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    pair.forEach { card ->
                                                        MiniStatCard(spec = card, modifier = Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (showMiniGrid && !isCompactWidth) {
                                    val gridWidth = if (miniCards.size == 1) 114.dp else 184.dp
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        ClockTextColumn(modifier = Modifier.widthIn(max = 236.dp))
                                        MiniCardsGrid(modifier = Modifier.width(gridWidth))
                                    }
                                } else {
                                    ClockTextColumn(modifier = Modifier.widthIn(max = 290.dp))
                                    if (showMiniGrid) {
                                        Spacer(Modifier.height(8.dp))
                                        MiniCardsGrid(modifier = Modifier.width(if (miniCards.size == 1) 124.dp else 196.dp))
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

                    // Dock Section (80% width centered)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 10.dp)
                    ) {
                        // Dock Header: Focused Title + Tab Title + 3 traffic dots
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

                        // Dock Bar
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
                                                                isAllAppsActive = true
                                                                val targetCol = i.coerceAtMost(
                                                                    (state.gridApps.size - 1).coerceAtLeast(0)
                                                                ).coerceAtMost(3)
                                                                scope.launch {
                                                                    delay(16L)
                                                                    try {
                                                                        gridFirstRowRequesters[targetCol].requestFocus()
                                                                    } catch (_: Exception) {
                                                                        try {
                                                                            gridFirstRowRequesters[0].requestFocus()
                                                                        } catch (_: Exception) {
                                                                        }
                                                                    }
                                                                    val scrollTarget = (allAppsTopPx - 12).coerceAtLeast(0)
                                                                    if (scrollTarget > 0) {
                                                                        scrollState.animateScrollTo(scrollTarget)
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
                // Focus is gated behind isAllAppsActive so D-pad Down only opens All Apps from Dock tiles.
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
                                    .padding(top = 10.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = if (en) "All apps" else "Tất cả ứng dụng",
                                    color = Color(0xCCFFFFFF),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                val activeName = focusedGridAppLabel.ifBlank {
                                    if (isAllAppsActive) state.focusedLabel else ""
                                }
                                if (activeName.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(LauncherColors.accent(state.theme).copy(alpha = 0.25f))
                                            .border(
                                                1.dp,
                                                LauncherColors.accent(state.theme).copy(alpha = 0.72f),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = activeName,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
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
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                rows.forEachIndexed { rowIdx, rowApps ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
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
                                                    onFocused = {
                                                        focusedGridAppLabel = app.label
                                                        onTileFocused(app.label)
                                                    },
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

@Composable
private fun LauncherBackground(
    theme: LauncherTheme,
    bgSpec: String,
    alphaPercent: Int,
    customVersion: Int
) {
    val context = LocalContext.current
    val alpha = (alphaPercent.coerceIn(0, 100) / 100f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LauncherColors.BackgroundBase)
    ) {
        when {
            bgSpec == "theme" && theme != LauncherTheme.MAC_OS -> {
                val res = if (theme == LauncherTheme.TULIP_BLUE) R.drawable.bg_tulip_blue else R.drawable.bg_tulip_purple
                Image(
                    painter = painterResource(res),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(alpha)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x1A000000))
                )
            }
            bgSpec == "custom" -> {
                val customBmp = remember(customVersion) {
                    val f = File(context.filesDir, "wallpaper.jpg")
                    if (f.exists()) BitmapFactory.decodeFile(f.path)?.asImageBitmap() else null
                }
                if (customBmp != null) {
                    Image(
                        bitmap = customBmp,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(alpha)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x66000000))
                    )
                } else {
                    PresetGlowCanvas(0, alpha)
                }
            }
            else -> {
                val idx = bgSpec.removePrefix("preset:").toIntOrNull()?.coerceIn(0, 4) ?: 0
                PresetGlowCanvas(idx, alpha)
            }
        }
    }
}

@Composable
private fun PresetGlowCanvas(idx: Int, alpha: Float) {
    val glow = LauncherColors.PresetGlows.getOrElse(idx) { LauncherColors.PresetGlows[0] }
    if (glow == Color.Transparent) return
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
    ) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(glow, Color.Transparent),
                center = Offset(size.width * 0.35f, size.height * 0.30f),
                radius = size.width * 0.7f
            )
        )
    }
}

@Composable
fun ClockTimeText(
    timeText: String,
    fontStyle: ClockFontStyle,
    bold: Boolean,
    dashed: Boolean,
    fontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    val family = remember(fontStyle) { clockFontFamily(fontStyle) }
    val weight = if (bold) FontWeight.Bold else FontWeight.Light
    val density = LocalDensity.current

    if (!dashed) {
        Text(
            text = timeText,
            color = Color.White,
            fontSize = fontSize,
            fontFamily = family,
            fontWeight = weight,
            letterSpacing = (-1).sp,
            maxLines = 1,
            modifier = modifier
        )
    } else {
        val strokeWidthPx = with(density) { (if (bold) 2.4.dp else 1.5.dp).toPx() }
        val dashOnPx = with(density) { 5.5.dp.toPx() }
        val dashOffPx = with(density) { 3.dp.toPx() }
        Box(modifier = modifier) {
            Text(
                text = timeText,
                style = TextStyle(
                    color = Color(0x28FFFFFF),
                    fontSize = fontSize,
                    fontFamily = family,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    letterSpacing = (-1).sp
                ),
                maxLines = 1
            )
            Text(
                text = timeText,
                style = TextStyle(
                    color = Color.White,
                    fontSize = fontSize,
                    fontFamily = family,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    letterSpacing = (-1).sp,
                    drawStyle = Stroke(
                        width = strokeWidthPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashOnPx, dashOffPx), 0f)
                    )
                ),
                maxLines = 1
            )
        }
    }
}

private fun clockFontFamily(style: ClockFontStyle): FontFamily = when (style) {
    ClockFontStyle.DEFAULT -> FontFamily.SansSerif
    ClockFontStyle.OUTFIT -> FontFamily(Font(R.font.outfit))
    ClockFontStyle.ORBITRON -> FontFamily(Font(R.font.orbitron))
    ClockFontStyle.JETBRAINS_MONO -> FontFamily(Font(R.font.jetbrains_mono))
    ClockFontStyle.PLAYFAIR -> FontFamily(Font(R.font.playfair_display))
}

private data class MiniCardSpec(
    val dotColor: Color,
    val label: String,
    val badge: String,
    val mainValue: String,
    val subValue: String,
    val progress: Float,
    val sensorContentDescription: String? = null
)

@Composable
private fun MiniStatCard(
    spec: MiniCardSpec,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x1EFFFFFF), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0x42FFFFFF), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(spec.dotColor)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = spec.label,
                color = Color(0xCCFFFFFF),
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (spec.sensorContentDescription != null) {
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Filled.Sensors,
                    contentDescription = spec.sensorContentDescription,
                    tint = Color(0xFF30D158),
                    modifier = Modifier.size(10.dp)
                )
            }
            if (spec.badge.isNotEmpty()) {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = spec.badge,
                    color = Color(0xB8FFFFFF),
                    fontSize = 7.sp,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(1.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = spec.mainValue,
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (spec.subValue.isNotEmpty()) {
                Text(
                    text = spec.subValue,
                    color = Color(0xD9FFFFFF),
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 0.5.dp)
                )
            }
        }
        Spacer(Modifier.height(2.5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color(0x30FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(spec.progress.coerceIn(0f, 1f))
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(spec.dotColor)
            )
        }
    }
}

@Composable
private fun DockTabDot(
    color: Color,
    selected: Boolean,
    testTag: String,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.4f else 1f,
        animationSpec = tween(120),
        label = "dot_scale"
    )

    LaunchedEffect(focused) {
        if (focused) onFocused()
    }

    Box(
        modifier = Modifier
            .size(16.dp)
            .testTag(testTag)
            .scale(scale)
            .alpha(if (selected) 1f else 0.5f)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected || focused) 2.dp else 0.dp,
                color = Color.White,
                shape = CircleShape
            )
            .tvDialogClickable(
                interactionSource = interactionSource,
                onFocusStateChanged = { focused = it },
                onClick = onClick
            )
    )
}

@Composable
private fun AppTileCard(
    app: AppItem,
    theme: LauncherTheme,
    focusScale: Float = 1.07f,
    showOuterRing: Boolean,
    isMoving: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val bmp: ImageBitmap? = remember(app.pkg, app.isBanner) {
        drawableToImageBitmap(app.image)
    }

    LedFocusTile(
        modifier = modifier,
        theme = theme,
        cornerRadius = 14.dp,
        focusScale = focusScale,
        showOuterRing = showOuterRing,
        testTag = testTag,
        onFocused = onFocused,
        onClick = onClick,
        onLongClick = onLongClick
    ) { isFocused ->
        val imgAlpha by animateFloatAsState(
            targetValue = if (isFocused || isMoving) 1f else 0.65f,
            animationSpec = tween(180),
            label = "tile_alpha"
        )
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = app.label,
                contentScale = if (app.isBanner) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(imgAlpha)
                    .then(if (!app.isBanner) Modifier.padding(horizontal = 18.dp, vertical = 10.dp) else Modifier)
            )
        } else {
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp)
            )
        }
        if (isMoving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, Color(0xFFFFD60A), RoundedCornerShape(14.dp))
            )
        }
    }
}

private fun drawableToImageBitmap(drawable: Drawable?): ImageBitmap? {
    if (drawable == null) return null
    return try {
        val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 192
        val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 108
        drawable.toBitmap(w.coerceAtMost(384), h.coerceAtMost(384), Bitmap.Config.ARGB_8888).asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}
