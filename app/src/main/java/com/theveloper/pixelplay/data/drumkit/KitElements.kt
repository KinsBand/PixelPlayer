package com.theveloper.pixelplay.data.drumkit

/**
 * The physical parts of a drum kit as drawn in the Drum kit sheet, and how the pad list is
 * grouped. A part can stand for several [DrumPiece]s (the snare's head, rim and cross-stick are
 * one drum). Pure Kotlin, unit-tested in KitElementsTest.
 */
enum class KitElement(
    val label: String,
    /** Every piece this part plays; a hit on any of them lights it up. */
    val pieces: List<DrumPiece>,
    /** What a pad is set to when it's assigned by tapping this part. */
    val primary: DrumPiece,
    /** Only drawn when the kit actually has it (mapped, or in the kit profile). */
    val optional: Boolean,
    /** Pad-list card this part's pads go in (toms share one). */
    val padGroup: String,
) {
    CRASH("Crash", listOf(DrumPiece.CRASH), DrumPiece.CRASH, false, "Crash"),
    SPLASH("Splash", listOf(DrumPiece.SPLASH), DrumPiece.SPLASH, true, "Splash"),
    CRASH_2("Crash 2", listOf(DrumPiece.CRASH_2), DrumPiece.CRASH_2, true, "Crash 2"),
    RIDE("Ride", listOf(DrumPiece.RIDE, DrumPiece.RIDE_BELL), DrumPiece.RIDE, false, "Ride"),
    CHINA("China", listOf(DrumPiece.CHINA), DrumPiece.CHINA, true, "China"),
    HIHAT("Hi-hat", listOf(DrumPiece.HH_CLOSED, DrumPiece.HH_HALF, DrumPiece.HH_OPEN), DrumPiece.HH_CLOSED, false, "Hi-hat"),
    HIHAT_PEDAL("Hi-hat pedal", listOf(DrumPiece.HH_FOOT), DrumPiece.HH_FOOT, false, "Hi-hat"),
    TOM_1("Tom 1", listOf(DrumPiece.TOM_HIGH), DrumPiece.TOM_HIGH, false, "Toms"),
    TOM_2("Tom 2", listOf(DrumPiece.TOM_MID), DrumPiece.TOM_MID, false, "Toms"),
    TOM_3("Tom 3", listOf(DrumPiece.TOM_LOW), DrumPiece.TOM_LOW, true, "Toms"),
    FLOOR_TOM("Floor tom", listOf(DrumPiece.TOM_FLOOR), DrumPiece.TOM_FLOOR, false, "Toms"),
    SNARE("Snare", listOf(DrumPiece.SNARE, DrumPiece.SNARE_RIM, DrumPiece.SIDE_STICK), DrumPiece.SNARE, false, "Snare"),
    KICK("Kick", listOf(DrumPiece.KICK), DrumPiece.KICK, false, "Kick"),
    COWBELL("Cowbell", listOf(DrumPiece.COWBELL), DrumPiece.COWBELL, true, "Percussion"),
    TAMBOURINE("Tambourine", listOf(DrumPiece.TAMBOURINE), DrumPiece.TAMBOURINE, true, "Percussion"),
    ;

    companion object {
        /** The drawn part a piece belongs to (null for "Other"). */
        fun of(piece: DrumPiece?): KitElement? = piece?.let { p -> entries.firstOrNull { p in it.pieces } }
    }
}

/** One pad (MIDI note) in the pad list, with what it plays now (null = not mapped / ignored). */
data class PadEntry(val note: Int, val piece: DrumPiece?)

/** One card in the pad list: a kit part (or toms together) and every pad that plays it. */
data class PadGroup(val title: String, val entries: List<PadEntry>)

object KitLayoutRules {
    const val UNMAPPED_TITLE = "Not mapped"

    /** Parts to draw: every standard part, plus optional ones this kit has. */
    fun visibleElements(map: DrumMap, notes: Collection<Int>): List<KitElement> {
        val have = HashSet<DrumPiece>()
        notes.forEach { n -> map.pieceFor(n)?.let { have += it } }
        map.profile.pieces?.let { have += it }
        return KitElement.entries.filter { e -> !e.optional || e.pieces.any { it in have } }
    }

    /** Pads (notes) that play [element], in note order. */
    fun notesFor(element: KitElement, map: DrumMap, notes: Collection<Int>): List<Int> =
        notes.filter { map.pieceFor(it) in element.pieces }.distinct().sorted()

    /**
     * The pad list: one card per kit part (so a snare that sends 38 and 39 is one card, and all
     * toms share one), in kit order, then unmapped / ignored pads last.
     */
    fun groupPads(notes: Collection<Int>, map: DrumMap): List<PadGroup> {
        val byTitle = LinkedHashMap<String, MutableList<PadEntry>>()
        // Card order follows the kit parts' order.
        KitElement.entries.forEach { byTitle.getOrPut(it.padGroup) { mutableListOf() } }
        val unmapped = mutableListOf<PadEntry>()
        for (note in notes.distinct().sorted()) {
            val piece = map.pieceFor(note)
            val element = KitElement.of(piece)
            if (element == null) unmapped += PadEntry(note, piece) else byTitle.getValue(element.padGroup) += PadEntry(note, piece)
        }
        val out = byTitle.filterValues { it.isNotEmpty() }.map { (title, entries) ->
            // Inside a card, keep the kit-part order (Tom 1 before Tom 2), then the note.
            PadGroup(title, entries.sortedWith(compareBy({ KitElement.of(it.piece)?.ordinal ?: Int.MAX_VALUE }, { it.note })))
        }
        return if (unmapped.isEmpty()) out else out + PadGroup(UNMAPPED_TITLE, unmapped)
    }
}
