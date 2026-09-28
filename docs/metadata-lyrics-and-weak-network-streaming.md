# Hybrid metadata, Lyricsfile and weak-network streaming

Three changes, each with the reason behind it and where it lives.

## 1. Hybrid song metadata (online songs)

Spotube's metadata plugins each read from one source (MusicBrainz + ListenBrainz, Spotify,
JioSaavn, and so on). PixelPlayer asks several free catalogues at once and merges what they
say field by field.

| Source | Asked for | Gives |
|---|---|---|
| Deezer | every lookup | ISRC, BPM, label, UPC, album, genre, track/disc numbers, 1000 px cover |
| iTunes Search | every lookup | genre, track/disc numbers, release date, explicit flag, cover up to 3000 px |
| MusicBrainz | *deep* lookups only | exact recording (via Deezer's ISRC), original release date, community genres and tags, composer, lyricist, MusicBrainz ids |
| Last.fm | when the user has a key | tags (genre fallback, mood) |

**Deep lookups** (`SongMetadataGatherer.gatherDeep`) run when a song's info sheet is opened,
when it is downloaded, and when it is liked or saved. MusicBrainz allows one request per
second, so playlists and mixes keep using the fast Deezer + iTunes lookup. A deep lookup
publishes the Deezer + iTunes result as soon as it has it, so a caller that can't wait for
MusicBrainz still gets something.

**Anchoring by ISRC.** MusicBrainz is asked by the ISRC Deezer returned
(`ws/2/isrc/<isrc>`), which names the exact recording. The recording must still match the
song's title and length, because YouTube uploads sometimes carry wrong ISRCs. Without an
ISRC, the existing scored MusicBrainz search is used.

**Consensus (`MetadataConsensus`, "consensus-v1").** Each source's value counts as a vote,
weighted by how reliable that source is for the field. For example, MusicBrainz's
community-voted genres get 1.2, Deezer's album-level genres get 0.8, and MusicBrainz's
album pick gets 0.6. Equivalent values are grouped together before counting:
- case and punctuation are ignored;
- album suffixes like "(Deluxe Edition)", "(2011 Remaster)" and "- Single" are ignored;
- genre spellings are merged: "Hip-Hop/Rap" = "Rap/Hip Hop" = "hip hop", and "R&B/Soul" = "R&B";
- durations within 2 s count as the same.

The heaviest group wins and is spelled the way its most trusted source spells it. Ties go to
the more trusted source.

The year is not voted: MusicBrainz's first release date wins when no other source has an
earlier year, so a 1975 song on a 2011 remaster stays 1975. Each field records its agreement
share (the claim's `confidence`), and disagreements are saved under
`provenance.conflicting_values`.

**What changes for the user:**
- More fields get filled: original year, composer, lyricist and language.
- MusicBrainz tags provide a mood without a Last.fm key.
- Downloaded files are tagged with composer, lyricist, language, and the MusicBrainz
  recording and work ids, so Picard and Navidrome can match them. The release id is left out
  because it is only a best guess.
- Tag-to-mood matching now uses whole words for short words, so "funk" no longer means
  "Happy".

**Licences.**
- MusicBrainz core data is CC0.
- Tags and genres are MusicBrainz supplementary data (CC BY-NC-SA). The app's
  local-library enrichment already used them.

## 2. Lyricsfile and more lyrics sources

- **Lyricsfile 1.0** (LRCLIB/LRCGET's YAML format: plain, line-synced and word-synced):
  - parsed wherever lyrics are parsed (`Lyricsfile.kt`);
  - importable as `.lyrics` files;
  - written as a `.lyrics` sidecar next to the `.lrc` when lyrics are saved.
- **LRCLIB's `lyricsfile` field** is the only place LRCLIB publishes word timing. It is now
  used in automatic, manual and scanner fetches.
- **Unison** (Better Lyrics' voted database) is a word- and line-synced source:
  - it is looked up by the playing YouTube video id first, so the timing matches that exact
    upload;
  - when that finds nothing, it is looked up by song, artist and duration.

  Its data is ODbL-1.0, so its lyrics always show "Lyrics from Unison
  (https://unison.boidu.dev)". Selling a product built on the corpus needs a commercial
  licence from Unison.
- **Credits** under the lyrics name the provider that actually supplied them, instead of
  always LRCLIB.

## 3. Streaming on a weak mobile signal

**Symptom:** a song played for a moment, stopped for a long time, then failed with
`Source error` (`SocketTimeoutException`). The logcat showed first bytes taking 16–57 s and
the same songs downloaded several times at once.

**Cause.** The local stream proxy wrote audio with `writeFully`, and Ktor only passes such
bytes on once 1 MiB has built up. On a slow connection this is what happened:
1. The player got nothing for tens of seconds after its first read.
2. It ran dry and hit its 8 s read timeout.
3. It reopened the stream, while the abandoned proxy responses kept downloading and
   reconnecting.
4. Each retry made the next one slower.

**Fixes:**
- **Flush:** every upstream read is flushed to the player as it arrives (`CloudStreamProxy`).
- **Stall replacement:** a connection silent for 10 s is replaced from the same byte.
  Failures only count while no byte arrives in between. Reconnecting stops after 20 s
  without a byte, or once the player has made a newer request for the same song, so
  abandoned responses no longer download.
- **Longer player timeout, proxy only:** the player waits 60 s for the loopback proxy
  (`LoopbackRoutingDataSource`), which is longer than the proxy's own recovery. Remote http
  sources keep Media3's 8 s default.
- **Rebuffer backoff:** after repeated rebuffers within a minute, playback waits for 3, 6
  and then 10 s of audio before resuming, instead of stopping again every second
  (`RebufferBackoffLoadControl`).
- **Next-song prefetch:** it now waits until the playing song stops loading.

**Tests:**
- `StalledUpstreamProxyTest`: a fake CDN that stalls mid-transfer.
- `RebufferBackoffTest` and `RebufferBackoffLoadControlTest`.
- `NextStreamPrewarmerTest`.
