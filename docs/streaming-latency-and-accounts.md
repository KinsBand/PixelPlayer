# Search, stream startup and connected accounts

Research date: 2026-09-17. The latency numbers below are proposed engineering acceptance targets, **not measured results**. No Android device was attached during this investigation. Authentication against real accounts still needs a user-owned Spotify developer app and a signed-in YouTube Music browser session.

## Findings from the requested repositories

| Repository / inspected revision | Relevant design | Application to PixelPlayer |
| --- | --- | --- |
| [Spotube](https://github.com/KRTirtho/spotube/tree/69a310c78f5ceaf4eab7dfee98f187d38211c9ba) | Separates metadata, matching, manifests and playback. `lib/services/sourced_track/sourced_track.dart` persists source matches; a cached match still needs a manifest. `lib/provider/audio_player/audio_player_streams.dart` resolves the next track after 80% progress. | Keep durable track IDs separate from expiring stream URLs. Next-track manifest preparation is a useful follow-up after measuring startup; do not resolve every search result. |
| [YouTube audio plugin](https://github.com/KRTirtho/spotube-plugin-youtube-audio/blob/94d87c573ef253afa330646f866af2943e6bbbca/src/segments/audio_source.ht) | A thin Hetu adapter to the host `YouTubeEngine`; searches ISRC then title/artists and retrieves streams for a selected match. | Not an HTTP service or a drop-in Android library. Retain native NewPipe and existing match/manifest caches. The plugin's stream labels are not evidence of lossless audio. |
| [Spotify metadata plugin](https://github.com/sonic-liberation/spotube-plugin-spotify/tree/47d0a1051b576616f9e823cc756b84e8dc1a53f4) | `RealCoreAPI.kt` uses a WebView, `sp_dc` cookies and token refresh; `AuthClient.kt` calls private web token APIs with TOTP parameters. | This is not the official Spotify OAuth API. Adopting its authentication would introduce an additional private API dependency and would not fix an incorrectly configured OAuth app. Retain PKCE. No plugin source was copied. |

Spotube's [YouTube engine](https://github.com/KRTirtho/spotube/blob/69a310c78f5ceaf4eab7dfee98f187d38211c9ba/lib/services/youtube_engine/youtube_explode_engine.dart) requests audio manifests independently of full video information. PixelPlayer now uses NewPipe's stream extractor directly for audio; it no longer asks `StreamInfo.getInfo` to assemble unrelated video metadata and recommendations before returning audio. Recommendations are fetched separately when requested. NewPipe can still perform multiple network calls internally.

## Changes implemented

- Remove the 50 ms debounce before local/cache search. Replay the latest search request so input sent before the collector starts is not lost. Apply 150 ms debounce only before remote search; cancellation of the old collection suppresses superseded results.
- Preserve existing five-minute query cache, bounded manifest cache, expiry margins, downloaded-file priority and durable Spotify-to-YouTube mappings. Propagate cancellation from Spotify matching instead of falling through to another network search.
- Reduce initial buffered media from 2,500 ms to 500 ms on both memory profiles. Keep 5,000 ms rebuffer recovery and existing buffer ceilings. This can increase stalls on a poor network; validate rebuffer rate before release. [Media3 load-control documentation](https://developer.android.com/reference/androidx/media3/exoplayer/DefaultLoadControl.Builder) defines these values as media duration, not elapsed startup time.
- Add monotonic `StreamingLatency` debug logs for query-to-first-nonempty-results, cache/network search including hydration, and uncached manifest extraction. Logs omit query text, credentials and stream URLs. First-results measures StateFlow publication, not screen rendering; manifest extraction is not first audible output.
- Validate imported YouTube Music credentials before persisting or emitting a connected account. A failed/cancelled reconnect no longer logs out the previous session and removes its saved library. Accept raw cookies, copied request headers, and header JSON; recover the account index from `X-Goog-AuthUser`. Reject blank signing cookies and injected control characters.
- Keep pending Spotify client ID separate from the active account until token exchange succeeds. Validate callback method/path/state, serialize exchanges and session cleanup, preserve specific token errors, handle declined consent, and keep a closed browser from replacing successful status with a timeout. PKCE verifier/state are single-use.

## Suggested performance budgets

Use a representative Android phone on stable Wi-Fi; record RTT, device, Android version, build type, thermal state and library size. These budgets require device validation and may need revision for mobile networks.

| Scenario | p50 target | p95 target | Boundary |
| --- | ---: | ---: | --- |
| Local/cached search | 50 ms | 100 ms | Final input event to visible rows |
| Fresh online search | 700 ms | 1,500 ms | Final input event, including 150 ms debounce, to visible rows |
| Warm stream | 300 ms | 700 ms | Tap to first audible output; valid manifest already cached |
| Cold YouTube stream | 1,200 ms | 3,000 ms | Tap through extraction, CDN transfer and decoding to audio |
| Unmatched Spotify track | 2,500 ms | 5,000 ms | Includes metadata match search and stream extraction |

A universal sub-100 ms fresh network search or cold stream is not a credible promise: DNS/TLS, provider latency, matching, CDN transfer and audio startup remain on the critical path. Buffering 500 ms of compressed audio does not imply waiting exactly 500 ms of wall time.

## Account setup and remaining service constraints

Spotify: create a developer app, enter its client ID in Accounts, allowlist the user, and register the three exact loopback URLs shown in the dialog (`http://127.0.0.1:8888/spotify-callback`, ports 8889 and 8890 as fallbacks). The browser runs on the same Android device as the listener. Use a regular browser, not an embedded WebView. No client secret is needed for PKCE. Spotify documents dynamic loopback ports too; this implementation intentionally uses explicit registered ports. See [PKCE](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow) and [redirect rules](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri).

Spotify's [development-mode changes](https://developer.spotify.com/documentation/web-api/references/changes/february-2026) impose account and endpoint restrictions, including Premium requirements for app owners. Successful sign-in cannot guarantee access to every playlist or analysis endpoint. Spotify supplies metadata/library data here; playback resolves to YouTube rather than Spotify audio.

YouTube Music: sign in at music.youtube.com, open browser Developer Tools → Network → a successful authenticated `browse` request, and import its Cookie value or headers in Accounts. Header JSON is also supported. This follows the [ytmusicapi browser-session approach](https://ytmusicapi.readthedocs.io/en/stable/setup/browser.html); it is an unofficial session import, not a new Google OAuth integration. Browser.json Authorization timestamps are not reused: requests generate a fresh SAPISID hash. Google [disallows OAuth sign-in in embedded user agents](https://developers.google.com/identity/protocols/oauth2/policies). A native app cannot read another browser's cookies automatically. Brand-account/delegated-session variants may require additional support; current account selection uses `X-Goog-AuthUser`.

## Verification procedure

Run the targeted unit suite:

```powershell
./gradlew.bat :app:testDebugUnitTest --tests 'com.theveloper.pixelplay.data.youtube.*' --tests 'com.theveloper.pixelplay.data.ytmusic.*' --tests 'com.theveloper.pixelplay.data.spotify.*' --tests 'com.theveloper.pixelplay.presentation.viewmodel.SearchStateHolderLatencyTest'
```

On a connected device, capture `adb logcat -v time StreamingLatency:D '*:S'` while running at least 30 trials per scenario. Separate cache hits from misses and report p50/p95 plus failures. Use screen/audio capture or an instrumented tap-to-audio test for the end-to-end playback budget; neither manifest logs nor `STATE_READY` prove audible startup. Measure rebuffer events during at least ten minutes on Wi-Fi and a throttled/mobile network. Compare to the original 2,500 ms setting before release.

Verify Spotify consent, denial, wrong state, browser closure, failed token exchange, refresh and reconnect. Verify YouTube raw/header/JSON import, wrong account, expired cookies, offline validation, cancellation and reconnect while an old library is saved. Restart the app and confirm successful account sync, private playlists, liked songs, and offline playback of downloaded tracks. Never attach cookies or token responses to bug reports.
