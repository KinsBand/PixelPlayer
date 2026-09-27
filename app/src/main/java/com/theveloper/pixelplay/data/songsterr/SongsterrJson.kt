package com.theveloper.pixelplay.data.songsterr

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.theveloper.pixelplay.data.songsterr.model.RevisionAutomations
import com.theveloper.pixelplay.data.songsterr.model.RevisionBeat
import com.theveloper.pixelplay.data.songsterr.model.RevisionBend
import com.theveloper.pixelplay.data.songsterr.model.RevisionBendPoint
import com.theveloper.pixelplay.data.songsterr.model.RevisionMarker
import com.theveloper.pixelplay.data.songsterr.model.RevisionMeasure
import com.theveloper.pixelplay.data.songsterr.model.RevisionNote
import com.theveloper.pixelplay.data.songsterr.model.RevisionTempoPoint
import com.theveloper.pixelplay.data.songsterr.model.RevisionTrack
import com.theveloper.pixelplay.data.songsterr.model.RevisionVoice

/**
 * Tolerant reader for Songsterr JSON. Every field is read by hand and accepts any JSON type
 * that can sensibly mean the same thing (true / 1 / "true" are all "true"; a number or an array
 * are both an alternate ending). One odd field never loses the whole tab.
 */
object SongsterrJson {

    // ─── Song meta (/api/meta/{songId}) ─────────────────────────────────────

    data class SongMeta(
        val songId: Long,
        val revisionId: Long,
        val image: String?,
        val artist: String,
        val title: String,
        val tracks: List<MetaTrack>,
        val popularTrackGuitar: Int?,
        val popularTrackBass: Int?,
        val popularTrackDrum: Int?,
        val defaultTrack: Int?,
        /** Who made this revision of the tab (when Songsterr's meta says so). */
        val author: String? = null,
        /** When this revision was made (ISO text as Songsterr sends it). */
        val createdAt: String? = null,
    )

    /** One saved version of a Songsterr tab (`/api/meta/{songId}/revisions`). */
    data class Revision(
        val revisionId: Long,
        val author: String?,
        val createdAt: String?,
        val description: String?,
        val image: String?,
    )

    data class MetaTrack(
        /** Position in the meta track list; this is the part number in the CDN URL. */
        val index: Int,
        val partId: Int,
        val instrumentId: Int,
        val instrument: String,
        val name: String,
        val hash: String,
        val tuning: List<Int>,
        val views: Long,
        val isDrums: Boolean,
        val isGuitar: Boolean,
        val isBass: Boolean,
        val isVocal: Boolean,
        val isEmpty: Boolean,
    ) {
        val family: TabParser.InstrumentFamily
            get() = when {
                isDrums -> TabParser.InstrumentFamily.DRUMS
                isBass -> TabParser.InstrumentFamily.BASS
                isGuitar -> TabParser.InstrumentFamily.GUITAR
                else -> TabParser.InstrumentFamily.OTHER
            }

        val displayName: String
            get() = name.ifBlank { instrument.ifBlank { "Track ${index + 1}" } }
    }

    fun parseMeta(json: String): SongMeta? = runCatching {
        metaFrom(JsonParser.parseString(json).asJsonObject)
    }.getOrNull()

    /** Reads either the API meta object or the page-state `meta.current` object. */
    fun metaFrom(o: JsonObject): SongMeta? {
        val songId = o.long("songId") ?: return null
        val revisionId = o.long("revisionId") ?: return null
        val tracks = o.arr("tracks")?.mapIndexedNotNull { i, e ->
            val t = e.objOrNull() ?: return@mapIndexedNotNull null
            val hash = t.str("hash").orEmpty()
            val prefix = hash.substringBefore('_', "").lowercase()
            val instrumentId = t.int("instrumentId") ?: -1
            val instrument = t.str("instrument").orEmpty()
            val isDrums = t.bool("isDrums") || instrumentId == 1024 || prefix == "drums"
            val isVocal = t.bool("isVocalTrack") || prefix == "vocals"
            val byId = TabParser.familyOf(instrumentId)
            val isBass = !isDrums && (t.bool("isBassGuitar") || prefix == "bass" ||
                (prefix.isEmpty() && byId == TabParser.InstrumentFamily.BASS))
            val isGuitar = !isDrums && !isBass && !isVocal && (t.bool("isGuitar") || prefix == "guitar" ||
                (prefix.isEmpty() && byId == TabParser.InstrumentFamily.GUITAR))
            MetaTrack(
                index = i,
                partId = t.int("partId") ?: i,
                instrumentId = instrumentId,
                instrument = instrument,
                name = t.str("name").orEmpty(),
                hash = hash,
                tuning = t.intList("tuning"),
                views = t.long("views") ?: 0L,
                isDrums = isDrums,
                isGuitar = isGuitar,
                isBass = isBass,
                isVocal = isVocal,
                isEmpty = t.bool("isEmpty"),
            )
        }.orEmpty()
        return SongMeta(
            songId = songId,
            revisionId = revisionId,
            image = o.str("image")?.takeIf { it.isNotBlank() },
            artist = o.str("artist").orEmpty(),
            title = o.str("title").orEmpty(),
            tracks = tracks,
            popularTrackGuitar = o.int("popularTrackGuitar"),
            popularTrackBass = o.int("popularTrackBass"),
            popularTrackDrum = o.int("popularTrackDrum"),
            defaultTrack = o.int("defaultTrack"),
            author = authorOf(o),
            createdAt = o.str("createdAt") ?: o.str("date") ?: o.str("updatedAt"),
        )
    }

    /** The tab author's name, from whichever field this response uses. */
    internal fun authorOf(o: JsonObject): String? {
        for (key in listOf("author", "person", "user", "createdBy", "owner")) {
            val e = o.get(key) ?: continue
            val name = when {
                e.isJsonPrimitive -> e.asString
                e.isJsonObject -> e.asJsonObject.let { u -> u.str("name") ?: u.str("username") ?: u.str("userName") ?: u.str("displayName") }
                else -> null
            }
            if (!name.isNullOrBlank() && name.toLongOrNull() == null) return name.trim()
        }
        for (key in listOf("authorName", "personName", "userName", "username")) {
            o.str(key)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        }
        return null
    }

    /** Every revision of a song, newest first. Accepts a bare array or `{ "revisions": [...] }`. */
    fun parseRevisions(json: String): List<Revision> = runCatching {
        val root = JsonParser.parseString(json)
        val arr = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> root.asJsonObject.arr("revisions") ?: root.asJsonObject.arr("items")
            else -> null
        } ?: return@runCatching emptyList()
        arr.mapNotNull { e ->
            val o = e.objOrNull() ?: return@mapNotNull null
            val id = o.long("revisionId") ?: o.long("id") ?: return@mapNotNull null
            Revision(
                revisionId = id,
                author = authorOf(o),
                createdAt = o.str("createdAt") ?: o.str("date") ?: o.str("updatedAt"),
                description = (o.str("description") ?: o.str("comment") ?: o.str("title"))?.trim()?.takeIf { it.isNotEmpty() },
                image = o.str("image")?.takeIf { it.isNotBlank() },
            )
        }.distinctBy { it.revisionId }.sortedByDescending { it.revisionId }
    }.getOrDefault(emptyList())

    // ─── Part (CDN note data) ────────────────────────────────────────────────

    fun parseTrack(json: String): RevisionTrack? = runCatching {
        trackFrom(JsonParser.parseString(json).asJsonObject)
    }.getOrNull()

    fun trackFrom(o: JsonObject): RevisionTrack = RevisionTrack(
        name = o.str("name"),
        instrument = o.str("instrument"),
        instrumentId = o.int("instrumentId"),
        partId = o.int("partId"),
        songId = o.long("songId"),
        revisionId = o.long("revisionId"),
        version = o.int("version"),
        tuning = o.intList("tuning"),
        strings = o.int("strings"),
        frets = o.int("frets"),
        capo = o.int("capo"),
        anacrusis = o.bool("anacrusis"),
        measures = o.arr("measures")?.mapNotNull { it.objOrNull()?.let(::measureFrom) }.orEmpty(),
        automations = o.obj("automations")?.let { a ->
            RevisionAutomations(
                tempo = a.arr("tempo")?.mapNotNull { e ->
                    val t = e.objOrNull() ?: return@mapNotNull null
                    RevisionTempoPoint(
                        measure = t.int("measure") ?: 0,
                        position = t.int("position") ?: 0,
                        bpm = t.double("bpm") ?: 120.0,
                        type = t.int("type") ?: 4,
                    )
                }.orEmpty(),
            )
        },
    )

    private fun measureFrom(o: JsonObject): RevisionMeasure = RevisionMeasure(
        voices = o.arr("voices")?.mapNotNull { e ->
            val v = e.objOrNull() ?: return@mapNotNull null
            RevisionVoice(
                beats = v.arr("beats")?.mapNotNull { it.objOrNull()?.let(::beatFrom) }.orEmpty(),
                rest = v.bool("rest"),
            )
        }.orEmpty(),
        signature = o.intList("signature").takeIf { it.size >= 2 && it[0] > 0 && it[1] > 0 },
        marker = o.get("marker")?.let { m ->
            val text = textOf(m) ?: return@let null
            RevisionMarker(text = text, width = m.objOrNull()?.int("width") ?: 0)
        },
        repeatStart = o.bool("repeatStart"),
        repeat = o.int("repeat")?.takeIf { it > 0 } ?: o.int("repeatCount")?.takeIf { it > 0 },
        alternateEnding = o.intList("alternateEnding"),
        doubleBarline = o.bool("doubleBarline"),
        rest = o.bool("rest"),
    )

    private fun beatFrom(o: JsonObject): RevisionBeat {
        val duration = o.intList("duration").takeIf { it.size >= 2 && it[1] != 0 }
        return RevisionBeat(
            notes = o.arr("notes")?.mapNotNull { it.objOrNull()?.let(::noteFrom) }.orEmpty(),
            type = o.int("type"),
            duration = duration,
            dots = o.int("dots") ?: if (o.bool("dotted")) 1 else 0,
            tuplet = o.int("tuplet")?.takeIf { it > 1 },
            tupletStart = o.bool("tupletStart"),
            tupletStop = o.bool("tupletStop"),
            beamStart = o.bool("beamStart"),
            beamStop = o.bool("beamStop"),
            velocity = o.str("velocity"),
            rest = o.bool("rest"),
            palmMute = o.bool("palmMute"),
            letRing = o.bool("letRing"),
            vibrato = o.bool("vibrato") || o.bool("vibratoWithTremoloBar"),
            wideVibrato = o.bool("wideVibrato"),
            pickStroke = o.str("pickStroke"),
            graceNote = o.get("graceNote")?.let { g ->
                when {
                    g.isJsonPrimitive && g.asJsonPrimitive.isString -> g.asString
                    g.isJsonPrimitive && g.asJsonPrimitive.isBoolean -> if (g.asBoolean) "beforeBeat" else null
                    else -> null
                }
            },
            tapping = o.bool("tapping"),
            chordText = o.get("chord")?.let(::textOf),
            text = o.get("text")?.let(::textOf),
            upStroke = o.bool("upStroke") || o.obj("brushStroke")?.str("direction") == "up",
            downStroke = o.obj("brushStroke")?.str("direction") == "down",
            tremoloPicking = o.has("tremoloPicking") && o.get("tremoloPicking").let { !it.isJsonNull && !(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && !it.asBoolean) },
            tremoloBar = o.has("tremoloBar") && !o.get("tremoloBar").isJsonNull,
        )
    }

    private fun noteFrom(o: JsonObject): RevisionNote {
        val lhv = o.str("leftHandVibrato")
        return RevisionNote(
            fret = o.int("fret"),
            string = o.double("string"),
            tie = o.bool("tie"),
            rest = o.bool("rest"),
            dead = o.bool("dead"),
            ghost = o.bool("ghost"),
            hp = o.bool("hp"),
            staccato = o.bool("staccato"),
            accentuated = accentLevel(o.get("accentuated")),
            vibrato = o.bool("vibrato") || lhv == "slight",
            wideVibrato = o.bool("wideVibrato") || lhv == "wide",
            slide = o.str("slide"),
            harmonic = o.str("harmonic"),
            harmonicFret = o.double("harmonicFret"),
            bend = o.obj("bend")?.let { b ->
                RevisionBend(
                    tone = b.int("tone") ?: 0,
                    points = b.arr("points")?.mapNotNull { e ->
                        val p = e.objOrNull() ?: return@mapNotNull null
                        RevisionBendPoint(position = p.int("position") ?: 0, tone = p.int("tone") ?: 0)
                    }.orEmpty(),
                )
            },
            trill = o.has("trill") && !o.get("trill").isJsonNull,
        )
    }

    private fun accentLevel(e: JsonElement?): Int {
        if (e == null || e.isJsonNull || !e.isJsonPrimitive) return 0
        val p = e.asJsonPrimitive
        return when {
            p.isBoolean -> if (p.asBoolean) 1 else 0
            p.isNumber -> p.asDouble.toInt().coerceIn(0, 2)
            p.isString -> when (p.asString.lowercase()) {
                "true", "1", "normal" -> 1
                "2", "heavy" -> 2
                else -> 0
            }
            else -> 0
        }
    }

    // ─── Video sync points (/api/video-points/{songId}/{revisionId}/list) ───

    /** Bar start times (seconds) in one YouTube recording, in playback order. */
    data class VideoPoints(
        val videoId: String,
        /** null / "alternative" = the full song; "backing" = without [tracks]; "solo" = only [tracks]. */
        val feature: String?,
        val points: List<Double>,
        /** Track indexes this video removes (backing) or isolates (solo). */
        val tracks: List<Int> = emptyList(),
    )

    fun parseVideoPoints(json: String): List<VideoPoints> = runCatching {
        JsonParser.parseString(json).asJsonArray.mapNotNull { e ->
            val o = e.objOrNull() ?: return@mapNotNull null
            if (o.str("status")?.let { it != "done" } == true) return@mapNotNull null
            val points = o.arr("points")?.mapNotNull { p ->
                if (p.isJsonPrimitive && p.asJsonPrimitive.isNumber) p.asDouble else null
            }.orEmpty()
            val id = o.str("videoId") ?: return@mapNotNull null
            if (points.size < 2) null else VideoPoints(id, o.str("feature"), points, o.intList("tracks"))
        }
    }.getOrDefault(emptyList())

    // ─── Lenient accessors ───────────────────────────────────────────────────

    private fun JsonElement.objOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null

    private fun textOf(e: JsonElement): String? = when {
        e.isJsonNull -> null
        e.isJsonPrimitive -> e.asString
        e.isJsonObject -> e.asJsonObject.str("text")
        else -> null
    }?.trim()?.takeIf { it.isNotEmpty() }

    internal fun JsonObject.obj(key: String): JsonObject? = get(key)?.objOrNull()

    internal fun JsonObject.arr(key: String): JsonArray? = get(key)?.let { if (it.isJsonArray) it.asJsonArray else null }

    internal fun JsonObject.str(key: String): String? {
        val e = get(key) ?: return null
        return if (e.isJsonPrimitive) e.asString else null
    }

    internal fun JsonObject.double(key: String): Double? {
        val e = get(key) ?: return null
        if (!e.isJsonPrimitive) return null
        val p = e.asJsonPrimitive
        return when {
            p.isNumber -> p.asDouble
            p.isString -> p.asString.toDoubleOrNull()
            else -> null
        }
    }

    internal fun JsonObject.int(key: String): Int? = double(key)?.let { kotlin.math.round(it).toInt() }

    internal fun JsonObject.long(key: String): Long? = double(key)?.let { kotlin.math.round(it).toLong() }

    internal fun JsonObject.bool(key: String): Boolean {
        val e = get(key) ?: return false
        if (!e.isJsonPrimitive) return !e.isJsonNull
        val p = e.asJsonPrimitive
        return when {
            p.isBoolean -> p.asBoolean
            p.isNumber -> p.asDouble != 0.0
            p.isString -> p.asString.equals("true", ignoreCase = true) || p.asString == "1"
            else -> false
        }
    }

    /** A number becomes a one-item list; an array keeps its numeric items. */
    internal fun JsonObject.intList(key: String): List<Int> {
        val e = get(key) ?: return emptyList()
        return when {
            e.isJsonArray -> e.asJsonArray.mapNotNull { item ->
                if (item.isJsonPrimitive && item.asJsonPrimitive.isNumber) kotlin.math.round(item.asDouble).toInt() else null
            }
            e.isJsonPrimitive && e.asJsonPrimitive.isNumber -> listOf(kotlin.math.round(e.asDouble).toInt())
            else -> emptyList()
        }
    }
}
