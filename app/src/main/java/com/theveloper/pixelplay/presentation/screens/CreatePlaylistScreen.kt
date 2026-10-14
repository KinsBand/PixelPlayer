@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class
)

package com.theveloper.pixelplay.presentation.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MicExternalOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.theveloper.pixelplay.presentation.components.CoverFiltersPanel
import com.theveloper.pixelplay.presentation.components.coverEditLayoutSpring
import com.theveloper.pixelplay.ui.theme.MotionTokens
import kotlin.math.roundToInt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.CoverAdjustMode
import com.theveloper.pixelplay.presentation.components.CoverArtLayer
import com.theveloper.pixelplay.presentation.components.CoverEditorState
import com.theveloper.pixelplay.presentation.components.CoverLayerCompositor
import com.theveloper.pixelplay.presentation.components.CoverLayerStrip
import com.theveloper.pixelplay.presentation.components.CoverLayersCanvas
import com.theveloper.pixelplay.presentation.components.rememberCoverEditorState
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material3.AlertDialog
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.Dp
import com.theveloper.pixelplay.presentation.components.CoverFilterPreset
import com.theveloper.pixelplay.presentation.components.CoverFilters
import com.theveloper.pixelplay.presentation.components.coverFilterEffect
import com.theveloper.pixelplay.data.model.PlaylistShapeType
import com.theveloper.pixelplay.data.model.SmartPlaylistRule
// import com.theveloper.pixelplay.presentation.screens.ShapeType // Removed local enum
import com.theveloper.pixelplay.presentation.components.SongPickerContent
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import androidx.compose.material3.Slider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import com.theveloper.pixelplay.utils.resolvePlaylistCoverContentColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Matrix
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import com.theveloper.pixelplay.data.model.StorageFilter
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.SliderDefaults


import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun smartPlaylistRuleTitle(rule: SmartPlaylistRule): String =
    stringResource(
        when (rule) {
            SmartPlaylistRule.TOP_PLAYED -> R.string.playlist_creation_rule_top_played_title
            SmartPlaylistRule.RECENTLY_PLAYED -> R.string.playlist_creation_rule_recently_played_title
            SmartPlaylistRule.FORGOTTEN_FAVORITES -> R.string.playlist_creation_rule_forgotten_title
            SmartPlaylistRule.NEW_GEMS -> R.string.playlist_creation_rule_new_gems_title
        }
    )

@Composable
private fun smartPlaylistRuleSubtitle(rule: SmartPlaylistRule): String =
    stringResource(
        when (rule) {
            SmartPlaylistRule.TOP_PLAYED -> R.string.playlist_creation_rule_top_played_sub
            SmartPlaylistRule.RECENTLY_PLAYED -> R.string.playlist_creation_rule_recently_played_sub
            SmartPlaylistRule.FORGOTTEN_FAVORITES -> R.string.playlist_creation_rule_forgotten_sub
            SmartPlaylistRule.NEW_GEMS -> R.string.playlist_creation_rule_new_gems_sub
        }
    )

private enum class PlaylistCreationMode {
    MANUAL,
    SMART
}

@Composable
fun CreatePlaylistDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onGenerateClick: () -> Unit,
    onCreate: (String, String?, Int?, String?, List<String>, Float, Float, Float, String?, Float?, Float?, Float?, Float?, String?, PlaylistCoverImageEdits) -> Unit
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible

    if (transitionState.currentState || transitionState.targetState) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(animationSpec = tween(220)),
                exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(animationSpec = tween(200)),
                label = "create_playlist_dialog"
            ) {
                CreatePlaylistContent(
                    onDismiss = onDismiss,
                    onGenerateClick = onGenerateClick,
                    onCreate = onCreate
                )
            }
        }
    }
}

// Enum removed, using com.theveloper.pixelplay.data.model.PlaylistShapeType

/** Flip, rotation and colour grade picked on the Adjust screen; baked into the saved cover image. */
@androidx.compose.runtime.Immutable
data class PlaylistCoverImageEdits(
    val flipHorizontal: Boolean = false,
    val filterId: String? = null,
    /** Clockwise, in quarter turns: 0, 90, 180 or 270. */
    val rotationDegrees: Int = 0
)

/** Snapshot of everything Edit playlist's Save writes (equality = "nothing changed"). */
private data class EditPlaylistSavePayload(
    val name: String,
    val imageUri: String?,
    val color: Int?,
    val iconName: String?,
    val cropScale: Float,
    val panX: Float,
    val panY: Float,
    val shapeType: String?,
    val d1: Float?,
    val d2: Float?,
    val d3: Float?,
    val d4: Float?,
    val imageEdits: PlaylistCoverImageEdits,
    /** Non-null when the cover has overlays or a stretch (rendered to one image on Save). */
    val layersSignature: String? = null
)

@Composable
fun EditPlaylistDialog(
    visible: Boolean,
    currentName: String,
    currentImageUri: String?,
    currentColor: Int?,
    currentIconName: String?,
    currentShapeType: PlaylistShapeType?,
    currentShapeDetail1: Float?,
    currentShapeDetail2: Float?,
    currentShapeDetail3: Float?,
    currentShapeDetail4: Float?,
    onDismiss: () -> Unit,
    onSave: (String, String?, Int?, String?, Float, Float, Float, String?, Float?, Float?, Float?, Float?, PlaylistCoverImageEdits) -> Unit,
    /** Non-null shows the transition (graph) action in the top bar. */
    onEditTransitionClick: (() -> Unit)? = null
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible

    if (transitionState.currentState || transitionState.targetState) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(animationSpec = tween(220)),
                exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(animationSpec = tween(200)),
                label = "edit_playlist_dialog"
            ) {
                 EditPlaylistContent(
                    initialName = currentName,
                    initialImageUri = currentImageUri,
                    initialColor = currentColor,
                    initialIconName = currentIconName,
                    initialShapeType = currentShapeType,
                    initialShapeDetail1 = currentShapeDetail1,
                    initialShapeDetail2 = currentShapeDetail2,
                    initialShapeDetail3 = currentShapeDetail3,
                    initialShapeDetail4 = currentShapeDetail4,
                    onDismiss = onDismiss,
                    onSave = onSave,
                    onEditTransitionClick = onEditTransitionClick
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun CreatePlaylistContent(
    onDismiss: () -> Unit,
    onGenerateClick: () -> Unit,
    onCreate: (String, String?, Int?, String?, List<String>, Float, Float, Float, String?, Float?, Float?, Float?, Float?, String?, PlaylistCoverImageEdits) -> Unit,
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // Shared State
    var playlistName by remember { mutableStateOf("") }
    
    // Step 1: Info State
    var currentStep by remember { mutableStateOf(0) } // 0: Info, 1: Songs
    var selectedTab by remember { mutableStateOf(0) } // 0: Default, 1: Image, 2: Icon
    var creationMode by remember { mutableStateOf(PlaylistCreationMode.MANUAL) }
    var selectedSmartRule by remember { mutableStateOf(SmartPlaylistRule.TOP_PLAYED) }
    
    // Songs State
    val selectedSongIds = remember { mutableStateMapOf<String, Boolean>() }
    
    // Image State
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showCropUi by remember { mutableStateOf(false) }
    // Lifted Bitmap State for Preview Consistency
    var imageBitmap by remember(selectedImageUri) { mutableStateOf<ImageBitmap?>(null) }

    // Cover layers: Layer 1 is the photo; zoom / stretch / pan / rotation / flip / filter live
    // per layer (same editor as Edit playlist).
    val coverEditor = rememberCoverEditorState()
    var showFilters by remember { mutableStateOf(false) }
    var isAddingLayer by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Custom Color/Icon State
    val defaultColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    var selectedColor by remember { mutableStateOf<Int?>(defaultColor) }
    var selectedIconName by remember { mutableStateOf<String?>("MusicNote") }

    // Shape State
    var selectedShapeType by remember { mutableStateOf(PlaylistShapeType.Circle) }
    
    // SmoothRect Params
    var smoothRectCornerRadius by remember { androidx.compose.runtime.mutableFloatStateOf(20f) } // 0-50
    var smoothRectSmoothness by remember { androidx.compose.runtime.mutableFloatStateOf(60f) } // 0-100
    
    var starCurve by remember { androidx.compose.runtime.mutableDoubleStateOf(0.15) } // 0.0 - 0.5
    var starRotation by remember { androidx.compose.runtime.mutableFloatStateOf(0f) } // 0 - 360
    var starScale by remember { androidx.compose.runtime.mutableFloatStateOf(1f) } // 0.5 - 1.5
    var starSides by remember { androidx.compose.runtime.mutableIntStateOf(5) } // 3 - 20

    LaunchedEffect(selectedImageUri) {
         if (selectedImageUri != null) {
             val loader = ImageLoader(context)
             val request = ImageRequest.Builder(context)
                 .data(selectedImageUri)
                 .allowHardware(false)
                 .build()
             
             val result = loader.execute(request)
             val drawable = result.drawable
             if (drawable is android.graphics.drawable.BitmapDrawable) {
                 val loaded = drawable.bitmap.asImageBitmap()
                 imageBitmap = loaded
                 coverEditor.setBaseImage(loaded)
             }
         } else {
             imageBitmap = null
             coverEditor.clear()
         }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            // A new photo starts upright and unmirrored; the filter and any overlays are kept.
            coverEditor.resetBaseForNewImage()
            showCropUi = true
            selectedTab = 1
        }
    }

    // "+" in Layers mode: stacks another image over the cover.
    val layerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isAddingLayer = true
        scope.launch {
            try {
                loadLayerBitmap(context, uri)?.let(coverEditor::addOverlay)
            } finally {
                isAddingLayer = false
            }
        }
    }

    val isAdjusted = coverEditor.isAdjusted
    val resetAdjustments: () -> Unit = { coverEditor.resetAll() }
    val closeAdjust: () -> Unit = {
        showFilters = false
        showCropUi = false
    }

    // One place that builds the playlist from the current cover choice.
    val submit: (List<String>, String?) -> Unit = { songIds, smartRuleKey ->
        val (d1, d2, d3, d4) = if (selectedTab == 2) {
            when (selectedShapeType) {
                PlaylistShapeType.SmoothRect -> Quadruple(smoothRectCornerRadius, smoothRectSmoothness, 0f, 0f)
                PlaylistShapeType.Star -> Quadruple(starCurve.toFloat(), starRotation, starScale, starSides.toFloat())
                else -> Quadruple(0f, 0f, 0f, 0f)
            }
        } else Quadruple(null, null, null, null)
        if (!isSubmitting) {
            isSubmitting = true
            scope.launch {
                try {
                    // Layered / stretched covers are rendered to one image first (off the main thread).
                    val cover = resolvePlaylistCover(
                        context,
                        coverEditor,
                        if (selectedTab == 1) selectedImageUri?.toString() else null
                    )
                    onCreate(
                        playlistName,
                        cover.imageUri,
                        if (selectedTab == 2) selectedColor else null,
                        if (selectedTab == 2) selectedIconName else null,
                        songIds,
                        cover.cropScale,
                        cover.pan.x,
                        cover.pan.y,
                        if (selectedTab == 2) selectedShapeType.name else null,
                        d1, d2, d3, d4,
                        smartRuleKey,
                        cover.edits
                    )
                } finally {
                    isSubmitting = false
                }
            }
        }
    }
    
    // Back Handler for navigation flow
    BackHandler(enabled = showCropUi || (currentStep == 1 && creationMode == PlaylistCreationMode.MANUAL)) {
        when {
            showCropUi && showFilters -> showFilters = false
            showCropUi -> closeAdjust()
            currentStep == 1 -> currentStep = 0
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    // Same title style and motion as Edit playlist (titleLarge, SemiBold, layout spring).
                    AnimatedContent(
                        targetState = if (showCropUi) 2 else currentStep,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220, delayMillis = 60)) +
                                slideInVertically(animationSpec = editLayoutSpring()) { it / 2 }) togetherWith
                                (fadeOut(animationSpec = tween(140)) +
                                    slideOutVertically(animationSpec = editLayoutSpring()) { -it / 2 })
                        },
                        label = "Title Animation"
                    ) { displayStep ->
                        Text(
                            when (displayStep) {
                                2 -> stringResource(R.string.playlist_creation_edit_cover)
                                0 -> {
                                    if (creationMode == PlaylistCreationMode.SMART) {
                                        stringResource(R.string.playlist_creation_new_smart)
                                    } else {
                                        stringResource(R.string.playlist_creation_new)
                                    }
                                }
                                else -> stringResource(R.string.playlist_creation_add_songs)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 6.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = {
                            when {
                                showCropUi -> closeAdjust()
                                currentStep == 1 && creationMode == PlaylistCreationMode.MANUAL -> currentStep = 0
                                else -> onDismiss()
                            }
                        }
                    ) {
                        Icon(
                            if (showCropUi || (currentStep == 1 && creationMode == PlaylistCreationMode.MANUAL)) {
                                Icons.AutoMirrored.Rounded.ArrowBack
                            } else {
                                Icons.Rounded.Close
                            },
                            contentDescription = stringResource(R.string.playlist_creation_cd_back_or_cancel)
                        )
                    }
                },
                actions = {
                    if (showCropUi) {
                        // Undo every adjustment: zoom, pan, rotation, flip and filter.
                        FilledIconButton(
                            modifier = Modifier.padding(end = 6.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            enabled = isAdjusted,
                            onClick = resetAdjustments
                        ) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset adjustments")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        // Step 1 (Add songs) brings its own bottom bar: now-playing quick add + confirm.
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                } else {
                    slideInHorizontally { width -> -width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> width } + fadeOut()
                }
            },
            modifier = Modifier.padding(top = paddingValues.calculateTopPadding()),
            label = "Step Transition"
        ) { step ->
            if (step == 0) {
                AnimatedContent(
                    targetState = showCropUi,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(260, delayMillis = 40)) +
                            scaleIn(initialScale = 0.94f, animationSpec = editLayoutSpring())) togetherWith
                            fadeOut(animationSpec = tween(160))
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "create_adjust_switch"
                ) { adjusting ->
                    if (adjusting) {
                        PlaylistCoverAdjustContent(
                            editor = coverEditor,
                            showFilters = showFilters,
                            onShowFiltersChange = { showFilters = it },
                            onChangeImage = { imagePickerLauncher.launch("image/*") },
                            onAddLayer = { layerPickerLauncher.launch("image/*") },
                            isAddingLayer = isAddingLayer,
                            onDone = closeAdjust
                        )
                    } else {
                        val isManual = creationMode == PlaylistCreationMode.MANUAL
                        EditPlaylistFormContent(
                            playlistName = playlistName,
                            onNameChange = { playlistName = it },
                            selectedTab = selectedTab,
                            onTabChange = { selectedTab = it },
                            hasImage = selectedImageUri != null,
                            imageBitmap = imageBitmap,
                            coverLayers = coverEditor.layers,
                            onOpenAdjust = { if (imageBitmap != null) showCropUi = true },
                            onChangeImage = { imagePickerLauncher.launch("image/*") },
                            onRemoveImage = { selectedImageUri = null },
                            // Manual: Next → pick songs. Smart: create straight away.
                            onSave = {
                                if (playlistName.isNotBlank()) {
                                    if (isManual) currentStep = 1 else submit(emptyList(), selectedSmartRule.storageKey)
                                }
                            },
                            primaryLabel = stringResource(
                                if (isManual) R.string.playlist_creation_next else R.string.playlist_creation_create
                            ),
                            primaryIcon = if (isManual) Icons.AutoMirrored.Rounded.ArrowForward else Icons.Rounded.Check,
                            primaryEnabled = playlistName.isNotBlank(),
                            belowName = {
                                CreationModeControls(
                                    creationMode = creationMode,
                                    onCreationModeChange = { creationMode = it },
                                    selectedSmartRule = selectedSmartRule,
                                    onSmartRuleChange = { selectedSmartRule = it }
                                )
                            },
                            iconPreview = { size ->
                                PlaylistIconCoverPreview(
                                    selectedShapeType = selectedShapeType,
                                    selectedColor = selectedColor,
                                    selectedIconName = selectedIconName,
                                    smoothRectCornerRadius = smoothRectCornerRadius,
                                    smoothRectSmoothness = smoothRectSmoothness,
                                    starSides = starSides,
                                    starCurve = starCurve,
                                    starRotation = starRotation,
                                    starScale = starScale,
                                    size = size
                                )
                            },
                            iconControls = {
                                PlaylistIconCustomizationControls(
                                    selectedColor = selectedColor,
                                    onColorChange = { selectedColor = it },
                                    selectedIconName = selectedIconName,
                                    onIconChange = { selectedIconName = it },
                                    selectedShapeType = selectedShapeType,
                                    onShapeTypeChange = { selectedShapeType = it },
                                    smoothRectCornerRadius = smoothRectCornerRadius,
                                    onSmoothRectCornerRadiusChange = { smoothRectCornerRadius = it },
                                    smoothRectSmoothness = smoothRectSmoothness,
                                    onSmoothRectSmoothnessChange = { smoothRectSmoothness = it },
                                    starSides = starSides,
                                    onStarSidesChange = { starSides = it },
                                    starCurve = starCurve,
                                    onStarCurveChange = { starCurve = it },
                                    starRotation = starRotation,
                                    onStarRotationChange = { starRotation = it },
                                    starScale = starScale,
                                    onStarScaleChange = { starScale = it }
                                )
                            }
                        )
                    }
                }
            } else {
                // Same Add songs screen as adding to an existing playlist: search, filter chips,
                // checkmarks, and the now-playing quick add docked beside the confirm button.
                SongPickerContent(
                    selectedSongIds = selectedSongIds,
                    onConfirm = { ids -> submit(ids.toList(), null) },
                    playerViewModel = playerViewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    showTitle = false,
                    confirmContentDescription = stringResource(R.string.playlist_creation_create)
                )
            }
        }
    }
}

/** Shared spring for the edit screen's layout motion (expressive, slightly under-damped). */
private fun <T> editLayoutSpring() = coverEditLayoutSpring<T>()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlaylistContent(
    initialName: String,
    initialImageUri: String?,
    initialColor: Int?,
    initialIconName: String?,
    initialShapeType: PlaylistShapeType?,
    initialShapeDetail1: Float?,
    initialShapeDetail2: Float?,
    initialShapeDetail3: Float?,
    initialShapeDetail4: Float?,
    onDismiss: () -> Unit,
    onSave: (String, String?, Int?, String?, Float, Float, Float, String?, Float?, Float?, Float?, Float?, PlaylistCoverImageEdits) -> Unit,
    onEditTransitionClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    
    // Initial State Setup
    var playlistName by remember { mutableStateOf(initialName) }
    
    // 0=Default, 1=Image, 2=Icon. Image wins, then a custom colour/icon, else Default.
    var selectedTab by remember { 
        mutableStateOf(
            when {
                initialImageUri != null -> 1 
                initialColor != null || initialIconName != null -> 2
                else -> 0
            }
        )
    }

    var selectedImageUri by remember { mutableStateOf<Uri?>(initialImageUri?.let { Uri.parse(it) }) }
    var showCropUi by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var imageBitmap by remember(selectedImageUri) { mutableStateOf<ImageBitmap?>(null) }
    
    // Layers start neutral when editing: the saved cover already has its edits baked in.
    val coverEditor = rememberCoverEditorState()
    var isAddingLayer by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val defaultColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    var selectedColor by remember { mutableStateOf(initialColor ?: defaultColor) }
    var selectedIconName by remember { mutableStateOf(initialIconName ?: "MusicNote") }

    var selectedShapeType by remember { mutableStateOf(initialShapeType ?: PlaylistShapeType.Circle) }
    
    // Shape Params
    var smoothRectCornerRadius by remember { androidx.compose.runtime.mutableFloatStateOf(initialShapeDetail1 ?: 20f) }
    var smoothRectSmoothness by remember { androidx.compose.runtime.mutableFloatStateOf(initialShapeDetail2 ?: 60f) }
    
    var starCurve by remember { androidx.compose.runtime.mutableDoubleStateOf(initialShapeDetail1?.toDouble() ?: 0.15) }
    var starRotation by remember { androidx.compose.runtime.mutableFloatStateOf(initialShapeDetail2 ?: 0f) }
    var starScale by remember { androidx.compose.runtime.mutableFloatStateOf(initialShapeDetail3 ?: 1f) }
    var starSides by remember { androidx.compose.runtime.mutableIntStateOf(initialShapeDetail4?.toInt() ?: 5) }

    // Image Loader
    LaunchedEffect(selectedImageUri) {
         if (selectedImageUri != null) {
             val loader = ImageLoader(context)
             val request = ImageRequest.Builder(context)
                 .data(selectedImageUri)
                 .allowHardware(false)
                 .build()
             val result = loader.execute(request)
             val drawable = result.drawable
             if (drawable is android.graphics.drawable.BitmapDrawable) {
                 val loaded = drawable.bitmap.asImageBitmap()
                 imageBitmap = loaded
                 coverEditor.setBaseImage(loaded)
             }
         } else {
             imageBitmap = null
             coverEditor.clear()
         }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            // A new photo starts upright and unmirrored; the filter and any overlays are kept.
            coverEditor.resetBaseForNewImage()
            showCropUi = true
            selectedTab = 1 // Force switch to image tab
        }
    }

    // "+" in Layers mode: stacks another image over the cover.
    val layerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isAddingLayer = true
        scope.launch {
            try {
                loadLayerBitmap(context, uri)?.let(coverEditor::addOverlay)
            } finally {
                isAddingLayer = false
            }
        }
    }

    val isAdjusted = coverEditor.isAdjusted
    val resetAdjustments: () -> Unit = { coverEditor.resetAll() }
    val closeAdjust: () -> Unit = {
        showFilters = false
        showCropUi = false
    }

    // Everything Save would write; compared with the opening snapshot to detect unsaved edits.
    val (d1, d2, d3, d4) = if (selectedTab == 2) {
        when (selectedShapeType) {
            PlaylistShapeType.SmoothRect -> Quadruple(smoothRectCornerRadius, smoothRectSmoothness, 0f, 0f)
            PlaylistShapeType.Star -> Quadruple(starCurve.toFloat(), starRotation, starScale, starSides.toFloat())
            else -> Quadruple(0f, 0f, 0f, 0f)
        }
    } else Quadruple(null, null, null, null)
    val savePayload = EditPlaylistSavePayload(
        name = playlistName,
        imageUri = if (selectedTab == 1) selectedImageUri?.toString() else null,
        color = if (selectedTab == 2) selectedColor else null,
        iconName = if (selectedTab == 2) selectedIconName else null,
        cropScale = if (selectedTab == 1) coverEditor.cropScale else 1f,
        panX = if (selectedTab == 1) coverEditor.cropOffset.x else 0f,
        panY = if (selectedTab == 1) coverEditor.cropOffset.y else 0f,
        shapeType = if (selectedTab == 2) selectedShapeType.name else null,
        d1 = d1, d2 = d2, d3 = d3, d4 = d4,
        imageEdits = if (selectedTab == 1) {
            PlaylistCoverImageEdits(
                flipHorizontal = coverEditor.isBaseFlipped,
                filterId = coverEditor.baseFilterId,
                rotationDegrees = coverEditor.baseRotationDegrees
            )
        } else PlaylistCoverImageEdits(),
        // Overlays / stretch aren't in the legacy fields above; this catches those edits.
        layersSignature = if (selectedTab == 1 && coverEditor.needsComposite) coverEditor.signature else null
    )
    // (Layer 1 loading doesn't change the payload: an untouched layer saves as scale 1, no edits.)
    val initialSavePayload = remember { savePayload }
    val hasUnsavedChanges = savePayload != initialSavePayload

    /** Saves (rendering a layered cover first, off the main thread), then runs [then]. */
    val performSave: (then: () -> Unit) -> Unit = { then ->
        if (!isSaving) {
            isSaving = true
            val p = savePayload
            scope.launch {
                try {
                    val cover = resolvePlaylistCover(context, coverEditor, p.imageUri)
                    onSave(p.name, cover.imageUri, p.color, p.iconName, cover.cropScale, cover.pan.x, cover.pan.y, p.shapeType, p.d1, p.d2, p.d3, p.d4, cover.edits)
                    then()
                } finally {
                    isSaving = false
                }
            }
        }
    }

    // Opening transitions closes this screen: offer to save first when something changed.
    var showUnsavedTransitionPrompt by remember { mutableStateOf(false) }
    val openTransitionEditor: () -> Unit = {
        if (hasUnsavedChanges) showUnsavedTransitionPrompt = true else onEditTransitionClick?.invoke()
    }
    if (showUnsavedTransitionPrompt && onEditTransitionClick != null) {
        AlertDialog(
            onDismissRequest = { showUnsavedTransitionPrompt = false },
            icon = { Icon(painterResource(R.drawable.outline_graph_1_24), contentDescription = null) },
            title = { Text("Save changes?") },
            text = { Text("Save your edits to this playlist before opening transitions? Unsaved changes will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedTransitionPrompt = false
                    // The transition editor replaces this screen, so open it only once saved.
                    performSave { onEditTransitionClick() }
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showUnsavedTransitionPrompt = false }) { Text("Cancel") }
                    TextButton(onClick = {
                        showUnsavedTransitionPrompt = false
                        onEditTransitionClick()
                    }) { Text("Discard") }
                }
            }
        )
    }

    BackHandler(enabled = showCropUi) {
        if (showFilters) showFilters = false else closeAdjust()
    }

    val topBarButtonColors = IconButtonDefaults.iconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    AnimatedContent(
                        targetState = showCropUi,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220, delayMillis = 60)) +
                                slideInVertically(animationSpec = editLayoutSpring()) { it / 2 }) togetherWith
                                (fadeOut(animationSpec = tween(140)) +
                                    slideOutVertically(animationSpec = editLayoutSpring()) { -it / 2 })
                        },
                        label = "edit_playlist_title"
                    ) { adjusting ->
                        Text(
                            text = if (adjusting) "Adjust" else stringResource(R.string.playlist_creation_edit),
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 6.dp),
                        colors = topBarButtonColors,
                        onClick = {
                            if (showCropUi) closeAdjust() else onDismiss()
                        }
                    ) {
                        Icon(
                            if (showCropUi) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Close,
                            contentDescription = stringResource(if (showCropUi) R.string.playlist_creation_cd_back_or_cancel else R.string.common_close)
                        )
                    }
                },
                actions = {
                    AnimatedContent(
                        targetState = showCropUi,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(200, delayMillis = 60)) +
                                scaleIn(initialScale = 0.6f, animationSpec = editLayoutSpring())) togetherWith
                                (fadeOut(animationSpec = tween(120)) + scaleOut(targetScale = 0.6f))
                        },
                        label = "edit_playlist_actions"
                    ) { adjusting ->
                        if (adjusting) {
                            // Undo every adjustment: zoom, pan, flip and filter.
                            FilledIconButton(
                                modifier = Modifier.padding(end = 6.dp),
                                colors = topBarButtonColors,
                                enabled = isAdjusted,
                                onClick = resetAdjustments
                            ) {
                                Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset adjustments")
                            }
                        } else if (onEditTransitionClick != null) {
                            FilledIconButton(
                                modifier = Modifier.padding(end = 6.dp),
                                colors = topBarButtonColors,
                                onClick = openTransitionEditor
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.outline_graph_1_24),
                                    contentDescription = stringResource(R.string.playlist_action_set_default_transition)
                                )
                            }
                        } else {
                            Spacer(Modifier.size(1.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        AnimatedContent(
            targetState = showCropUi,
            transitionSpec = {
                (fadeIn(animationSpec = tween(260, delayMillis = 40)) +
                    scaleIn(initialScale = 0.94f, animationSpec = editLayoutSpring())) togetherWith
                    fadeOut(animationSpec = tween(160))
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding()),
            label = "edit_adjust_switch"
        ) { adjusting ->
            if (adjusting) {
                PlaylistCoverAdjustContent(
                    editor = coverEditor,
                    showFilters = showFilters,
                    onShowFiltersChange = { showFilters = it },
                    onChangeImage = { imagePickerLauncher.launch("image/*") },
                    onAddLayer = { layerPickerLauncher.launch("image/*") },
                    isAddingLayer = isAddingLayer,
                    onDone = closeAdjust
                )
            } else {
                EditPlaylistFormContent(
                    playlistName = playlistName,
                    onNameChange = { playlistName = it },
                    selectedTab = selectedTab,
                    onTabChange = { selectedTab = it },
                    hasImage = selectedImageUri != null,
                    imageBitmap = imageBitmap,
                    coverLayers = coverEditor.layers,
                    onOpenAdjust = { if (imageBitmap != null) showCropUi = true },
                    onChangeImage = { imagePickerLauncher.launch("image/*") },
                    onRemoveImage = { selectedImageUri = null },
                    onSave = { performSave {} },
                    iconPreview = { size ->
                        PlaylistIconCoverPreview(
                            selectedShapeType = selectedShapeType,
                            selectedColor = selectedColor,
                            selectedIconName = selectedIconName,
                            smoothRectCornerRadius = smoothRectCornerRadius,
                            smoothRectSmoothness = smoothRectSmoothness,
                            starSides = starSides,
                            starCurve = starCurve,
                            starRotation = starRotation,
                            starScale = starScale,
                            size = size
                        )
                    },
                    iconControls = {
                        PlaylistIconCustomizationControls(
                            selectedColor = selectedColor,
                            onColorChange = { selectedColor = it },
                            selectedIconName = selectedIconName,
                            onIconChange = { selectedIconName = it },
                            selectedShapeType = selectedShapeType,
                            onShapeTypeChange = { selectedShapeType = it },
                            smoothRectCornerRadius = smoothRectCornerRadius,
                            onSmoothRectCornerRadiusChange = { smoothRectCornerRadius = it },
                            smoothRectSmoothness = smoothRectSmoothness,
                            onSmoothRectSmoothnessChange = { smoothRectSmoothness = it },
                            starSides = starSides,
                            onStarSidesChange = { starSides = it },
                            starCurve = starCurve,
                            onStarCurveChange = { starCurve = it },
                            starRotation = starRotation,
                            onStarRotationChange = { starRotation = it },
                            starScale = starScale,
                            onStarScaleChange = { starScale = it }
                        )
                    }
                )
            }
        }
    }
}

/**
 * Edit playlist body: the cover sits centred in whatever space is left above the controls and
 * resizes / rises as the Icon controls expand upward from the mode tabs. Tabs and the action
 * bar stay pinned to the bottom.
 */
@Composable
private fun EditPlaylistFormContent(
    modifier: Modifier = Modifier,
    playlistName: String,
    onNameChange: (String) -> Unit,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    hasImage: Boolean,
    imageBitmap: ImageBitmap?,
    /** The cover's layer stack (Layer 1 first) as edited on the Adjust screen. */
    coverLayers: List<CoverArtLayer>,
    onOpenAdjust: () -> Unit,
    onChangeImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onSave: () -> Unit,
    iconPreview: @Composable (Dp) -> Unit,
    iconControls: @Composable () -> Unit,
    /** Main button; defaults to Save (New playlist uses Next / Create). */
    primaryLabel: String? = null,
    primaryIcon: ImageVector = Icons.Rounded.Check,
    primaryEnabled: Boolean = true,
    /** Extra controls under the name (New playlist: Manual / Smart). */
    belowName: (@Composable () -> Unit)? = null
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxControlsHeight = maxHeight * 0.48f
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
        ) {
            // COVER + NAME
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val nameBlock = 96.dp
                val coverSize = minOf(maxHeight - nameBlock - 24.dp, 220.dp).coerceAtLeast(0.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (coverSize >= 56.dp) {
                        EditPlaylistCoverPreview(
                            size = coverSize,
                            selectedTab = selectedTab,
                            hasImage = hasImage,
                            imageBitmap = imageBitmap,
                            coverLayers = coverLayers,
                            onOpenAdjust = onOpenAdjust,
                            onPickImage = onChangeImage,
                            iconPreview = iconPreview
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = onNameChange,
                        label = { Text(stringResource(R.string.playlist_creation_name_label)) },
                        placeholder = { Text(stringResource(R.string.playlist_creation_name_placeholder)) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                    if (belowName != null) {
                        Spacer(Modifier.height(12.dp))
                        belowName()
                    }
                }
            }

            // ICON CONTROLS: rise from the tabs (colour → symbol → shape + sliders).
            AnimatedVisibility(
                visible = selectedTab == 2,
                enter = expandVertically(expandFrom = Alignment.Bottom, animationSpec = editLayoutSpring()) +
                    fadeIn(animationSpec = tween(220, delayMillis = 80)),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = editLayoutSpring()) +
                    fadeOut(animationSpec = tween(120))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxControlsHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp, bottom = 12.dp)
                ) {
                    iconControls()
                }
            }

            // MODE TABS
            val tabs = listOf(
                stringResource(R.string.playlist_creation_tab_default),
                stringResource(R.string.playlist_creation_tab_image),
                stringResource(R.string.playlist_creation_tab_icon)
            )
            ExpressiveButtonGroup(
                items = tabs,
                selectedIndex = selectedTab,
                onItemClick = onTabChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 8.dp)
            )

            // Separates the Default · Image · Icon choice from the actions below it.
            EditActionsDivider()

            // ACTION BAR
            EditPlaylistActionBar(
                showImageActions = selectedTab == 1 && hasImage,
                onChangeImage = onChangeImage,
                onSave = onSave,
                onRemoveImage = onRemoveImage,
                primaryLabel = primaryLabel ?: stringResource(R.string.common_save),
                primaryIcon = primaryIcon,
                primaryEnabled = primaryEnabled
            )
        }
    }
}

@Composable
private fun EditPlaylistCoverPreview(
    size: Dp,
    selectedTab: Int,
    hasImage: Boolean,
    imageBitmap: ImageBitmap?,
    coverLayers: List<CoverArtLayer>,
    onOpenAdjust: () -> Unit,
    onPickImage: () -> Unit,
    iconPreview: @Composable (Dp) -> Unit
) {
    val coverShape = RoundedCornerShape(percent = 16)
    AnimatedContent(
        targetState = selectedTab,
        transitionSpec = {
            (fadeIn(animationSpec = tween(220, delayMillis = 40)) +
                scaleIn(initialScale = 0.9f, animationSpec = editLayoutSpring())) togetherWith
                (fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.9f))
        },
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size),
        label = "edit_cover_preview"
    ) { tab ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (tab) {
                0 -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(coverShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.GridView,
                            contentDescription = null,
                            modifier = Modifier.size(size * 0.36f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        if (size >= 140.dp) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.playlist_creation_auto_collage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
                1 -> when {
                    imageBitmap != null && coverLayers.isNotEmpty() -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(coverShape)
                            .clickable(onClick = onOpenAdjust)
                    ) {
                        CoverLayersCanvas(
                            layers = coverLayers,
                            modifier = Modifier.fillMaxSize(),
                            enabled = false
                        )
                        // "Tap to adjust" affordance
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Crop,
                                contentDescription = "Adjust cover",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    hasImage -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(coverShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                    else -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(coverShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable(onClick = onPickImage),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.AddPhotoAlternate,
                                contentDescription = stringResource(R.string.playlist_creation_cd_add_photo),
                                modifier = Modifier.size(size * 0.28f),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            if (size >= 120.dp) {
                                Spacer(Modifier.height(12.dp))
                                Text(stringResource(R.string.playlist_creation_pick_image), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
                else -> iconPreview(size)
            }
        }
    }
}

/** Save is full width; on the Image tab with a picture it makes room for Change and Remove. */
@Composable
private fun EditPlaylistActionBar(
    showImageActions: Boolean,
    onChangeImage: () -> Unit,
    onSave: () -> Unit,
    onRemoveImage: () -> Unit,
    primaryLabel: String,
    primaryIcon: ImageVector = Icons.Rounded.Check,
    primaryEnabled: Boolean = true
) {
    // Change and Remove are icon-only at the two ends; Save takes all the width between them.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = showImageActions,
            enter = editSideButtonEnter(Alignment.Start),
            exit = editSideButtonExit(Alignment.Start)
        ) {
            FilledTonalIconButton(
                onClick = onChangeImage,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(EditSaveButtonHeight),
                shape = CircleShape
            ) {
                Icon(
                    Icons.Rounded.AddPhotoAlternate,
                    contentDescription = stringResource(R.string.playlist_creation_change),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Button(
            onClick = onSave,
            enabled = primaryEnabled,
            modifier = Modifier
                .weight(1f)
                .height(EditSaveButtonHeight),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            Icon(primaryIcon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                primaryLabel,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        AnimatedVisibility(
            visible = showImageActions,
            enter = editSideButtonEnter(Alignment.End),
            exit = editSideButtonExit(Alignment.End)
        ) {
            FilledIconButton(
                onClick = onRemoveImage,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(EditSaveButtonHeight),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.playlist_creation_remove),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/** Height of Edit playlist's Save button; the icon buttons beside it are square at this size. */
private val EditSaveButtonHeight = 64.dp

/** Separator above the bottom action row, shared by Edit playlist and Adjust so they match. */
@Composable
private fun EditActionsDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    )
}

/** Side icon buttons grow out from their edge on the layout spring, fading in a beat later. */
private fun editSideButtonEnter(from: Alignment.Horizontal) =
    expandHorizontally(expandFrom = from, animationSpec = editLayoutSpring()) +
        fadeIn(
            animationSpec = tween(
                MotionTokens.DurationMedium1,
                delayMillis = MotionTokens.DurationShort2,
                easing = MotionTokens.EmphasizedDecelerate
            )
        )

private fun editSideButtonExit(towards: Alignment.Horizontal) =
    shrinkHorizontally(shrinkTowards = towards, animationSpec = editLayoutSpring()) +
        fadeOut(animationSpec = tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))

/**
 * Adjust screen. Everything edits the focused layer:
 *  - Transform: pinch to zoom, drag to move. Stretch: two-axis pinch or edge / corner handles.
 *  - Layers: "+" stacks another image; the strip above the cover (Layer 1 | overlays) focuses,
 *    re-stacks and removes layers; a tap on the cover focuses the layer under the finger.
 *  - Rotate / flip in the corner and the Filters panel apply to the focused layer too.
 * Opening filters slides the viewport up, reveals era/mood/mono presets and turns Done into Apply.
 */
@Composable
private fun PlaylistCoverAdjustContent(
    modifier: Modifier = Modifier,
    editor: CoverEditorState,
    showFilters: Boolean,
    onShowFiltersChange: (Boolean) -> Unit,
    onChangeImage: () -> Unit,
    onAddLayer: () -> Unit,
    isAddingLayer: Boolean,
    onDone: () -> Unit
) {
    val viewportBias by animateFloatAsState(
        targetValue = if (showFilters) -1f else 0f,
        animationSpec = editLayoutSpring(),
        label = "adjust_viewport_bias"
    )
    val focused = editor.focusedLayer
    val showStrip = editor.overlays.isNotEmpty() || editor.mode == CoverAdjustMode.Layers
    // The open tool/strength slider, hoisted out of the filters panel: its ruler sits above the
    // bottom row, its value left of Apply and its reset right of Apply.
    val sliderState = remember { com.theveloper.pixelplay.presentation.components.CoverSliderState() }
    val sliderResetter = com.theveloper.pixelplay.presentation.components.rememberCoverSliderResetter()
    LaunchedEffect(showFilters) {
        if (!showFilters) {
            sliderResetter.cancel()
            sliderState.open(null)
        }
    }
    // Switching tool (or closing) stops a running reset where it is.
    LaunchedEffect(sliderState.target) { sliderResetter.cancel() }
    val sliderOpen = showFilters && sliderState.target != null && focused != null
    val sliderSpec = com.theveloper.pixelplay.presentation.components.coverSliderSpec(
        layer = focused,
        target = sliderState.shown,
        onFilterSelect = editor::setFocusedFilter,
        onStrengthChange = editor::setFocusedStrength,
        onAdjustChange = editor::setFocusedAdjust
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        // TOP: a one-line hint for the current mode (the layer strip sits on the cover itself).
        AnimatedVisibility(
            visible = !showFilters && !showStrip,
            enter = expandVertically(animationSpec = editLayoutSpring()) + fadeIn(),
            exit = shrinkVertically(animationSpec = editLayoutSpring()) + fadeOut(animationSpec = tween(100))
        ) {
            AnimatedContent(
                targetState = editor.mode,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "adjust_hint"
            ) { mode ->
                Text(
                    text = when (mode) {
                        CoverAdjustMode.Stretch -> "Pinch or drag the handles to stretch"
                        else -> "Pinch to zoom · drag to move"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 8.dp)
                )
            }
        }

        // VIEWPORT: the cover (the layer strip rides just above it, as wide as it) and, while
        // filters are open, the filters panel right under it. Closed, the group is centred; open,
        // it rides to the top, so the tabs and presets sit just below the cover and the free space
        // ends up above Apply. The cover gives up exactly the strip's and the panel's height.
        val stripSpace by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (showStrip && editor.base != null) CoverLayerStripHeight else 0.dp,
            animationSpec = tween(220),
            label = "layer_strip_space"
        )
        if (editor.base != null && focused != null) {
            Layout(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
                content = {
                    // 0: layer strip
                    Box {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showStrip,
                            enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(200, delayMillis = 60)),
                            exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(120))
                        ) {
                            CoverLayerStrip(
                                base = editor.base,
                                overlays = editor.overlays,
                                focusedLayerId = focused.id,
                                onFocus = editor::focus,
                                onMove = editor::moveOverlay,
                                onRemove = editor::removeOverlay,
                                onAddLayer = onAddLayer,
                                isAddingLayer = isAddingLayer,
                                horizontalPadding = 0.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp)
                            )
                        }
                    }
                    // 1: cover
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(32.dp))
                    ) {
                        CoverLayersCanvas(
                            layers = editor.layers,
                            modifier = Modifier.fillMaxSize(),
                            focusedLayerId = focused.id,
                            mode = editor.mode,
                            enabled = true,
                            onFocusLayer = editor::focus,
                            onLayerChange = editor::update,
                            onFlipToggle = editor::toggleFlipFocused
                        )
                        // Rotate 90° clockwise and flip (the focused layer), side by side in the corner.
                        val isFlipped = focused.isFlipped
                        val flipRotation by animateFloatAsState(
                            targetValue = if (isFlipped) 180f else 0f,
                            animationSpec = editLayoutSpring(),
                            label = "flip_icon_rotation"
                        )
                        val flipContainer by animateColorAsState(
                            targetValue = if (isFlipped) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                            label = "flip_button_container"
                        )
                        val flipContent by animateColorAsState(
                            targetValue = if (isFlipped) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                            label = "flip_button_content"
                        )
                        val rotated = focused.normalizedRotation != 0
                        val rotateContainer by animateColorAsState(
                            targetValue = if (rotated) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                            label = "rotate_button_container"
                        )
                        val rotateContent by animateColorAsState(
                            targetValue = if (rotated) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                            label = "rotate_button_content"
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledIconButton(
                                onClick = editor::rotateFocused,
                                modifier = Modifier.size(40.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = rotateContainer,
                                    contentColor = rotateContent
                                )
                            ) {
                                Icon(
                                    Icons.Rounded.Rotate90DegreesCw,
                                    contentDescription = "Rotate 90 degrees",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            FilledIconButton(
                                onClick = editor::toggleFlipFocused,
                                modifier = Modifier.size(40.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = flipContainer,
                                    contentColor = flipContent
                                )
                            ) {
                                Icon(
                                    Icons.Rounded.Flip,
                                    contentDescription = "Flip horizontally",
                                    modifier = Modifier
                                        .size(20.dp)
                                        .graphicsLayer { rotationY = flipRotation }
                                )
                            }
                        }
                    }
                    // 2: filters panel (for the focused layer)
                    Box {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showFilters,
                            enter = expandVertically(expandFrom = Alignment.Top, animationSpec = editLayoutSpring()) +
                                fadeIn(
                                    animationSpec = tween(
                                        MotionTokens.DurationMedium1,
                                        delayMillis = MotionTokens.DurationShort2,
                                        easing = MotionTokens.EmphasizedDecelerate
                                    )
                                ),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = editLayoutSpring()) +
                                fadeOut(animationSpec = tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
                        ) {
                            CoverFiltersPanel(
                                layer = focused,
                                onFilterSelect = editor::setFocusedFilter,
                                onStrengthChange = editor::setFocusedStrength,
                                onAdjustChange = editor::setFocusedAdjust,
                                sliderState = sliderState
                            )
                        }
                    }
                }
            ) { measurables, constraints ->
                val sidePadding = 16.dp.roundToPx()
                val maxW = constraints.maxWidth
                val maxH = constraints.maxHeight
                val panel = measurables[2].measure(Constraints(maxWidth = maxW, maxHeight = maxH))
                val side = minOf(maxW - 2 * sidePadding, maxH - stripSpace.roundToPx() - panel.height)
                    .coerceAtLeast(0)
                val strip = measurables[0].measure(Constraints(minWidth = side, maxWidth = side, maxHeight = maxH))
                val cover = measurables[1].measure(Constraints.fixed(side, side))
                layout(maxW, maxH) {
                    // Read here (placement only), so the bias animation never re-measures.
                    val free = (maxH - strip.height - side - panel.height).coerceAtLeast(0)
                    val top = (free * (1f + viewportBias) / 2f).roundToInt().coerceIn(0, free)
                    val x = (maxW - side) / 2
                    strip.placeRelative(x, top)
                    cover.placeRelative(x, top + strip.height)
                    panel.placeRelative(0, top + strip.height + side)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }

        // MODES: Transform · Stretch · Layers (hidden while the filters panel has the space).
        AnimatedVisibility(
            visible = !showFilters,
            enter = expandVertically(animationSpec = editLayoutSpring()) + fadeIn(animationSpec = tween(200, delayMillis = 60)),
            exit = shrinkVertically(animationSpec = editLayoutSpring()) + fadeOut(animationSpec = tween(100))
        ) {
            ExpressiveButtonGroup(
                items = CoverAdjustMode.entries.map { it.label },
                selectedIndex = editor.mode.ordinal,
                onItemClick = { editor.mode = CoverAdjustMode.entries[it] },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 4.dp)
            )
        }

        // RULER: the open tool's value ruler, low down right above the bottom row (no pill).
        AnimatedVisibility(
            visible = sliderOpen,
            enter = expandVertically(expandFrom = Alignment.Bottom, animationSpec = editLayoutSpring()) +
                fadeIn(tween(MotionTokens.DurationMedium1, easing = MotionTokens.EmphasizedDecelerate)),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = editLayoutSpring()) +
                fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
        ) {
            sliderSpec?.let { spec ->
                com.theveloper.pixelplay.presentation.components.CoverAdjustRuler(
                    spec = spec,
                    resetter = sliderResetter,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        // CONTROLS: Change · Done · Filters. With filters open, Change and Filters fold away and
        // Done turns into a centred Apply pill, so the panel gets all the room. With a slider
        // open: value · Apply · reset.
        val applyCompact by animateFloatAsState(
            targetValue = if (showFilters) 1f else 0f,
            animationSpec = editLayoutSpring(),
            label = "apply_compact"
        )
        // Same separator as Edit playlist, above the bottom row; not needed while a slider is open
        // (the ruler sits there instead).
        AnimatedVisibility(
            visible = !sliderOpen,
            enter = expandVertically(animationSpec = editLayoutSpring()) +
                fadeIn(tween(MotionTokens.DurationShort4, easing = MotionTokens.EmphasizedDecelerate)),
            exit = shrinkVertically(animationSpec = editLayoutSpring()) +
                fadeOut(tween(MotionTokens.DurationShort3, easing = MotionTokens.EmphasizedAccelerate))
        ) {
            EditActionsDivider()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = !showFilters,
                enter = editSideButtonEnter(Alignment.Start),
                exit = editSideButtonExit(Alignment.Start)
            ) {
                FilledTonalIconButton(
                    onClick = onChangeImage,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(56.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Rounded.AddPhotoAlternate,
                        contentDescription = stringResource(R.string.playlist_creation_change),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            // Slider open: its value (or the tool's icon) left of Apply.
            AnimatedVisibility(
                visible = sliderOpen,
                enter = editSideButtonEnter(Alignment.Start),
                exit = editSideButtonExit(Alignment.Start)
            ) {
                sliderSpec?.let { spec ->
                    com.theveloper.pixelplay.presentation.components.CoverAdjustValueBubble(
                        spec = spec,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            val doneContainer by animateColorAsState(
                targetValue = if (showFilters) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                label = "done_apply_container"
            )
            val doneContent by animateColorAsState(
                targetValue = if (showFilters) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                label = "done_apply_content"
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Button(
                    onClick = { if (showFilters) onShowFiltersChange(false) else onDone() },
                    modifier = Modifier
                        .height(56.dp)
                        // Full width as Done; shrinks to a centred pill as Apply.
                        .layout { measurable, constraints ->
                            val full = constraints.maxWidth
                            val compact = ApplyPillWidth.roundToPx().coerceAtMost(full)
                            val width = (full + (compact - full) * applyCompact).roundToInt().coerceIn(0, full)
                            val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                            layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                        },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = doneContainer,
                        contentColor = doneContent
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    AnimatedContent(
                        targetState = showFilters,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(200, delayMillis = 40)) +
                                slideInVertically(animationSpec = editLayoutSpring()) { it }) togetherWith
                                (fadeOut(animationSpec = tween(120)) +
                                    slideOutVertically(animationSpec = editLayoutSpring()) { -it })
                        },
                        label = "done_apply_label"
                    ) { applying ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (applying) Icons.Rounded.DoneAll else Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (applying) "Apply" else stringResource(R.string.common_done),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Slider open: reset right of Apply (glides the value back to its default).
            AnimatedVisibility(
                visible = sliderOpen,
                enter = editSideButtonEnter(Alignment.End),
                exit = editSideButtonExit(Alignment.End)
            ) {
                sliderSpec?.let { spec ->
                    com.theveloper.pixelplay.presentation.components.CoverAdjustResetButton(
                        spec = spec,
                        resetter = sliderResetter,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            AnimatedVisibility(
                visible = !showFilters,
                enter = editSideButtonEnter(Alignment.End),
                exit = editSideButtonExit(Alignment.End)
            ) {
                FilledTonalIconButton(
                    onClick = { onShowFiltersChange(true) },
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(56.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Filters", modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/** Width of the Apply pill while the filters panel is open. */
private val ApplyPillWidth = 184.dp

/** Height the layer strip takes above the cover (chip row + its padding + the gap). */
private val CoverLayerStripHeight = 76.dp

/** What Save hands the playlist pipeline for the Image tab. */
private data class ResolvedPlaylistCover(
    val imageUri: String?,
    val cropScale: Float,
    val pan: Offset,
    val edits: PlaylistCoverImageEdits
)

/**
 * Turns the editor into the legacy save parameters. A plain photo (one layer, no stretch) keeps
 * the original crop pipeline; layered or stretched covers are rendered to an image first and
 * passed through untouched (scale 1, no pan, no extra edits).
 */
private suspend fun resolvePlaylistCover(
    context: android.content.Context,
    editor: CoverEditorState,
    imageUri: String?
): ResolvedPlaylistCover {
    if (imageUri == null) return ResolvedPlaylistCover(null, 1f, Offset.Zero, PlaylistCoverImageEdits())
    if (editor.needsComposite) {
        val composite = try {
            CoverLayerCompositor.renderToFile(context, editor.layers)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            android.util.Log.w("CreatePlaylistScreen", "Cover composite failed; saving the base layer only", e)
            null
        }
        if (composite != null) {
            return ResolvedPlaylistCover(Uri.fromFile(composite).toString(), 1f, Offset.Zero, PlaylistCoverImageEdits())
        }
    }
    return ResolvedPlaylistCover(
        imageUri = imageUri,
        cropScale = editor.cropScale,
        pan = editor.cropOffset,
        edits = PlaylistCoverImageEdits(
            flipHorizontal = editor.isBaseFlipped,
            filterId = editor.baseFilterId,
            rotationDegrees = editor.baseRotationDegrees
        )
    )
}

/** Loads a picked image for a new layer, capped in size (overlays never need full resolution). */
private suspend fun loadLayerBitmap(context: android.content.Context, uri: Uri): ImageBitmap? = try {
    val request = ImageRequest.Builder(context)
        .data(uri)
        .allowHardware(false)
        .size(1600)
        .build()
    (ImageLoader(context).execute(request).drawable as? android.graphics.drawable.BitmapDrawable)
        ?.bitmap?.asImageBitmap()
} catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}


/** New playlist: Manual (pick songs next) or Smart (filled by a rule), under the name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreationModeControls(
    creationMode: PlaylistCreationMode,
    onCreationModeChange: (PlaylistCreationMode) -> Unit,
    selectedSmartRule: SmartPlaylistRule,
    onSmartRuleChange: (SmartPlaylistRule) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
        ) {
            SegmentedButton(
                icon = {},
                selected = creationMode == PlaylistCreationMode.MANUAL,
                onClick = { onCreationModeChange(PlaylistCreationMode.MANUAL) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text(stringResource(R.string.playlist_creation_mode_manual))
            }
            SegmentedButton(
                icon = {},
                selected = creationMode == PlaylistCreationMode.SMART,
                onClick = { onCreationModeChange(PlaylistCreationMode.SMART) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text(stringResource(R.string.playlist_creation_mode_smart))
            }
        }

        AnimatedVisibility(
            visible = creationMode == PlaylistCreationMode.SMART,
            enter = expandVertically(animationSpec = editLayoutSpring()) + fadeIn(animationSpec = tween(200, delayMillis = 60)),
            exit = shrinkVertically(animationSpec = editLayoutSpring()) + fadeOut(animationSpec = tween(120))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.playlist_creation_smart_rule),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SmartPlaylistRule.entries.forEach { rule ->
                        FilterChip(
                            selected = selectedSmartRule == rule,
                            onClick = { onSmartRuleChange(rule) },
                            label = { Text(smartPlaylistRuleTitle(rule)) },
                            shape = CircleShape
                        )
                    }
                }
                Text(
                    text = smartPlaylistRuleSubtitle(selectedSmartRule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The Icon-mode cover: the chosen symbol on the chosen colour, clipped to the chosen shape. */
@Composable
internal fun PlaylistIconCoverPreview(
    selectedShapeType: PlaylistShapeType,
    selectedColor: Int?,
    selectedIconName: String?,
    smoothRectCornerRadius: Float,
    smoothRectSmoothness: Float,
    starSides: Int,
    starCurve: Double,
    starRotation: Float,
    starScale: Float,
    size: androidx.compose.ui.unit.Dp = 180.dp
) {
    AnimatedContent(
        targetState = selectedShapeType,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.8f) togetherWith 
            fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.8f)
        },
        label = "shape_transition"
    ) { currentShapeType ->
        val currentShape: Shape = when(currentShapeType) {
            PlaylistShapeType.Circle -> CircleShape
            PlaylistShapeType.SmoothRect -> AbsoluteSmoothCornerShape(
                cornerRadiusTL = smoothRectCornerRadius.dp,
                smoothnessAsPercentTR = smoothRectSmoothness.toInt(),
                cornerRadiusTR = smoothRectCornerRadius.dp,
                smoothnessAsPercentTL = smoothRectSmoothness.toInt(),
                cornerRadiusBR = smoothRectCornerRadius.dp,
                smoothnessAsPercentBR = smoothRectSmoothness.toInt(),
                cornerRadiusBL = smoothRectCornerRadius.dp,
                smoothnessAsPercentBL = smoothRectSmoothness.toInt(),
            )
            PlaylistShapeType.RotatedPill -> {
                androidx.compose.foundation.shape.GenericShape { size, _ ->
                    val w = size.width
                    val h = size.height
                    val pillW = w * 0.75f
                    val offset = (w - pillW) / 2
                    addRoundRect(RoundRect(offset, 0f, offset + pillW, h, CornerRadius(pillW/2, pillW/2)))
                }
            }
            PlaylistShapeType.Star -> RoundedStarShape(
                sides = starSides,
                curve = starCurve,
                rotation = starRotation
            )
        }
    
        val shapeMod = if(currentShapeType == PlaylistShapeType.RotatedPill) Modifier.graphicsLayer(rotationZ = 45f) else Modifier
        val iconMod = if(currentShapeType == PlaylistShapeType.RotatedPill) Modifier.graphicsLayer(rotationZ = -45f) else Modifier
        val scaleMod = if(currentShapeType == PlaylistShapeType.Star) Modifier.graphicsLayer(scaleX = starScale, scaleY = starScale) else Modifier

        Box(
            modifier = Modifier
                .size(size)
                .then(scaleMod)
                .then(shapeMod)
                .clip(currentShape)
                .background(selectedColor?.let { Color(it) }
                    ?: MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (selectedIconName != null) {
                val icon = getIconByName(selectedIconName) ?: Icons.Rounded.MusicNote
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(size * 0.44f)
                        .then(iconMod),
                    tint = selectedColor?.let { getThemeContentColor(it, MaterialTheme.colorScheme) } 
                          ?: MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/** Icon-mode controls, top to bottom: background colour, icon symbol, shape style + parameters. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlaylistIconCustomizationControls(
    modifier: Modifier = Modifier,
    selectedColor: Int?,
    onColorChange: (Int) -> Unit,
    selectedIconName: String?,
    onIconChange: (String) -> Unit,
    selectedShapeType: PlaylistShapeType,
    onShapeTypeChange: (PlaylistShapeType) -> Unit,
    smoothRectCornerRadius: Float,
    onSmoothRectCornerRadiusChange: (Float) -> Unit,
    smoothRectSmoothness: Float,
    onSmoothRectSmoothnessChange: (Float) -> Unit,
    starSides: Int,
    onStarSidesChange: (Int) -> Unit,
    starCurve: Double,
    onStarCurveChange: (Double) -> Unit,
    starRotation: Float,
    onStarRotationChange: (Float) -> Unit,
    starScale: Float,
    onStarScaleChange: (Float) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Colors
        Text(
            modifier = Modifier.padding(start = 22.dp),
            text = stringResource(R.string.playlist_creation_bg_color),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val colors = listOf( 
            MaterialTheme.colorScheme.primary.toArgb(),
            MaterialTheme.colorScheme.primaryContainer.toArgb(),
            MaterialTheme.colorScheme.secondary.toArgb(),
            MaterialTheme.colorScheme.secondaryContainer.toArgb(),
            MaterialTheme.colorScheme.tertiary.toArgb(),
            MaterialTheme.colorScheme.tertiaryContainer.toArgb(),
            MaterialTheme.colorScheme.error.toArgb(),
            MaterialTheme.colorScheme.errorContainer.toArgb(),
            MaterialTheme.colorScheme.surfaceContainerHigh.toArgb(),
            MaterialTheme.colorScheme.inverseSurface.toArgb()
        )

        FlowRow(
           horizontalArrangement = Arrangement.spacedBy(12.dp),
           verticalArrangement = Arrangement.spacedBy(12.dp),
           modifier = Modifier
               .fillMaxWidth()
               .padding(horizontal = 18.dp)
        ) {
            colors.forEach { color ->
                val isSelected = selectedColor == color
                val cornerRadius by animateDpAsState(targetValue = if (isSelected) 12.dp else 24.dp, label = "CornerRadius")
        
                Box(
                    modifier = Modifier
                        .size(52.dp)
                       .clip(RoundedCornerShape(cornerRadius))
                        .background(if (isSelected) Color(color) else Color.Transparent)
                       .border(
                           width = if (isSelected) 3.dp else 0.dp,
                           color = if (isSelected) Color(color) else Color.Transparent,
                           shape = RoundedCornerShape(cornerRadius)
                       ),
                    contentAlignment = Alignment.Center
                ) {
                     Box(
                        modifier = Modifier
                            .size(if (isSelected) 42.dp else 48.dp)
                           .clip(RoundedCornerShape(if (isSelected) 8.dp else cornerRadius))
                           .background(Color(color))
                            .clickable { onColorChange(color) }
                     )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Icons
        Text(
            modifier = Modifier.padding(start = 22.dp),
            text = stringResource(R.string.playlist_creation_icon_symbol),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val icons = listOf(
           "MusicNote", "Headphones", "Album", "Mic", "Speaker", "Favorite", "Piano", "Queue"
        )

        FlowRow(
           horizontalArrangement = Arrangement.spacedBy(12.dp),
           verticalArrangement = Arrangement.spacedBy(12.dp),
           modifier = Modifier
               .fillMaxWidth()
               .padding(horizontal = 18.dp)
        ) {
            icons.forEach { iconName ->
                val isSelected = selectedIconName == iconName
                val cornerRadius by animateDpAsState(targetValue = if (isSelected) 12.dp else 24.dp, label = "CornerRadius")

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { onIconChange(iconName) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getIconByName(iconName) ?: Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = if(isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Shapes
        Text(
            modifier = Modifier.padding(start = 22.dp),
            text = stringResource(R.string.playlist_creation_shape_style),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            item {
                PlaylistShapeType.entries.forEach { shapeType ->
                    val isSelected = selectedShapeType == shapeType
                    val previewShape = when(shapeType) {
                        PlaylistShapeType.Circle -> CircleShape
                        PlaylistShapeType.SmoothRect -> AbsoluteSmoothCornerShape(12.dp, 60, 12.dp, 60, 12.dp, 60, 12.dp, 60)
                        PlaylistShapeType.RotatedPill -> androidx.compose.foundation.shape.GenericShape { size, _ ->
                            val w = size.width
                            val h = size.height
                            val pillW = w * 0.75f
                            val offset = (w - pillW) / 2
                            addRoundRect(RoundRect(offset, 0f, offset + pillW, h, CornerRadius(pillW/2, pillW/2)))
                        }
                        PlaylistShapeType.Star -> RoundedStarShape(5, 0.15, 0f)
                    }

                    val rotationM = if(shapeType == PlaylistShapeType.RotatedPill) Modifier.graphicsLayer(rotationZ = 45f) else Modifier
                    val cornerRadius by animateDpAsState(targetValue = if (isSelected) 12.dp else 24.dp, label = "CornerRadius")

                    Row(modifier = Modifier.padding(2.dp)) {
                        Spacer(Modifier.width(2.dp))
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(cornerRadius))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                                .clickable { onShapeTypeChange(shapeType) }
                                .padding(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .then(rotationM)
                                    .clip(previewShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {}
                        }
                        Spacer(Modifier.width(2.dp))
                    }
                }
            }
        }

        // Params
        AnimatedVisibility(visible = selectedShapeType == PlaylistShapeType.SmoothRect) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.playlist_creation_shape_params), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ShapeParameterCard(stringResource(R.string.playlist_creation_corner_radius), smoothRectCornerRadius, 0f..50f, onSmoothRectCornerRadiusChange, { it.toInt().toString() })
                ShapeParameterCard(stringResource(R.string.playlist_creation_smoothness), smoothRectSmoothness, 0f..100f, onSmoothRectSmoothnessChange, { "${it.toInt()}%" })
            }
        }

        AnimatedVisibility(visible = selectedShapeType == PlaylistShapeType.Star) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.playlist_creation_shape_params), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ShapeParameterCard(stringResource(R.string.playlist_creation_sides), starSides.toFloat(), 3f..20f, { onStarSidesChange(it.toInt()) }, { it.toInt().toString() }, steps = 17)
                ShapeParameterCard(stringResource(R.string.playlist_creation_curve), starCurve.toFloat(), 0f..0.5f, { onStarCurveChange(it.toDouble()) }, { String.format("%.2f", it) })
                ShapeParameterCard(stringResource(R.string.playlist_creation_rotation), starRotation, 0f..360f, onStarRotationChange, { "${it.toInt()}°" })
                ShapeParameterCard(stringResource(R.string.playlist_creation_scale), starScale, 0.5f..1.5f, onStarScaleChange, { String.format("%.1fx", it) })
            }
        }
    }
}

fun getIconByName(name: String?): ImageVector? {
    return when (name) {
        "MusicNote" -> Icons.Rounded.MusicNote
        "Headphones" -> Icons.Rounded.Headphones
        "Album" -> Icons.Rounded.Album
        "Mic" -> Icons.Rounded.MicExternalOn 
        "Speaker" -> Icons.Rounded.Speaker
        "Favorite" -> Icons.Rounded.Favorite
        "Piano" -> Icons.Rounded.Piano
        "Queue" -> Icons.AutoMirrored.Rounded.QueueMusic
        else -> null
    }
}

@Composable
fun ExpressiveButtonGroup(
    items: List<String>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Transparent, CircleShape), // Optional container background
            //.padding(1.dp), // Check if padding is needed
        horizontalArrangement = Arrangement.spacedBy(4.dp) // Gap between buttons
    ) {
        items.forEachIndexed { index, title ->
            val isSelected = selectedIndex == index
            val shape = if (isSelected) CircleShape else RoundedCornerShape(10.dp) // Pill vs RoundedRect
            val containerColor by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "ButtonColor"
            )
            val contentColor by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                label = "ContentColor"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp) // Taller button
                    .clip(shape)
                    .background(containerColor)
                    .clickable { onItemClick(index) },
                contentAlignment = Alignment.Center
            ) {
                 Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                     Text(
                         text = title,
                         style = MaterialTheme.typography.labelLarge,
                         color = contentColor,
                         fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Medium
                     )
                 }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShapeParameterCard(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueDisplay: (Float) -> String,
    steps: Int = 0
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                     text = title,
                     style = MaterialTheme.typography.labelMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                     text = valueDisplay(value),
                     style = MaterialTheme.typography.labelLarge,
                     color = MaterialTheme.colorScheme.onSurface,
                     fontWeight = FontWeight.Bold
                )
            }
            
            // Custom Thick Slider
            ThickSlider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps
            )
        }
    }
}

@Composable
fun ThickSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
             thumbColor = MaterialTheme.colorScheme.primary,
             activeTrackColor = MaterialTheme.colorScheme.primary,
             inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        thumb = {
             Box(
                 modifier = Modifier
                    .size(width = 6.dp, height = 18.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
             )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(8.dp),
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = 4.dp,
                drawStopIndicator = null
            )
        }
    )
}


fun getThemeContentColor(colorArgb: Int, scheme: androidx.compose.material3.ColorScheme): Color {
    return resolvePlaylistCoverContentColor(colorArgb, scheme)
}

// End of file
