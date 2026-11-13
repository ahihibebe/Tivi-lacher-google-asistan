package vn.launcher.tv.ui.screens

import android.annotation.SuppressLint
import android.view.KeyEvent as AndroidKeyEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import vn.launcher.tv.data.CrashGuard
import vn.launcher.tv.data.PriceRepository
import vn.launcher.tv.data.VoiceCommand
import vn.launcher.tv.model.ActiveDialog
import vn.launcher.tv.model.LauncherUiState
import vn.launcher.tv.model.PriceKind
import vn.launcher.tv.ui.components.MacActionButton
import vn.launcher.tv.ui.components.MacEditDialog
import vn.launcher.tv.ui.components.MacRow
import vn.launcher.tv.ui.components.MacWindowDialog
import vn.launcher.tv.ui.theme.LauncherColors
import vn.launcher.tv.viewmodel.LauncherViewModel

@Composable
fun SecondaryDialogHost(
    dialog: ActiveDialog,
    state: LauncherUiState,
    vm: LauncherViewModel
) {
    val en = state.isEnglish
    val palette = LauncherColors.SettingsPalette

    when (dialog) {
        ActiveDialog.TvFrameMenu -> {
            MacWindowDialog(
                title = if (en) "Channels in the TV frame" else "Kênh trong khung TV",
                theme = state.theme,
                maxWidth = 520.dp,
                onDismiss = vm::closeDialog
            ) {
                state.tvChannels.forEach { ch ->
                    MacRow(
                        title = "${if (en) "Channel" else "Kênh"} ${ch.slot + 1}: ${ch.name}",
                        theme = state.theme,
                        icon = Icons.Filled.LiveTv,
                        iconTintBg = palette[5 + ch.slot],
                        showChevron = true,
                        onFocused = vm::playMove,
                        onClick = { vm.openDialog(ActiveDialog.EditChannel(ch.slot, ch.name, ch.link)) }
                    )
                }
            }
        }

        is ActiveDialog.EditChannel -> MacEditDialog(
            title = "${if (en) "Edit channel " else "Sửa kênh "}${dialog.slot + 1}",
            message = null,
            hints = listOf(
                if (en) "Channel name" else "Tên kênh",
                if (en) "Channel link" else "Liên kết mở kênh"
            ),
            initialValues = listOf(dialog.name, dialog.link),
            theme = state.theme,
            isEnglish = en,
            onSave = { vals -> vm.saveChannelSlot(dialog.slot, vals[0], vals[1]) },
            onDefault = { vm.resetChannelSlot(dialog.slot) },
            onDismiss = vm::closeDialog
        )

        ActiveDialog.VoiceChannelsEdit -> MacEditDialog(
            title = if (en) "Voice channels" else "Kênh gọi bằng giọng nói",
            message = if (en) {
                "Each channel as NAME|link, separated by commas. Say \"VTV1\", \"Open VTV3 on VieON\" or \"VTV1 on VTVgo\"."
            } else {
                "Mỗi kênh dạng TÊN|liên kết, cách nhau bằng dấu phẩy. Nói \"VTV1\", \"Mở VTV3 trên VieON\" hoặc \"VTV1 trên VTVgo\"."
            },
            hints = listOf("FPT Play", "VTVgo", "VieON"),
            initialValues = listOf(state.voiceChannelFpt, state.voiceChannelVtvgo, state.voiceChannelVieon),
            theme = state.theme,
            isEnglish = en,
            onSave = { vals -> vm.saveVoiceChannels(vals[0], vals[1], vals[2]) },
            onDefault = vm::resetVoiceChannels,
            onDismiss = vm::closeDialog
        )

        ActiveDialog.KeyMapMenu -> {
            MacWindowDialog(
                title = if (en) "Key shortcuts for apps" else "Gán phím mở ứng dụng",
                theme = state.theme,
                maxWidth = 520.dp,
                onDismiss = vm::closeDialog
            ) {
                state.keyShortcuts.entries.forEachIndexed { idx, (code, pkg) ->
                    val kName = vm.keyName(code)
                    val aLabel = vm.appLabelOf(pkg)
                    MacRow(
                        title = "$kName  →  $aLabel",
                        theme = state.theme,
                        icon = Icons.Filled.SettingsRemote,
                        iconTintBg = palette[idx % palette.size],
                        onFocused = vm::playMove,
                        onClick = { vm.openDialog(ActiveDialog.ConfirmRemoveKey(code, kName, aLabel)) }
                    )
                }
                MacRow(
                    title = if (en) "+ Add key shortcut" else "+ Thêm gán phím",
                    theme = state.theme,
                    icon = Icons.Filled.Tune,
                    iconTintBg = palette[1],
                    showChevron = true,
                    onFocused = vm::playMove,
                    onClick = { vm.openDialog(ActiveDialog.PickAppForKey) }
                )
            }
        }

        ActiveDialog.PickAppForKey -> {
            MacWindowDialog(
                title = if (en) "Choose an app" else "Chọn ứng dụng",
                theme = state.theme,
                maxWidth = 520.dp,
                onDismiss = vm::closeDialog
            ) {
                MacRow(
                    title = if (en) "Open voice command" else "Mở giọng nói (nói lệnh)",
                    theme = state.theme,
                    icon = Icons.Filled.Mic,
                    iconTintBg = palette[0],
                    onFocused = vm::playMove,
                    onClick = {
                        vm.openDialog(ActiveDialog.CaptureKey("@voice", vm.appLabelOf("@voice")))
                    }
                )
                state.allApps.filter { !it.isSelf }.forEachIndexed { idx, app ->
                    MacRow(
                        title = app.label,
                        theme = state.theme,
                        icon = Icons.Filled.Apps,
                        iconTintBg = palette[(idx + 1) % palette.size],
                        onFocused = vm::playMove,
                        onClick = { vm.openDialog(ActiveDialog.CaptureKey(app.pkg, app.label)) }
                    )
                }
            }
        }

        is ActiveDialog.CaptureKey -> {
            MacWindowDialog(
                title = if (en) "Assign key" else "Gán phím",
                theme = state.theme,
                maxWidth = 520.dp,
                onDismiss = vm::closeDialog
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusable()
                        .onKeyEvent { ev ->
                            if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                            if (ev.key == Key.Back) {
                                vm.closeDialog()
                                return@onKeyEvent true
                            }
                            vm.assignKeyShortcut(ev.nativeKeyEvent.keyCode, dialog.pkg, dialog.label)
                            true
                        }
                        .padding(12.dp)
                ) {
                    Text(
                        text = (if (en) "Press a remote key to open " else "Bấm một phím trên remote để mở ") +
                            dialog.label +
                            (if (en) "\n\n(Press Back to cancel)" else "\n\n(Bấm Back để hủy)"),
                        color = Color(0xE6FFFFFF),
                        fontSize = 14.sp
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    MacActionButton(
                        text = if (en) "Cancel" else "Hủy",
                        color = Color(0x33FFFFFF),
                        onClick = vm::closeDialog
                    )
                }
            }
        }

        is ActiveDialog.ConfirmRemoveKey -> {
            MacWindowDialog(
                title = "${if (en) "Remove shortcut " else "Xóa gán phím "}${dialog.keyName}?",
                theme = state.theme,
                maxWidth = 500.dp,
                onDismiss = vm::closeDialog
            ) {
                Text(dialog.appLabel, color = Color(0xE6FFFFFF), fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    MacActionButton(if (en) "Cancel" else "Hủy", Color(0x33FFFFFF), onClick = vm::closeDialog)
                    Spacer(Modifier.width(10.dp))
                    MacActionButton(
                        if (en) "Remove" else "Xóa",
                        Color(0xFFFF453A),
                        onClick = { vm.removeKeyShortcut(dialog.keyCode) }
                    )
                }
            }
        }

        ActiveDialog.FptLinkEdit -> MacEditDialog(
            title = if (en) "FPT Play – TV section link" else "FPT Play – liên kết mở Truyền hình",
            message = if (en) {
                "Used when opening FPT Play (app tile and key shortcut). Empty = open FPT Play normally."
            } else {
                "Dùng khi mở FPT Play (ô ứng dụng và phím gán). Để trống = mở FPT Play như bình thường."
            },
            hints = listOf(if (en) "Link" else "Liên kết"),
            initialValues = listOf(state.fptLink),
            theme = state.theme,
            isEnglish = en,
            onSave = { vals -> vm.saveFptLink(vals[0]) },
            onDefault = vm::resetFptLink,
            onDismiss = vm::closeDialog
        )

        ActiveDialog.PriceMenu -> {
            MacWindowDialog(
                title = if (en) "Prices" else "Bảng giá",
                theme = state.theme,
                maxWidth = 520.dp,
                onDismiss = vm::closeDialog
            ) {
                val items = listOf(
                    PriceKind.GOLD to if (en) "Gold & gold ring price" else "Giá vàng & vàng nhẫn",
                    PriceKind.COFFEE to if (en) "Coffee price (giacaphe.com)" else "Giá cà phê (giacaphe.com)",
                    PriceKind.PEPPER to if (en) "Pepper price (giacaphe.com)" else "Giá hồ tiêu (giacaphe.com)",
                    PriceKind.FUEL to if (en) "Fuel price (zones 1 and 2)" else "Giá xăng dầu (vùng 1, vùng 2)"
                )
                items.forEachIndexed { idx, (kind, label) ->
                    MacRow(
                        title = label,
                        theme = state.theme,
                        icon = Icons.Filled.Payments,
                        iconTintBg = palette[idx],
                        showChevron = true,
                        onFocused = vm::playMove,
                        onClick = { vm.openPrice(kind) }
                    )
                }
                MacRow(
                    title = if (en) "Edit price sources…" else "Sửa nguồn bảng giá…",
                    theme = state.theme,
                    icon = Icons.Filled.Tune,
                    iconTintBg = palette[4],
                    showChevron = true,
                    onFocused = vm::playMove,
                    onClick = { vm.openDialog(ActiveDialog.EditPriceUrls) }
                )
            }
        }

        ActiveDialog.EditPriceUrls -> MacEditDialog(
            title = if (en) "Price sources" else "Nguồn bảng giá",
            message = if (en) {
                "Web page addresses that show the price tables. Change them if a default page shows no table."
            } else {
                "Địa chỉ trang web hiển thị bảng giá. Đổi sang trang khác nếu trang mặc định không hiện bảng."
            },
            hints = listOf(
                if (en) "Gold" else "Giá vàng",
                if (en) "Coffee" else "Giá cà phê",
                if (en) "Pepper" else "Giá hồ tiêu",
                if (en) "Fuel" else "Giá xăng dầu"
            ),
            initialValues = listOf(
                state.priceUrls[PriceKind.GOLD].orEmpty(),
                state.priceUrls[PriceKind.COFFEE].orEmpty(),
                state.priceUrls[PriceKind.PEPPER].orEmpty(),
                state.priceUrls[PriceKind.FUEL].orEmpty()
            ),
            theme = state.theme,
            isEnglish = en,
            onSave = { v -> vm.savePriceUrls(v[0], v[1], v[2], v[3]) },
            onDefault = vm::resetPriceUrls,
            onDismiss = vm::closeDialog
        )

        is ActiveDialog.PriceViewer -> PriceViewerDialog(dialog, state, vm)

        is ActiveDialog.VoiceListening -> VoiceListeningDialog(dialog, state, vm)

        is ActiveDialog.TimeBoard -> TimeBoardDialog(dialog, state, vm)

        is ActiveDialog.WeatherBoard -> WeatherBoardDialog(dialog, state, vm)

        ActiveDialog.MediaSettings -> {
            MacWindowDialog(
                title = if (en) "Music & photos" else "Nhạc & ảnh",
                theme = state.theme,
                maxWidth = 520.dp,
                onDismiss = vm::closeDialog
            ) {
                MacRow(
                    title = if (en) "Choose slideshow folders…" else "Chọn thư mục ảnh trình chiếu…",
                    theme = state.theme,
                    icon = Icons.Filled.Image,
                    iconTintBg = palette[2],
                    showChevron = true,
                    onFocused = vm::playMove,
                    onClick = { vm.chooseSlideFolders() }
                )
                MacRow(
                    title = "${if (en) "Seconds per photo: " else "Thời gian chuyển ảnh: "}${state.slideSeconds}s",
                    theme = state.theme,
                    icon = Icons.Filled.Tune,
                    iconTintBg = palette[3],
                    showChevron = true,
                    onFocused = vm::playMove,
                    onClick = { vm.openDialog(ActiveDialog.ChooseSlideSeconds) }
                )
                MacRow(
                    title = if (en) "Refresh music & photo library" else "Làm mới thư viện nhạc & ảnh",
                    theme = state.theme,
                    icon = Icons.Filled.MusicNote,
                    iconTintBg = palette[8],
                    onFocused = vm::playMove,
                    onClick = { vm.refreshAllMediaLibrary() }
                )
            }
        }

        ActiveDialog.ChooseSlideSeconds -> {
            val opts = listOf(3, 5, 8, 15, 30)
            MacWindowDialog(
                title = if (en) "Seconds per photo" else "Thời gian chuyển ảnh",
                theme = state.theme,
                maxWidth = 440.dp,
                onDismiss = vm::closeDialog
            ) {
                opts.forEachIndexed { idx, sec ->
                    MacRow(
                        title = "$sec${if (en) " seconds" else " giây"}",
                        theme = state.theme,
                        icon = Icons.Filled.Tune,
                        iconTintBg = palette[idx % palette.size],
                        trailing = if (sec == state.slideSeconds) "✓" else null,
                        onFocused = vm::playMove,
                        onClick = {
                            vm.playSelect()
                            vm.setSlideSeconds(sec)
                        }
                    )
                }
            }
        }

        is ActiveDialog.ChooseSlideFolders -> {
            val selected = remember(dialog) {
                mutableStateListOf<String>().apply { addAll(dialog.selectedIds) }
            }
            MacWindowDialog(
                title = if (en) "Slideshow folders (storage, USB)" else "Thư mục ảnh trình chiếu (bộ nhớ, USB)",
                theme = state.theme,
                maxWidth = 540.dp,
                onDismiss = vm::closeDialog
            ) {
                dialog.albums.forEach { album ->
                    val checked = selected.contains(album.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (checked) selected.remove(album.id) else selected.add(album.id)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                if (it) selected.add(album.id) else selected.remove(album.id)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = LauncherColors.accent(state.theme),
                                uncheckedColor = Color(0x99FFFFFF),
                                checkmarkColor = Color.White
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${album.name} (${album.count})", color = Color.White, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    MacActionButton(
                        text = if (en) "All photos" else "Tất cả ảnh",
                        color = Color(0x33FFFFFF),
                        onClick = { vm.saveSlideFolders(null) }
                    )
                    Spacer(Modifier.width(10.dp))
                    MacActionButton(
                        text = if (en) "Cancel" else "Hủy",
                        color = Color(0x33FFFFFF),
                        onClick = vm::closeDialog
                    )
                    Spacer(Modifier.width(10.dp))
                    MacActionButton(
                        text = if (en) "Save" else "Lưu",
                        color = LauncherColors.accent(state.theme),
                        onClick = { vm.saveSlideFolders(selected.toSet()) }
                    )
                }
            }
        }

        ActiveDialog.DefaultLauncherMenu -> {
            val ip = state.localIp ?: "<IP-TV>"
            val pkg = LocalContext.current.packageName
            val setCmd = "adb shell cmd package set-home-activity $pkg/vn.launcher.tv.MainActivity"
            MacWindowDialog(
                title = if (en) "Set as default launcher" else "Đặt launcher mặc định",
                theme = state.theme,
                maxWidth = 540.dp,
                onDismiss = vm::closeDialog
            ) {
                MacRow(
                    title = if (en) "System dialog" else "Hộp thoại hệ thống",
                    theme = state.theme,
                    icon = Icons.Filled.Home,
                    iconTintBg = palette[1],
                    onFocused = vm::playMove,
                    onClick = {
                        vm.closeDialog()
                        vm.requestSystemDefaultLauncher()
                    }
                )
                MacRow(
                    title = if (en) "ADB over Wi-Fi" else "ADB qua Wi-Fi",
                    theme = state.theme,
                    icon = Icons.Filled.SettingsRemote,
                    iconTintBg = palette[2],
                    onFocused = vm::playMove,
                    onClick = {
                        vm.openDialog(
                            ActiveDialog.AdbHelp(
                                title = if (en) "ADB over Wi-Fi" else "ADB qua Wi-Fi",
                                intro = if (en) {
                                    "1. On the TV: Settings → Developer options → enable USB debugging and network debugging.\n" +
                                        "2. On your computer, run the commands below. Accept the debugging prompt on the TV if it appears."
                                } else {
                                    "1. Trên TV: Cài đặt → Tùy chọn nhà phát triển → bật Gỡ lỗi USB và Gỡ lỗi qua mạng.\n" +
                                        "2. Trên máy tính, chạy các lệnh dưới đây. Nếu TV hỏi cho phép gỡ lỗi thì chọn Cho phép."
                                },
                                command = "adb connect $ip:5555\n$setCmd"
                            )
                        )
                    }
                )
                MacRow(
                    title = if (en) "ADB over USB" else "ADB qua USB",
                    theme = state.theme,
                    icon = Icons.Filled.SettingsRemote,
                    iconTintBg = palette[3],
                    onFocused = vm::playMove,
                    onClick = {
                        vm.openDialog(
                            ActiveDialog.AdbHelp(
                                title = if (en) "ADB over USB" else "ADB qua USB",
                                intro = if (en) {
                                    "1. Enable USB debugging on the TV and connect it to the computer with a USB cable.\n" +
                                        "2. Run the commands below. Accept the debugging prompt on the TV if it appears."
                                } else {
                                    "1. Bật Gỡ lỗi USB trên TV và nối TV với máy tính bằng cáp USB (cổng hỗ trợ ADB).\n" +
                                        "2. Chạy các lệnh dưới đây trên máy tính. Nếu TV hỏi cho phép gỡ lỗi thì chọn Cho phép."
                                },
                                command = "adb devices\n$setCmd"
                            )
                        )
                    }
                )
                MacRow(
                    title = if (en) "Automatic (local ADB)" else "Tự động bằng ADB nội bộ",
                    theme = state.theme,
                    icon = Icons.Filled.Tune,
                    iconTintBg = palette[4],
                    onFocused = vm::playMove,
                    onClick = {
                        vm.closeDialog()
                        vm.autoSetDefaultLauncher()
                    }
                )
                MacRow(
                    title = if (en) "How to enable network ADB" else "Hướng dẫn bật ADB qua mạng",
                    theme = state.theme,
                    icon = Icons.Filled.Tune,
                    iconTintBg = palette[6],
                    onFocused = vm::playMove,
                    onClick = {
                        vm.openDialog(
                            ActiveDialog.AdbHelp(
                                title = if (en) "Enable network ADB" else "Bật ADB qua mạng",
                                intro = if (en) {
                                    "\"Automatic with local ADB\" lets the launcher connect to the TV's own ADB (port 5555).\n\n" +
                                        "1. On the TV: Settings → Developer options → enable USB debugging and network debugging (if available).\n" +
                                        "2. If port 5555 is not available (Android 11+), connect a computer and run the command below once (it resets on reboot).\n" +
                                        "3. Choose Always allow when the TV asks."
                                } else {
                                    "Tính năng \"Tự động bằng ADB nội bộ\" cho launcher tự kết nối tới ADB của chính TV (cổng 5555).\n\n" +
                                        "1. Trên TV: Cài đặt → Tùy chọn nhà phát triển → bật Gỡ lỗi USB và Gỡ lỗi qua mạng (nếu có).\n" +
                                        "2. Nếu TV không có cổng 5555 (Android 11 trở lên), kết nối máy tính với TV rồi chạy lệnh dưới đây một lần.\n" +
                                        "3. Khi TV hỏi cho phép gỡ lỗi, chọn Luôn cho phép."
                                },
                                command = "adb tcpip 5555"
                            )
                        )
                    }
                )
                MacRow(
                    title = if (en) "Restore stock launcher" else "Khôi phục launcher gốc",
                    theme = state.theme,
                    icon = Icons.Filled.Home,
                    iconTintBg = palette[5],
                    onFocused = vm::playMove,
                    onClick = {
                        vm.openDialog(
                            ActiveDialog.AdbHelp(
                                title = if (en) "Restore stock launcher" else "Khôi phục launcher gốc",
                                intro = if (en) {
                                    "Use this if the screen is black or you want the old home screen back. Pick the command matching Android TV or Google TV."
                                } else {
                                    "Dùng khi màn hình đen hoặc muốn quay về giao diện cũ. Chọn 1 trong 2 lệnh phù hợp với Android TV hoặc Google TV."
                                },
                                command = "adb shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity\n" +
                                    "adb shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity"
                            )
                        )
                    }
                )
            }
        }

        is ActiveDialog.AdbHelp -> {
            MacWindowDialog(title = dialog.title, theme = state.theme, maxWidth = 560.dp, onDismiss = vm::closeDialog) {
                Text(dialog.intro, color = Color(0xE6FFFFFF), fontSize = 13.sp, modifier = Modifier.padding(6.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    text = dialog.command,
                    color = Color(0xFF64D2FF),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x40000000))
                        .padding(12.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    MacActionButton(if (en) "Close" else "Đóng", Color(0x33FFFFFF), onClick = vm::closeDialog)
                    Spacer(Modifier.width(10.dp))
                    MacActionButton(
                        text = if (en) "Copy commands" else "Sao chép lệnh",
                        color = LauncherColors.accent(state.theme),
                        onClick = {
                            vm.copyToClipboard("adb", dialog.command)
                            vm.closeDialog()
                        }
                    )
                }
            }
        }

        ActiveDialog.HiddenAppsMenu -> {
            val hidden = state.hiddenPackages.sorted()
            MacWindowDialog(
                title = if (en) "Hidden apps" else "Ứng dụng đã ẩn",
                theme = state.theme,
                maxWidth = 520.dp,
                onDismiss = vm::closeDialog
            ) {
                if (hidden.isEmpty()) {
                    Text(
                        text = if (en) "No hidden apps" else "Chưa có ứng dụng nào bị ẩn",
                        color = Color(0xCCFFFFFF),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                } else {
                    hidden.forEachIndexed { idx, pkg ->
                        MacRow(
                            title = vm.appLabelOf(pkg),
                            theme = state.theme,
                            icon = Icons.Filled.Apps,
                            iconTintBg = palette[idx % palette.size],
                            onFocused = vm::playMove,
                            onClick = {
                                vm.playSelect()
                                vm.restoreHiddenApp(pkg)
                            }
                        )
                    }
                }
            }
        }

        ActiveDialog.MusicPanel -> MusicPanelDialog(
            music = state.music,
            theme = state.theme,
            isEnglish = en,
            onPrev = vm::prevTrack,
            onTogglePlay = vm::toggleMusicPlay,
            onStop = vm::stopMusicPlayback,
            onNext = vm::nextTrack,
            onSelectTrack = vm::playTrack,
            onRefresh = vm::refreshMusicLibrary,
            onDismiss = vm::closeDialog
        )

        ActiveDialog.ConfirmPowerOff -> {
            MacWindowDialog(
                title = if (en) "Turn off the TV?" else "Tắt TV?",
                theme = state.theme,
                maxWidth = 500.dp,
                onDismiss = vm::closeDialog
            ) {
                Text(
                    text = if (en) "The TV will go to standby. Local ADB must be enabled."
                    else "TV sẽ chuyển sang chế độ chờ. Cần bật ADB nội bộ.",
                    color = Color(0xE6FFFFFF),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(8.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    MacActionButton(if (en) "Cancel" else "Hủy", Color(0x33FFFFFF), onClick = vm::closeDialog)
                    Spacer(Modifier.width(10.dp))
                    MacActionButton(if (en) "Turn off" else "Tắt TV", Color(0xFFFF453A), onClick = vm::powerOff)
                }
            }
        }

        is ActiveDialog.TileActions -> {
            MacWindowDialog(title = dialog.app.label, theme = state.theme, maxWidth = 460.dp, onDismiss = vm::closeDialog) {
                MacRow(
                    title = if (en) "Move" else "Di chuyển",
                    theme = state.theme,
                    icon = Icons.AutoMirrored.Filled.DriveFileMove,
                    iconTintBg = palette[0],
                    onFocused = vm::playMove,
                    onClick = {
                        vm.playSelect()
                        vm.startMove(dialog.app)
                    }
                )
                MacRow(
                    title = if (en) "Hide" else "Ẩn",
                    theme = state.theme,
                    icon = Icons.Filled.VisibilityOff,
                    iconTintBg = palette[1],
                    onFocused = vm::playMove,
                    onClick = {
                        vm.playSelect()
                        vm.hideApp(dialog.app)
                    }
                )
                if (!dialog.app.isSelf) {
                    MacRow(
                        title = if (en) "Uninstall" else "Xóa",
                        theme = state.theme,
                        icon = Icons.Filled.Delete,
                        iconTintBg = palette[5],
                        onFocused = vm::playMove,
                        onClick = {
                            vm.playSelect()
                            vm.uninstallApp(dialog.app)
                        }
                    )
                }
            }
        }

        is ActiveDialog.SpeechPopup -> {
            MacWindowDialog(title = dialog.title, theme = state.theme, maxWidth = 520.dp, onDismiss = vm::closeDialog) {
                Text(dialog.message, color = Color(0xE6FFFFFF), fontSize = 14.sp, modifier = Modifier.padding(8.dp))
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    MacActionButton(if (en) "Close" else "Đóng", LauncherColors.accent(state.theme), onClick = vm::closeDialog)
                }
            }
        }

        is ActiveDialog.CrashReport -> {
            MacWindowDialog(
                title = if (en) "The launcher hit an error earlier" else "Launcher từng gặp lỗi",
                theme = state.theme,
                maxWidth = 560.dp,
                onDismiss = vm::closeDialog
            ) {
                Text(
                    text = CrashGuard.firstLines(dialog.trace, 10),
                    color = Color(0xCCFFFFFF),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(6.dp)
                )
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    MacActionButton(if (en) "Close" else "Đóng", Color(0x33FFFFFF), onClick = vm::closeDialog)
                    Spacer(Modifier.width(10.dp))
                    MacActionButton(
                        text = if (en) "Copy" else "Sao chép",
                        color = LauncherColors.accent(state.theme),
                        onClick = {
                            vm.copyToClipboard("crash", dialog.trace)
                            vm.closeDialog()
                        }
                    )
                }
            }
        }

        else -> Unit
    }
}

@Composable
private fun VoiceListeningDialog(
    dialog: ActiveDialog.VoiceListening,
    state: LauncherUiState,
    vm: LauncherViewModel
) {
    val en = state.isEnglish
    val pulse = rememberInfiniteTransition(label = "mic_pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "mic_scale"
    )

    MacWindowDialog(
        title = if (en) "Voice Assistant" else "Trợ lý giọng nói",
        theme = state.theme,
        maxWidth = 560.dp,
        onDismiss = vm::dismissVoiceListening
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(if (dialog.errorText == null) scale else 1f)
                    .clip(CircleShape)
                    .background(if (dialog.errorText != null) Color(0x33FF453A) else Color(0x330A84FF))
                    .border(
                        2.dp,
                        if (dialog.errorText != null) Color(0xFFFF453A) else LauncherColors.accent(state.theme),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            // 4-color Google Assistant indicator dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(
                    Color(0xFF4285F4),
                    Color(0xFFEA4335),
                    Color(0xFFFBBC05),
                    Color(0xFF34A853)
                ).forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(c)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = when {
                    dialog.errorText != null -> dialog.errorText
                    dialog.partialText.isNotBlank() -> "\"${dialog.partialText}\""
                    else -> dialog.statusText.ifEmpty { if (en) "Listening…" else "Đang nghe…" }
                },
                color = if (dialog.errorText != null) Color(0xFFFF9F9A) else Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = if (en) {
                    "Try: \"Open YouTube\" · \"VTV1\" · \"Search ...\" · \"What time is it\" · \"Weather\" · \"Coffee price Dak Lak\" · \"Gold price\" · \"Fuel price\""
                } else {
                    "Thử nói: \"Mở YouTube\" · \"Mở VTV1\" · \"Tìm kiếm ...\" · \"Mấy giờ rồi\" · \"Thời tiết Đắk Lắk\" · \"Giá cà phê Đắk Lắk\" · \"Giá tiêu\" · \"Giá vàng nhẫn\" · \"Giá xăng\""
                },
                color = Color(0xB3FFFFFF),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                MacActionButton(
                    text = if (en) "Cancel" else "Hủy",
                    color = Color(0x33FFFFFF),
                    onClick = vm::dismissVoiceListening
                )
                Spacer(Modifier.width(10.dp))
                MacActionButton(
                    text = if (en) "Google Voice" else "Dùng Google Voice",
                    color = LauncherColors.accent(state.theme),
                    onClick = vm::switchToGoogleVoiceDialog
                )
            }
        }
    }
}

@Composable
private fun TimeBoardDialog(
    dialog: ActiveDialog.TimeBoard,
    state: LauncherUiState,
    vm: LauncherViewModel
) {
    val en = state.isEnglish
    MacWindowDialog(
        title = if (en) "Date & Time" else "Bảng Ngày & Giờ",
        theme = state.theme,
        maxWidth = 540.dp,
        onDismiss = vm::closeDialog,
        headerActions = {
            HeaderChipButton(Icons.AutoMirrored.Filled.VolumeUp, if (en) "Read" else "Đọc lại") {
                vm.speak(dialog.spokenSummary)
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.AccessTime, contentDescription = null, tint = LauncherColors.accent(state.theme), modifier = Modifier.size(30.dp))
                Text(
                    text = dialog.timeText,
                    color = Color.White,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1).sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = dialog.solarDate,
                color = Color(0xEBFFFFFF),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            if (dialog.lunarDate.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = dialog.lunarDate,
                    color = Color(0xCCFFFFFF),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
            if (!dialog.holidayText.isNullOrEmpty()) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x22FF453A))
                        .border(1.dp, Color(0x55FF453A), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(dialog.holidayText, color = Color(0xFFFFB4AE), fontSize = 13.sp)
                }
            }
            if (!dialog.lunarAlert.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(dialog.lunarAlert, color = Color(0xFFFF9F0A), fontSize = 13.sp)
            }
            Spacer(Modifier.height(14.dp))
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
private fun WeatherBoardDialog(
    dialog: ActiveDialog.WeatherBoard,
    state: LauncherUiState,
    vm: LauncherViewModel
) {
    val en = state.isEnglish
    MacWindowDialog(
        title = if (en) "Weather" else "Bảng Thời Tiết",
        theme = state.theme,
        maxWidth = 560.dp,
        onDismiss = vm::closeDialog,
        headerActions = {
            if (!dialog.loading && dialog.spokenSummary.isNotEmpty()) {
                HeaderChipButton(Icons.AutoMirrored.Filled.VolumeUp, if (en) "Read" else "Đọc lại") {
                    vm.speak(dialog.spokenSummary)
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Place, contentDescription = null, tint = LauncherColors.accent(state.theme), modifier = Modifier.size(18.dp))
                Text(
                    text = dialog.location,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = dialog.dateText,
                color = Color(0x99FFFFFF),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(Modifier.height(14.dp))

            if (dialog.loading) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 18.dp)) {
                    CircularProgressIndicator(color = LauncherColors.accent(state.theme), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (en) "Fetching weather forecast…" else "Đang tải dự báo thời tiết…",
                        color = Color(0xCCFFFFFF),
                        fontSize = 15.sp
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x18FFFFFF))
                        .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Icon(
                        imageVector = if (dialog.rainPct >= 40 || dialog.weatherCode >= 51) Icons.Filled.Cloud else Icons.Filled.WbSunny,
                        contentDescription = null,
                        tint = if (dialog.rainPct >= 40 || dialog.weatherCode >= 51) Color(0xFF64D2FF) else Color(0xFFFFD60A),
                        modifier = Modifier.size(46.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${dialog.tempC}°C",
                            color = Color.White,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Light
                        )
                        Text(
                            text = dialog.description,
                            color = Color(0xE6FFFFFF),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${dialog.minC}° / ${dialog.maxC}°C",
                            color = Color(0xCCFFFFFF),
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = Color(0xFF64D2FF), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${dialog.rainPct}%",
                                color = Color(0xFF64D2FF),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            if (dialog.spokenSummary.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = dialog.spokenSummary,
                    color = Color(0xB3FFFFFF),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(14.dp))
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PriceViewerDialog(
    dialog: ActiveDialog.PriceViewer,
    state: LauncherUiState,
    vm: LauncherViewModel
) {
    val en = state.isEnglish
    val title = when (dialog.kind) {
        PriceKind.GOLD -> if (en) "Gold & Gold Ring Prices" else "Bảng giá Vàng & Vàng Nhẫn"
        PriceKind.COFFEE -> if (en) "Coffee Prices (giacaphe.com)" else "Bảng giá Cà Phê hôm nay"
        PriceKind.PEPPER -> if (en) "Pepper Prices (giacaphe.com)" else "Bảng giá Hồ Tiêu hôm nay"
        PriceKind.FUEL -> if (en) "Petrolimex Fuel Prices" else "Bảng giá Xăng Dầu"
    }
    val h = (LocalConfiguration.current.screenHeightDp * 0.68f).dp
    var showWebMode by remember(dialog.kind) { mutableStateOf(false) }
    var webLoading by remember(dialog.url) { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var tries by remember(dialog.url) { mutableIntStateOf(0) }

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
        // Date + source status bar
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
                .background(if (showWebMode) Color.White else Color(0x18FFFFFF))
        ) {
            // Embedded WebView stays attached when loading tables or when user toggles Web mode
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        isFocusable = showWebMode
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                webLoading = false
                                fun extractLater(delayMs: Long) {
                                    view.postDelayed({
                                        view.evaluateJavascript(PriceRepository.EXTRACT_ALL_TABLES_JS) { value ->
                                            val ok = vm.onPriceJsonExtracted(value ?: "", dialog.speakOnLoad)
                                            if (!ok && tries < 2) {
                                                tries++
                                                extractLater(2500L)
                                            }
                                        }
                                    }, delayMs)
                                }
                                extractLater(1500L)
                            }
                        }
                        loadUrl(dialog.url)
                        webViewRef = this
                    }
                },
                update = { view ->
                    view.isFocusable = showWebMode
                },
                modifier = if (showWebMode) Modifier.fillMaxSize() else Modifier.size(1.dp).alpha(0f)
            )

            if (!showWebMode) {
                if (dialog.sections.isEmpty()) {
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
                    val tableScroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(tableScroll)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Highlight spoken province / commune at top
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

                        // All parsed price sections (Coffee/Pepper regions, Gold Bar + Vàng Nhẫn, Petrolimex Fuel)
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
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                when {
                                                    isHighlight -> Color(0x26FFD60A)
                                                    rIdx % 2 == 1 -> Color(0x0CFFFFFF)
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
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
                                                fontWeight = if (cIdx == 0 || isHighlight) FontWeight.SemiBold else FontWeight.Medium,
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
