package com.theveloper.pixelplay.presentation.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Behaviour tests for settings search.
 *
 * These are written against the failures the previous `String.contains` matcher produced,
 * not against the implementation: accented and localised queries finding nothing,
 * multi-word queries behaving like one literal, and an exact title match ranking below an
 * incidental keyword hit.
 */
class SettingsSearchTest {

    private fun row(
        id: String,
        title: String,
        description: String = "",
        keywords: List<String> = emptyList(),
        category: String = "appearance"
    ) = SettingsSearch.Indexed(
        value = id,
        title = SettingsSearch.normalize(title),
        description = SettingsSearch.normalize(description),
        keywords = keywords.map { SettingsSearch.normalize(it) },
        categoryName = SettingsSearch.normalize(category)
    )

    private val rows = listOf(
        row("theme", "Theme Mode", "Light, dark or follow system", listOf("dark", "light", "theme")),
        row("language", "App Language", "Choose the display language", listOf("locale", "translation")),
        row("haptics", "Haptic feedback", "Vibration feedback across the app", listOf("vibration", "vibrate")),
        row("blur", "Disable blur", "Turn off blur effects", listOf("blur", "performance")),
        row("replaygain", "Enable ReplayGain", "Normalise volume between tracks", listOf("volume", "loudness"), "playback")
    )

    @Test
    fun `normalize folds case accents and whitespace`() {
        assertEquals("theme mode", SettingsSearch.normalize("  Themé   MODE "))
        assertEquals("dunkel", SettingsSearch.normalize("Dunkel"))
        assertEquals("asthetik", SettingsSearch.normalize("Ästhetik"))
    }

    @Test
    fun `blank query returns nothing rather than everything`() {
        assertTrue(SettingsSearch.search("", rows).isEmpty())
        assertTrue(SettingsSearch.search("   ", rows).isEmpty())
    }

    @Test
    fun `accented query matches unaccented text and vice versa`() {
        assertTrue(SettingsSearch.search("themé", rows).contains("theme"))
        assertTrue(SettingsSearch.search("THEME", rows).contains("theme"))
    }

    @Test
    fun `keyword match finds a setting whose title does not contain the term`() {
        // "vibration" appears only in the keywords of "Haptic feedback".
        assertEquals(listOf("haptics"), SettingsSearch.search("vibration", rows))
    }

    @Test
    fun `multi-word query requires every term`() {
        // Both terms are satisfied by the theme row (title + keyword).
        assertTrue(SettingsSearch.search("theme dark", rows).contains("theme"))
        // "dark" alone does not appear anywhere on the language row, so requiring all
        // terms must exclude it rather than matching on "language" alone.
        assertFalse(SettingsSearch.search("language dark", rows).contains("language"))
    }

    @Test
    fun `exact title match outranks an incidental description match`() {
        val results = SettingsSearch.search("language", rows)
        assertEquals("language", results.first())
    }

    @Test
    fun `word prefix matches mid-title words`() {
        // "mode" is the second word of "Theme Mode".
        assertTrue(SettingsSearch.search("mode", rows).contains("theme"))
    }

    @Test
    fun `unmatched query returns empty`() {
        assertTrue(SettingsSearch.search("zzzzz", rows).isEmpty())
    }

    /**
     * Every setting must be reachable by typing its own title — otherwise a user who
     * knows exactly what the setting is called still cannot find it.
     */
    @Test
    fun `every registry entry is findable by an exact word from its title`() {
        val indexed = SettingsRegistry.entries.map { entry ->
            SettingsSearch.Indexed(
                value = entry.key,
                // Resource ids cannot be resolved on the JVM, so the key itself stands in
                // for the title here. That still exercises tokenising and ranking.
                title = SettingsSearch.normalize(entry.key.replace('_', ' ')),
                description = "",
                keywords = entry.keywords.map { SettingsSearch.normalize(it) },
                categoryName = SettingsSearch.normalize(entry.categoryId)
            )
        }
        val unreachable = SettingsRegistry.entries.filter { entry ->
            val firstWord = entry.key.substringBefore('_')
            !SettingsSearch.search(firstWord, indexed).contains(entry.key)
        }
        assertTrue(unreachable.isEmpty(), "Not findable by their own name: ${unreachable.map { it.key }}")
    }
}
