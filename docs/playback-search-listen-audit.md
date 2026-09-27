# Playback, metadata, artwork, search, and Listen audit

Reviewed 2026-09-21 against the working checkout, which already contains substantial uncommitted streaming and UI changes. Changes in this audit extend that implementation rather than replace it.

## External source review

| Source and revision | What the code actually does | Application to PixelPlayer |
| --- | --- | --- |
| [Spotify metadata plugin](https://github.com/sonic-liberation/spotube-plugin-spotify/tree/47d0a1051b576616f9e823cc756b84e8dc1a53f4), `RealMetadataSearchAPI.kt`, `RealMetadataTrackAPI.kt` | Kotlin plugin interfaces over a Spotify GraphQL client. Separates paginated searches, track/album metadata, artist identities, cover-art sources, and library operations. It does not supply playback audio. | Keep Spotify metadata and artwork attached to the original track while mapping its recording to an audio source. PixelPlayer already uses its own OAuth/Web API repository and persistent `TrackMappingDao`; importing this plugin would require its runtime/authentication and would not eliminate network latency. |
| [YouTube audio plugin](https://github.com/KRTirtho/spotube-plugin-youtube-audio/blob/94d87c573ef253afa330646f866af2943e6bbbca/src/segments/audio_source.ht) | Searches by ISRC, with title/artist fallback, through Spotube's host `YouTubeEngine`; obtains renditions through `streamManifest`. The plugin delegates actual extraction. Its output hardcodes AAC and a lossless label even though it lists lossy presets. | PixelPlayer's native NewPipe extractor already provides the corresponding boundary. Preserve actual container/bitrate and signed-URL expiry. Prefetch and cache the manifest instead of loading a second plugin runtime or copying hardcoded codec claims. |
| [YouTube Music plugin](https://github.com/Benisgo/spotube-plugin-youtube-music/tree/8db47209bf4a4534cd3cbc4058e3a51ce288314c/src/segments), `search.ht`, `track.ht` | Search methods make InnerTube requests but return empty arrays. Track lookup returns “Unknown Track”, no artists/artwork, and duration zero; batch tracks and lyrics are placeholders. | This revision is not a functioning search/metadata replacement and cannot substantiate speed or correctness claims. Retain native search. |

No external plugin source was copied into the app.

## Existing playback and metadata path

`MusicRepositoryImpl.searchAll` merges the local Room-backed catalog with `YouTubeRepository`. `YouTubeMusicApiService` uses NewPipe's music-song search, a 64-entry five-minute memory cache, and batch favorite/download hydration. `SearchStateHolder` uses latest-query cancellation and request IDs. Search results already include artwork URLs; detailed metadata enrichment does not need to block those results.

Spotify imports retain catalog titles, artists, album names, duration, ISRC, and artwork. `SpotifyToYouTubeResolver` checks persistent recording mappings, then performs an ISRC search and a title/artist fallback. Cold resolution can therefore require more than one network request. Its permissive fallback for ISRC results and substring-based title matching still merit a separate recording-accuracy audit; the new conversational matcher deliberately requires a normalized exact title and the requested artist.

`DualPlayerEngine` uses Media3, downloaded audio when available, and a local streaming proxy. NewPipe extracts progressive audio-only streams. `YouTubeAudioStream` maintains signed-URL expiry with a safety margin. The extractor caches manifests and the proxy preserves a chosen rendition during ranged reads. Warming a manifest removes extraction work from a later transition; it does not prebuffer the audio bytes or guarantee instantaneous sound.

`EnrichmentRepository` separately gathers editorial metadata, MusicBrainz/cover art, analysis, and lyrics. These tasks involve network calls and/or decoding and should remain background work. YouTube search currently labels the album generically as “YouTube Music”; a thumbnail is not proof of the official album cover.

Artwork already uses shared Coil memory/disk caching and bounded decode sizes. This change chooses the largest reported thumbnail by dimensions instead of assuming list order, removes invented search-result bitrate/sample-rate values, and reduces the default artwork fade from 300 to 100 ms. Provider data still determines artwork correctness.

## Implemented latency fixes

- Replace 16 hash-bucket locks with reference-counted per-key locks in search and manifest extraction. Slow work for one song/query no longer blocks an unrelated key that happens to collide. Same-key callers still share cached results after the first finishes; cancelled waiters release their references.
- Let songs, albums, artists, and playlists emit independently into ALL search instead of withholding the first song result until every local category responds.
- Show cached search results immediately and skip the network debounce on cache hits. Retain 150 ms debounce for cold queries to avoid a request for every keystroke.
- Re-run the current query when the filter changes.
- Prefetch the next YouTube queue item's manifest after a 200 ms settling period. Cancel obsolete prefetch work on queue changes and avoid network extraction for existing downloads. This currently warms YouTube items; unmapped Spotify items still need recording resolution.

## Listen defects and repairs

The previous controller captured PCM with `AudioRecord`, waited for speech to end, then ignored that PCM and started a new `SpeechRecognizer` microphone capture. Both microphone owners could overlap. Preference collection could also start capture without active playback, the manifest lacked a microphone foreground service, and the Queue toggle did not request permission. The class called `SileroVadEngine` is an energy-threshold detector, not a loaded Silero model.

Listen now has one microphone owner: an on-device speech recognizer started before speech. A dedicated, non-exported microphone foreground service is started from Queue or Settings after microphone permission. It has a persistent notification and Stop action, remains independent of the music service, and does not restart automatically after process death. UI state follows the running service rather than a stale persisted toggle.

Recognition sessions restart after results or silence, back off on transient errors, and stop after repeated failures or missing permission/language support. A watchdog handles a provider that never completes. Stop cancels matching work, destroys recognition, restores ducked playback volume, and invalidates late callbacks. Conversation audio is not recorded to files or sent to a cloud recognizer by this path. Only an extracted song query may be sent to the online catalog.

Recognized requests and structured mentions such as “play Hello by Adele”, “I love Blinding Lights by The Weeknd”, and “that song …” are matched against the local library first. An online fallback runs only if a matching local recording is absent. Arbitrary conversation is not a general-purpose song entity recognizer: bare ambiguous titles and unsupported phrasing can be missed.

Suggestions remain pending in the Queue. Existing Add, Play next, Dismiss, and Clear all actions are retained. Suggestions now appear even when the playback queue is empty. Dismissal/approval suppresses the same title/artist across providers for five minutes, and the pending list is capped at 50 entries. Nothing is automatically queued by the recognizer.

## Performance and platform limits

Millisecond targets apply to local and warmed paths; first-time online extraction/search, image downloads, speech endpointing, and decoding have external latency. No universal millisecond playback claim is justified without device/network measurements. Existing `StreamingLatency` logs report search and manifest time; this change adds transcript-to-suggestion matching time. That metric starts after transcription, not at the start of speech.

Listen requires Android 12+ with an installed on-device recognition service and language model. Android 11 or devices without that facility receive an unavailable message. [Android's SpeechRecognizer documentation](https://developer.android.com/reference/android/speech/SpeechRecognizer) explicitly cautions that the API is not intended for continuous recognition; repeated sessions are device-dependent and may consume significant battery. A dedicated streaming offline ASR engine would be needed for a universal always-listening implementation. [Microphone foreground-service restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) also require a visible user-originated start. A foreground notification cannot bypass a denied/revoked microphone permission or an unavailable model.

## Verification

Targeted tests cover independent and same-key lock behavior, cancellation, local matching, artist/version rejection, conversational parsing, bounded suggestions, dismissal suppression, search cache hydration, latest-query/filter dispatch, and recognition restart/stop/unavailable behavior. The cache test reports JVM memory-lookup p50/p95 with a mocked catalog; this is not an Android end-to-end playback benchmark.

Device acceptance still needs spoken phrases on a supported phone, including background/screen-off use, notification Stop, permission denial/revocation, unavailable language models, playback ducking/restoration, and queue approval. Cold/warm search and tap-to-audio p50/p95 should be measured separately on that phone. Build/test outcomes are recorded below after validation.
