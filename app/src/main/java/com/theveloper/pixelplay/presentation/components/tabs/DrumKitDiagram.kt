package com.theveloper.pixelplay.presentation.components.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.runtime.key
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.drumkit.KitElement
import com.theveloper.pixelplay.data.drumkit.KitLayoutRules
import com.theveloper.pixelplay.presentation.components.ConnectivityStrokeWidth
import com.theveloper.pixelplay.ui.theme.MotionTokens
import com.theveloper.pixelplay.ui.theme.rememberSystemReducedMotion
import kotlinx.coroutines.launch

/** Where a kit part sits in the drawing (drummer's-seat top view), as fractions of the area. */
private class PartSpot(val x: Float, val y: Float, val r: Float, val kind: Kind) {
    enum class Kind { DRUM, CYMBAL, PEDAL }
}

/** x, y = centre (fractions of width / height); r = radius as a fraction of the width. */
private val Spots: Map<KitElement, PartSpot> = mapOf(
    KitElement.HIHAT to PartSpot(0.11f, 0.44f, 0.095f, PartSpot.Kind.CYMBAL),
    KitElement.HIHAT_PEDAL to PartSpot(0.15f, 0.88f, 0.05f, PartSpot.Kind.PEDAL),
    KitElement.SNARE to PartSpot(0.28f, 0.62f, 0.10f, PartSpot.Kind.DRUM),
    KitElement.KICK to PartSpot(0.54f, 0.64f, 0.14f, PartSpot.Kind.DRUM),
    KitElement.TOM_1 to PartSpot(0.38f, 0.30f, 0.08f, PartSpot.Kind.DRUM),
    KitElement.TOM_2 to PartSpot(0.58f, 0.28f, 0.085f, PartSpot.Kind.DRUM),
    KitElement.TOM_3 to PartSpot(0.72f, 0.45f, 0.085f, PartSpot.Kind.DRUM),
    KitElement.FLOOR_TOM to PartSpot(0.82f, 0.73f, 0.11f, PartSpot.Kind.DRUM),
    KitElement.CRASH to PartSpot(0.18f, 0.16f, 0.12f, PartSpot.Kind.CYMBAL),
    KitElement.SPLASH to PartSpot(0.37f, 0.07f, 0.065f, PartSpot.Kind.CYMBAL),
    KitElement.CRASH_2 to PartSpot(0.56f, 0.08f, 0.09f, PartSpot.Kind.CYMBAL),
    KitElement.RIDE to PartSpot(0.84f, 0.21f, 0.14f, PartSpot.Kind.CYMBAL),
    KitElement.CHINA to PartSpot(0.93f, 0.48f, 0.065f, PartSpot.Kind.CYMBAL),
    KitElement.COWBELL to PartSpot(0.70f, 0.09f, 0.045f, PartSpot.Kind.DRUM),
    KitElement.TAMBOURINE to PartSpot(0.06f, 0.70f, 0.05f, PartSpot.Kind.CYMBAL),
)

/** Height of the drawing relative to its width. */
private const val KIT_ASPECT = 0.8f
private val MinTouch = 48.dp
private val LabelWidth = 84.dp
private val MaxKitHeight = 360.dp

/**
 * An interactive drawing of the connected kit.
 * - Every hit lights the part it plays, as it happens (from [DrumSession.lastNoteSerial]).
 * - Tap a part to arm it (its pad numbers show); the next hit on the kit sets that pad to it.
 *   Tap it again to disarm.
 * Flashes are Animatables read only in the draw phase, so hits don't recompose the drawing.
 */
@Composable
internal fun DrumKitDiagram(d: DrumSession, onBg: Color, accent: Color, modifier: Modifier = Modifier) {
    val reduced = rememberSystemReducedMotion()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val seen = d.judge.seenNotes.keys
    val notes = remember(d.map, seen.size) { (seen.toList() + d.map.knownNotes()).distinct() }
    val parts = remember(d.map, notes) { KitLayoutRules.visibleElements(d.map, notes) }
    val flashes = remember { KitElement.entries.associateWith { Animatable(0f) } }
    var armed by remember { mutableStateOf<KitElement?>(null) }
    var armedAtSerial by remember { mutableIntStateOf(0) }
    var assigned by remember { mutableStateOf<Pair<Int, KitElement>?>(null) }

    LaunchedEffect(d.lastNoteSerial) {
        val note = d.lastNote ?: return@LaunchedEffect
        val target = armed
        if (target != null && d.lastNoteSerial > armedAtSerial && d.calibrating == null) {
            d.assign(note, target.primary)
            armed = null
            assigned = note to target
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        val part = KitElement.of(d.map.pieceFor(note)) ?: return@LaunchedEffect
        val flash = flashes.getValue(part)
        // Own scope: a hit on another part mustn't cut this part's fade short.
        scope.launch {
            flash.animateTo(1f, tween(MotionTokens.DurationShort1))
            flash.animateTo(0f, tween(MotionTokens.DurationMedium2, easing = MotionTokens.Standard))
        }
    }
    LaunchedEffect(assigned) {
        if (assigned != null) {
            kotlinx.coroutines.delay(2_500)
            assigned = null
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Square-ish, never taller than MaxKitHeight (tablets, landscape).
        val w = min(maxWidth, MaxKitHeight / KIT_ASPECT)
        val h = w * KIT_ASPECT
        Box(Modifier.width(w).height(h)) {
            // Drums first, cymbals on top (cymbals sit above the drums on a real kit).
            val ordered = parts.filter { it in Spots }.sortedBy { if (Spots[it]?.kind == PartSpot.Kind.CYMBAL) 1 else 0 }
            for (part in ordered) key(part) {
                val spot = Spots.getValue(part)
                val diameter: Dp = w * (spot.r * 2f)
                val box = max(diameter, MinTouch)
                val isArmed = armed == part
                val armedProgress by animateFloatAsState(
                    targetValue = if (isArmed) 1f else 0f,
                    animationSpec = tween(MotionTokens.DurationShort4, easing = MotionTokens.Emphasized),
                    label = "kitArmed"
                )
                val flash = flashes.getValue(part)
                val partNotes = KitLayoutRules.notesFor(part, d.map, notes)
                val description = buildString {
                    append(part.label)
                    if (partNotes.isNotEmpty()) append(", pads ").append(partNotes.joinToString(", "))
                }
                Box(
                    Modifier
                        .offset(x = w * spot.x - box / 2, y = h * spot.y - box / 2)
                        .size(box)
                        .graphicsLayer {
                            val s = if (reduced) 1f else 1f + 0.06f * flash.value
                            scaleX = s
                            scaleY = s
                        }
                        .clip(CircleShape)
                        .clickable(role = Role.Button) {
                            if (armed == part) {
                                armed = null
                            } else {
                                armed = part
                                armedAtSerial = d.lastNoteSerial
                            }
                        }
                        .semantics {
                            contentDescription = description
                            selected = isArmed
                        }
                        .drawBehind {
                            val radius = diameter.toPx() / 2f
                            val c = center
                            val strokePx = 2.dp.toPx()
                            val f = flash.value
                            when (spot.kind) {
                                PartSpot.Kind.DRUM -> {
                                    drawCircle(lerp(onBg.copy(alpha = 0.10f), accent, f), radius, c)
                                    drawCircle(onBg.copy(alpha = 0.35f), radius - strokePx / 2f, c, style = Stroke(strokePx))
                                    drawCircle(onBg.copy(alpha = 0.12f), radius * 0.72f, c, style = Stroke(strokePx * 0.6f))
                                }
                                PartSpot.Kind.CYMBAL -> {
                                    drawCircle(lerp(accent.copy(alpha = 0.16f), accent, f), radius, c)
                                    drawCircle(accent.copy(alpha = 0.55f), radius - strokePx / 2f, c, style = Stroke(strokePx * 0.8f))
                                    drawCircle(accent.copy(alpha = 0.35f), radius * 0.22f, c)
                                }
                                PartSpot.Kind.PEDAL -> {
                                    val pw = radius * 1.1f
                                    val ph = radius * 2f
                                    drawRoundRect(
                                        lerp(onBg.copy(alpha = 0.14f), accent, f),
                                        topLeft = Offset(c.x - pw / 2f, c.y - ph / 2f),
                                        size = Size(pw, ph),
                                        cornerRadius = CornerRadius(pw / 3f)
                                    )
                                }
                            }
                            if (armedProgress > 0f) {
                                val ring = ConnectivityStrokeWidth.toPx()
                                drawCircle(
                                    accent.copy(alpha = armedProgress),
                                    radius + 3.dp.toPx(),
                                    c,
                                    style = Stroke(ring)
                                )
                            }
                        }
                )
                // Label under the part; armed, it shows the part's pad numbers.
                Text(
                    text = if (isArmed && partNotes.isNotEmpty()) partNotes.joinToString(" · ") else part.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isArmed) FontWeight.Bold else FontWeight.Medium,
                    color = if (isArmed) accent else onBg.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .width(LabelWidth)
                        .offset(x = w * spot.x - LabelWidth / 2, y = h * spot.y + diameter / 2 + 2.dp)
                )
            }
        }
    }

    // State line under the drawing: what the next hit will do, or what was just set.
    val armedPart = armed
    val justSet = assigned
    val line = when {
        armedPart != null -> stringResource(R.string.drum_kit_assign_armed, armedPart.label)
        justSet != null -> stringResource(R.string.drum_kit_assigned, justSet.first, justSet.second.label)
        else -> null
    }
    Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
        if (line != null) {
            Text(
                line,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}
