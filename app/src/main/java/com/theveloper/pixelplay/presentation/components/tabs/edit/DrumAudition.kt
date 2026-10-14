package com.theveloper.pixelplay.presentation.components.tabs.edit

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.theveloper.pixelplay.data.soundfont.SoundFont
import com.theveloper.pixelplay.data.songsterr.SongsterrJson
import com.theveloper.pixelplay.data.songsterr.TabParser
import com.theveloper.pixelplay.presentation.components.tabs.NotationAuditionPlayer

/**
 * Sounds kit pieces while editing (a pad tap, a long-press preview). Builds a one-beat drum
 * part and plays it through the same path as tab playback, so it sounds like the song's kit.
 * Silent until the instrument sounds are downloaded.
 */
internal class DrumAudition {
    private val player = NotationAuditionPlayer()
    private var n = 0

    fun playPieces(pieces: List<Int>, font: SoundFont?, staffPos: (Int) -> Double) {
        if (pieces.isEmpty() || font == null) return
        val raw = JsonObject().apply {
            addProperty("instrumentId", 1024)
            add("measures", JsonArray().apply {
                add(JsonObject().apply {
                    add("signature", JsonArray().apply { add(1); add(4) })
                    add("voices", JsonArray().apply {
                        add(JsonObject().apply {
                            add("beats", JsonArray().apply {
                                add(JsonObject().apply {
                                    addProperty("type", 4)
                                    add("duration", JsonArray().apply { add(1); add(4) })
                                    add("notes", JsonArray().apply {
                                        pieces.forEach { art ->
                                            add(JsonObject().apply {
                                                addProperty("fret", art)
                                                addProperty("string", staffPos(art))
                                            })
                                        }
                                    })
                                })
                            })
                        })
                    })
                })
            })
        }
        val track = runCatching {
            TabParser.toRenderModel(SongsterrJson.trackFrom(raw), TabParser.InstrumentFamily.DRUMS)
        }.getOrNull() ?: return
        player.stop()
        player.play("edit-${n++}", track, font)
    }

    fun release() = player.release()
}
