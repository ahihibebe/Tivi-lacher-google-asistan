package vn.launcher.tv.ui.components

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.launcher.tv.model.LauncherTheme
import vn.launcher.tv.ui.theme.LauncherColors

/**
 * Guards against hardware TV remote key bounce, duplicate key/pointer triggers,
 * and a held OK button leaking into a newly opened long-press dialog.
 */
object TvInputGuard {
    @Volatile
    private var lastLongPressUptimeMs: Long = 0L

    fun markLongPressHandled() {
        lastLongPressUptimeMs = SystemClock.uptimeMillis()
    }

    fun isInLongPressCooldown(): Boolean {
        return SystemClock.uptimeMillis() - lastLongPressUptimeMs < 550L
    }

    fun isConfirmKeyCode(keyCode: Int): Boolean {
        return keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
            keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
            keyCode == AndroidKeyEvent.KEYCODE_SPACE
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tvCombinedClickable(
    interactionSource: MutableInteractionSource,
    onFocusStateChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnFocusChanged by rememberUpdatedState(onFocusStateChanged)

    var pressedKeyCode by remember { mutableStateOf<Int?>(null) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var longPressTriggered by remember { mutableStateOf(false) }
    var lastActionUptimeMs by remember { mutableLongStateOf(0L) }

    fun triggerClickOnce() {
        val now = SystemClock.uptimeMillis()
        if (now - lastActionUptimeMs < 380L) return
        lastActionUptimeMs = now
        currentOnClick()
    }

    fun triggerLongClickOnce() {
        val lc = currentOnLongClick ?: return
        val now = SystemClock.uptimeMillis()
        if (now - lastActionUptimeMs < 550L) return
        lastActionUptimeMs = now
        TvInputGuard.markLongPressHandled()
        lc()
    }

    this
        .onFocusChanged { state ->
            if (!state.isFocused) {
                longPressJob?.cancel()
                longPressJob = null
                pressedKeyCode = null
                longPressTriggered = false
            }
            currentOnFocusChanged(state.isFocused)
        }
        .onPreviewKeyEvent { ev ->
            val code = ev.nativeKeyEvent.keyCode
            if (!TvInputGuard.isConfirmKeyCode(code)) {
                return@onPreviewKeyEvent false
            }
            when (ev.type) {
                KeyEventType.KeyDown -> {
                    val repeat = ev.nativeKeyEvent.repeatCount
                    if (repeat == 0) {
                        pressedKeyCode = code
                        longPressTriggered = false
                        longPressJob?.cancel()
                        if (currentOnLongClick != null) {
                            longPressJob = scope.launch {
                                delay(480L)
                                if (pressedKeyCode == code && !longPressTriggered) {
                                    longPressTriggered = true
                                    triggerLongClickOnce()
                                }
                            }
                        }
                    } else if (
                        pressedKeyCode == code &&
                        currentOnLongClick != null &&
                        !longPressTriggered &&
                        (ev.nativeKeyEvent.isLongPress || repeat >= 6)
                    ) {
                        longPressJob?.cancel()
                        longPressJob = null
                        longPressTriggered = true
                        triggerLongClickOnce()
                    }
                    true
                }
                KeyEventType.KeyUp -> {
                    longPressJob?.cancel()
                    longPressJob = null
                    val wasPressedHere = pressedKeyCode == code
                    val alreadyLongPressed = longPressTriggered
                    pressedKeyCode = null
                    longPressTriggered = false
                    if (wasPressedHere && !alreadyLongPressed) {
                        triggerClickOnce()
                    }
                    true
                }
                else -> false
            }
        }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = { triggerClickOnce() },
            onLongClick = if (onLongClick != null) {
                { triggerLongClickOnce() }
            } else {
                null
            }
        )
}

@Composable
fun rememberLedBorderColor(active: Boolean): Color {
    if (!active) return Color.Transparent
    val transition = rememberInfiniteTransition(label = "led_border")
    val color by transition.animateColor(
        initialValue = LauncherColors.LedCycle[0],
        targetValue = LauncherColors.LedCycle[4],
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2400
                LauncherColors.LedCycle[0] at 0 using LinearEasing
                LauncherColors.LedCycle[1] at 600 using LinearEasing
                LauncherColors.LedCycle[2] at 1200 using LinearEasing
                LauncherColors.LedCycle[3] at 1800 using LinearEasing
                LauncherColors.LedCycle[4] at 2400 using LinearEasing
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "led_color"
    )
    return color
}

fun glassSurfaceBrush(theme: LauncherTheme): Brush = if (theme == LauncherTheme.MAC_OS) {
    Brush.verticalGradient(
        colors = listOf(Color(0x2EFFFFFF), Color(0x1AFFFFFF))
    )
} else {
    Brush.verticalGradient(
        colors = listOf(LauncherColors.glassTop(theme), LauncherColors.glassBottom(theme))
    )
}

fun barSurfaceBrush(theme: LauncherTheme): Brush = if (theme == LauncherTheme.MAC_OS) {
    Brush.verticalGradient(
        colors = listOf(Color(0x26FFFFFF), Color(0x18FFFFFF))
    )
} else {
    Brush.verticalGradient(
        colors = listOf(
            LauncherColors.glassTop(theme).copy(alpha = 0.40f),
            LauncherColors.glassBottom(theme).copy(alpha = 0.55f)
        )
    )
}

@Composable
fun LedFocusTile(
    modifier: Modifier = Modifier,
    theme: LauncherTheme,
    cornerRadius: Dp = 14.dp,
    focusScale: Float = 1.18f,
    showOuterRing: Boolean = false,
    testTag: String? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocusedByState by interactionSource.collectIsFocusedAsState()
    var isFocused by remember { mutableStateOf(false) }
    val focused = isFocused || isFocusedByState

    val scale by animateFloatAsState(
        targetValue = if (focused) focusScale else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tile_scale"
    )
    val ledColor = rememberLedBorderColor(focused)
    val shape: Shape = RoundedCornerShape(cornerRadius)

    LaunchedEffect(focused) {
        if (focused) onFocused()
    }

    val ringModifier = if (showOuterRing) {
        Modifier
            .background(
                brush = Brush.verticalGradient(listOf(Color(0x2AFFFFFF), Color(0x12FFFFFF))),
                shape = RoundedCornerShape(cornerRadius + 4.dp)
            )
            .border(1.dp, Color(0x48FFFFFF), RoundedCornerShape(cornerRadius + 4.dp))
            .padding(4.dp)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .zIndex(if (focused) 10f else 0f)
            .scale(scale)
            .then(ringModifier)
            .drawBehind {
                if (focused) {
                    val cr = cornerRadius.toPx()
                    drawRoundRect(
                        color = ledColor.copy(alpha = 0.28f),
                        cornerRadius = CornerRadius(cr, cr),
                        style = Stroke(width = 8.dp.toPx())
                    )
                    drawRoundRect(
                        color = ledColor,
                        cornerRadius = CornerRadius(cr, cr),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
            .clip(shape)
            .background(brush = glassSurfaceBrush(theme), shape = shape)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0x73FFFFFF), Color(0x26FFFFFF))
                ),
                shape = shape
            )
            .tvCombinedClickable(
                interactionSource = interactionSource,
                onFocusStateChanged = { isFocused = it },
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        content(focused)
    }
}

@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    theme: LauncherTheme,
    testTag: String? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.07f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pill_scale"
    )
    val accent = LauncherColors.accent(theme)
    val shape = RoundedCornerShape(40.dp)

    LaunchedEffect(isFocused) {
        if (isFocused) onFocused()
    }

    val bgBrush = if (isFocused) {
        Brush.verticalGradient(listOf(accent.copy(alpha = 0.95f), accent))
    } else if (theme == LauncherTheme.MAC_OS) {
        Brush.verticalGradient(listOf(Color(0x36FFFFFF), Color(0x22FFFFFF)))
    } else {
        Brush.verticalGradient(listOf(LauncherColors.glassTop(theme), LauncherColors.glassBottom(theme)))
    }

    Box(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .scale(scale)
            .clip(shape)
            .background(bgBrush, shape)
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x55FFFFFF),
                shape = shape
            )
            .tvCombinedClickable(
                interactionSource = interactionSource,
                onFocusStateChanged = { isFocused = it },
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        content(isFocused)
    }
}
