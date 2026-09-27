package com.theveloper.pixelplay.data.drumkit

/**
 * Drum kit pieces, how tab notes and MIDI notes map to them, and the kit profiles used to map
 * an electronic kit automatically. Pure Kotlin (no Android), so it can be unit-tested.
 */
enum class DrumGroup { KICK, SNARE, HIHAT, HIHAT_FOOT, TOM, CRASH, RIDE, PERC }

enum class DrumPiece(
    val label: String,
    val group: DrumGroup,
    /** Staff position (line spaces, 0 = top line) — where a stray hit is marked. */
    val staffPos: Float,
    /** Toms only: 0 = highest … 3 = floor. */
    val tomOrder: Int = -1,
) {
    KICK("Kick", DrumGroup.KICK, 3.5f),
    SNARE("Snare", DrumGroup.SNARE, 1.5f),
    SNARE_RIM("Snare rim", DrumGroup.SNARE, 1.5f),
    SIDE_STICK("Cross-stick", DrumGroup.SNARE, 1.5f),
    HH_CLOSED("Hi-hat closed", DrumGroup.HIHAT, -0.5f),
    HH_HALF("Hi-hat half-open", DrumGroup.HIHAT, -0.5f),
    HH_OPEN("Hi-hat open", DrumGroup.HIHAT, -0.5f),
    HH_FOOT("Hi-hat pedal", DrumGroup.HIHAT_FOOT, 4.5f),
    TOM_HIGH("Tom 1 (high)", DrumGroup.TOM, 1f, 0),
    TOM_MID("Tom 2 (mid)", DrumGroup.TOM, 2f, 1),
    TOM_LOW("Tom 3 (low)", DrumGroup.TOM, 2.5f, 2),
    TOM_FLOOR("Floor tom", DrumGroup.TOM, 3f, 3),
    CRASH("Crash", DrumGroup.CRASH, -1f),
    CRASH_2("Crash 2", DrumGroup.CRASH, -0.5f),
    SPLASH("Splash", DrumGroup.CRASH, -1f),
    CHINA("China", DrumGroup.CRASH, -1.5f),
    RIDE("Ride", DrumGroup.RIDE, 0f),
    RIDE_BELL("Ride bell", DrumGroup.RIDE, 0f),
    COWBELL("Cowbell", DrumGroup.PERC, 0.5f),
    TAMBOURINE("Tambourine", DrumGroup.PERC, 0.5f),
    OTHER("Other", DrumGroup.PERC, 0.5f),
    ;

    val isHiHatHand: Boolean get() = group == DrumGroup.HIHAT

    companion object {
        /** Songsterr drum articulation id (see TabParser) → piece. */
        fun ofArticulation(id: Int): DrumPiece = when (id) {
            35, 36 -> KICK
            38, 40 -> SNARE
            91 -> SNARE_RIM
            37 -> SIDE_STICK
            39 -> SNARE // hand clap: nearest pad on a kit
            42 -> HH_CLOSED
            92 -> HH_HALF
            46 -> HH_OPEN
            44 -> HH_FOOT
            48, 50 -> TOM_HIGH
            47 -> TOM_MID
            45 -> TOM_LOW
            41, 43 -> TOM_FLOOR
            49, 97 -> CRASH
            57, 98 -> CRASH_2
            55, 95 -> SPLASH
            52, 96 -> CHINA
            51, 59, 93 -> RIDE
            53 -> RIDE_BELL
            56 -> COWBELL
            54 -> TAMBOURINE
            else -> OTHER
        }
    }
}

/** How well a hit piece matches the tab's piece. */
enum class PieceMatch { EXACT, LOOSE, HH_OPENNESS, NONE }

fun matchPiece(expected: DrumPiece, hit: DrumPiece): PieceMatch {
    if (expected == hit) return PieceMatch.EXACT
    if (expected.group != hit.group) return PieceMatch.NONE
    return when (expected.group) {
        // Hi-hat hit with the pedal in the wrong place still counts, but not as exact.
        DrumGroup.HIHAT -> PieceMatch.HH_OPENNESS
        // Toms can be one position off (kits and songs have different tom counts).
        DrumGroup.TOM -> if (kotlin.math.abs(expected.tomOrder - hit.tomOrder) <= 1) PieceMatch.LOOSE else PieceMatch.NONE
        DrumGroup.PERC -> if (expected == DrumPiece.OTHER || hit == DrumPiece.OTHER) PieceMatch.LOOSE else PieceMatch.NONE
        else -> PieceMatch.LOOSE
    }
}

/** A built-in note map for a family of kits, picked by the USB device's name. */
class KitProfile(
    val id: String,
    val name: String,
    /** Lower-case fragments of the USB/MIDI device name that identify the kit. */
    val match: List<String>,
    val notes: Map<Int, DrumPiece>,
    /** Pieces this kit physically has (null = unknown, assume everything). */
    val pieces: Set<DrumPiece>? = null,
    /** Yamaha-style: CC4 (foot controller) is sent and splits open / half / closed. */
    val usesCc4: Boolean = true,
)

object KitProfiles {
    /** General MIDI percussion map (what most kits send by default). */
    val GM = KitProfile(
        id = "gm",
        name = "General MIDI",
        match = emptyList(),
        notes = mapOf(
            35 to DrumPiece.KICK, 36 to DrumPiece.KICK,
            37 to DrumPiece.SIDE_STICK, 38 to DrumPiece.SNARE, 39 to DrumPiece.SNARE, 40 to DrumPiece.SNARE_RIM,
            41 to DrumPiece.TOM_FLOOR, 43 to DrumPiece.TOM_FLOOR, 45 to DrumPiece.TOM_LOW,
            47 to DrumPiece.TOM_MID, 48 to DrumPiece.TOM_HIGH, 50 to DrumPiece.TOM_HIGH,
            42 to DrumPiece.HH_CLOSED, 44 to DrumPiece.HH_FOOT, 46 to DrumPiece.HH_OPEN,
            49 to DrumPiece.CRASH, 57 to DrumPiece.CRASH_2, 55 to DrumPiece.SPLASH, 52 to DrumPiece.CHINA,
            51 to DrumPiece.RIDE, 59 to DrumPiece.RIDE, 53 to DrumPiece.RIDE_BELL,
            56 to DrumPiece.COWBELL, 54 to DrumPiece.TAMBOURINE,
        ),
    )

    /**
     * Yamaha DTX400 module (DTX400K / DTX430K / DTX450K), default note numbers from Yamaha's
     * "DTX400K/DTX430K/DTX450K MIDI Reference": snare 38, open rim 40, closed rim 37, toms
     * 48 / 47 / 43, ride 51, crash 49, hi-hat open 46 / closed 42 / foot close 44, hi-hat
     * splash 83, kick 36, extra pad 57. The module also sends CC4 (foot controller).
     */
    val YAMAHA_DTX400 = KitProfile(
        id = "yamaha_dtx400",
        name = "Yamaha DTX400 series (DTX450K)",
        match = listOf("dtx4", "dtx 4", "dtx450", "dtx430", "dtx400"),
        notes = mapOf(
            36 to DrumPiece.KICK,
            38 to DrumPiece.SNARE, 40 to DrumPiece.SNARE_RIM, 37 to DrumPiece.SIDE_STICK,
            48 to DrumPiece.TOM_HIGH, 47 to DrumPiece.TOM_MID, 43 to DrumPiece.TOM_FLOOR,
            51 to DrumPiece.RIDE, 49 to DrumPiece.CRASH,
            46 to DrumPiece.HH_OPEN, 42 to DrumPiece.HH_CLOSED, 44 to DrumPiece.HH_FOOT, 83 to DrumPiece.HH_FOOT,
            57 to DrumPiece.CRASH_2,
        ),
        pieces = setOf(
            DrumPiece.KICK, DrumPiece.SNARE, DrumPiece.SNARE_RIM, DrumPiece.SIDE_STICK,
            DrumPiece.TOM_HIGH, DrumPiece.TOM_MID, DrumPiece.TOM_FLOOR,
            DrumPiece.RIDE, DrumPiece.CRASH, DrumPiece.CRASH_2,
            DrumPiece.HH_OPEN, DrumPiece.HH_HALF, DrumPiece.HH_CLOSED, DrumPiece.HH_FOOT,
        ),
    )

    /** Newer Yamaha modules (DTX402/432/452/482, DTX6, DTX8). Mostly GM; CC4 hi-hat. */
    val YAMAHA_DTX = KitProfile(
        id = "yamaha_dtx",
        name = "Yamaha DTX",
        match = listOf("dtx"),
        notes = GM.notes + mapOf(83 to DrumPiece.HH_FOOT),
    )

    /** Roland V-Drums / TD modules: head/rim pairs and bow/edge hi-hat notes, CC4 hi-hat. */
    val ROLAND_TD = KitProfile(
        id = "roland_td",
        name = "Roland V-Drums",
        match = listOf("td-", "td0", "td1", "td2", "td5", "roland", "v-drums"),
        notes = GM.notes + mapOf(
            22 to DrumPiece.HH_CLOSED, 26 to DrumPiece.HH_OPEN,
            48 to DrumPiece.TOM_HIGH, 50 to DrumPiece.TOM_HIGH, 45 to DrumPiece.TOM_MID, 47 to DrumPiece.TOM_MID,
            43 to DrumPiece.TOM_FLOOR, 58 to DrumPiece.TOM_FLOOR, 41 to DrumPiece.TOM_FLOOR, 39 to DrumPiece.TOM_FLOOR,
            55 to DrumPiece.CRASH, 52 to DrumPiece.CRASH_2, 59 to DrumPiece.RIDE, 53 to DrumPiece.RIDE_BELL,
            40 to DrumPiece.SNARE_RIM, 37 to DrumPiece.SIDE_STICK,
        ),
    )

    /** Alesis modules (Nitro, Surge, Strike, Command). GM-like; hi-hat by note. */
    val ALESIS = KitProfile(
        id = "alesis",
        name = "Alesis",
        match = listOf("alesis", "nitro", "surge", "strike", "command", "crimson", "turbo"),
        notes = GM.notes + mapOf(23 to DrumPiece.HH_HALF, 21 to DrumPiece.HH_FOOT),
        usesCc4 = false,
    )

    val ALL = listOf(YAMAHA_DTX400, YAMAHA_DTX, ROLAND_TD, ALESIS, GM)

    fun forDeviceName(name: String?): KitProfile {
        val n = name?.lowercase().orEmpty()
        return ALL.firstOrNull { p -> p.match.any { n.contains(it) } } ?: GM
    }

    fun byId(id: String?): KitProfile? = ALL.firstOrNull { it.id == id }
}

/** The note → piece map for one kit: the profile, plus what was learned and what the user set. */
class DrumMap(
    val profile: KitProfile,
    /** Learned automatically from playing. */
    val learned: Map<Int, DrumPiece> = emptyMap(),
    /** Set by hand in the mapping editor (always wins). */
    val manual: Map<Int, DrumPiece> = emptyMap(),
    /** Notes set by hand to be ignored. */
    val ignored: Set<Int> = emptySet(),
) {
    fun pieceFor(note: Int): DrumPiece? {
        if (note in ignored) return null
        return manual[note] ?: learned[note] ?: profile.notes[note]
    }

    enum class Source { MANUAL, LEARNED, PROFILE, NONE }

    fun sourceOf(note: Int): Source = when {
        note in manual || note in ignored -> Source.MANUAL
        note in learned -> Source.LEARNED
        note in profile.notes -> Source.PROFILE
        else -> Source.NONE
    }

    fun isPinned(note: Int) = note in manual || note in ignored

    fun withLearned(note: Int, piece: DrumPiece) = DrumMap(profile, learned + (note to piece), manual, ignored)
    fun withManual(note: Int, piece: DrumPiece?) = if (piece == null) {
        DrumMap(profile, learned - note, manual - note, ignored + note)
    } else {
        DrumMap(profile, learned - note, manual + (note to piece), ignored - note)
    }
    fun reset() = DrumMap(profile)

    /** Does this kit have something that can play [piece] (under loose matching)? */
    fun canPlay(piece: DrumPiece): Boolean {
        val have = HashSet<DrumPiece>()
        profile.pieces?.let { have += it } ?: return true
        have += learned.values
        have += manual.values
        if (piece in have) return true
        return have.any { matchPiece(piece, it) != PieceMatch.NONE }
    }

    /** Every note shown in the mapping editor. */
    fun knownNotes(extra: Collection<Int> = emptyList()): List<Int> =
        (profile.notes.keys + learned.keys + manual.keys + ignored + extra).toSortedSet().toList()

    /** Saved form: "profileId|note:PIECE:L,note:PIECE:M,note:-:M". */
    fun encode(): String = buildString {
        append(profile.id).append('|')
        val parts = ArrayList<String>()
        learned.forEach { (n, p) -> parts += "$n:${p.name}:L" }
        manual.forEach { (n, p) -> parts += "$n:${p.name}:M" }
        ignored.forEach { parts += "$it:-:M" }
        append(parts.joinToString(","))
    }

    companion object {
        fun decode(s: String?, fallback: KitProfile): DrumMap {
            if (s.isNullOrBlank()) return DrumMap(fallback)
            val head = s.substringBefore('|')
            val profile = KitProfiles.byId(head) ?: fallback
            val learned = HashMap<Int, DrumPiece>()
            val manual = HashMap<Int, DrumPiece>()
            val ignored = HashSet<Int>()
            s.substringAfter('|', "").split(',').filter { it.isNotBlank() }.forEach { part ->
                val bits = part.split(':')
                val note = bits.getOrNull(0)?.toIntOrNull() ?: return@forEach
                val piece = bits.getOrNull(1)?.let { n -> DrumPiece.entries.firstOrNull { it.name == n } }
                when (bits.getOrNull(2)) {
                    "L" -> if (piece != null) learned[note] = piece
                    "M" -> if (piece != null) manual[note] = piece else ignored += note
                }
            }
            return DrumMap(profile, learned, manual, ignored)
        }
    }
}

/** One pad hit from the kit. [timeNanos] is on the System.nanoTime() clock. */
data class DrumHit(val timeNanos: Long, val note: Int, val velocity: Int, val channel: Int, val hhPedal: Int?)

/**
 * Turns raw MIDI bytes into hits: Note On (velocity > 0), with running status, and tracks the
 * hi-hat foot controller (CC4). Real-time and SysEx bytes are skipped.
 */
class DrumMidiParser {
    private var status = 0
    private val data = IntArray(2)
    private var count = 0
    private var inSysex = false

    /** Latest CC4 value (null until the kit sends one). */
    var hhPedal: Int? = null
        private set

    fun feed(bytes: ByteArray, offset: Int, length: Int, timeNanos: Long, out: (DrumHit) -> Unit) {
        for (i in offset until offset + length) {
            val b = bytes[i].toInt() and 0xFF
            if (b >= 0xF8) continue // real-time (clock, active sensing …)
            if (b == 0xF0) { inSysex = true; continue }
            if (b == 0xF7) { inSysex = false; continue }
            if (inSysex) continue
            if (b >= 0x80) {
                status = if (b >= 0xF0) 0 else b
                count = 0
                continue
            }
            if (status == 0) continue
            data[count++] = b
            val need = when (status and 0xF0) { 0xC0, 0xD0 -> 1; else -> 2 }
            if (count < need) continue
            count = 0
            val ch = status and 0x0F
            when (status and 0xF0) {
                0x90 -> if (data[1] > 0) out(DrumHit(timeNanos, data[0], data[1], ch, hhPedal))
                0xB0 -> if (data[0] == 4) hhPedal = data[1]
            }
        }
    }
}

/**
 * Resolves a hit to a piece. When the kit sends CC4, an "open" hi-hat note with the pedal
 * part-way down is a half-open hit. (Only the middle of the range is used, so it works whichever
 * way round the kit counts the pedal.)
 */
fun resolvePiece(map: DrumMap, hit: DrumHit): DrumPiece? {
    val base = map.pieceFor(hit.note) ?: return null
    if (!map.profile.usesCc4 || base != DrumPiece.HH_OPEN || map.isPinned(hit.note)) return base
    val v = hit.hhPedal ?: return base
    return if (v in 28..99) DrumPiece.HH_HALF else base
}
