# Faster export, Transfer prompt, smoother sheets and tabs, Gemma 4 on-device: build notes (2026-10-14)

**Compiled: no. Run on device: no.**

## You need to add one Gradle line first
LiteRT-LM (approved in chat) runs Gemma 4 on the phone. In `app/build.gradle.kts` dependencies:

```kotlin
implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
```

Google's docs only give `latest.release`. After your first sync, pin it to the version Gradle resolved, so builds stay reproducible. Without this line, the four `data/ai/gemma/*` files and `RoutingNanoEngine` won't compile.

## Also fixed: last build's error
`MainActivity.kt:1344` used the name `density`, which there meant Compose's `Density` object rather than a number. It's now `DeckSheetBlurDp.dp.toPx()`, the same 3 dp.

## 1. Export: faster, and no more "400"
I didn't call TuneMyMusic's private service. The app uses the same techniques TuneMyMusic-style transfers rely on:
- **Match first, create last.** Every song is matched before anything is made on the service, 4 at a time. Spotify matches try the exact recording (ISRC) first, then title + artist (now on web sign-ins too), and are cached for the session. Only then is the playlist created and filled in 100-song batches.
  - Nothing is created when nothing matched, so failed exports no longer leave empty playlists behind.
- **Live progress** in the export sheet: "Matching 40 of 120…", "Creating the playlist…", "Adding 112 songs…".
- **Errors name the step**, e.g. "Couldn't add songs on Spotify (…)" instead of a bare 400.
- **Likely 400 causes, fixed:**
  - On web sign-ins, all songs were sent to Spotify in one request; they now go 100 at a time.
  - YouTube Music silently dropped any song without a YouTube ID. Phone and other-service songs are now matched by YouTube Music search, with the same title/artist/version checks.

## 2. Transfer
Tapping Transfer opens a small sheet: TuneMyMusic can import a playlist file. Your choices:
- **Save file & open TuneMyMusic**: saves the `.m3u`, then opens the site so you can upload it.
- **Open without a file.**

## 3. Song options sheet
- **Cause:** my last fix pre-loaded the Info page so both tabs shared the taller height, which made the sheet always open at full height.
- **Fix:** removed that. The sheet now sizes to whichever page is showing, and the height change is a smooth spring the whole way (the old version snapped late, which caused the "drop").

## 4. Artist page tabs
Switching tabs slides the new tab's sections in from the side you moved toward (36 dp) while they fade up (350 ms, emphasized decelerate). It runs in the draw phase, so the list doesn't recompose. Off when animations are off.

## 5. AI page and Gemma 4
### The page
- **On-device AI hero**: live status ("Gemma 4 E2B · ready"), one line on privacy, and chips for what it powers (artists, lyrics, mixes).
- **Engine**: two tiles side by side, **Gemini Nano** (built into Pixel) or **Gemma 4** (download).
- **Gemma model cards**:
  - **E2B** (2.59 GB, ~2 GB RAM, quick) and **E4B** (3.66 GB, ~4 GB RAM, for 12 GB phones).
  - "For this phone" badge and a fits / may-not-fit pill from your RAM.
  - Download with wavy progress ("1.2 GB of 2.6 GB · 45%"), Pause/Resume, Use, Delete.
- **Wi-Fi only** switch (on by default), and a credit line.
- **Then the rest**: Network (offline mode), **AI playlists** (system prompt as a normal row, plus the safe-token switch), usage report and Advanced, as before.

### Models
- Phone builds from Hugging Face `litert-community/gemma-4-E2B-it-litert-lm` and `…-E4B-…` (Apache 2.0, no sign-in).
- Gemma 3 270M and FunctionGemma are left out: their repos require a Hugging Face login.
- **Download behaviour**:
  - Resumable, carries on when you leave Settings, and goes through `NetworkAccessPolicy` (offline mode blocks it).
  - Pauses off Wi-Fi when Wi-Fi only is on, and checks the file size before marking a model ready.
  - Saved in app storage (`noBackupFilesDir/gemma-models`).

### How it runs
- **One router for every feature:** a new `RoutingNanoEngine` replaces the old engine binding. When Gemma is picked and downloaded it answers; otherwise Gemini Nano does. So Ask about the artist, lyrics translation and mix prompts all use Gemma with no other changes.
- **Loading:** the model loads on first use (GPU, then CPU fallback; up to ~10 s, not counted against answer timeouts), answers one request at a time, and closes after 60 s idle to free its 2–4 GB.
- The Gemini Nano card still shows Nano's own status.

### Artist Ask box with tools (keyless)
When Gemma is active, the Ask box gets three LiteRT-LM tools, as in your guide:
- **getBandRosterAndPfps**: MusicBrainz lineup with each member's role and current/former status.
  - Member photos now come from one batched Wikipedia request (600 px). A photo is only used when the page's description says musician, drummer, singer and so on, with Deezer as the fallback.
  - Photos go straight to the Members row on screen. Gemma only gets names and roles.
- **searchArtistWebInfo**: Wikipedia search (title, snippet, link).
- **readWebpageMarkdown**: one page via Jina Reader, cut to 8,000 characters.

All three go through `NetworkAccessPolicy`. The better member photos also show on the artist page for everyone, not just in Ask.

- **Manifest:** an optional `uses-native-library libOpenCL.so` line, so Gemma can use the GPU (it doesn't stop phones without OpenCL from installing).

## Files
- **New**: `data/ai/gemma/GemmaModels.kt`, `GemmaModelManager.kt`, `GemmaLiteRtEngine.kt`, `ArtistAgentTools.kt`, `data/ai/nano/RoutingNanoEngine.kt`, `presentation/screens/settings/OnDeviceAiSection.kt`
- **Edited**:
  - Engine binding: `NanoModule` (now binds the router), `GeminiNanoCard` (shows Nano itself)
  - Artist: `ArtistHubRepository` (photos, search, page reader), `ArtistDetailViewModel` (Gemma + tools)
  - Settings: `AiSettings` (new layout)
  - Export: `ConnectedLibraryRepository` (export rewrite, plus the `YouTubeMusicApiService` search as a lazy dependency), `PlaylistViewModel` (progress), `SpotifyRepository` (web ISRC), `SpotifyWebLibrary` (100 per add), `PlaylistDetailScreen` (progress, Transfer sheet)
  - Sheets and tabs: `SongInfoBottomSheet`, `ArtistDetailScreen`
  - Also: `MainActivity`, `AndroidManifest.xml`
- **Strings**: `strings_settings.xml` (ai_*), `strings_library.xml` (playlist_transfer_*)

## Verification
| Check | Result |
|---|---|
| Compiled / run | **No / No** |
| ktlint 1.3.1 parse, 19 Kotlin files | No parse errors |
| Strings / manifest | Well-formed, no duplicates, every new string defined; manifest parses |

## Please run
1. Add the Gradle line above, then sync.
2. `./gradlew :app:compileDebugKotlin`

## Compile risks to watch (LiteRT-LM is new to the project)
- `conversation.sendMessage(text).toString()` for the reply text, and nested maps/lists in tool results. Both follow Google's examples, but are untested here.
- `ConversationConfig(systemInstruction = Contents.of(…), samplerConfig = SamplerConfig(…), tools = …)` and `EngineConfig(modelPath, backend, cacheDir)`, as documented.

## Needs on-device testing
- **Pixel 10 Pro**: Settings → AI → Gemma 4 → download E2B (2.6 GB; Wi-Fi). Pause/resume, then Use. First answer is slow (loading); later ones are quicker.
- **Artist page Ask box with Gemma**: "who's in the band?" should fill Members with photos, and "what guitar does X use?" should search.
- **Export** a large playlist to Spotify (web sign-in) and YouTube Music. Check the progress text, timing, and that local songs now match on YouTube Music.
- **Transfer**: Save file & open → file saved, then TuneMyMusic opens.
- **Song options**: normal height again; Options ↔ Info glides.
- **Artist page tabs**: slide direction matches tab order.
