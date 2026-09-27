# Vibe-aware mixing and automatic genre EQ

## Research and decisions

Spotify's [sequential preference study](https://research.atspotify.com/publications/exploiting-sequential-music-preferences-via-optimisation-based-sequencing) reports that optimizing track sequences improved completion and skipping outcomes in its experiment. Its [contextual recommendation research](https://research.atspotify.com/2021/4/contextual-and-sequential-user-embeddings-for-music-recommendation) supports separating recent/session preferences from long-term taste and accounting for skips. These motivate this app's changes; the app does not reproduce Spotify's trained models or claim their measured improvements.

The mix planner now gives the latest four seeds increasing weight, uses genre aliases and families, and evaluates mood, valence, energy, acousticness, danceability, instrumentalness, and half/double-time tempo compatibility between adjacent tracks. When available, outgoing outro energy is compared with incoming intro energy. Missing, invalid, or nonfinite features contribute no invented signal. Favorites, listening completion, scoped skip feedback, fatigue, artist spacing, and bounded discovery remain in use. Recent skips are explicitly sorted newest-first before exploration is adjusted.

These are bounded heuristics using available track metadata, not live audio understanding. Listening tests and skip/completion telemetry are needed to establish whether they improve subjective fit.

## Equalizer

Added 65 named genre presets with a shared alias vocabulary and searchable, swipeable chips. Curves have ten bands and modest boosts/cuts (within four dB), with more specific tuning for genres such as drill, lo-fi, deep house, drum & bass, and metalcore. Some related genres intentionally share a gentle family curve. [Rane's equalizer guidance](https://www.ranecommercial.com/legacy/note101.html) discusses realistic small corrections; it does not validate these genre curves. They are editable tonal starting points, not mastering corrections.

The opt-in Adapt to song genre setting is persisted. The playback service applies it on track changes, metadata events, player swaps/crossfades, startup, and enabling EQ. It therefore works without keeping the EQ screen open. Unknown or absent genre tags select Flat instead of retaining the previous track's preset. Automatic mode uses graphic EQ and automatic preamp headroom. Preset/band changes, parametric mode, and A/B auditioning stop automatic adaptation, so manual choices win. Turning adaptation off keeps the last applied curve. Saved custom presets remain available.

Genre adaptation requires metadata and applies to local playback, including online streams played through the local engine. It is not an acoustic genre classifier and does not process audio on a remote Cast receiver.

## Validation

Tests cover aliases and specificity, missing tags, safe ten-band preset shape, vibe ranking, missing/nonfinite features, half-time tempos, recency weighting, existing mix planning, and Quick Picks. The obsolete test constructors and overlay APIs have been repaired. Final validation includes the debug app, unit-test APK, and instrumentation-test APK assemblies and targeted regression tests, without source exclusions. See build/stability-final.log.

Device checks still needed: switch between known/unknown genres with the screen closed; disable EQ and re-enable it; manually adjust during a track change; test a crossfade and a rapid skip; compare loudness and listen for artifacts on headphones.

Final result: all three debug assemblies succeeded and all 44 targeted regressions passed. Runtime limits and the remaining emulator startup jank are documented in logcat-stability-review.md.
