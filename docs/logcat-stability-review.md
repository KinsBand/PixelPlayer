# Logcat stability review

The Pixel 10 Pro crash buffer recorded an out-of-memory failure at 18:36 on September 22: PixelPlay exhausted its 256 MB Java heap. The failed DisplayInfo allocation is the final symptom, not proof of the retaining component. A later process snapshot showed 13,481 KB of Dalvik allocations; a single snapshot cannot rule out a long-session leak.

Startup logged two skipped-frame bursts, a caught foreground-service restriction, and YouTube DNS failures. The emulator crash belonged to Android Bluetooth, not PixelPlay. The phone disconnected during follow-up collection. Raw local captures are in build/pixel-crash-logcat.txt, build/pixel-current-logcat.txt, build/pixel-meminfo.txt, and build/pixel-gfxinfo.txt.

Player buffer targets now use 12 MB per player on low-RAM devices and 24 MB otherwise. Byte targets take priority over long time targets during high-bitrate overlap, while retaining 500 ms startup and 1 s rebuffer thresholds. See [Media3 load control](https://developer.android.com/reference/kotlin/androidx/media3/exoplayer/DefaultLoadControl.Builder). These targets are not an absolute cap on total process memory.

Artist catalogs now use a weighted LRU cache; obsolete requests are cancelled. ML model loading moves out of dependency injection into the existing background preparation path. Inference is serialized, with input bounded before resampling.

Test fixtures now provide the added metadata and scrobbling collaborators. Overlay tests exercise current IslandMetrics snapping and fling behavior rather than removed APIs. Validation uses the full debug app, unit-test, and instrumentation-test assemblies plus targeted regressions, without excluding sources. Final output is in build/stability-final.log. Long-session playback on the phone remains necessary to verify that the observed memory failure is eliminated.

Regression testing also exposed and fixed an initialization cycle in the new genre preset registry. Preset enumeration is now lazy so first access through either entry point is safe. The emulator launch logged no new PixelPlay fatal exception, but startup still had skipped-frame bursts; this is not evidence of smooth long-session playback.

Final validation: assembleDebug, assembleDebugUnitTest, and assembleDebugAndroidTest succeeded. All 44 targeted unit tests passed with zero failures or skips. Instrumentation tests were assembled, not executed. The final ARM64 debug APK installed and launched on emulator-5554 (PID 1494); its captured process log contained no fatal exception, OOM, or ANR. Launch took about 26 seconds and logged skipped-frame bursts, so startup responsiveness remains unresolved on this emulated environment. See build/emulator-final-logcat.txt. The physical phone was unavailable for final verification.
