package com.theveloper.pixelplay.presentation.components

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size // Import Coil's Size
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.theveloper.pixelplay.R

val SmartImageCompactListTargetSize = Size(96, 96)
val SmartImageListTargetSize = Size(128, 128)
private val DefaultSmartImageSize = Size(300, 300)

@Composable
fun SmartImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderResId: Int = R.drawable.ic_music_placeholder,
    errorResId: Int = R.drawable.ic_music_placeholder,
    shape: Shape = RectangleShape,
    contentScale: ContentScale = ContentScale.Crop,
    crossfadeDurationMillis: Int = 100,
    useDiskCache: Boolean = true,
    useMemoryCache: Boolean = true,
    allowHardware: Boolean = false,
    targetSize: Size = DefaultSmartImageSize,
    colorFilter: ColorFilter? = null,
    alpha: Float = 1f,
    placeholderModel: Any? = null,
    placeHolderBackgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    songId: String? = null,
    isDownloading: Boolean = false,
    indicatorSize: Dp? = null,
    onState: ((AsyncImagePainter.State) -> Unit)? = null
) {
    val context = LocalContext.current
    val clippedModifier = modifier.clip(shape)
    val requestTargetSize = remember(targetSize) {
        safeAlbumArtTargetSize(targetSize)
    }
    val downloading = isDownloading || (songId != null && LocalDownloadingSongIds.current.contains(songId))

    val effectiveModel = if (model is String && model.isBlank()) null else model

    val imageContent = @Composable { imageModifier: Modifier ->
        if (effectiveModel == null || effectiveModel is ImageVector || effectiveModel is Painter || effectiveModel is ImageBitmap || effectiveModel is Bitmap) {
            if (effectiveModel == null) {
                Placeholder(
                    modifier = imageModifier,
                    drawableResId = placeholderResId,
                    contentDescription = contentDescription,
                    containerColor = placeHolderBackgroundColor,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    alpha = alpha
                )
            } else {
                handleDirectModel(
                    data = effectiveModel,
                    modifier = imageModifier,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    colorFilter = colorFilter,
                    alpha = alpha
                )
            }
        } else {
            val request = remember(
                context,
                effectiveModel,
                crossfadeDurationMillis,
                useDiskCache,
                useMemoryCache,
                allowHardware,
                requestTargetSize
            ) {
                if (model is ImageRequest) {
                    model.newBuilder(context)
                        .size(requestTargetSize)
                        .build()
                } else {
                    ImageRequest.Builder(context)
                        .data(model)
                        .crossfade(crossfadeDurationMillis)
                        .diskCachePolicy(if (useDiskCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
                        .memoryCachePolicy(if (useMemoryCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
                        .allowHardware(allowHardware)
                        .size(requestTargetSize)
                        .build()
                }
            }

            if (onState != null || placeholderModel != null) {
                SubcomposeAsyncImage(
                    model = request,
                    contentDescription = contentDescription,
                    modifier = imageModifier,
                    contentScale = contentScale,
                    colorFilter = colorFilter,
                    alpha = alpha
                ) {
                    val state = painter.state
                    LaunchedEffect(state) {
                        onState?.invoke(state)
                    }

                    when (state) {
                        is AsyncImagePainter.State.Success -> {
                            SubcomposeAsyncImageContent()
                        }
                        is AsyncImagePainter.State.Loading,
                        is AsyncImagePainter.State.Empty -> {
                            if (placeholderModel != null) {
                                AsyncImage(
                                    model = placeholderModel,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = contentScale,
                                    colorFilter = colorFilter,
                                    alpha = alpha
                                )
                            } else {
                                CoverLoadingIndicator(
                                    modifier = Modifier.fillMaxSize().alpha(alpha),
                                    containerColor = placeHolderBackgroundColor,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    indicatorSize = indicatorSize
                                )
                            }
                        }
                        else -> {
                            Placeholder(
                                modifier = Modifier.fillMaxSize(),
                                drawableResId = if (state is AsyncImagePainter.State.Error) errorResId else placeholderResId,
                                contentDescription = contentDescription,
                                containerColor = placeHolderBackgroundColor,
                                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                alpha = alpha
                            )
                        }
                    }
                }
            } else {
                var asyncState by remember(request) {
                    mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
                }
                Box(
                    modifier = imageModifier.background(placeHolderBackgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = request,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = contentScale,
                        colorFilter = colorFilter,
                        alpha = alpha,
                        onState = { state ->
                            asyncState = state
                            onState?.invoke(state)
                        }
                    )
                    when (asyncState) {
                        is AsyncImagePainter.State.Loading,
                        is AsyncImagePainter.State.Empty -> {
                            CoverLoadingIndicator(
                                modifier = Modifier.fillMaxSize().alpha(alpha),
                                containerColor = placeHolderBackgroundColor,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                indicatorSize = indicatorSize
                            )
                        }
                        is AsyncImagePainter.State.Error -> {
                            Placeholder(
                                modifier = Modifier.fillMaxSize(),
                                drawableResId = errorResId,
                                contentDescription = contentDescription,
                                containerColor = placeHolderBackgroundColor,
                                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                alpha = alpha
                            )
                        }
                        is AsyncImagePainter.State.Success -> Unit
                    }
                }
            }
        }
    }

    if (downloading) {
        Box(
            modifier = clippedModifier,
            contentAlignment = Alignment.Center
        ) {
            imageContent(Modifier.fillMaxSize())
            AmbientDownloadCoverOverlay(
                isDownloading = true,
                shape = RectangleShape
            )
        }
    } else {
        imageContent(clippedModifier)
    }
}

@Composable
private fun handleDirectModel(
    data: Any?,
    modifier: Modifier,
    contentDescription: String?,
    contentScale: ContentScale,
    colorFilter: ColorFilter?,
    alpha: Float
): Any? {
    return when (data) {
        is ImageVector -> {
            Image(
                imageVector = data,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
                alpha = alpha
            )
            data
        }
        is Painter -> {
            Image(
                painter = data,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
                alpha = alpha
            )
            data
        }
        is ImageBitmap -> {
            Image(
                bitmap = data,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
                alpha = alpha
            )
            data
        }
        is Bitmap -> {
            Image(
                bitmap = data.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
                alpha = alpha
            )
            data
        }
        else -> null
    }
}

@Composable
fun CoverLoadingIndicator(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    indicatorSize: Dp? = null,
) {
    BoxWithConstraints(
        modifier = modifier.background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        val resolvedIndicatorSize = indicatorSize ?: run {
            val hasValidWidth = maxWidth != androidx.compose.ui.unit.Dp.Unspecified &&
                maxWidth != androidx.compose.ui.unit.Dp.Infinity && maxWidth > 0.dp
            val hasValidHeight = maxHeight != androidx.compose.ui.unit.Dp.Unspecified &&
                maxHeight != androidx.compose.ui.unit.Dp.Infinity && maxHeight > 0.dp
            val containerSize = if (hasValidWidth && hasValidHeight) {
                minOf(maxWidth, maxHeight)
            } else if (hasValidWidth) {
                maxWidth
            } else if (hasValidHeight) {
                maxHeight
            } else {
                48.dp
            }
            (containerSize * 0.45f).coerceIn(20.dp, 36.dp)
        }
        SongCoverLoadingIndicator(
            size = resolvedIndicatorSize,
            color = indicatorColor
        )
    }
}

@Composable
private fun Placeholder(
    modifier: Modifier,
    @DrawableRes drawableResId: Int,
    contentDescription: String?,
    containerColor: Color,
    iconColor: Color,
    alpha: Float,
) {
    BoxWithConstraints(
        modifier = modifier
            .alpha(alpha)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        val hasValidWidth = maxWidth != androidx.compose.ui.unit.Dp.Unspecified &&
            maxWidth != androidx.compose.ui.unit.Dp.Infinity && maxWidth > 0.dp
        val hasValidHeight = maxHeight != androidx.compose.ui.unit.Dp.Unspecified &&
            maxHeight != androidx.compose.ui.unit.Dp.Infinity && maxHeight > 0.dp
        val containerSize = if (hasValidWidth && hasValidHeight) {
            minOf(maxWidth, maxHeight)
        } else if (hasValidWidth) {
            maxWidth
        } else if (hasValidHeight) {
            maxHeight
        } else {
            48.dp
        }
        val iconSize = (containerSize * 0.5f).coerceIn(16.dp, 32.dp)
        Image(
            painter = painterResource(drawableResId),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(iconColor),
            modifier = Modifier.size(iconSize),
            contentScale = ContentScale.Fit
        )
    }
}
