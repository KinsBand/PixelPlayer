# Smart mixing, artist catalog, and playback responsiveness

The mix toggle starts Smart Mix directly. Quick Picks and speed dial use recent listening and favorites, with a related discovery every fourth slot when one is available. Daily mixes interleave discoveries with familiar tracks instead of placing discoveries at the end. Online discovery seeds use listening engagement instead of incidental database ordering.

Artist pages show all returned tracks in swipeable five-row pages, all returned albums, and singles/EPs. Album lookup fetches tracklists for every returned release with four concurrent requests. Known tracks use Deezer popularity rank; unranked tracks follow alphabetically. Local tracks remain visible during loading and supplement the online catalog. Cards show album titles and release metadata rather than synthetic play counts.

Provider search limits and regional availability still constrain catalog completeness. iTunes entries retain the existing preview playback behavior; this change does not supply full-track streaming rights or turn previews into full songs. Existing artist analytics estimates are unchanged.

Play/pause now toggles playback intent, so buffering can be paused. ExoPlayer playlist preloading warms five seconds of the next item (two on low-RAM devices), independently of crossfade. Rebuffer recovery requires one second instead of five seconds. Existing current-track buffering remains prioritized.

Device validation: check rapid play/pause while buffering; resume an already buffered local song; queue two online songs with crossfade off and on; skip while the next song is warming; inspect a large artist catalog and play a local fallback while offline. Real network latency and on-device transitions remain to be measured.

Validation: obsolete test collaborators and overlay API references are repaired. Final build and regression results are recorded in build/stability-final.log, without excluding test sources.

Final result: all three debug assemblies succeeded and all 44 targeted regressions passed. Runtime limits and the remaining emulator startup jank are documented in logcat-stability-review.md.
