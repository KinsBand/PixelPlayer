package com.theveloper.pixelplay.data.equalizer

import com.theveloper.pixelplay.data.GenreTaxonomy

/** Gentle 10-band starting points; recordings and headphones still vary within a genre. */
internal object GenreEqualizerPresets {
    private val curves = mapOf(
        "rock" to listOf(2, 2, 1, 0, -1, 0, 1, 2, 2, 1),
        "pop" to listOf(0, 1, 1, 0, 0, 1, 2, 1, 1, 0),
        "hip_hop" to listOf(3, 3, 2, 0, -1, 0, 1, 1, 1, 0),
        "jazz" to listOf(1, 1, 0, 0, 0, 1, 1, 1, 1, 0),
        "classical" to listOf(0, 0, 0, 0, 0, 0, 1, 1, 1, 0),
        "electronic" to listOf(3, 2, 1, 0, -1, 0, 1, 1, 2, 1),
        "metal" to listOf(2, 2, 1, -1, -1, 0, 2, 1, 1, 0),
        "soul" to listOf(1, 2, 1, 0, 0, 2, 2, 1, 0, 0),
        "ambient" to listOf(1, 1, 0, 0, 0, 0, 0, 0, 1, 1),
        "latin" to listOf(1, 2, 1, 0, 0, 1, 2, 1, 1, 0),
        "folk" to listOf(0, 0, 0, 0, 1, 2, 1, 1, 0, 0),
        "blues" to listOf(1, 1, 1, 0, 1, 1, 2, 0, 0, 0),
        "reggae" to listOf(3, 3, 2, 0, -1, 0, 1, 1, 0, 0),
        "african" to listOf(2, 3, 1, 0, 0, 1, 2, 1, 1, 0),
        "world" to listOf(0, 1, 0, 0, 1, 1, 1, 1, 0, 0)
    )
    private val specific = mapOf(
        "lofi" to listOf(1, 1, 1, 0, 0, 0, -1, -2, -3, -3),
        "shoegaze" to listOf(1, 1, 0, -1, 0, 1, 1, 0, -1, -1),
        "post_rock" to listOf(1, 1, 0, 0, 0, 1, 1, 1, 2, 1),
        "deep_house" to listOf(3, 3, 1, 0, -1, 0, 0, 1, 1, 0),
        "tech_house" to listOf(2, 3, 2, 0, -1, 0, 1, 2, 1, 0),
        "drum_and_bass" to listOf(3, 3, 1, -1, -1, 0, 1, 2, 2, 0),
        "dubstep" to listOf(4, 3, 1, -1, -1, 0, 1, 1, 2, 1),
        "trap" to listOf(4, 3, 1, 0, -1, 0, 1, 2, 2, 0),
        "drill" to listOf(3, 4, 2, 0, -1, 0, 1, 2, 1, 0),
        "boom_bap" to listOf(2, 3, 2, 1, 0, 1, 1, 0, 0, -1),
        "ambient" to listOf(1, 1, 0, 0, 0, 0, -1, 0, 1, 1),
        "chillout" to listOf(1, 2, 1, 0, 0, 0, 0, 0, 1, 0),
        "downtempo" to listOf(2, 2, 1, 0, 0, 0, 1, 0, 0, -1),
        "acoustic" to listOf(0, 0, 0, 0, 1, 2, 2, 1, 0, 0),
        "piano" to listOf(0, 0, 1, 1, 0, 1, 1, 0, 0, 0),
        "opera" to listOf(0, 0, 0, 0, 1, 2, 2, 1, 0, 0),
        "soundtrack" to listOf(2, 1, 0, 0, 0, 0, 1, 1, 1, 1),
        "reggae" to listOf(3, 3, 2, 0, -1, 0, 1, 0, 0, -1),
        "dub" to listOf(4, 3, 2, 0, -1, 0, 0, 0, -1, -1),
        "dancehall" to listOf(3, 3, 1, 0, -1, 1, 2, 1, 1, 0),
        "reggaeton" to listOf(3, 3, 1, 0, -1, 1, 2, 1, 1, 1),
        "salsa" to listOf(0, 1, 1, 0, 1, 1, 2, 2, 1, 0),
        "bossa_nova" to listOf(0, 1, 1, 0, 0, 1, 1, 1, 0, 0),
        "amapiano" to listOf(3, 4, 2, 0, -1, 0, 1, 1, 1, 0),
        "afrobeats" to listOf(2, 3, 1, 0, 0, 1, 2, 2, 1, 0),
        "metalcore" to listOf(2, 2, 1, -2, -1, 0, 2, 2, 1, 0),
        "death_metal" to listOf(1, 2, 1, -2, -1, 1, 2, 1, 0, -1),
        "hard_rock" to listOf(2, 3, 1, 0, -1, 1, 2, 2, 1, 0),
        "punk" to listOf(1, 2, 1, 0, 0, 1, 2, 1, 0, 0),
        "pop_punk" to listOf(2, 2, 1, 0, -1, 1, 2, 2, 1, 1),
        "synthwave" to listOf(3, 2, 1, 0, -1, 0, 1, 2, 2, 1),
        "synth_pop" to listOf(1, 2, 1, 0, 0, 1, 2, 2, 1, 1),
        "dance_pop" to listOf(2, 2, 1, 0, -1, 1, 2, 1, 2, 1),
        "k_pop" to listOf(1, 2, 1, 0, 0, 1, 2, 2, 2, 1),
        "j_pop" to listOf(0, 1, 1, 0, 0, 2, 2, 1, 1, 0),
        "neo_soul" to listOf(1, 2, 1, 1, 0, 2, 1, 0, 0, -1),
        "funk" to listOf(1, 3, 2, 0, -1, 1, 2, 1, 1, 0),
        "disco" to listOf(2, 2, 1, 0, -1, 1, 1, 2, 2, 1),
        "bluegrass" to listOf(0, 0, 0, 1, 1, 2, 2, 1, 0, 0),
        "country" to listOf(1, 1, 0, 0, 1, 2, 1, 1, 0, 0)
    )
    val all: List<EqualizerPreset> = GenreTaxonomy.genres.map { genre ->
        val bands = specific[genre.id] ?: curves.getValue(genre.family)
        EqualizerPreset("genre_${genre.id}", genre.label, bands, preAmpDb = -(bands.maxOrNull() ?: 0).toFloat())
    }
    fun forGenre(tag: String?): EqualizerPreset = GenreTaxonomy.match(tag)?.let { genre ->
        all.firstOrNull { it.name == "genre_${genre.id}" }
    } ?: EqualizerPreset.FLAT
}
