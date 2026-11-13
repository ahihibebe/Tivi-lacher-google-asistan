package vn.launcher.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import vn.launcher.tv.model.LauncherTheme
import vn.launcher.tv.ui.theme.LauncherColors

@Composable
fun MacWindowDialog(
    title: String,
    theme: LauncherTheme,
    maxWidth: Dp = 560.dp,
    onDismiss: () -> Unit,
    headerActions: (@Composable () -> Unit)? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.84f).dp
    val corner = if (theme == LauncherTheme.MAC_OS) 22.dp else 26.dp
    val shape = RoundedCornerShape(corner)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth(0.92f)
                .heightIn(max = maxH)
                .clip(shape)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(LauncherColors.windowTop(theme), LauncherColors.windowBottom(theme))
                    ),
                    shape = shape
                )
                .border(1.dp, Color(0x40FFFFFF), shape)
        ) {
            // macOS title bar with traffic dots + title + optional header buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x18FFFFFF))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF5F57))
                            .clickable { onDismiss() }
                    )
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEBC2E))
                    )
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF28C840))
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                headerActions?.invoke()
            }

            if (scrollable) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    content = content
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun MacSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        color = Color(0x99FFFFFF),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
fun MacRow(
    title: String,
    theme: LauncherTheme,
    icon: ImageVector? = null,
    iconTintBg: Color = LauncherColors.SettingsPalette[1],
    trailing: String? = null,
    showChevron: Boolean = false,
    testTag: String? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val accent = LauncherColors.accent(theme)
    val shape = RoundedCornerShape(12.dp)

    LaunchedEffect(focused) {
        if (focused) onFocused()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clip(shape)
            .background(if (focused) accent else Color(0x1AFFFFFF), shape)
            .border(
                width = if (focused) 1.5.dp else 0.5.dp,
                color = if (focused) Color.White else Color(0x22FFFFFF),
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(if (theme == LauncherTheme.MAC_OS) RoundedCornerShape(7.dp) else CircleShape)
                    .background(iconTintBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!trailing.isNullOrEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailing,
                color = if (focused) Color.White else Color(0xB3FFFFFF),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showChevron) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (focused) Color.White else Color(0x88FFFFFF),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun MacToggleRow(
    title: String,
    checked: Boolean,
    theme: LauncherTheme,
    isEnglish: Boolean,
    icon: ImageVector? = null,
    iconTintBg: Color = LauncherColors.SettingsPalette[8],
    testTag: String? = null,
    onFocused: () -> Unit = {},
    onToggle: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val accent = LauncherColors.accent(theme)
    val shape = RoundedCornerShape(12.dp)

    LaunchedEffect(focused) {
        if (focused) onFocused()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clip(shape)
            .background(if (focused) accent else Color(0x1AFFFFFF), shape)
            .border(
                width = if (focused) 1.5.dp else 0.5.dp,
                color = if (focused) Color.White else Color(0x22FFFFFF),
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(if (theme == LauncherTheme.MAC_OS) RoundedCornerShape(7.dp) else CircleShape)
                    .background(iconTintBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (checked) LauncherColors.ToggleGreen else LauncherColors.ToggleOff)
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (checked) (if (isEnglish) "On" else "Bật") else (if (isEnglish) "Off" else "Tắt"),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MacActionButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(9.dp)

    LaunchedEffect(focused) {
        if (focused) onFocused()
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(color, shape)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MacEditDialog(
    title: String,
    message: String?,
    hints: List<String>,
    initialValues: List<String>,
    theme: LauncherTheme,
    isEnglish: Boolean,
    onSave: (List<String>) -> Unit,
    onDefault: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val values = remember(title, initialValues) {
        mutableStateListOf<String>().apply { addAll(initialValues) }
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    MacWindowDialog(
        title = title,
        theme = theme,
        maxWidth = 560.dp,
        onDismiss = onDismiss
    ) {
        if (!message.isNullOrBlank()) {
            Text(
                text = message,
                color = Color(0xB3FFFFFF),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(6.dp))
        }
        hints.forEachIndexed { index, hint ->
            OutlinedTextField(
                value = values.getOrElse(index) { "" },
                onValueChange = { if (index < values.size) values[index] = it },
                label = { Text(hint) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = LauncherColors.accent(theme),
                    unfocusedBorderColor = Color(0x44FFFFFF),
                    focusedLabelColor = LauncherColors.accent(theme),
                    unfocusedLabelColor = Color(0x99FFFFFF)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier)
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (onDefault != null) {
                MacActionButton(
                    text = if (isEnglish) "Default" else "Mặc định",
                    color = Color(0x33FFFFFF),
                    onClick = {
                        onDefault()
                        onDismiss()
                    }
                )
                Spacer(Modifier.width(10.dp))
            }
            MacActionButton(
                text = if (isEnglish) "Cancel" else "Hủy",
                color = Color(0x33FFFFFF),
                onClick = onDismiss
            )
            Spacer(Modifier.width(10.dp))
            MacActionButton(
                text = if (isEnglish) "Save" else "Lưu",
                color = LauncherColors.accent(theme),
                onClick = {
                    onSave(values.map { it.trim() })
                    onDismiss()
                }
            )
        }
    }
}
