package com.theveloper.pixelplay.data.lyrics

import com.theveloper.pixelplay.data.model.*
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** Bounded native timing format. No inferred subdivision is labelled as measured alignment. */
object LyricsTiming {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun parse(raw: String): Lyrics? = runCatching {
        require(raw.length <= 1_000_000)
        json.decodeFromString<Lyrics>(raw).also { require(validate(it).isEmpty()) }
    }.getOrNull()
    fun encode(lyrics: Lyrics): String = json.encodeToString(Lyrics.serializer(), lyrics)
    fun validate(lyrics: Lyrics): List<String> = buildList {
        val lines = lyrics.synced.orEmpty()
        if (lines.size > 5000) add("Too many lyric lines")
        lines.forEach { line ->
            if (line.time < 0 || (line.endTime != null && line.endTime <= line.time)) add("Invalid line interval")
            val words = line.words.orEmpty()
            if (words.size > 1000 || words.zipWithNext().any { (a, b) -> a.time > b.time }) add("Invalid word order")
            words.forEach { word ->
                if (word.time < line.time || (word.endTime != null && word.endTime <= word.time)) add("Invalid word interval")
                if (line.endTime != null && (word.endTime ?: word.time) > line.endTime) add("Word outside line")
                val phones = word.phonemes.orEmpty()
                if (phones.size > 256 || phones.zipWithNext().any { (a, b) -> a.endTime > b.time }) add("Invalid phoneme order")
                phones.forEach { phone ->
                    if (phone.time < word.time || phone.endTime <= phone.time || (word.endTime != null && phone.endTime > word.endTime)) add("Invalid phoneme interval")
                    if (phone.characterStart !in 0..word.word.length || phone.characterEnd !in 0..word.word.length || phone.characterEnd <= phone.characterStart) add("Invalid phoneme character range")
                    if (phone.alphabet.isBlank()) add("Missing phoneme alphabet")
                }
            }
        }
    }.distinct()
    fun textHash(lyrics: Lyrics): String = hash(lyrics.synced?.joinToString("\n") { it.line } ?: lyrics.plain.orEmpty().joinToString("\n"))
    fun hash(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    fun progress(word: SyncedWord, positionMs: Long, fallbackEndMs: Long): Float {
        if (positionMs < word.time) return 0f
        val end = word.endTime?.toLong() ?: fallbackEndMs
        if (positionMs >= end) return 1f
        val phones = word.phonemes.orEmpty()
        if (phones.isNotEmpty() && word.word.isNotEmpty()) {
            val active = phones.lastOrNull { it.time <= positionMs } ?: return 0f
            val elapsed = fraction(positionMs, active.time.toLong(), active.endTime.toLong())
            return ((active.characterStart + (active.characterEnd - active.characterStart) * elapsed) / word.word.length).coerceIn(0f, 1f)
        }
        return fraction(positionMs, word.time.toLong(), end)
    }
    private fun fraction(position: Long, start: Long, end: Long): Float =
        if (end <= start) if (position >= end) 1f else 0f else ((position - start).toDouble() / (end - start)).toFloat().coerceIn(0f, 1f)
}
