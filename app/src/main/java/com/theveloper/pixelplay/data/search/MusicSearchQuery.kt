package com.theveloper.pixelplay.data.search

/** Cheap, deterministic intent parsing; no model round trip on the typing path. */
data class MusicSearchQuery(
    val text: String,
    val terms: String,
    val tempo: ClosedFloatingPointRange<Float>? = null,
    val lyrics: Boolean = false,
) {
    val onlineQuery: String get() = when {
        tempo != null -> "$terms ${((tempo.start + tempo.endInclusive) / 2).toInt()} bpm songs".trim()
        lyrics -> "$terms lyrics".trim()
        else -> terms
    }
    val hint: String? get() = when {
        tempo != null -> "Library: ${tempo.start.toInt()}–${tempo.endInclusive.toInt()} BPM. Online results are tempo candidates, not verified BPM matches."
        lyrics -> "Matching saved lyrics and searching online for this lyric."
        else -> null
    }

    companion object {
        private val range = Regex("""(?i)\b(\d{2,3}(?:\.\d+)?)\s*(?:-|–|to)\s*(\d{2,3}(?:\.\d+)?)\s*(?:bpm|bpi|beats?\s+per\s+minute)\b""")
        private val single = Regex("""(?i)\b(\d{2,3}(?:\.\d+)?)\s*(?:bpm|bpi|beats?\s+per\s+minute)\b""")
        private val prefix = Regex("""(?i)^(?:lyrics?\s*[:：]|(?:find\s+)?(?:the\s+)?song\s+(?:that\s+goes|with\s+(?:the\s+)?lyrics?)\s*[:：]?)\s*""")
        private val filler = Regex("""(?i)\b(?:find|me|some|songs?|tracks?|music|with|at|around|about|near|between|and|of|please)\b""")
        fun parse(raw: String): MusicSearchQuery {
            val text = raw.trim().replace(Regex("\\s+"), " ")
            val match = range.find(text) ?: single.find(text)
            val tempo = match?.let {
                val a = it.groupValues[1].toFloatOrNull() ?: return@let null
                val b = it.groupValues.getOrNull(2)?.toFloatOrNull()
                if (a !in 20f..400f || (b != null && b !in 20f..400f)) null
                else if (b != null) minOf(a, b)..maxOf(a, b) else maxOf(20f, a - 5)..minOf(400f, a + 5)
            }
            val explicitLyrics = prefix.containsMatchIn(text) ||
                (text.length > 2 && text.first() in "\"“" && text.last() in "\"”")
            val terms = if (tempo != null) {
                filler.replace(text.removeRange(match.range), " ").replace(Regex("\\s+"), " ").trim()
            } else prefix.replace(text, "").trim().trim('"', '“', '”')
            return MusicSearchQuery(text, terms, tempo, explicitLyrics)
        }

        /** Escape user text, so '%' and '_' never become SQL wildcards. */
        fun likePattern(text: String): String = "%" + text.replace("\\", "\\\\")
            .replace("%", "\\%").replace("_", "\\_") + "%"
    }
}
