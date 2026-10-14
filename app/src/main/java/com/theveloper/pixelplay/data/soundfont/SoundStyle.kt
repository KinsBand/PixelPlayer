package com.theveloper.pixelplay.data.soundfont

/**
 * How the tab Synth sounds once the instrument sounds are downloaded.
 *
 * - [REAL]: recorded guitars, basses, drums and keys (the SoundFont as it is).
 * - [CHIP]: 8-bit. Melodic parts on a square-wave lead, basses on a synth bass, drums unchanged.
 * - [SYNTH]: saw leads and synth basses, pads for strings and keys.
 * - [STRINGS]: no samples: guitars and basses on the modelled strings, other parts on Android's
 *   MIDI synth. Also what plays before the sounds are downloaded.
 *
 * Every style keeps the full string articulation (bends, slides, hammer-ons…) because those are
 * pitch changes on the voice, not part of the sample.
 */
enum class SoundStyle(val label: String) {
    REAL("Real"),
    CHIP("8-bit"),
    SYNTH("Synth"),
    STRINGS("Strings");

    val usesSamples: Boolean get() = this != STRINGS

    /** General MIDI melodic [program] (bank 0, not drums) for this style. */
    fun mapProgram(program: Int): Int {
        val p = program and 127
        if (this == REAL || this == STRINGS) return p
        val family = p / 8
        return when (this) {
            CHIP -> when (family) {
                4 -> SYNTH_BASS_1                    // basses
                12, 13, 14, 15 -> p                  // percussive, sound effects: leave
                else -> SQUARE_LEAD                  // pianos, organs, guitars, strings, brass, leads, pads…
            }
            SYNTH -> when (family) {
                4 -> SYNTH_BASS_2
                5, 6, 11 -> WARM_PAD                 // strings, ensembles, pads
                12, 13, 14, 15 -> p
                else -> SAW_LEAD
            }
            else -> p
        }
    }

    companion object {
        const val SQUARE_LEAD = 80
        const val SAW_LEAD = 81
        const val SYNTH_BASS_1 = 38
        const val SYNTH_BASS_2 = 39
        const val WARM_PAD = 89

        /**
         * The style every sampled engine (tab Synth and the notation demos) uses. Set by the tab
         * practice controller from its saved preference; read when an engine is created.
         */
        @Volatile var current: SoundStyle = REAL

        fun fromName(name: String?): SoundStyle = entries.firstOrNull { it.name == name } ?: REAL
    }
}
