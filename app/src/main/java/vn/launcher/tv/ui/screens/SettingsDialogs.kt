package vn.launcher.tv.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.launcher.tv.R
import vn.launcher.tv.model.ActiveDialog
import vn.launcher.tv.model.LauncherTheme
import vn.launcher.tv.model.LauncherUiState
import vn.launcher.tv.ui.components.MacActionButton
import vn.launcher.tv.ui.components.MacEditDialog
import vn.launcher.tv.ui.components.MacRow
import vn.launcher.tv.ui.components.MacSectionHeader
import vn.launcher.tv.ui.components.MacToggleRow
import vn.launcher.tv.ui.components.MacWindowDialog
import vn.launcher.tv.ui.theme.LauncherColors
import vn.launcher.tv.viewmodel.LauncherViewModel
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ActiveDialogHost(
    state: LauncherUiState,
    vm: LauncherViewModel,
    onPickDeviceImage: () -> Unit
) {
    val dialog = state.activeDialog ?: return
    val en = state.isEnglish

    when (dialog) {
        ActiveDialog.SettingsMain -> SettingsMainDialog(state, vm)
        ActiveDialog.ThemeMenu -> ThemeMenuDialog(state, vm)
        ActiveDialog.WallpaperMenu -> WallpaperMenuDialog(state, vm, onPickDeviceImage)
        ActiveDialog.WallpaperOpacity -> WallpaperOpacityDialog(state, vm)
        is ActiveDialog.QrUpload -> QrUploadDialog(
            title = if (en) "Choose a photo from your phone" else "Chọn ảnh từ điện thoại",
            hint = if (en) {
                "Scan with your phone (same Wi-Fi as the TV), pick a photo and tap Send."
            } else {
                "Quét mã bằng điện thoại (cùng mạng Wi-Fi với TV), chọn ảnh rồi bấm Gửi."
            },
            url = dialog.url,
            qrBitmap = dialog.qrBitmap,
            theme = state.theme,
            en = en,
            onDismiss = vm::closeDialog
        )
        is ActiveDialog.QrApiKeyUpload -> QrApiKeyUploadDialog(
            dialog = dialog,
            theme = state.theme,
            en = en,
            onBack = { vm.openPersonalAiKeyConfig(dialog.returnQuery) },
            onDismiss = vm::closeDialog
        )
        is ActiveDialog.LocalWallpapers -> LocalWallpapersDialog(dialog, state.theme, en, vm)
        ActiveDialog.LanguageMenu -> LanguageMenuDialog(state, vm)
        ActiveDialog.EditUserName -> MacEditDialog(
            title = if (en) "Your name" else "Tên của bạn",
            message = if (en) "Shown in the greeting and the settings window." else "Tên hiện trong lời chào và khung cài đặt.",
            hints = listOf(if (en) "Name" else "Tên"),
            initialValues = listOf(state.userName),
            theme = state.theme,
            isEnglish = en,
            onSave = { vm.setUserName(it.firstOrNull().orEmpty()) },
            onDismiss = vm::closeDialog
        )
        ActiveDialog.AboutInfo -> AboutInfoDialog(state, vm)
        ActiveDialog.DisplayMenu -> DisplayMenuDialog(state, vm)
        ActiveDialog.ClimateSourceMenu -> ClimateSourceMenuDialog(state, vm)
        ActiveDialog.ClockStyleMenu -> ClockStyleMenuDialog(state, vm)
        ActiveDialog.ChooseProvince -> ChooseProvinceDialog(state, vm)
        is ActiveDialog.ChooseWard -> ChooseWardDialog(dialog, state.theme, en, vm)
        is ActiveDialog.ManualWardInput -> MacEditDialog(
            title = if (en) "Ward / Commune" else "Phường / Xã",
            message = if (en) "Could not load the list for ${dialog.province.name}. Enter a ward/commune name:"
            else "Không tải được danh sách của ${dialog.province.name}. Nhập tên phường/xã:",
            hints = listOf(if (en) "Example: Phường Bến Nghé" else "Ví dụ: Phường Bến Nghé"),
            initialValues = listOf(""),
            theme = state.theme,
            isEnglish = en,
            onSave = { vals ->
                val name = vals.firstOrNull().orEmpty()
                if (name.isNotEmpty()) vm.selectWard(dialog.province, name, reopenDisplay = true)
            },
            onDismiss = vm::closeDialog
        )
        else -> SecondaryDialogHost(dialog, state, vm)
    }
}

@Composable
private fun SettingsMainDialog(state: LauncherUiState, vm: LauncherViewModel) {
    val en = state.isEnglish
    val palette = LauncherColors.SettingsPalette

    MacWindowDialog(
        title = if (en) "Settings" else "Cài đặt",
        theme = state.theme,
        maxWidth = 540.dp,
        onDismiss = vm::closeDialog
    ) {
        ProfileHeaderRow(state, onMoveSound = vm::playMove) {
            vm.openDialog(ActiveDialog.EditUserName)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickTile(
                icon = Icons.AutoMirrored.Filled.Input,
                color = Color(0xFF0A84FF),
                title = if (en) "Input" else "Nguồn vào",
                subtitle = if (en) "Choose input" else "Chọn nguồn vào",
                theme = state.theme,
                modifier = Modifier.weight(1f),
                onFocused = vm::playMove,
                onClick = {
                    vm.playSelect()
                    vm.closeDialog()
                    vm.openInputs()
                }
            )
            QuickTile(
                icon = Icons.Filled.Bluetooth,
                color = Color(0xFF0082FC),
                title = "Bluetooth",
                subtitle = state.bluetoothStatus,
                theme = state.theme,
                modifier = Modifier.weight(1f),
                onFocused = vm::playMove,
                onClick = {
                    vm.playSelect()
                    vm.closeDialog()
                    vm.openBluetooth()
                }
            )
            QuickTile(
                icon = Icons.Filled.Wifi,
                color = Color(0xFF34C759),
                title = if (en) "Network" else "Mạng",
                subtitle = state.networkPill.label,
                theme = state.theme,
                modifier = Modifier.weight(1f),
                onFocused = vm::playMove,
                onClick = {
                    vm.playSelect()
                    vm.closeDialog()
                    vm.openNetworkSettings()
                }
            )
            QuickTile(
                icon = Icons.Filled.PowerSettingsNew,
                color = Color(0xFFFF453A),
                title = if (en) "Turn off" else "Tắt TV",
                subtitle = if (en) "Power off TV" else "Tắt hẳn TV",
                theme = state.theme,
                modifier = Modifier.weight(1f),
                onFocused = vm::playMove,
                onClick = { vm.openDialog(ActiveDialog.ConfirmPowerOff) }
            )
        }

        if (state.display.musicFrameOn) {
            InlineMusicSettingsCard(state, vm)
        }

        VolumeSliderRow(state, vm)

        MacSectionHeader(if (en) "General" else "Chung")
        MacRow(
            title = if (en) "Theme" else "Giao diện",
            theme = state.theme,
            icon = Icons.Filled.Palette,
            iconTintBg = palette[6],
            showChevron = true,
            onFocused = vm::playMove,
            onClick = { vm.openDialog(ActiveDialog.ThemeMenu) }
        )
        MacRow(
            title = if (en) "Wallpaper" else "Hình nền",
            theme = state.theme,
            icon = Icons.Filled.Image,
            iconTintBg = palette[2],
            showChevron = true,
            onFocused = vm::playMove,
            onClick = { vm.openDialog(ActiveDialog.WallpaperMenu) }
        )
        MacRow(
            title = if (en) "Clock widget style" else "Kiểu chữ widget giờ",
            theme = state.theme,
            icon = Icons.Filled.AccessTime,
            iconTintBg = palette[5],
            trailing = clockFontShortLabel(state.display.clockFont, en),
            showChevron = true,
            testTag = "settings_clock_style_row",
            onFocused = vm::playMove,
            onClick = { vm.openDialog(ActiveDialog.ClockStyleMenu) }
        )
        MacRow(
            title = if (en) "Language" else "Ngôn ngữ",
            theme = state.theme,
            icon = Icons.Filled.Language,
            iconTintBg = palette[1],
            showChevron = true,
            onFocused = vm::playMove,
            onClick = { vm.openDialog(ActiveDialog.LanguageMenu) }
        )
        MacToggleRow(
            title = if (en) "Navigation sound" else "Âm thanh di chuyển",
            checked = state.display.soundOn,
            theme = state.theme,
            isEnglish = en,
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            iconTintBg = palette[8],
            onFocused = vm::playMove,
            onToggle = { vm.toggleDisplayPref("sound") }
        )
        MacRow(
            title = stringResource(R.string.info_title),
            theme = state.theme,
            icon = Icons.Filled.Info,
            iconTintBg = palette[9],
            showChevron = true,
            onFocused = vm::playMove,
            onClick = { vm.openDialog(ActiveDialog.AboutInfo) }
        )

        MacSectionHeader(if (en) "Frames & keys" else "Khung & phím")
        MacRow(
            title = if (en) "Channels in the TV frame" else "Kênh trong khung TV",
            theme = state.theme,
            icon = Icons.Filled.LiveTv,
            iconTintBg = palette[5],
            showChevron = true,
            onFocused = vm::playMove,
            onClick = { vm.openDialog(ActiveDialog.TvFrameMenu) }
        )
        MacRow(
            title = if (en) "Voice channels" else "Kênh gọi bằng giọng nói",
            theme = state.theme,
            icon = Icons.Filled.LiveTv,
            iconTintBg = palette[7],
            showChevron = true,
            onFocused = vm::playMove,
            onClick = { vm.openDialog(ActiveDialog.VoiceChannelsEdit) }
        )
        MacRow(
            title = if (en) "Remote key shortcuts (apps & voice)" else "Gán phím điều khiển (ứng dụng & trợ lý)",
            theme = state.theme,
            icon = Icons.Filled.SettingsRemote,
            iconTintBg = palette[6],
            showChevron = true,
            onFocused = vm::playMove,
            onClick = { vm.openDialog(ActiveDialog.KeyMapMenu) }
        )
        MacRow(
            title = if (en) "FPT Play: TV section link" else "FPT Play: liên kết Truyền hình",
            theme = state.theme,
            icon = Icons.Filled.Link,
            iconTintBg = palette[4],
            showChevron = true,
            onFocused = vm::playMove,
            onClick = { vm.openDialog(ActiveDialog.FptLinkEdit) }
        )

        MacSectionHeader(if (en) "More" else "Khác")
        MacRow(
            title = if (en) "Google AI · Personal API Key" else "Trợ lý AI · API Key cá nhân",
            theme = state.theme,
            icon = Icons.Filled.AutoAwesome,
            iconTintBg = palette[2],
            trailing = if (state.hasPersonalAiKey) state.maskedPersonalAiKey else (if (en) "Not set" else "Chưa nhập"),
            showChevron = true,
            testTag = "settings_ai_key_row",
            onFocused = vm::playMove,
            onClick = { vm.openPersonalAiKeyConfig(null) }
        )
        MacRow(
            title = if (en) "Prices (gold, coffee, pepper, fuel)" else "Bảng giá (vàng, cà phê, tiêu, xăng dầu)",
            theme = state.theme,
            icon = Icons.Filled.Payments,
            iconTintBg = palette[4],
            showChevron = true,
            onFocused = vm::playMove,
            onClick = { vm.openDialog(ActiveDialog.PriceMenu) }
        )
        MacRow(
            title = if (en) "Display" else "Hiển thị",
            theme = state.theme,
            icon = Icons.Filled.Tune,
            iconTintBg = palette[3],
            showChevron = true,
            onFocused = vm::playMove,
            onClick = { vm.openDialog(ActiveDialog.DisplayMenu) }
        )
        MacRow(
            title = if (en) "Music & photos" else "Nhạc & ảnh",
            theme = state.theme,
            icon = Icons.Filled.MusicNote,
            iconTintBg = palette[8],
            showChevron = true,
            onFocused = vm::playMove,
            onClick = { vm.openDialog(ActiveDialog.MediaSettings) }
        )
        MacRow(
            title = if (en) "Set as default launcher" else "Đặt launcher mặc định",
            theme = state.theme,
            icon = Icons.Filled.Home,
            iconTintBg = palette[1],
            showChevron = true,
            onFocused = vm::playMove,
            onClick = { vm.openDialog(ActiveDialog.DefaultLauncherMenu) }
        )
        MacRow(
            title = if (en) "System settings" else "Cài đặt hệ thống",
            theme = state.theme,
            icon = Icons.Filled.Tv,
            iconTintBg = palette[0],
            showChevron = true,
            onFocused = vm::playMove,
            onClick = {
                vm.playSelect()
                vm.closeDialog()
                vm.openSystemSettings()
            }
        )
        MacRow(
            title = if (en) "Hidden apps" else "Ứng dụng đã ẩn",
            theme = state.theme,
            icon = Icons.Filled.Apps,
            iconTintBg = palette[7],
            showChevron = true,
            onFocused = vm::playMove,
            onClick = { vm.openDialog(ActiveDialog.HiddenAppsMenu) }
        )
    }
}

@Composable
private fun ProfileHeaderRow(
    state: LauncherUiState,
    onMoveSound: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val en = state.isEnglish
    var focused by remember { mutableStateOf(false) }

    val thumbBmp = remember(state.theme, state.bgSpec, state.customWallpaperVersion) {
        when {
            state.bgSpec == "theme" && state.theme != LauncherTheme.MAC_OS -> {
                val res = if (state.theme == LauncherTheme.TULIP_BLUE) R.drawable.bg_tulip_blue else R.drawable.bg_tulip_purple
                BitmapFactory.decodeResource(context.resources, res)?.asImageBitmap()
            }
            state.bgSpec == "custom" -> {
                val f = File(context.filesDir, "wallpaper.jpg")
                if (f.exists()) BitmapFactory.decodeFile(f.path)?.asImageBitmap() else null
            }
            else -> null
        }
    }

    LaunchedEffect(focused) { if (focused) onMoveSound() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) LauncherColors.accent(state.theme) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF060F2E))
                .border(1.5.dp, Color(0x4DFFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (thumbBmp != null) {
                Image(
                    bitmap = thumbBmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_vietlauncher_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = state.userName.trim().ifEmpty { if (en) "Set your name" else "Đặt tên của bạn" },
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${stringResource(R.string.app_name)} · ${state.appVersion}",
                color = Color(0xB3FFFFFF),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun QuickTile(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String,
    theme: LauncherTheme,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    LaunchedEffect(focused) { if (focused) onFocused() }

    Column(
        modifier = modifier
            .clip(shape)
            .background(if (focused) LauncherColors.accent(theme) else Color(0x1EFFFFFF), shape)
            .border(1.dp, if (focused) Color.White else Color(0x22FFFFFF), shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            color = Color(0xB3FFFFFF),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InlineMusicSettingsCard(state: LauncherUiState, vm: LauncherViewModel) {
    val en = state.isEnglish
    val m = state.music
    val track = m.tracks.getOrNull(m.currentIndex)
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(if (focused) LauncherColors.accent(state.theme) else Color(0x1EFFFFFF), shape)
            .border(1.dp, if (focused) Color.White else Color(0x22FFFFFF), shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable {
                vm.playSelect()
                vm.openMusicPanel()
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x26FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            if (m.coverArt != null) {
                Image(
                    bitmap = m.coverArt.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track?.title ?: (if (en) "Local music" else "Nhạc trên máy"),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when {
                    track != null && track.artist.isNotEmpty() -> track.artist
                    m.tracksLoaded -> if (en) "${m.tracks.size} songs" else "${m.tracks.size} bài hát"
                    else -> if (en) "Tap to open player" else "Bấm để mở trình phát"
                },
                color = Color(0xB3FFFFFF),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PlayerControlIcon(Icons.Filled.SkipPrevious, "Prev", state.theme, onClick = vm::prevTrack)
            PlayerControlIcon(
                if (m.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                "Play/Pause",
                state.theme,
                onClick = vm::toggleMusicPlay
            )
            PlayerControlIcon(Icons.Filled.SkipNext, "Next", state.theme, onClick = vm::nextTrack)
        }
    }
}

@Composable
private fun VolumeSliderRow(state: LauncherUiState, vm: LauncherViewModel) {
    val max = state.volumeMax.coerceAtLeast(1)
    val level = state.volumeLevel.coerceIn(0, max)
    val fraction = (level.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    val pct = (fraction * 100f).roundToInt().coerceIn(0, 100)
    val shape = RoundedCornerShape(14.dp)
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(focused) { if (focused) vm.playMove() }

    val volIcon = when {
        level == 0 -> Icons.AutoMirrored.Filled.VolumeOff
        pct < 50 -> Icons.AutoMirrored.Filled.VolumeDown
        else -> Icons.AutoMirrored.Filled.VolumeUp
    }
    val accent = LauncherColors.accent(state.theme)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(if (focused) accent.copy(alpha = 0.28f) else Color(0x1EFFFFFF), shape)
            .border(if (focused) 1.5.dp else 1.dp, if (focused) Color.White else Color(0x22FFFFFF), shape)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft -> {
                        vm.adjustVolume(-1)
                        true
                    }
                    Key.DirectionRight -> {
                        vm.adjustVolume(1)
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(volIcon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = "◀",
            color = if (focused && level > 0) Color.White else Color(0x66FFFFFF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .height(22.dp)
                .pointerInput(max) {
                    detectTapGestures { offset ->
                        val w = size.width.coerceAtLeast(1)
                        val ratio = (offset.x / w.toFloat()).coerceIn(0f, 1f)
                        vm.setVolume((ratio * max).roundToInt())
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Color(0x44FFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(if (focused) Color.White else accent)
                )
            }
        }
        Text(
            text = "▶",
            color = if (focused && level < max) Color.White else Color(0x66FFFFFF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "$pct%",
            color = if (focused) Color.White else Color(0xCCFFFFFF),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(42.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun LanguageMenuDialog(state: LauncherUiState, vm: LauncherViewModel) {
    val en = state.isEnglish
    MacWindowDialog(title = if (en) "Language" else "Ngôn ngữ", theme = state.theme, maxWidth = 440.dp, onDismiss = vm::closeDialog) {
        MacRow(
            title = "Tiếng Việt",
            theme = state.theme,
            icon = Icons.Filled.Language,
            iconTintBg = LauncherColors.SettingsPalette[1],
            trailing = if (!en) "✓" else null,
            onFocused = vm::playMove,
            onClick = {
                vm.playSelect()
                vm.setLanguage("vi")
            }
        )
        MacRow(
            title = "English",
            theme = state.theme,
            icon = Icons.Filled.Language,
            iconTintBg = LauncherColors.SettingsPalette[2],
            trailing = if (en) "✓" else null,
            onFocused = vm::playMove,
            onClick = {
                vm.playSelect()
                vm.setLanguage("en")
            }
        )
    }
}

@Composable
private fun AboutInfoDialog(state: LauncherUiState, vm: LauncherViewModel) {
    val context = LocalContext.current
    val en = state.isEnglish
    val url = stringResource(R.string.info_url)
    MacWindowDialog(
        title = stringResource(R.string.info_title),
        theme = state.theme,
        maxWidth = 500.dp,
        onDismiss = vm::closeDialog
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .border(1.5.dp, Color(0x55FFFFFF), RoundedCornerShape(14.dp))
                    .padding(5.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.img_donate_qr),
                    contentDescription = "QR Donate MB: 220011026789",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "MB: 220011026789",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.info_description),
                color = Color(0xE6FFFFFF),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${stringResource(R.string.app_name)} · ${state.appVersion}",
                color = Color(0x99FFFFFF),
                fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
            MacRow(
                title = "Facebook",
                theme = state.theme,
                icon = Icons.Filled.Link,
                iconTintBg = Color(0xFF1877F2),
                showChevron = true,
                onFocused = vm::playMove,
                onClick = {
                    vm.playSelect()
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: Exception) {
                        Toast.makeText(
                            context,
                            if (en) "No browser on this TV. Visit: $url" else "TV chưa có trình duyệt. Hãy truy cập: $url",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
            Text(url, color = Color(0x99FFFFFF), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                MacActionButton(
                    text = if (en) "Close" else "Đóng",
                    color = LauncherColors.accent(state.theme),
                    onClick = vm::closeDialog
                )
            }
        }
    }
}
