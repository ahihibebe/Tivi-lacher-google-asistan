package vn.launcher.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.launcher.tv.data.AdminData
import vn.launcher.tv.model.ActiveDialog
import vn.launcher.tv.model.ClimateDataSource
import vn.launcher.tv.model.ClockFontStyle
import vn.launcher.tv.model.LauncherTheme
import vn.launcher.tv.model.LauncherUiState
import vn.launcher.tv.ui.components.MacActionButton
import vn.launcher.tv.ui.components.MacRow
import vn.launcher.tv.ui.components.MacSectionHeader
import vn.launcher.tv.ui.components.MacToggleRow
import vn.launcher.tv.ui.components.MacWindowDialog
import vn.launcher.tv.ui.theme.LauncherColors
import vn.launcher.tv.viewmodel.LauncherViewModel

@Composable
internal fun DisplayMenuDialog(state: LauncherUiState, vm: LauncherViewModel) {
    val en = state.isEnglish
    val d = state.display
    val palette = LauncherColors.SettingsPalette

    MacWindowDialog(title = if (en) "Display" else "Hiển thị", theme = state.theme, maxWidth = 560.dp, onDismiss = vm::closeDialog) {
        MacSectionHeader(if (en) "Home screen frames" else "Khung trên màn hình chính")
        MacToggleRow(if (en) "TV channel frame" else "Khung kênh TV", d.tvOn, state.theme, en, onFocused = vm::playMove) {
            vm.toggleDisplayPref("tvOn")
        }
        MacToggleRow(if (en) "Music player (inside Settings)" else "Khung phát nhạc (trong Cài đặt)", d.musicFrameOn, state.theme, en, onFocused = vm::playMove) {
            vm.toggleDisplayPref("musicFrameOn")
        }
        MacToggleRow(if (en) "Photo slideshow" else "Khung trình chiếu ảnh", d.photoFrameOn, state.theme, en, onFocused = vm::playMove) {
            vm.toggleDisplayPref("photoFrameOn")
        }

        MacSectionHeader(if (en) "Information shown" else "Thông tin hiển thị")
        MacToggleRow(if (en) "Weather" else "Thời tiết", d.weatherOn, state.theme, en, onFocused = vm::playMove) {
            vm.toggleDisplayPref("weatherOn")
        }
        MacToggleRow(
            title = if (en) "Temperature & Humidity" else "Nhiệt độ & Độ ẩm",
            checked = d.humidityOn,
            theme = state.theme,
            isEnglish = en,
            testTag = "toggle_humidity_widget",
            onFocused = vm::playMove
        ) {
            vm.toggleDisplayPref("humidityOn")
        }
        MacRow(
            title = if (en) "Temp & humidity source" else "Nguồn nhiệt độ & độ ẩm",
            theme = state.theme,
            icon = when (d.climateSource) {
                ClimateDataSource.SENSOR -> Icons.Filled.Sensors
                ClimateDataSource.WEB -> Icons.Filled.Cloud
                ClimateDataSource.AUTO -> Icons.Filled.DeviceThermostat
            },
            iconTintBg = palette[4],
            trailing = climateSourceShortLabel(d.climateSource, en),
            showChevron = true,
            testTag = "display_climate_source_row",
            onFocused = vm::playMove,
            onClick = { vm.openDialog(ActiveDialog.ClimateSourceMenu) }
        )
        MacRow(
            title = if (en) "Province / City" else "Tỉnh / Thành phố",
            theme = state.theme,
            icon = Icons.Filled.Place,
            iconTintBg = palette[2],
            trailing = state.wxProvinceLabel,
            showChevron = true,
            onFocused = vm::playMove,
            onClick = { vm.openDialog(ActiveDialog.ChooseProvince) }
        )
        MacRow(
            title = if (en) "Ward / Commune" else "Phường / Xã",
            theme = state.theme,
            icon = Icons.Filled.Place,
            iconTintBg = palette[3],
            trailing = state.wxWardLabel,
            showChevron = true,
            onFocused = vm::playMove,
            onClick = {
                vm.playSelect()
                vm.openWardChooser()
            }
        )
        MacRow(
            title = if (en) "Update weather location" else "Cập nhật vị trí thời tiết",
            theme = state.theme,
            icon = Icons.Filled.Refresh,
            iconTintBg = palette[1],
            onFocused = vm::playMove,
            onClick = {
                vm.playSelect()
                vm.updateWeatherLocation()
            }
        )
        MacToggleRow(if (en) "Network (Wi-Fi name / LAN)" else "Mạng (tên Wi-Fi / LAN)", d.wifiOn, state.theme, en, onFocused = vm::playMove) {
            vm.toggleDisplayPref("wifiOn")
        }
        MacToggleRow(if (en) "Voice search" else "Tìm kiếm giọng nói", d.voiceOn, state.theme, en, onFocused = vm::playMove) {
            vm.toggleDisplayPref("voiceOn")
        }
        MacToggleRow(if (en) "Now playing (YouTube, SmartTube…)" else "Bài hát/video đang phát (YouTube, SmartTube…)", d.musicOn, state.theme, en, onFocused = vm::playMove) {
            vm.toggleDisplayPref("musicOn")
        }
        MacToggleRow(if (en) "Memory & RAM" else "Bộ nhớ & RAM", d.barsOn, state.theme, en, onFocused = vm::playMove) {
            vm.toggleDisplayPref("barsOn")
        }
        MacToggleRow(if (en) "Holiday countdown" else "Đếm ngược ngày lễ", d.holidayOn, state.theme, en, onFocused = vm::playMove) {
            vm.toggleDisplayPref("holidayOn")
        }
        MacToggleRow(if (en) "Lunar alerts (1st, 14th, 15th, last day)" else "Nhắc mùng 1, 14, 15, cuối tháng âm", d.lunarAlertOn, state.theme, en, onFocused = vm::playMove) {
            vm.toggleDisplayPref("lunarAlertOn")
        }
        MacToggleRow(if (en) "Show name in greeting" else "Hiện tên trong lời chào", d.nameOn, state.theme, en, onFocused = vm::playMove) {
            vm.toggleDisplayPref("nameOn")
        }

        MacSectionHeader(if (en) "Clock widget & Wallpaper" else "Widget giờ & Hình nền")
        MacRow(
            title = if (en) "Clock widget style (font, bold, dashed)" else "Kiểu chữ widget giờ (font, đậm, nét đứt)",
            theme = state.theme,
            icon = Icons.Filled.AccessTime,
            iconTintBg = palette[5],
            trailing = clockFontShortLabel(d.clockFont, en),
            showChevron = true,
            testTag = "display_clock_style_row",
            onFocused = vm::playMove,
            onClick = { vm.openDialog(ActiveDialog.ClockStyleMenu) }
        )
        MacRow(
            title = if (en) "Wallpaper opacity…" else "Độ trong suốt hình nền…",
            theme = state.theme,
            icon = Icons.Filled.Image,
            iconTintBg = palette[2],
            trailing = "${state.bgAlphaPercent}%",
            showChevron = true,
            onFocused = vm::playMove,
            onClick = { vm.openDialog(ActiveDialog.WallpaperOpacity) }
        )
    }
}

internal fun clockFontShortLabel(style: ClockFontStyle, en: Boolean): String = when (style) {
    ClockFontStyle.DEFAULT -> if (en) "Default" else "Mặc định"
    ClockFontStyle.OUTFIT -> "Outfit"
    ClockFontStyle.ORBITRON -> "Orbitron"
    ClockFontStyle.JETBRAINS_MONO -> "Mono"
    ClockFontStyle.PLAYFAIR -> "Playfair"
}

internal fun climateSourceShortLabel(source: ClimateDataSource, en: Boolean): String = when (source) {
    ClimateDataSource.AUTO -> if (en) "Auto" else "Tự động"
    ClimateDataSource.SENSOR -> if (en) "Sensor" else "Cảm biến"
    ClimateDataSource.WEB -> if (en) "Web" else "Dữ liệu web"
}

@Composable
internal fun ClimateSourceMenuDialog(state: LauncherUiState, vm: LauncherViewModel) {
    val en = state.isEnglish
    val current = state.display.climateSource
    val dc = state.deviceClimate
    val palette = LauncherColors.SettingsPalette

    MacWindowDialog(
        title = if (en) "Temp & humidity source" else "Nguồn nhiệt độ & độ ẩm",
        theme = state.theme,
        maxWidth = 500.dp,
        onDismiss = { vm.openDialog(ActiveDialog.DisplayMenu) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x1EFFFFFF))
                .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Filled.DeviceThermostat,
                    contentDescription = null,
                    tint = Color(0xFFFF9F0A),
                    modifier = Modifier.size(18.dp)
                )
                Icon(
                    imageVector = Icons.Filled.WaterDrop,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    val sensorDetectedText = when {
                        dc.hasTempSensor && dc.hasHumiditySensor -> {
                            if (en) "Temperature & humidity sensors detected" else "Đã phát hiện cảm biến nhiệt độ & độ ẩm"
                        }
                        dc.hasTempSensor -> {
                            if (en) "Temperature sensor detected (no humidity sensor)" else "Có cảm biến nhiệt độ (chưa có cảm biến độ ẩm)"
                        }
                        dc.hasHumiditySensor -> {
                            if (en) "Humidity sensor detected (no temperature sensor)" else "Có cảm biến độ ẩm (chưa có cảm biến nhiệt độ)"
                        }
                        else -> {
                            if (en) "No ambient temp/humidity sensor found on device" else "Thiết bị chưa có cảm biến nhiệt độ/độ ẩm"
                        }
                    }
                    Text(
                        text = sensorDetectedText,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (dc.sensorDiagnostic.isNotEmpty()) {
                        Text(
                            text = dc.sensorDiagnostic,
                            color = Color(0xAAFFFFFF),
                            fontSize = 10.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        val options = listOf(
            Triple(
                ClimateDataSource.AUTO,
                if (en) "Auto (prioritize device sensor)" else "Tự động (ưu tiên cảm biến)",
                Icons.Filled.DeviceThermostat
            ),
            Triple(
                ClimateDataSource.SENSOR,
                if (en) "Device sensor only" else "Chỉ dùng cảm biến thiết bị",
                Icons.Filled.Sensors
            ),
            Triple(
                ClimateDataSource.WEB,
                if (en) "Web data (Open-Meteo)" else "Dữ liệu web (Open-Meteo)",
                Icons.Filled.Cloud
            )
        )
        options.forEachIndexed { idx, (src, label, icon) ->
            MacRow(
                title = label,
                theme = state.theme,
                icon = icon,
                iconTintBg = palette[(idx + 3) % palette.size],
                trailing = if (current == src) "✓" else null,
                testTag = "climate_source_${src.id}",
                onFocused = vm::playMove,
                onClick = { vm.setClimateSource(src) }
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            MacActionButton(
                text = if (en) "Rescan sensors" else "Quét lại cảm biến",
                color = Color(0x38FFFFFF),
                onClick = vm::rescanClimateSensors
            )
            MacActionButton(
                text = if (en) "Back" else "Quay lại",
                color = LauncherColors.accent(state.theme),
                onClick = { vm.openDialog(ActiveDialog.DisplayMenu) }
            )
        }
    }
}

@Composable
internal fun ClockStyleMenuDialog(state: LauncherUiState, vm: LauncherViewModel) {
    val en = state.isEnglish
    val d = state.display
    val palette = LauncherColors.SettingsPalette

    val fontOptions = listOf(
        ClockFontStyle.DEFAULT to (if (en) "Default (System Sans)" else "Mặc định (Hệ thống)"),
        ClockFontStyle.OUTFIT to (if (en) "Modern Rounded (Outfit)" else "Hiện đại (Outfit)"),
        ClockFontStyle.ORBITRON to (if (en) "Digital LED (Orbitron)" else "Kỹ thuật số (Orbitron)"),
        ClockFontStyle.JETBRAINS_MONO to (if (en) "Monospace (JetBrains Mono)" else "Đơn cách (JetBrains Mono)"),
        ClockFontStyle.PLAYFAIR to (if (en) "Classic Serif (Playfair)" else "Cổ điển (Playfair)")
    )

    MacWindowDialog(
        title = if (en) "Clock widget style" else "Kiểu chữ widget giờ",
        theme = state.theme,
        maxWidth = 540.dp,
        onDismiss = vm::closeDialog
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0x22FFFFFF))
                .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(16.dp))
                .padding(vertical = 12.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ClockTimeText(
                    timeText = state.header.timeText.ifEmpty { "19:33" },
                    fontStyle = d.clockFont,
                    bold = d.clockBold,
                    dashed = d.clockDashed,
                    fontSize = 48.sp
                )
                if (state.header.dateText.isNotEmpty()) {
                    Text(
                        text = state.header.dateText,
                        color = Color(0xCCFFFFFF),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        MacSectionHeader(if (en) "Digit style" else "Kiểu nét chữ")
        MacToggleRow(
            title = if (en) "Bold digits" else "Chữ đậm",
            checked = d.clockBold,
            theme = state.theme,
            isEnglish = en,
            testTag = "clock_bold_toggle",
            onFocused = vm::playMove,
            onToggle = { vm.toggleClockBold() }
        )
        MacToggleRow(
            title = if (en) "Dashed outline" else "Nét đứt",
            checked = d.clockDashed,
            theme = state.theme,
            isEnglish = en,
            testTag = "clock_dashed_toggle",
            onFocused = vm::playMove,
            onToggle = { vm.toggleClockDashed() }
        )

        MacSectionHeader(if (en) "Font family" else "Kiểu font")
        fontOptions.forEachIndexed { idx, (style, label) ->
            MacRow(
                title = label,
                theme = state.theme,
                icon = Icons.Filled.AccessTime,
                iconTintBg = palette[idx % palette.size],
                trailing = if (d.clockFont == style) "✓" else null,
                testTag = "clock_font_${style.id}",
                onFocused = vm::playMove,
                onClick = {
                    vm.playSelect()
                    vm.setClockFont(style)
                }
            )
        }
    }
}

@Composable
internal fun ChooseProvinceDialog(state: LauncherUiState, vm: LauncherViewModel) {
    val en = state.isEnglish
    MacWindowDialog(
        title = if (en) "Province / City" else "Tỉnh / Thành phố",
        theme = state.theme,
        maxWidth = 520.dp,
        onDismiss = { vm.openDialog(ActiveDialog.DisplayMenu) }
    ) {
        MacRow(
            title = if (en) "Automatic (current location)" else "Tự động (theo vị trí hiện tại)",
            theme = state.theme,
            icon = Icons.Filled.Place,
            iconTintBg = LauncherColors.SettingsPalette[0],
            onFocused = vm::playMove,
            onClick = {
                vm.playSelect()
                vm.selectAutoWeatherLocation()
            }
        )
        AdminData.PROVINCES.forEachIndexed { idx, p ->
            MacRow(
                title = p.name,
                theme = state.theme,
                icon = Icons.Filled.Place,
                iconTintBg = LauncherColors.SettingsPalette[(idx + 1) % LauncherColors.SettingsPalette.size],
                onFocused = vm::playMove,
                onClick = {
                    vm.playSelect()
                    vm.selectProvince(p)
                }
            )
        }
    }
}

@Composable
internal fun ChooseWardDialog(
    dialog: ActiveDialog.ChooseWard,
    theme: LauncherTheme,
    en: Boolean,
    vm: LauncherViewModel
) {
    MacWindowDialog(
        title = dialog.province.shortName(),
        theme = theme,
        maxWidth = 520.dp,
        onDismiss = { vm.openDialog(ActiveDialog.DisplayMenu) }
    ) {
        when {
            dialog.loading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = LauncherColors.accent(theme), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(if (en) "Loading…" else "Đang tải…", color = Color.White, fontSize = 14.sp)
                }
            }
            dialog.error || dialog.wards == null -> {
                Text(
                    text = if (en) "Could not load wards list." else "Không tải được danh sách phường/xã.",
                    color = Color(0xCCFFFFFF),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(10.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    MacActionButton(
                        text = if (en) "Enter manually" else "Nhập tay",
                        color = LauncherColors.accent(theme),
                        onClick = { vm.openDialog(ActiveDialog.ManualWardInput(dialog.province)) }
                    )
                }
            }
            else -> {
                MacRow(
                    title = if (en) "Whole province/city" else "Cả tỉnh/thành phố",
                    theme = theme,
                    icon = Icons.Filled.Place,
                    iconTintBg = LauncherColors.SettingsPalette[0],
                    onFocused = vm::playMove,
                    onClick = {
                        vm.playSelect()
                        vm.selectWholeProvinceWard(dialog.province)
                    }
                )
                dialog.wards.forEachIndexed { idx, w ->
                    MacRow(
                        title = w.name,
                        theme = theme,
                        icon = Icons.Filled.Place,
                        iconTintBg = LauncherColors.SettingsPalette[(idx + 1) % LauncherColors.SettingsPalette.size],
                        onFocused = vm::playMove,
                        onClick = {
                            vm.playSelect()
                            vm.selectWard(dialog.province, w.name, reopenDisplay = true)
                        }
                    )
                }
            }
        }
    }
}
