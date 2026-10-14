package com.theveloper.pixelplay.data.radio

/**
 * Simple top-level radio filters. Every station's tags fold into one or more of these;
 * holding a filter shows its sections found in the area (Music → Rock, 80s, Dance…).
 */
enum class RadioCategory(val label: String) {
    MUSIC("Music"),
    NEWS_TALK("News & talk"),
    SPORT("Sport"),
    COMMUNITY("Community"),
    FAITH("Faith"),
    CULTURE("Languages & culture"),
    OTHER("Other"),
}

/** A filter with how many stations it covers and its sections (short tag → count), biggest first. */
data class RadioCategoryGroup(
    val category: RadioCategory,
    val count: Int,
    val sections: List<Pair<String, Int>>,
)

object RadioCategories {
    private val byShortTag: Map<String, RadioCategory> = buildMap {
        listOf(
            "AC", "Hits", "Oldies", "60s", "70s", "80s", "90s", "00s", "Class", "Opera", "Rock", "Metal", "Punk",
            "Alt", "Indie", "Elec", "EDM", "Techno", "House", "Trance", "DnB", "Dance", "Disco", "HipHop", "Rap",
            "R&B", "Soul", "Funk", "Ctry", "Folk", "Jazz", "Blues", "Reggae", "Latin", "Easy", "Chill", "Pop",
            "Mix", "JJJ", "Music",
        ).forEach { put(it, RadioCategory.MUSIC) }
        listOf("Talk", "News", "Public", "ABC", "SBS", "BBC", "NPR").forEach { put(it, RadioCategory.NEWS_TALK) }
        put("Sport", RadioCategory.SPORT)
        listOf("Comm", "Uni", "Local").forEach { put(it, RadioCategory.COMMUNITY) }
        put("Faith", RadioCategory.FAITH)
        listOf("World", "Indig").forEach { put(it, RadioCategory.CULTURE) }
    }

    /** Short tags that say nothing on their own (never shown as a section). */
    private val vague = setOf("Radio", "Music")

    private val sectionNames = mapOf(
        "AC" to "Adult contemporary", "Hits" to "Hits", "Oldies" to "Classic hits", "Class" to "Classical",
        "Alt" to "Alternative", "Elec" to "Electronic", "EDM" to "EDM", "DnB" to "Drum & bass", "HipHop" to "Hip-hop",
        "Ctry" to "Country", "Easy" to "Easy listening", "Chill" to "Chill", "Mix" to "Variety", "JJJ" to "triple j",
        "Talk" to "Talk", "News" to "News", "Public" to "Public radio", "Comm" to "Community", "Uni" to "Student",
        "Local" to "Local", "Faith" to "Faith", "World" to "Multicultural", "Indig" to "First Nations", "Kids" to "Kids",
    )

    /** "Ctry" → "Country". */
    fun sectionLabel(shortTag: String): String = sectionNames[shortTag] ?: shortTag

    fun categoryOfTag(shortTag: String): RadioCategory = byShortTag[shortTag] ?: RadioCategory.OTHER

    /** Every filter [station] belongs to (OTHER when its tags fit none). */
    fun categoriesOf(station: RadioStation): Set<RadioCategory> {
        val known = station.tags.mapNotNullTo(HashSet()) { byShortTag[RadioLabels.shortTag(it)] }
        return known.ifEmpty { setOf(RadioCategory.OTHER) }
    }

    /** The filters present in [stations], biggest first, each with its sections. */
    fun groups(stations: List<RadioStation>): List<RadioCategoryGroup> {
        val counts = HashMap<RadioCategory, Int>()
        val sections = HashMap<RadioCategory, HashMap<String, Int>>()
        stations.forEach { station ->
            categoriesOf(station).forEach { counts[it] = (counts[it] ?: 0) + 1 }
            station.tags.map(RadioLabels::shortTag).distinct()
                .filter { it !in vague }
                .forEach { short ->
                    val cat = categoryOfTag(short)
                    val map = sections.getOrPut(cat) { HashMap() }
                    map[short] = (map[short] ?: 0) + 1
                }
        }
        return counts.entries
            .sortedWith(compareBy<Map.Entry<RadioCategory, Int>> { it.key == RadioCategory.OTHER }.thenByDescending { it.value })
            .map { (cat, count) ->
                RadioCategoryGroup(
                    category = cat,
                    count = count,
                    sections = sections[cat].orEmpty().entries
                        .sortedByDescending { it.value }
                        .take(20)
                        .map { it.key to it.value }
                )
            }
    }

    /** Whether [station] passes the filter [category] (and [section], a short tag, if set). */
    fun matches(station: RadioStation, category: RadioCategory?, section: String?): Boolean {
        if (category == null) return true
        if (category !in categoriesOf(station)) return false
        if (section == null) return true
        return station.tags.any { RadioLabels.shortTag(it) == section }
    }
}
