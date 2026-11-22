package vn.launcher.tv.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.launcher.tv.model.ActiveDialog
import vn.launcher.tv.model.LauncherTheme
import vn.launcher.tv.model.LauncherUiState
import vn.launcher.tv.ui.components.MacActionButton
import vn.launcher.tv.ui.components.MacRow
import vn.launcher.tv.ui.components.MacSectionHeader
import vn.launcher.tv.ui.components.MacWindowDialog
import vn.launcher.tv.ui.theme.LauncherColors
import vn.launcher.tv.viewmodel.LauncherViewModel
import kotlin.math.roundToInt

@Composable
internal fun ThemeMenuDialog(state: LauncherUiState, vm: LauncherViewModel) {
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
internal fun WallpaperMenuDialog(
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
internal fun WallpaperOpacityDialog(state: LauncherUiState, vm: LauncherViewModel) {
    val en = state.isEnglish
    var pct by remember(state.bgAlphaPercent) { mutableIntStateOf(state.bgAlphaPercent.coerceIn(0, 100)) }
    val barFocus = remember { FocusRequester() }
    var barFocused by remember { mutableStateOf(false) }
    var okPressedHere by remember { mutableStateOf(false) }
    var trackWidthPx by remember { mutableIntStateOf(1) }
    val accent = LauncherColors.accent(state.theme)

    fun applyPct(next: Int) {
        val clamped = next.coerceIn(0, 100)
        if (clamped != pct) {
            pct = clamped
            vm.setWallpaperOpacity(clamped)
        }
    }

    LaunchedEffect(Unit) {
        barFocus.requestFocus()
    }

    MacWindowDialog(
        title = if (en) "Wallpaper opacity" else "Độ trong suốt hình nền",
        theme = state.theme,
        maxWidth = 500.dp,
        scrollable = false,
        onDismiss = vm::closeDialog
    ) {
        Text(
            text = "${if (en) "Opacity" else "Độ rõ"}: $pct%",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Text(
            text = if (en) "Press Left / Right to adjust, OK to close"
            else "Bấm Trái / Phải để chỉnh, bấm OK để xong",
            color = Color(0xB3FFFFFF),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
        Spacer(Modifier.height(10.dp))

        val barShape = RoundedCornerShape(14.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("wallpaper_opacity_slider")
                .clip(barShape)
                .background(if (barFocused) accent.copy(alpha = 0.28f) else Color(0x1EFFFFFF), barShape)
                .border(if (barFocused) 2.dp else 1.dp, if (barFocused) Color.White else Color(0x33FFFFFF), barShape)
                .focusRequester(barFocus)
                .onFocusChanged {
                    barFocused = it.isFocused
                    if (!it.isFocused) okPressedHere = false
                }
                .onPreviewKeyEvent { ev ->
                    when {
                        ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft -> {
                            vm.playMove()
                            applyPct(pct - 5)
                            true
                        }
                        ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionRight -> {
                            vm.playMove()
                            applyPct(pct + 5)
                            true
                        }
                        ev.type == KeyEventType.KeyDown && (ev.key == Key.DirectionUp || ev.key == Key.DirectionDown) -> {
                            true
                        }
                        ev.type == KeyEventType.KeyDown &&
                            (ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter) -> {
                            if (ev.nativeKeyEvent.repeatCount == 0) {
                                okPressedHere = true
                            }
                            true
                        }
                        ev.type == KeyEventType.KeyUp &&
                            (ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter) -> {
                            val wasPressed = okPressedHere
                            okPressedHere = false
                            if (wasPressed) {
                                vm.playSelect()
                                vm.closeDialog()
                            }
                            true
                        }
                        else -> false
                    }
                }
                .focusable()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "◀",
                color = if (pct > 0) Color.White else Color(0x55FFFFFF),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp)
                    .onSizeChanged { trackWidthPx = it.width.coerceAtLeast(1) }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val ratio = (offset.x / trackWidthPx.toFloat()).coerceIn(0f, 1f)
                            applyPct((ratio * 100f).roundToInt())
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val ratio = (change.position.x / trackWidthPx.toFloat()).coerceIn(0f, 1f)
                            applyPct((ratio * 100f).roundToInt())
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(Color(0x44FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((pct / 100f).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(if (barFocused) Color.White else accent)
                    )
                }
            }
            Text(
                text = "▶",
                color = if (pct < 100) Color.White else Color(0x55FFFFFF),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "$pct%",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.End
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            MacActionButton(
                text = if (en) "Done" else "Xong",
                color = accent,
                onClick = vm::closeDialog
            )
        }
    }
}

@Composable
internal fun QrUploadDialog(
    title: String,
    hint: String,
    url: String,
    qrBitmap: Bitmap?,
    theme: LauncherTheme,
    en: Boolean,
    onDismiss: () -> Unit
) {
    MacWindowDialog(
        title = title,
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
            if (qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .padding(10.dp)
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = hint,
                color = Color(0xE6FFFFFF),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(text = url, color = Color(0x99FFFFFF), fontSize = 11.sp, textAlign = TextAlign.Center)
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
internal fun QrApiKeyUploadDialog(
    dialog: ActiveDialog.QrApiKeyUpload,
    theme: LauncherTheme,
    en: Boolean,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    MacWindowDialog(
        title = if (en) "Send Gemini API Key from Phone" else "Quét QR nhập API Key từ điện thoại",
        theme = theme,
        maxWidth = 490.dp,
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
                        .size(240.dp)
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
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (en) {
                    "Scan with your phone (same Wi-Fi as the TV), paste your Gemini API key from aistudio.google.com/apikey and tap Send."
                } else {
                    "Dùng điện thoại (cùng Wi-Fi với TV) quét mã, dán Gemini API Key lấy từ aistudio.google.com/apikey rồi bấm Gửi lên TV."
                },
                color = Color(0xE6FFFFFF),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(text = dialog.url, color = Color(0x99FFFFFF), fontSize = 11.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MacActionButton(
                    text = if (en) "Enter on TV" else "Nhập trên TV",
                    color = Color(0x33FFFFFF),
                    onClick = onBack
                )
                MacActionButton(
                    text = if (en) "Close" else "Đóng",
                    color = LauncherColors.accent(theme),
                    onClick = onDismiss
                )
            }
        }
    }
}

@Composable
internal fun LocalWallpapersDialog(
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
