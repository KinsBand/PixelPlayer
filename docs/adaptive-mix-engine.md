# Adaptive mix engine — implementation and validation

## Implemented

- Pure Kotlin playback exposure accounting (`PlaybackExposure`): monotonic wall time, unique merged media intervals, replayed media duration, seek counts, stall/pause/buffering exclusion, playback-speed separation, and conservative rejection of unreported jumps.
- Service-owned one-second sampling, grouped Media3 event handling, track/repeat attempts with UUIDs, explicit end reasons, and 15-second/pause checkpoints. Cast sampling uses the remote playback snapshot instead of the inactive local player. Process termination leaves checkpoint evidence UNKNOWN. Playback errors are not dislike labels.
- A separate Room `mix_learning.db` stores the last 2,000 attempts, updating checkpoints by attempt ID. A bounded asynchronous writer keeps database work off playback callbacks. Overflow drops analytics with a diagnostic instead of interrupting audio. Reset prevents queued pre-reset attempts from recreating deleted learning history.
- Durable exclusions scoped to Normal Mix and Smart Mix. Global exclusions are a distinct explicit control. Legacy global rejected songs remain global for compatibility; old artist penalties are ignored. New track dislikes do not penalize an entire artist. Undo retracts the last exclusion or fatigue operation; dislikes do not also generate a learned artist penalty.
- Smart Mix combines library songs and discoveries. Related-song and metadata retrieval run concurrently with individual four-second deadlines. Remote failure falls back to the available library; cancellation propagates. Normal Mix uses library candidates only.
- Deterministic Kotlin ranking uses metadata context, explicit favorites, sufficiently covered completed plays, voluntary-play weighting, recent mix-scoped early skips, rediscovery and decaying exposure/fatigue. Playback minutes alone cannot create enjoyment. The planner spaces artists/albums and balances familiar/discovery choices. ISRC is used when available; title/artist identity is a conservative fallback, with YouTube aliases also checked for exclusions.
- The rolling queue retains its committed next item when feedback changes direction, replaces later tracked automatic entries, and preserves manually queued song order. Exclusions are checked again after asynchronous song resolution and immediately before appending. Rejecting the last queued song can trigger a refill. Normal Mix no longer turns shuffle on over a planned sequence.
- The portrait player's **Mix controls** opens discovery balance, More like this, Heard this too much, global exclusion, undo, mix-history deletion, feedback reset, and an inspector displaying actual source/score components. The ordinary dislike button still skips immediately and means exclusion from this mix. Favorites use the existing favorite control. Discovery balance is persisted; 35% is a tunable default, not a measured optimum or guaranteed quota when candidates are limited.

Media3 grouped events are used because they follow the individual callbacks, allowing the old-position discontinuity to be recorded before the new attempt is created: [Player.Listener documentation](https://developer.android.com/reference/androidx/media3/common/Player.Listener).

## Validation

Focused JVM suites: PlaybackExposureTest, MixSequencePlannerTest, MixFeedbackTest, AdaptiveMixTest, ListeningStatsTrackerTest. They cover seek-to-end rejection, buffering/stalls, overlap union, speed changes, scoped/global exclusions and undo, no artist-wide dislike inference, offline discovery fallback, cancellation, observed playback measurement, deterministic spacing and discovery balance.

The normal unit-test compile is blocked by the existing unrelated `OverlayCutoutDimensionsTest`, which references removed `getLevel1Height`, `getLevel2Height`, `getLevel3Height`, and `nearestSnapLevel` methods. The focused run excludes that source with a temporary Gradle init script; the repository test and build configuration are unchanged. This is not a full-suite pass.

No Android device/emulator was connected. Compose layout, Room device execution, Cast, audio-focus interruption, dual-player crossfades and background battery/latency measurements still require device validation.

## Boundaries and next gates

This is the local, interpretable foundation of the supplied research roadmap, not the complete frontier system.

- Continuous queue refilling still has the existing PlayerViewModel lifetime; playback measurement lives in MusicService and survives closing the player screen. Moving queue orchestration and restoring its session/ownership after process death remain a service-level follow-up. Merely using an application coroutine scope would retain the ViewModel and would not solve restoration.
- The current persistent mix IDs are the two built-in modes. Arbitrary named mix identity, per-occurrence queue ownership/pinning, and a browsable per-track feedback-management screen remain to be implemented. Automatic ownership is conservatively tracked by song ID; adding that ID manually protects it from automatic queue rewriting.
- Media interval summaries are stored; a full syncable interaction/event ledger, separate work/recording/release/asset graph, queue-edit events, decision-attempt attribution and durable decision probabilities are not implemented. The inspector is in-memory. The deterministic planner is not a contextual bandit and exposes no invented sampling probabilities.
- Preferences are interpretable track summaries, a mix-scoped rejection history and metadata-based session direction. Trained taste clusters, collaborative retrieval, embeddings, delayed discovery-save/replay outcomes, learned multi-output ranking, controlled experiments and chronological evaluation are later gates requiring suitable data and benchmarks.
- Existing audio transition code remains separate and unchanged. No new beat matching, DSP time stretching, acoustic analysis confidence, or section-aware transitions are claimed.
- Learning deletion clears this new mix history only; the app's pre-existing general listening statistics are separate. Deleting during an active attempt suppresses that pre-reset attempt from the new learning store. It does not delete favorites. Resetting exclusions/fatigue retains discovery balance.
- Exposure is evidence of playback, not proof of attention. The 150-ms ranking and 100-ms feedback targets have not been benchmarked, and no Spotify/YouTube quality comparison has been established.
