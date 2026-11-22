package vn.launcher.tv.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import vn.launcher.tv.R
import vn.launcher.tv.model.AppItem
import vn.launcher.tv.model.ClockFontStyle
import vn.launcher.tv.model.LauncherTheme
import vn.launcher.tv.ui.components.LedFocusTile
import vn.launcher.tv.ui.components.tvDialogClickable
import vn.launcher.tv.ui.theme.LauncherColors
import java.io.File

@Composable
internal fun LauncherBackground(
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
internal fun PresetGlowCanvas(idx: Int, alpha: Float) {
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

internal fun clockFontFamily(style: ClockFontStyle): FontFamily = when (style) {
    ClockFontStyle.DEFAULT -> FontFamily.SansSerif
    ClockFontStyle.OUTFIT -> FontFamily(Font(R.font.outfit))
    ClockFontStyle.ORBITRON -> FontFamily(Font(R.font.orbitron))
    ClockFontStyle.JETBRAINS_MONO -> FontFamily(Font(R.font.jetbrains_mono))
    ClockFontStyle.PLAYFAIR -> FontFamily(Font(R.font.playfair_display))
}

internal data class MiniCardSpec(
    val dotColor: Color,
    val label: String,
    val badge: String,
    val mainValue: String,
    val subValue: String,
    val progress: Float,
    val expanded: Boolean = false,
    val sourceIcon: ImageVector? = null,
    val sourceIconTint: Color = Color(0xFF30D158),
    val sourceContentDescription: String? = null,
    val mainIcon: ImageVector? = null,
    val mainIconTint: Color = Color(0xFFFF9F0A),
    val subIcon: ImageVector? = null,
    val subIconTint: Color = Color(0xFF38BDF8)
)

@Composable
internal fun MiniStatCard(
    spec: MiniCardSpec,
    modifier: Modifier = Modifier
) {
    val corner = if (spec.expanded) 11.dp else 9.dp
    val hPad = if (spec.expanded) 7.dp else 5.dp
    val vPad = if (spec.expanded) 4.5.dp else 3.5.dp

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(Color(0x1EFFFFFF), RoundedCornerShape(corner))
            .border(1.dp, Color(0x42FFFFFF), RoundedCornerShape(corner))
            .padding(horizontal = hPad, vertical = vPad)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(if (spec.expanded) 5.5.dp else 5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(spec.dotColor)
            )
            Spacer(Modifier.width(if (spec.expanded) 3.5.dp else 3.dp))
            Text(
                text = spec.label,
                color = Color(0xD9FFFFFF),
                fontSize = if (spec.expanded) 7.8.sp else 6.8.sp,
                lineHeight = if (spec.expanded) 9.5.sp else 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = if (spec.expanded) 0.2.sp else 0.1.sp,
                maxLines = if (spec.expanded) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (spec.sourceIcon != null) {
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = spec.sourceIcon,
                    contentDescription = spec.sourceContentDescription,
                    tint = spec.sourceIconTint,
                    modifier = Modifier.size(9.5.dp)
                )
            }
            if (spec.badge.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = spec.badge,
                    color = Color(0xCCFFFFFF),
                    fontSize = if (spec.expanded) 7.2.sp else 6.8.sp,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(if (spec.expanded) 2.dp else 1.5.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (spec.expanded) 3.dp else 2.5.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (spec.mainIcon != null) {
                Icon(
                    imageVector = spec.mainIcon,
                    contentDescription = null,
                    tint = spec.mainIconTint,
                    modifier = Modifier.size(if (spec.expanded) 12.dp else 9.5.dp)
                )
            }
            Text(
                text = spec.mainValue,
                color = Color.White,
                fontSize = when {
                    spec.expanded -> 13.sp
                    spec.subIcon != null -> 9.8.sp
                    else -> 10.8.sp
                },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            if (spec.subValue.isNotEmpty()) {
                if (spec.subIcon != null) {
                    Spacer(Modifier.width(1.dp))
                    Icon(
                        imageVector = spec.subIcon,
                        contentDescription = null,
                        tint = spec.subIconTint,
                        modifier = Modifier.size(if (spec.expanded) 10.5.dp else 9.5.dp)
                    )
                }
                Text(
                    text = spec.subValue,
                    color = if (spec.subIcon != null) Color.White else Color(0xD9FFFFFF),
                    fontSize = when {
                        spec.expanded -> 8.2.sp
                        spec.subIcon != null -> 9.8.sp
                        else -> 7.sp
                    },
                    fontWeight = if (spec.subIcon != null) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.weight(1f, fill = true))
        Spacer(Modifier.height(if (spec.expanded) 3.dp else 2.dp))
        val barH = if (spec.expanded) 3.dp else 2.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barH)
                .clip(RoundedCornerShape(1.5.dp))
                .background(Color(0x30FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(spec.progress.coerceIn(0f, 1f))
                    .height(barH)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(spec.dotColor)
            )
        }
    }
}

@Composable
internal fun DockTabDot(
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
internal fun AppTileCard(
    app: AppItem,
    theme: LauncherTheme,
    showOuterRing: Boolean,
    isMoving: Boolean,
    testTag: String,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusScale: Float = 1.07f,
    showFocusedLabelOnTop: Boolean = false
) {
    val bmp: ImageBitmap? = remember(app.pkg, app.isBanner) {
        drawableToImageBitmap(app.image)
    }
    var tileFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .onFocusChanged { tileFocused = it.hasFocus }
            .zIndex(if (tileFocused) 20f else 0f)
    ) {
        LedFocusTile(
            modifier = Modifier.fillMaxSize(),
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

        if (showFocusedLabelOnTop && tileFocused) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-13).dp)
                    .zIndex(30f)
                    .widthIn(max = 176.dp)
                    .clip(CircleShape)
                    .background(Color(0x880B0F1A), CircleShape)
                    .border(1.dp, Color(0xB8FFFFFF), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = app.label,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

internal fun drawableToImageBitmap(drawable: Drawable?): ImageBitmap? {
    if (drawable == null) return null
    return try {
        val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 192
        val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 108
        drawable.toBitmap(w.coerceAtMost(384), h.coerceAtMost(384), Bitmap.Config.ARGB_8888).asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}
