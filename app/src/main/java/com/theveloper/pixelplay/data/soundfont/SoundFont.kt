package com.theveloper.pixelplay.data.soundfont

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import java.nio.channels.FileChannel

/**
 * A SoundFont 2 bank (for example GeneralUser GS): recorded instrument samples plus the zones
 * that say which sample plays which key, and how (tuning, loop, envelope, filter, pan).
 *
 * The sample data is memory-mapped, not copied: a 30 MB bank costs almost no heap and pages in
 * only what is played. Every preset is flattened at load into [Region]s, so finding the samples
 * for a note is a short scan with no allocation on the audio thread.
 *
 * Pure JVM (no Android), so it can be tested off-device.
 */
class SoundFont private constructor(
    /** 16-bit mono sample points, little-endian, for the whole bank. */
    internal val samples: ShortBuffer,
    private val presets: Map<Int, Preset>,
    val name: String,
) {

    /** A playable sample zone: the result of combining a preset zone with an instrument zone. */
    class Region(
        val keyLo: Int, val keyHi: Int,
        val velLo: Int, val velHi: Int,
        /** Every generator value (SoundFont 2.01 §8.1.3), preset values already added. */
        val gen: IntArray,
        val sample: SampleHeader,
    ) {
        fun matches(key: Int, vel: Int) = key in keyLo..keyHi && vel in velLo..velHi
    }

    class SampleHeader(
        val name: String,
        val start: Int, val end: Int,
        val loopStart: Int, val loopEnd: Int,
        val sampleRate: Int,
        val originalPitch: Int,
        val pitchCorrection: Int,
        val type: Int,
    )

    class Preset(val name: String, val bank: Int, val program: Int, val regions: List<Region>)

    /** Bank/program lookup with General MIDI fallbacks (drums: bank 128). */
    fun preset(bank: Int, program: Int): Preset? =
        presets[key(bank, program)]
            ?: if (bank == DRUM_BANK) presets[key(DRUM_BANK, 0)] else presets[key(0, program)]
            ?: presets[key(0, 0)]

    fun hasPreset(bank: Int, program: Int): Boolean = presets.containsKey(key(bank, program))

    /** Regions of [preset] that play [key] at [vel], into [out] (cleared first). */
    fun regionsFor(preset: Preset, key: Int, vel: Int, out: MutableList<Region>) {
        out.clear()
        for (r in preset.regions) if (r.matches(key, vel)) out += r
    }

    val sampleCount: Int get() = samples.limit()

    companion object {
        const val DRUM_BANK = 128
        private fun key(bank: Int, program: Int) = bank * 1000 + program

        // Generator ids used below (SoundFont 2.01 §8.1.2).
        const val START_OFFSET = 0
        const val END_OFFSET = 1
        const val START_LOOP_OFFSET = 2
        const val END_LOOP_OFFSET = 3
        const val START_COARSE = 4
        const val MOD_LFO_TO_PITCH = 5
        const val VIB_LFO_TO_PITCH = 6
        const val MOD_ENV_TO_PITCH = 7
        const val FILTER_FC = 8
        const val FILTER_Q = 9
        const val MOD_LFO_TO_FILTER = 10
        const val MOD_ENV_TO_FILTER = 11
        const val END_COARSE = 12
        const val MOD_LFO_TO_VOLUME = 13
        const val REVERB_SEND = 16
        const val PAN = 17
        const val DELAY_MOD_LFO = 21
        const val FREQ_MOD_LFO = 22
        const val DELAY_VIB_LFO = 23
        const val FREQ_VIB_LFO = 24
        const val DELAY_MOD_ENV = 25
        const val ATTACK_MOD_ENV = 26
        const val HOLD_MOD_ENV = 27
        const val DECAY_MOD_ENV = 28
        const val SUSTAIN_MOD_ENV = 29
        const val RELEASE_MOD_ENV = 30
        const val KEY_TO_MOD_ENV_HOLD = 31
        const val KEY_TO_MOD_ENV_DECAY = 32
        const val DELAY_VOL_ENV = 33
        const val ATTACK_VOL_ENV = 34
        const val HOLD_VOL_ENV = 35
        const val DECAY_VOL_ENV = 36
        const val SUSTAIN_VOL_ENV = 37
        const val RELEASE_VOL_ENV = 38
        const val KEY_TO_VOL_ENV_HOLD = 39
        const val KEY_TO_VOL_ENV_DECAY = 40
        const val INSTRUMENT = 41
        const val KEY_RANGE = 43
        const val VEL_RANGE = 44
        const val START_LOOP_COARSE = 45
        const val KEYNUM = 46
        const val VELOCITY = 47
        const val ATTENUATION = 48
        const val END_LOOP_COARSE = 50
        const val COARSE_TUNE = 51
        const val FINE_TUNE = 52
        const val SAMPLE_ID = 53
        const val SAMPLE_MODES = 54
        const val SCALE_TUNING = 56
        const val EXCLUSIVE_CLASS = 57
        const val ROOT_KEY = 58
        private const val GEN_COUNT = 61

        /** Generators that only exist at instrument level, or are ranges / links: never summed. */
        private val NON_ADDITIVE = intArrayOf(
            START_OFFSET, END_OFFSET, START_LOOP_OFFSET, END_LOOP_OFFSET, START_COARSE, END_COARSE,
            START_LOOP_COARSE, END_LOOP_COARSE, INSTRUMENT, KEY_RANGE, VEL_RANGE, KEYNUM, VELOCITY,
            SAMPLE_ID, SAMPLE_MODES, EXCLUSIVE_CLASS, ROOT_KEY,
        ).toHashSet()

        private fun defaults(): IntArray = IntArray(GEN_COUNT).also {
            it[FILTER_FC] = 13500
            for (g in intArrayOf(
                DELAY_MOD_LFO, DELAY_VIB_LFO, DELAY_MOD_ENV, ATTACK_MOD_ENV, HOLD_MOD_ENV, DECAY_MOD_ENV,
                RELEASE_MOD_ENV, DELAY_VOL_ENV, ATTACK_VOL_ENV, HOLD_VOL_ENV, DECAY_VOL_ENV, RELEASE_VOL_ENV,
            )) it[g] = -12000
            it[KEY_RANGE] = 127 shl 8 // lo = 0, hi = 127 (packed hi<<8 | lo)
            it[VEL_RANGE] = 127 shl 8
            it[KEYNUM] = -1
            it[VELOCITY] = -1
            it[SCALE_TUNING] = 100
            it[ROOT_KEY] = -1
        }

        /** Opens and indexes an .sf2 file. Throws on a file that isn't a valid SoundFont 2. */
        fun load(file: File): SoundFont {
            RandomAccessFile(file, "r").use { raf ->
                val channel = raf.channel
                val map = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()).order(ByteOrder.LITTLE_ENDIAN)
                return parse(map)
            }
        }

        fun parse(buf: ByteBuffer): SoundFont {
            val b = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            require(fourcc(b, 0) == "RIFF" && fourcc(b, 8) == "sfbk") { "Not a SoundFont 2 file" }
            var name = "SoundFont"
            var smplOffset = -1
            var smplSize = 0
            val pdta = HashMap<String, Pair<Int, Int>>() // chunk id -> (offset, size)
            var pos = 12
            val end = minOf(b.limit(), 8 + b.getInt(4))
            while (pos + 8 <= end) {
                val id = fourcc(b, pos)
                val size = b.getInt(pos + 4)
                if (id == "LIST") {
                    val kind = fourcc(b, pos + 8)
                    var p = pos + 12
                    val listEnd = pos + 8 + size
                    while (p + 8 <= listEnd) {
                        val sub = fourcc(b, p)
                        val subSize = b.getInt(p + 4)
                        when {
                            kind == "INFO" && sub == "INAM" -> name = string(b, p + 8, subSize)
                            kind == "sdta" && sub == "smpl" -> { smplOffset = p + 8; smplSize = subSize }
                            kind == "pdta" -> pdta[sub] = (p + 8) to subSize
                        }
                        p += 8 + subSize + (subSize and 1)
                    }
                }
                pos += 8 + size + (size and 1)
            }
            require(smplOffset >= 0) { "SoundFont has no sample data" }
            for (c in listOf("phdr", "pbag", "pgen", "inst", "ibag", "igen", "shdr")) require(c in pdta) { "SoundFont is missing $c" }

            val samplesBuf = b.duplicate().order(ByteOrder.LITTLE_ENDIAN).apply {
                position(smplOffset)
                limit(smplOffset + smplSize)
            }.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

            // ── Sample headers ──
            val (shdrOff, shdrSize) = pdta.getValue("shdr")
            val sampleHeaders = (0 until shdrSize / 46 - 1).map { i ->
                val o = shdrOff + i * 46
                SampleHeader(
                    name = string(b, o, 20),
                    start = b.getInt(o + 20), end = b.getInt(o + 24),
                    loopStart = b.getInt(o + 28), loopEnd = b.getInt(o + 32),
                    sampleRate = b.getInt(o + 36).takeIf { it in 400..200_000 } ?: 44_100,
                    originalPitch = (b.get(o + 40).toInt() and 0xFF).let { if (it > 127) 60 else it },
                    pitchCorrection = b.get(o + 41).toInt(),
                    type = b.getShort(o + 44).toInt() and 0xFFFF,
                )
            }

            fun bags(chunk: String): IntArray {
                val (off, size) = pdta.getValue(chunk)
                return IntArray(size / 4) { i -> b.getShort(off + i * 4).toInt() and 0xFFFF }
            }
            fun gens(chunk: String): List<Pair<Int, Int>> {
                val (off, size) = pdta.getValue(chunk)
                return (0 until size / 4).map { i ->
                    val o = off + i * 4
                    (b.getShort(o).toInt() and 0xFFFF) to b.getShort(o + 2).toInt()
                }
            }
            val ibag = bags("ibag")
            val igen = gens("igen")
            val pbag = bags("pbag")
            val pgen = gens("pgen")

            /** Generator list of a zone: (id, raw 16-bit amount). Ranges keep lo/hi bytes. */
            fun zoneGens(bagList: IntArray, genList: List<Pair<Int, Int>>, zone: Int): List<Pair<Int, Int>> {
                val from = bagList[zone]
                val to = if (zone + 1 < bagList.size) bagList[zone + 1] else genList.size
                return (from until to.coerceAtMost(genList.size)).map { genList[it] }
            }
            fun apply(target: IntArray, list: List<Pair<Int, Int>>) {
                for ((id, amount) in list) if (id < GEN_COUNT) {
                    target[id] = if (id == KEY_RANGE || id == VEL_RANGE) {
                        // lo byte = low, hi byte = high
                        val lo = amount and 0xFF
                        val hi = (amount shr 8) and 0xFF
                        (hi shl 8) or lo
                    } else amount
                }
            }
            fun lo(range: Int) = range and 0xFF
            fun hi(range: Int) = (range shr 8) and 0xFF

            // ── Instruments: each flattened into zones with a sample ──
            class InstZone(val gen: IntArray, val set: BooleanArray)
            val (instOff, instSize) = pdta.getValue("inst")
            val instCount = instSize / 22 - 1
            val instruments = (0 until instCount).map { i ->
                val o = instOff + i * 22
                val firstBag = b.getShort(o + 20).toInt() and 0xFFFF
                val lastBag = b.getShort(o + 22 + 20).toInt() and 0xFFFF
                var global: List<Pair<Int, Int>> = emptyList()
                val zones = ArrayList<InstZone>()
                for (z in firstBag until lastBag) {
                    if (z >= ibag.size) break
                    val list = zoneGens(ibag, igen, z)
                    val hasSample = list.any { it.first == SAMPLE_ID }
                    if (!hasSample) {
                        if (z == firstBag) global = list
                        continue
                    }
                    val g = defaults()
                    val set = BooleanArray(GEN_COUNT)
                    apply(g, global); global.forEach { if (it.first < GEN_COUNT) set[it.first] = true }
                    apply(g, list); list.forEach { if (it.first < GEN_COUNT) set[it.first] = true }
                    zones += InstZone(g, set)
                }
                zones
            }

            // ── Presets ──
            val (phdrOff, phdrSize) = pdta.getValue("phdr")
            val presetCount = phdrSize / 38 - 1
            val presets = HashMap<Int, Preset>()
            for (i in 0 until presetCount) {
                val o = phdrOff + i * 38
                val presetName = string(b, o, 20)
                val program = b.getShort(o + 20).toInt() and 0xFFFF
                val bank = b.getShort(o + 22).toInt() and 0xFFFF
                val firstBag = b.getShort(o + 24).toInt() and 0xFFFF
                val lastBag = b.getShort(o + 38 + 24).toInt() and 0xFFFF
                var global: List<Pair<Int, Int>> = emptyList()
                val regions = ArrayList<Region>()
                for (z in firstBag until lastBag) {
                    if (z >= pbag.size) break
                    val list = zoneGens(pbag, pgen, z)
                    val instId = list.firstOrNull { it.first == INSTRUMENT }?.second?.and(0xFFFF)
                    if (instId == null) {
                        if (z == firstBag) global = list
                        continue
                    }
                    if (instId !in instruments.indices) continue
                    // Preset-level values: relative offsets added to the instrument's.
                    val pg = IntArray(GEN_COUNT)
                    val pRange = intArrayOf(127 shl 8, 127 shl 8)
                    for ((id, amount) in global + list) {
                        if (id >= GEN_COUNT) continue
                        when (id) {
                            KEY_RANGE -> pRange[0] = ((amount shr 8 and 0xFF) shl 8) or (amount and 0xFF)
                            VEL_RANGE -> pRange[1] = ((amount shr 8 and 0xFF) shl 8) or (amount and 0xFF)
                            else -> pg[id] = amount
                        }
                    }
                    // A zone's own value replaces the global one (not summed twice).
                    val pZone = IntArray(GEN_COUNT)
                    val pSet = BooleanArray(GEN_COUNT)
                    for ((id, amount) in global) if (id < GEN_COUNT && id != KEY_RANGE && id != VEL_RANGE) { pZone[id] = amount; pSet[id] = true }
                    for ((id, amount) in list) if (id < GEN_COUNT && id != KEY_RANGE && id != VEL_RANGE) { pZone[id] = amount; pSet[id] = true }

                    for (iz in instruments[instId]) {
                        val g = iz.gen.copyOf()
                        for (id in 0 until GEN_COUNT) {
                            if (pSet[id] && id !in NON_ADDITIVE) g[id] += pZone[id]
                        }
                        val sampleId = g[SAMPLE_ID] and 0xFFFF
                        val sh = sampleHeaders.getOrNull(sampleId) ?: continue
                        if (sh.type and 0x8000 != 0) continue // ROM sample: not in the file
                        val keyLo = maxOf(lo(g[KEY_RANGE]), lo(pRange[0]))
                        val keyHi = minOf(hi(g[KEY_RANGE]), hi(pRange[0]))
                        val velLo = maxOf(lo(g[VEL_RANGE]), lo(pRange[1]))
                        val velHi = minOf(hi(g[VEL_RANGE]), hi(pRange[1]))
                        if (keyLo > keyHi || velLo > velHi) continue
                        regions += Region(keyLo, keyHi, velLo, velHi, g, sh)
                    }
                }
                if (regions.isNotEmpty()) presets.putIfAbsent(key(bank, program), Preset(presetName, bank, program, regions))
            }
            require(presets.isNotEmpty()) { "SoundFont has no presets" }
            return SoundFont(samplesBuf, presets, name)
        }

        private fun fourcc(b: ByteBuffer, at: Int): String {
            if (at + 4 > b.limit()) return ""
            val chars = CharArray(4) { (b.get(at + it).toInt() and 0xFF).toChar() }
            return String(chars)
        }

        private fun string(b: ByteBuffer, at: Int, len: Int): String {
            val sb = StringBuilder()
            for (i in 0 until len) {
                if (at + i >= b.limit()) break
                val c = b.get(at + i).toInt() and 0xFF
                if (c == 0) break
                sb.append(c.toChar())
            }
            return sb.toString().trim()
        }
    }
}
