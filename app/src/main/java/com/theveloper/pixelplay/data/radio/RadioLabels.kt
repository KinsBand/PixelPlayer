package com.theveloper.pixelplay.data.radio

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Short labels for the radio, so chips, subtitles and pins stay as short as "Music", "Dance"
 * or "ABC": "New South Wales" → "NSW", "community radio" → "Comm", "news talk" → "Talk".
 *
 * It also knows which spellings mean the same state. Radio Browser's `state` field is typed by
 * whoever added the station, so one state shows up as "New South Wales", "NSW", "nsw", "Sydney"
 * or "AU-NSW". [stateKey] folds all of those to one key, which is what lets Region and Local find
 * every station in an area instead of only the ones spelled the way the geocoder spells it.
 */
object RadioLabels {

    // ---- States / territories / provinces ------------------------------------------------------

    private class Region(val code: String, val names: List<String>, val cities: List<String> = emptyList())

    /** Country code → its states. Codes are the ISO 3166-2 suffixes (AU-NSW → "NSW"). */
    private val REGIONS: Map<String, List<Region>> = mapOf(
        "AU" to listOf(
            Region("NSW", listOf("new south wales", "n s w"), listOf("sydney", "newcastle", "wollongong", "central coast", "albury", "wagga wagga", "dubbo", "tamworth", "orange", "bathurst", "port macquarie", "coffs harbour", "lismore", "byron bay", "blue mountains", "penrith", "parramatta", "broken hill", "armidale", "goulburn", "nowra")),
            Region("VIC", listOf("victoria"), listOf("melbourne", "geelong", "ballarat", "bendigo", "shepparton", "mildura", "warrnambool", "traralgon", "wodonga")),
            Region("QLD", listOf("queensland"), listOf("brisbane", "gold coast", "sunshine coast", "cairns", "townsville", "toowoomba", "mackay", "rockhampton", "bundaberg", "hervey bay", "ipswich", "logan", "gladstone", "mount isa")),
            Region("WA", listOf("western australia"), listOf("perth", "fremantle", "bunbury", "geraldton", "kalgoorlie", "albany", "broome", "mandurah", "karratha", "port hedland")),
            Region("SA", listOf("south australia"), listOf("adelaide", "mount gambier", "whyalla", "port augusta", "port lincoln", "murray bridge")),
            Region("TAS", listOf("tasmania"), listOf("hobart", "launceston", "devonport", "burnie")),
            Region("NT", listOf("northern territory"), listOf("darwin", "alice springs", "katherine")),
            Region("ACT", listOf("australian capital territory"), listOf("canberra")),
        ),
        "NZ" to listOf(
            Region("AUK", listOf("auckland")), Region("WGN", listOf("wellington")), Region("CAN", listOf("canterbury"), listOf("christchurch")),
            Region("WKO", listOf("waikato"), listOf("hamilton")), Region("BOP", listOf("bay of plenty"), listOf("tauranga", "rotorua")),
            Region("OTA", listOf("otago"), listOf("dunedin", "queenstown")),
        ),
        "CA" to listOf(
            Region("ON", listOf("ontario"), listOf("toronto", "ottawa")), Region("QC", listOf("quebec", "québec"), listOf("montreal", "montréal")),
            Region("BC", listOf("british columbia"), listOf("vancouver", "victoria bc")), Region("AB", listOf("alberta"), listOf("calgary", "edmonton")),
            Region("MB", listOf("manitoba"), listOf("winnipeg")), Region("SK", listOf("saskatchewan"), listOf("regina", "saskatoon")),
            Region("NS", listOf("nova scotia"), listOf("halifax")), Region("NB", listOf("new brunswick")), Region("NL", listOf("newfoundland and labrador", "newfoundland")),
            Region("PE", listOf("prince edward island")), Region("YT", listOf("yukon")), Region("NT", listOf("northwest territories")), Region("NU", listOf("nunavut")),
        ),
        "US" to listOf(
            "AL" to "alabama", "AK" to "alaska", "AZ" to "arizona", "AR" to "arkansas", "CA" to "california", "CO" to "colorado",
            "CT" to "connecticut", "DE" to "delaware", "FL" to "florida", "GA" to "georgia", "HI" to "hawaii", "ID" to "idaho",
            "IL" to "illinois", "IN" to "indiana", "IA" to "iowa", "KS" to "kansas", "KY" to "kentucky", "LA" to "louisiana",
            "ME" to "maine", "MD" to "maryland", "MA" to "massachusetts", "MI" to "michigan", "MN" to "minnesota", "MS" to "mississippi",
            "MO" to "missouri", "MT" to "montana", "NE" to "nebraska", "NV" to "nevada", "NH" to "new hampshire", "NJ" to "new jersey",
            "NM" to "new mexico", "NY" to "new york", "NC" to "north carolina", "ND" to "north dakota", "OH" to "ohio", "OK" to "oklahoma",
            "OR" to "oregon", "PA" to "pennsylvania", "RI" to "rhode island", "SC" to "south carolina", "SD" to "south dakota",
            "TN" to "tennessee", "TX" to "texas", "UT" to "utah", "VT" to "vermont", "VA" to "virginia", "WA" to "washington",
            "WV" to "west virginia", "WI" to "wisconsin", "WY" to "wyoming", "DC" to "district of columbia",
        ).map { (code, name) -> Region(code, listOf(name)) },
    )

    /** "new south wales" / "nsw" / "sydney" → "NSW", per country. Built once. */
    private val LOOKUP: Map<String, Map<String, String>> = REGIONS.mapValues { (_, regions) ->
        buildMap {
            regions.forEach { r ->
                put(fold(r.code), r.code)
                r.names.forEach { put(fold(it), r.code) }
                r.cities.forEach { put(fold(it), r.code) }
            }
        }
    }

    /** Every country we know states for, so a station without a country code can still match. */
    private val ANY_COUNTRY: Map<String, String> = buildMap {
        // Full names only: "WA" or "CA" alone mean different things in different countries.
        REGIONS.forEach { (_, regions) -> regions.forEach { r -> r.names.forEach { putIfAbsent(fold(it), r.code) } } }
    }

    /**
     * One key per state, whatever the spelling: "AU:NSW" for "New South Wales", "NSW", "nsw",
     * "Sydney" or ISO "AU-NSW". Unknown names fold to "CC:new south wales"-style keys, so two
     * stations with the same unknown name still match each other.
     */
    fun stateKey(state: String?, countryCode: String?, iso3166_2: String? = null): String? {
        val cc = countryCode?.uppercase(Locale.ROOT)?.takeIf { it.length == 2 }
        iso3166_2?.takeIf { it.length in 4..6 && it[2] == '-' }?.let { iso ->
            val isoCc = iso.substring(0, 2).uppercase(Locale.ROOT)
            if (cc == null || cc == isoCc) return "$isoCc:${iso.substring(3).uppercase(Locale.ROOT)}"
        }
        val raw = state?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val folded = fold(raw)
        if (folded.isEmpty()) return null
        // "AU-NSW" typed into the state field.
        if (folded.length in 4..6 && folded[2] == ' ' && cc != null && folded.startsWith(cc.lowercase(Locale.ROOT))) {
            return stateKey(null, cc, raw.uppercase(Locale.ROOT).replace(' ', '-'))
        }
        val table = cc?.let { LOOKUP[it] }
        val code = table?.get(folded)
            // "Sydney, NSW" / "Newcastle New South Wales": any known part decides.
            ?: table?.let { t -> splitParts(folded).firstNotNullOfOrNull { t[it] } }
            ?: if (cc == null) ANY_COUNTRY[folded] else null
        return if (code != null) "${cc ?: "??"}:$code" else "${cc ?: "??"}:$folded"
    }

    /** True when [station] is in the state of [home] (any spelling, or by its ISO code). */
    fun inSameState(station: RadioStation, home: RadioHome): Boolean {
        val homeKey = stateKey(home.state, home.countryCode) ?: return false
        if (station.countryCode != null && home.countryCode != null &&
            !station.countryCode.equals(home.countryCode, ignoreCase = true)
        ) return false
        val stationKey = stateKey(station.state, station.countryCode ?: home.countryCode, station.subdivisionCode)
        return stationKey != null && stationKey.substringAfter(':') == homeKey.substringAfter(':')
    }

    /** "AU:NSW" (a [stateKey]) → "New South Wales"; null when the state isn't one we know. */
    fun stateName(key: String): String? {
        val cc = key.substringBefore(':', "")
        val code = key.substringAfter(':', "")
        val region = REGIONS[cc]?.firstOrNull { it.code == code } ?: return null
        return region.names.first().split(' ').joinToString(" ") { w ->
            if (w == "of" || w == "and") w else w.replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }

    /** "New South Wales" → "NSW", "California" → "CA". Unknown names come back unchanged. */
    fun shortState(state: String?, countryCode: String? = null): String? {
        val raw = state?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val key = stateKey(raw, countryCode) ?: return raw
        val code = key.substringAfter(':')
        // Only use the code when we really recognised the state (not the folded fallback).
        return if (code.length <= 4 && code.all { it.isUpperCase() || it.isDigit() }) code else raw
    }

    // ---- Genre tags ---------------------------------------------------------------------------

    /** Folded tag → short label. Longest phrases are tried first. */
    private val TAGS: List<Pair<String, String>> = listOf(
        "adult contemporary" to "AC", "hot adult contemporary" to "AC", "contemporary hit" to "Hits", "top 40" to "Hits", "top40" to "Hits",
        "classic hits" to "Oldies", "golden oldies" to "Oldies", "oldies" to "Oldies", "60s" to "60s", "70s" to "70s", "80s" to "80s", "90s" to "90s", "00s" to "00s",
        "community radio" to "Comm", "community" to "Comm", "local news" to "Local", "local" to "Local",
        "news talk" to "Talk", "talk radio" to "Talk", "talk" to "Talk", "news" to "News", "current affairs" to "News",
        "sports" to "Sport", "sport" to "Sport", "racing" to "Sport", "football" to "Sport",
        "christian" to "Faith", "religious" to "Faith", "religion" to "Faith", "gospel" to "Faith", "catholic" to "Faith", "islamic" to "Faith", "worship" to "Faith",
        "classical" to "Class", "orchestral" to "Class", "opera" to "Opera",
        "classic rock" to "Rock", "hard rock" to "Rock", "rock" to "Rock", "heavy metal" to "Metal", "metal" to "Metal", "punk" to "Punk",
        "alternative" to "Alt", "alt rock" to "Alt", "indie" to "Indie",
        "electronic" to "Elec", "electronica" to "Elec", "edm" to "EDM", "techno" to "Techno", "house" to "House", "trance" to "Trance",
        "drum and bass" to "DnB", "dnb" to "DnB", "dance" to "Dance", "disco" to "Disco",
        "hip hop" to "HipHop", "hiphop" to "HipHop", "rap" to "Rap", "r b" to "R&B", "rnb" to "R&B", "soul" to "Soul", "funk" to "Funk",
        "country" to "Ctry", "folk" to "Folk", "bluegrass" to "Folk", "americana" to "Folk",
        "jazz" to "Jazz", "smooth jazz" to "Jazz", "blues" to "Blues", "reggae" to "Reggae", "latin" to "Latin",
        "easy listening" to "Easy", "chillout" to "Chill", "chill" to "Chill", "lounge" to "Chill", "ambient" to "Chill", "relax" to "Chill",
        "university" to "Uni", "college" to "Uni", "student" to "Uni", "campus" to "Uni",
        "aboriginal" to "Indig", "indigenous" to "Indig", "first nations" to "Indig",
        "multicultural" to "World", "ethnic" to "World", "world music" to "World", "world" to "World",
        "children" to "Kids", "kids" to "Kids", "variety" to "Mix", "eclectic" to "Mix", "public radio" to "Public", "public" to "Public",
        "pop" to "Pop", "hits" to "Hits", "music" to "Music",
        "abc" to "ABC", "sbs" to "SBS", "bbc" to "BBC", "npr" to "NPR", "triple j" to "JJJ",
    ).sortedByDescending { it.first.length }

    /** Words that say nothing about the genre. */
    private val FILLER = setOf("radio", "station", "fm", "am", "online", "internet", "webradio", "web", "stream", "live", "the", "and", "channel")

    private val tagCache = ConcurrentHashMap<String, String>()

    /** "community radio" → "Comm", "News/Talk" → "Talk", "Classic Rock" → "Rock". At most 6 characters. */
    fun shortTag(tag: String): String = tagCache.getOrPut(tag) { computeShortTag(tag) }

    private fun computeShortTag(tag: String): String {
        val folded = fold(tag)
        if (folded.isEmpty()) return tag.take(6)
        val padded = " $folded "
        TAGS.firstOrNull { (k, _) -> padded.contains(" $k ") }?.let { return it.second }
        val words = folded.split(' ').filter { it.isNotEmpty() && it !in FILLER }
        if (words.isEmpty()) return "Radio"
        if (words.size == 1) {
            val w = words.first()
            return if (w.length <= 6) w.replaceFirstChar { it.titlecase(Locale.ROOT) } else w.take(5).replaceFirstChar { it.titlecase(Locale.ROOT) } + "."
        }
        // "Adult album alternative" → "AAA".
        return words.take(4).joinToString("") { it.first().uppercase(Locale.ROOT) }
    }

    // ---- Text helpers -------------------------------------------------------------------------

    /** Lower case, accents off, punctuation as single spaces. */
    internal fun fold(text: String): String = RadioSearchIndex.fold(text)

    /** "sydney nsw" → ["sydney nsw", "sydney", "nsw"]; two-word pieces too ("new south wales" stays whole). */
    private fun splitParts(folded: String): List<String> {
        val words = folded.split(' ')
        if (words.size <= 1) return emptyList()
        val out = ArrayList<String>()
        for (len in minOf(4, words.size) downTo 1) {
            for (start in 0..words.size - len) out += words.subList(start, start + len).joinToString(" ")
        }
        return out
    }
}
