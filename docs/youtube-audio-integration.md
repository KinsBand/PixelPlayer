# YouTube audio integration

Reference: https://github.com/KRTirtho/spotube-plugin-youtube-audio/tree/94d87c573ef253afa330646f866af2943e6bbbca

The reference is a Hetu plugin that delegates search and audio manifests to a host-provided `YouTubeEngine`. It is not a standalone HTTP service. PixelPlayer implements that boundary natively with NewPipe; it does not load Spotube `.smplug` binaries or embed Flutter. This is an independent implementation of the observed interface, not copied plugin code.

## Behaviour

- Search returns track metadata and stable YouTube IDs without resolving every stream. Successful queries are cached for five minutes, up to 64 queries. Local search and remote search are collected independently so Room updates do not issue new network searches. Existing metadata matching checks ISRC, then title and artist.
- The selected video's manifest contains its available MP4/WebM audio renditions, real MIME type, bitrate in bits/second and URL expiry. NewPipe reports kbit/second; conversion happens at this boundary. Audio is not labelled lossless and unavailable quality tiers are not fabricated.
- Playback and download share a bounded manifest cache. Playback prefers a readable saved file; otherwise it resolves the video. URLs are refreshed before their advertised expiry. No public proxy-instance discovery runs before playback.
- Downloads keep the source container (`.m4a` or `.webm`), include the video ID in the filename, and refresh the same container on retry. Tags are best effort. Local path fields survive subsequent cloud metadata saves.
- Liking saves metadata independently of downloading. Existing cloud-favourite joins allow liked tracks to remain streamable without a local file.

The native extractor can stop working when YouTube changes. This integration is not an official YouTube API and cannot guarantee availability or fresh network searches in milliseconds. Cache latency, actual playback and offline behaviour need device verification.

## Checks

Run `:app:testDebugUnitTest --tests 'com.theveloper.pixelplay.data.youtube.*'` for manifest selection, expiry, resolver caching and search caching/cancellation regressions.

On a device, search a track, like it without downloading, restart and play it from Liked. Download it, enable airplane mode, and play it again. Verify that a repeated cached query does not make another search request, WebM downloads retain their format, and a failed extraction leaves local playback usable.
