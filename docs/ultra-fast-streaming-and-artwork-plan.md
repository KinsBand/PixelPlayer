# Ultra-fast first play and instant cover art: implementation plan (v2)

Written 2026-09-27. This is a refined version of the "Ultra-Fast First-Time Streaming & Instant Cover Art" plan (v1). Every item below was checked against the current source, and file references are relative to `app/src/main/java/com/theveloper/pixelplay/`. The latency figures are **estimates to test against, not measurements**. No device was attached while this plan was written.

Background reading: `docs/musify-playback-artwork-audit.md` (Musify comparison, already implemented) and `docs/streaming-latency-and-accounts.md` (earlier latency budgets).

---

## 0. What changed from v1, and why

v1 treated every first play the same way. v2 splits first plays into three cases, because each needs a different fix:

| Case | Example | Current critical path | v2 strategy |
| --- | --- | --- | --- |
| **Cold**: the app has never seen the song | New search result | Proxy boot → manifest (VISIONOS / NewPipe hedge) → CDN TTFB → decode | Shorten the path, and start it before the tap (touch-down prewarm, search top-N) |
| **Known**: the song played before, but its URL has expired | Yesterday's song | Same as cold, because nothing is persisted except a 64-entry in-RAM manifest | **Stream-before-resolve**: serve persisted head bytes while the manifest resolves in parallel |
| **Predicted**: the song was prewarmed | Next in queue, top search hit | Manifest from RAM, then CDN TTFB | Head bytes already in RAM, so no network wait at all |

### v1 items corrected or dropped after checking the code

| v1 item | Finding in the current code | v2 decision |
| --- | --- | --- |
| "Allow DASH adaptive audio in `extractNewPipeStreams`" | For normal (non-OTF, non-live) YouTube audio, NewPipe already reports adaptive `itag 251/140` as `PROGRESSIVE_HTTP`. The only streams that filter rejects are OTF/live DASH, and those **cannot** be served as byte ranges. | **Dropped.** It would change nothing, or break playback. |
| "Prefer WebM/Opus on first start" | `selectRendition` already picks the highest bitrate, which is `itag 251` (Opus, about 130–160 kbps) over `140` (AAC 128k). YouTube's MP4 audio is fragmented with `sidx` at the front, so there is no `moov`-at-end seek problem. | **Dropped as a code change.** Recorded here as a verified property. |
| "Player tier uses `hqdefault.webp`" | `hqdefault` is 480×360 with letterbox bars, which is worse than what the player shows today. | Use `maxresdefault.webp`, fall back to `sddefault` → `hqdefault`, and centre-crop. See §7.4. |
| "`placeholderMemoryCacheKey` promotion" presented as new | `OptimizedAlbumArt.kt:79-85` already sets a placeholder key, but it is **the request's own key** (`albumArtMemoryCacheKey(uri, requestTargetSize)`), so it can never find the list-tier bitmap. | **Kept, re-scoped:** fix the key so it points at the list tier. See §7.1. |
| "`runBlocking` in `resolveDataSpec` is a bottleneck" | ExoPlayer loader threads are designed to block. The real cost is a Room query plus a proxy readiness check on every `DataSource` open. | Keep `runBlocking`, but add a fast path for YouTube resolution (§2.1). |
| "Direct field-masked `/player` cuts latency by 98%" | The 98% is payload size, not latency. On a cold path, latency is mostly round trips. NewPipe's VISIONOS helper also supplies visitor data and client constants that change with NewPipe releases. | **Moved to Phase 6**, behind a kill switch, raced against NewPipe rather than replacing it. |
| "Two artwork tiers (256 / 1024)" | Two-column grids on xxhdpi phones need about 540 px, so a 1024 px tier would roughly double grid bytes. YouTube Music's own native size is 544. | **Three tiers: 256 / 544 / 1024**, with the player, notification and widget all sharing the 1024 tier. |
| "Enable hardware bitmaps in `SmartImage` by default" | Six call sites force software bitmaps. Palette and blur consumers need CPU-readable pixels, and a hardware bitmap crashes `Palette`. | Opt in per call site after an audit, not as a global default. See §7.6. |
| "Edit `ResolverOrchestrator`" (implied) | It has no production callers (see the audit doc). | Out of scope. Delete it or leave it alone. |

### New bottlenecks found that v1 missed

1. **Catalog queues match every track before any playback.** `PlaybackDispatchStateHolder.hydrateSongsIfNeeded` (about lines 491–523) calls `spotifyToYouTubeResolver.resolveSpotifyTrackToVideoId` **sequentially for every unmapped track** before `internalPlaySongs`. Each call is a YouTube search, so a 50-track Spotify playlist with no cached mappings means about 50 searches before song 1 starts. This is the largest single first-play delay in the app. (v1 described the symptom but not the cause.)
2. **A transient network blip pushes playback onto the slowest resolver for 2 minutes.** `CloudStreamProxy.serveChunked` calls `invalidateStream(id)` on *any* upstream read failure. For YouTube this reaches `YouTubeStreamExtractor.invalidate`, which sets a **120 s `directCooldown`**. During the cooldown, every re-resolve for that song skips VISIONOS and runs the full NewPipe watch-page extraction, which is slow and uses tens of MB of heap.
3. **The first search downloads the whole YouTube Music homepage.** `InnerTubeClient.clientVersion()` fetches `https://music.youtube.com/` (hundreds of KB of HTML) to scrape `INNERTUBE_CLIENT_VERSION`. Nothing persists the result, so this happens on every cold app start.
4. **Coil has its own connection pool.** `AppModule.provideImageLoader` builds `OkHttpClient.Builder().build()`, a brand-new client. Artwork therefore never reuses warm HTTP/2 or TLS connections from the rest of the app, and every cold image host costs an extra handshake.
5. **The proxy port has a race, and startup has a polling delay.** `CloudStreamProxy.start()` opens `ServerSocket(0)`, closes it, and then asks Ktor to bind that port. Another process can take the port in between. `awaitReady` then polls in 50 ms steps, so readiness is detected up to 50 ms late.
6. **The OkHttp interceptor blocks a thread on every request.** `YouTubeNetworkModule` runs `runBlocking { networkAccessPolicy.getDecision(Update) }`, which does up to three `DataStore.first()` reads. That covers every NewPipe request and every 10 MiB chunk.
7. **Nothing persists the rendition.** Resolved `(videoId → itag, clen, mime)` lives only in two in-RAM maps (`manifests`, 64 entries, and `renditionKeys`, 100). After a process death, every song is cold again.

---

## 1. Latency model and targets

A first play is a chain of stages. The goal is to remove stages from the critical path, not just make each one a bit faster.

```
tap ─┬─ [A] dispatch: hydrate/match queue, build MediaItem          (0 – 50,000 ms today for catalog queues)
     ├─ [B] proxy ready (lazy Ktor CIO boot on first ever play)     (~50–300 ms cold, once per process)
     ├─ [C] manifest: VISIONOS player (+visitor data) ∥ NewPipe     (~300–1,500 ms cold; 0 if cached)
     ├─ [D] CDN: DNS + TLS + TTFB to rrN---sn-xxx.googlevideo.com  (~100–400 ms)
     ├─ [E] container probe + bufferForPlaybackMs of audio          (~10–50 ms once bytes flow)
     └─ [F] decoder + AudioTrack start                              (~20–80 ms)
```

| Scenario | Today (estimate) | v2 target p50 | v2 target p95 | How |
| --- | ---: | ---: | ---: | --- |
| Catalog playlist, unmapped tracks | seconds to tens of seconds | ≤ 1.5 s | ≤ 3.5 s | [A] only matches the start song (§1.1) |
| Cold search result, no prewarm | ~1.0–2.0 s | ≤ 800 ms | ≤ 1.8 s | [B] removed, [C] shortened, [D] overlapped |
| Cold search result, touch-down prewarm | same | ≤ 600 ms | ≤ 1.4 s | [C] starts about 100 ms before `onClick` (§4.2) |
| Top-N search hit, prewarmed | ~300–600 ms | ≤ 150 ms | ≤ 400 ms | [C] and [D] finished before the tap (§4.1, §5) |
| Previously played song, URL expired | same as cold | ≤ 150 ms | ≤ 350 ms | Stream-before-resolve from the disk head cache (§5.3) |
| Next in queue | ~200–400 ms | ≤ 80 ms | ≤ 200 ms | RAM head cache (§5) |

**The bar for "faster than Musify":** Musify has no head cache and no stream-before-resolve, so its known and predicted cases still pay [C] and [D]. The two v2 features that change the shape of the problem, not just the constants, are head bytes that stay valid regardless of URL expiry (§5.2) and touch-down speculation (§4.2).

---

## Phase 0: Measure first (required before other phases merge)

Without this, none of the targets can be verified.

- **New `PlaybackTrace` in `data/diagnostics/`.** One trace ID is created on tap in `PlaybackDispatchStateHolder.playSongs` / `loadAndPlaySong` and passed through `MediaItem.requestMetadata.extras`. Stages are marked with monotonic `SystemClock.elapsedRealtimeNanos()`:
  `tap`, `dispatch_ready`, `proxy_ready`, `manifest_done(provider, cache=hit|miss|memo)`, `upstream_ttfb`, `proxy_first_bytes`, `player_ready` (`STATE_READY`), and `audio_advancing` (from the existing `player_transition_to_audio_ms` hook).
- Emit one summary line per play: `StreamingLatency trace=… tap_to_audio_ms=… stages=…`. Emit no URLs, queries or IDs beyond the video ID.
- Add counters for prewarm hits, prewarm waste (bytes and requests that were never played), head-cache hit ratio, and `429`/reCAPTCHA events.
- Add a debug-only developer-settings panel showing the last 20 traces with p50/p95. The same data is used for before/after comparisons.
- **Baseline protocol:** Wi-Fi plus throttled 4G (Android emulator network profile or a real SIM). Run at least 30 plays per scenario in the table above, and record the device, build type and thermal state.

---

## Phase 1: Remove self-inflicted delays (low risk, highest ROI)

### 1.1 Match only the start song before playing a catalog queue
**Files:** `presentation/viewmodel/PlaybackDispatchStateHolder.kt`, `data/spotify/SpotifyToYouTubeResolver.kt`

- Split `hydrateSongsIfNeeded` into two functions:
  - `hydrateForStart(songs, startSong)`: runs the DB lookup for blank-URI songs (one batched query, already cheap), and runs the **YouTube match only for `startSong`**. Other unmapped catalog songs keep their `catalog://` URI for now.
  - `hydrateRemainingInBackground(queue, startIndex)`: runs after `enginePlayer.play()`, with `Semaphore(3)` parallelism, **ordered by distance from the start index** (next 1, next 2, previous 1, …), so the next song is always matched first.
- When a background match finishes, replace that `MediaItem` with `player.replaceMediaItem(i, …)` on Main in batches of 25 or more. Never touch the current item.
- **Safety net:** if playback reaches an item that is still unmatched, the existing dispatch path matches it on demand. `MusicService`'s `NextStreamPrewarmer` should also call the resolver when the *next* item is a catalog URI, so it is matched and prewarmed before the transition.
- Deduplicate concurrent matches per `spotifyId` with a `KeyedMutex`, the same pattern the extractor uses.
- **Win:** turns an O(N searches) wait into O(1).

### 1.2 Start the proxy early on a port Ktor binds itself
**Files:** `data/stream/CloudStreamProxy.kt`, `data/service/MusicService.kt`

- Let Ktor bind the port: `embeddedServer(CIO, port = 0, host = "127.0.0.1")`, then read `engine.resolvedConnectors().first().port`. This removes the `ServerSocket(0)` race.
- Add `private val ready = CompletableDeferred<Int>()`, completed with the port after `start(wait = false)`. `awaitReady` becomes `withTimeoutOrNull(t) { ready.await() } != null`. `stop()` swaps in a new deferred.
- Call `youTubeStreamProxy.startIfNeeded()` in `MusicService.onCreate` (already off the UI thread via `proxyScope`), and **also** when the search screen first becomes visible, because search → tap can happen before the service exists. Ktor CIO class loading is the main part of the cold cost; the baseline profile (`baselineprofile/`) should also include a proxy request so those classes are AOT-compiled.
- **Win:** removes [B] from the first play of every process.

### 1.3 Make the network-policy interceptor non-blocking
**Files:** `data/network/NetworkAccessPolicy.kt`, `di/YouTubeNetworkModule.kt`

- In `NetworkAccessPolicy`, hold `StateFlow`s created with `stateIn(appScope, SharingStarted.Eagerly, <safe default>)` for offline mode, lyrics integration and enrichment. Add `fun decisionNow(purpose): NetworkDecision` that reads `.value`, and keep the suspend `getDecision` for existing callers.
- The safe default for offline mode is `false`. The very first request could run before DataStore is read, so `decisionNow` should fall back to the blocking read only while the flow has not emitted yet. This preserves offline-mode correctness.
- **Win:** removes a `runBlocking` and up to three DataStore reads from every NewPipe request and every chunk.

### 1.4 Stop sending the stream to NewPipe after a network blip
**Files:** `data/youtube/YouTubeStreamExtractor.kt`, `data/stream/CloudStreamProxy.kt`, `data/stream/YouTubeStreamProxy.kt`

- Introduce `enum class InvalidationReason { URL_REJECTED /*403/410/416-on-known-clen*/, TRANSPORT /*IOException, early EOF*/, DECODER }`.
- `invalidateStream(id, reason)`:
  - `URL_REJECTED` → drop the manifest and set `directCooldown` (current behaviour).
  - `TRANSPORT` → **keep the manifest**, and retry the same signed URL with backoff. A socket reset does not make a signed URL invalid.
  - `DECODER` → keep the current `useCompatibleRendition` behaviour.
- `openUpstream` already knows the HTTP status, so pass it through. `serveChunked`'s `UpstreamReadException` is `TRANSPORT`.
- **Win:** mid-song recoveries and the retry of a first chunk stay on the fast path, instead of a watch-page extraction that takes seconds.

### 1.5 Persist the InnerTube client version
**Files:** `data/youtube/InnerTubeClient.kt`, plus a small DataStore key

- On success, store `clientVersion` with a timestamp, and use it on cold start if it is less than 7 days old.
- Keep a compiled-in fallback constant (the current `1.2026MMDD.xx.xx`) for the very first install.
- If a request with a cached version returns `400`, the existing code already clears `version`. It then refetches the homepage **once**, in the background, with single-flight.
- Also persist NewPipe's visitor data if the helper exposes it. If it does not, record this as a Phase 5 item.
- **Win:** the first search of every session no longer downloads the YouTube Music homepage.

### 1.6 Share the app connection pool with Coil, and warm it
**Files:** `di/AppModule.kt`

- Inject the app's base `OkHttpClient` and give Coil `base.newBuilder()`. This shares the connection pool and dispatcher; keep the app interceptors, since artwork hosts accept the app UA.
- On app start, send one idle-priority `HEAD` to `https://lh3.googleusercontent.com/` and `https://i.ytimg.com/` so the first cover finds a TLS 1.3 connection that is already open. Skip this on metered networks when Data Saver is on.

### 1.7 LoadControl startup threshold
**File:** `data/service/player/DualPlayerEngine.kt` (`baseLoadControlBufferProfileFor`)

- Change `bufferForPlaybackMs` from 500 to **250** on both profiles, and keep `bufferForPlaybackAfterRebufferMs = 1_000`. At 160 kbps that is about 5 KB of media.
- Expect only a small gain (tens of ms). The byte arrival rate, not this threshold, decides startup on most networks. Revert if the Phase 0 rebuffer-in-first-10-s rate rises by more than 1 percentage point.

---

## Phase 2: Make YouTube reopens cheap
**File:** `data/service/player/DualPlayerEngine.kt`

### 2.1 Fast path in `resolveDataSpec` for YouTube
- Keep a `@Volatile` map `videoId → Uri` with a 15 s TTL, holding the last proxy URL or local file URI. Container probing and the extractor's second open within the same start then return immediately, without a Room query or readiness check.
- The map is invalidated when `localSourcesByVideoId` changes (`observeDownloadedSources`), so a new download still wins, and on any `onPlayerError` for that ID.

### 2.2 Replace the offline check in `resolveYouTubeUriAsync`
- Use the in-memory `localSourcesByVideoId` map (already maintained from `cloudSongDao.observeDownloads()`) **before** falling back to `cloudSongDao.getDownloadsByVideoId`. The map is authoritative after its first emission, so the DB is only queried before that.

---

## Phase 3: Isolate playback extraction from background work
**Files:** `data/youtube/NewPipeExecution.kt`, `YouTubeStreamExtractor.kt`, `YouTubeMusicApiService.kt`

- Add priority lanes instead of a larger shared pool:
  - `NewPipeExecution.run(lane = Lane.PLAYBACK) { … }` uses `Dispatchers.IO.limitedParallelism(3)`, reserved for manifest extraction for a tap or the next song.
  - `Lane.BACKGROUND` uses `limitedParallelism(3)` for search fallback, related songs, and speculative prewarm of search results.
- **Speculative prewarms use `BACKGROUND`, and are promoted when tapped.** If a `BACKGROUND` extraction for the same ID is already running when the user taps, the tap joins it through the existing `KeyedMutex` in `streamManifest`. It does not start a second extraction, and it does not wait for a lane slot.
- `relatedSongs` stays behind its `Semaphore(1)` and heap check, on `BACKGROUND`.
- **Win:** a burst of related-song or search-fallback parses can no longer take every thread while the user is waiting on a tap.

---

## Phase 4: Speculation, starting work before the tap

### 4.1 Central `StreamPrewarmScheduler`
**New:** `data/stream/StreamPrewarmScheduler.kt`. **Callers:** search VM, `MusicService`, list screens.

A single owner for all speculative work, so it stays bounded and polite:

| Priority | Trigger | Work |
| --- | --- | --- |
| P0 | Tap (already the critical path) | Not scheduled here; the tap cancels lower-priority work for other IDs if the lanes are full |
| P1 | Touch-down on a song row (§4.2) | Manifest, plus the first 256 KB of head bytes |
| P2 | Next queue item (the existing `NextStreamPrewarmer`) | Manifest plus head bytes, and player-tier artwork |
| P3 | Top **2** song results of a finished search (SONGS/ALL) | Manifest only on metered networks; manifest plus head bytes on unmetered |
| P4 | Song rows visible for ≥ 600 ms after scrolling stops | Manifest only, unmetered only, at most 3 |

Budgets:
- At most 2 concurrent speculative jobs and at most 20 speculative manifests per rolling minute.
- **Pause all speculation for 10 minutes after any `429` or `ReCaptchaException`**, because over-prefetching is the fastest way to get the app's traffic challenged.
- Respect Data Saver and `ConnectivityManager.isActiveNetworkMetered`.
- Cancel P3/P4 jobs when the search query changes or the screen leaves composition.

This replaces v1's "fire-and-forget prewarm of the top 3 in `searchItems`". The repository should not own UI-driven speculation, and the unbounded fire-and-forget pattern is what triggers rate limits.

### 4.2 Touch-down prewarm (new)
**Files:** the song-row composables used by search, playlists and albums

- In the row's `pointerInput` / `Modifier.pointerInteropFilter` equivalent (or `interactionSource` `PressInteraction.Press`), call `scheduler.onPress(videoId)` (P1). A press becomes an `onClick` about 80–150 ms later, and that head start comes straight off [C].
- `PressInteraction.Cancel` (scroll began) cancels the job **only if it has not started network I/O yet**. A finished manifest is harmless and is kept.

### 4.3 Artwork follows speculation
- Every P1 and P2 job also enqueues a Coil prefetch of the **player-tier (1024)** cover into the disk cache (`memoryCachePolicy(WRITE_ONLY)` for P2, `ENABLED` for P1), so the full player opens with an HD cover that is already decoded or cached on disk.

---

## Phase 5: Head cache and stream-before-resolve (the structural win)

### 5.1 Why this is safe
A given `(videoId, itag)` refers to immutable bytes, and `clen` identifies the exact file. Signed URLs expire, but **the bytes they point to do not change**. Cached head bytes keyed by `videoId:itag:clen` therefore stay valid indefinitely, and only the URL needed to fetch the *rest* of the song has to be refreshed.

### 5.2 Components
**New:** `data/stream/RenditionMemo.kt`, `data/stream/StreamHeadCache.kt`. **Modified:** `YouTubeStreamProxy.kt`, `CloudStreamProxy.kt`.

- **`RenditionMemo`**: persists `videoId → (itag, mime, clen, bitrate, updatedAt)` in a small Room table (or a 2,000-entry LRU file), written whenever `resolveStreamUrl` selects a rendition. This also replaces the in-RAM `renditionKeys`, so the same rendition is chosen after a process restart, which keeps byte ranges consistent.
- **`StreamHeadCache`**:
  - L1 (RAM): LRU with 8 entries × 256 KB (about 2 MB), registered with `HeapPressure` so it clears on trim.
  - L2 (disk): `cacheDir/stream_head/`, one file per `videoId_itag_clen`, holding the first **512 KB** (about 25 s of Opus at 160 kbps), under a 96 MB LRU, which is about 190 songs. Written with atomic rename, so a partially written file is never served.
  - Filled by (a) the first chunk of every normal play, teed as it streams (no extra request), and (b) P1/P2 prewarm fetching `bytes=0-262143`.

### 5.3 Serving path in `CloudStreamProxy.serveChunked`
For a request of `bytes=from-…`, where `from` is inside the cached head and a memo exists for the ID:

1. **Before any network work,** answer immediately with the status and headers from the memo (`Content-Type = memo.mime`, `Content-Length` / `Content-Range` from `memo.clen`), then write the head bytes from `from` onward, straight from RAM or disk.
2. **In parallel,** as soon as the request arrives, resolve the manifest through the extractor and open the upstream chunk at `headEnd`, using the **memo's itag**.
3. When the head runs out, continue from the already-opened upstream. If the upstream is not ready yet, the writer suspends, which ExoPlayer sees as normal network slowness, not an error.
4. **Mismatch guard:** if the resolved manifest no longer contains `memo.itag` with `memo.clen`, abort the response by closing the socket. ExoPlayer retries the open, the proxy marks the memo stale, and the retry takes the normal path. This should be rare (YouTube re-encodes), costs one reopen, and never splices two different files.
5. Head bytes the player skips past (a seek beyond the head) fall back to the normal path.

**Effect:** any previously played or prewarmed song starts from local bytes in a few milliseconds, while the 300–1,500 ms manifest resolution and the CDN handshake happen **behind about 25 s of already-available audio**. This removes [C] and [D] from the critical path for the known and predicted cases, which is where v2 moves clearly past Musify.

### 5.4 Two-stage chunking (v1 item, re-justified)
- Make the first upstream range after the head (or after byte 0 on a cold miss) **1 MiB** instead of 10 MiB, then continue with 10 MiB chunks.
- The reason is not TTFB, which googlevideo does not scale with range size. It is that a quick skip or seek abandons a 1 MiB in-flight response instead of a 10 MiB one, which frees bandwidth for the next song. It also puts a second request early in playback, so a throttled first response is noticed and replaced sooner.

### 5.5 Optional later: full replay cache
- Extend L2 to whole files for songs played to at least 50% (a separate 512 MB LRU, with the limit in settings). Replays are then completely offline. This should reuse `PlaybackBandwidthGate` rules, so filling the cache never competes with the current stream. It lives inside the proxy for the same rendition-safety reason as the head cache. **Do not** use ExoPlayer `CacheDataSource` above the proxy, because the rendition is chosen below it.

---

## Phase 6: Direct field-masked player request (experimental, behind a kill switch)
**Files:** `data/youtube/InnerTubeClient.kt`, `YouTubeStreamExtractor.kt`, `remote-config/`

- Add `directPlayerRequest(id, client)`, a direct OkHttp POST to `https://www.youtube.com/youtubei/v1/player?prettyPrint=false`.
  - Header: `X-Goog-FieldMask: playabilityStatus(status),streamingData(adaptiveFormats(itag,url,mimeType,bitrate,contentLength,type),expiresInSeconds),videoDetails(videoId)`.
  - Client constants (UA, clientVersion, visitor data) are **read from NewPipe's `YoutubeParsingHelper` getters at runtime**, never copied into the app, so they stay aligned with NewPipe upgrades.
- Parse the response with the existing `InnerTubeParser.directStreams`, which keeps the `n`-parameter, OTF, host and expiry filters.
- Race this against the current VISIONOS path using the existing `resolveWithHedgedFallback`, with NewPipe full extraction as the third tier. The first usable result wins, and the losers are cancelled.
- Optionally add an `ANDROID_VR` client as a second direct candidate, again only if it can be built from NewPipe-maintained constants.
- **Kill switch:** a flag in `remote-config/` plus a developer setting. Enable it only if Phase 0 traces show a p50 improvement of at least 100 ms without raising the fallback rate. Realistic gain: tens to low hundreds of ms on slow networks, mostly from a smaller payload and faster parsing.

---

## Phase 7: Instant, flicker-free cover art

### 7.1 Show the list thumbnail instantly in the player
**Files:** `presentation/components/OptimizedAlbumArt.kt`, `SmartImage.kt`, new `data/image/ArtworkKeys.kt`

- Add `ArtworkKeys.memoryKey(url, tier)`: one deterministic key per logical cover and tier (`tier ∈ {THUMB, GRID, PLAYER}`). Both `SmartImage` and `OptimizedAlbumArt` set it explicitly as the `memoryCacheKey`, replacing today's `"${url}_${w}x${h}"`, which differs by exact pixel size and so rarely matches.
- In `OptimizedAlbumArt`, set `placeholderMemoryCacheKey` to the first hit among `memoryKey(url, GRID)` and `memoryKey(url, THUMB)`, checked against `imageLoader.memoryCache?.get(...)`. Never use the request's own key, which is the current bug.
- Result: on the first frame, the player shows the already-decoded list bitmap, then crossfades (reduce from 350 to 200 ms) to the HD bitmap.

### 7.2 Three shared tiers, with one HD download
**Files:** `data/metadata/ArtworkUrls.kt`, `data/service/CoilBitmapLoader.kt`, the widget code in `MusicService`

- Change `forDisplay` buckets to **≤ 300 → 256**, **≤ 600 → 544**, **otherwise → 1024**. Requests larger than 1024 are also mapped to the 1024 tier, instead of today's "keep the original URL". That original can be 1400–3000 px, and it produces a *fourth* separate disk entry.
- The notification (`CoilBitmapLoader`, already 1024), the widget and the full player all request 1024 through the same `ArtworkKeys` tier. Playing a song then downloads the HD cover **once**.
- Keep `ArtworkUrls.DEFAULT_SIZE = 1400` for **embedding into downloads** only (`SongDownloadManager`).

### 7.3 Request larger Google variants for the player tier
- Today `forDisplay` only ever downsizes, so a YouTube Music search result with `=w120-h120-l90-rj` art is shown in the full player as a 120 px image scaled up.
- For Google hosts (`googleusercontent.com`, `ggpht.com`) only, allow the PLAYER tier to request `=w1024-h1024…`. Google serves the original size, capped at the source resolution, so this never invents pixels. It only stops the app from asking for a tiny variant. Leave Apple and Deezer unchanged.

### 7.4 YouTube video-frame artwork
**File:** `ArtworkUrls.kt`

- Rewrite `i.ytimg.com/vi/<id>/…` and `img.youtube.com/vi/<id>/…` to `https://i.ytimg.com/vi_webp/<id>/<name>.webp`:
  - THUMB/GRID tier: `mqdefault` (320×180, no letterbox).
  - PLAYER tier: `maxresdefault`, with a Coil `error()` chain falling back to `sddefault` then `hqdefault`.
- Centre-crop to a square.
- Keep treating these as low-quality (`isLowQuality`), so `SongMetadataGatherer` still upgrades them to a real album cover in the background.

### 7.5 Metadata gatherer on the shared pool
**File:** `data/metadata/SongMetadataGatherer.kt`

- Replace `HttpURLConnection` in `getJsonWithCode` with the injected app `OkHttpClient`, so Deezer and iTunes lookups reuse HTTP/2 connections and are cancelled with their coroutine.
- In `PlaybackDispatchStateHolder`, apply `metadataGatherer.applyCached` before building the start `MediaItem`, so a known upgraded cover is used on the first frame. The network upgrade for new streamed tracks stays in the background and **after** `STATE_READY`, so it never competes with audio startup.

### 7.6 Hardware bitmaps: opt in after an audit
- Audit the six `allowHardware(false)` call sites. Rows with no palette, blur or `toBitmap` consumer get `allowHardware = true`. Palette and `ColorSchemeProcessor` inputs stay software.
- Leave the `SmartImage` default at `false` until the audit is complete, because a hardware bitmap reaching `Palette` crashes.

### 7.7 Local artwork overhead (v1, kept)
**Files:** `utils/LocalArtworkUri.kt`, `utils/AlbumArtUtils.kt`

- `isLikelyLocalMedia` returns `false` for `youtube://`, `spotify://`, `applemusic://`, `deezer://` and `http(s)://`.
- Call `setLastModified(now)` only when the file is more than 24 h stale.
- Raise `artworkWorkPermits` from 2 to 4 **only** on devices where `!isLowRamDevice`. Keep 2 on low-RAM devices to protect the existing OOM safeguards.

---

## 8. Rollout order and risk

| Order | Phase | Risk | Expected effect | Kill switch |
| --- | --- | --- | --- | --- |
| 1 | 0 Measurement | none | Enables all verification | n/a |
| 2 | 1.1 Start-song-only matching | medium (queue mutation) | Largest: catalog playlists start in about 1 match instead of N | Pref `catalog_lazy_match` |
| 3 | 1.2–1.6 | low | Removes [B], interceptor blocking, the homepage fetch, and cold image handshakes | none needed |
| 4 | 1.4 Invalidation reasons | low | Prevents 2-minute slow-path penalties | none needed |
| 5 | 7.1–7.4 Artwork | low | Instant player cover, one HD download | Pref `artwork_tiers_v2` |
| 6 | 2, 3 | low | Cheaper reopens, lane isolation | none needed |
| 7 | 4 Speculation | medium (rate limits, data use) | Cold → about 100 ms faster; top-N → near-instant | Remote flag plus automatic 429 back-off |
| 8 | 5 Head cache | medium-high (new serving path) | Known and predicted plays → local-speed start | Remote flag; memo mismatch self-heals |
| 9 | 1.7, 5.4 | low | Tens of ms | Revert constants |
| 10 | 6 Direct player | high (external API drift) | Tens to hundreds of ms | Remote flag, off by default |

Cross-cutting risks:
- **Rate limiting / reCAPTCHA.** Mitigated by the Phase 4 budgets, the automatic 10-minute back-off, and joining an in-flight extraction instead of duplicating it.
- **Mobile data.** Speculative head bytes are allowed only on unmetered networks. On metered networks, speculation fetches the manifest only (about 5–30 KB).
- **Memory.** L1 is capped at about 2 MB and registered with `HeapPressure`. Head writes stream to disk and are never held whole.
- **Correctness of byte ranges.** Byte ranges are never spliced across renditions. The memo mismatch guard closes the socket instead of sending wrong bytes.

---

## 9. Tests

Unit tests (`./gradlew :app:testDebugUnitTest`, using filtered runs as in the audit doc if unrelated tests still fail to compile):

- `PlaybackDispatchStateHolderTest`: with a 50-track catalog queue, `play()` is called after exactly **one** resolver call, and background matching runs in order of distance from the start song.
- `CloudStreamProxyTest`:
  - readiness `CompletableDeferred` works without polling, and after `stop()`/`start()`;
  - a `TRANSPORT` failure does not set the direct cooldown, while `URL_REJECTED` does;
  - the first post-head range is 1 MiB, and later ranges are 10 MiB.
- `StreamHeadCacheTest`:
  - LRU eviction;
  - atomic writes (a partially written file is never served);
  - the key includes `clen`;
  - a `HeapPressure` trim clears L1.
- `StreamBeforeResolveTest` (fake upstream):
  - head bytes are served before the manifest resolves;
  - playback continues seamlessly at `headEnd`;
  - on a memo mismatch the response is aborted and the retry takes the normal path;
  - a seek beyond the head skips the head cache.
- `StreamPrewarmSchedulerTest`: priority ordering, the concurrency and per-minute budgets, 429 back-off, metered-network policy, and cancellation on query change.
- `NewPipeExecutionTest`: `BACKGROUND` saturation does not delay a `PLAYBACK` job, and a tap joins an in-flight background extraction.
- `ArtworkUrlsTest`: the 256/544/1024 buckets, the >1024 → 1024 mapping, Google player-tier enlargement, no changes to Apple or Deezer, and `vi_webp` rewriting with its fallback chain.
- `ArtworkKeysTest` / `OptimizedAlbumArtTest`: the placeholder key resolves to the list tier, not to the request itself.
- `NetworkAccessPolicyTest`: `decisionNow` matches `getDecision` after the first emission, and uses the blocking read before it.

On-device checks, required before enabling each remote flag:
- Collect Phase 0 traces for every row of the §1 table, at least 30 trials each, on Wi-Fi and throttled 4G, reporting p50 and p95.
- Check the rebuffer rate in the first 10 s, speculative bytes that were never played, and head-cache hit ratio.
- Check that no 429s were caused by speculation.
- Artwork: confirm the first frame of the full player is never blank when coming from a list, and count exactly one HD request per new song (OkHttp `EventListener` counters).

---

## 10. Implementation status (2026-09-27)

Implemented on branch `claude/magical-mccarthy-gv9uau`.

**Build status:** this environment has no Android SDK, so **no Gradle build or full test run was possible**. Instead, every platform-independent file that changed was compiled with Kotlin 2.4 against the project's real library versions (Ktor 3.5, OkHttp 5.4, coroutines 1.11), with small stubs for Android, Hilt, Timber and NewPipe. The listed unit tests were run that way with JUnit 6. Compose, Hilt wiring and Android-only files were reviewed by hand but not compiled. **Run `./gradlew :app:testDebugUnitTest` and a device smoke test before merging.**

| Plan item | Status | Where |
| --- | --- | --- |
| 0 PlaybackTrace | Done: one `StreamingLatency tap_to_audio_ms=… stages=dispatch,manifest(provider),first_bytes(head/network),audio` line per play | `data/diagnostics/PlaybackTrace.kt` |
| 1.1 Start-song-only catalog matching | Done. The player now resolves `spotify://`, `applemusic://` and `deezer://` on open; the next-song prewarmer matches ahead; Cast keeps full matching | `data/accounts/CatalogPlaybackResolver.kt`, `DualPlayerEngine`, `PlaybackDispatchStateHolder` |
| 1.2 Proxy port and readiness | Done. The engine binds port 0 itself; readiness is a `CompletableDeferred`; the proxy starts in `DualPlayerEngine.initialize()` | `CloudStreamProxy` |
| 1.3 Non-blocking network policy | Done for both the YouTube and the app interceptors (`decisionNow`, falling back to the blocking read until the first value is known) | `NetworkAccessPolicy`, `YouTubeNetworkModule`, `AppModule` |
| 1.4 Invalidation reasons | Done. 401/403/410 re-resolve with the cooldown; 429 reports rate limiting; other failures retry the same URL, and re-resolve without the penalty only after they repeat | `YouTubeStreamProxy.onUpstreamFailure`, `StreamRetryPolicy.urlRejected` |
| 1.5 Persisted client version | Done, **without** a compiled-in fallback version (an invented version could make the first search fail); a rejected saved version is cleared and retried once | `InnerTubeVersionStore` |
| 1.6 Coil connection pool | Done. Also raised the shared pool's keep-alive from 30 s to 5 min so connections warmed by prewarming survive until the tap. The HEAD warm-up pings were not added | `AppModule` |
| 1.7 `bufferForPlaybackMs` 250 | Done | `DualPlayerEngine` |
| 2.2 In-memory download lookup on reopen | Done. The separate 15 s URI cache (2.1) was unnecessary once the DB query and manifest wait were gone | `DualPlayerEngine.resolveYouTubeUriAsync` |
| 3 Extraction lanes | Done: PLAYBACK and BACKGROUND lanes, 3 threads each; related songs, collection search and speculation run in BACKGROUND | `NewPipeExecution` |
| 4 Speculation | Done: press prewarm (256 KB head) and top 2 search results (128 KB head, unmetered only), 2 concurrent jobs, 20 per minute, 10 min back-off after a 429. The "rows visible ≥ 600 ms" trigger (P4) was not added | `StreamPrewarmScheduler`, `PressPrewarm.kt`, `SearchStateHolder` |
| 4.3 Artwork on press | Done (1024 px variant into the disk cache) | `PlayerViewModel.onSongPressed` |
| 5.1–5.4 Head cache, stream-before-resolve, 1 MiB first range | Done. The "rendition memo" is the cache's file names (`<id>.<itag>.<clen>.<bitrate>.<ext>`), so no Room table was needed. Heads fill from normal plays at no extra cost and from prewarm; the next song gets a 256 KB head | `StreamHeadCache`, `CloudStreamProxy.serveChunked`, `YouTubeStreamProxy` |
| 5.5 Full replay cache | Not done (optional) | |
| 6 Field-masked `/player` | **Done 2026-09-28**, without a remote flag: the mask switches itself off for the session after a 400 that an unmasked retry fixes, and the request is still raced against NewPipe. Visitor data is now reused instead of fetched per lookup. See `docs/musify-deep-dive-speedups.md` | `data/youtube/VisionOsPlayer.kt` |
| 7.1 Placeholder from list tier | Done | `OptimizedAlbumArt.smallerArtworkKeyCandidates` |
| 7.2 Tiers | **No change needed**: at the default "High" quality (800 px) the player, notification and widget already share the 1024 px variant. Mapping above 1024 px was left alone so the "Original" quality setting is still respected | |
| 7.3 Google player-tier enlargement | **No change needed**: YouTube Music thumbnails are already upgraded to 1400 px when search results are parsed | |
| 7.4 YouTube video frames | Done for list sizes (≤ 256 px): `mqdefault.jpg`, not `vi_webp`, because not every video has WebP variants and a 404 would show no cover | `ArtworkUrls` |
| 7.5 Metadata gatherer on OkHttp | Done | `SongMetadataGatherer` |
| 7.6 Hardware bitmaps | Not changed (needs the per-call-site audit) | |
| 7.7 Local artwork | Timestamp throttle done. Permits stay at 2 (a deliberate OOM safeguard); `isLikelyLocalMedia` was unchanged, since it had no speed impact | `AlbumArtUtils.touchIfStale` |

Tests added or updated (all pass in the standalone harness):
- New: `StreamHeadCacheTest` (7), `HeadCacheProxyTest` (5, end to end through the real Ktor proxy and a fake CDN), `StreamPrewarmSchedulerTest` (6), `CatalogPlaybackResolverTest` (4), `PlaybackTraceTest` (2).
- Updated: `ArtworkUrlsTest`, `NewPipeExecutionTest` (lane isolation), `StreamReliabilityTest`.
- Updated but not run here (Compose/Coil or Android): `OptimizedAlbumArtTest`, `AlbumArtUtilsTest`, `LoadControlBufferProfileTest`, and the `PlayerViewModelTest` constructor.

In the harness end-to-end test, a song with a cached head delivered its first byte in about 6 ms while its URL took 1.5 s to resolve. That is a loopback measurement, not device latency.
