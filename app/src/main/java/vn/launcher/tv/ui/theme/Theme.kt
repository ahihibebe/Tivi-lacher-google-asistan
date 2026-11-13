package vn.launcher.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import vn.launcher.tv.model.LauncherTheme

object LauncherColors {
    val BackgroundBase = Color(0xFF0D0D10)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xCCFFFFFF)
    val TextMuted = Color(0x99FFFFFF)
    val AlertRed = Color(0xFFFF5252)
    val HeartRed = Color(0xFFFF3B30)
    val ToggleGreen = Color(0xFF34C759)
    val ToggleOff = Color(0x66787880)

    val LedCycle = listOf(
        Color(0xFFFF3B30),
        Color(0xFFFFD60A),
        Color(0xFF30D158),
        Color(0xFFFFFFFF),
        Color(0xFFFF3B30)
    )

    val TabDots = listOf(
        Color(0xFFFF5F57), // Apps
        Color(0xFFFEBC2E), // Recently opened
        Color(0xFF28C840)  // Most used
    )

    val SettingsPalette = listOf(
        Color(0xFF8E8E93),
        Color(0xFF0A84FF),
        Color(0xFF30B0C7),
        Color(0xFFAF52DE),
        Color(0xFF34C759),
        Color(0xFFFF453A),
        Color(0xFFFF9F0A),
        Color(0xFF64D2FF),
        Color(0xFFFF375F),
        Color(0xFF5E5CE6)
    )

    val PresetGlows = listOf(
        Color(0xAAB71C1C), // Red (default)
        Color(0xAA1565C0), // Blue
        Color(0xAA6A1B9A), // Purple
        Color(0xAA2E7D32), // Green
        Color.Transparent   // Black
    )

    fun accent(theme: LauncherTheme): Color = when (theme) {
        LauncherTheme.MAC_OS -> Color(0xFF0A84FF)
        LauncherTheme.TULIP_BLUE -> Color(0xFF2AA7F0)
        LauncherTheme.TULIP_PURPLE -> Color(0xFF9E6DF0)
    }

    fun glassTop(theme: LauncherTheme): Color = when (theme) {
        LauncherTheme.MAC_OS -> Color(0x33FFFFFF)
        LauncherTheme.TULIP_BLUE -> Color(0x8C4FA3E0)
        LauncherTheme.TULIP_PURPLE -> Color(0x8CA27BE8)
    }

    fun glassBottom(theme: LauncherTheme): Color = when (theme) {
        LauncherTheme.MAC_OS -> Color(0x1FFFFFFF)
        LauncherTheme.TULIP_BLUE -> Color(0xB32F72BC)
        LauncherTheme.TULIP_PURPLE -> Color(0xB36A45B8)
    }

    fun windowTop(theme: LauncherTheme): Color = when (theme) {
        LauncherTheme.MAC_OS -> Color(0xEB2B2B2E)
        LauncherTheme.TULIP_BLUE -> Color(0xE04A86C4)
        LauncherTheme.TULIP_PURPLE -> Color(0xE08562C8)
    }

    fun windowBottom(theme: LauncherTheme): Color = when (theme) {
        LauncherTheme.MAC_OS -> Color(0xEB242427)
        LauncherTheme.TULIP_BLUE -> Color(0xEB2B5C99)
        LauncherTheme.TULIP_PURPLE -> Color(0xEB5A3A9C)
    }
}

private val VietLauncherTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 48.sp,
        letterSpacing = (-1).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp
    )
)

@Composable
fun VietLauncherTheme(
    theme: LauncherTheme = LauncherTheme.MAC_OS,
    content: @Composable () -> Unit
) {
    val accent = LauncherColors.accent(theme)
    val colorScheme = darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        secondary = Color(0xFF64D2FF),
        background = LauncherColors.BackgroundBase,
        surface = Color(0xFF1C1C1E),
        surfaceVariant = Color(0xFF2C2C2E),
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = Color(0xCCFFFFFF),
        error = LauncherColors.AlertRed
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VietLauncherTypography,
        content = content
    )
}
