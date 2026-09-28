# Audio foundation: first implementation slice

This slice extends the existing Media3 player and mix planner. It does not implement
direct USB output, stem separation, beat-grid transitions, or a replacement DSP engine.

## Queue persistence

- Queue occurrences carry a UUID independent of the recording/media ID.
- Play Next entries use the priority tier and insert immediately after the current item.
  Existing batch insertion reverses calls to preserve the selected order.
- Play Later entries use the session tier and append to the queue.
- Explicit additions and reordered future entries are pinned against automatic mix changes.
- Snapshots preserve tier, pin, generated/manual origin, mix decision, and session metadata.
- Restore selects the saved occurrence, including repeated copies of the same recording.
- A restored queue stays paused. An explicit transport command can start it.
- Legacy snapshots decode with defaults. Queue changes continue to use the existing
  debounced, atomic DataStore snapshot mechanism; the debounce window is not a guarantee
  against an abrupt process kill immediately after an edit.

## Smart resume

- Under 30 seconds: retain the current offset.
- From 30 seconds through five minutes: rewind seven seconds, clamped to zero.
- Above five minutes: the policy accepts a validated phrase boundary. The live service
  currently uses the seven-second fallback because reliable phrase timestamps are not
  yet supplied by analysis.
- Rewound resumes use a 300 ms linear gain envelope that advances only while playing.
  It multiplies the ReplayGain target, including targets updated during the fade.
- Explicit seeks while paused and changing queue occurrences cancel the rewind.
- The policy never starts playback. Crossfades and non-seekable streams retain existing
  behavior. Navigation ducking is not treated as a paused interruption in this slice.
- Output-device removal supplements Media3's noisy-output handling when the final
  headphone/USB/Bluetooth sink disappears. This requires real-device verification;
  connected-device enumeration is not proof of the active route.

## Micro-skips

- A manual skip before 15 seconds of both playback and track position qualifies.
- Seeks, resumed excerpts, errors, natural endings, and interruptions do not qualify.
  A 250 ms starting-position tolerance accommodates listener callback latency.
- Two consecutive qualifying outcomes in the same session trigger artist and/or
  specific-subgenre cooldowns. Unknown metadata and broad genre families are ignored.
- Cooldowns persist independently of the bounded attempt history, expire after seven
  days, and contribute a named negative ranking score: 8 in the triggering session,
  3 in subsequent sessions. They lower rotation preference rather than excluding music.
- Database version 4 adds metadata/position columns and a cooldown table. Historical
  positions default to -1, so old skips cannot become synthetic micro-skips.
- Final outcomes publish after the attempt/cooldown transaction commits. Resetting
  learned history also clears cooldowns.

## Capability and event contracts

The engine exposes decoded-format capabilities and observational transport events.
Hardware output format remains unknown and bit-perfect verification remains false.
The event stream is bounded and lossy; durable listening history continues through
the existing tracker and Room storage.

## Fingerprinting

Missing or incompatible Chromaprint native code returns null. The deterministic
mock fingerprint has been removed; AcoustID is only called with an actual native
fingerprint. Metadata text-search fallback remains available.

## Verification

Focused JVM tests cover resume boundaries, fade cancellation/buffering, micro-skip
classification/ranking/expiry, snapshot serialization, legacy defaults, and missing
native fingerprint support. An Android migration test validates version 3 to 4 and
preservation of historical attempts. Run device tests and audio route checks before
shipping; JVM tests cannot establish speaker-leakage timing or audible fade quality.

Device acceptance checks:

1. Queue the same track twice, play the second occurrence, restart the process, and
   confirm the second occurrence and offset restore with playback paused.
2. Add A then B using Play Next: expect B then A. Add a selection [A, B] using Play
   Next: expect A then B. Append C using Play Later and confirm it remains at the end.
   Reorder a generated future entry and confirm mix maintenance leaves it pinned.
3. Pause a local track for 29 seconds, 30 seconds, and over five minutes. Verify the
   offset/fallback behavior and fade with ReplayGain both enabled and disabled. Seek
   while paused and confirm resume respects the selected position.
4. During playback and during a crossfade, disconnect the final wired, USB, and
   Bluetooth sink. Check that both players stop and no later focus-gain event restarts
   playback unexpectedly. Test a manual pause while transient focus is lost.
5. Skip two tracks by one artist before 15 seconds and inspect the named cooldown
   score in subsequent mix decisions. Repeat after process restart, reset history,
   and confirm the cooldown is removed.
