package vn.launcher.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import vn.launcher.tv.data.MediaLibrary
import vn.launcher.tv.model.LauncherTheme
import vn.launcher.tv.model.MusicPlayerState
import vn.launcher.tv.model.SlideshowState
import vn.launcher.tv.ui.components.MacWindowDialog
import vn.launcher.tv.ui.theme.LauncherColors

@Composable
fun MusicPanelDialog(
    music: MusicPlayerState,
    theme: LauncherTheme,
    isEnglish: Boolean,
    onPrev: () -> Unit,
    onTogglePlay: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onSelectTrack: (Int) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentTrack = music.tracks.getOrNull(music.currentIndex)
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try {
            playFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    MacWindowDialog(
        title = if (isEnglish) "Music player" else "Trình phát nhạc",
        theme = theme,
        maxWidth = 700.dp,
        scrollable = false,
        onDismiss = onDismiss,
        headerActions = {
            HeaderChipButton(
                icon = Icons.Filled.Refresh,
                label = if (isEnglish) "Refresh" else "Làm mới",
                onClick = onRefresh
            )
        }
    ) {
        // Header with Cover Art + Info + Progress + Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x26FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                if (music.coverArt != null) {
                    Image(
                        bitmap = music.coverArt.asImageBitmap(),
                        contentDescription = currentTrack?.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color(0xCCFFFFFF),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTrack?.title ?: (if (isEnglish) "No track selected" else "Chưa chọn bài hát"),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (currentTrack != null && currentTrack.artist.isNotEmpty()) {
                    Text(
                        text = currentTrack.artist,
                        color = Color(0xB3FFFFFF),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(10.dp))

                val progress = if (music.durationMs > 0L) {
                    (music.positionMs.toFloat() / music.durationMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { progress },
                    color = LauncherColors.accent(theme),
                    trackColor = Color(0x33FFFFFF),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = MediaLibrary.formatTime(music.positionMs),
                        color = Color(0x99FFFFFF),
                        fontSize = 11.sp
                    )
                    Text(
                        text = MediaLibrary.formatTime(music.durationMs),
                        color = Color(0x99FFFFFF),
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerControlIcon(Icons.Filled.SkipPrevious, "Previous", theme, onClick = onPrev)
                    PlayerControlIcon(
                        icon = if (music.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDesc = if (music.isPlaying) "Pause" else "Play",
                        theme = theme,
                        modifier = Modifier.focusRequester(playFocus),
                        onClick = onTogglePlay
                    )
                    PlayerControlIcon(Icons.Filled.Stop, "Stop", theme, onClick = onStop)
                    PlayerControlIcon(Icons.Filled.SkipNext, "Next", theme, onClick = onNext)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (music.tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isEnglish) {
                        "No songs found.\nPlug in a USB drive or copy music to the TV, then press Refresh."
                    } else {
                        "Không tìm thấy bài hát.\nCắm USB hoặc chép nhạc vào TV rồi bấm nút Làm mới."
                    },
                    color = Color(0x99FFFFFF),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                itemsIndexed(music.tracks) { index, track ->
                    val current = index == music.currentIndex
                    var focused by remember { mutableStateOf(false) }
                    val shape = RoundedCornerShape(10.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(shape)
                            .background(
                                when {
                                    focused -> LauncherColors.accent(theme)
                                    current -> Color(0x26FFFFFF)
                                    else -> Color.Transparent
                                },
                                shape
                            )
                            .onFocusChanged { focused = it.isFocused }
                            .focusable()
                            .clickable { onSelectTrack(index) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (current) "♪" else "${index + 1}",
                            color = if (current && !focused) Color(0xFFFF375F) else Color(0xCCFFFFFF),
                            fontSize = 12.sp,
                            modifier = Modifier.width(30.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = if (current && !focused) Color(0xFFFF375F) else Color.White,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (track.artist.isNotEmpty()) {
                                Text(
                                    text = track.artist,
                                    color = Color(0x99FFFFFF),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (track.duration > 0L) {
                            Text(
                                text = MediaLibrary.formatTime(track.duration),
                                color = Color(0x99FFFFFF),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderChipButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(if (focused) Color(0xFF0A84FF) else Color(0x26FFFFFF), shape)
            .border(1.dp, if (focused) Color.White else Color(0x33FFFFFF), shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun PlayerControlIcon(
    icon: ImageVector,
    contentDesc: String,
    theme: LauncherTheme,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (focused) LauncherColors.accent(theme) else Color(0x28FFFFFF))
            .border(if (focused) 1.5.dp else 0.5.dp, if (focused) Color.White else Color(0x33FFFFFF), CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDesc, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun FullscreenSlideshowDialog(
    slideshow: SlideshowState,
    theme: LauncherTheme,
    onStep: (Int) -> Unit,
    onTogglePause: () -> Unit,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler(onBack = onClose)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusable()
                .onKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (ev.key) {
                        Key.DirectionLeft -> {
                            onStep(-1)
                            true
                        }
                        Key.DirectionRight -> {
                            onStep(1)
                            true
                        }
                        Key.DirectionCenter, Key.Enter -> {
                            onTogglePause()
                            true
                        }
                        Key.Back, Key.Escape -> {
                            onClose()
                            true
                        }
                        else -> false
                    }
                }
        ) {
            Crossfade(
                targetState = slideshow.fullscreenBitmap,
                animationSpec = tween(400),
                label = "fs_photo"
            ) { bm ->
                if (bm != null) {
                    Image(
                        bitmap = bm.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (slideshow.uris.isNotEmpty()) {
                Text(
                    text = "${slideshow.fullscreenIndex + 1} / ${slideshow.uris.size}",
                    color = Color(0xB3FFFFFF),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp, bottom = 18.dp)
                )
            }

            // Touch/remote control bar at bottom-right
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PlayerControlIcon(Icons.Filled.SkipPrevious, "Previous", theme, onClick = { onStep(-1) })
                PlayerControlIcon(
                    icon = if (slideshow.fullscreenPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDesc = if (slideshow.fullscreenPaused) "Play" else "Pause",
                    theme = theme,
                    onClick = onTogglePause
                )
                PlayerControlIcon(Icons.Filled.SkipNext, "Next", theme, onClick = { onStep(1) })
                PlayerControlIcon(Icons.Filled.Close, "Close", theme, onClick = onClose)
            }
        }
    }
}
