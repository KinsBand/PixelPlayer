# PixelPlayer — real on-device Gemini Nano (prompt + plan, 2026-10-01)

Paths are relative to `app/src/main/java/com/theveloper/pixelplay/` unless they start with `app/` or `gradle/`.

---

## 1. Evidence

**App code**
- N1: `data/ai/GeminiNanoClient.kt:57-86` – `generateContent` never calls a model. `simulateAicoreExecution` "picks a subset of candidate IDs", `ids.shuffled().take(playlistSize)`, so every "Gemini Nano" result today is random.
- N2: `GeminiNanoClient.kt:19-34` – `isAvailable` only checks that the GMS package exists and SDK ≥ 34. It never asks AICore.
- N3: `data/ai/AiHandler.kt:71-72` – `useNano = !isOffline && nanoClient.isAvailable(context) && userProvider == GEMINI_NANO`. An **on-device** model is switched off in offline mode.
- N4: `AiHandler.kt:96` – token usage is guessed as `length / 4`. `:38` – results are cached for 30 min by prompt hash.
- N5: `AiHandler.kt` `executeLocalFallback` – pulls `<query>` plus candidate IDs and ranks them with `LocalHeuristicPlaylistGenerator` (limit 30).
- N6: `data/ai/AiPlaylistGenerator.kt:37,46-82` – the prompt carries up to 100 candidates (`limit = 100`, `sampleCap`) as JSON, plus a user digest and the system prompt, and asks the model to return song IDs.
- N7: `data/ai/AiSystemPromptEngine.kt` (11 KB) – long XML-style system prompts per type (`PLAYLIST, METADATA, TAGGING, MOOD_ANALYSIS, PERSONA, DAILY_MIX, GENERAL`).
- N8: `presentation/viewmodel/AiStateHolder.kt:287-315` – `translateLyrics` sends the whole lyrics with timestamps through `aiHandler.generateContent(type = GENERAL, temperature = 0.1f)`.
- N9: `data/ai/provider/AiProvider.kt` – only `GEMINI_NANO` and `LOCAL_FALLBACK`. `AiProviderSupport.kt` is stubs (`isModelUnavailable() = false`, …).
- N10: `presentation/viewmodel/SettingsAiStateHolder.kt:31-141` – provider, temperature, topP/topK, max tokens and sample size settings exist. There is no model status or download UI.
- N11: `PlayerViewModel.kt:3058-3085` + `data/AdaptiveMix.kt:468-495` – the queue prompt only resolves song/artist names (see the 2026-10-01 prompts doc, E10/E11, Prompt B).
- N12: `app/build.gradle.kts:63,94-95,128` – compileSdk 37, minSdk 30, targetSdk 37, `isMinifyEnabled = true`. `gradle/libs.versions.toml:81` – KSP 2.3.9, `:27` – Kotlin 2.4.0. There's no ML Kit GenAI dependency yet.

**Google docs (checked today)**
- G1: [Prompt API – get started](https://developers.google.com/ml-kit/genai/prompt/android/get-started)
  - Dependency: `implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")`. minSdk 26.
  - `Generation.getClient()`, `checkStatus()` → `AVAILABLE / DOWNLOADABLE / DOWNLOADING / UNAVAILABLE`, `download()`, `warmup()`, `generateContent()`, `generateContentStream()`, `countTokens()`.
  - Request params: `temperature`, `topK`, `candidateCount`, `maxOutputTokens`, `seed`.
  - Input should stay **under ~4000 tokens**. AICore enforces a **per-app inference quota**.
  - Not supported on devices with an **unlocked bootloader**. Errors include 601 BINDING_FAILURE and 606 FEATURE_NOT_FOUND (config still downloading).
- G2: [Structured output (alpha)](https://developers.google.com/ml-kit/genai/prompt/android/structured-output)
  - `@Generable` data classes with `@Guide(description, minItems, maxItems)`, then `generateTypedContentRequest(baseRequest, outputClass)`.
  - Needs `ksp("com.google.mlkit:genai-schema-compiler:1.0.0-alpha1")` and KSP ≥ 2.3.6. Kotlin only. Needs **ProGuard keep rules** for annotated classes.
- G3: [Prefix caching (experimental)](https://developers.google.com/ml-kit/genai/prompt/android/prefix-caching)
  - `generateContentRequest(TextPart(suffix)) { promptPrefix = PromptPrefix(prefix) }`.
  - On Pixel 9, a 1000-token prefix went from 2.11 s to 0.5 s. Text only, and only on some devices.
- G4: [On-device inference blog, Jul 2026](https://android-developers.googleblog.com/2026/07/android-on-device-inference.html)
  - FAST vs FULL model preference, and structured output via `@Generable`.
  - Prompt tuning took one example from 13 s to under 2 s.
  - [APO blog, Jan 2026](https://android-developers.googleblog.com/2026/01/how-automated-prompt-optimization.html): supported devices run **nano-v3**.

Not confirmed: the exact Kotlin names for the FAST/FULL model config, whether background use is blocked, and whether Pixel 10 Pro is on a published device list. Step 1 checks all three on your phone.

---

## 2. Improved prompt (copy-ready)

```
Goal: Replace the mock Gemini Nano client with real on-device Gemini Nano (ML Kit GenAI Prompt API)
and use it where it actually helps. Short, structured jobs only: the local engine still picks the
songs, and Nano understands what the user asked for.

Scope:
- data/ai/: GeminiNanoClient (N1, N2), AiHandler (N3-N5), AiPlaylistGenerator (N6),
  AiSystemPromptEngine (N7), provider/ (N9)
- the queue prompt pipeline from Prompt B (N11)
- AiStateHolder.translateLyrics (N8)
- one Settings card (N10)
- Gradle (N12)

Current behaviour: "Gemini Nano" is a mock that returns random song IDs (N1). Availability is
guessed (N2). It's switched off when offline (N3). Prompts carry up to 100 songs of JSON plus long
system prompts (N6, N7), which is far over Nano's ~4000-token input (G1).

Design rule (why it will be good and fast): Nano never picks song IDs from a list. It turns the
user's words into a small typed object (G2). The existing on-device scorers turn that object into
songs. Prompts stay under ~600 tokens, outputs under ~200, so a reply takes about a second after
warmup, and results only ever contain real library songs.

Spec:
1. Engine: data/ai/nano/NanoEngine.kt (interface) + MlKitNanoEngine (ML Kit, G1).
   - status: StateFlow<NanoStatus> = Unsupported(reason) | Downloadable | Downloading(progress) |
     Ready | Error(code). Built from checkStatus() and the download() flow.
   - download(), warmup(), and
     suspend fun <T> generate(prefix, input, schema: KClass<T>, maxOutputTokens, temperature): T?
     using generateTypedContentRequest (G2) + promptPrefix (G3; ignored where unsupported).
   - Model choice: FAST preference for intent/translation jobs (G4). Exact API confirmed in step 1.
   - One shared client, one request at a time (Mutex). Every call has a timeout (intent 2.5 s,
     translation chunk 8 s). On timeout, BUSY, quota, or any exception → null + log; callers fall
     back to local.
   - countTokens() before each call. If the prompt is over budget, trim the input (never the
     prefix) or skip Nano.
   - Delete GeminiNanoClient's simulate code. Keep the class name as an adapter if Hilt wiring
     depends on it.
2. AiHandler: drop the !isOffline gate for Nano (N3). Log real token counts (N4). Keep the cache.
3. Typed outputs (@Generable, G2), in data/ai/nano/NanoSchemas.kt:
   - MixIntent: tempo (slow|mid|fast|null), bpmMin/bpmMax, energy (low|mid|high|null),
     moods[], genres[], decades[], countries[], artists[], songTitles[],
     modifiers (chiller|faster|happier|sadder|heavier|softer)[], exclude[] ("no rap").
     Each field gets a @Guide with a one-line meaning and a max of 5 items.
   - TranslatedLines: lines: List<String>, same count as the input.
4. Use 1 — queue prompt (Prompt B):
   - MixPromptParser (local) runs first and steers the mix immediately (instant, offline).
   - If Nano is Ready, it runs in parallel. When it returns within the timeout, merge it: Nano
     fills empty fields and adds artists/genres the parser missed; the local values win on
     conflicts with explicit numbers. If the merged intent is different, re-steer the mix once
     (refreshFuture keepAutomatic = 1, same as N11).
   - The toast shows the merged labels. A small "✦ on-device" suffix shows when Nano contributed.
   - Warm up Nano when the queue options menu opens (not at app start).
5. Use 2 — AI playlist (N6): same MixIntent → the MusicVibeFilters/local scorer builds the
   playlist from the whole library. Remove the 100-song JSON + digest from the Nano path. Length
   min/max is unchanged.
6. Use 3 — lyrics translation (N8): send only the text lines (no timestamps) in chunks that fit
   the budget (about 30 lines). Get back TranslatedLines and re-attach the original timestamps in
   code. If the count doesn't match → retry that chunk once, then show original lines for it.
7. Prompts (N7): new short prefixes per job (≤ 150 words each, 2 few-shot examples for MixIntent).
   They live in data/ai/nano/NanoPrompts.kt and are used only by Nano. The old long prompts stay
   for nothing else, so delete what becomes unused.
8. Settings card "On-device AI (Gemini Nano)" in the existing AI settings section (N10):
   - Status line from NanoStatus.
   - Primary action: Download (Downloadable), progress (Downloading), "Test" (Ready). Test runs
     the queue example "upbeat 80s rock like Queen" and shows the parsed labels + milliseconds.
   - Unsupported shows the reason: unlocked bootloader / AICore missing / device not supported.
   - M3 roles + MaterialTheme.typography/shapes only. No new colors.
9. Provider enum (N9): keep GEMINI_NANO and LOCAL_FALLBACK. With GEMINI_NANO selected but not
   Ready, everything silently uses local and the card says why.
10. Build: add genai-prompt (G1) to libs.versions.toml + app, the KSP schema compiler (G2), and
    R8 keep rules for @Generable classes and the ML Kit GenAI packages (minify is on, N12). Do a
    release-build smoke test (see project note release-crash-gson-r8-2026-09-29).

Motion (Settings card only):
- Status/action swap: AnimatedContent, fade-through. Enter tween(DurationMedium2,
  EmphasizedDecelerate), exit tween(DurationShort4, EmphasizedAccelerate).
- Progress: LinearProgressIndicator with the M3 default progress animation.
- Test result row: expandVertically + fadeIn with the same enter tokens. Exit uses the exit tokens.
- Interruption: a new status mid-transition retargets from the current value.
- Reduced motion (animator scale 0): snap.

Accessibility: status is a live region (polite). Buttons are ≥48dp with text labels. Progress
reads "Downloading Gemini Nano, N percent".

Don't: send any song data off the device; let Nano output song IDs; block UI on Nano; call Nano
from background work (inference only while the app is in the foreground); add a cloud provider.

Acceptance criteria:
- [ ] On the Pixel 10 Pro, the card goes Downloadable → Downloading → Ready (or shows an exact
      Unsupported reason).
- [ ] Test: "upbeat 80s rock like Queen" returns tempo=fast, decades=[1980s], genres=[rock],
      artists=[Queen] in < 2 s after warmup.
- [ ] Queue prompt: the mix starts instantly from the local parse. Nano refines once when it adds
      something. Works in airplane mode (Nano + local).
- [ ] "chill songs like <library song> but no rap" excludes rap and seeds from that song.
- [ ] AI playlist never returns a song that isn't in the library. Generation < 3 s.
- [ ] Lyrics translation keeps every timestamp and line count.
- [ ] Nano BUSY/quota/timeout → local result with no error toast. Logged once.
- [ ] Release build (minify) runs all three uses without a crash.
- [ ] No reference to simulateAicoreExecution remains.
- [ ] gradlew assembleDebug and assembleRelease pass. Unit tests pass.
```

---

## 3. Plan (copy-ready)

1. **Confirm the API on device:** add `genai-prompt` (G1) and a temporary debug log that prints `checkStatus()` and the model name, plus the FAST/FULL config class names from the beta4 API. Run it on the Pixel 10 Pro – `app/build.gradle.kts`, `libs.versions.toml` (N12) – done when: status and exact class names are written into `NanoEngine.kt` KDoc.
2. **Gradle/R8:** add the KSP `genai-schema-compiler` (G2) and keep rules in `proguard-rules.pro` – done when: `assembleRelease` passes.
3. **NanoEngine interface + NanoStatus** – `data/ai/nano/NanoEngine.kt` – done when: a fake implementation is used in unit tests.
4. **MlKitNanoEngine:** status flow, download flow, warmup, Mutex, timeout, `countTokens` budget, typed `generate` with `promptPrefix` (G1-G3); bind it in Hilt – done when: the step-1 device log shows a real reply.
5. **Remove the mock:** delete the `simulateAicoreExecution` path, make `GeminiNanoClient` delegate to `NanoEngine` or remove it, and fix the `AiHandler` offline gate + token logging (N1-N4) – done when: there's no mock code and Nano works in airplane mode.
6. **Schemas:** `MixIntent`, `TranslatedLines` with `@Guide` limits (G2) – `data/ai/nano/NanoSchemas.kt` – done when: it compiles with KSP.
7. **Prompts:** short prefixes + few-shot examples – `data/ai/nano/NanoPrompts.kt` – done when: each prefix fits within 600 tokens with a typical input (checked with `countTokens`).
8. **Queue prompt:** `MixIntent` ↔ `PromptIntent` mapper + merge rules; parallel Nano refine with one re-steer; warmup on menu open – `MixPromptParser.kt` (Prompt B), `PlayerViewModel.steerMixWithPrompt` (N11), `QueueBottomSheet` options open – done when: AC2-AC4 pass.
9. **AI playlist:** route `AiPlaylistGenerator` through intent → local scorer; drop the 100-song JSON from the Nano path (N6) – done when: AC5 passes.
10. **Lyrics translation:** chunking, typed lines, timestamp re-attach, retry/fallback (N8) – `AiStateHolder.kt:287-315` – done when: AC6 passes on a 60+ line song.
11. **Settings card:** status/download/test UI with the motion + accessibility spec – the AI settings screen + `SettingsAiStateHolder` (N10) – done when: AC1 passes and it looks right in light/dark/album colors at animator 0x/0.5x.
12. **Unit tests:** merge rules, mapper, chunking/re-attach, fallback on null/timeout (fake engine) – done when: tests are green.
13. **Verify:** debug + release builds; AC1-AC9 on the Pixel 10 Pro, including airplane mode, a rapid double-submit (Mutex/cancel), and leaving the app mid-request (foreground rule).

**Risks**
- APIs are beta/alpha (G1, G2), so pin the versions and keep all Nano code behind `NanoEngine` so a breaking update touches one file.
- R8 can strip `@Generable` classes (G2). Step 2 + the release smoke test cover this, and it's the same failure class as the earlier gson/R8 crash.
- Quota (G1): one call per submit, no retries except the single translation-chunk retry. The cache (N4) avoids repeats.
- Unlocked bootloader → `UNAVAILABLE` (G1). If your phone is unlocked for dev work, Nano won't run there.
- Prefix caching is device-dependent (G3). It speeds things up when it's there and is harmless otherwise.

---

## 4. Assumptions
- Uses limited to queue prompt, AI playlist and lyrics translation. Background song tagging (the TAGGING/MOOD_ANALYSIS types) is a follow-up, because inference is meant for foreground use. Affects steps 8-10.
- FAST model preference by default. FULL only if step 1 shows FAST misreads multi-part prompts.
- Nano refines the local parse and never replaces the instant local result. That's your "faster and higher quality" rule.
- The exact Kotlin names for model preference and download status are confirmed in step 1, not assumed.
