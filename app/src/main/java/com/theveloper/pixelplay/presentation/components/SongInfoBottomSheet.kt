package com.theveloper.pixelplay.presentation.components

import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.MoreVert
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.withFrameNanos


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.subcomps.AutoSizingTextToFill
import com.theveloper.pixelplay.utils.formatDuration
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.theveloper.pixelplay.data.media.CoverArtUpdate
import com.theveloper.pixelplay.ui.theme.MontserratFamily
import com.theveloper.pixelplay.presentation.viewmodel.SongInfoBottomSheetViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SongInfoBottomSheetViewModel.ToneTarget
import kotlinx.coroutines.launch

import androidx.compose.ui.graphics.TransformOrigin
import com.theveloper.pixelplay.presentation.screens.TabAnimation
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.utils.AudioMetaUtils
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.presentation.components.subcomps.TightWrapText
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun SongInfoBottomSheet(
    song: Song,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    onPlaySong: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddNextToQueue: () -> Unit,
    onAddToPlayList: () -> Unit,
    onDeleteFromDevice: (activity: Activity, song: Song, onResult: (Boolean) -> Unit) -> Unit,
    onNavigateToAlbum: () -> Unit,
    onNavigateToArtist: () -> Unit,
    onNavigateToArtistById: (Long) -> Unit = { onNavigateToArtist() },
    /** Opens an artist profile by name — used for online songs whose artists aren't in the local library. */
    onNavigateToArtistByName: (String) -> Unit = { onNavigateToArtist() },
    onNavigateToGenre: () -> Unit,
    onEditSong: (
        title: String,
        artist: String,
        album: String,
        albumArtist: String,
        composer: String,
        genre: String,
        lyrics: String,
        trackNumber: Int,
        discNumber: Int?,
        replayGainTrackGainDb: String,
        replayGainAlbumGainDb: String,
        coverArtUpdate: CoverArtUpdate?
    ) -> Unit,
    removeFromListTrigger: () -> Unit,
    songInfoViewModel: SongInfoBottomSheetViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val ringtonePermissionMissingMsg = stringResource(R.string.song_info_ringtone_permission_missing)
    val ringtoneFailedFormat = stringResource(R.string.song_info_ringtone_failed)
    val shareChooserTitle = stringResource(R.string.song_info_share_chooser_title)
    val errorShareSongFormat = stringResource(R.string.song_info_error_share_song)
    var showEditSheet by remember { mutableStateOf(false) }
    var showArtistPicker by remember { mutableStateOf(false) }
    var showTonePickerDialog by remember { mutableStateOf(false) }
    var showShareOptionsDialog by remember { mutableStateOf(false) }
    var toneConfirmationTarget by remember { mutableStateOf<ToneTarget?>(null) }
    var pendingTonePermissionSong by remember { mutableStateOf<Song?>(null) }
    var pendingTonePermissionTarget by remember { mutableStateOf<ToneTarget?>(null) }
    val audioMeta by songInfoViewModel.audioMeta.collectAsStateWithLifecycle()
    val resolvedArtists by songInfoViewModel.resolvedArtists.collectAsStateWithLifecycle()

    // Artists offered in the picker. Local songs use library artists; online songs usually have no
    // library entries, so fall back to the names parsed from the artist string.
    val parsedArtistNames = remember(song.displayArtist) {
        song.displayArtist.split(",", "/", "&", ";", " feat. ", " ft. ", " with ")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "<unknown>" }
            .distinct()
    }
    val pickerArtists = remember(resolvedArtists, parsedArtistNames) {
        resolvedArtists.ifEmpty {
            if (parsedArtistNames.size > 1) {
                parsedArtistNames.map { Artist(id = it.hashCode().toLong(), name = it, songCount = 0) }
            } else emptyList()
        }
    }
    val openArtist: (Artist) -> Unit = { artist ->
        // Local library artists open by id; online artists (no library entry) open by name.
        if (song.isLocal && artist.id > 0L) onNavigateToArtistById(artist.id)
        else onNavigateToArtistByName(artist.name)
    }
    val onArtistClick: () -> Unit = {
        when {
            pickerArtists.size > 1 -> showArtistPicker = true
            pickerArtists.size == 1 -> openArtist(pickerArtists.first())
            else -> onNavigateToArtist()
        }
    }

    val ringtonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val pendingSong = pendingTonePermissionSong
        val pendingTarget = pendingTonePermissionTarget
        pendingTonePermissionSong = null
        pendingTonePermissionTarget = null
        if (pendingSong == null || pendingTarget == null) {
            return@rememberLauncherForActivityResult
        }
        if (songInfoViewModel.hasSystemWritePermission()) {
            songInfoViewModel.setSongAsTone(pendingSong, pendingTarget) { result ->
                val message = when (result) {
                    is SongInfoBottomSheetViewModel.ToneActionResult.Success -> result.message
                    is SongInfoBottomSheetViewModel.ToneActionResult.Error -> result.message
                    is SongInfoBottomSheetViewModel.ToneActionResult.NeedsSystemWritePermission -> result.message
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(
                context,
                ringtonePermissionMissingMsg,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun requestToneSystemWritePermission(songToSet: Song, target: ToneTarget, message: String) {
        pendingTonePermissionSong = songToSet
        pendingTonePermissionTarget = target
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        try {
            ringtonePermissionLauncher.launch(songInfoViewModel.createSystemWriteSettingsIntent())
        } catch (_: ActivityNotFoundException) {
            try {
                ringtonePermissionLauncher.launch(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                pendingTonePermissionSong = null
                pendingTonePermissionTarget = null
                Toast.makeText(
                    context,
                    ringtoneFailedFormat.format(e.localizedMessage ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun handleToneResult(
        songToSet: Song,
        target: ToneTarget,
        result: SongInfoBottomSheetViewModel.ToneActionResult
    ) {
        when (result) {
            is SongInfoBottomSheetViewModel.ToneActionResult.Success -> {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
            is SongInfoBottomSheetViewModel.ToneActionResult.Error -> {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
            is SongInfoBottomSheetViewModel.ToneActionResult.NeedsSystemWritePermission -> {
                requestToneSystemWritePermission(songToSet, target, result.message)
            }
        }
    }

    fun setCurrentSongAsTone(target: ToneTarget) {
        songInfoViewModel.setSongAsTone(song, target) { result ->
            handleToneResult(song, target, result)
        }
    }

    val evenCornerRadiusElems = 26.dp

    val listItemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = 20.dp, smoothnessAsPercentBR = 60, cornerRadiusBR = 20.dp,
            smoothnessAsPercentTL = 60, cornerRadiusTL = 20.dp, smoothnessAsPercentBL = 60,
            cornerRadiusBL = 20.dp, smoothnessAsPercentTR = 60
        )
    }
    val albumArtShape = remember(evenCornerRadiusElems) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = evenCornerRadiusElems, smoothnessAsPercentBR = 60, cornerRadiusBR = evenCornerRadiusElems,
            smoothnessAsPercentTL = 60, cornerRadiusTL = evenCornerRadiusElems, smoothnessAsPercentBL = 60,
            cornerRadiusBL = evenCornerRadiusElems, smoothnessAsPercentTR = 60
        )
    }
    val playButtonShape = remember(evenCornerRadiusElems) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = evenCornerRadiusElems, smoothnessAsPercentBR = 60, cornerRadiusBR = evenCornerRadiusElems,
            smoothnessAsPercentTL = 60, cornerRadiusTL = evenCornerRadiusElems, smoothnessAsPercentBL = 60,
            cornerRadiusBL = evenCornerRadiusElems, smoothnessAsPercentTR = 60
        )
    }

    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )




    val audioMetaLabel = remember(audioMeta, song) {
        val meta = audioMeta
        val sampleRate = meta?.sampleRate?.takeIf { it > 0 } ?: song.sampleRate?.takeIf { it > 0 }
        val bitrate = meta?.bitrate?.takeIf { it > 0 } ?: song.bitrate?.takeIf { it > 0 }
        val mimeType = meta?.mimeType ?: song.mimeType
        val formatLabel = mimeType?.let { AudioMetaUtils.mimeTypeToFormat(it) }
            ?.takeIf { it != "-" }
            ?.uppercase(java.util.Locale.getDefault())
            ?: song.path.takeIf { song.isLocal }?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() && it.length <= 5 }
                ?.uppercase(java.util.Locale.getDefault())

        val parts = buildList {
            sampleRate?.let { add(String.format(java.util.Locale.US, "%.1f kHz", it / 1000.0)) }
            bitrate?.let { add("${it / 1000} kbps") }
            formatLabel?.let { add(it) }
        }
        parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
    val songLocationInfo = remember(song.path, song.contentUriString) {
        songInfoViewModel.getSongLocationInfo(song)
    }

    LaunchedEffect(song.id) {
        songInfoViewModel.loadDetails(song)
        songInfoViewModel.loadAudioMeta(song)
        songInfoViewModel.loadArtistsForSong(song)
    }

    LaunchedEffect(Unit) {
        songInfoViewModel.downloadEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 2 })
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val maxPagerHeight = (
        configuration.screenHeightDp.dp -
            safeInsets.calculateTopPadding() -
            safeInsets.calculateBottomPadding() -
            180.dp
        ).coerceAtLeast(280.dp)

    var heightAnimationEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        heightAnimationEnabled = true
    }

    ModalBottomSheet(
        onDismissRequest = {
            android.util.Log.d("PixelPlayerDebug", "ModalBottomSheet: onDismissRequest called, showEditSheet=$showEditSheet")
            if (!showEditSheet) {
                onDismiss()
            }
        },
        sheetState = sheetState,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                    ) {
                        // Fila para la carátula del álbum y el título (Always visible)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SmartImage(
                                model = song.albumArtUriString,
                                contentDescription = stringResource(R.string.common_album_art),
                                shape = albumArtShape,
                                modifier = Modifier.size(80.dp),
                                contentScale = ContentScale.Crop,
                                songId = song.id
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 24.sp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = song.displayArtist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable(
                                            enabled = song.displayArtist.isNotBlank() && song.displayArtist != "<unknown>",
                                            onClick = onArtistClick
                                        )
                                )
                            }
                            val isEditable = remember(song) { songInfoViewModel.isSongEditable(song) }
                            if (isEditable) {
                                FilledTonalIconButton(
                                    modifier = Modifier.size(46.dp),
                                    shape = CircleShape,
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceBright,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    onClick = { showEditSheet = true },
                                ) {
                                    Icon(
                                        modifier = Modifier.size(22.dp),
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = stringResource(R.string.song_info_cd_edit_metadata)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Swipeable Content
                    var isPageTransitioning by remember { mutableStateOf(false) }
                    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
                        if (pagerState.isScrollInProgress) {
                            isPageTransitioning = true
                        } else {
                            kotlinx.coroutines.delay(300)
                            isPageTransitioning = false
                        }
                    }
                    val sizeAnimationSpec = if (heightAnimationEnabled && isPageTransitioning) {
                        tween<androidx.compose.ui.unit.IntSize>(durationMillis = 280, easing = FastOutSlowInEasing)
                    } else {
                        androidx.compose.animation.core.snap()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxPagerHeight)
                            .animateContentSize(
                                animationSpec = sizeAnimationSpec,
                                alignment = Alignment.TopCenter
                            )
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .wrapContentHeight()
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) { page ->
                            when (page) {
                                0 -> { // Options / Actions
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer {
                                                val pageOffset = page - (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                                val progress = kotlin.math.abs(pageOffset).coerceIn(0f, 1f)
                                                scaleX = 1f + 0.15f * progress * (1f - progress)
                                                translationX = pageOffset * size.width * 0.15f
                                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                                            }
                                            .verticalScroll(rememberScrollState())
                                            .padding(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Queue / Next: once the song has been added, close the menu
                                        // (slides down, then dismisses). Playlist keeps its own picker.
                                        val closeAfterSuccess: (() -> Unit) -> Unit = { action ->
                                            if (runCatching(action).isSuccess) {
                                                scope.launch { sheetState.hide() }.invokeOnCompletion {
                                                    if (!showEditSheet) onDismiss()
                                                }
                                            }
                                        }
                                        QueuePlaylistNextActionsRow(
                                            onAddToQueue = { closeAfterSuccess(onAddToQueue) },
                                            onAddToPlayList = onAddToPlayList,
                                            onAddNextToQueue = { closeAfterSuccess(onAddNextToQueue) }
                                        )

                                        PracticeActionRow(song = song)

                                        val isCloudSong = remember(song) { songInfoViewModel.isCloudSong(song) }
                                        val downloadStateMap by songInfoViewModel.downloadUiState.collectAsStateWithLifecycle()
                                        val downloadProgressState = downloadStateMap[song.id]
                                        val downloadedIds by songInfoViewModel.downloadedIds.collectAsStateWithLifecycle()
                                        val isThisDownloading = remember(downloadProgressState) {
                                            when (downloadProgressState) {
                                                is com.theveloper.pixelplay.data.youtube.DownloadProgress.Resolving,
                                                is com.theveloper.pixelplay.data.youtube.DownloadProgress.Downloading,
                                                is com.theveloper.pixelplay.data.youtube.DownloadProgress.Tagging,
                                                is com.theveloper.pixelplay.data.youtube.DownloadProgress.Scanning -> true
                                                else -> false
                                            }
                                        }
                                        // Same source of truth as the tick (DownloadDone badge) next to the title:
                                        // song.downloadState == DOWNLOADED. A just-finished download also counts.
                                        val isAlreadyDownloaded = song.isDownloaded || song.id in downloadedIds ||
                                            downloadProgressState is com.theveloper.pixelplay.data.youtube.DownloadProgress.Completed
                                        // Only offer "Download Song" for online songs that aren't downloaded yet.
                                        if (isCloudSong && !isAlreadyDownloaded) {

                                            FilledTonalButton(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 66.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                ),
                                                enabled = !isThisDownloading,
                                                contentPadding = PaddingValues(horizontal = 10.dp),
                                                shape = CircleShape,
                                                onClick = { songInfoViewModel.downloadSong(song) }
                                            ) {
                                                if (isThisDownloading) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = "Downloading...",
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                } else {
                                                    Icon(
                                                        Icons.Rounded.Download,
                                                        contentDescription = "Download Song"
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = "Download Song",
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        BottomActionsRow(
                                            isFavorite = isFavorite,
                                            onPlaySong = onPlaySong,
                                            onToggleFavorite = onToggleFavorite,
                                            onShareClick = { showShareOptionsDialog = true },
                                            playButtonShape = playButtonShape,
                                            evenCornerRadiusElems = evenCornerRadiusElems
                                        )

                                        Spacer(Modifier.height(80.dp))
                                    }
                                }
                                1 -> { // Details / Metadata tab
                                    val clipboardManager = remember {
                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .graphicsLayer {
                                                val pageOffset = page - (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                                val progress = kotlin.math.abs(pageOffset).coerceIn(0f, 1f)
                                                scaleX = 1f + 0.15f * progress * (1f - progress)
                                                translationX = pageOffset * size.width * 0.15f
                                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                                            }
                                            .verticalScroll(rememberScrollState())
                                            .padding(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Online songs get their basics filled in (cached lookup).
                                        val gatheredSong by songInfoViewModel.detailsSong.collectAsStateWithLifecycle()
                                        val details = gatheredSong?.takeIf { it.id == song.id } ?: song
                                        val localFilePath by songInfoViewModel.localFilePath.collectAsStateWithLifecycle()

                                        // 1. Duration
                                        MetadataItemCard(
                                            icon = Icons.Rounded.Schedule,
                                            title = stringResource(R.string.song_info_duration_label),
                                            subtitle = if (details.duration > 0) formatDuration(details.duration) else "\u2013"
                                        )

                                        // 2. Genre
                                        val effectiveGenre = details.genre?.takeIf {
                                            it.isNotBlank() && it != "<unknown>" && it != "YouTube Music" && it != "YouTube"
                                        }
                                        MetadataItemCard(
                                            icon = Icons.Rounded.MusicNote,
                                            title = stringResource(R.string.song_info_genre_label),
                                            subtitle = effectiveGenre ?: stringResource(R.string.song_info_unknown_genre),
                                            onClick = if (effectiveGenre != null) {
                                                onNavigateToGenre
                                            } else null
                                        )

                                        // 3. Album
                                        val effectiveAlbum = details.album.takeIf { it.isNotBlank() && it != "<unknown>" && it != "YouTube Music" }
                                        MetadataItemCard(
                                            icon = Icons.Rounded.Album,
                                            title = stringResource(R.string.song_info_album_label),
                                            subtitle = effectiveAlbum ?: stringResource(R.string.common_unknown_album),
                                            onClick = if (effectiveAlbum != null) {
                                                onNavigateToAlbum
                                            } else null
                                        )

                                        // 4. Artist
                                        val effectiveArtist = details.displayArtist.takeIf { it.isNotBlank() && it != "<unknown>" }
                                        MetadataItemCard(
                                            icon = Icons.Rounded.Person,
                                            title = stringResource(R.string.song_info_artist_label),
                                            subtitle = effectiveArtist ?: stringResource(R.string.common_unknown_artist),
                                            onClick = if (effectiveArtist != null) onArtistClick else null
                                        )

                                        // 5. Song info
                                        if (!audioMetaLabel.isNullOrBlank()) {
                                            MetadataItemCard(
                                                icon = Icons.Rounded.Info,
                                                title = stringResource(R.string.song_info_audio_format_label),
                                                subtitle = audioMetaLabel
                                            )
                                        }

                                        // 6. Path — only for songs that are a file on this device
                                        // (library files and downloads). Streaming-only online
                                        // songs have no path, so the row is left out.
                                        val shownPath = localFilePath?.takeIf { it.isNotBlank() }
                                        if (shownPath != null) {
                                            MetadataItemCard(
                                                icon = Icons.Rounded.AudioFile,
                                                title = stringResource(R.string.song_info_path_label),
                                                subtitle = shownPath,
                                                onClick = {
                                                    clipboardManager?.setPrimaryClip(
                                                        ClipData.newPlainText("Song Path", shownPath)
                                                    )
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.song_info_path_copied),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            )
                                        }

                                        Spacer(Modifier.height(88.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Custom Bottom Tab Bar Row
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        TabAnimation(
                            index = 0,
                            title = stringResource(R.string.song_info_tab_options),
                            selectedIndex = pagerState.currentPage,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            },
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Menu,
                                    contentDescription = stringResource(R.string.song_info_tab_options),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.song_info_tab_options_badge),
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    // Middle Delete Button — only for songs that exist on the device
                    // (local files or downloaded songs, i.e. the ones showing the tick).
                    // Online / not-downloaded songs have nothing to delete.
                    if (song.isLocalOrDownloaded) IconButton(
                        onClick = {
                            (context as? Activity)?.let { activity ->
                                onDeleteFromDevice(activity, song) { result ->
                                    if (result) {
                                        removeFromListTrigger()
                                        onDismiss()
                                    }
                                }
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteForever,
                            contentDescription = stringResource(R.string.song_info_action_delete),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        TabAnimation(
                            index = 1,
                            title = stringResource(R.string.song_info_tab_info),
                            selectedIndex = pagerState.currentPage,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            },
                            transformOrigin = TransformOrigin(1f, 0.5f)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Info,
                                    contentDescription = stringResource(R.string.song_info_tab_info),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.song_info_tab_info_badge),
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
    }

    EditSongSheet(
        visible = showEditSheet,
        song = song,
        onDismiss = { showEditSheet = false },
        onSave = { title, artist, album, albumArtist, composer, genre, lyrics, trackNumber, discNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArt ->
            onEditSong(
                title,
                artist,
                album,
                albumArtist,
                composer,
                genre,
                lyrics,
                trackNumber,
                discNumber,
                replayGainTrackGainDb,
                replayGainAlbumGainDb,
                coverArt
            )
            showEditSheet = false
        },
    )

    @Suppress("DEPRECATION")
    val artistPickerSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (showArtistPicker && pickerArtists.isNotEmpty()) {
        com.theveloper.pixelplay.presentation.components.player.PlayerArtistPickerBottomSheet(
            song = song,
            artists = pickerArtists,
            sheetState = artistPickerSheetState,
            onDismiss = { showArtistPicker = false },
            onArtistClick = { artist ->
                showArtistPicker = false
                openArtist(artist)
            }
        )
    }

    if (showShareOptionsDialog) {
        ShareOptionsDialog(
            song = song,
            onDismiss = { showShareOptionsDialog = false },
            onShareFile = {
                showShareOptionsDialog = false
                try {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/*"
                        putExtra(Intent.EXTRA_STREAM, song.contentUriString.toUri())
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(
                            shareIntent,
                            shareChooserTitle
                        )
                    )
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        errorShareSongFormat.format(e.localizedMessage ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onSetAsSound = {
                showShareOptionsDialog = false
                showTonePickerDialog = true
            }
        )
    }

    if (showTonePickerDialog) {
        ToneTargetPickerDialog(
            onDismiss = { showTonePickerDialog = false },
            onTargetSelected = { target ->
                showTonePickerDialog = false
                toneConfirmationTarget = target
            }
        )
    }

    toneConfirmationTarget?.let { target ->
        ToneConfirmationDialog(
            song = song,
            target = target,
            onDismiss = { toneConfirmationTarget = null },
            onConfirm = {
                toneConfirmationTarget = null
                setCurrentSongAsTone(target)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToneTargetPickerDialog(
    onDismiss: () -> Unit,
    onTargetSelected: (ToneTarget) -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTR = 32.dp,
                smoothnessAsPercentBR = 60,
                cornerRadiusBR = 32.dp,
                smoothnessAsPercentTL = 60,
                cornerRadiusTL = 32.dp,
                smoothnessAsPercentBL = 60,
                cornerRadiusBL = 32.dp,
                smoothnessAsPercentTR = 60,
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToneDialogIcon(target = null)
                    Text(
                        text = stringResource(R.string.song_info_tone_picker_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = stringResource(R.string.song_info_tone_picker_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(
                    modifier = Modifier.clip(RoundedCornerShape(22.dp)),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    ToneTarget.values().forEach { target ->
                        ToneTargetOption(
                            target = target,
                            onClick = { onTargetSelected(target) },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToneTargetOption(
    target: ToneTarget,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        leadingContent = {
            ToneDialogIcon(
                target = target,
                modifier = Modifier.size(42.dp),
                iconModifier = Modifier.size(22.dp),
            )
        },
        headlineContent = {
            Text(
                text = stringResource(target.titleResId),
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = {
            Text(stringResource(target.subtitleResId))
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToneConfirmationDialog(
    song: Song,
    target: ToneTarget,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTR = 32.dp,
                smoothnessAsPercentBR = 60,
                cornerRadiusBR = 32.dp,
                smoothnessAsPercentTL = 60,
                cornerRadiusTL = 32.dp,
                smoothnessAsPercentBL = 60,
                cornerRadiusBL = 32.dp,
                smoothnessAsPercentTR = 60,
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToneDialogIcon(target = target)
                    Text(
                        text = stringResource(R.string.song_info_tone_confirm_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.song_info_tone_confirm_body,
                        song.title,
                        stringResource(target.confirmLabelResId),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel))
                    }
                    FilledTonalButton(onClick = onConfirm) {
                        Text(stringResource(R.string.song_info_tone_confirm_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToneDialogIcon(
    target: ToneTarget?,
    modifier: Modifier = Modifier.size(56.dp),
    iconModifier: Modifier = Modifier.size(28.dp),
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        when (target) {
            ToneTarget.Ringtone -> Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = iconModifier,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            ToneTarget.Notification -> Icon(
                painter = painterResource(R.drawable.rounded_notifications_active_24),
                contentDescription = null,
                modifier = iconModifier,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            ToneTarget.Alarm -> Icon(
                painter = painterResource(R.drawable.rounded_alarm_24),
                contentDescription = null,
                modifier = iconModifier,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            null -> Icon(
                painter = painterResource(R.drawable.rounded_notifications_active_24),
                contentDescription = null,
                modifier = iconModifier,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun RingtoneActionButton(
    modifier: Modifier,
    showText: Boolean,
    compactText: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
) {
    val colors = ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )

    if (showText) {
        FilledTonalButton(
            modifier = modifier,
            colors = colors,
            contentPadding = PaddingValues(horizontal = if (compactText) 12.dp else 18.dp),
            shape = CircleShape,
            onClick = onClick,
            interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        ) {
            Icon(
                modifier = Modifier.size(if (compactText) 20.dp else 24.dp),
                painter = painterResource(R.drawable.rounded_notifications_active_24),
                contentDescription = stringResource(R.string.song_info_cd_set_sound_as),
            )
            Spacer(Modifier.width(if (compactText) 6.dp else 8.dp))
            Text(
                text = stringResource(
                    if (compactText) R.string.song_info_set_sound_as_short else R.string.song_info_set_sound_as_long
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        FilledTonalIconButton(
            modifier = modifier,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            shape = CircleShape,
            onClick = onClick,
            interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        ) {
            Icon(
                modifier = Modifier.size(FloatingActionButtonDefaults.LargeIconSize),
                painter = painterResource(R.drawable.rounded_notifications_active_24),
                contentDescription = stringResource(R.string.song_info_cd_set_sound_as),
            )
        }
    }
}

private val ToneTarget.titleResId: Int
    get() = when (this) {
        ToneTarget.Ringtone -> R.string.song_info_tone_ringtone_title
        ToneTarget.Notification -> R.string.song_info_tone_notification_title
        ToneTarget.Alarm -> R.string.song_info_tone_alarm_title
    }

private val ToneTarget.subtitleResId: Int
    get() = when (this) {
        ToneTarget.Ringtone -> R.string.song_info_tone_ringtone_subtitle
        ToneTarget.Notification -> R.string.song_info_tone_notification_subtitle
        ToneTarget.Alarm -> R.string.song_info_tone_alarm_subtitle
    }

private val ToneTarget.confirmLabelResId: Int
    get() = when (this) {
        ToneTarget.Ringtone -> R.string.song_info_tone_ringtone_label
        ToneTarget.Notification -> R.string.song_info_tone_notification_label
        ToneTarget.Alarm -> R.string.song_info_tone_alarm_label
    }

@Composable
private fun MetadataItemCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .let { baseModifier ->
                if (onClick != null) {
                    baseModifier.clickable(onClick = onClick)
                } else {
                    baseModifier
                }
            },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareOptionsDialog(
    song: Song,
    onDismiss: () -> Unit,
    onShareFile: () -> Unit,
    onSetAsSound: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTR = 32.dp,
                smoothnessAsPercentBR = 60,
                cornerRadiusBR = 32.dp,
                smoothnessAsPercentTL = 60,
                cornerRadiusTL = 32.dp,
                smoothnessAsPercentBL = 60,
                cornerRadiusBL = 32.dp,
                smoothnessAsPercentTR = 60,
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.song_info_share_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(
                    modifier = Modifier.clip(RoundedCornerShape(22.dp)),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onShareFile),
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        },
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.song_info_share_action_file),
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        supportingContent = {
                            Text(stringResource(R.string.song_info_share_action_file_desc))
                        },
                    )

                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onSetAsSound),
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.rounded_notifications_active_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        },
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.song_info_share_action_sound),
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        supportingContent = {
                            Text(stringResource(R.string.song_info_share_action_sound_desc))
                        },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomActionsRow(
    isFavorite: Boolean,
    onPlaySong: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShareClick: () -> Unit,
    playButtonShape: Shape,
    evenCornerRadiusElems: androidx.compose.ui.unit.Dp
) {
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var clickPending by remember { mutableStateOf(false) }

    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    var playVisualPressed by remember { mutableStateOf(false) }
    LaunchedEffect(isPlayPressed) {
        if (isPlayPressed) {
            playVisualPressed = true
        } else {
            kotlinx.coroutines.delay(180)
            playVisualPressed = false
        }
    }

    val favoriteInteractionSource = remember { MutableInteractionSource() }
    val isFavoritePressed by favoriteInteractionSource.collectIsPressedAsState()
    var favoriteVisualPressed by remember { mutableStateOf(false) }
    LaunchedEffect(isFavoritePressed) {
        if (isFavoritePressed) {
            favoriteVisualPressed = true
        } else {
            kotlinx.coroutines.delay(180)
            favoriteVisualPressed = false
        }
    }

    val shareInteractionSource = remember { MutableInteractionSource() }
    val isSharePressed by shareInteractionSource.collectIsPressedAsState()
    var shareVisualPressed by remember { mutableStateOf(false) }
    LaunchedEffect(isSharePressed) {
        if (isSharePressed) {
            shareVisualPressed = true
        } else {
            kotlinx.coroutines.delay(180)
            shareVisualPressed = false
        }
    }

    val pressSpec = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = 300f
    )

    val pressFractionPlay by animateFloatAsState(
        targetValue = if (playVisualPressed) 1f else 0f,
        animationSpec = pressSpec,
        label = "PlayPressFraction"
    )
    val pressFractionFavorite by animateFloatAsState(
        targetValue = if (favoriteVisualPressed) 1f else 0f,
        animationSpec = pressSpec,
        label = "FavoritePressFraction"
    )
    val pressFractionShare by animateFloatAsState(
        targetValue = if (shareVisualPressed) 1f else 0f,
        animationSpec = pressSpec,
        label = "SharePressFraction"
    )

    val weightPlay = (0.46f + 0.08f * pressFractionPlay - 0.04f * pressFractionFavorite - 0.04f * pressFractionShare).coerceAtLeast(0.1f)
    val weightFavorite = (0.27f + 0.08f * pressFractionFavorite - 0.04f * pressFractionPlay - 0.02f * pressFractionShare).coerceAtLeast(0.05f)
    val weightShare = (0.27f + 0.08f * pressFractionShare - 0.04f * pressFractionPlay - 0.02f * pressFractionFavorite).coerceAtLeast(0.05f)

    // Local favorite button animations
    val favoriteButtonCornerRadius by animateDpAsState(
        targetValue = if (isFavorite) evenCornerRadiusElems else 60.dp,
        animationSpec = tween(durationMillis = 300), label = "FavoriteCornerAnimation"
    )
    val favoriteButtonContainerColor by animateColorAsState(
        targetValue = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(durationMillis = 300), label = "FavoriteContainerColorAnimation"
    )
    val favoriteButtonContentColor by animateColorAsState(
        targetValue = if (isFavorite) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 300), label = "FavoriteContentColorAnimation"
    )

    val favoriteButtonShape = remember(favoriteButtonCornerRadius) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = favoriteButtonCornerRadius, smoothnessAsPercentBR = 60, cornerRadiusBR = favoriteButtonCornerRadius,
            smoothnessAsPercentTL = 60, cornerRadiusTL = favoriteButtonCornerRadius, smoothnessAsPercentBL = 60,
            cornerRadiusBL = favoriteButtonCornerRadius, smoothnessAsPercentTR = 60
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Bottom Left: Like card (to the left of the play card)
        FilledIconButton(
            modifier = Modifier
                .weight(weightFavorite)
                .fillMaxHeight(),
            onClick = {
                if (clickPending) return@FilledIconButton
                clickPending = true
                favoriteVisualPressed = true
                android.util.Log.d("PixelPlayerDebug", "BottomActionsRow: Favorite clicked")
                coroutineScope.launch {
                    kotlinx.coroutines.delay(180)
                    favoriteVisualPressed = false
                    clickPending = false
                    onToggleFavorite()
                }
            },
            shape = favoriteButtonShape,
            interactionSource = favoriteInteractionSource,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = favoriteButtonContainerColor,
                contentColor = favoriteButtonContentColor
            )
        ) {
            Icon(
                modifier = Modifier.size(FloatingActionButtonDefaults.LargeIconSize),
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = stringResource(
                    if (isFavorite) R.string.song_info_cd_remove_from_favorites else R.string.song_info_cd_add_to_favorites
                )
            )
        }

        // Bottom Middle: Play card
        FilledTonalButton(
            modifier = Modifier
                .weight(weightPlay)
                .heightIn(min = 80.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 10.dp),
            shape = playButtonShape,
            interactionSource = playInteractionSource,
            onClick = {
                if (clickPending) return@FilledTonalButton
                clickPending = true
                playVisualPressed = true
                android.util.Log.d("PixelPlayerDebug", "BottomActionsRow: Play clicked")
                coroutineScope.launch {
                    kotlinx.coroutines.delay(180)
                    playVisualPressed = false
                    clickPending = false
                    onPlaySong()
                }
            }
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.song_info_cd_play),
            )
            Spacer(Modifier.width(6.dp))
            TightWrapText(
                text = stringResource(R.string.song_info_action_play),
                modifier = Modifier.padding(end = 4.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                lineHeight = 22.sp,
                style = MaterialTheme.typography.titleLarge
            )
        }

        // Bottom Right: Share button/card
        FilledTonalIconButton(
            modifier = Modifier
                .weight(weightShare)
                .fillMaxHeight(),
            onClick = {
                if (clickPending) return@FilledTonalIconButton
                clickPending = true
                shareVisualPressed = true
                android.util.Log.d("PixelPlayerDebug", "BottomActionsRow: Share clicked")
                coroutineScope.launch {
                    kotlinx.coroutines.delay(180)
                    shareVisualPressed = false
                    clickPending = false
                    onShareClick()
                }
            },
            shape = playButtonShape,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            interactionSource = shareInteractionSource
        ) {
            Icon(
                modifier = Modifier.size(FloatingActionButtonDefaults.LargeIconSize),
                imageVector = Icons.Rounded.Share,
                contentDescription = stringResource(R.string.song_info_cd_share_song_file)
            )
        }
    }
}

@Composable
private fun QueuePlaylistNextActionsRow(
    onAddToQueue: () -> Unit,
    onAddToPlayList: () -> Unit,
    onAddNextToQueue: () -> Unit
) {
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var clickPending by remember { mutableStateOf(false) }

    val queueInteractionSource = remember { MutableInteractionSource() }
    val isQueuePressed by queueInteractionSource.collectIsPressedAsState()
    var queueVisualPressed by remember { mutableStateOf(false) }
    LaunchedEffect(isQueuePressed) {
        if (isQueuePressed) {
            queueVisualPressed = true
        } else {
            kotlinx.coroutines.delay(180)
            queueVisualPressed = false
        }
    }

    val playlistInteractionSource = remember { MutableInteractionSource() }
    val isPlaylistPressed by playlistInteractionSource.collectIsPressedAsState()
    var playlistVisualPressed by remember { mutableStateOf(false) }
    LaunchedEffect(isPlaylistPressed) {
        if (isPlaylistPressed) {
            playlistVisualPressed = true
        } else {
            kotlinx.coroutines.delay(180)
            playlistVisualPressed = false
        }
    }

    val nextInteractionSource = remember { MutableInteractionSource() }
    val isNextPressed by nextInteractionSource.collectIsPressedAsState()
    var nextVisualPressed by remember { mutableStateOf(false) }
    LaunchedEffect(isNextPressed) {
        if (isNextPressed) {
            nextVisualPressed = true
        } else {
            kotlinx.coroutines.delay(180)
            nextVisualPressed = false
        }
    }

    val pressSpec = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = 300f
    )

    val pressFractionQueue by animateFloatAsState(
        targetValue = if (queueVisualPressed) 1f else 0f,
        animationSpec = pressSpec,
        label = "QueuePressFraction"
    )
    val pressFractionPlaylist by animateFloatAsState(
        targetValue = if (playlistVisualPressed) 1f else 0f,
        animationSpec = pressSpec,
        label = "PlaylistPressFraction"
    )
    val pressFractionNext by animateFloatAsState(
        targetValue = if (nextVisualPressed) 1f else 0f,
        animationSpec = pressSpec,
        label = "NextPressFraction"
    )

    val weightQueue = (0.33f + 0.08f * pressFractionQueue - 0.04f * pressFractionPlaylist - 0.04f * pressFractionNext).coerceAtLeast(0.1f)
    val weightPlaylist = (0.34f + 0.08f * pressFractionPlaylist - 0.04f * pressFractionQueue - 0.04f * pressFractionNext).coerceAtLeast(0.1f)
    val weightNext = (0.33f + 0.08f * pressFractionNext - 0.04f * pressFractionQueue - 0.04f * pressFractionPlaylist).coerceAtLeast(0.1f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left: Queue (shortened label to icon + "Queue")
        FilledTonalButton(
            modifier = Modifier
                .weight(weightQueue)
                .heightIn(min = 66.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 6.dp),
            shape = CircleShape,
            interactionSource = queueInteractionSource,
            onClick = {
                if (clickPending) return@FilledTonalButton
                clickPending = true
                queueVisualPressed = true
                android.util.Log.d("PixelPlayerDebug", "QueuePlaylistNextActionsRow: Queue clicked")
                coroutineScope.launch {
                    kotlinx.coroutines.delay(180)
                    queueVisualPressed = false
                    clickPending = false
                    onAddToQueue()
                }
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = stringResource(R.string.song_info_cd_add_to_queue),
                )
                Spacer(Modifier.width(4.dp))
                TightWrapText(
                    text = stringResource(R.string.song_info_action_queue),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    lineHeight = 16.sp,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansRounded
                    )
                )
            }
        }

        // Middle: Playlist
        FilledTonalButton(
            modifier = Modifier
                .weight(weightPlaylist)
                .heightIn(min = 66.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 6.dp),
            shape = CircleShape,
            interactionSource = playlistInteractionSource,
            onClick = {
                if (clickPending) return@FilledTonalButton
                clickPending = true
                playlistVisualPressed = true
                android.util.Log.d("PixelPlayerDebug", "QueuePlaylistNextActionsRow: Playlist clicked")
                coroutineScope.launch {
                    kotlinx.coroutines.delay(180)
                    playlistVisualPressed = false
                    clickPending = false
                    onAddToPlayList()
                }
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = stringResource(R.string.song_info_cd_add_to_playlist)
                )
                Spacer(Modifier.width(4.dp))
                TightWrapText(
                    text = stringResource(R.string.common_playlist),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    lineHeight = 16.sp,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansRounded
                    )
                )
            }
        }

        // Right: Next
        FilledTonalButton(
            modifier = Modifier
                .weight(weightNext)
                .heightIn(min = 66.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            ),
            contentPadding = PaddingValues(horizontal = 6.dp),
            shape = CircleShape,
            interactionSource = nextInteractionSource,
            onClick = {
                if (clickPending) return@FilledTonalButton
                clickPending = true
                nextVisualPressed = true
                android.util.Log.d("PixelPlayerDebug", "QueuePlaylistNextActionsRow: Next clicked")
                coroutineScope.launch {
                    kotlinx.coroutines.delay(180)
                    nextVisualPressed = false
                    clickPending = false
                    onAddNextToQueue()
                }
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = stringResource(R.string.song_info_cd_queue_next)
                )
                Spacer(Modifier.width(4.dp))
                TightWrapText(
                    text = stringResource(R.string.song_info_action_queue_next),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    lineHeight = 16.sp,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansRounded
                    )
                )
            }
        }
    }
}

/**
 * "Practice" in the song menu: adds the song to Want to learn, or shows where it is
 * (Want / Learning / Finished) with one tap to move it on and a menu to move or remove it.
 */
@Composable
private fun PracticeActionRow(song: Song) {
    val context = LocalContext.current
    val store = remember { com.theveloper.pixelplay.data.practice.PracticeStore.get(context) }
    val entries by store.entries.collectAsStateWithLifecycle()
    val stage = entries.firstOrNull { it.songId == song.id }?.stage
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable {
                when {
                    stage == null -> store.add(song.id, song.title, song.artist)
                    stage.next != null -> store.move(song.id, stage.next!!)
                    else -> menuOpen = true
                }
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.School,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.tertiary
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (stage == null) "Add to Practice" else "Practice · ${stage.label}",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontFamily = GoogleSansRounded)
            )
            Text(
                text = when {
                    stage == null -> "Goes to Want to learn"
                    stage.next != null -> "Tap to move to ${stage.next!!.label}"
                    else -> "Tap for options"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (stage != null) {
            Box {
                androidx.compose.material3.IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Practice options", modifier = Modifier.size(18.dp))
                }
                androidx.compose.material3.DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    com.theveloper.pixelplay.data.practice.PracticeStage.entries.filter { it != stage }.forEach { target ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Move to ${target.label}") },
                            onClick = { menuOpen = false; store.move(song.id, target) }
                        )
                    }
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Remove from Practice") },
                        onClick = { menuOpen = false; store.remove(song.id) }
                    )
                }
            }
        }
    }
}

