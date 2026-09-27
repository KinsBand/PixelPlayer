package com.theveloper.pixelplay.presentation.settings

import java.text.Normalizer
import java.util.Locale

/**
 * Matching and ranking for settings search.
 *
 * Deliberately pure: no Compose, no Android, no resources. The rows come in already
 * resolved to display strings, which keeps this unit-testable and means the expensive
 * part (normalising every row) can be hoisted and cached by the caller.
 *
 * The previous implementation was a single `String.contains` over title, description and
 * keywords. That failed in three ways users actually hit:
 *   - "dunkel" or "themé" matched nothing, because accents and locale were not folded;
 *   - "dark mode" matched nothing, because the query was treated as one literal string
 *     rather than as terms that may appear in different fields;
 *   - results came back in registry order, so an exact title match could sit below an
 *     incidental keyword match.
 */
object SettingsSearch {

    /** A row with its searchable text pre-normalised. Build once, reuse per keystroke. */
    data class Indexed<T>(
        val value: T,
        val title: String,
        val description: String,
        val keywords: List<String>,
        val categoryName: String
    )

    private val DIACRITICS = Regex("\\p{Mn}+")

    /**
     * Lowercase, strip accents, collapse whitespace.
     *
     * "Themé  Mode" and "theme mode" normalise to the same thing, which is what makes
     * search work for locales whose translations carry diacritics.
     */
    fun normalize(input: String): String {
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        return DIACRITICS.replace(decomposed, "")
            .lowercase(Locale.ROOT)
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    /** Splits a query into terms. Every term must match something for the row to match. */
    fun terms(query: String): List<String> =
        normalize(query).split(' ').filter { it.isNotEmpty() }

    /**
     * Score for one term against one row, or 0 when it does not match.
     *
     * The ordering that matters: a title hit always beats a keyword hit, which always
     * beats a description hit, and a prefix hit beats a mid-word hit. Without this,
     * searching "theme" could surface a setting that merely mentions themes in its
     * subtitle above the setting actually called "Theme Mode".
     */
    private fun scoreTerm(term: String, row: Indexed<*>): Int = when {
        row.title == term -> 1000
        row.title.startsWith(term) -> 500
        wordPrefix(row.title, term) -> 300
        row.keywords.any { it == term } -> 200
        row.keywords.any { it.startsWith(term) } -> 120
        row.title.contains(term) -> 80
        row.categoryName.contains(term) -> 50
        row.description.contains(term) -> 30
        row.keywords.any { it.contains(term) } -> 20
        else -> 0
    }

    /** True when [term] starts any word in [text], so "mode" hits "Theme Mode". */
    private fun wordPrefix(text: String, term: String): Boolean =
        text.split(' ').any { it.startsWith(term) }

    /**
     * Rows matching every term in [query], best first.
     *
     * Requiring all terms is what makes multi-word queries useful: "dark mode" narrows,
     * rather than returning everything that mentions either word.
     */
    fun <T> search(query: String, rows: List<Indexed<T>>): List<T> {
        val terms = terms(query)
        if (terms.isEmpty()) return emptyList()

        return rows
            .mapNotNull { row ->
                var total = 0
                for (term in terms) {
                    val score = scoreTerm(term, row)
                    if (score == 0) return@mapNotNull null
                    total += score
                }
                row to total
            }
            // Stable secondary sort on title keeps equal-scoring results in a predictable
            // order instead of shuffling as the registry is edited.
            .sortedWith(compareByDescending<Pair<Indexed<T>, Int>> { it.second }.thenBy { it.first.title })
            .map { it.first.value }
    }
}
