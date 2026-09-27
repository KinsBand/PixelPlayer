# Streaming, mixes, fullscreen controls and offline source synchronization

Implementation target: **PixelPlayer (Kotlin/Compose/Media3)**, as confirmed during this task. The linked Spotube plugins were audited as architectural references. No Flutter runtime or Spotube plugin loader was introduced. Audit date: 2026-09-21.

## 1. Repository and architecture audit

The following revisions were cloned and inspected. These repositories do not contain three interchangeable audio resolvers.

| Repository / revision | Actual responsibility | Findings |
| --- | --- | --- |
| [Spotify metadata plugin](https://github.com/sonic-liberation/spotube-plugin-spotify/tree/47d0a1051b576616f9e823cc756b84e8dc1a53f4) | Kotlin Multiplatform metadata APIs bound to Spotube through Zipline | `RealMetadataTrackAPI.recommendationsBasedOnTracks` requests a radio playlist from the first seed. It does not own audio sockets, the player queue, downloads or negative feedback. Adding audio retries here would not repair playback. |
| [YouTube audio plugin](https://github.com/KRTirtho/spotube-plugin-youtube-audio/tree/94d87c573ef253afa330646f866af2943e6bbbca) | Hetu adapter to the host's `YouTubeEngine` | `src/segments/audio_source.ht` searches ISRC with a title/artist fallback, then delegates to `streamManifest`. Its stream mapping hardcodes `type: lossless` and `codec: aac`, even for WebM. These labels cannot describe every returned rendition correctly. Expiry, HTTP and decoding recovery belong in the host. PixelPlayer uses real container MIME types and bitrate rather than copying those labels. |
| [YouTube Music metadata plugin](https://github.com/Benisgo/spotube-plugin-youtube-music/tree/8db47209bf4a4534cd3cbc4058e3a51ce288314c) | Partial Hetu metadata integration | `src/plugin.ht` exposes auth/search/track; album, artist, playlist and other endpoints are commented out. `track.ht` returns placeholder track metadata and an empty `getMany` result. There is no audio-source endpoint to fall back to. `innertube.ht` hardcodes a 2023 client version and forwards cookies. Source inspection does not establish whether that client still authenticates against the service. |

PixelPlayer's equivalent flow is:

1. Spotify metadata → durable Spotify/YouTube match; or YouTube search → video ID.
2. `PlaybackDispatchStateHolder` builds a MediaItem with a logical `youtube://` URI.
3. `DualPlayerEngine` checks a published downloaded file before checking connectivity.
4. Otherwise, its data source reaches the local YouTube proxy, which obtains an expiry-checked NewPipe manifest and streams the selected rendition.
5. Media3 owns buffering, byte-range reopening, extraction and decoder fallback.

The standalone `ResolverOrchestrator` was not the active Media3 path. Fixing it alone could not fix playback or downloaded-source synchronization.

## 2. Root causes and changes

### Task 1 — extraction and playback

* **Conflicting URL cache lifetimes:** the proxy cached URLs for five hours even though `YouTubeAudioStream.expiry` already accounts for advertised expiration and a safety margin. YouTube now bypasses that second cache; the bounded manifest cache remains authoritative.
* **No recovery before a failed HTTP response:** the proxy previously forwarded a failure without invalidating the extractor. It now makes at most three upstream attempts for I/O failures, 401/403/408/429 and 5xx, with 300/600 ms backoff. Permanent statuses such as 404 and 416 are not retried by this layer.
* **Unsafe rendition changes on resumed transfer:** refreshed manifests can offer a different preferred stream. Renditions are pinned by container/itag, with MIME/bitrate fallback for URLs without itag. A byte-range request cannot silently switch to a different rendition. If the pinned rendition disappears, the request fails rather than joining incompatible bytes.
* **Cancellation/resource leaks:** asynchronous OkHttp opening cancels the call when its coroutine is cancelled and closes a response that arrives too late. Proxy bodies are scoped to `use`. A pre-response proxy exception now produces a gateway error; a failure after response commitment propagates so the client can reopen at its saved offset.
* **Transport and decoder recovery:** the proxy uses a 20-second idle read timeout without a whole-track call timeout. Media3 retains its normal load retries and enabled platform decoder fallback. The engine bounds terminal YouTube recovery to two attempts per track; decoder failures can select an actual MP4 rendition and restart extraction from the time position. A changed cache key forces a new media source. Recovery checks the current player/item/index before acting.
* **Startup preparation:** neighbor preparation now warms the actual manifest. Existing extraction locks and cache share that work with playback; search results do not resolve every audio URL.
* **Spotify identity normalization:** matching previously stored a search result's `yt_`-prefixed song ID as a video ID. New and cached mappings are normalized at the resolver boundary, and offline lookup accepts legacy prefixed IDs.

No alternative public proxy service or unverified extractor was added. Fallback means ISRC → title/artist matching, fresh signed URL resolution, platform decoder fallback and an available compatible rendition. Provider unavailability cannot be bypassed by switching to either metadata plugin.

### Task 2 — standard queues and smart replenishment

* The existing continuous mix loop appends to the current playlist, preserving its standard tracks. Physical queue order remains under the existing shuffle implementation.
* Refill begins when fewer than **three upcoming tracks** remain. A single coroutine owns each mix session and checks approximately every 1.5 seconds; there are no concurrent refills within that session. Empty batches back off for 30 seconds.
* Every append checks cancellation, mix generation, queue revision, current IDs/recording identity and current feedback. The queue revision also rejects replacement playlists with the same display name. Checks run again after database and source preparation awaits.
* Appending uses the current controller directly after preparation. The previous dispatched append could execute after the mix job was cancelled.
* A failed related-track endpoint no longer discards search-based recommendations. Cancellation remains cancellation rather than an empty discovery result.
* The existing recent-history bound and queue-history trimming remain. Smart discovery still excludes library recordings; Normal Mix can reuse older material when fresh candidates run out, subject to feedback exclusions.

The refill loop remains in the existing PlayerViewModel lifecycle. It runs while the app is backgrounded with that ViewModel alive; process death, force-stop or destruction of the ViewModel ends the live mix session. This change does not add persisted autonomous mix sessions or a scheduler.

### Task 3 — fullscreen controls and feedback

* The fullscreen secondary control row is now **Mix — Favorite — Dislike**. Favorite occupies the center; the broken-heart action occupies the former Favorite slot. Transport play/pause/previous/next stay in their existing row.
* Repeat moves to the fullscreen overflow sheet and displays off/one/queue state. Track options remain accessible from that sheet. Lyrics-specific controls are unchanged.
* Dislike records feedback synchronously in memory and persists it through SharedPreferences, skips forward, removes the rejected current item and removes rejected upcoming aliases. At the end it pauses rather than wrapping into the rejected track.
* Explicitly rejected recording identities and video aliases are excluded from subsequent mix candidates and seed selection. Other tracks by a rejected artist receive an **8-point score penalty**, rather than being globally banned. Feedback is checked again when an already-running fetch returns.
* Likes and ordinary skips do not create negative feedback. This is explainable metadata weighting, not an acoustic model or a claim of measured recommendation quality.

### Task 4 — downloads and immediate offline resolution

* The old download wrote directly to its final filename and swallowed tagging errors. Downloads now write to a unique hidden staging file, check nonempty/full transfer length, flush audio, commit metadata and read it back before an atomic rename publishes the file.
* Known album artwork is required to download and validate successfully before publication. Artwork fetches have bounded retries and an 8 MiB limit; cancellation propagates. Title, artist, album and artwork bytes are verified after tag commit. Failed transfer/tagging leaves no published download state and cleans up staging.
* Tagged downloads choose an actual **MP4/M4A rendition** because the installed jaudiotagger implementation cannot write WebM metadata. A WebM-only manifest fails with a clear error rather than reporting a fully tagged download. Streaming still supports WebM. MP4 metadata uses its native tags, not ID3 blocks inserted into the wrong container. No lossy transcoding is performed.
* Download calls for the same provider song are serialized; completed-file reuse rejects missing, unreadable or empty files. Stable filenames avoid long Unicode title/artist filename limits. Spotify downloads can resolve their source match when no YouTube ID has yet been attached.
* A Room transaction merges metadata updates with the latest committed download fields, preventing a late metadata refresh from clearing a download. Explicit `updateDownloadStatus` remains the way to clear download state.
* Both queue dispatch and engine preparation preserve logical YouTube URIs. Each data-source reopen checks local storage before connectivity. Room lookup uses the video ID as well as the original provider identity, so a saved Spotify alias can satisfy the YouTube source.
* The engine observes committed download rows and replaces matching current/upcoming MediaItems with the local file while preserving current time and play/pause intent. It defers mutation until an active crossfade finishes. A source replacement can briefly rebuffer; zero-gap switching has not been established on hardware.
* Completion notifications now follow the returned download result rather than racing a progress collector that was immediately cancelled. Media scanning uses the real MIME type. Playback readiness depends on the file and Room record, not on asynchronous MediaStore scanning.

## 3. Change delivery

The implementation is in the working tree. Existing unrelated edits were retained. Review patches are generated against snapshots of the files as they existed at the start of this task, including pre-existing uncommitted code, rather than against Git HEAD. Apply the entire set before building: playback and download files share the offline-source interfaces.

Patch files are under `C:\Users\trai\Documents\GitHub\spotube-reliability\diffs`. The combined patch and numbered task patches contain only this task's incremental changes. The small `OverlayCutoutService` default-argument fix is separated as build support; it unblocks an unrelated pre-existing compilation error.

## 4. Automated verification

Use JDK 21 and the Android SDK required by this checkout (`compileSdk = 37`). No Flutter SDK is needed for the confirmed PixelPlayer target. The normal Android Studio JBR directory was missing its Java executable in this environment, so a Microsoft JDK 21 archive was downloaded to the sibling audit workspace for verification.

From the PixelPlayer repository in PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Users\trai\Documents\GitHub\spotube-reliability\jdk\jdk-21.0.12.1+1'
.\gradlew.bat :app:testDebugUnitTest --max-workers=2 --console=plain `
  --tests 'com.theveloper.pixelplay.data.youtube.*' `
  --tests 'com.theveloper.pixelplay.data.stream.*' `
  --tests 'com.theveloper.pixelplay.data.spotify.*' `
  --tests 'com.theveloper.pixelplay.data.AdaptiveMixTest' `
  --tests 'com.theveloper.pixelplay.data.Mix*Test'
```

New regressions cover stable rendition selection, compatible-container fallback, retryable/permanent statuses, transfer length validation, cancellation/late-body disposal, downloaded-file eligibility, download/metadata merging, feedback persistence and alias matching, recommendation penalties/fallback/cancellation, and refill threshold boundaries. Existing manifest-expiry, search and resolver tests remain in the selected suite.

Build/test results for this task are recorded in the adjacent `streaming-reliability-verification.md`. Unit tests do not establish real CDN availability, codec decoding on a device or seamless audio output.

## 5. Device and fault-injection acceptance checks

1. **Streaming and codecs:** play actual MP4/AAC and WebM/Opus manifests, seek repeatedly, and move across mixed local/online queues. Exercise platform-supported local MP3, FLAC and WAV too. Verify audible output and position; a ready event alone is insufficient. Induce a decoder failure for the preferred rendition and verify compatible fallback, preserving time without combining byte streams.
2. **Expiry/network:** use a controlled test upstream or debugger to invalidate the manifest, return 403 before headers, interrupt a response mid-body, and temporarily disconnect the device. Confirm bounded attempts, Range offsets, fresh URLs and cancellation when skipping. Test 404/416 and exhausted retries; failures should be visible rather than loop forever. Do not log signed URLs or credentials.
3. **Latency:** record at least 30 warm and cold starts on one device with known network conditions. Compare median/p95 tap-to-audible time and 10-minute rebuffer count to the baseline. No latency numbers or universal streaming guarantee are claimed by this patch.
4. **Mixed queues:** start a standard playlist, enable Smart Mix, and insert ordinary tracks. At three upcoming tracks no threshold refill is required; at two it should fetch and append without replacing the current item. Delay the fetch, start another playlist (including one with the same display name), and verify no old batch appears. Repeat while switching Normal/Smart/off and rapidly skipping. Test an empty discovery response and offline retry backoff.
5. **Controls/feedback:** expand the player and verify Mix/Favorite/Dislike placement, broken-heart accessibility label and Repeat in overflow. Toggle every repeat mode. Dislike while playing, paused, on the last track, and while refill is pending. Confirm immediate skip/pause, no rejected aliases in later batches, and retained feedback after app restart.
6. **Downloads:** download a current track and an upcoming track while playback is running. Confirm a local file URI becomes active at the existing position without reloading the playlist. Repeat paused and during a crossfade. Then disable networking and replay/seek both tracks, including a Spotify alias of the same video.
7. **Integrity/races:** interrupt a transfer, return truncated/empty content, fail artwork retrieval or tag commit, and enqueue the same song twice. Failed files must not become downloaded rows. Verify published files with a real media decoder and a metadata inspector. Refresh provider metadata concurrently with completion and confirm the local path remains. Remove a saved file externally and verify the next logical-source resolution can use remote audio while online.

Remaining release validation includes real accounts/CDNs, Android device playback, crossfade/source-switch timing, Compose interaction, actual metadata writer behavior on provider files and process lifecycle behavior. Those checks require a device and controlled media; static inspection and unit tests cannot substitute for them.
