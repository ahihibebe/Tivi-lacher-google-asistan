package vn.launcher.tv.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.launcher.tv.data.AppCheckMode
import vn.launcher.tv.data.currentAppCheckMode
import vn.launcher.tv.data.generateDebugUuidCandidate
import vn.launcher.tv.data.getActiveDebugSecret
import vn.launcher.tv.data.getCustomDebugToken
import vn.launcher.tv.data.getFirebaseAppId
import vn.launcher.tv.data.getPersonalGeminiApiKey
import vn.launcher.tv.data.maskGeminiApiKey
import vn.launcher.tv.model.ActiveDialog
import vn.launcher.tv.model.LauncherUiState
import vn.launcher.tv.ui.components.MacActionButton
import vn.launcher.tv.ui.components.MacWindowDialog
import vn.launcher.tv.ui.theme.LauncherColors
import vn.launcher.tv.viewmodel.LauncherViewModel

@Composable
internal fun VoiceListeningDialog(
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
                    "Try: \"Open YouTube\" · \"VTV1\" · \"Ask AI ...\" · \"What time is it\" · \"Weather\" · \"Coffee price Dak Lak\" · \"Gold price\" · \"Fuel price\""
                } else {
                    "Thử nói: \"Hỏi AI ...\" · \"Mở YouTube\" · \"Mở VTV1\" · \"Mấy giờ rồi\" · \"Thời tiết\" · \"Giá cà phê Đắk Lắk\" · \"Giá tiêu\" · \"Giá vàng nhẫn\" · \"Giá xăng\""
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
internal fun TimeBoardDialog(
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
internal fun WeatherBoardDialog(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiAnswerBoardDialog(
    dialog: ActiveDialog.AiAnswerBoard,
    state: LauncherUiState,
    vm: LauncherViewModel
) {
    val en = state.isEnglish
    val accent = LauncherColors.accent(state.theme)
    val scroll = rememberScrollState()

    MacWindowDialog(
        title = if (en) "Google AI Smart Search" else "Tìm kiếm thông minh · Google AI",
        theme = state.theme,
        maxWidth = 680.dp,
        onDismiss = vm::closeDialog,
        headerActions = {
            if (!dialog.loading && dialog.spokenText.isNotEmpty()) {
                HeaderChipButton(Icons.AutoMirrored.Filled.VolumeUp, if (en) "Read" else "Đọc lại") {
                    vm.speak(dialog.spokenText)
                }
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x220A84FF))
                .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = dialog.query,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 300.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x1C000000))
                .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            when {
                dialog.loading && dialog.answerText.isBlank() -> {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = accent, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = dialog.statusText.ifBlank {
                                if (en) "Asking Google AI…" else "Google AI đang tìm câu trả lời…"
                            },
                            color = Color(0xE6FFFFFF),
                            fontSize = 15.sp
                        )
                    }
                }
                !dialog.errorMessage.isNullOrBlank() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scroll)
                    ) {
                        Text(
                            text = dialog.errorMessage,
                            color = Color(0xFFFF8A80),
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scroll)
                    ) {
                        Text(
                            text = dialog.answerText,
                            color = Color(0xF2FFFFFF),
                            fontSize = 15.sp,
                            lineHeight = 23.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!dialog.errorMessage.isNullOrBlank() && (dialog.isAppCheckError || dialog.isApiKeyError)) {
                MacActionButton(
                    text = if (en) "Personal API Key" else "API Key cá nhân",
                    color = Color(0x66FF9F0A),
                    onClick = { vm.openPersonalAiKeyConfig(dialog.query) }
                )
            }
            if (!dialog.errorMessage.isNullOrBlank() && dialog.isAppCheckError) {
                MacActionButton(
                    text = "App Check",
                    color = Color(0x33FF9F0A),
                    onClick = { vm.openAppCheckConfig(dialog.query) }
                )
            }
            if (!dialog.loading && !dialog.errorMessage.isNullOrBlank()) {
                MacActionButton(
                    text = if (en) "Retry" else "Thử lại",
                    color = Color(0x5534C759),
                    onClick = { vm.askGoogleAi(dialog.query, forceRefresh = true) }
                )
            }
            MacActionButton(
                text = if (en) "Ask again" else "Hỏi tiếp",
                color = accent,
                onClick = vm::startVoiceCommand
            )
            MacActionButton(
                text = if (en) "Search Google" else "Tìm Google",
                color = Color(0x33FFFFFF),
                onClick = { vm.openExternalSearch(dialog.query, onYouTube = false) }
            )
            MacActionButton(
                text = "YouTube",
                color = Color(0x55FF3B30),
                onClick = { vm.openExternalSearch(dialog.query, onYouTube = true) }
            )
            MacActionButton(
                text = if (en) "Close" else "Đóng",
                color = Color(0x28FFFFFF),
                onClick = vm::closeDialog
            )
        }
    }
}

@Composable
internal fun AppCheckConfigDialog(
    dialog: ActiveDialog.AppCheckConfig,
    state: LauncherUiState,
    vm: LauncherViewModel
) {
    val context = LocalContext.current
    val en = state.isEnglish
    val accent = LauncherColors.accent(state.theme)
    val savedOrActive = remember {
        getCustomDebugToken(context) ?: getActiveDebugSecret(context).orEmpty()
    }
    var tokenInput by remember { mutableStateOf(savedOrActive) }
    val modeLabel = remember {
        when (currentAppCheckMode()) {
            AppCheckMode.RECAPTCHA -> "reCAPTCHA Enterprise"
            AppCheckMode.DEBUG_INTENT -> "Debug Token (Intent)"
            AppCheckMode.DEBUG_CUSTOM -> "Debug Token (TV)"
            AppCheckMode.DEBUG_AUTO_UUID -> "Debug Token (Auto UUID)"
            AppCheckMode.UNCONFIGURED -> if (en) "Unconfigured" else "Chưa cấu hình"
        }
    }

    val firebaseAppId = remember { getFirebaseAppId(context).orEmpty() }
    MacWindowDialog(
        title = if (en) "Firebase App Check Config" else "Cấu hình Firebase App Check",
        theme = state.theme,
        maxWidth = 640.dp,
        onDismiss = {
            if (!dialog.returnQuery.isNullOrBlank()) {
                vm.askGoogleAi(dialog.returnQuery, forceRefresh = true)
            } else {
                vm.closeDialog()
            }
        }
    ) {
        Text(
            text = if (en) {
                "Package: ${context.packageName}\n" +
                    (if (firebaseAppId.isNotEmpty()) "Firebase App ID: $firebaseAppId\n" else "") +
                    "Current mode: $modeLabel\n\n" +
                    "Important: A random UUID created on the TV will fail with 403 unless you also add that exact UUID under Firebase Console → App Check → Apps → Manage debug tokens (using your own Firebase project's google-services.json)."
            } else {
                "Gói ứng dụng: ${context.packageName}\n" +
                    (if (firebaseAppId.isNotEmpty()) "Firebase App ID: $firebaseAppId\n" else "") +
                    "Chế độ hiện tại: $modeLabel\n\n" +
                    "Lưu ý quan trọng: Chỉ bấm tạo UUID trên TV sẽ luôn báo lỗi 403 nếu mã UUID đó chưa được dán và lưu trong Firebase Console → App Check → Apps → Manage debug tokens (của đúng project Firebase trong google-services.json)."
            },
            color = Color(0xCCFFFFFF),
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it },
            label = {
                Text(if (en) "Registered App Check Debug Token (UUID)" else "Mã Debug Token đã đăng ký trên Firebase (UUID)")
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = accent,
                unfocusedBorderColor = Color(0x44FFFFFF),
                focusedLabelColor = accent,
                unfocusedLabelColor = Color(0x99FFFFFF)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MacActionButton(
                text = if (en) "New UUID" else "Tạo UUID mới",
                color = Color(0x33FFFFFF),
                onClick = { tokenInput = generateDebugUuidCandidate() }
            )
            MacActionButton(
                text = if (en) "Use reCAPTCHA" else "Dùng reCAPTCHA",
                color = Color(0x33FFFFFF),
                onClick = { vm.saveAppCheckDebugToken(null, dialog.returnQuery) }
            )
            MacActionButton(
                text = if (en) "Save & Retry" else "Lưu & Thử lại",
                color = accent,
                onClick = { vm.saveAppCheckDebugToken(tokenInput, dialog.returnQuery) }
            )
            MacActionButton(
                text = if (en) "Close" else "Đóng",
                color = Color(0x28FFFFFF),
                onClick = vm::closeDialog
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonalAiKeyDialog(
    dialog: ActiveDialog.PersonalAiKeyConfig,
    state: LauncherUiState,
    vm: LauncherViewModel
) {
    val context = LocalContext.current
    val en = state.isEnglish
    val accent = LauncherColors.accent(state.theme)
    val savedKey = remember { getPersonalGeminiApiKey(context).orEmpty() }
    var keyInput by remember { mutableStateOf(savedKey) }
    val maskedSaved = remember(savedKey) { maskGeminiApiKey(savedKey) }

    MacWindowDialog(
        title = if (en) "Personal Gemini API Key" else "Gemini API Key cá nhân",
        theme = state.theme,
        maxWidth = 650.dp,
        onDismiss = vm::closeDialog
    ) {
        Text(
            text = if (en) {
                val status = if (savedKey.isNotEmpty()) {
                    "Active key: $maskedSaved (bypasses Firebase App Check on TV Box)"
                } else {
                    "No personal key saved yet (currently using Firebase App Check)."
                }
                "$status\n\nGet a free Gemini API key at aistudio.google.com/apikey, then scan QR from your phone or paste it below. Stored locally on this TV only."
            } else {
                val status = if (savedKey.isNotEmpty()) {
                    "Đang dùng key cá nhân: $maskedSaved (chạy thẳng trên TV Box không cần App Check)"
                } else {
                    "Chưa nhập API Key cá nhân (hiện đang dùng Firebase App Check)."
                }
                "$status\n\nLấy key miễn phí tại aistudio.google.com/apikey, sau đó bấm \"Quét QR nhập từ ĐT\" để dán nhanh từ điện thoại hoặc nhập trực tiếp bên dưới."
            },
            color = Color(0xD9FFFFFF),
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            label = {
                Text(if (en) "Gemini API Key (AIza...)" else "Mã Gemini API Key (AIza...)")
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = accent,
                unfocusedBorderColor = Color(0x44FFFFFF),
                focusedLabelColor = accent,
                unfocusedLabelColor = Color(0x99FFFFFF)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MacActionButton(
                text = if (en) "QR from Phone" else "Quét QR nhập từ ĐT",
                color = Color(0x5530D158),
                onClick = { vm.startApiKeyQrUpload(dialog.returnQuery) }
            )
            if (savedKey.isNotEmpty() || keyInput.isNotBlank()) {
                MacActionButton(
                    text = if (en) "Clear Key" else "Xóa key",
                    color = Color(0x44FF453A),
                    onClick = { vm.savePersonalGeminiApiKeyInput(null, dialog.returnQuery) }
                )
            }
            MacActionButton(
                text = if (!dialog.returnQuery.isNullOrBlank()) {
                    if (en) "Save & Retry" else "Lưu & Thử lại"
                } else {
                    if (en) "Save" else "Lưu"
                },
                color = accent,
                onClick = { vm.savePersonalGeminiApiKeyInput(keyInput, dialog.returnQuery) }
            )
            MacActionButton(
                text = if (en) "Close" else "Đóng",
                color = Color(0x28FFFFFF),
                onClick = vm::closeDialog
            )
        }
    }
}
