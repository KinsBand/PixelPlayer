package com.theveloper.pixelplay.data.metadata

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SongMetadataTest {
    @Test fun `catalogue covers every major layer with unique fields`() {
        val fields = SongMetadataCatalogue.fields
        assertTrue(fields.size > 300)
        assertEquals(fields.size, fields.map { it.key }.toSet().size)
        assertEquals(MetadataScope.entries.toSet(), fields.map { it.scope }.toSet())
        assertTrue(fields.all { it.sources.isNotEmpty() })
    }
    @Test fun `automatic refresh cannot replace a manual correction`() {
        val manual = MetadataClaim("D minor", "user", 1, locked = true)
        var claims = listOf(manual)
        repeat(25) { claims = mergeMetadataClaim(claims, MetadataClaim("C major $it", "local-dsp", it.toLong(), MetadataState.ESTIMATED)) }
        val doc = SongMetadataDocument(songId = "1", assetRevision = "a", claims = mapOf("harmony.key_candidates" to claims))
        assertEquals(manual, doc.selected("harmony.key_candidates"))
        assertTrue(claims.size <= 20)
    }
    @Test fun `manual unknown is distinct from fabricated zero`() {
        val claim = MetadataClaim(null, "user", 1, MetadataState.UNKNOWN, locked = true)
        assertNull(claim.value)
        assertEquals(MetadataState.UNKNOWN, claim.state)
    }
}
