package com.theveloper.pixelplay.data.practice

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Where a song is in your practice list. */
enum class PracticeStage(val label: String) {
    WANT("Want to learn"),
    LEARNING("Learning"),
    FINISHED("Finished");

    val next: PracticeStage? get() = entries.getOrNull(ordinal + 1)
    val previous: PracticeStage? get() = entries.getOrNull(ordinal - 1)
}

data class PracticeEntry(
    val songId: String,
    val stage: PracticeStage,
    val addedAt: Long,
    /** When it last changed stage (newest first inside a stage). */
    val movedAt: Long,
    /** Snapshot so the row still shows something if the song leaves the library. */
    val title: String,
    val artist: String,
)

/**
 * The songs you're practising, grouped Want → Learning → Finished. Kept in SharedPreferences as
 * JSON; one shared instance so the Practice screen, the Playlists button and the song options
 * menu stay in step.
 */
class PracticeStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("practice_songs", Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<PracticeEntry>> = _entries.asStateFlow()

    fun stageOf(songId: String): PracticeStage? = _entries.value.firstOrNull { it.songId == songId }?.stage

    /** Adds songs (to Want by default). Songs already in the list keep their stage. */
    fun add(songs: List<Triple<String, String, String>>, stage: PracticeStage = PracticeStage.WANT) {
        val now = System.currentTimeMillis()
        update { list ->
            val have = list.mapTo(HashSet()) { it.songId }
            list + songs.filter { it.first !in have }.distinctBy { it.first }.mapIndexed { i, (id, title, artist) ->
                PracticeEntry(id, stage, now + i, now + i, title, artist)
            }
        }
    }

    fun add(songId: String, title: String, artist: String, stage: PracticeStage = PracticeStage.WANT) =
        add(listOf(Triple(songId, title, artist)), stage)

    fun move(songId: String, stage: PracticeStage) {
        val now = System.currentTimeMillis()
        update { list -> list.map { if (it.songId == songId && it.stage != stage) it.copy(stage = stage, movedAt = now) else it } }
    }

    fun remove(songId: String) = update { list -> list.filterNot { it.songId == songId } }

    private fun update(block: (List<PracticeEntry>) -> List<PracticeEntry>) {
        synchronized(this) {
            val next = block(_entries.value)
            _entries.value = next
            save(next)
        }
    }

    private fun load(): List<PracticeEntry> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PracticeEntry(
                songId = id,
                stage = runCatching { PracticeStage.valueOf(o.optString("stage")) }.getOrDefault(PracticeStage.WANT),
                addedAt = o.optLong("added"),
                movedAt = o.optLong("moved", o.optLong("added")),
                title = o.optString("title"),
                artist = o.optString("artist"),
            )
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<PracticeEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.songId)
                    .put("stage", e.stage.name)
                    .put("added", e.addedAt)
                    .put("moved", e.movedAt)
                    .put("title", e.title)
                    .put("artist", e.artist),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "entries_v1"

        @Volatile
        private var instance: PracticeStore? = null

        fun get(context: Context): PracticeStore =
            instance ?: synchronized(this) { instance ?: PracticeStore(context).also { instance = it } }
    }
}
