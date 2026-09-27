package com.theveloper.pixelplay.data.songsterr.model

/**
 * One Songsterr part (a single instrument track) as served by the CloudFront CDN.
 *
 * These classes are filled by [com.theveloper.pixelplay.data.songsterr.SongsterrJson], a
 * tolerant hand-written reader. Songsterr's JSON is not strictly typed (accents are numbers,
 * drum `string` values are fractional, alternate endings are arrays), so reflection-based Gson
 * parsing used to throw and send the user to the website.
 */
data class RevisionTrack(
    val name: String? = null,
    val instrument: String? = null,
    val instrumentId: Int? = null,
    val partId: Int? = null,
    val songId: Long? = null,
    val revisionId: Long? = null,
    val version: Int? = null,
    /** MIDI note numbers, highest string first. Empty for drums. */
    val tuning: List<Int> = emptyList(),
    val strings: Int? = null,
    val frets: Int? = null,
    val capo: Int? = null,
    /** The first bar is a pickup (shorter than its time signature). */
    val anacrusis: Boolean = false,
    val measures: List<RevisionMeasure> = emptyList(),
    val automations: RevisionAutomations? = null,
)

data class RevisionAutomations(
    val tempo: List<RevisionTempoPoint> = emptyList(),
)

data class RevisionTempoPoint(
    val measure: Int = 0,
    val position: Int = 0,
    val bpm: Double = 120.0,
    /** Note value the bpm refers to (4 = quarter). */
    val type: Int = 4,
)

data class RevisionMeasure(
    val voices: List<RevisionVoice> = emptyList(),
    /** Only present on the bar where the signature changes. */
    val signature: List<Int>? = null,
    val marker: RevisionMarker? = null,
    val repeatStart: Boolean = false,
    /** Total play count on a repeat-end bar (Songsterr field `repeat`). */
    val repeat: Int? = null,
    val alternateEnding: List<Int> = emptyList(),
    val doubleBarline: Boolean = false,
    val rest: Boolean = false,
) {
    /** Kept for older callers. */
    val repeatCount: Int? get() = repeat
}

data class RevisionVoice(
    val beats: List<RevisionBeat> = emptyList(),
    val rest: Boolean = false,
)

data class RevisionBeat(
    val notes: List<RevisionNote> = emptyList(),
    /** Written note value (1, 2, 4, 8, 16, 32, 64). */
    val type: Int? = null,
    /** Real length as a fraction of a whole note, dots and tuplets already applied. */
    val duration: List<Int>? = null,
    val dots: Int = 0,
    val tuplet: Int? = null,
    val tupletStart: Boolean = false,
    val tupletStop: Boolean = false,
    val beamStart: Boolean = false,
    val beamStop: Boolean = false,
    val velocity: String? = null,
    val rest: Boolean = false,
    val palmMute: Boolean = false,
    val letRing: Boolean = false,
    val vibrato: Boolean = false,
    val wideVibrato: Boolean = false,
    val pickStroke: String? = null,
    /** "onBeat" / "beforeBeat" — takes no time in the bar. */
    val graceNote: String? = null,
    val tapping: Boolean = false,
    val chordText: String? = null,
    val text: String? = null,
    val upStroke: Boolean = false,
    val downStroke: Boolean = false,
    val tremoloPicking: Boolean = false,
    val tremoloBar: Boolean = false,
)

data class RevisionNote(
    val fret: Int? = null,
    /** Guitar: string index, 0 = highest. Drums: staff line, may be fractional or negative. */
    val string: Double? = null,
    val tie: Boolean = false,
    val rest: Boolean = false,
    val dead: Boolean = false,
    val ghost: Boolean = false,
    val hp: Boolean = false,
    val staccato: Boolean = false,
    /** 0 = none, 1 = accent, 2 = heavy accent. */
    val accentuated: Int = 0,
    val vibrato: Boolean = false,
    val wideVibrato: Boolean = false,
    val slide: String? = null,
    val harmonic: String? = null,
    val harmonicFret: Double? = null,
    val bend: RevisionBend? = null,
    val trill: Boolean = false,
)

data class RevisionBend(
    /** Hundredths of a whole tone: 25 = ¼, 50 = ½, 100 = full. */
    val tone: Int = 0,
    val points: List<RevisionBendPoint> = emptyList(),
)

data class RevisionBendPoint(
    val position: Int = 0,
    val tone: Int = 0,
)

data class RevisionMarker(
    val text: String = "",
    val width: Int = 0,
)
