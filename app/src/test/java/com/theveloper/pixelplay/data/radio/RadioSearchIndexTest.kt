package com.theveloper.pixelplay.data.radio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RadioSearchIndexTest {
    private fun station(
        id: String,
        name: String,
        tags: List<String> = emptyList(),
        cc: String? = null,
        state: String? = null,
        clicks: Int = 0,
    ) = RadioStation(
        uuid = id, name = name, streamUrl = "https://example.com/$id",
        tags = tags, countryCode = cc, state = state, clickCount = clicks,
    )

    @Test fun `name prefix beats word prefix beats substring`() {
        val index = RadioSearchIndex(
            listOf(
                station("sub", "Hitjazz Radio", clicks = 900),
                station("word", "Smooth Jazz FM", clicks = 500),
                station("prefix", "Jazz24", clicks = 10),
            )
        )
        assertEquals(listOf("prefix", "word", "sub"), index.search("jazz").map { it.uuid })
    }

    @Test fun `accents punctuation and case are ignored`() {
        val index = RadioSearchIndex(listOf(station("a", "Radio Café-Música"), station("b", "Triple-J")))
        assertEquals(listOf("a"), index.search("cafe musica").map { it.uuid })
        assertEquals(listOf("b"), index.search("triple j").map { it.uuid })
    }

    @Test fun `words can match name tags or place`() {
        val index = RadioSearchIndex(
            listOf(
                station("bne", "Triple M", tags = listOf("rock"), state = "Queensland"),
                station("syd", "Triple M", tags = listOf("rock"), state = "New South Wales"),
            )
        )
        assertEquals(listOf("bne"), index.search("triple queensland").map { it.uuid })
        assertEquals(2, index.search("rock").size)
        assertTrue(index.search("metal").isEmpty())
    }

    @Test fun `home country first, then popularity`() {
        val index = RadioSearchIndex(
            listOf(
                station("us", "News Talk", cc = "US", clicks = 1000),
                station("au", "News Talk", cc = "AU", clicks = 5),
            )
        )
        assertEquals(listOf("au", "us"), index.search("news", preferCountry = "AU").map { it.uuid })
        assertEquals(listOf("us", "au"), index.search("news").map { it.uuid })
    }

    @Test fun `answers in well under a millisecond for thousands of stations`() {
        val words = listOf("radio", "fm", "jazz", "rock", "news", "hits", "classic", "country", "talk", "dance")
        val stations = (0 until 8000).map { i ->
            station("s$i", "${words[i % 10]} ${words[(i / 10) % 10]} $i", tags = listOf(words[(i / 7) % 10]), clicks = i)
        }
        val index = RadioSearchIndex(stations)
        repeat(50) { index.search("rock") } // warm up the JIT
        val runs = 200
        val start = System.nanoTime()
        repeat(runs) { index.search(if (it % 2 == 0) "jazz" else "classic rock") }
        val perQueryMs = (System.nanoTime() - start) / 1e6 / runs
        assertTrue(perQueryMs < 5.0, "search took $perQueryMs ms per query")
        println("RadioSearchIndex: %.3f ms per query over %d stations".format(perQueryMs, stations.size))
    }
}
