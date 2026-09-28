# Musify deep dive and the next round of speed-ups

Written 2026-09-28. Musify revision: `5b98d3a3cddd3fdb16ea6cc14d59d667e82cccd2` (unchanged since the audit in `docs/musify-playback-artwork-audit.md`, so this round goes deeper rather than wider). NewPipeExtractor `v0.26.5` and Media3 `1.10.1` sources were read to confirm the behaviour described here. No Musify code was copied.

Earlier rounds: `docs/musify-playback-artwork-audit.md`, `docs/ultra-fast-streaming-and-artwork-plan.md`.

---

## 1. How Musify gets its speed

Musify is Flutter + just_audio (ExoPlayer on Android) + a vendored `youtube_explode_dart`.

### First play
`playSong` (`lib/services/audio_service.dart:2280`) pauses the old song, resolves a URL, builds the source and calls `setAudioSource` + `play()`.

1. **URL cache on disk.** `fetchSongStreamUrl` (`common_services.dart:749`) keeps the signed URL in Hive for **3 hours** (`:761`). After 1 hour (`:104`) it re-validates with a `HEAD` request first (`_validateCachedUrl`, `:152`), which adds a round trip. A song played in the last few hours needs no manifest at all, even after a restart.
2. **Cold manifest.** `getManifest` (`packages/youtube_explode_dart/.../stream_client.dart:86`) defaults to `requireWatchPage = true`, so each cold lookup is: **the full watch page HTML** (`WatchPage.get`, `:294`) → a VISIONOS `/player` POST → a **`HEAD` of the first stream URL** to check for 403 (`:137`). That is three sequential requests, one of them a large HTML page.
3. **SponsorBlock on the critical path.** With SponsorBlock on (the default), `_buildBufferedAudioSource` awaits the segment lookup before playback (`audio_service.dart:2784`).
4. **Optimistic UI.** The new media item and a "loading" state are published before any network work (`_emitOptimisticLoadingState`).

### Streaming
`BufferedStreamAudioSource` (`lib/services/stream_buffer_service.dart`) downloads the **whole song into a file** with ranged requests (~10 MB each, `youtube_http_client.dart:206`) while the player reads from the file, so the buffer runs far ahead of playback. Costs: reads poll every **50 ms** (`:39`) for bytes not yet written; a seek past the downloaded part waits for the linear download; the previous song's download is discarded on every change (`:179`). Start threshold is 500 ms of audio (`audio_service.dart:53`).

### Pause / play
Plain `pause()` / `play()` (`audio_service.dart:2125`, `:2155`). What makes resume robust is that the buffer file keeps filling during a pause, so after a long pause the song is usually on disk.

### Next song
After a successful start, and again 2 s later (`:2512`), `_preloadUpcomingSongs` (`:1405`) resolves URLs for the **next 3** songs (`:110`), two at a time (`:111`). Manifests only, no audio bytes.

### Search
Search runs on submit only; suggestions are debounced 300 ms (`lib/screens/search_page.dart:297`). Songs, artists, albums and playlists are requested in parallel and published independently. Song search **scrapes the `/results?search_query=` HTML page** (`search_page.dart:44` in the package), and there is no result cache.

### Cover art
Lists use YouTube's `default.jpg` (120×90, `thumbnail_set.dart:17`); the player uses `maxresdefault.jpg` via `CachedNetworkImage` with a **spinner** placeholder (`song_artwork.dart:65`), so the player cover is blank until the large image arrives. `ArtworkProvider` caches provider objects in an unbounded map (`artwork_provider.dart:32`).

### Summary

| Stage | Musify | PixelPlayer before this round |
| --- | --- | --- |
| Cold manifest | watch page + player + HEAD (3 sequential, one large) | VISIONOS direct (visitor + player, 2 hosts) hedged against NewPipe after 200 ms |
| Known song | 3 h disk URL cache (+HEAD after 1 h) | 512 KB head cache plays while the manifest resolves |
| Next song | 3 manifests, 2 concurrent | 1 manifest + 256 KB head |
| Streaming | whole-file ranged download to disk | ranged chunks through the local proxy (1 MiB, then 10 MiB) |
| Long-pause resume | file keeps filling | 7 s rewind **discarded the buffer** (see §2.3) |
| Search | submit only, HTML scrape, no cache | as you type, InnerTube JSON hedged with NewPipe, 5 min cache |
| Player cover | spinner, then maxres | list thumbnail at once, then 1024 px |

PixelPlayer was already ahead on cold manifests, known songs, search and covers. Musify was ahead on lookahead depth and long-pause resume. Reading the code more closely also turned up a bug on PixelPlayer's side that hurt cold starts more than anything else.

---

## 2. What was still slow

### 2.1 The hedged manifest resolver discarded its own winner (bug)
`resolveWithHedgedFallback` starts NewPipe's full extraction 200 ms after the VISIONOS request if that has not answered. When one branch wins, the `finally` block cancels the other. The loser's `attempt()` caught that `CancellationException` and called `this@coroutineScope.cancel(...)`, which **cancelled the whole scope**. So the winning manifest was dropped and the caller got a `CancellationException`.

This happens whenever the direct request takes longer than 200 ms, which is common on a phone: it was two sequential requests, often on new connections. From there:
- `resolveDataSpec` throws.
- Media3 wraps the exception in `UnexpectedLoaderException`, which `DefaultLoadErrorHandlingPolicy` never retries, so playback fails with IO error 2000.
- `onPlayerError` waits 300 ms and calls `invalidateStream()`. That puts the song on a **2-minute direct-path cooldown**, so the retry runs NewPipe's full extraction (1–3 s).
- Prewarms failed the same way, silently.

The existing test `slow direct request no longer adds its timeout before fallback` was already failing because of this. The search-side `hedgedLookup` had the correct guard (`if (currentCoroutineContext().isActive)`); the manifest resolver did not.

### 2.2 Two sequential requests on two hosts for every cold manifest
NewPipe's `YoutubeStreamHelper.getVisionOsPlayerResponse` (v0.26.5, `YoutubeStreamHelper.java:232`) POSTs `www.youtube.com/youtubei/v1/visitor_id` on **every call**, then POSTs `/player` to `youtubei.googleapis.com`. Visitor data is a long-lived session token; a real client keeps it. The full player response is ~70 KB (15 KB gzipped): video formats, captions, tracking and config that the audio path never reads. The app then parsed it three times (nanojson, back to a string, then `org.json`).

### 2.3 Long-pause resume went back to the network
`SmartResumePolicy` rewinds 7 s after a pause of 30 s or longer. Media3's default back buffer is **0** (`DefaultLoadControl.DEFAULT_BACK_BUFFER_DURATION_MS`), so played samples are discarded every loop. The rewind could not seek inside the buffer (`ProgressiveMediaPeriod.seekToUs` → `seekInsideBufferUs` fails), so it cancelled the load and **re-requested the stream**.

### 2.4 Other gaps
- Only the next song was prepared; a double skip landed on a cold manifest.
- Stream URLs are bound to the client IP. After a Wi-Fi ↔ mobile switch, every cached manifest returned 403, and each 403 also put its song on the 2-minute slow path.
- The first search and the first tap of a session paid DNS, TCP and TLS.
- The next song's full-size cover was not prefetched, so it downloaded at the transition.

---

## 3. Changes

| # | Change | Files |
| --- | --- | --- |
| 1 | **Hedging bug fixed.** A loser cancelled by the winner no longer cancels the scope; a provider's own cancellation still ends the lookup. | `data/youtube/HedgedStreamResolver.kt` |
| 2 | **One request per cold manifest.** New `VisionOsPlayer` builds the same body NewPipe builds (`ofVisionOsClient()` + `prepareJsonBuilder`, so client constants still follow NewPipe). Visitor data is fetched once, **from the player's own host** (verified to work), kept in memory and on disk, renewed after 12 h, and renewed plus retried once if YouTube answers `LOGIN_REQUIRED`. | `data/youtube/VisionOsPlayer.kt`, `InnerTubeClient.kt`, `InnerTubeVersionStore.kt` |
| 3 | **Field-masked player response**, parsed once. 15 KB → ~3.4 KB on the wire, and ~25 KB of JSON instead of 70 KB. If YouTube rejects the mask (400) and the same request works without it, the mask is dropped for the session. | `VisionOsPlayer.kt` |
| 4 | **Connection warm-up.** When the engine starts, the manifest host's connection is opened (by renewing visitor data). When Search opens, both the search host and the manifest host are opened. Each is skipped while its connection is still pooled (4 min, under OkHttp's 5 min keep-alive) and while YouTube is rate limiting. | `DualPlayerEngine.initialize`, `StreamPrewarmScheduler.onSearchOpened`, `InnerTubeClient.warmUpSearch/warmUpPlayback`, `SearchScreen` |
| 5 | **Instant long-pause resume.** A 10 s back buffer (the rewind is 7 s) with `retainBackBufferFromKeyframe` (every audio sample is a keyframe). This is about 200 KB for Opus at 160 kbps. Media3 clears the back buffer by itself if it ever blocks loading. The load-control profile moved to its own file so it can be tested. | `LoadControlBufferProfile.kt`, `DualPlayerEngine.buildAdaptiveLoadControl`, `PlaybackContracts.kt` |
| 6 | **Lookahead of 3 songs** (Musify parity). After the next song (manifest plus 256 KB head), the two songs after it get their manifests, one at a time, in shuffle/repeat order. The existing readiness gate and cancellation are kept. | `NextStreamPrewarmer.kt`, `MusicService.prewarmNextStream/upcomingAfter`, `DualPlayerEngine.prewarmNextStream` |
| 7 | **Network changes.** On a new default network: pooled connections are evicted, IP-bound manifests and cooldowns are cleared, and a lookup that straddled the switch is used but not cached. | `YouTubeStreamProxy` (own `registerDefaultNetworkCallback`, so it also works with no UI), `YouTubeStreamExtractor.onNetworkChanged` |
| 8 | **Next song's cover** is prefetched into the disk cache at the player/notification size, 2.5 s after the current song settles, in the hook that already prefetches lyrics. | `PlayerViewModel` |

A `StreamingLatency` debug line `visionos_visitor=reused|fetched field_mask=true|false` shows on a device whether items 2 and 3 are active.

---

## 4. Measurements

Taken from this environment against YouTube's live InnerTube API, through an egress proxy that re-terminates TLS. These are **not phone numbers**, but the request counts and sizes are exact, and the relative timings are indicative. `www.youtube.com` and the googlevideo CDN are blocked here, so the old visitor call and byte delivery could not be timed.

Player response size for the same video:

| Request | On the wire (gzip) | JSON |
| --- | ---: | ---: |
| Full response (before) | 15,012 B | 70,438 B |
| Field-masked (now) | 3,435 B | 24,752 B |

Median manifest time over 5 videos, measured through the app's own `InnerTubeClient` + `NewPipeDownloader`:

| State | Requests | Median |
| --- | ---: | ---: |
| New connection, visitor fetched | 2 (same host) | 540 ms |
| New connection, visitor remembered (after a restart) | 1 | 457 ms |
| Warmed connection, visitor remembered (the usual case now) | 1 | **160 ms** |

The old path needed a visitor request to `www.youtube.com` plus a player request to a second host, so it was at least as slow as the first row plus another TLS handshake. On top of that, the bug in §2.1 turned most slow lookups into a failure followed by NewPipe extraction.

Other findings from the live probes:
- VISIONOS audio URLs have no `n` parameter, so the direct parser accepts them.
- They have no `ratebypass`, which is why ranged requests matter.
- They expire after 6 h.
- Without visitor data the API returns a bot check (`LOGIN_REQUIRED`). One visitor token reused across 17 player requests over about ten minutes kept returning `OK`.
- Some label-restricted videos are `UNPLAYABLE` for VISIONOS ("This video is not available"). Those fall back to NewPipe immediately, as before.

---

## 5. Tests

This environment has no Android SDK and cannot reach Google Maven or JitPack. So, as in earlier rounds, the platform-independent sources were compiled and tested in a standalone JVM harness:
- Kotlin 2.4, JDK 21.
- Real NewPipeExtractor v0.26.5 and nanojson, built from source.
- Real OkHttp 5.4, Ktor 3.5, coroutines 1.11, `org.json`, mockk.
- Small stubs for Android, Timber and Hilt.

**86 tests pass.** Before this round, 62 of 63 passed; the failure was the bug in §2.1.

New or changed tests:
- `HedgedStreamResolverTest`: direct wins while extraction is in flight (the phone case). The previously failing test now passes.
- `DirectPlayerRequestTest`, rewritten around a fake NewPipe downloader:
  - a cold lookup is visitor + masked player, on the same host;
  - later lookups send the player request only;
  - visitor data survives a restart;
  - a sign-in check renews the visitor once;
  - a fresh visitor is not retried;
  - mask rejection and mask retention;
  - visitor expiry;
  - warm-up;
  - network-change flush and straddle;
  - end to end through `YouTubeStreamExtractor`: the manifest arriving after the hedge is kept and cached. This test **fails with `JobCancellationException` without fix #1.**
- `YouTubeStreamProxyNetworkTest`: only a change of default network flushes.
- `NextStreamPrewarmerTest`: lookahead order, lookahead waits for the next song and is skipped if it fails, skip and promotion behaviour.
- `LoadControlBufferProfileTest`: the back buffer covers `SmartResumePolicy.REWIND_MS`.
- `StreamPrewarmSchedulerTest`: opening Search warms connections; nothing happens while rate limited.

`DualPlayerEngine`, `MusicService`, `PlayerViewModel`, `SearchStateHolder` and `SearchScreen` need the Android SDK and were reviewed by hand. Two constructs were compiled separately to be sure: a default argument that uses a private constant, and a bound member reference. The Media3 `Timeline` / `DefaultLoadControl` / `ProgressiveMediaPeriod` behaviour relied on was checked in the 1.10.1 source.

Run in the repository before merging:

```sh
./gradlew :app:testDebugUnitTest --tests '*HedgedStreamResolverTest' --tests '*DirectPlayerRequestTest' \
  --tests '*YouTubeStreamProxyNetworkTest' --tests '*NextStreamPrewarmerTest' \
  --tests '*LoadControlBufferProfileTest' --tests '*StreamPrewarmSchedulerTest' --tests '*SmartResumePolicyTest'
./gradlew :app:assembleDebug
```

---

## 6. On a device

With `adb logcat -v time StreamingLatency:D '*:S'`:

1. **Cold first play** from search, 30 taps: `tap_to_audio_ms`.
   - After the first play, expect `manifest(visionos)` and `visionos_visitor=reused`.
   - There should be no player error and no `manifest(newpipe)` right after it. That pair was the §2.1 failure.
2. **Pause 60 s, then play** on an online song: playback resumes 7 s earlier with no `proxy_first_bytes` line (no new request).
3. **Skip twice quickly**: the second song should also log `manifest(visionos)` with a small `manifest` stage, or no manifest stage at all if it was prepared.
4. **Switch Wi-Fi to mobile data mid-song**: no `Upstream HTTP 403` from `YouTubeProxy`, and the next song logs `manifest(visionos)`.
5. **Open Search, wait, type**: the first query's `search_ms` should match later queries.

---

## 7. Not done, and why

- **Disk-persisted signed URLs** (Musify's 3 h Hive cache). The head cache already starts known songs from local bytes, and URLs are IP-bound, so a persisted URL would often 403 after the network changes. It would only help a mid-song resume after process death, and would save about one request there. Worth adding only if device traces show that case matters.
- **Adaptive hedge delay.** Now that the direct path is one small request, a fixed 200 ms hedge will often start NewPipe just before the direct answer arrives; its calls are cancelled at once now that the bug is fixed. Setting the delay from measured `manifest` p90 is the next step once device traces exist.
- **Keeping the previous results visible while a new query loads.** This is a UX choice (results are currently cleared on purpose), not a latency fix.
