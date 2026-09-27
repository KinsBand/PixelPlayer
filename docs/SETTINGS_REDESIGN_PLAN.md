# PixelPlayer — Settings Redesign Plan

**Version** 2.4 · **Date** 2026-09-16 · **Status** Phases 1–3 and 4A complete; 4B onward awaiting a successful build
**Analysed against** the working copy on `desktop-f7ve027` (files dated 2026-09-16), diffed against `cb42660`
**Supersedes** v1.0, which was written against the committed baseline only

---

## 0. What changed since v1.0

v1.0 was written from commit `cb42660` because the working copy was unreachable. It is now connected and has been read in full. **Your uncommitted work resolves roughly half of what v1.0 found.** This version is re-derived from the live code.

Diff size against the baseline, in changed lines:

| File | Δ lines |
|---|---|
| `SettingsScreen.kt` | 914 |
| `SettingsComponents.kt` | 655 |
| `PlaybackSettings.kt` | 280 |
| `SettingsCategoryScreen.kt` | 145 |
| `AppearanceSettings.kt` | 101 |
| `AppNavigation.kt` | 61 |
| `BehaviorSettings.kt` · `LyricsSettings.kt` · `DeveloperSettings.kt` | 37–39 each |
| `LibrarySettings.kt` · `Screen.kt` | 12–14 |
| `SettingsViewModel.kt` · `SettingsCategory.kt` · `BackupSettings.kt` | 0 |

### Already fixed — closed, no action

| v1.0 finding | Status in working copy |
|---|---|
| Crash trigger in root Danger Zone | **Removed.** Three maintenance rows remain (rebuild DB, clear AI logs, reset config) |
| ReplayGain / HiFi hidden in a collapsed disclosure | **Disclosure removed.** Playback restructured into Sound Quality · Queue & Transitions · Audio Output · Background Playback |
| 5 search entries leading to unreachable controls | **Fixed.** Experimental is now a proper indexed row with `Screen.Experimental.route` |
| Lyrics settings indexed as `player` | **Fixed** — immersive lyrics, auto-hide delay and reset-imported now indexed as `lyrics` |
| Multi-artist delimiters indexed as `library` | **Fixed** — removed; `settings_artists_title` added instead |
| ~20 settings missing from the index | **Mostly fixed** — 56 rows now, up from 59 with better coverage (pause-on-volume-zero, crossfade duration, equalizer, artists all added) |
| `remember(searchQuery)` stale-memo bug | **Fixed** → `remember(searchQuery, searchRows)` |
| `aiVal` Gemini-only, `lyricsVal` hardcoded English | **Fixed** — `aiBadge` and `lyricsVal` both use string resources |
| Root section headers hardcoded English | **Fixed** — `settings_group_personalization`, `settings_group_playback_behavior`, `settings_group_system_advanced`, `settings_reset_maintenance_section` |
| Search chrome hardcoded English | **Fixed** — `settings_search_hint` and friends |
| 22 hardcoded chip hex pairs | **Fixed** — `getCategoryColors` now returns M3 role pairs; dynamic colour works |
| `luminance() < 0.5f` dark-mode inference | **Removed entirely** — `isDark` parameter gone from the colour functions |
| Only 4 of 11 root rows showed a value | **Improved** — 6 now (theme, player theme, library, crossfade, lyrics, AI) |
| Switch rows not row-clickable | **Fixed** — `SwitchSettingItem` wraps in a clickable `Surface` |

### Also newly built (not in v1.0)

- `QuickPreferencesHeroCard` — inline segmented theme switcher at the top of the root screen.
- `SettingsSegmentedSelectorItem<T>` — a typed segmented alternative to the bottom-sheet picker.
- `SearchResultRow` with **query-term highlighting** (`rememberHighlightedText`) and **inline toggles** — 20 of the 56 indexed rows can be flipped straight from search results without navigating. Genuinely good.
- **Search deep-link plumbing**: `Screen.SettingsCategory` now carries `?highlight={key}`, `AppNavigation` declares the nullable arg, `SettingsCategoryScreen` provides `LocalHighlightSettingKey`, and `HighlightableSettingItem` implements a 2.2 s fade-out highlight.

---

## 1. Current findings

Severity: **S1** user-visible defect · **S2** measurable friction or risk · **S3** quality debt.

### 1.1 The highlight feature is wired end-to-end and then does nothing — S1

This is the single highest-value fix available right now, because the hard part is already done.

Verified chain:

```
SearchResultRow onClick
  └→ createRoute(categoryId, highlight = row.key)        SettingsScreen.kt:352      ✅
     └→ navArgument("highlight") nullable                AppNavigation.kt           ✅
        └→ SettingsCategoryScreen(highlight = …)         SettingsCategoryScreen:66  ✅
           └→ CompositionLocalProvider(
                LocalHighlightSettingKey provides …)     SettingsCategoryScreen:201 ✅
              └→ HighlightableSettingItem(key = …)       SettingsComponents.kt:86   ✅ exists
                 └→ used by any category file            ❌ ZERO call sites
```

`grep -rn 'HighlightableSettingItem' screens/settings/*.kt` returns **0 results**. The composable is defined, the key is in scope, the animation is written — and no setting row is ever wrapped in it. The user searches, taps a result, arrives at the category, and nothing is highlighted.

Two further gaps in the same feature:

- **No scroll-to.** Even once rows are wrapped, `SettingsCategoryScreen` never scrolls the target into view. A highlighted row below the fold fades out over 2.2 s while off-screen. There is one `item {}` holding the entire category content, so a `LazyListState` scroll won't reach individual rows — the target needs `Modifier.onGloballyPositioned` plus a scroll request, or the category content needs to become real list items.
- **2 of 56 `SettingRow` entries have no `key`**, so `highlight` arrives null and those two can never highlight even after the wrapping is done.

**Effort: ~half a day.** Wrap each row, add scroll-to, fill the 2 missing keys.

### 1.2 Eight search entries still route to the wrong category — S1

Reduced from 11, but the Appearance ↔ Player pair is untouched and is now the *only* remaining routing drift.

| Searched setting | Index says | Actually rendered by |
|---|---|---|
| Default tab | `appearance` | `BehaviorSettings.kt:63` (`player`) |
| Library navigation | `appearance` | `BehaviorSettings.kt:79` (`player`) |
| Player theme | `player` | `AppearanceSettings.kt:105` (`appearance`) |
| Show player file info | `player` | `AppearanceSettings.kt:117` (`appearance`) |
| Album art palette | `player` | `AppearanceSettings.kt:125` (`appearance`) |
| Carousel style | `player` | `AppearanceSettings.kt:133` (`appearance`) |
| Collage pattern | `player` | `AppearanceSettings.kt:185` (`appearance`) |
| Auto-rotate patterns | `player` | `AppearanceSettings.kt:196` (`appearance`) |

The cause is structural, not clerical: `AppearanceSettings.kt` renders four subsections — Global Theme, **Now Playing**, **Navigation Bar**, **Home Collage** — while `BehaviorSettings.kt` renders Gestures & Feedback plus **App Navigation**. "Now playing" and "navigation" settings are split across both files with no clean rule, so whoever maintains the index guesses. Correcting the eight ids is a 10-minute patch; §2.2 fixes the reason it keeps happening.

There is also a smaller title drift: the index row `app_theme` uses `settings_app_theme_title`, but the control in Appearance is now labelled `settings_theme_mode_inline_title`. Search shows the user one label and the destination shows another.

### 1.3 Three routes still render a blank screen — S2

`SettingsCategoryScreen.kt:263–271` — `ABOUT`, `EQUALIZER` and `DEVICE_CAPABILITIES` are still empty `when` branches with a comment. Navigating to `settings_category/about` gives a top bar over nothing. Unchanged from baseline.

### 1.4 450 ms input dead-zone on every settings navigation — S2

Still present in both `SettingsScreen.kt` and `SettingsCategoryScreen.kt`: a full-screen `pointerInput` trap swallowing all events for `TRANSITION_DURATION` (450 ms) after entry, with no visual cue. Unchanged.

### 1.5 The collapsing top bar is still hand-rolled and duplicated — S2

Unchanged from baseline:

- `LaunchedEffect(topBarHeight.value)` writes `collapseFraction` to state every animation frame; that value drives `LazyColumn.contentPadding`, so **the list remeasures every frame of every scroll**. Still the most expensive thing on the screen — and now more so, because `rememberSettingRows()` was made heavier (see §1.6).
- `SettingsCategoryScreen.kt:116` — `val isLongTitle = categoryTitle.length > 13` still decides header height and title line count. In an app shipping Arabic, German, Korean, Russian and Chinese, a character count is not a layout rule.
- Two verbatim copies of the `NestedScrollConnection` block.
- Search bar positioned by absolute padding with a hardcoded `+ 76.dp` compensation on the list.

### 1.6 `rememberSettingRows()` got more expensive — S2

It is still a `@Composable` returning a fresh `listOf(...)`, now building **56 `SettingRow` objects, ~112 `stringResource` lookups, 56 keyword lists, and 20 capturing lambdas** on every recomposition — and it now also calls `collectAsStateWithLifecycle()` internally (`SettingsScreen.kt:1049`). `remember(searchQuery, searchRows)` correctly keys the *filter*, but `searchRows` itself is a new list identity every composition, so the filter re-runs every time too. On a screen that recomposes per scroll frame (§1.5), this runs per frame.

Search matching itself is still plain `String.contains`: no diacritic folding, no token or prefix matching, no debounce, no ranking. "dark mode" does not match "App theme"; "dunkel" matches nothing.

### 1.7 State topology unchanged — S2

`SettingsViewModel.kt` and `SettingsCategory.kt` are byte-identical to the baseline. Everything from v1.0 stands:

- One 58-field `SettingsUiState` in a single `StateFlow`; every category receives the whole thing.
- `SettingsCategoryScreen.kt:83` — the comment *"State Collection (Duplicated from SettingsScreen for now to ensure functionality)"* is still there, and the screen still collects file-explorer state, storage enumeration, AI key/model and transfer progress **for every category**. Opening Lyrics still primes the file explorer and enumerates storage volumes.
- `statsViewModel = hiltViewModel()` (`:70`) constructed for all eight categories; only Developer uses it.
- `FileExplorerDialog` (`:315`) and `BackupTransferProgressDialogHost` (`:312`) composed regardless of category.
- Confirmation state is still four loose `mutableStateOf` vars plus a lambda-in-state.

### 1.8 Accessibility — S1

Measured on the live files:

- **22** `contentDescription = null` across the settings package, including every category chip and chevron.
- **0** occurrences of `toggleable`, `Role.`, `semantics {` or `heading()` in `SettingsComponents.kt`.

Making `SwitchSettingItem` row-clickable was the right move but has made the semantics *worse* in one respect: TalkBack now announces the row as a **button** and the `Switch` as a **separate** focusable switch — two stops, one control, and the row does not announce its on/off state. The correct shape is `Modifier.toggleable(value, role = Role.Switch)` on the row with the `Switch` marked non-focusable.

Also outstanding: `SliderSettingsItem` has no `stateDescription` (values announced as bare numbers, no unit); section headers carry no `heading()`; no `maxLines`/overflow on row titles or the value badge, which competes with the title for width in German, Russian and Arabic.

### 1.9 Localisation — S2

Down from 24+ to **30 measured literals**, but now heavily concentrated:

- **27 in `AiSettings.kt`** — `"Local AI Engine"`, `"Gemini Nano"`, the full privacy paragraph, `"Advanced AI Configuration"`, `"Generation Parameters"`, `"Temperature"`, `"Top P"`, `"Top K"`, `"Max Output Tokens"`, `"Presence Penalty"`, `"Frequency Penalty"` and each one's explanatory sentence, `"Song Data Configuration"`, `"Sample Size"`, `"Digest Detail"`, `"Extended Song Fields"`, `"System Prompt Behavior"`, `"Edit System Prompt"`.
- `SettingsScreen.kt:834` — `"Section: ${getCategoryDisplayName(...)}"` in every search result.
- `getCategoryDisplayName()` (`:1027`) — still a hardcoded English `when`, still **no `"about"` branch** (falls through to the raw route id).
- The "Clear AI Logs" maintenance row still hardcodes its confirmation title, body and action label, while the two rows either side of it use resources.
- `"Reset"` action label in the reset-config confirmation.

`AiSettings.kt` is the one screen that was never localised at all, and it is the most jargon-dense — exactly the content most in need of translation.

### 1.10 Naming — S3

Unchanged. One concept, four names: enum `PLAYER` / id `"player"` / resource `settings_category_behavior_title` ("Behavior") / display name `"Player"` / file `BehaviorSettings.kt`. Likewise `LIBRARY` / `"library"` / "Music Management".

### 1.11 Test coverage — S2

Still **0 of 81** test files touch settings. Nothing prevents the §1.2 index drift from recurring, and nothing would have caught §1.1.

---

## 2. Target design

### 2.1 Principles

1. **One source of truth.** A setting is declared once; placement, search index and deep link all derive from it.
2. **Search is a first-class navigation mode**, not a lookup table — finish the deep-link/highlight work already started.
3. **Answer without navigating** — value badges and inline toggles, both already begun.
4. **Localised and accessible by construction.**

### 2.2 Information architecture

The Appearance/Behavior split is what keeps breaking the index. `AppearanceSettings.kt` already renders its content as four clean subsections; promoting three of them to real categories matches both the code and the user's mental model:

```
Settings
├─ [Quick Preferences card]              ← keep, already built
├─ [search — deep-links + highlights]
│
├─ PERSONALIZATION
│  ├─ Appearance ········ theme · language · corners · blur · scrollbar
│  ├─ Now Playing ······· player theme · palette · carousel · collage · file info
│  └─ Navigation ········ nav bar style/corners/compact · default tab · library nav
│                          + gestures · haptics · tap-to-close   (from Behavior)
│
├─ PLAYBACK & LIBRARY
│  ├─ Library · Playback · Equalizer · Lyrics      ← unchanged, all correct today
│
├─ AI  ·  BACKUP & RESTORE
│
└─ SYSTEM & ADVANCED
   └─ Accounts · Device Info · Developer · About
```

Every one of the eight §1.2 mismatches disappears as a *consequence* of this split rather than as a manual correction: each setting ends up in the category whose file already renders it.

Old route ids (`appearance`, `player`) stay as aliases redirecting to the new ones for at least one release.

### 2.3 The settings registry

Replace the hand-maintained `rememberSettingRows()` with one declarative list that both the screens and the search index read from.

```kotlin
// presentation/settings/registry/SettingsRegistry.kt
@Immutable
data class SettingEntry(
    val key: String,                  // required — no nullable keys (§1.1)
    val category: SettingsCategory,
    val section: SectionId,
    @StringRes val titleRes: Int,     // one label, used by both the row and search (§1.2)
    @StringRes val subtitleRes: Int?,
    @StringRes val keywordsRes: Int,  // localised, comma-separated
    val destination: Destination,     // InCategory | Route(String)
    val inlineToggle: InlineToggle?,  // powers the existing search-result switches
)
```

Built once and memoised in the ViewModel — not rebuilt per composition (§1.6). A unit test asserts registry ↔ composable parity, which makes §1.2 structurally impossible.

### 2.4 Components

`SettingsComponents.kt` is already close. The remaining work is semantics, not structure:

| Component | Change |
|---|---|
| `SwitchSettingItem` | `Modifier.toggleable(role = Role.Switch)` on the row; `Switch` non-focusable; one TalkBack stop that announces state |
| `SliderSettingsItem` | `stateDescription` with unit; D-pad/keyboard stepping |
| `SettingsSubsectionHeader` | `heading()` semantics |
| `SettingsItem` / `MainSettingsCategoryRow` | `maxLines` + overflow; meaningful `contentDescription` on chips |
| `HighlightableSettingItem` | **wire it up** — wrap every row; add scroll-to |
| `SettingsConfirm` (new) | One sheet replacing the three current patterns; severity levels; typed confirmation for irreversible actions |
| `SettingsScaffold` (new) | `TopAppBarScrollBehavior`; kills both hand-rolled top bars and the `length > 13` rule |

---

## 3. Phased plan

### Phase 1 — Finish what's started · ✅ COMPLETE (2026-09-16)

*Highest value per hour in the whole plan. No new architecture.*

1. **Wrap every setting row in `HighlightableSettingItem(key = …)`** across all eight category files.
2. **Add scroll-to-highlight** in `SettingsCategoryScreen` — `onGloballyPositioned` on the target + scroll request on arrival.
3. **Add the 2 missing `key` values** in `rememberSettingRows()`.
4. Correct the 8 wrong `categoryId` values (§1.2) as an interim fix; Phase 4 removes the cause.
5. Align the `app_theme` index title with `settings_theme_mode_inline_title`.
6. Guard the three empty `when` branches — redirect to the real routes instead of rendering blank.
7. Add the `"about"` branch to `getCategoryDisplayName`.

**Acceptance** — searching any of the 56 settings, tapping the result, and landing on the target row scrolled into view and visibly highlighted; no route renders an empty body.

### Phase 2 — Accessibility · ✅ COMPLETE (2026-09-16)

1. `toggleable` + `Role.Switch` on switch rows; `Switch` non-focusable.
2. `stateDescription` on sliders; `heading()` on section headers.
3. Replace the 22 `contentDescription = null` on meaningful icons; keep `null` only on genuinely decorative ones.
4. `maxLines` + overflow on every row text; value badge constrained so it cannot squeeze the title.
5. Verify with Accessibility Scanner and TalkBack at 200 % font scale.

**Acceptance** — one focus stop per control, announced with role and state; Accessibility Scanner clean; no truncation at 200 % in de/ru/ar.

### Phase 3 — Localise `AiSettings.kt` · ✅ COMPLETE (2026-09-16)

Extract all 27 literals plus `"Section: "`, the Clear-AI-Logs confirmation strings, and the `"Reset"` label. Replace `getCategoryDisplayName` with `stringResource(category.titleRes)`. Add `TODO` placeholders for the 12 locales — Android falls back per-key, so this never crashes.

**Acceptance** — zero hardcoded user-facing strings in the settings package, lint-enforced.

### Phase 4 — Registry and IA · 4A ✅ COMPLETE (2026-09-16) · 4B deferred

1. Introduce `SettingsRegistry`; declare all settings; drive both screens and search from it.
2. Split Appearance/Behavior into **Appearance · Now Playing · Navigation** (§2.2); keep old ids as aliases.
3. Delete `rememberSettingRows()`; memoise the index in the ViewModel (§1.6).
4. Add the registry-parity test.

**Acceptance** — index and implementation cannot diverge; parity test green; every alias route resolves; scrolling the root allocates no `SettingRow` per frame.

### Phase 5 — Scaffold and responsiveness · ~2 days

1. `SettingsScaffold` on `TopAppBarScrollBehavior`; delete both hand-rolled top bars, the per-frame `contentPadding` write, the `length > 13` rule and the `+ 76.dp` magic number.
2. Remove the 450 ms input trap; rely on the navigation library's own gating.
3. Add `PredictiveBackHandler`.
4. Single `SettingsConfirm` component; migrate the three current patterns.

**Acceptance** — zero dropped frames scrolling the settings root on a mid-tier device; settings is interactive the moment it appears; one confirmation component in the tree.

### Phase 6 — State · ~2 days

1. Split `SettingsUiState` into per-category slices, each its own `StateFlow`.
2. Split `SettingsViewModel` into a shell plus `LibrarySettingsViewModel`, `AiSettingsViewModel`, `BackupViewModel`.
3. Scope `statsViewModel`, `FileExplorerDialog` and `BackupTransferProgressDialogHost` to the categories that use them; delete the "Duplicated… for now" block.
4. Hoist confirmation state into one `rememberSaveable` model.

**Acceptance** — opening Lyrics performs no storage enumeration and constructs no `StatsViewModel`; a single preference change recomposes only its own category.

### Phase 7 — Search quality · ~1.5 days

Diacritic folding, token and prefix matching, ranked results (title > keyword > subtitle), 120 ms debounce off the composition thread, localised `keywordsRes`, category chip filters, recent searches, a real empty state. Keep the existing query highlighting and inline toggles — both are good.

**Acceptance** — "dark", "dunkel", "vibration", "replaygain", "temperature" each reach the exact control in every shipped locale.

### Phase 8 — Verification · ~1 day

1. Registry-parity test (makes §1.2 impossible).
2. Search-routing test: every entry's title navigates to a screen containing it, highlighted.
3. Lint rule: no string literal reaches a `Text` in the settings package.
4. Compose UI tests for toggle, choice, slider, confirm, with semantics asserted.
5. Macrobenchmark: settings scroll jank before/after.
6. Screenshot tests — 2 themes × 3 locales (en, de, ar) × 2 font scales.

**Total ~12.5 days**, of which **Phases 1–3 are ~3 days** and clear every S1.


---

## 3A. Phase 1 report — delivered 2026-09-16

### Correction to §1.1

v2.0 claimed `HighlightableSettingItem` had **zero call sites**. That was wrong. The grep was for the composable's name inside the category files, but the wiring is done properly — through a `settingKey: String?` parameter on each shared component (`SettingsItem`, `SwitchSettingItem`, `ThemeSelectorItem`, `SettingsSegmentedSelectorItem`, `SliderSettingsItem`, `ActionSettingsItem`), each of which wraps itself. 52 call sites across the category files already passed a `settingKey`. That is a better design than per-call-site wrapping.

The real defect was narrower and harder to see: **key drift between the search index and the implementation.** 46 of 54 indexed rows highlighted correctly; 8 did not, because the two sides had independently chosen different names for the same setting, or the implementation side had no key at all.

### Changes made

**Highlight key drift — 5 renames** (index → implementation)

| Index key (before) | Now |
|---|---|
| `album_art_palette` | `palette_style` |
| `folder_back` | `folder_back_gesture` |
| `navbar_corner` | `navbar_corner_radius` |
| `player_file_info` | `show_player_file_info` |
| `tap_closes` | `tap_bg_closes` |

**Highlight keys never implemented — 3 wired**

- `export_backup`, `import_backup` → `settingKey` added to both `ActionSettingsItem` calls in `BackupSettings.kt`.
- `refresh_library` → `RefreshLibraryItem` given a `settingKey` parameter and a `HighlightableSettingItem` wrapper in `SettingsComponents.kt`; passed from `LibrarySettings.kt`.

**Scroll-to-highlight — new**

All category content lives inside a single `LazyColumn` item, so `animateScrollToItem` cannot target a row. Implemented instead as: `HighlightableSettingItem` reports its window position once via `onGloballyPositioned`, and the host scrolls by the delta with `animateScrollBy`. Plumbed through a new `LocalHighlightScrollRequest` CompositionLocal.

Deliberately built on stable APIs. `BringIntoViewRequester` would have been the idiomatic choice, but the codebase has no precedent for `androidx.compose.foundation.relocation` and the project is on a very recent Compose BOM (2026.06.00) — not worth the compile risk for this.

Two details worth knowing:
- The one-shot scroll is guarded by a `scrollRequested` flag, because `onGloballyPositioned` fires again during the scroll animation it triggers.
- The rest position targets `maxTopBarHeightPx + 48px`, not the collapsed height, because a programmatic scroll bypasses the nested-scroll connection and therefore does not collapse the top bar.

**Category routing — 8 corrected**

`default_tab` and `library_navigation` → `player`; `player_theme`, `show_player_file_info`, `palette_style`, `carousel_style`, `collage_pattern`, `auto_rotate_patterns` → `appearance`.

**Blank routes — fixed**

`SettingsCategoryScreen` now redirects `about` / `equalizer` / `device_capabilities` to their real screens via `navigateSafelyReplacing`, popping the category entry so Back still returns to the settings root. The three `when` branches are collapsed to a single `-> Unit` with a comment explaining they are unreachable and kept only for exhaustiveness.

**Also**

- `dev_test_setup` added to the search index (was implemented but unsearchable).
- `app_theme` index title aligned to `settings_theme_mode_inline_title`, the label the control actually shows.
- `getCategoryDisplayName` given its missing `"about"` branch.
- `accounts` and `device_capabilities` given keys for Phase 4 registry consistency.

### Verification

- **Parity audit, scripted**: 57 indexed keys vs 55 implemented. The only two indexed-without-implementation are `accounts` and `device_capabilities`, both of which navigate by explicit `route` — correct by design.
- **Routing audit, scripted**: for every in-category row, the declared `categoryId` was checked against the file that actually renders that `settingKey`. **52 correct, 0 mismatches.** 5 further rows carry an explicit route and do not use the category for navigation.
- **Syntax**: all five files parse clean under ktlint 1.5.0 (Kotlin PSI parser), verified against a deliberately broken control file to confirm the check has teeth.
- Not verified: full compilation and on-device behaviour. No Android SDK or Gradle in this environment — **please build and run once before trusting the highlight scroll geometry**, which is the one change whose correctness depends on runtime layout.

### Files changed

| File | Changed lines |
|---|---|
| `screens/SettingsScreen.kt` | 38 |
| `screens/SettingsComponents.kt` | 52 |
| `screens/settings/SettingsCategoryScreen.kt` | 65 |
| `screens/settings/BackupSettings.kt` | 2 |
| `screens/settings/LibrarySettings.kt` | 1 |

Written directly to the working copy with mtime guards; no concurrent edit was overwritten.


---

## 3B. Phase 2 report — accessibility, delivered 2026-09-16

### Correction to §1.8

v2.0 listed **"22 `contentDescription = null` on meaningful icons"** as an S1 defect. Audited all 22 with surrounding context: **the finding was wrong.** Every one sits on a decorative icon beside text that already carries the meaning — category chips next to their titles, chevrons on clickable rows, the warning glyph above a titled sheet, button icons beside button labels. `null` is correct Compose practice there; filling them in would make TalkBack read *"Palette, Appearance, Customize the look…"* instead of *"Appearance, Customize the look…"*. Both genuinely icon-only controls in the package (`settings_search_cd_clear`, `common_clear_search`) already had descriptions. **No content descriptions were changed.**

### What was actually broken, and is now fixed

| Defect | Fix |
|---|---|
| Switch rows announced **no state**. The row used `.clickable {}` while the `Switch` had `onCheckedChange = null`, so TalkBack said "title, subtitle, button" and never "on"/"off" | `Modifier.toggleable(value, enabled, role = Role.Switch)` — one focus stop, correct role, state announced |
| Search-result **inline toggles** (20 rows) had the same hole via `Surface(onClick)` | Restructured onto `Modifier.toggleable`; `clip` + `LocalIndication` preserve the ripple and corner shape |
| Sliders read as bare percentages | `contentDescription = label` + `stateDescription = valueText(value)` → "Minimum song duration, 15s" |
| No heading navigation | `heading()` on all three header composables (`SettingsSection`, `SettingsSectionHeader`, `SettingsSubsectionHeader`) |
| The AI **Advanced Configuration** disclosure never announced expanded/collapsed | `expand {}` / `collapse {}` semantics actions — framework-localised labels, so it works in all 12 locales with no new strings |
| Root row `currentValue` badge had **no width constraint** and could squeeze the title to nothing (German/Russian run ~40% longer) | Capped at 120 dp, `maxLines = 1`, ellipsised, end-aligned |
| Row titles unbounded | `maxLines = 2` + ellipsis on `SettingsItem`, `SwitchSettingItem`, `ThemeSelectorItem`, `MainSettingsCategoryRow`. **Subtitles deliberately left to wrap** — truncating those hides information |

### Note on the inline-toggle fix

The first attempt overrode the role with a `semantics { role = Role.Switch; toggleableState = … }` block layered over `Surface`'s own `onClick`. That was backed out: whether an outer `semantics` modifier wins over `Surface`'s internal `clickable` depends on modifier-ordering behaviour that cannot be confirmed without running a semantics test. Driving the input from `Modifier.toggleable` directly removes the ambiguity.

### Files changed

`SettingsScreen.kt` · `SettingsComponents.kt` · `SettingsSection.kt` · `AiSettings.kt`

Delivery note: the device disconnected mid-commit and the files were written on reconnect, with mtime guards intact.

---

## 3C. Phase 3 report — localisation, delivered 2026-09-16

### Changes

**34 new string resources** added to `strings_settings.xml` (634 → 668 keys), in two commented blocks.

- **`AiSettings.kt` — 28 literals extracted.** The whole advanced surface: `Local AI Engine`, `Gemini Nano`, the on-device privacy paragraph, `System Prompt Behavior`, `Edit System Prompt`, `Advanced AI Configuration` + subtitle, `Generation Parameters`, all six sampling parameters (Temperature, Top P, Top K, Max Output Tokens, Presence Penalty, Frequency Penalty) with their explanations, `Song Data Configuration`, `Sample Size`, `Digest Detail` + its two option labels, `Extended Song Fields`. This was the only settings screen never localised at all, and the most jargon-dense.
- **`SettingsScreen.kt` — 5 literals extracted.** `"Section: %1$s"` on every search result, plus the Clear-AI-Logs confirmation title/body/action and the reset-config action label, which were the odd ones out among rows that already used resources.

**`getCategoryDisplayName` rewritten.** Was a hardcoded English `when` over 12 ids. Now resolves `SettingsCategory.fromId(id)?.titleRes` through `stringResource`, with Accounts as the single explicit case (it has its own screen and no enum entry). This localises the search-result section label *and* removes a second source of truth — the name in search results can no longer drift from the title on the row.

### Verification

- **Zero hardcoded user-facing literals** remain in the settings package. Scripted check across `SettingsScreen.kt`, `SettingsComponents.kt` and all of `screens/settings/` returns nothing (animation `label =` identifiers excluded — those are not user-facing).
- **XML well-formed**, 668 strings, **no duplicate names**.
- **All 325 `R.string.*` references** used by settings code resolve against the defined resource set.
- ktlint parse clean on all files.

### Not done

The 12 locale files were not touched. New keys fall back to English per-key, which is Android's normal behaviour and never crashes — translation is a separate, human task.


---

## 3D. Phase 4A report — the settings registry, delivered 2026-09-16

### Why Phase 4 was split

The plan's §2.3 said "each category composable renders **from** the registry." Reading the
category files closely, that is not achievable without rewriting them. These settings are
genuinely heterogeneous — sliders with per-setting ranges and units, conditionally visible
sub-rows, custom dialogs, activity-result launchers, a file explorer. Generating them from
a data table would be a rewrite that loses behaviour, not a refactor.

The valuable half is separable, and it is the half that stops the drift:

- **4A (done)** — the registry as the single source of truth for setting *metadata*
  (key, category, title, subtitle, route, keywords), with composables still hand-written
  but declaring their `settingKey`, and a test that fails the build when the two disagree.
  This delivers 100% of the anti-drift benefit and the §1.6 performance fix, and touches
  exactly one production file.
- **4B (deferred)** — splitting Appearance/Behavior into **Appearance / Now Playing /
  Navigation**. This needs new enum entries, new string resources, new content files,
  composable blocks moved between files, root-screen changes and route aliases. It is the
  right change, but doing it on top of four phases that have not yet been compiled would
  make any build failure hard to attribute. It should follow a green build.

### What shipped

**`presentation/settings/SettingsRegistry.kt`** — new, 477 lines, 57 entries.
`SettingEntry` is deliberately plain data: no Compose types, no resolved strings, no
lambdas. That is what makes it testable on the JVM without Robolectric.

**`rememberSettingRows()` rewritten** — 476 lines removed, 76 added. It now reads the
registry, resolves the static text once per configuration, and binds the 20 inline
toggles through a `toggleBindingFor` lookup kept next to the ViewModel. Previously it
allocated 57 rows, ~114 `stringResource` lookups and 20 capturing lambdas **on every
recomposition** — on a screen whose collapsing header recomposes per scroll frame (§1.5).
Static text now survives every state change; only the toggle bindings rebuild, and only
when the state they read changes.

**`SettingsRegistryTest.kt`** — new, 7 assertions. Six are pure data. The seventh reads
the category source files and cross-checks every `settingKey = "…"` against the registry.
A source scan is deliberate rather than clever: the drift lives *between* the registry and
the composables, so a test that only inspects the registry cannot see it. Failure messages
name the consequence, not just the mismatch — *"search would open the wrong screen"*.

The seven assertions cover: unique keys · snake_case keys · every `categoryId` resolves to
a real `SettingsCategory` · `byKey` completeness · **every in-category entry is rendered by
the category it declares** · every rendered setting is in the registry · routed entries
that also appear in a category declare that category.

### Verification

- **Metadata parity: byte-identical.** The registry was generated by parsing the existing
  index rather than transcribed by hand, then parsed back and diffed field-by-field
  against the original 57 rows — key, category, title resource, description resource,
  route and keywords all match exactly. No behaviour change.
- **Toggle coverage: 20/20**, no missing and no extra cases.
- **All 7 test assertions simulated against the real sources: pass.**
- ktlint parse clean; brace balance verified.

### One assertion was wrong, and the code was right

The first draft asserted that a routed entry is never also rendered inside a category.
That failed on `palette_style`, `equalizer` and `lyrics_experimental` — and the test was
wrong, not the code. Those three are legitimately both: a row inside a category *and* a
link to their own screen. The assertion was replaced with the useful version — if a routed
entry does appear in a category, the declared category must be the one rendering it, since
that is what the "Section:" label in search results names.

### Still not compiled

Phases 1–4A have not been built or run. Four phases of unbuilt changes is the practical
limit; **4B should not start until this compiles.**

---

## 4. Sequencing

| Priority | Order |
|---|---|
| Fastest visible win | **1 → 2 → 3** (~3 days, all S1 closed) |
| Lowest risk | 1 → 2 → 3 → 8, then 4 → 5 → 6 → 7 |
| Recommended | 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 |

Phase 1 depends on nothing and can ship today. Phases 2 and 3 are independent of each other and of 1.

---

## 5. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Further uncommitted edits land mid-implementation | Medium | Re-stage and diff before each phase; phases are small and file-scoped |
| Category split breaks existing deep links | Medium | Old ids kept as redirecting aliases for one release |
| Registry refactor destabilises a working screen | Medium | Migrate category by category; parity test gates each |
| 12 locales fall behind new AI strings | High | Ship English + `TODO`; per-key fallback, never a crash |
| Scroll-to-highlight fights the collapsing top bar | Medium | Sequence Phase 1 before Phase 5, then re-verify after the scaffold lands |
| Splitting `SettingsUiState` misses a consumer | Medium | Keep the aggregate flow as a deprecated derived value for one release |

---

## 6. Out of scope

Preference storage and keys · `UserPreferencesRepository` · backup file format · Equalizer DSP · Accounts provider integrations (note: the working copy has swapped Netease/QQ/Navidrome/Jellyfin dashboards for Spotify/YouTube Music stubs that currently just `sendToast` — separate concern, flagged not planned) · Device Capabilities detection · `SetupScreen`.

---

## 7. Decision requested

Approve the phase list and sequencing, or adjust. On approval I start with Phase 1 — wiring up the highlight feature you already built — and report back per phase.
