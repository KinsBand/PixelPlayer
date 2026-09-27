# Playback and artwork: Musify comparison

Reviewed 2026-09-27. Changes apply to PixelPlayer. Musify source revision:
`5b98d3a3cddd3fdb16ea6cc14d59d667e82cccd2`. No Musify implementation was copied.

## Reference findings

- [Audio service](https://github.com/gokadzev/Musify/blob/5b98d3a3cddd3fdb16ea6cc14d59d667e82cccd2/lib/services/audio_service.dart): offline-source preference, transition IDs to reject obsolete loads, upcoming-song preparation with three-item lookahead and two concurrent preloads, and invalidation on playback failure.
- [Common services](https://github.com/gokadzev/Musify/blob/5b98d3a3cddd3fdb16ea6cc14d59d667e82cccd2/lib/services/common_services.dart): cache selected audio streams and quality-specific URLs; resolve audio-only manifests on misses. URL cache uses a three-hour duration with a separate validation policy.
- [Stream buffer](https://github.com/gokadzev/Musify/blob/5b98d3a3cddd3fdb16ea6cc14d59d667e82cccd2/lib/services/stream_buffer_service.dart): ranged downloading into a file while playback reads it. PixelPlayer already has a ranged stream proxy; a second independent buffering system would duplicate storage/network responsibilities.
- [Artwork provider](https://github.com/gokadzev/Musify/blob/5b98d3a3cddd3fdb16ea6cc14d59d667e82cccd2/lib/utilities/artwork_provider.dart) and [song artwork](https://github.com/gokadzev/Musify/blob/5b98d3a3cddd3fdb16ea6cc14d59d667e82cccd2/lib/widgets/song_artwork.dart): choose cached network, local file, embedded data or asset image providers. The provider-object map has no size bound. The song artwork widget sets display dimensions without explicit network-image resizing. These are loading mechanisms, not an independent album-cover identification service.
- [Song formatter](https://github.com/gokadzev/Musify/blob/5b98d3a3cddd3fdb16ea6cc14d59d667e82cccd2/lib/utilities/formatter.dart): the video-to-song path takes a supplied playlist image or YouTube's standard/low/maximum-resolution thumbnail URLs. It retrieves existing images; it does not generate covers. PixelPlayer's catalogue matching can provide an actual album cover where a video thumbnail is unsuitable.

## PixelPlayer's existing path

Playback keeps logical YouTube IDs in the queue, prefers readable downloads, then resolves through the stream proxy. `YouTubeStreamExtractor` tries a bounded direct InnerTube request before NewPipe, caches expiring manifests, and serializes extraction per video. Next-song preparation and decoder buffer tuning already exist. `ResolverOrchestrator` has no production callers in the inspected source, so changing it would not accelerate the active player.

For remote metadata, `SongMetadataGatherer` queries Deezer and iTunes concurrently, scores identity using title/artist/duration (also validating ISRC hits), and optionally adds Last.fm tags. It prefers iTunes covers, persists results, shares in-flight lookups and bounds concurrency. Existing source cover URLs are enlarged to 1400 pixels where supported. Gathering waits for the catalogue pair and optional tags; it is separate from fetching image bytes. No provider matching rules were loosened to make lookups appear faster.

Local covers are extracted lazily from embedded artwork, cached persistently, and bounded to 1536 pixels when necessary. Two allocation permits bound concurrent heavy extraction. Folder/gallery images remain excluded from automatic fallback.

## Implemented changes

1. **Protect current-track startup.** A main-thread-owned `NextStreamPrewarmer` starts speculative work only when the player is ready and intends to play. Queue changes coalesce for 200 ms. A lookup promoted to the current track survives the buffering transition, so foreground extraction can reuse it. Unrelated obsolete work is cancelled. Preparation is capped at ten seconds and one speculative job; pause/resume and ready-state events now update it.
2. **Fetch thumbnail-sized artwork.** Coil maps recognized Google, Apple and Deezer URLs to shared 256/512/1024-pixel variants according to requested pixel dimensions. Requests above 1024 pixels retain their source URL. Small originals are not enlarged, and source metadata/download URLs remain unchanged. Local, unknown, malformed and query/fragment-bearing URLs are untouched. Exact host boundaries prevent accidental rewriting based on a domain name appearing in a path or another hostname. Automatic cache keys derive from the mapped URL; callers' explicit keys are retained.
3. **Share local extraction work.** The local Coil fetcher serializes requests per song using a suspending keyed mutex. Waiting requests reuse either the cached cover or the no-art marker. Blocking MediaStore/file/metadata work runs on `Dispatchers.IO`, rather than occupying the image loader's CPU dispatcher. Existing extraction memory limits remain in effect.

## Validation and limits

Focused unit tests exercise source-URL preservation and resize variants, cold-start deferral, rapid queue replacement, promoted-lookup survival, stale cancellation, pause/resume and timeout. Run:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests '*ArtworkUrlsTest' --tests '*NextStreamPrewarmerTest'
```

Result: production Kotlin/Java compilation passed. The normal command could not compile unrelated existing tests, including outdated constructors in `PlayerViewModelTest`, `QueueStateHolderTest` and mix tests. All **seven new tests passed** with a temporary source filter (same configuration saved here for reproducibility):

```powershell
./gradlew.bat :app:testDebugUnitTest --tests '*ArtworkUrlsTest' --tests '*NextStreamPrewarmerTest' --init-script tools/validation/musify-playback-artwork.init.gradle --no-configuration-cache
```

The filtered run is not a full test-suite pass. It leaves the unrelated test sources unchanged.

No Android device was attached during this work. These changes remove identified unnecessary work; no end-to-end speedup percentage is claimed. A 256-square response has about 30 times fewer pixels than a 1400-square source, but compressed bytes and elapsed download time do not scale directly with pixel count. Existing 128-pixel decode targets already limit final bitmap size; the principal remote-art benefit is avoiding large source transfers and disk entries.

Before release, compare cold/warm artwork scrolling, full-player quality, offline embedded covers, rapid skipping, pause/resume, and rebuffering on Wi-Fi and a constrained network. Record p50/p95 tap-to-audible latency and image bytes transferred. Measure at least 30 trials per playback case; separate manifest-cache hits from misses. Separate thumbnail variants can cost an extra request when later opening a large cover, so measure both scrolling and player-opening paths. Provider URL formats remain an external dependency.

## Follow-up: first-time online track startup

The initial artwork/readiness changes did not replace the cold manifest resolver. A deeper comparison found Musify's [client configuration](https://github.com/gokadzev/Musify/blob/5b98d3a3cddd3fdb16ea6cc14d59d667e82cccd2/lib/constants/clients.dart) explicitly selects VISIONOS. Its [stream client](https://github.com/gokadzev/Musify/blob/5b98d3a3cddd3fdb16ea6cc14d59d667e82cccd2/packages/youtube_explode_dart/lib/src/videos/streams/stream_client.dart) can omit watch-page retrieval when requested, though Musify's inspected caller uses the default `requireWatchPage=true`. This is source evidence for the client choice, not proof of a particular first-play latency.

PixelPlayer previously tried WEB_REMIX through the search client's homepage/version bootstrap, waited up to 700 ms, and only then began full NewPipe extraction. The revised path:

- Uses NewPipe 0.26.5's maintained `getVisionOsPlayerResponse` directly. This obtains visitor data and the native player response without the WEB_REMIX homepage or a full watch-page/metadata extraction. It is two requests on a cold direct lookup, not a claimed single request. The native user agent and playback nonce stay consistent with subsequent CDN requests. Returned video identity, progressive audio formats, expiry and URL restrictions are checked.
- Starts full extraction after a 200 ms head start if the direct request has not produced a usable result. Empty/error results start fallback immediately. The first nonempty result wins; the other branch's HTTP calls are cancelled through `NewPipeExecution`. The direct branch has a 1500 ms budget, but that budget no longer adds serially to fallback time. Failed signed URLs still bypass the direct route during the existing cooldown.
- Removes a second YouTube neighbour-resolution loop in `DualPlayerEngine` that started both adjacent lookups after 600 ms even during cold startup. The readiness-gated next-track prewarmer remains the owner of explicit YouTube preparation. Media3's own queue preloading remains intact.
- Flushes the first proxied audio bytes promptly and logs `manifest_provider`, `manifest_network_ms`, `proxy_first_bytes_ms`, and `player_transition_to_audio_ms` under `StreamingLatency`. The last metric ends at Media3's audio-position-advancing event and begins at its item-transition callback. It excludes earlier catalogue matching/dispatch and is not a physical speaker or tap-to-audible measurement. Some reused-sink transitions may not emit a new audio-start event. Proxy timing is per HTTP request, including seeks.

The concurrent fallback can create extra requests when native resolution exceeds 200 ms. Winners cancel losers, but already completed requests cannot be undone. Buffer thresholds, audio quality selection and download priority were not reduced to manufacture lower timing numbers.

The deterministic scheduling test covers a stalled direct request and a 100 ms fallback: completion is **300 ms** (200 ms head start + 100 ms), versus **800 ms** under the former 700 ms serial timeout. This is a simulated regression check, not a real network benchmark or a guarantee that every first play takes 300 ms.

Additional focused checks cover direct-only success without starting fallback, immediate failure/empty fallback, both-provider failure, timeout, cancellation of both branches, provider cancellation, native visitor/player request shape, matching playback nonce/user agent, existing parser restrictions and byte-range rendition consistency. Run the expanded focused suite with:

```powershell
./gradlew.bat :app:testDebugUnitTest --init-script tools/validation/musify-playback-artwork.init.gradle --no-configuration-cache
```
