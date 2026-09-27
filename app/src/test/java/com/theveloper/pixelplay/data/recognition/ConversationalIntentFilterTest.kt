package com.theveloper.pixelplay.data.recognition

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.recognition.ambient.ConversationalIntentFilter
import org.junit.Test

class ConversationalIntentFilterTest {

    @Test
    fun `extracts title and artist from can we play phrase`() {
        val result = ConversationalIntentFilter.parse("can we play Blinding Lights by The Weeknd")
        assertThat(result).isNotNull()
        assertThat(result?.cleanTitle).isEqualTo("blinding lights")
        assertThat(result?.artist).isEqualTo("the weeknd")
    }

    @Test
    fun `extracts title with trailing please suffix stripped`() {
        val result = ConversationalIntentFilter.parse("put on Bohemian Rhapsody please")
        assertThat(result).isNotNull()
        assertThat(result?.cleanTitle).isEqualTo("bohemian rhapsody")
        assertThat(result?.artist).isNull()
    }

    @Test
    fun `handles from artist delimiter`() {
        val result = ConversationalIntentFilter.parse("how about Hotel California from Eagles")
        assertThat(result).isNotNull()
        assertThat(result?.cleanTitle).isEqualTo("hotel california")
        assertThat(result?.artist).isEqualTo("eagles")
    }

    @Test
    fun `strips leading filler words like some or the song`() {
        val result = ConversationalIntentFilter.parse("play some Daft Punk")
        assertThat(result).isNotNull()
        assertThat(result?.cleanTitle).isEqualTo("daft punk")
    }

    @Test
    fun `extracts song from queue up phrase`() {
        val result = ConversationalIntentFilter.parse("queue up Espresso by Sabrina Carpenter")
        assertThat(result).isNotNull()
        assertThat(result?.cleanTitle).isEqualTo("espresso")
        assertThat(result?.artist).isEqualTo("sabrina carpenter")
    }

    @Test
    fun `extracts song from i wanna hear phrase`() {
        val result = ConversationalIntentFilter.parse("i wanna hear Starboy")
        assertThat(result).isNotNull()
        assertThat(result?.cleanTitle).isEqualTo("starboy")
    }

    @Test
    fun `rejects general ambient conversation without music trigger prefixes`() {
        val result = ConversationalIntentFilter.parse("hey what are we going to eat for dinner tonight")
        assertThat(result).isNull()
    }

    @Test
    fun `rejects short non-informative phrases`() {
        val result = ConversationalIntentFilter.parse("yes")
        assertThat(result).isNull()
    }

    @Test
    fun `rejects blank transcript`() {
        val result = ConversationalIntentFilter.parse("   ")
        assertThat(result).isNull()
    }
}
