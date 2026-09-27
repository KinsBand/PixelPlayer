package com.theveloper.pixelplay.data

import java.util.Locale

/** Shared metadata vocabulary. A genre tag is not an acoustic measurement. */
internal object GenreTaxonomy {
    data class Genre(val id: String, val label: String, val family: String, val aliases: List<String>)
    val genres = listOf(
        Genre("rock", "Rock", "rock", listOf("rock", "classic rock")),
        Genre("pop", "Pop", "pop", listOf("pop", "popular")),
        Genre("hip_hop", "Hip Hop", "hip_hop", listOf("hip hop", "hiphop", "rap")),
        Genre("jazz", "Jazz", "jazz", listOf("jazz")),
        Genre("classical", "Classical", "classical", listOf("classical", "orchestral")),
        Genre("electronic", "Electronic", "electronic", listOf("electronic", "electronica", "edm")),
        Genre("alternative_rock", "Alternative Rock", "rock", listOf("alternative rock", "alt rock", "alternative")),
        Genre("indie_rock", "Indie Rock", "rock", listOf("indie rock", "indie")),
        Genre("hard_rock", "Hard Rock", "rock", listOf("hard rock")),
        Genre("punk", "Punk", "rock", listOf("punk", "punk rock")),
        Genre("pop_punk", "Pop Punk", "rock", listOf("pop punk")),
        Genre("metal", "Metal", "metal", listOf("metal", "heavy metal")),
        Genre("metalcore", "Metalcore", "metal", listOf("metalcore")),
        Genre("death_metal", "Death Metal", "metal", listOf("death metal")),
        Genre("progressive_rock", "Progressive Rock", "rock", listOf("progressive rock", "prog rock")),
        Genre("grunge", "Grunge", "rock", listOf("grunge")),
        Genre("shoegaze", "Shoegaze", "ambient", listOf("shoegaze")),
        Genre("post_rock", "Post Rock", "ambient", listOf("post rock")),
        Genre("indie_pop", "Indie Pop", "pop", listOf("indie pop")),
        Genre("synth_pop", "Synth Pop", "electronic", listOf("synth pop", "synthpop")),
        Genre("dance_pop", "Dance Pop", "electronic", listOf("dance pop")),
        Genre("k_pop", "K-Pop", "pop", listOf("k pop", "kpop")),
        Genre("j_pop", "J-Pop", "pop", listOf("j pop", "jpop")),
        Genre("latin_pop", "Latin Pop", "latin", listOf("latin pop")),
        Genre("rnb", "R&B", "soul", listOf("randb", "rnb", "r and b", "rhythm and blues")),
        Genre("soul", "Soul", "soul", listOf("soul")),
        Genre("neo_soul", "Neo Soul", "soul", listOf("neo soul", "neosoul")),
        Genre("funk", "Funk", "soul", listOf("funk")),
        Genre("disco", "Disco", "electronic", listOf("disco")),
        Genre("trap", "Trap", "hip_hop", listOf("trap")),
        Genre("drill", "Drill", "hip_hop", listOf("drill", "uk drill")),
        Genre("boom_bap", "Boom Bap", "hip_hop", listOf("boom bap")),
        Genre("lofi", "Lo-Fi", "ambient", listOf("lo fi", "lofi", "lo fi hip hop", "chillhop")),
        Genre("house", "House", "electronic", listOf("house")),
        Genre("deep_house", "Deep House", "electronic", listOf("deep house")),
        Genre("tech_house", "Tech House", "electronic", listOf("tech house")),
        Genre("techno", "Techno", "electronic", listOf("techno")),
        Genre("trance", "Trance", "electronic", listOf("trance")),
        Genre("drum_and_bass", "Drum & Bass", "electronic", listOf("drum and bass", "dnb", "d n b", "jungle")),
        Genre("dubstep", "Dubstep", "electronic", listOf("dubstep")),
        Genre("garage", "UK Garage", "electronic", listOf("uk garage", "garage", "ukg")),
        Genre("ambient", "Ambient", "ambient", listOf("ambient")),
        Genre("downtempo", "Downtempo", "ambient", listOf("downtempo", "trip hop")),
        Genre("chillout", "Chillout", "ambient", listOf("chillout", "chill out")),
        Genre("synthwave", "Synthwave", "electronic", listOf("synthwave", "retrowave")),
        Genre("acoustic", "Acoustic", "folk", listOf("acoustic")),
        Genre("folk", "Folk", "folk", listOf("folk", "singer songwriter")),
        Genre("country", "Country", "folk", listOf("country")),
        Genre("bluegrass", "Bluegrass", "folk", listOf("bluegrass")),
        Genre("blues", "Blues", "blues", listOf("blues")),
        Genre("reggae", "Reggae", "reggae", listOf("reggae")),
        Genre("dub", "Dub", "reggae", listOf("dub")),
        Genre("dancehall", "Dancehall", "reggae", listOf("dancehall")),
        Genre("reggaeton", "Reggaeton", "latin", listOf("reggaeton")),
        Genre("salsa", "Salsa", "latin", listOf("salsa")),
        Genre("bossa_nova", "Bossa Nova", "jazz", listOf("bossa nova")),
        Genre("afrobeats", "Afrobeats", "african", listOf("afrobeats", "afrobeat")),
        Genre("amapiano", "Amapiano", "african", listOf("amapiano")),
        Genre("gospel", "Gospel", "soul", listOf("gospel")),
        Genre("soundtrack", "Soundtrack", "classical", listOf("soundtrack", "film score", "cinematic")),
        Genre("opera", "Opera", "classical", listOf("opera")),
        Genre("piano", "Piano", "classical", listOf("piano", "solo piano")),
        Genre("world", "World", "world", listOf("world", "world music")),
        Genre("bollywood", "Bollywood", "world", listOf("bollywood", "filmi")),
        Genre("celtic", "Celtic", "folk", listOf("celtic")),
        // Catalogue genres. Streamed songs now get their genre from Deezer (album genre) and
        // iTunes (primaryGenreName), which use broad store labels such as "Dance", "Electro",
        // "Latin", "R&B/Soul", "Films/Games" or "Fitness & Workout". Without these they matched
        // nothing, so those songs had no genre signal at all in mixes.
        Genre("dance", "Dance", "electronic", listOf("dance", "electro", "fitness and workout", "club")),
        Genre("latin", "Latin", "latin", listOf("latin", "urbano latino", "latin urban", "regional mexicano",
            "musica mexicana", "música mexicana", "brazilian music", "brazilian", "mpb", "sertanejo", "cumbia", "bachata")),
        Genre("new_age", "New Age", "ambient", listOf("new age", "easy listening", "relaxation", "meditation")),
        Genre("african", "African", "african", listOf("african music", "afropop", "afro pop")),
        // Compound store labels and common tags that used to fall through (or land in the
        // wrong family through a partial match) — see GenreFamilies.
        Genre("pop_rock", "Pop Rock", "rock", listOf("pop rock", "pop and rock", "rock and roll", "rock n roll",
            "rockabilly", "emo", "post punk", "post hardcore", "screamo", "new wave", "psychedelic", "psych",
            "stoner", "visual kei", "surf")),
        Genre("hip_hop_rap", "Hip Hop", "hip_hop", listOf("hip hop rap", "rap hip hop", "hip hop and rap",
            "phonk", "grime", "gangsta", "horrorcore")),
        Genre("rnb_soul", "R&B", "soul", listOf("r and b soul", "soul and r and b", "rnb soul", "doo wop", "motown",
            "quiet storm", "slow jam")),
        Genre("electronic_extra", "Electronic", "electronic", listOf("dance electronic", "electronic dance",
            "electropop", "electro pop", "hardstyle", "breakbeat", "idm", "glitch", "chillwave", "vaporwave",
            "future bass", "bass music", "industrial", "eurodance", "euro house", "rave", "jersey club", "big room")),
        Genre("pop_extra", "Pop", "pop", listOf("hyperpop", "britpop", "c pop", "mandopop", "cantopop", "schlager",
            "oldies", "top 40", "bedroom pop", "art pop", "dream pop", "vocaloid")),
        Genre("metal_extra", "Metal", "metal", listOf("deathcore", "djent", "doom", "sludge", "grindcore", "nu metal")),
        Genre("folk_extra", "Folk", "folk", listOf("indie folk", "singer songwriter", "singer and songwriter",
            "chanson", "folklore", "alt country", "outlaw country")),
        Genre("jazz_extra", "Jazz", "jazz", listOf("fusion", "swing", "bebop", "ragtime", "acid jazz", "nu jazz")),
        Genre("classical_extra", "Classical", "classical", listOf("chamber music", "symphony", "sonata", "choral",
            "baroque", "neoclassical", "modern classical", "contemporary classical", "musical", "musicals",
            "show tunes", "showtunes", "broadway", "video game", "game")),
        Genre("latin_extra", "Latin", "latin", listOf("funk carioca", "baile funk", "brazilian funk", "funk brasileiro",
            "trap latino", "latin trap", "corridos", "corridos tumbados", "dembow", "tango", "samba", "flamenco",
            "pop latino", "urbano")),
        Genre("reggae_extra", "Reggae", "reggae", listOf("ska", "dance hall", "roots reggae")),
        Genre("african_extra", "African", "african", listOf("kizomba", "highlife", "bongo flava", "gqom", "afro house")),
        Genre("world_extra", "World", "world", listOf("punjabi", "bhangra", "arabic", "rai", "fado", "k indie")),
        Genre("gospel_extra", "Gospel", "soul", listOf("worship", "christian and gospel", "praise")),
        Genre("ambient_extra", "Ambient", "ambient", listOf("trip hop", "sleep", "space", "dream")),
    )

    /**
     * Extra aliases for existing genres, in the catalogue's own wording (Deezer / iTunes).
     * Kept separate so the table above stays readable.
     */
    private val catalogueAliases = mapOf(
        "latin_pop" to listOf("pop latino"),
        "soundtrack" to listOf("films games", "film games", "video game music", "musicals"),
        "j_pop" to listOf("anime"),
        "gospel" to listOf("christian", "christian and gospel", "inspirational"),
        "country" to listOf("americana"),
        "jazz" to listOf("big band", "vocal jazz"),
        "bollywood" to listOf("indian music", "indian"),
        "world" to listOf("worldwide", "asian music"),
        "folk" to listOf("singer and songwriter"),
    )
    // Precompiled once. These used to be built on every call, and match() runs inside
    // mix ranking loops (song x seed), so each ranking allocated thousands of Regex
    // objects and strings — a main cause of GC churn, jank and memory pressure.
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val SPACES = Regex("\\s+")

    private fun normalize(value: String) = value.lowercase(Locale.ROOT).replace("&", " and ")
        .replace(NON_WORD, " ").trim().replace(SPACES, " ")

    /** Aliases, most specific first, pre-padded so matching needs no per-call string building. */
    private val aliases: List<Pair<String, Genre>> = genres.flatMap { genre ->
        (genre.aliases + catalogueAliases[genre.id].orEmpty()).map { normalize(it) to genre }
    }
        .sortedWith(compareByDescending<Pair<String, Genre>> { it.first.count { char -> char == ' ' } }
            .thenByDescending { it.first.length })
        .map { (alias, genre) -> " $alias " to genre }

    /**
     * Results per raw tag. A library has at most a few hundred distinct genre tags, so
     * this stays small; it is cleared if it ever grows past [MAX_MEMO] (garbage tags).
     */
    private val memo = java.util.concurrent.ConcurrentHashMap<String, Any>()
    private val NO_MATCH = Any()
    private const val MAX_MEMO = 4_096

    /** Alias (unpadded) -> genre, for whole-tag lookups. */
    private val exactAliases: Map<String, Genre> = HashMap<String, Genre>().also { map ->
        aliases.forEach { (padded, genre) -> map.putIfAbsent(padded.trim(), genre) }
    }

    /** Single-word aliases of 3+ letters, longest first, for the suffix fallback ("electropop"). */
    private val suffixAliases: List<Pair<String, Genre>> = aliases
        .map { (padded, genre) -> padded.trim() to genre }
        .filter { (alias, _) -> ' ' !in alias && alias.length >= 3 }
        .sortedByDescending { it.first.length }

    /** The genre whose alias is exactly this tag ("Hip-Hop/Rap", "Pop/Rock"), or null. */
    fun matchExact(tag: String?): Genre? {
        if (tag.isNullOrBlank()) return null
        return exactAliases[normalize(tag)]
    }

    /**
     * Genre of a raw tag.
     * 1. The whole tag is an alias ("indie folk", "pop rock").
     * 2. Otherwise the alias with the most words wins; between aliases with the same number of
     *    words, the one furthest right (the head of an English genre compound: "Indie Folk" is
     *    folk, "Christian Rock" is rock, "Pop Rap" is hip hop).
     * 3. Otherwise a single-word alias the tag ends with ("electropop", "britpop").
     */
    fun match(tag: String?): Genre? {
        if (tag.isNullOrBlank()) return null
        memo[tag]?.let { return if (it === NO_MATCH) null else it as Genre }
        val normalized = normalize(tag)
        val result = if (normalized.isBlank()) {
            null
        } else {
            exactAliases[normalized] ?: run {
                val padded = " $normalized "
                var best: Genre? = null
                var bestWords = -1
                var bestEnd = -1
                var bestLength = -1
                for ((alias, genre) in aliases) {
                    val index = padded.lastIndexOf(alias)
                    if (index < 0) continue
                    val words = alias.count { it == ' ' }
                    val end = index + alias.length
                    val better = words > bestWords ||
                        (words == bestWords && (end > bestEnd || (end == bestEnd && alias.length > bestLength)))
                    if (better) {
                        best = genre; bestWords = words; bestEnd = end; bestLength = alias.length
                    }
                }
                best ?: if (' ' !in normalized) {
                    suffixAliases.firstOrNull { (alias, _) -> normalized.length > alias.length && normalized.endsWith(alias) }?.second
                } else null
            }
        }
        if (memo.size >= MAX_MEMO) memo.clear()
        memo[tag] = result ?: NO_MATCH
        return result
    }

    fun affinity(first: String?, second: String?): Int {
        val a = match(first) ?: return 0
        val b = match(second) ?: return 0
        return if (a.id == b.id) 4 else if (a.family == b.family) 2 else 0
    }
}
