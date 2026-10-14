# PixelPlayer: screen open/close animation audit (2026-10-01)

Scope: every NavHost route, the player sheet, lyrics, queue, mini player, drawer, voice search, external player overlay, and all 30 `ModalBottomSheet`s. Not compiled here (no Android build in this environment), so please run a build first.

## What was broken, and the fix

| # | Problem | How to reproduce it | Fix |
|---|---|---|---|
| 1 | **Double-tapping a back arrow blanks the app.** Every back arrow called `popBackStack()` directly. | Open Album from Home, then tap ← twice fast. The 2nd pop removes Home and the NavHost goes empty. With a deeper stack (A→B→C), a double tap skips B. | New `NavController.popBackStackSafely()` (in `NavControllerExtensions.kt`). It never pops the last screen and ignores a second pop during the same transition. All 30 back/close callbacks now use it, including `navigateUp()` in EditTransition. |
| 2 | **Drawer couldn't be closed with Back or a swipe.** `gesturesEnabled = false` and the `ModalDrawerSheet` overload without `drawerState`, so nothing handled Back. | Open the sidebar on Home and press Back. The app closes (or shows the app-lock toast) and the drawer never animates shut. | `ModalDrawerSheet(drawerState = …)` adds M3's predictive-back close. `gesturesEnabled = drawerState.isOpen` lets you swipe it closed, and opening by swipe stays off. |
| 3 | **DJ Space (Mashup) had no close button.** | Open DJ Space. There's no way out except the system Back. | `MashupScreen(onBackClick = …)` adds a back arrow to its TopAppBar. |
| 4 | **Interrupting the player predictive-back left it stuck halfway.** The finish animation could be cancelled (leaving composition, a new back-stack entry, a second Back) before `collapsePlayerSheet()` ran. | Swipe back on the full player, release, and press Back again within about 300 ms. You get jitter, or the player stays half-collapsed and blurred. | Commit/cancel now run in one tracked job with a `NonCancellable` `finally`, so the player always ends fully collapsed or fully reset. The handler is disabled while the commit plays. |
| 5 | **Lyrics enter spring and back gesture fought each other.** They used two separate animations writing the same `backProgress`. | Open lyrics and start a back swipe right away. It flickers or snaps. Also, Back pressed during the exit was swallowed. | A single `Animatable` now drives both. A committed gesture always calls `onBack` once, then stops intercepting. If you reopen the sheet while it's closing, it re-enters cleanly (`isShown` from the `AnimatedVisibility` transition). |
| 6 | **Queue Back could be skipped.** The player handler re-registers on every navigation, which put it above the queue handler. | Open the queue, then let any navigation happen (for example from song info). Back collapses the whole player instead of the queue. | The queue handler is keyed the same way, so it stays on top. Swipe-edge state is reset in `finally`. |
| 7 | **Mini player swipe-dismiss could get stuck.** Touching it again during the 200 ms fly-out cancelled the dismiss, and there was no `onDragCancel`. | Fling the mini player away and tap it immediately. It stays half off-screen and the nav bar is stuck in its "dismissing" corner shape. | The commit can't be interrupted (`NonCancellable` finally). New drags are ignored while it commits. `onDragCancel` springs back to 0. |
| 8 | **Bottom sheets vanished without animating out.** 40+ in-sheet buttons called `onDismiss()` directly, so the sheet was removed on the next frame. Swipe, scrim tap and Back did animate. | Tap "Play" in the album multi-select sheet, "Cancel timer" in the sleep timer, any option in a Settings dropdown sheet, etc. | New `rememberAnimatedSheetDismiss(sheetState, onDismiss)` runs `hide()` and then always calls `onDismiss`, with double-tap protection. Applied to 16 sheets: multi-selection (song/album/genre/playlist), Cast + Bluetooth device, custom EQ presets, playlist add, reorder tabs, song info delete, streaming provider, sleep timer, lyrics "more", radio location, search browse, playlist options, settings option picker. |
| 9 | **External player overlay cut its own exit off.** `onDismiss()` (which finishes the activity) ran in the same frame as `sheetVisible = false`. | Open a file from another app, then press Back or tap outside. | It now plays the 250 ms slide-down and fade first, then dismisses. |
| 10 | **Easter egg ran two exits at once.** It hid its own `AnimatedVisibility` (shrink + fade) while the NavHost slide played. | Close the brick-breaker game. | Only the navigation pop transition runs now. |
| 11 | **Blank screens on bad routes.** A missing `albumId`/`artistId`/`playlistId` rendered nothing, and the Experimental redirect gave up if the entry wasn't STARTED yet. | Use a deep link or a stale route. | These now pop back automatically, and the redirect retries for up to 1 s before popping. |
| 12 | **Push/pop pairs didn't match.** Home/Search/Library used the `enterTransition()` set for pushes and pops while every other route used `aospSharedAxis*`, so leaving a tab and coming back mixed two different motions. | Push from Home to any screen and back. | Root routes now fall back to the same `aospSharedAxis*` transitions as the rest of the NavHost. Tab-to-tab switches are unchanged. |

## Checked and already solid (no change)

- **NavHost predictive back:** nav-compose 2.9.8 and `enableOnBackInvokedCallback="true"`, so screens seek with the gesture. `ScreenWrapper`'s dim/blur already handles cancelled gestures, via `previousBackStackEntry`.
- **Player sheet vertical drag:** `onDragCancel` → `onDragEnd`, and settling is driven through `MutatorMutex`.
- **Queue sheet controller:** collapse already resets in `finally`.
- **Cast sheet custom drag:** cancel springs back to rest, and Back is guarded by `isDismissing`.
- **Voice search sheet:** swipe and Back both drive the shared deck `Animatable`, and it springs back under the threshold.
- **Equalizer sheet in the player:** `AnimatedVisibility` with a matching `BackHandler`.
- **Swipe, scrim tap and Back on every `ModalBottomSheet`:** M3 animates these itself.

## Worth a look later (not changed)

- `rememberSystemReducedMotion()` reads the animator scale once per `Context`, so toggling "Remove animations" doesn't apply until the activity is recreated.
- Nav bar show/hide is 220 ms LinearOutSlowIn, while screen transitions are 350 ms Emphasized. The bar finishes early. `MotionTokens.DurationMedium3` with `EmphasizedDecelerate` would line them up.
- The dismiss-undo bar enters from the bottom but exits upward (`targetOffsetY = { -it }`). Check that this is intentional.
- Friends, Your Music, Practice and Platform Playlists aren't in `routesWithHiddenNavigationBar`, so they keep the nav bar. Fine if that's intended.
- `Screen.SpotifyDashboard` and `Screen.YouTubeMusicDashboard` have no `composable()`. Navigating to them would crash. Nothing calls them today.
- The lyrics sheet has two stacked entrances: the host's `AnimatedVisibility` slide plus its own internal spring. It works, but one of them could be dropped.

## Files changed (43)

New: `presentation/components/AnimatedSheetDismiss.kt`.
Core: `NavControllerExtensions.kt`, `AppNavigation.kt`, `AppSidebarDrawer.kt`, `PlayerSheetPredictiveBackHandler.kt`, `LyricsPredictiveBackHandler.kt`, `LyricsSheet.kt`, `FullPlayerContent.kt`, `UnifiedPlayerSheetV2.kt`, `MiniPlayerDismissGestureHandler.kt`, `ExternalPlayerOverlay.kt`, `MashupScreen.kt`, `EasterEggScreen.kt`.
Back-arrow callers: 15 screens. Bottom sheets: 16 files.

## Test pass to run on a device

1. Double-tap ← on Album, Artist, Settings → Category, Stats and Equalizer. You should land exactly one screen back, never on a blank screen.
2. Open the sidebar on Home, then press Back and also swipe left. Both should animate it closed.
3. In the full player, do a back swipe → release → Back again quickly, then a back swipe → cancel. The player should never stop halfway.
4. Open lyrics and immediately back-swipe. Close lyrics and reopen them mid-exit.
5. With the queue open, tap an artist and then press Back.
6. Fling the mini player away and tap it during the fly-out. Also drag it and pull the notification shade mid-drag.
7. Tap an action button in each sheet listed in #8. Every one should slide down.
8. Repeat 1–7 with Developer options → animator scale at 0.5×, 5× and off.
