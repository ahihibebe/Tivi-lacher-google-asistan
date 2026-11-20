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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Apps
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
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.launcher.tv.R
import vn.launcher.tv.data.AdminData
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
        is ActiveDialog.QrUpload -> QrUploadDialog(dialog, state.theme, en, vm::closeDialog)
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
        // Profile Header
        ProfileHeaderRow(state, onMoveSound = vm::playMove) {
            vm.openDialog(ActiveDialog.EditUserName)
        }

        // 4 Quick Action Tiles
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

        // Inline Music Card
        if (state.display.musicFrameOn) {
            InlineMusicSettingsCard(state, vm)
        }

        // Volume Slider
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
                .background(Color(0xFF2C2C2E))
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
                    painter = painterResource(R.drawable.img_vietlauncher_icon_1794536180658),
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
        BoxWithConstraints(
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
private fun ThemeMenuDialog(state: LauncherUiState, vm: LauncherViewModel) {
    val en = state.isEnglish
    val items = listOf(
        Triple(LauncherTheme.MAC_OS, "macOS", Color(0xFF0A84FF)),
        Triple(LauncherTheme.TULIP_BLUE, if (en) "Blue tulip (Liquid Glass)" else "Tulip xanh (Kính lỏng)", Color(0xFF2AA7F0)),
        Triple(LauncherTheme.TULIP_PURPLE, if (en) "Purple tulip (Liquid Glass)" else "Tulip tím (Kính lỏng)", Color(0xFF9E6DF0))
    )
    MacWindowDialog(title = if (en) "Theme" else "Giao diện", theme = state.theme, maxWidth = 520.dp, onDismiss = vm::closeDialog) {
        items.forEach { (th, label, col) ->
            MacRow(
                title = label,
                theme = state.theme,
                icon = Icons.Filled.Palette,
                iconTintBg = col,
                trailing = if (state.theme == th) "✓" else null,
                onFocused = vm::playMove,
                onClick = {
                    vm.playSelect()
                    vm.setThemeChoice(th)
                }
            )
        }
    }
}

@Composable
private fun WallpaperMenuDialog(
    state: LauncherUiState,
    vm: LauncherViewModel,
    onPickDeviceImage: () -> Unit
) {
    val en = state.isEnglish
    val names = if (en) listOf("Red (default)", "Blue", "Purple", "Green", "Black")
    else listOf("Đỏ (mặc định)", "Xanh dương", "Tím", "Xanh lá", "Đen")
    val palette = LauncherColors.SettingsPalette

    MacWindowDialog(title = if (en) "Wallpaper" else "Hình nền", theme = state.theme, maxWidth = 520.dp, onDismiss = vm::closeDialog) {
        names.forEachIndexed { idx, name ->
            val col = if (LauncherColors.PresetGlows[idx] != Color.Transparent) {
                LauncherColors.PresetGlows[idx].copy(alpha = 1f)
            } else {
                Color(0xFF3A3A3C)
            }
            MacRow(
                title = name,
                theme = state.theme,
                icon = Icons.Filled.Image,
                iconTintBg = col,
                trailing = if (state.bgSpec == "preset:$idx") "✓" else null,
                onFocused = vm::playMove,
                onClick = {
                    vm.playSelect()
                    vm.selectPresetWallpaper(idx)
                }
            )
        }
        MacSectionHeader(if (en) "Your photos" else "Ảnh của bạn")
        MacRow(
            title = if (en) "Choose a photo from your phone (QR code)" else "Chọn ảnh từ điện thoại (quét mã QR)",
            theme = state.theme,
            icon = Icons.Filled.Image,
            iconTintBg = palette[4],
            showChevron = true,
            onFocused = vm::playMove,
            onClick = {
                vm.playSelect()
                vm.startQrUpload()
            }
        )
        MacRow(
            title = if (en) "Choose a photo from device…" else "Chọn ảnh từ thiết bị…",
            theme = state.theme,
            icon = Icons.Filled.Image,
            iconTintBg = palette[0],
            showChevron = true,
            onFocused = vm::playMove,
            onClick = {
                vm.playSelect()
                vm.closeDialog()
                onPickDeviceImage()
            }
        )
        MacRow(
            title = if (en) "Choose a photo from the app folder…" else "Chọn ảnh từ thư mục ứng dụng…",
            theme = state.theme,
            icon = Icons.Filled.Image,
            iconTintBg = palette[1],
            showChevron = true,
            onFocused = vm::playMove,
            onClick = {
                vm.playSelect()
                vm.openLocalWallpapers()
            }
        )
        MacRow(
            title = if (en) "Opacity…" else "Độ trong suốt…",
            theme = state.theme,
            icon = Icons.Filled.Tune,
            iconTintBg = palette[3],
            trailing = "${state.bgAlphaPercent}%",
            showChevron = true,
            onFocused = vm::playMove,
            onClick = { vm.openDialog(ActiveDialog.WallpaperOpacity) }
        )
    }
}

@Composable
private fun WallpaperOpacityDialog(state: LauncherUiState, vm: LauncherViewModel) {
    val en = state.isEnglish
    var valPct by remember(state.bgAlphaPercent) { mutableFloatStateOf(state.bgAlphaPercent.toFloat()) }

    MacWindowDialog(
        title = if (en) "Wallpaper opacity" else "Độ trong suốt hình nền",
        theme = state.theme,
        maxWidth = 500.dp,
        onDismiss = vm::closeDialog
    ) {
        Text(
            text = "${if (en) "Opacity" else "Độ rõ"}: ${valPct.roundToInt()}%",
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.padding(8.dp)
        )
        Slider(
            value = valPct,
            onValueChange = {
                valPct = it
                vm.setWallpaperOpacity(it.roundToInt())
            },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = LauncherColors.accent(state.theme),
                inactiveTrackColor = Color(0x44FFFFFF)
            ),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            MacActionButton(
                text = if (en) "Done" else "Xong",
                color = LauncherColors.accent(state.theme),
                onClick = vm::closeDialog
            )
        }
    }
}

@Composable
private fun QrUploadDialog(
    dialog: ActiveDialog.QrUpload,
    theme: LauncherTheme,
    en: Boolean,
    onDismiss: () -> Unit
) {
    MacWindowDialog(
        title = if (en) "Choose a photo from your phone" else "Chọn ảnh từ điện thoại",
        theme = theme,
        maxWidth = 460.dp,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (dialog.qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .padding(10.dp)
                ) {
                    Image(
                        bitmap = dialog.qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = if (en) {
                    "Scan with your phone (same Wi-Fi as the TV), pick a photo and tap Send."
                } else {
                    "Quét mã bằng điện thoại (cùng mạng Wi-Fi với TV), chọn ảnh rồi bấm Gửi."
                },
                color = Color(0xE6FFFFFF),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(text = dialog.url, color = Color(0x99FFFFFF), fontSize = 11.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            MacActionButton(
                text = if (en) "Close" else "Đóng",
                color = LauncherColors.accent(theme),
                onClick = onDismiss
            )
        }
    }
}

@Composable
private fun LocalWallpapersDialog(
    dialog: ActiveDialog.LocalWallpapers,
    theme: LauncherTheme,
    en: Boolean,
    vm: LauncherViewModel
) {
    MacWindowDialog(
        title = if (en) "Choose a wallpaper" else "Chọn ảnh nền",
        theme = theme,
        maxWidth = 540.dp,
        onDismiss = vm::closeDialog
    ) {
        if (dialog.files.isEmpty()) {
            Text(
                text = if (en) {
                    "Copy .jpg or .png photos into:\n\n${dialog.folderPath}\n\nwith ADB:\nadb push photo.jpg ${dialog.folderPath}/"
                } else {
                    "Chép ảnh .jpg hoặc .png vào thư mục:\n\n${dialog.folderPath}\n\nbằng lệnh ADB:\nadb push anh.jpg ${dialog.folderPath}/"
                },
                color = Color(0xE6FFFFFF),
                fontSize = 13.sp,
                modifier = Modifier.padding(10.dp)
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                MacActionButton(
                    text = if (en) "Close" else "Đóng",
                    color = LauncherColors.accent(theme),
                    onClick = vm::closeDialog
                )
            }
        } else {
            dialog.files.forEach { file ->
                MacRow(
                    title = file.name,
                    theme = theme,
                    icon = Icons.Filled.Image,
                    onFocused = vm::playMove,
                    onClick = {
                        vm.playSelect()
                        vm.applyCustomWallpaperFromFile(file)
                    }
                )
            }
        }
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
        maxWidth = 480.dp,
        onDismiss = vm::closeDialog
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.img_vietlauncher_icon_1794536180658),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(22.dp))
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.app_name), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "${if (en) "Version " else "Phiên bản "}${state.appVersion}",
                color = Color(0xB3FFFFFF),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.info_description),
                color = Color(0xCCFFFFFF),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
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
            Text(url, color = Color(0x99FFFFFF), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(12.dp))
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

@Composable
private fun DisplayMenuDialog(state: LauncherUiState, vm: LauncherViewModel) {
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
        MacToggleRow(if (en) "Storage & RAM" else "Dung lượng & RAM", d.barsOn, state.theme, en, onFocused = vm::playMove) {
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

        MacSectionHeader(if (en) "Wallpaper" else "Hình nền")
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

@Composable
private fun ChooseProvinceDialog(state: LauncherUiState, vm: LauncherViewModel) {
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
private fun ChooseWardDialog(
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
