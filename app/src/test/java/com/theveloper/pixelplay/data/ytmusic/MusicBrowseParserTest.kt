package com.theveloper.pixelplay.data.ytmusic

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MusicBrowseParserTest {
    @Test fun `reads track metadata from renderer and ignores menu video ids`() {
        val page = JSONObject("""{"contents":[{"musicResponsiveListItemRenderer":{
          "playlistItemData":{"videoId":"realVideo"},
          "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"A real title"}]}}},
          {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"An artist","navigationEndpoint":{"browseEndpoint":{"browseId":"UC123"}}}]}}},
          {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"An album"}]}}}],
          "fixedColumns":[{"musicResponsiveListItemFixedColumnRenderer":{"text":{"runs":[{"text":"3:42"}]}}}],
          "menu":{"watchEndpoint":{"videoId":"unrelated"}}
        }}]}""")
        val track = MusicBrowseParser.tracks(page).single()
        assertEquals("realVideo", track.videoId)
        assertEquals("A real title", track.title)
        assertEquals("An artist", track.artistName)
        assertEquals("An album", track.albumName)
        assertEquals(222000, track.durationMs)
    }
    @Test fun `unavailable rows are retained without invented duration`() {
        val tracks = MusicBrowseParser.tracks(JSONObject("""{"musicResponsiveListItemRenderer":{"flexColumns":[]}}"""))
        assertTrue(tracks.single().videoId.startsWith("unavailable:"))
        assertEquals(0, tracks.single().durationMs)
        assertEquals("", tracks.single().toSong().contentUriString)
    }
    @Test fun `reads both continuation response shapes`() {
        assertEquals("next1", MusicBrowseParser.continuation(JSONObject("""{"continuations":[{"nextContinuationData":{"continuation":"next1"}}]}""")))
        assertEquals("next2", MusicBrowseParser.continuation(JSONObject("""{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"next2"}}}}""")))
    }
    @Test fun `playlist title comes from its renderer`() {
        val playlist = MusicBrowseParser.playlists(JSONObject("""{"items":[{"musicTwoRowItemRenderer":{"title":{"runs":[{"text":"My favourites"}]},"navigationEndpoint":{"browseEndpoint":{"browseId":"VLPLabc"}}}}]}""")).single()
        assertEquals("PLabc", playlist.id)
        assertEquals("My favourites", playlist.title)
    }
    @Test fun `playlist scope excludes suggestions and their continuation`() {
        val page = JSONObject("""{"contents":[
          {"musicPlaylistShelfRenderer":{"contents":[{"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"member"}}},{"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"members-next"}}}}]}},
          {"musicShelfRenderer":{"contents":[{"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"suggestion"}}}],"continuations":[{"nextContinuationData":{"continuation":"suggestions-next"}}]}}
        ]}""")
        assertEquals(listOf("member"), MusicBrowseParser.tracks(page).map { it.videoId })
        assertEquals("members-next", MusicBrowseParser.continuation(page))
    }
}
