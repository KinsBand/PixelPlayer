package com.theveloper.pixelplay.presentation.components.lyrics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.theveloper.pixelplay.data.SongReaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Compact reactions for the lyrics screen: one small trigger in each bottom corner (positive
 * left, negative right). Pressing a trigger pops its reactions upwards out of it; they overlay the
 * empty corner space and never move or resize the lyrics.
 *
 *  - Tap the trigger to open, tap a reaction to pick it, tap the trigger again to close.
 *  - Or press, slide up over a reaction (it grows, with a light tick) and let go to pick it.
 *  - The open menu closes by itself after 3 s without interaction, but never while touched.
 *  - A tap anywhere else closes it at once (see [Modifier.dismissReactionMenuOnOutsideTap]).
 *  - Only one side is open at a time.
 */

enum class ReactionSide(val reactions: List<SongReaction>) {
    POSITIVE(SongReaction.positive),
    NEGATIVE(SongReaction.negative),
}

@Stable
class ReactionMenuState internal constructor() {
    var openSide by mutableStateOf<ReactionSide?>(null)
        private set
    /** A finger is down on an open menu or its trigger: never auto-close while true. */
    var touching by mutableStateOf(false)
        internal set
    var lastInteractionMs by mutableLongStateOf(0L)
        private set
    /** The reaction under the finger during press-and-slide. */
    var hovered by mutableStateOf<SongReaction?>(null)
        internal set
    /** Briefly shown on the trigger after a pick. */
    var echo by mutableStateOf<SongReaction?>(null)
        internal set

    internal val boundsInRoot = mutableStateMapOf<ReactionSide, Rect>()
    /** Coordinates of the lyrics root that listens for outside taps (plain field, not state). */
    internal var rootCoordinates: LayoutCoordinates? = null

    val isOpen: Boolean get() = openSide != null

    fun open(side: ReactionSide) {
        openSide = side
        touch()
    }

    fun close() {
        openSide = null
        hovered = null
        touching = false
    }

    fun touch() {
        lastInteractionMs = System.currentTimeMillis()
    }

    /** True if [rootPosition] is on the open menu (or its trigger). */
    internal fun containsInRoot(rootPosition: Offset): Boolean {
        val side = openSide ?: return false
        return boundsInRoot[side]?.contains(rootPosition) == true
    }
}

@Composable
fun rememberReactionMenuState(): ReactionMenuState {
    val state = remember { ReactionMenuState() }
    // 3 s without any interaction → sink back into the trigger. Never while a finger is down.
    LaunchedEffect(state.openSide, state.lastInteractionMs, state.touching) {
        if (state.openSide != null && !state.touching) {
            delay(REACTION_MENU_TIMEOUT_MS)
            state.close()
        }
    }
    // The picked emoji rests on the trigger for a moment, then the trigger returns to neutral.
    LaunchedEffect(state.echo) {
        if (state.echo != null) {
            delay(1_200)
            state.echo = null
        }
    }
    return state
}

const val REACTION_MENU_TIMEOUT_MS = 3_000L

/**
 * Put on the lyrics root: while a reaction menu is open, a press anywhere outside it closes the
 * menu immediately. That press is consumed, so it doesn't also seek to a lyric line or skip.
 */
fun Modifier.dismissReactionMenuOnOutsideTap(state: ReactionMenuState): Modifier = this.then(
    Modifier
        .onGloballyPositioned { state.rootCoordinates = it }
        .pointerInput(state) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (!state.isOpen) return@awaitEachGesture
                val coords = state.rootCoordinates
                val rootPos = if (coords != null && coords.isAttached) coords.localToRoot(down.position) else down.position
                if (!state.containsInRoot(rootPos)) {
                    state.close()
                    down.consume()
                    // Swallow the rest of this press as well.
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            }
        }
)

/**
 * One corner: the trigger at the bottom and, while open, its reactions stacked above it.
 * Place it with `Modifier.align(BottomStart / BottomEnd)` inside an overlay Box.
 *
 * With [expandDownward] the trigger sits at the top instead and the reactions drop down out of
 * it (face-to-face landscape puts the positive trigger at the top centre); after a pick they
 * rise back up into it. Place that one with `Modifier.align(TopCenter)`.
 */
@Composable
fun ReactionCorner(
    side: ReactionSide,
    state: ReactionMenuState,
    triggerContainer: Color,
    triggerContent: Color,
    ringColor: Color,
    onSelect: (SongReaction) -> Unit,
    modifier: Modifier = Modifier,
    /** Idle-dimming: lower when the screen hasn't been touched for a while. */
    idleAlpha: () -> Float = { 1f },
    /** Trigger on top, reactions open below it (they close back upwards). */
    expandDownward: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val reactions = side.reactions
    val isOpen = state.openSide == side
    val onSelectState = rememberUpdatedState(onSelect)

    // Pop-out progress per reaction: staggered springs out, a quicker reverse back in.
    val progress = remember(side) { reactions.map { Animatable(0f) } }
    LaunchedEffect(isOpen) {
        progress.forEachIndexed { index, anim ->
            launch {
                if (isOpen) {
                    delay(index * STAGGER_MS)
                    anim.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 700f))
                } else {
                    delay((reactions.lastIndex - index) * (STAGGER_MS / 2))
                    anim.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }
    val showItems = isOpen || progress.any { it.value > 0.01f }

    // Item bounds inside this column, for press-and-slide hit testing. Index = distance from trigger.
    val itemBounds = remember(side) { arrayOfNulls<Rect>(reactions.size) }
    var triggerBounds by remember { mutableStateOf<Rect?>(null) }

    fun select(reaction: SongReaction) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        state.echo = reaction
        state.close()
        onSelectState.value(reaction)
    }

    Column(
        modifier = modifier
            .onGloballyPositioned { state.boundsInRoot[side] = it.boundsInRoot() }
            .pointerInput(side, state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startedOnTrigger = triggerBounds?.contains(down.position) == true
                    val wasOpen = state.openSide == side
                    if (!wasOpen && !startedOnTrigger) return@awaitEachGesture
                    down.consume()
                    state.touching = true
                    if (!wasOpen) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        state.open(side)
                    } else {
                        state.touch()
                    }

                    var dragged = false
                    var travelled = Offset.Zero
                    var lastPosition = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        travelled += change.positionChange()
                        lastPosition = change.position
                        if (!dragged && travelled.getDistance() > touchSlop) dragged = true
                        if (dragged) {
                            val over = if (state.openSide == side) itemIndexAt(itemBounds, change.position)?.let { reactions[it] } else null
                            if (over != state.hovered) {
                                if (over != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                state.hovered = over
                            }
                            state.touch()
                        }
                        change.consume()
                        if (!change.pressed) break
                    }

                    val releasedOn = if (state.openSide == side) itemIndexAt(itemBounds, lastPosition)?.let { reactions[it] } else null
                    state.touching = false
                    state.hovered = null
                    when {
                        releasedOn != null -> select(releasedOn)
                        // Plain tap on the trigger of an already-open menu: close it.
                        !dragged && startedOnTrigger && wasOpen -> state.close()
                        else -> state.touch() // keep open; timer restarts
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ITEM_GAP)
    ) {
        val items: @Composable () -> Unit = {
            if (showItems) {
                // Nearest reaction next to the trigger: farthest first when opening upwards,
                // nearest first when opening downwards.
                val order = if (expandDownward) reactions.indices else reactions.indices.reversed()
                for (index in order) {
                    val reaction = reactions[index]
                    val p = progress[index]
                    val hovered = state.hovered == reaction
                    val hoverScale by animateFloatAsState(
                        targetValue = if (hovered) 1.25f else 1f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
                        label = "reactionHover"
                    )
                    val travel = (ITEM_SIZE + ITEM_GAP) * (index + 1)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(ITEM_SIZE)
                            .onGloballyPositioned { itemBounds[index] = it.boundsInParent() }
                            .graphicsLayer {
                                val t = p.value
                                // Comes out of the trigger: starts at its centre, small and clear.
                                // Opening upwards it rises; opening downwards it drops.
                                val direction = if (expandDownward) -1f else 1f
                                translationY = direction * (1f - t) * travel.toPx()
                                val s = lerp(0.4f, 1f, t.coerceIn(0f, 1.2f)) * hoverScale
                                scaleX = s
                                scaleY = s
                                alpha = t.coerceIn(0f, 1f)
                                transformOrigin = TransformOrigin.Center
                            }
                            .clip(CircleShape)
                            .background(if (hovered) ringColor.copy(alpha = 0.28f) else triggerContainer.copy(alpha = 0.92f))
                            .semantics {
                                role = Role.Button
                                contentDescription = reaction.label
                                onClick(label = reaction.label) { select(reaction); true }
                            }
                    ) {
                        Text(text = reaction.emoji, fontSize = 22.sp)
                    }
                }
            }
        }

        // ── Trigger ──
        val trigger: @Composable () -> Unit = {
            val triggerAlpha = if (isOpen) 1f else idleAlpha()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(TRIGGER_SIZE)
                    .onGloballyPositioned { triggerBounds = it.boundsInParent() }
                    .graphicsLayer { alpha = triggerAlpha }
                    .clip(CircleShape)
                    .background(if (isOpen) ringColor.copy(alpha = 0.22f) else triggerContainer)
                    .semantics {
                        role = Role.Button
                        contentDescription = if (side == ReactionSide.POSITIVE) "Positive reactions" else "Negative reactions"
                        onClick {
                            if (state.openSide == side) state.close() else state.open(side)
                            true
                        }
                    }
            ) {
                val echo = state.echo?.takeIf { it in reactions }
                AnimatedContent(
                    targetState = echo,
                    transitionSpec = {
                        (scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = 0.45f, stiffness = 600f)) + fadeIn(tween(120)))
                            .togetherWith(scaleOut(targetScale = 0.6f) + fadeOut(tween(120)))
                    },
                    label = "reactionTriggerEcho"
                ) { shown ->
                    if (shown != null) {
                        Text(text = shown.emoji, fontSize = 20.sp)
                    } else {
                        Icon(
                            imageVector = if (side == ReactionSide.POSITIVE) Icons.Rounded.SentimentSatisfied else Icons.Rounded.SentimentDissatisfied,
                            contentDescription = null,
                            tint = if (isOpen) ringColor else triggerContent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        if (expandDownward) {
            trigger()
            items()
        } else {
            items()
            trigger()
        }
    }
}

private fun itemIndexAt(bounds: Array<Rect?>, position: Offset): Int? {
    bounds.forEachIndexed { index, rect ->
        // A little forgiveness around each bubble makes sliding easy.
        if (rect != null && rect.inflate(6f).contains(position)) return index
    }
    return null
}

private val TRIGGER_SIZE = 40.dp
private val ITEM_SIZE = 44.dp
private val ITEM_GAP = 8.dp
private const val STAGGER_MS = 35L
