package com.theveloper.pixelplay.data.songsterr

/**
 * Every notation mark the tab view can show, per instrument family, with:
 *  - [usedIn]: which of them the loaded part actually uses (for "in this song" highlights), and
 *  - [demoTrack]: a short example bar of each one, built from the same render model as a real tab,
 *    so it plays through exactly the same path as the song (tab → [TabMidi] → strings / samples).
 */
object NotationCatalog {

    enum class Group(val title: String) {
        NOTES("Notes"),
        LEGATO("Legato & slides"),
        BENDS("Bends & vibrato"),
        HARMONICS("Harmonics"),
        MUTING("Muting & sustain"),
        PICKING("Picking & articulation"),
        RHYTHM("Rhythm & structure"),
        KIT("Drum kit"),
    }

    data class Item(
        val id: String,
        /** What the mark looks like in the tab. */
        val symbol: String,
        val name: String,
        val description: String,
        val group: Group,
        /** Drum kit pieces: the Songsterr articulation id. */
        val drumId: Int? = null,
    )

    private val STRING_ITEMS = listOf(
        Item("fret", "5", "Fretted note", "Play the string at that fret.", Group.NOTES),
        Item("open", "0", "Open string", "Play the string without fretting it.", Group.NOTES),
        Item("chord", "5 · 7 · 7", "Chord", "Several strings struck together.", Group.NOTES),
        Item("tie", "5 ⁀ 5", "Tied note", "Keep the note ringing into the next beat; don't pick it again.", Group.NOTES),
        Item("ghost", "(5)", "Ghost note", "Played very softly, felt more than heard.", Group.NOTES),
        Item("dead", "x", "Dead note", "Rest the fretting hand on the string and pick: a percussive click with no pitch.", Group.NOTES),
        Item("grace", "⁵7", "Grace note", "A very quick note just before the main one.", Group.NOTES),

        Item("hammer", "H", "Hammer-on", "Sound the higher note by fretting it firmly, without picking.", Group.LEGATO),
        Item("pull", "P", "Pull-off", "Sound the lower note by pulling the finger off the string, without picking.", Group.LEGATO),
        Item("slide_legato", "5 / 7", "Legato slide", "Slide to the next note and let the slide sound it; no second pick.", Group.LEGATO),
        Item("slide_shift", "5 S 7", "Shift slide", "Slide to the next note, then pick it again.", Group.LEGATO),
        Item("slide_in", "/7", "Slide in", "Arrive at the note by sliding up (or down) into it.", Group.LEGATO),
        Item("slide_out", "7\\", "Slide out", "After playing the note, slide away from it as it fades.", Group.LEGATO),
        Item("tapping", "T", "Tapping", "Hammer the note onto the fretboard with a picking-hand finger.", Group.LEGATO),
        Item("trill", "tr", "Trill", "Alternate quickly between the note and the one above with hammer-ons and pull-offs.", Group.LEGATO),

        Item("bend", "↑ full", "Bend", "Push the string sideways to raise the pitch: ½ = one fret, full = two frets.", Group.BENDS),
        Item("bend_release", "↑ ↓", "Bend and release", "Bend up, then let the string come back to the fretted pitch.", Group.BENDS),
        Item("prebend", "pre ↑", "Pre-bend", "Bend the string before picking it, then (usually) release it.", Group.BENDS),
        Item("vibrato", "~", "Vibrato", "Gently shake the pitch by rocking the string.", Group.BENDS),
        Item("wide_vibrato", "~~~", "Wide vibrato", "A wider, slower vibrato.", Group.BENDS),
        Item("whammy", "w/bar", "Whammy bar", "Dip the pitch with the tremolo arm and bring it back.", Group.BENDS),

        Item("harmonic_natural", "<12>", "Natural harmonic", "Touch the string lightly over the fret (not pressing it) and pick: a bell-like tone.", Group.HARMONICS),
        Item("harmonic_artificial", "A.H.", "Artificial harmonic", "Fret the note and touch the string 12 frets higher with the picking hand.", Group.HARMONICS),
        Item("harmonic_pinch", "P.H.", "Pinch harmonic", "Let the thumb graze the string right after the pick: a squeal.", Group.HARMONICS),
        Item("harmonic_tapped", "T.H.", "Tapped harmonic", "Tap the string 12 frets above the fretted note.", Group.HARMONICS),
        Item("harmonic_semi", "S.H.", "Semi harmonic", "A pinch harmonic mixed with the plain note.", Group.HARMONICS),

        Item("palm_mute", "P.M. - - -", "Palm mute", "Rest the picking hand's palm on the strings near the bridge: a tight, chunky sound.", Group.MUTING),
        Item("let_ring", "let ring", "Let ring", "Let every note keep sounding over the next ones.", Group.MUTING),
        Item("staccato", "·", "Staccato", "Cut the note short.", Group.MUTING),

        Item("accent", ">", "Accent", "Play the note louder.", Group.PICKING),
        Item("heavy_accent", "^", "Heavy accent", "Play the note much louder.", Group.PICKING),
        Item("pick_down", "⊓", "Downstroke", "Pick towards the floor.", Group.PICKING),
        Item("pick_up", "V", "Upstroke", "Pick towards the ceiling.", Group.PICKING),
        Item("strum_down", "↓ chord", "Strum down", "Strum the chord from the low strings to the high ones.", Group.PICKING),
        Item("strum_up", "↑ chord", "Strum up", "Strum the chord from the high strings to the low ones.", Group.PICKING),
        Item("tremolo_picking", "≡", "Tremolo picking", "Pick the same note as fast and evenly as possible.", Group.PICKING),
        Item("dynamics", "p · mf · ff", "Dynamics", "How loud to play: p soft, mf medium, ff very loud.", Group.PICKING),

        Item("triplet", "⌐ 3 ¬", "Triplet", "Three notes in the time of two.", Group.RHYTHM),
        Item("repeat", "‖: :‖", "Repeat", "Play the bars between the repeat signs again (×2 = twice).", Group.RHYTHM),
    )

    private val DRUM_TECHNIQUES = listOf(
        Item("accent", ">", "Accent", "Hit the drum harder.", Group.PICKING),
        Item("ghost", "(●)", "Ghost note", "A very soft hit, usually on the snare.", Group.PICKING),
        Item("grace", "flam", "Flam", "A soft grace hit just before the main hit.", Group.PICKING),
        Item("dynamics", "p · mf · ff", "Dynamics", "How loud to play.", Group.PICKING),
        Item("triplet", "⌐ 3 ¬", "Triplet", "Three hits in the time of two.", Group.RHYTHM),
        Item("repeat", "‖: :‖", "Repeat", "Play the bars between the repeat signs again.", Group.RHYTHM),
    )

    private val OTHER_ITEMS = listOf(
        Item("fret", "♩", "Note", "A note on the staff; its height is its pitch.", Group.NOTES),
        Item("chord", "♩♩♩", "Chord", "Several notes played together.", Group.NOTES),
        Item("tie", "♩⁀♩", "Tie", "Hold the note into the next one.", Group.NOTES),
        Item("grace", "♪ small", "Grace note", "A very quick note just before the main one.", Group.NOTES),
        Item("staccato", "·", "Staccato", "Cut the note short.", Group.MUTING),
        Item("accent", ">", "Accent", "Play the note louder.", Group.PICKING),
        Item("heavy_accent", "^", "Heavy accent", "Play the note much louder.", Group.PICKING),
        Item("dynamics", "p · mf · ff", "Dynamics", "How loud to play.", Group.PICKING),
        Item("triplet", "⌐ 3 ¬", "Triplet", "Three notes in the time of two.", Group.RHYTHM),
        Item("repeat", "‖: :‖", "Repeat", "Play the bars between the repeat signs again.", Group.RHYTHM),
    )

    /** The full list for an instrument family, in reading order. */
    fun itemsFor(family: TabParser.InstrumentFamily): List<Item> = when (family) {
        TabParser.InstrumentFamily.GUITAR -> STRING_ITEMS
        TabParser.InstrumentFamily.BASS -> STRING_ITEMS.filterNot { it.id == "whammy" || it.id == "harmonic_semi" }
        TabParser.InstrumentFamily.DRUMS ->
            TabParser.drumArticulations.map {
                Item("drum:${it.id}", "", it.name, "General MIDI sound ${it.gm}", Group.KIT, drumId = it.id)
            } + DRUM_TECHNIQUES
        TabParser.InstrumentFamily.OTHER -> OTHER_ITEMS
    }

    // ── Which marks the part uses ─────────────────────────────────────────────

    /** First bar (measure index) each notation appears in. */
    fun usedIn(track: RenderedTrack): Map<String, Int> {
        val found = LinkedHashMap<String, Int>()
        fun mark(id: String, measure: Int) { if (id !in found) found[id] = measure }
        for (m in track.measures) {
            val mi = m.index
            if (m.repeatStart || (m.repeatCount ?: 0) > 1) mark("repeat", mi)
            for (slot in m.slots) if (slot.graces.any { it.notes.isNotEmpty() }) mark("grace", mi)
            for (b in m.beats) {
                if (b.isRest) continue
                if (b.tuplet != null && b.tuplet > 1) mark("triplet", mi)
                if (b.velocity != null) mark("dynamics", mi)
                if (track.isDrums) {
                    for (n in b.notes) {
                        mark("drum:${n.fret}", mi)
                        if (n.isAccented) mark("accent", mi)
                        if (n.isGhost) mark("ghost", mi)
                    }
                    continue
                }
                if (b.notes.size >= 2) mark("chord", mi)
                if (b.palmMute) mark("palm_mute", mi)
                if (b.letRing) mark("let_ring", mi)
                if (b.vibrato) mark("vibrato", mi)
                if (b.wideVibrato) mark("wide_vibrato", mi)
                if (b.tapping) mark("tapping", mi)
                if (b.tremoloPicking) mark("tremolo_picking", mi)
                if (b.tremoloBar) mark("whammy", mi)
                if (b.downStroke) mark("strum_down", mi)
                if (b.upStroke) mark("strum_up", mi)
                b.pickStroke?.let { ps ->
                    if (ps.contains("down", ignoreCase = true)) mark("pick_down", mi)
                    if (ps.contains("up", ignoreCase = true)) mark("pick_up", mi)
                }
                for (n in b.notes) {
                    if (!n.isDead) mark("fret", mi)
                    if (n.fret == 0 && !n.isDead && n.harmonic == null) mark("open", mi)
                    if (n.isTie) mark("tie", mi)
                    if (n.isGhost) mark("ghost", mi)
                    if (n.isDead) mark("dead", mi)
                    when (n.hpLabel) { "h" -> mark("hammer", mi); "p" -> mark("pull", mi) }
                    n.slide?.let { s ->
                        when {
                            s.endsWith("legato") && !s.startsWith("below") && !s.startsWith("above") -> mark("slide_legato", mi)
                            s.endsWith("shift") && !s.startsWith("below") && !s.startsWith("above") -> mark("slide_shift", mi)
                            s.startsWith("below") || s.startsWith("above") -> mark("slide_in", mi)
                            s == "downwards" || s == "upwards" -> mark("slide_out", mi)
                            else -> mark("slide_legato", mi)
                        }
                    }
                    if (n.bendTone > 0) when {
                        n.preBend -> mark("prebend", mi)
                        n.bendRelease -> mark("bend_release", mi)
                        else -> mark("bend", mi)
                    }
                    if (n.vibrato) mark("vibrato", mi)
                    if (n.wideVibrato) mark("wide_vibrato", mi)
                    if (n.trill) mark("trill", mi)
                    if (n.staccato) mark("staccato", mi)
                    when (n.accent) { 1 -> mark("accent", mi); 2 -> mark("heavy_accent", mi) }
                    when (n.harmonic) {
                        null -> Unit
                        "natural" -> mark("harmonic_natural", mi)
                        "artificial" -> mark("harmonic_artificial", mi)
                        "pinch" -> mark("harmonic_pinch", mi)
                        "tapped" -> mark("harmonic_tapped", mi)
                        "semi" -> mark("harmonic_semi", mi)
                        else -> mark("harmonic_artificial", mi)
                    }
                }
            }
        }
        return found
    }

    // ── Demo bars ─────────────────────────────────────────────────────────────

    private const val Q = TabParser.TICKS_PER_QUARTER

    /** A note in a demo bar: fret on string [row] of [ref] (pitch from its tuning). */
    private class DemoNote(
        val row: Int, val fret: Int,
        val tie: Boolean = false, val ghost: Boolean = false, val dead: Boolean = false,
        val accent: Int = 0, val hp: String? = null, val slide: String? = null, val nextFret: Int? = null,
        val harmonic: String? = null, val harmonicFret: Double? = null,
        val bend: List<Int> = emptyList(), val bendRelease: Boolean = false, val preBend: Boolean = false,
        val staccato: Boolean = false, val vibrato: Boolean = false, val wideVibrato: Boolean = false, val trill: Boolean = false,
        val drumId: Int? = null,
    )

    private class DemoBeat(
        val quarters: Double,
        val notes: List<DemoNote>,
        val palmMute: Boolean = false, val letRing: Boolean = false, val vibrato: Boolean = false,
        val tapping: Boolean = false, val tremolo: Boolean = false, val whammy: Boolean = false,
        val down: Boolean = false, val up: Boolean = false, val pick: String? = null,
        val velocity: String? = null, val tuplet: Int? = null,
        val graces: List<DemoNote> = emptyList(),
    )

    /**
     * A one- or two-bar example of [item], for the instrument of [ref] (its program, tuning and
     * capo). Returns null when there's nothing to hear (it can't happen for listed items).
     */
    fun demoTrack(item: Item, ref: RenderedTrack): RenderedTrack? {
        val family = ref.family
        val drums = ref.isDrums
        val bass = family == TabParser.InstrumentFamily.BASS
        val strings = ref.numStrings.coerceAtLeast(1)
        // A middle string (G on guitar, D on bass) at a comfortable fret.
        val row = if (drums) 0 else if (bass) (strings - 3).coerceAtLeast(0) else (strings / 2 - 1).coerceIn(0, strings - 1)
        val f = if (bass) 5 else 7
        fun n(fret: Int = f, r: Int = row) = DemoNote(r, fret)
        val beats: List<DemoBeat> = if (drums) {
            val id = item.drumId
            val snare = 38
            when {
                id != null -> listOf(DemoBeat(1.0, listOf(DemoNote(0, id, drumId = id))), DemoBeat(1.0, listOf(DemoNote(0, id, drumId = id))))
                item.id == "accent" -> listOf(DemoBeat(1.0, listOf(DemoNote(0, snare, drumId = snare))), DemoBeat(1.0, listOf(DemoNote(0, snare, accent = 2, drumId = snare))))
                item.id == "ghost" -> listOf(DemoBeat(0.5, listOf(DemoNote(0, snare, drumId = snare))), DemoBeat(0.5, listOf(DemoNote(0, snare, ghost = true, drumId = snare))), DemoBeat(0.5, listOf(DemoNote(0, snare, ghost = true, drumId = snare))), DemoBeat(0.5, listOf(DemoNote(0, snare, drumId = snare))))
                item.id == "grace" -> listOf(DemoBeat(1.0, listOf(DemoNote(0, snare, drumId = snare)), graces = listOf(DemoNote(0, snare, drumId = snare))), DemoBeat(1.0, listOf(DemoNote(0, snare, drumId = snare)), graces = listOf(DemoNote(0, snare, drumId = snare))))
                item.id == "dynamics" -> listOf(DemoBeat(1.0, listOf(DemoNote(0, snare, drumId = snare)), velocity = "p"), DemoBeat(1.0, listOf(DemoNote(0, snare, drumId = snare)), velocity = "mf"), DemoBeat(1.0, listOf(DemoNote(0, snare, drumId = snare)), velocity = "ff"))
                item.id == "triplet" -> List(3) { DemoBeat(2.0 / 3.0, listOf(DemoNote(0, snare, drumId = snare)), tuplet = 3) } + DemoBeat(1.0, listOf(DemoNote(0, 36, drumId = 36)))
                else -> listOf(DemoBeat(1.0, listOf(DemoNote(0, 36, drumId = 36))), DemoBeat(1.0, listOf(DemoNote(0, snare, drumId = snare))))
            }
        } else when (item.id) {
            "fret" -> listOf(DemoBeat(1.0, listOf(n(f))), DemoBeat(1.0, listOf(n(f + 2))), DemoBeat(1.0, listOf(n(f + 4))))
            "open" -> listOf(DemoBeat(1.5, listOf(n(0))), DemoBeat(1.5, listOf(n(0, (row + 1).coerceAtMost(strings - 1)))))
            "chord" -> listOf(DemoBeat(2.0, chordNotes(strings, bass)), DemoBeat(2.0, chordNotes(strings, bass)))
            "tie" -> listOf(DemoBeat(1.0, listOf(n())), DemoBeat(2.0, listOf(DemoNote(row, f, tie = true))))
            "ghost" -> listOf(DemoBeat(0.5, listOf(n())), DemoBeat(0.5, listOf(DemoNote(row, f, ghost = true))), DemoBeat(0.5, listOf(DemoNote(row, f, ghost = true))), DemoBeat(0.5, listOf(n())))
            "dead" -> listOf(DemoBeat(0.5, listOf(n())), DemoBeat(0.5, listOf(DemoNote(row, f, dead = true))), DemoBeat(0.5, listOf(n())), DemoBeat(0.5, listOf(DemoNote(row, f, dead = true))))
            "grace" -> listOf(DemoBeat(1.5, listOf(n(f + 2)), graces = listOf(n(f))), DemoBeat(1.5, listOf(n(f + 2)), graces = listOf(n(f))))
            "hammer" -> listOf(DemoBeat(1.0, listOf(DemoNote(row, f, hp = "h", nextFret = f + 2))), DemoBeat(1.0, listOf(n(f + 2))))
            "pull" -> listOf(DemoBeat(1.0, listOf(DemoNote(row, f + 2, hp = "p", nextFret = f))), DemoBeat(1.0, listOf(n(f))))
            "slide_legato" -> listOf(DemoBeat(1.0, listOf(DemoNote(row, f, slide = "legato", nextFret = f + 5))), DemoBeat(1.5, listOf(n(f + 5))))
            "slide_shift" -> listOf(DemoBeat(1.0, listOf(DemoNote(row, f, slide = "shift", nextFret = f + 5))), DemoBeat(1.5, listOf(n(f + 5))))
            "slide_in" -> listOf(DemoBeat(1.5, listOf(DemoNote(row, f + 2, slide = "below"))), DemoBeat(1.5, listOf(DemoNote(row, f + 2, slide = "below"))))
            "slide_out" -> listOf(DemoBeat(2.0, listOf(DemoNote(row, f + 5, slide = "downwards"))))
            "tapping" -> listOf(DemoBeat(0.5, listOf(DemoNote(row, f + 7, hp = "p", nextFret = f + 3)), tapping = true), DemoBeat(0.5, listOf(DemoNote(row, f + 3, hp = "p", nextFret = f))), DemoBeat(0.5, listOf(DemoNote(row, f, hp = "h", nextFret = f + 7))), DemoBeat(0.5, listOf(DemoNote(row, f + 7)), tapping = true))
            "trill" -> listOf(DemoBeat(2.0, listOf(DemoNote(row, f, trill = true))))
            "bend" -> listOf(DemoBeat(2.0, listOf(DemoNote(row, f, bend = listOf(0, 0, 15, 100, 60, 100)))))
            "bend_release" -> listOf(DemoBeat(2.5, listOf(DemoNote(row, f, bend = listOf(0, 0, 12, 100, 30, 100, 45, 0, 60, 0), bendRelease = true))))
            "prebend" -> listOf(DemoBeat(2.0, listOf(DemoNote(row, f, bend = listOf(0, 100, 25, 100, 45, 0, 60, 0), preBend = true))))
            "vibrato" -> listOf(DemoBeat(2.0, listOf(DemoNote(row, f, vibrato = true))))
            "wide_vibrato" -> listOf(DemoBeat(2.0, listOf(DemoNote(row, f, wideVibrato = true))))
            "whammy" -> listOf(DemoBeat(2.0, listOf(n()), whammy = true))
            "harmonic_natural" -> listOf(DemoBeat(1.5, listOf(DemoNote(row, 12, harmonic = "natural"))), DemoBeat(1.5, listOf(DemoNote((row + 1).coerceAtMost(strings - 1), 12, harmonic = "natural"))))
            "harmonic_artificial" -> listOf(DemoBeat(2.0, listOf(DemoNote(row, 5, harmonic = "artificial", harmonicFret = 17.0))))
            "harmonic_pinch" -> listOf(DemoBeat(2.0, listOf(DemoNote(row, f, harmonic = "pinch", vibrato = true))))
            "harmonic_tapped" -> listOf(DemoBeat(2.0, listOf(DemoNote(row, 5, harmonic = "tapped", harmonicFret = 17.0))))
            "harmonic_semi" -> listOf(DemoBeat(2.0, listOf(DemoNote(row, f, harmonic = "semi"))))
            "palm_mute" -> List(6) { DemoBeat(0.5, listOf(n(0, strings - 1)), palmMute = true) }
            "let_ring" -> List(4) { k -> DemoBeat(0.5, listOf(n(listOf(0, 2, 2, 0)[k], (strings - 1 - k).coerceAtLeast(0))), letRing = true) }
            "staccato" -> List(4) { DemoBeat(0.5, listOf(DemoNote(row, f, staccato = true))) }
            "accent" -> List(4) { k -> DemoBeat(0.5, listOf(DemoNote(row, f, accent = if (k % 2 == 0) 1 else 0))) }
            "heavy_accent" -> List(4) { k -> DemoBeat(0.5, listOf(DemoNote(row, f, accent = if (k == 0) 2 else 0))) }
            "pick_down" -> List(4) { DemoBeat(0.5, listOf(n()), pick = "down") }
            "pick_up" -> List(4) { DemoBeat(0.5, listOf(n()), pick = "up") }
            "strum_down" -> listOf(DemoBeat(2.0, chordNotes(strings, bass), down = true))
            "strum_up" -> listOf(DemoBeat(2.0, chordNotes(strings, bass), up = true))
            "tremolo_picking" -> listOf(DemoBeat(2.0, listOf(n()), tremolo = true))
            "dynamics" -> listOf(DemoBeat(1.0, listOf(n()), velocity = "p"), DemoBeat(1.0, listOf(n()), velocity = "mf"), DemoBeat(1.0, listOf(n()), velocity = "ff"))
            "triplet" -> List(3) { k -> DemoBeat(2.0 / 3.0, listOf(n(f + k * 2)), tuplet = 3) } + DemoBeat(1.0, listOf(n(f + 5)))
            "repeat" -> listOf(DemoBeat(1.0, listOf(n(f))), DemoBeat(1.0, listOf(n(f + 2))))
            else -> listOf(DemoBeat(1.0, listOf(n())))
        }
        return build(ref, beats, repeat = item.id == "repeat")
    }

    /** A playable chord on the lowest strings (power chord on bass). */
    private fun chordNotes(strings: Int, bass: Boolean): List<DemoNote> {
        val low = strings - 1
        return if (bass) listOf(DemoNote(low, 3), DemoNote(low - 1, 5))
        else listOf(DemoNote(low, 3), DemoNote(low - 1, 2), DemoNote(low - 2, 0), DemoNote(low - 3, 0), DemoNote(low - 4, 3).takeIf { strings >= 6 }).filterNotNull()
    }

    private fun build(ref: RenderedTrack, beats: List<DemoBeat>, repeat: Boolean): RenderedTrack {
        val drums = ref.isDrums
        val capo = ref.capo ?: 0
        fun pitchOf(d: DemoNote): Int =
            if (drums) TabParser.drumArticulation(d.drumId ?: d.fret).gm
            else ((ref.tuning.getOrNull(d.row) ?: listOf(64, 59, 55, 50, 45, 40, 35, 30).getOrElse(d.row) { 40 }) + capo + d.fret).coerceIn(0, 127)
        fun note(d: DemoNote): RenderedNote {
            val art = if (drums) TabParser.drumArticulation(d.drumId ?: d.fret) else null
            return RenderedNote(
                row = d.row,
                fret = d.fret,
                label = if (drums) "" else if (d.dead) "x" else d.fret.toString(),
                staffPos = art?.staffPos ?: 0f,
                pitch = pitchOf(d),
                isDead = d.dead,
                isGhost = d.ghost,
                isTie = d.tie,
                accent = d.accent,
                hpLabel = d.hp,
                slide = d.slide,
                harmonic = d.harmonic,
                bendTone = d.bend.filterIndexed { i, _ -> i % 2 == 1 }.maxOrNull() ?: 0,
                bendRelease = d.bendRelease,
                preBend = d.preBend,
                staccato = d.staccato,
                vibrato = d.vibrato,
                wideVibrato = d.wideVibrato,
                trill = d.trill,
                drumGlyph = art?.glyph ?: TabParser.DrumGlyph.HEAD,
                bendPoints = d.bend,
                nextFret = d.nextFret,
                harmonicFret = d.harmonicFret,
            )
        }
        val measures = ArrayList<RenderedMeasure>()
        val slots = ArrayList<BeatSlot>()
        val allBeats = ArrayList<RenderedBeat>()
        var tick = 0L
        beats.forEachIndexed { i, b ->
            val dur = (b.quarters * Q).toLong()
            val beat = RenderedBeat(
                index = i,
                onsetTicks = tick,
                durationTicks = dur,
                durationFraction = b.quarters / 4.0,
                dots = 0,
                tuplet = b.tuplet,
                isRest = false,
                palmMute = b.palmMute,
                letRing = b.letRing,
                vibrato = b.vibrato,
                pickStroke = b.pick,
                velocity = b.velocity,
                tapping = b.tapping,
                upStroke = b.up,
                downStroke = b.down,
                tremoloPicking = b.tremolo,
                tremoloBar = b.whammy,
                notes = b.notes.map(::note),
            )
            val graces = b.graces.map { g ->
                RenderedBeat(index = i, onsetTicks = tick, durationTicks = Q / 8, durationFraction = 1.0 / 32, dots = 0, tuplet = null,
                    isRest = false, isGrace = true, palmMute = false, vibrato = false, pickStroke = null, velocity = null, notes = listOf(note(g)))
            }
            slots += BeatSlot(tick, dur, listOf(beat), graces)
            allBeats += beat
            tick += dur
        }
        // Round the bar up to whole quarters, with a short tail so the last note can ring.
        val quarters = ((tick + Q - 1) / Q + 1).coerceIn(2, 8)
        val bpm = 96.0
        measures += RenderedMeasure(
            index = 0, timeSignature = listOf(quarters.toInt(), 4), marker = null,
            repeatStart = repeat, repeatCount = if (repeat) 2 else null,
            bpm = bpm, lengthTicks = quarters * Q, slots = slots, beats = allBeats,
        )
        return RenderedTrack(
            name = "demo", instrument = ref.instrument, instrumentId = ref.instrumentId, family = ref.family,
            numStrings = ref.numStrings, tuning = ref.tuning, capo = ref.capo, bpm = bpm.toInt(), measures = measures,
        )
    }
}
