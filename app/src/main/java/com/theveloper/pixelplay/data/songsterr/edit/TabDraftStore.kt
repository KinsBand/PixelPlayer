package com.theveloper.pixelplay.data.songsterr.edit

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A tab being edited: a copy of one part of one Songsterr version. Only kept on the phone
 * until it's published (then [TabDraftStore.delete] removes it), so a draft is one small file.
 */
data class TabDraft(
    val songId: Long,
    val baseRevisionId: Long,
    val trackIndex: Int,
    /** The edited part, in Songsterr's own JSON. */
    val part: JsonObject,
    val savedAt: Long = System.currentTimeMillis(),
    val message: String = "",
)

object TabDraftStore {

    private fun dir(context: Context) = File(context.filesDir, "tab_drafts")

    private fun file(context: Context, songId: Long, baseRevisionId: Long, trackIndex: Int) =
        File(dir(context), "${songId}_${baseRevisionId}_$trackIndex.json")

    suspend fun save(context: Context, draft: TabDraft) = withContext(Dispatchers.IO) {
        runCatching {
            val f = file(context, draft.songId, draft.baseRevisionId, draft.trackIndex)
            f.parentFile?.mkdirs()
            val o = JsonObject().apply {
                addProperty("songId", draft.songId)
                addProperty("baseRevisionId", draft.baseRevisionId)
                addProperty("trackIndex", draft.trackIndex)
                addProperty("savedAt", System.currentTimeMillis())
                addProperty("message", draft.message)
                add("part", draft.part)
            }
            // Write then rename, so a kill mid-write never leaves half a draft.
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }

    suspend fun load(context: Context, songId: Long, baseRevisionId: Long, trackIndex: Int): TabDraft? =
        withContext(Dispatchers.IO) {
            val f = file(context, songId, baseRevisionId, trackIndex)
            if (!f.exists()) return@withContext null
            runCatching {
                val o = JsonParser.parseString(f.readText()).asJsonObject
                TabDraft(
                    songId = songId,
                    baseRevisionId = baseRevisionId,
                    trackIndex = trackIndex,
                    part = o.getAsJsonObject("part"),
                    savedAt = o.get("savedAt")?.asLong ?: f.lastModified(),
                    message = o.get("message")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                )
            }.getOrNull()
        }

    suspend fun delete(context: Context, songId: Long, baseRevisionId: Long, trackIndex: Int) = withContext(Dispatchers.IO) {
        file(context, songId, baseRevisionId, trackIndex).delete()
    }

    /** Parts of this version that have a draft waiting (for the "Edit a copy" page). */
    suspend fun draftedParts(context: Context, songId: Long, baseRevisionId: Long): Set<Int> = withContext(Dispatchers.IO) {
        dir(context).listFiles()?.mapNotNull { f ->
            val parts = f.nameWithoutExtension.split('_')
            if (f.extension == "json" && parts.size == 3 && parts[0] == "$songId" && parts[1] == "$baseRevisionId") parts[2].toIntOrNull() else null
        }?.toSet().orEmpty()
    }
}
