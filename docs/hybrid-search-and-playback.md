# Kotlin search and playback refinement

Reviewed 22 September 2026 against the existing working tree and [Musify](https://github.com/gokadzev/Musify/tree/41032baba8580861194b1d9f2b969de9472ee5c6).

## Findings

The app already uses Kotlin, coroutines, Compose, Room, Media3 and NewPipe. Its search path had FTS/local results, a short network debounce, a 64-entry song cache, ID-scoped favorite/download hydration and latest-query protection. Playback already had audio-only rendition selection, signed-URL expiry, download preference, range-aware proxying and retry recovery. Those are useful foundations; a Flutter/Dart runtime would not improve them.

Online search previously supported songs only. Artists, albums and playlists were local-only filters, song filtering narrowed to title matches, artist results appeared below songs/albums, and neither saved lyrics nor tempo metadata participated in search. Blocking extractor HTTP calls could outlive cancelled queries. The old InnerTube request model was unused and contained a fixed 2024 client version.

Musify's `packages/youtube_music_explode_dart/lib/src/music_client.dart` separates canonical YouTube Music artist/track lookup from its broader YouTube search. Its `lib/services/common_services.dart` separates search caching from audio manifest resolution, and `artist_service.dart` validates artist identities. This implementation follows those architectural ideas in Kotlin; it does not embed Musify or its Dart runtime. “Outer” is interpreted here as broader public YouTube extraction through NewPipe, not a separate dependency called OuterTube. NewPipe itself also uses InnerTube internally.

## Implemented behavior

- Direct public InnerTube search for songs, artists, albums and playlists, with runtime client-version discovery. Typed parsing uses result-row endpoints rather than unrelated IDs hidden in menus.
- NewPipe music and public-video search as fallbacks. A 250 ms head start lets a fast direct response avoid a second request; a slower request races the fallback. The first useful response wins and cancels the other. Empty/failing primary responses start the fallback immediately.
- Immediate local/cache results remain independent of remote artist and lyric requests. New queries and filters clear obsolete results, and requests capture their filter rather than reading changing UI state later.
- Native horizontal artist shelf at the top for every category, including credited artists from matching tracks when their identities are available. Remote artist, album and playlist results open a paginated track sheet with playback; local results keep local navigation. Remote collections are excluded from local bulk-selection actions.
- Music-specific song search now includes artist names. Library album/artist searches respect directory filtering.
- `songs around 120 bpm` matches known library tempos from 115 through 125 BPM. Explicit ranges such as `110–130 bpm`, decimal tempos, `beats per minute`, and the typo `bpi` are accepted. Optional genre/title/artist terms are retained. Saved musical-feature metadata takes precedence over analyzed BPM from `track_analysis`; missing/invalid BPM never counts as a match. Candidates are filtered and ranked before the result limit.
- Saved lyric snippets are searched in embedded, saved and normalized lyric stores, without returning large lyric text in list cursors. SQL wildcard characters are escaped. Explicit `lyrics: …`, quoted phrases and `song that goes …` queries invoke lyric search. Longer free-text queries also request broad online lyric candidates independently of ordinary music results.
- Direct progressive audio is attempted within a 700 ms budget, followed by NewPipe when extraction/signature processing is needed. Video, OTF, expired, non-HTTPS, foreign-host and `n`-transformed URLs are rejected by the direct parser. Both paths use the existing manifest cache and rendition selection. Invalidated URLs temporarily bypass the direct path so recovery does not repeatedly choose a rejected URL.
- Blocking NewPipe operations now run through a bounded worker dispatcher and a per-operation HTTP-call registry. Cancellation releases the caller promptly, cancels in-flight calls and rejects subsequent calls from that obsolete extraction.

## Practical limits

YouTube does not provide verified BPM as part of these search results. Online BPM results are search candidates and the UI explicitly says so; they are not assigned invented tempo values. Library tempo filtering requires stored or previously analyzed BPM. It does not decode every online candidate during typing.

Online lyric search is candidate discovery, not guaranteed fingerprint recognition. Availability, exact words, language, punctuation and provider indexing affect matches. Local lyric matching currently finds contiguous snippets; phrases split by LRC timestamps or different line breaks may need shorter snippets. There is no promise that every natural-language request or every song can be resolved.

Search fetches the initial result page; remote collection browsing supports continuation pages. An artist page may not expose playable tracks. Public InnerTube responses and NewPipe remain subject to upstream changes, network conditions and regional availability.

No Android device/emulator was connected during verification, so live provider playback, UI appearance and device latency still need device validation. Kotlin alone does not guarantee instant network results.

## Verification

The app compiles with `:app:compileDebugKotlin`. Focused JUnit suites cover intent/range parsing, tempo precedence, result renderer identity, direct stream validation, provider fallback, hedging, HTTP cancellation, cache hydration, stream quality and query/filter changes.

The ordinary unit-test command is blocked during test compilation by pre-existing unresolved methods in `OverlayCutoutDimensionsTest.kt` (`getLevel1Height`, `getLevel2Height`, `getLevel3Height`, `nearestSnapLevel`). A temporary Gradle init script excludes only that unrelated file for the focused run; the source test is unchanged. The focused run is not a claim that the entire repository test suite passes.

The new DAO SQL is also exercised with in-memory SQLite using the current Room-generated table definitions, checking tag/analyzed BPM, excluded folders, literal wildcard matching, normalized lyrics and omission of lyric bodies from list results.
