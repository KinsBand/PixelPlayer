# Batch 16 build notes: visualisers, Adjust, download quality, friend history, playlist options, blends, queue History/Heard, notification heart (2026-10-10)

This implements `batch16-visualisers-adjust-blends-queue-plan-2026-10-10.md` (all 13 items plus 7.1, 7.2, 10.5 and 10.7). The code was written straight into `PixelPlayer-Clean-`.

**Compiled: no. Run on device: no. Tests: added or updated, not run.**

## What changed, by item

| # | Change | Root cause / decision |
|---|---|---|
| 13 | The notification heart uses `LIKE_ON` / `LIKE_OFF` actions, one per state (`MusicNotificationProvider`, `MusicService`). The old `LIKE` toggle is kept for older controllers and the overlay. | Like used one action string for both states. Shuffle already uses per-state actions and updates correctly. This is a hypothesis until tested on a device. |
| 8 | Audio buffering (shown after 250 ms, so seeks don't flicker) feeds the play/pause button's loading state. The separate spinner chip next to lyrics is gone. The button's content description is now "Loading". | `isLoadingProvider` was only set in video mode. |
| 12 (fix) | New `HeardSongResolver`: searches your library first, then YouTube Music, the same way Search does. Pixel Now Playing titles are resolved before they're added. Approve, Next and Play-now resolve any older entry that has no source. | Now Playing entries were `Song.emptySong()` with a random id: no URI, no YouTube id, so nothing to play. |
| 1, 3, 4, 5 | One shared bars badge, bottom-right on every cover: home grid tiles, a friend's playlist card, blend and custom-blend cards, and the playlist page cover. Bars rest while paused (`NowPlayingPlaylist.isPlaying`, fed by PlayerViewModel). On the Friends Mix button, the play arrow becomes the bars and tapping it pauses or plays. Custom-blend Play now passes its playlist id. | The home grid never marked playlists. The friend card badge sat top-left and always animated. Friends Mix and custom blend weren't tied to a playlist id. |
| 2 | Home > Playlists shows only your own playlists (no `friendId`). A friend's picture shows their saved and unsaved playlists. A pinned friend playlist stays. Friend items no longer use up the mix-tile budget. | Your answer: everything except friends' playlists. |
| 9 | New `FriendPlayMerge`. A new sighting merges into the current play when: it's the same start (±30 s); it's a jump inside the song (a resume or seek, less than the song length minus about 15 s); or the friend was seen paused (within 30 minutes). Only a real replay adds a card. Song lengths come from a cached Spotify search per track id. Old duplicates collapse once on load, and `friend-activity-v1.pre-merge.json` keeps a backup. Cards show "🔥 n" when a song was played 2 or more times that week. | The ±30 s rule split one play into several cards, because the live feed timestamp moves on pause and resume, and `durationMs` was always null. |
| 7, 7.1, 7.2 | The slider is hoisted out of the filters panel (`CoverSliderState`). With a tool open, the ruler sits low, just above the bottom row, with no pill behind it. The bottom row is [value or icon] [Apply] [reset], and the divider hides. Reset counts the value back to its default over 300 ms (Emphasized), so the cover, the number and the ruler all glide; dragging the ruler or switching tool stops it. | Your answer: ruler with no pill. |
| 10 | Edit is a toggle: it shows "Done" with a tick in primary colours, and tapping it again leaves edit mode. | |
| 10.5 | Options sheet: Queue · Soon · Next, then Auto | Export (50/50), then a slim Delete row. Soon slides a panel over the lower rows: a slider for "after song k of N", the song it follows, and a tick (top right) to confirm. A whole playlist is queued as one tagged block (`QueueEntryMetadata.groupId/groupName/groupPlaylistId`). The queue shows a "Then: <playlist>" card before it, and long-press on the card removes the block. When the block is reached, the queue title animates to the playlist's name and that playlist's cover gets the badge. | Export's string is now "Export". Auto is always shown and disabled until something from the playlist is downloaded. |
| 10.7, 11 | Custom blend title = friends' initials ("J · M · K"; one friend shows their name; more than 5 shows "+n"). Members' pictures sit bottom-centre on the cover and open the friend picker when tapped. Blend pages (friend and custom) have the deck [friends] [Edit, centred] [filter/sort]. First use: the card opens the friend list with "Build blend", then the card plays its building animation, then the blend page opens. After that, the card opens the built blend directly. A full-cover building animation (light sweep plus avatars gathering, drawn without recomposition) plays on any blend card that's still building, then crossfades to the built cover. | Changing friends from a custom blend page rebuilds it and swaps the page. From a friend's blend page, that friend starts ticked. |
| 12 (UI) | History and Heard sit side by side. Heard only shows while there are heard songs. Opening one turns the other half into a red-tinted Clear button. There are no ✕ buttons: tap the open half to close it. Heard rows now look like History rows, with ✓ and Next. Tapping a row plays it now, swiping dismisses it, and long-press opens its options. | |
| 6 | The download menu is now a sheet with High / Medium / Low cards, each showing "x songs" already at that quality. It downloads missing songs at the picked quality. "Also change N downloaded songs" re-downloads songs to upgrade or downgrade them; each old file is deleted only after the new one is saved, verified and indexed. | Your answer: no Room migration. `DownloadQualityStore` (a JSON file) records each new download. Older downloads are inferred from their file's container and average bitrate (`classifyDownloadQuality`). |

## Files touched

**New**
- `data/recognition/ambient/HeardSongResolver.kt`
- `data/social/FriendPlayMerge.kt`
- `data/youtube/DownloadQualityStore.kt`

**New tests (not run)**
- `test/.../data/social/FriendPlayMergeTest.kt`
- `test/.../data/youtube/DownloadQualityClassifyTest.kt`
- `test/.../presentation/components/BlendTitlesTest.kt`

**Edited (main source)**
- `data/model/QueueEntryMetadata.kt`
- `data/playback/NowPlayingPlaylist.kt`
- `data/recognition/ambient/AmbientSuggestionController.kt`
- `data/recognition/ambient/NowPlayingNotificationListener.kt`
- `data/service/MusicNotificationProvider.kt`
- `data/service/MusicService.kt`
- `data/social/FriendActivity.kt`
- `data/spotify/web/SpotifyFriendActivitySource.kt`
- `data/youtube/DownloadQueue.kt`
- `data/youtube/SongDownloadManager.kt`
- `presentation/components/BlendsRow.kt`
- `presentation/components/CoverAdjustSlider.kt` (rewritten around the existing ruler)
- `presentation/components/CoverFiltersPanel.kt`
- `presentation/components/PlaylistPlayingBadge.kt`
- `presentation/components/PlaylistsHub.kt`
- `presentation/components/QueueBottomSheet.kt`
- `presentation/components/SpeedDialGrid.kt`
- `presentation/components/player/AnimatedPlaybackControls.kt`
- `presentation/components/player/FullPlayerContent.kt`
- `presentation/screens/CreatePlaylistScreen.kt`
- `presentation/screens/FriendsScreen.kt`
- `presentation/screens/PlaylistDetailScreen.kt`
- `presentation/viewmodel/MediaControllerSyncStateHolder.kt`
- `presentation/viewmodel/PlaybackDispatchStateHolder.kt`
- `presentation/viewmodel/PlayerUiState.kt`
- `presentation/viewmodel/PlayerViewModel.kt`
- `presentation/viewmodel/PlaylistViewModel.kt`

**Edited (resources)**
- `res/values/strings_player.xml`
- `res/values/strings_screens.xml`

**Edited tests (constructor arguments only)**
- `test/.../AmbientSuggestionControllerTest.kt`
- `test/.../PlayerViewModelTest.kt`

**Not touched:** manifest, Room schema, Gradle, ProGuard (the `data.social.**` keep rule already covers the new `FriendTrack` fields), and the settings registry.

## Verification

| Check | Result |
|---|---|
| Compiled | **no** — run `./gradlew :app:compileDebugKotlin`, then `./gradlew :app:testDebugUnitTest` |
| Run on device | **no** |
| ktlint parse (all 30 edited or new main files + 4 test files) | 0 parse errors (style warnings only, same as untouched code) |
| XML well-formed (`strings_player.xml`, `strings_screens.xml`) | pass |
| Every `R.string.*` referenced in the touched files exists | pass (13 new strings) |
| Line endings preserved (CRLF files stayed CRLF) | pass |
| Merge, title, id-parsing and classifier logic re-run as a Python port | all cases pass (extra evidence only) |

## Needs your testing

- **Heart (13):** like or unlike from the notification and from the app, then skip between liked and unliked songs. If the heart still doesn't fill, the next suspect is the media id of streamed songs.
- **Loader (8):** cold-start a streamed song; seek inside a downloaded one (it shouldn't flicker); check video mode.
- **Heard (12):** approve a Pixel Now Playing song and make sure it plays. Check that songs which can't be matched no longer appear in Heard.
- **Friend history (9):** a friend pauses and resumes one song, loops one, and plays across days. Check that old duplicate cards collapse after a restart.
- **Adjust (7):** each tool and Strength; the reset glide; dragging during a reset; Apply closing everything; layout on a small screen.
- **Queueing (10.5):** test all three placements with a Smart or Continuous mix running, the Soon slider at both ends, the title turnover, and removing a block. Landscape shows no turnover card yet.
- **Blends (10.7, 11):** first run versus returning, the 5+ friend title, rebuilding from the page, the building animation, and animator scale 0.
- **Downloads (6):** a new playlist at Low; a mixed upgrade; turning Wi-Fi off mid-upgrade (the old files must keep playing); storage freed after a change.
- **Everywhere:** light and dark themes, and album-art colours.

## Open items

- Landscape queue: the turnover card is only drawn in the portrait list.
- Auto-download ("Keep in sync") still downloads new songs at the default quality, not the one last picked in the menu.
- Some new UI labels follow their file's existing style (hard-coded English: "History", "Heard", "Clear heard", the blend picker, the download sheet). Move them to `strings_*.xml` if you want them translated.
- Opening the friend picker from a friend's blend page ticks that friend in your custom blend straight away, even if you then close the picker.
