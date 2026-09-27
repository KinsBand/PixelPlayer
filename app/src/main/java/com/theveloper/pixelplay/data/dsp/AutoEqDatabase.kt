package com.theveloper.pixelplay.data.dsp

import com.theveloper.pixelplay.data.equalizer.EqualizerPreset

/**
 * An AutoEq headphone calibration profile.
 */
data class AutoEqHeadphoneProfile(
    val manufacturer: String,
    val model: String,
    val target: String,
    val bandLevels: List<Int>, // 10 bands (-15 to +15 dB)
    val recommendedPreampDb: Float
) {
    val displayName: String
        get() = "$manufacturer $model ($target)"

    val id: String
        get() = "autoeq_${manufacturer.lowercase().replace(" ", "_")}_${model.lowercase().replace(" ", "_")}_${target.lowercase().replace(" ", "_")}"

    fun toEqualizerPreset(): EqualizerPreset {
        return EqualizerPreset(
            name = id,
            displayName = "$manufacturer $model",
            bandLevels = bandLevels,
            isCustom = true,
            isAutoEq = true,
            headphoneModel = "$manufacturer $model",
            targetCurve = target,
            preAmpDb = recommendedPreampDb
        )
    }
}

/**
 * Curated database of popular headphone calibration profiles derived from AutoEq measurements
 * (Harman Target 2018/2019 and Neutral target curves).
 */
object AutoEqDatabase {
    val PROFILES: List<AutoEqHeadphoneProfile> = listOf(
        // SONY
        AutoEqHeadphoneProfile(
            manufacturer = "Sony",
            model = "WH-1000XM4",
            target = "Harman",
            bandLevels = listOf(-3, -2, -1, 1, 0, 1, 3, 2, -1, 2),
            recommendedPreampDb = -3.5f
        ),
        AutoEqHeadphoneProfile(
            manufacturer = "Sony",
            model = "WH-1000XM5",
            target = "Harman",
            bandLevels = listOf(-2, -1, 0, 1, 0, 1, 2, 3, 1, 0),
            recommendedPreampDb = -3.2f
        ),
        AutoEqHeadphoneProfile(
            manufacturer = "Sony",
            model = "WF-1000XM4",
            target = "Harman In-Ear",
            bandLevels = listOf(-1, 0, 1, 0, 1, 2, 3, 1, -2, 1),
            recommendedPreampDb = -3.0f
        ),

        // APPLE
        AutoEqHeadphoneProfile(
            manufacturer = "Apple",
            model = "AirPods Pro 2",
            target = "Harman In-Ear",
            bandLevels = listOf(1, 1, 0, 0, 0, 1, 2, 0, 2, 3),
            recommendedPreampDb = -3.0f
        ),
        AutoEqHeadphoneProfile(
            manufacturer = "Apple",
            model = "AirPods Max",
            target = "Harman",
            bandLevels = listOf(0, -1, 0, 1, 0, 1, 2, 2, 0, 1),
            recommendedPreampDb = -2.5f
        ),

        // SENNHEISER
        AutoEqHeadphoneProfile(
            manufacturer = "Sennheiser",
            model = "HD 600",
            target = "Harman",
            bandLevels = listOf(6, 4, 1, 0, 0, 0, 1, -1, 2, 1),
            recommendedPreampDb = -6.2f
        ),
        AutoEqHeadphoneProfile(
            manufacturer = "Sennheiser",
            model = "HD 650",
            target = "Harman",
            bandLevels = listOf(6, 5, 2, 0, 0, 0, 1, 0, 3, 2),
            recommendedPreampDb = -6.5f
        ),
        AutoEqHeadphoneProfile(
            manufacturer = "Sennheiser",
            model = "HD 560S",
            target = "Harman",
            bandLevels = listOf(4, 2, 0, 0, 0, 0, -1, -2, 1, 0),
            recommendedPreampDb = -4.0f
        ),

        // AUDIO-TECHNICA
        AutoEqHeadphoneProfile(
            manufacturer = "Audio-Technica",
            model = "ATH-M50x",
            target = "Harman",
            bandLevels = listOf(-2, -1, 0, 1, 1, 0, -2, -1, 3, 1),
            recommendedPreampDb = -3.0f
        ),

        // BEYERDYNAMIC
        AutoEqHeadphoneProfile(
            manufacturer = "Beyerdynamic",
            model = "DT 770 Pro 80Ω",
            target = "Harman",
            bandLevels = listOf(1, 0, -1, 1, 2, 1, -1, -4, -2, 2),
            recommendedPreampDb = -2.5f
        ),
        AutoEqHeadphoneProfile(
            manufacturer = "Beyerdynamic",
            model = "DT 990 Pro 250Ω",
            target = "Harman",
            bandLevels = listOf(4, 2, 0, 1, 2, 0, -2, -5, -4, 0),
            recommendedPreampDb = -4.5f
        ),

        // BOSE
        AutoEqHeadphoneProfile(
            manufacturer = "Bose",
            model = "QuietComfort 45",
            target = "Harman",
            bandLevels = listOf(1, 0, 1, 0, 0, 1, 2, -1, -3, 1),
            recommendedPreampDb = -2.5f
        ),
        AutoEqHeadphoneProfile(
            manufacturer = "Bose",
            model = "QuietComfort Ultra",
            target = "Harman",
            bandLevels = listOf(-1, 0, 1, 1, 0, 1, 2, 1, -1, 2),
            recommendedPreampDb = -2.5f
        ),

        // SAMSUNG
        AutoEqHeadphoneProfile(
            manufacturer = "Samsung",
            model = "Galaxy Buds 2 Pro",
            target = "Harman In-Ear",
            bandLevels = listOf(0, 0, 0, 1, 0, 1, 1, 0, 1, 2),
            recommendedPreampDb = -2.0f
        ),

        // POPULAR IEMS
        AutoEqHeadphoneProfile(
            manufacturer = "Moondrop",
            model = "Aria",
            target = "Harman In-Ear",
            bandLevels = listOf(1, 1, 0, 0, 0, 1, 1, 0, 2, 1),
            recommendedPreampDb = -2.0f
        ),
        AutoEqHeadphoneProfile(
            manufacturer = "Tangzu",
            model = "Wan'er S.G",
            target = "Harman In-Ear",
            bandLevels = listOf(1, 0, 0, 0, 0, 1, 1, -1, 2, 2),
            recommendedPreampDb = -2.2f
        ),
        AutoEqHeadphoneProfile(
            manufacturer = "7Hz",
            model = "Salnotes Zero",
            target = "IEF Neutral",
            bandLevels = listOf(2, 2, 1, 0, 0, 0, 1, 0, 1, 2),
            recommendedPreampDb = -2.5f
        ),

        // GENERIC TARGET CURVES
        AutoEqHeadphoneProfile(
            manufacturer = "Target Curve",
            model = "Harman Over-Ear 2018",
            target = "Standard",
            bandLevels = listOf(5, 4, 2, 0, 0, 0, 1, 2, 1, 0),
            recommendedPreampDb = -5.5f
        ),
        AutoEqHeadphoneProfile(
            manufacturer = "Target Curve",
            model = "Harman In-Ear 2019",
            target = "Standard",
            bandLevels = listOf(6, 5, 2, 0, 0, 1, 2, 2, 1, 0),
            recommendedPreampDb = -6.5f
        ),
        AutoEqHeadphoneProfile(
            manufacturer = "Target Curve",
            model = "IEF Neutral",
            target = "Flat/Accurate",
            bandLevels = listOf(1, 1, 0, 0, 0, 0, 0, 0, 0, 0),
            recommendedPreampDb = -1.5f
        ),
        AutoEqHeadphoneProfile(
            manufacturer = "Target Curve",
            model = "Diffuse Field",
            target = "Bright/Open",
            bandLevels = listOf(0, 0, 0, 0, 1, 2, 4, 3, 2, 1),
            recommendedPreampDb = -4.5f
        )
    )

    fun search(query: String): List<AutoEqHeadphoneProfile> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return PROFILES
        return PROFILES.filter {
            it.manufacturer.lowercase().contains(q) ||
                it.model.lowercase().contains(q) ||
                it.target.lowercase().contains(q)
        }
    }

    fun findById(id: String): AutoEqHeadphoneProfile? {
        return PROFILES.find { it.id == id }
    }
}
