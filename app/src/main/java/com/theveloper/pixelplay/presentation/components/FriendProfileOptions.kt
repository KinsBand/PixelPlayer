package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.MotionTokens
import kotlinx.coroutines.launch
import androidx.core.graphics.drawable.toBitmap
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/** Top-right of a followed person's header card: opens [FriendOptionsSheet]. */
@Composable
internal fun ProfileOptionsButton(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.friend_options_cd, name))
    }
}

/** Top-right of someone you don't follow: Follow. */
@Composable
internal fun ProfileFollowButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(onClick = onClick, modifier = modifier.heightIn(min = 40.dp)) {
        Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.friend_history_follow))
    }
}

/**
 * A person's options: Follow / Unfollow, Block and Hide side by side, and (for a friend in your
 * list) Play along under them. Block asks once more on the same tile before it acts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FriendOptionsSheet(
    name: String,
    /** Following them on the platform; null = the platform has no follow (the tile is off). */
    following: Boolean?,
    onToggleFollow: () -> Unit,
    onBlock: () -> Unit,
    onHide: () -> Unit,
    /** Play along with them (songs they start play next); null = not offered here. */
    playingAlong: Boolean?,
    onTogglePlayAlong: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: (() -> Unit) -> Unit = { action ->
        action()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    var blockArmed by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        com.theveloper.pixelplay.utils.KeepSystemBarsHiddenInDialog()
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                name,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OptionTile(
                    icon = if (following == true) Icons.Rounded.PersonRemove else Icons.Rounded.PersonAdd,
                    label = stringResource(if (following == true) R.string.friend_options_unfollow else R.string.friend_history_follow),
                    enabled = following != null,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { close(onToggleFollow) },
                    modifier = Modifier.weight(1f)
                )
                OptionTile(
                    icon = Icons.Rounded.Block,
                    label = stringResource(if (blockArmed) R.string.friend_options_block_confirm else R.string.friend_options_block),
                    enabled = true,
                    container = if (blockArmed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.errorContainer,
                    content = if (blockArmed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onErrorContainer,
                    onClick = { if (blockArmed) close(onBlock) else blockArmed = true },
                    modifier = Modifier.weight(1f)
                )
                OptionTile(
                    icon = Icons.Rounded.VisibilityOff,
                    label = stringResource(R.string.friend_options_hide),
                    enabled = true,
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurface,
                    onClick = { close(onHide) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (playingAlong != null) {
                Surface(
                    onClick = onTogglePlayAlong,
                    shape = AbsoluteSmoothCornerShape(20.dp, 60),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.friend_options_play_along), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.friend_history_follow_hint, name),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = playingAlong, onCheckedChange = { onTogglePlayAlong() })
                    }
                }
            }
            AnimatedVisibility(blockArmed) {
                Text(
                    stringResource(R.string.friend_options_block_body, name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OptionTile(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = AbsoluteSmoothCornerShape(22.dp, 60),
        color = if (enabled) container else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        contentColor = if (enabled) content else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        modifier = modifier.heightIn(min = 88.dp)
    ) {
        Column(
            Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = GoogleSansRounded),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Someone's picture, full screen: pinch to zoom (1–5×), drag to move, double-tap to zoom in / reset,
 * tap the background or Close to leave. Download saves it to Pictures/PixelPlayer.
 */
@Composable
internal fun AvatarViewer(name: String, imageUrl: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var saving by remember { mutableStateOf(false) }
    val enter = remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        enter.animateTo(1f, tween(MotionTokens.DurationMedium2, easing = MotionTokens.EmphasizedDecelerate))
    }
    val savedText = stringResource(R.string.avatar_viewer_saved)
    val failedText = stringResource(R.string.avatar_viewer_save_failed)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = enter.value }
                .background(Color.Black.copy(alpha = 0.94f))
                .pointerInput(Unit) { detectTapGestures(onTap = { if (scale <= 1.01f) onDismiss() }) }
        ) {
            SmartImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                // Full resolution: it fills the screen and can be zoomed.
                targetSize = coil.size.Size.ORIGINAL,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer {
                        val e = 0.86f + 0.14f * enter.value
                        scaleX = scale * e
                        scaleY = scale * e
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val next = (scale * zoom).coerceIn(1f, 5f)
                            scale = next
                            // Keep the picture roughly on screen while zoomed.
                            val limit = (next - 1f) * size.width / 2f
                            offset = if (next <= 1.01f) Offset.Zero else Offset(
                                (offset.x + pan.x).coerceIn(-limit, limit),
                                (offset.y + pan.y).coerceIn(-limit, limit)
                            )
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1.01f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                        })
                    }
            )
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.common_close), tint = Color.White)
                }
                Text(
                    name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    enabled = !saving,
                    onClick = {
                        saving = true
                        scope.launch {
                            val ok = saveImageToGallery(context, imageUrl, "PixelPlayer_" + name.filter { it.isLetterOrDigit() }.take(40))
                            saving = false
                            android.widget.Toast.makeText(context, if (ok) savedText else failedText, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = "Download" }
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}

/** Loads [url] (from Coil's cache when it's there) and saves it to Pictures/PixelPlayer. */
private suspend fun saveImageToGallery(context: android.content.Context, url: String, name: String): Boolean {
    val result = runCatching {
        context.imageLoaderForSave().execute(
            coil.request.ImageRequest.Builder(context).data(url).allowHardware(false).build()
        )
    }.getOrNull() as? coil.request.SuccessResult ?: return false
    val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        ?: runCatching { result.drawable.toBitmap() }.getOrNull()
        ?: return false
    return com.theveloper.pixelplay.presentation.screens.stats.share.StatsShareExporter.saveToGallery(
        context,
        com.theveloper.pixelplay.presentation.screens.stats.share.StatsShareExporter.toSoftware(bitmap),
        name
    )
}

private fun android.content.Context.imageLoaderForSave(): coil.ImageLoader = coil.Coil.imageLoader(this)
