package com.theveloper.pixelplay.presentation.settings

import com.theveloper.pixelplay.presentation.model.SettingsCategory
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the invariant that made settings search unreliable for a long time: the search
 * index and the screens that render the settings were two independent lists, maintained
 * by hand, and they drifted.
 *
 * Concretely, these had all shipped at once:
 *   - eight entries whose `categoryId` pointed at a screen that did not contain them,
 *     so tapping a search result opened the wrong category with no error;
 *   - five keys spelled differently on the two sides (`tap_closes` vs `tap_bg_closes`),
 *     so deep-link highlighting silently did nothing;
 *   - three keys with no `settingKey` on the composable at all;
 *   - one setting that was implemented but absent from the index, so it was unsearchable.
 *
 * The source scan below is deliberate rather than clever. A pure-data test can only check
 * the registry against itself; the drift lives *between* the registry and the composables,
 * so the composables have to be read. Gradle runs unit tests with the module directory as
 * the working directory, which is what [settingsSourceDir] relies on.
 */
class SettingsRegistryTest {

    private val settingsSourceDir =
        File("src/main/java/com/theveloper/pixelplay/presentation/screens/settings")

    /** `settingKey = "..."` as written at a composable call site. */
    private val settingKeyPattern = Regex("""settingKey\s*=\s*"([a-z0-9_]+)"""")

    /**
     * Maps each category content file to the [SettingsCategory] id it renders. A new
     * category file must be added here, which is intentional: it forces the author to
     * state which category the file belongs to.
     */
    private val fileToCategory = mapOf(
        "GeneralSettings.kt" to "general",
        "AppearanceSettings.kt" to "appearance",
        // Player & Lyrics: the player-look half and the lyrics half, one page ("lyrics").
        "NowPlayingSettings.kt" to "lyrics",
        "LyricsSettings.kt" to "lyrics",
        "WidgetsSettings.kt" to "widgets",
        "PlaybackSettings.kt" to "playback",
        "LibrarySettings.kt" to "library",
        "LikedDownloadsSettings.kt" to "library",
        "ServicesSettings.kt" to "services",
        "AiSettings.kt" to "ai",
        "BackupSettings.kt" to "backup_restore",
        "DeveloperSettings.kt" to "developer"
    )

    /** key -> category id, as actually rendered on screen. */
    private fun implementedKeys(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for ((fileName, categoryId) in fileToCategory) {
            val file = File(settingsSourceDir, fileName)
            assertTrue(file.isFile, "Missing settings source file: ${file.path}")
            for (match in settingKeyPattern.findAll(file.readText())) {
                val key = match.groupValues[1]
                val previous = result.put(key, categoryId)
                assertEquals(
                    null,
                    previous,
                    "settingKey \"$key\" is used in more than one category file " +
                        "($previous and $categoryId); keys must be unique"
                )
            }
        }
        return result
    }

    @Test
    fun `registry keys are unique`() {
        val duplicates = SettingsRegistry.entries
            .groupingBy { it.key }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        assertTrue(duplicates.isEmpty(), "Duplicate registry keys: $duplicates")
    }

    @Test
    fun `registry keys are non-blank and lowercase snake case`() {
        val malformed = SettingsRegistry.entries
            .map { it.key }
            .filterNot { it.matches(Regex("[a-z0-9]+(_[a-z0-9]+)*")) }
        assertTrue(malformed.isEmpty(), "Malformed registry keys: $malformed")
    }

    @Test
    fun `every categoryId resolves to a real SettingsCategory`() {
        val unknown = SettingsRegistry.entries.filter { entry ->
            SettingsCategory.fromId(entry.categoryId) == null
        }
        assertTrue(unknown.isEmpty(), "Entries with unknown categoryId: ${unknown.map { it.key }}")
    }

    @Test
    fun `byKey covers every entry`() {
        assertEquals(SettingsRegistry.entries.size, SettingsRegistry.byKey.size)
    }

    /**
     * The check that matters: every in-category entry must be rendered by the category
     * it claims. A failure here means a search result would open the wrong screen.
     */
    @Test
    fun `every in-category entry is rendered by the category it declares`() {
        val implemented = implementedKeys()
        val problems = mutableListOf<String>()

        for (entry in SettingsRegistry.entries) {
            // Entries with an explicit route are navigated to directly, so they are not
            // required to appear inside a category. Several of them legitimately do —
            // "palette_style" is a row in Appearance that opens its own screen — and
            // those are checked separately in the test below.
            if (entry.route != null) continue

            val renderedIn = implemented[entry.key]
            when {
                renderedIn == null ->
                    problems += "\"${entry.key}\" is in the registry but no composable " +
                        "passes settingKey = \"${entry.key}\" (search would open " +
                        "\"${entry.categoryId}\" and highlight nothing)"

                renderedIn != entry.categoryId ->
                    problems += "\"${entry.key}\" declares categoryId = " +
                        "\"${entry.categoryId}\" but is rendered by \"$renderedIn\" " +
                        "(search would open the wrong screen)"
            }
        }

        assertTrue(problems.isEmpty(), "Registry/screen mismatch:\n  " + problems.joinToString("\n  "))
    }

    /** A setting on screen but missing from the registry cannot be found by search. */
    @Test
    fun `every rendered setting is present in the registry`() {
        val missing = implementedKeys().keys - SettingsRegistry.byKey.keys
        assertTrue(
            missing.isEmpty(),
            "Rendered but unsearchable (add a SettingEntry for each): $missing"
        )
    }

    /**
     * A routed entry may also be rendered as a row inside a category — "palette_style"
     * is a row in Appearance whose job is to open the palette screen. When it is, the
     * declared category still has to be the one that renders it, otherwise the "Section:"
     * label in search results names a screen the setting is not on.
     */
    @Test
    fun `routed entries that also appear in a category declare that category`() {
        val implemented = implementedKeys()
        val mismatched = SettingsRegistry.entries
            .filter { it.route != null }
            .mapNotNull { entry ->
                val renderedIn = implemented[entry.key] ?: return@mapNotNull null
                if (renderedIn == entry.categoryId) {
                    null
                } else {
                    "\"${entry.key}\" declares \"${entry.categoryId}\" but is rendered by \"$renderedIn\""
                }
            }
        assertTrue(mismatched.isEmpty(), "Routed entry category mismatch: $mismatched")
    }

    /**
     * A registry entry must name the page that renders it, not a merged id that only
     * resolves to it. Otherwise the "Section:" label and the in-category check disagree.
     */
    @Test
    fun `registry uses canonical category ids`() {
        val stale = SettingsRegistry.entries.filter { entry ->
            val category = SettingsCategory.fromId(entry.categoryId)
            category != null && category.id != entry.categoryId
        }
        assertTrue(stale.isEmpty(), "Entries using a merged or legacy id: ${stale.map { it.key to it.categoryId }}")
    }

    /** Old links (saved deep links, restored back stacks) must still open a real page. */
    @Test
    fun `merged and legacy ids resolve to the page that now holds their settings`() {
        assertEquals(SettingsCategory.LYRICS, SettingsCategory.fromId("now_playing"))
        assertEquals(SettingsCategory.GENERAL, SettingsCategory.fromId("navigation"))
        assertEquals(SettingsCategory.GENERAL, SettingsCategory.fromId("player"))
        assertEquals(SettingsCategory.GENERAL, SettingsCategory.fromId("behavior"))
        assertEquals(SettingsCategory.LYRICS, SettingsCategory.fromId("lyrics"))
    }
}
