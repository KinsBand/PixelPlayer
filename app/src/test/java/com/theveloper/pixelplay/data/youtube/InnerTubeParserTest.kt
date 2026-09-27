package com.theveloper.pixelplay.data.youtube

import com.theveloper.pixelplay.data.model.SearchResultItem
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class InnerTubeParserTest {
    private fun row(id: String = "abcdefghijk") = JSONObject("""{
      "musicResponsiveListItemRenderer": {
        "flexColumns": [
          {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"The Song","navigationEndpoint":{"watchEndpoint":{"videoId":"$id"}}}]}}},
          {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"The Artist","navigationEndpoint":{"browseEndpoint":{"browseId":"UCartist"}}},{"text":" • 3:12"}]}}}
        ],
        "menu":{"watchEndpoint":{"videoId":"wrongmenuid"}}
      }
    }""")

    @Test fun `title endpoint wins over unrelated menu video`() {
        val item = InnerTubeParser.results(row()).single() as SearchResultItem.SongItem
        assertEquals("yt_abcdefghijk", item.song.id)
        assertEquals("The Artist", item.song.artist)
        assertEquals(192_000L, item.song.duration)
    }
    @Test fun `invalid title video never falls back to menu ID`() {
        assertTrue(InnerTubeParser.results(row("bad")).isEmpty())
    }
    @Test fun `artist requires canonical music artist page type`() {
        val artist = JSONObject("""{"musicResponsiveListItemRenderer":{
          "navigationEndpoint":{"browseEndpoint":{"browseId":"UCartist","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}},
          "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Artist"}]}}}]
        }}""")
        assertEquals("UCartist", (InnerTubeParser.results(artist).single() as SearchResultItem.ArtistItem).browseId)
        artist.getJSONObject("musicResponsiveListItemRenderer").getJSONObject("navigationEndpoint")
            .getJSONObject("browseEndpoint").remove("browseEndpointContextSupportedConfigs")
        assertTrue(InnerTubeParser.results(artist).isEmpty())
    }
    @Test fun `collection ID remains distinct from local IDs`() {
        val root = JSONObject("""{"musicTwoRowItemRenderer":{
          "title":{"runs":[{"text":"Album"}]},"navigationEndpoint":{"browseEndpoint":{
          "browseId":"MPREalbum","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ALBUM"}}}}
        }}""")
        val album = InnerTubeParser.results(root).single() as SearchResultItem.AlbumItem
        assertEquals("MPREalbum", album.browseId)
        assertTrue(album.album.id < 0)
    }
    @Test fun `only usable progressive audio URLs enter direct manifest`() {
        val now = 1_000_000L
        fun manifest(url: String, mime: String = "audio/mp4", extra: String = "") = JSONObject("""{
            "streamingData":{"adaptiveFormats":[{"url":"$url","mimeType":"$mime","contentLength":"1024","bitrate":128000$extra}]}}
        """)
        val url = "https://r1.googlevideo.com/videoplayback?expire=9999"
        assertEquals(1, InnerTubeParser.directStreams(manifest(url), now).size)
        assertTrue(InnerTubeParser.directStreams(manifest("$url&n=requires_transform"), now).isEmpty())
        assertTrue(InnerTubeParser.directStreams(manifest(url, "video/mp4"), now).isEmpty())
        assertTrue(InnerTubeParser.directStreams(manifest("https://googlevideo.com.evil.example/videoplayback"), now).isEmpty())
        assertTrue(InnerTubeParser.directStreams(manifest(url, extra = ",\"type\":\"FORMAT_STREAM_TYPE_OTF\""), now).isEmpty())
        assertTrue(InnerTubeParser.directStreams(manifest("https://r1.googlevideo.com/videoplayback?expire=1"), now).isEmpty())
    }
}
