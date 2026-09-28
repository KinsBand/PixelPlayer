# PixelPlayer streaming: analysis of 6 YouTube-music apps and what was adopted

Date: 2026-09-28. Repos analysed at their latest commits (Metrolist 2026-09-23 + its InnerTubeX library 2026-09-26, SimpMusic 2026-09-26, ViTune 2026-09-27, Musify 2026-09-26, Spotube 2026-06-05, InnerTune 2025-11-13), plus yt-dlp master (2026-09-27) as the reference extractor.

## How each app gets a playable stream

| App | Stream resolution | Reliability features | Verdict |
|---|---|---|---|
| **Metrolist** | InnerTubeX: a catalogue of ~20 InnerTube clients scored by lifecycle, content support, token needs and runtime health. Fast path = **VISIONOS 0.1 on `music.youtube.com/youtubei/v1/player`** (Safari UA, no JS). Then watch-config + cipher (Zemer / EJS / QuickJS) web clients, PO tokens via WebView BotGuard. | Per-video failed-client exclusion (5 min), `ClientHealthMonitor`, cipher refresh after rejection, 15 s initial-buffer stall watchdog, network-retry backoff, SimpleCache player + download caches. | Most robust and fastest. |
| **SimpMusic** | WEB_REMIX player request for metadata, then PipePipe with local cipher → PipePipe remote decoder → BravePipe. | HEAD-checks a random stream before trusting it; three independent tiers. Sends playback-tracking pings. | Robust, but sequential full extractions are slow. |
| **ViTune** | On-device **yt-dlp** (Chaquopy/Python + yt-dlp-ejs), self-upgradable. | SimpleCache, 512 KB ranged requests, URI cache. | Most future-proof, slowest cold start, big APK. |
| **Musify** | youtube_explode with **visionOS 1.02 only** (Safari UA). | Sends the minting client's UA to googlevideo; HEAD validation of cached URLs; optional proxies. | Simple; single point of failure. |
| **Spotube** | Pluggable engines (youtube_explode / NewPipe / yt-dlp) behind a local proxy. | HEAD check before streaming, refresh on failure, **swap to another upload** of the same song, full-file cache. | Good fallbacks; Dart-side proxy. |
| **InnerTune** | IOS → TVHTML5 + Piped, sequential. | 512 KB chunks, URL cache (with an inverted expiry check). | Outdated: IOS client is now broken. |

yt-dlp itself now defaults to `visionos` + `web` (jsless: `visionos` only); `android_vr` was dropped after YouTube 403'd it on 2026-08-17. Everyone has converged on visionOS as the zero-JavaScript client.

## Where PixelPlayer stood

Already ahead of all six on transport: local Ktor proxy with 10 MiB chunks and a small first range, stall detection and same-byte resume, head cache (start before URL is resolved), network-change handling, rendition pinning, rebuffer back-off, readiness-gated prefetch, hedged manifest (visionOS direct vs NewPipe).

Gaps:
1. Only **one** zero-JS client (NewPipe's visionOS 1.02 profile). InnerTubeX notes this profile "can stall before receiving media" on clean Android sessions.
2. A URL that never sent a first byte was retried on the same URL for ~10 s × 3 before switching (~30 s of silence).
3. A 403 sent the song straight to slow full extraction for 2 minutes.
4. Full extraction was hedged at a fixed 200 ms, so it ran alongside most mobile plays (data, CPU, rate-limit risk).
5. No whole-song cache: replays and backward seeks always needed the network.

## What was implemented (best-of plus research)

- **Client ladder** (`StreamClients`, `VisionOsMusicPlayer`): NewPipe visionOS 1.02 → Metrolist's visionOS 0.1 on the YouTube Music endpoint → NewPipe full extraction. The backup reuses the stored visitor data and the response field mask; its URLs are fetched with the Safari UA that minted them (`YouTubeHttp.rememberUserAgent`).
- **Adaptive hedging** (`HedgedRace`, `StreamClientHealth`): staggered race where each attempt starts at a delay learned from the primary's own latency (p90 + 50 ms for the backup, p95 × 1.5 for full extraction, "The Tail at Scale" hedged requests), or immediately when earlier attempts fail.
- **Client health** (after InnerTubeX `ClientHealthMonitor`): 2 failures in a row demote a client, 3 put it on an exponential cooldown (1 → 30 min); offline errors never count; a network change resets it.
- **Per-video exclusion** (after Metrolist `markStreamClientFailed`): a 403 or first-byte stall excludes the client that produced the URL for that song for 5 minutes, so the retry goes to the other direct client first.
- **First-byte stall switch** (`CloudStreamProxy.onFirstByteStall`): requests before the player has a byte use a 5 s read timeout and switch source on silence.
- **Whole-song cache** (`StreamBodyCache`, 256 MB LRU): bytes teed in order while streaming, resumable partial files, served from disk with range support, used offline; the next song is prefetched whole on unmetered networks (paced by `PlaybackBandwidthGate`).

## Verification

Compiled with Kotlin 2.4.0 against the project's own dependency jars; 104 JVM tests pass (all existing streaming tests plus 5 new test classes), timing-sensitive ones 3× in a row. Not verified live: the sandbox cannot reach YouTube. DualPlayerEngine / MusicService edits were reviewed but not compiled here.

## Next ideas (not done)

- Auto-skip or Spotube-style "swap to another upload" when every client fails for a video.
- PO-token WebView provider (Metrolist's `PoTokenGenerator`) to unlock WEB_REMIX / explicit / age-gated content.
- Setting for the song-cache size and "prefetch next song on mobile data".
