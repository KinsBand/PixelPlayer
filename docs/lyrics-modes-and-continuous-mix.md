# Lyrics modes, online search and continuous mixes

Implemented in the existing Android app, 2026-09-17.

## Lyrics

Lyrics → Options offers Synced (best available), Word by word, Vowels / letters, and Sentence by sentence. The choice persists in DataStore. Unspoken characters use the dimmed foreground; phoneme mode colours the actual UTF-16 character ranges supplied by timing data, so proportional fonts do not turn letter positions into inaccurate width percentages. Seeking backwards restores unspoken ranges. Word mode reveals a complete word at its real onset. Sentence mode ignores detailed word data and uses line timestamps.

Save, Translate and Reset this song are grouped under Manage lyrics. Source priority, local scanning, online lyric lookup, immersive display and delay, global reset, animation and blur are directly in Options. These controls share the same settings as the Settings screen. Alignment, translation visibility, romanization and screen-on controls remain available.

Selecting a mode cannot create absent acoustic evidence. Untimed lyrics remain plain; lines without words remain line-timed; words without phones use word timing. The menu explains these fallbacks. Translations do not inherit original-language phoneme timing. Universal automatic word/vowel alignment is still not implemented: it requires a suitable singing model and actual recording audio. No fabricated equal-duration letter timestamps are stored.

## Search responsiveness

Exact repeated searches can emit memory-cached online results before Room finishes local searching. Fresh results then update them. Hydration now queries only the returned song IDs instead of loading the complete cloud catalogue and all favourite IDs. Existing normalized query caching and shared request locks remain in place. The search input debounce is 50 ms.

100 ms is a desired warm-cache UI target, not a verified end-to-end guarantee. Uncached online search, provider throttling, stream extraction, decoding and audio buffering all add variable latency. No claim of sub-100-ms cold streaming or device benchmark is made.

## Mix flavours

The mix behavior described below is the earlier implementation. See [Adaptive mix engine](adaptive-mix-engine.md) for the current scoped feedback, exposure tracking, balanced Smart Mix, sequencing, controls, validation and remaining research gates.

Home's mix launcher exposes Normal Mix and Smart Mix, with status text.

- Normal Mix starts from the mix/library and draws from current local songs, connected playlists, provider likes and saved favourites. Each refill reloads the catalogue. After fresh candidates run out it can revisit older played songs, while excluding the current and upcoming queue.
- Smart Mix starts from the current queue context (or the existing mix). It combines artist/genre search with related music candidates from YouTube stream information. Candidates already in the library or recent mix history are excluded, including duplicate title/artist recordings. Ranking is metadata-based relevance, not a claim of measured audio similarity.
- A playback observer checks the remaining queue every 1.5 seconds. At five remaining songs it prepares up to twelve more, rechecks that the user is still listening to the same mix, and appends without replacing the current item. Discovery failures retain playback and retry after thirty seconds. Older queue history and recent identity history are bounded. Starting another source stops the refill loop.

Refilling lives in the player ViewModel lifetime. A force-stop/process death ends the live mix session; it is not a scheduled background task. Provider failures, an exhausted discovery pool, or a library containing only the current song can leave no fresh candidate. Existing queue playback is retained in these cases.

The NewPipe reference used for related stream items is [StreamInfo v0.26.5](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/stream/StreamInfo.java). This remains an unofficial YouTube integration.
