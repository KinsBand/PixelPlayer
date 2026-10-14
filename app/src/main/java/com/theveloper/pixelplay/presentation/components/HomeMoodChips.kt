package com.theveloper.pixelplay.presentation.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.library.MusicVibeFilters
import com.theveloper.pixelplay.presentation.library.VibeFilter
import java.util.Locale
import kotlin.math.ln

/** Moods offered on Home, in chip order (ids from [MusicVibeFilters.presets]). */
private val HomeMoodIds = listOf(
    "chill", "hype", "focus", "party", "drive", "workout", "unwind", "euphoric", "romantic",
    "melancholy", "dreamy", "nostalgia", "dark", "gritty", "sleep", "reset", "dining", "gaming",
)

/** A mood needs at least this many matching songs in the library to get a chip. */
private const val MinSongsForMoodChip = 8

/** A shelf falls back to its usual list when the mood leaves fewer songs than this. */
const val MinSongsAfterMoodFilter = 4

/** Ids of moods typed by the user start with this, so they never clash with presets. */
const val CustomMoodPrefix = "custom_mood_"

/**
 * Song id → relevance for one mood. Built once per (pool, mood) off the main thread and reused by
 * every shelf, so switching chips doesn't re-score the library per section.
 */
@Immutable
data class HomeMoodScores(val mood: VibeFilter, val scores: Map<String, Double>) {
    fun matches(song: Song): Boolean = score(song) >= MusicVibeFilters.MATCH_THRESHOLD
    fun score(song: Song): Double = scores[song.id] ?: MusicVibeFilters.score(song, mood)
}

/** Moods with enough matching songs in [pool] to be worth a chip. Heavy: call off the main thread. */
fun homeMoodOptions(pool: List<Song>): List<VibeFilter> =
    HomeMoodIds.mapNotNull { id -> MusicVibeFilters.presets.firstOrNull { it.id == id } }
        .filter { mood ->
            var count = 0
            for (song in pool) {
                if (MusicVibeFilters.score(song, mood) >= MusicVibeFilters.MATCH_THRESHOLD) {
                    if (++count >= MinSongsForMoodChip) return@filter true
                }
            }
            false
        }

/** Scores every song in [pool] for [mood]. Heavy: call off the main thread. */
fun buildHomeMoodScores(pool: List<Song>, mood: VibeFilter): HomeMoodScores =
    HomeMoodScores(mood, pool.associate { it.id to MusicVibeFilters.score(it, mood) })

/** Result of narrowing a shelf to a mood. [fellBack] means the mood found too little to fill it. */
@Immutable
data class MoodRanked<T>(val items: List<T>, val fellBack: Boolean)

/**
 * Keeps the songs that match the mood, best first. With fewer than [MinSongsAfterMoodFilter]
 * matches the shelf keeps all its songs, closest to the mood first (and says so).
 */
fun rankSongsByMood(songs: List<Song>, mood: HomeMoodScores?): MoodRanked<Song> {
    if (mood == null) return MoodRanked(songs, fellBack = false)
    val matching = songs.filter { mood.matches(it) }.sortedByDescending { mood.score(it) }
    return if (matching.size < MinSongsAfterMoodFilter) {
        MoodRanked(songs.sortedByDescending { mood.score(it) }, fellBack = true)
    } else {
        MoodRanked(matching, fellBack = false)
    }
}

/**
 * Quick picks for a mood: your usual picks that fit it, topped up from the whole library with the
 * songs that fit it best (nudged toward ones you play). The shelf keeps its usual length.
 * Heavy: call off the main thread.
 */
fun moodQuickPicks(
    quickPicks: List<Song>,
    pool: List<Song>,
    mood: HomeMoodScores?,
    size: Int = maxOf(quickPicks.size, 20),
): MoodRanked<Song> {
    if (mood == null) return MoodRanked(quickPicks, fellBack = false)
    val fromPicks = quickPicks.filter { mood.matches(it) }.sortedByDescending { mood.score(it) }
    val seen = fromPicks.mapTo(HashSet()) { it.id }
    val maxPlays = pool.maxOfOrNull { it.userActivityStats.playCount }?.coerceAtLeast(1) ?: 1
    val topUp = pool.asSequence()
        .filter { it.id !in seen && mood.matches(it) }
        .map { song ->
            val familiarity = ln(1.0 + song.userActivityStats.playCount) / ln(1.0 + maxPlays)
            song to (mood.score(song) + familiarity * 1.5 + if (song.isFavorite) 0.75 else 0.0)
        }
        .sortedByDescending { it.second }
        .map { it.first }
        .take((size - fromPicks.size).coerceAtLeast(0))
        .toList()
    val picks = (fromPicks + topUp).take(size)
    return if (picks.size < MinSongsAfterMoodFilter) {
        MoodRanked(quickPicks.sortedByDescending { mood.score(it) }, fellBack = true)
    } else {
        MoodRanked(picks, fellBack = false)
    }
}

/** "Not much Chill here — closest first" caption for a shelf that fell back. */
fun moodFallbackCaption(mood: HomeMoodScores?): String? =
    mood?.let { "Not much ${it.mood.label.lowercase(Locale.ROOT)} here — closest matches first" }

/**
 * Moods typed on Home ("rainy sunday", "2am drive"), newest first. Kept so they stay as chips
 * until removed.
 */
object HomeCustomMoodStore {
    private const val PREFS = "home_moods"
    private const val KEY = "custom_moods"
    private const val MAX = 6

    fun load(context: Context): List<VibeFilter> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?.map { toFilter(it) }
            .orEmpty()

    /** Adds (or moves to the front) a typed mood; returns the new list. */
    fun add(context: Context, text: String): List<VibeFilter> {
        val clean = text.trim().replace('\n', ' ')
        if (clean.isEmpty()) return load(context)
        val next = (listOf(clean) + load(context).map { it.label }.filterNot { it.equals(clean, ignoreCase = true) }).take(MAX)
        save(context, next)
        return next.map { toFilter(it) }
    }

    fun remove(context: Context, id: String): List<VibeFilter> {
        val next = load(context).filterNot { it.id == id }.map { it.label }
        save(context, next)
        return next.map { toFilter(it) }
    }

    fun idFor(text: String): String = CustomMoodPrefix + text.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "_")

    private fun toFilter(text: String): VibeFilter = MusicVibeFilters.custom(idFor(text), text)

    private fun save(context: Context, labels: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, labels.joinToString("\n")).apply()
    }
}

/**
 * One row of mood chips: "For you", the moods you've typed, then the moods your library can fill.
 * Picking one re-ranks everything on Home toward that vibe.
 *
 * Scrolled to the start, keep swiping right to type any mood instead ([PullToRevealSearchRow]).
 */
@Composable
fun HomeMoodChips(
    moods: List<VibeFilter>,
    customMoods: List<VibeFilter>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onCustomMood: (String) -> Unit,
    onRemoveCustomMood: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var typing by rememberSaveable { mutableStateOf(false) }
    PullToRevealSearchRow(
        revealed = typing,
        onRevealedChange = { typing = it },
        onSubmit = onCustomMood,
        placeholder = "Any mood — rainy sunday, 2am drive, 90s rock…",
        pullLabel = "Custom mood",
        listState = rememberLazyListState(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        item(key = "mood_for_you") {
            MoodChip(label = "For you", selected = selectedId == null, onClick = { onSelect(null) })
        }
        items(customMoods, key = { "mood_" + it.id }) { mood ->
            val selected = selectedId == mood.id
            ExpressiveFilterChip(
                label = mood.label,
                selected = selected,
                onClick = { onSelect(if (selected) null else mood.id) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Remove ${mood.label}",
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable { onRemoveCustomMood(mood.id) }
                    )
                },
                modifier = Modifier.semantics {
                    contentDescription = "Your mood ${mood.label}"
                    stateDescription = if (selected) "Selected" else "Not selected"
                }
            )
        }
        items(moods, key = { "mood_" + it.id }) { mood ->
            MoodChip(
                label = mood.label,
                selected = selectedId == mood.id,
                onClick = { onSelect(if (selectedId == mood.id) null else mood.id) }
            )
        }
    }
}

@Composable
private fun MoodChip(label: String, selected: Boolean, onClick: () -> Unit) {
    ExpressiveFilterChip(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = Modifier.semantics {
            stateDescription = if (selected) "Filter Home by $label, selected" else "Filter Home by $label"
        }
    )
}
