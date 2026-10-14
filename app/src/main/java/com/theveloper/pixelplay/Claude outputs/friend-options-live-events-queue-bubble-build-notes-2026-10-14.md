# Friend profile options, avatar viewer, right-side reorder, Live events, queue bubble: build notes (2026-10-14)

**Compiled: no. Run on device: no.**

## 1. Profiles (friend history sheet)
- **Not following someone:** a **Follow** button sits top-right of their header card (follows on Spotify).
- **Following someone, or a friend in your list:** an **options** button (⋮) sits top-right instead. It opens a bottom sheet with three side-by-side tiles:
  - **Follow / Unfollow**, on Spotify. The tile is off for services without follows.
  - **Block**: tap once to arm it ("Tap to block", with a one-line explanation), tap again to block. Blocking unfollows on Spotify, hides them from Friends (their saved playlists too), stops play-along, and keeps them out of followers / following lists. Restoring them from Accounts → Hidden also unblocks them. The block list is new and stored on the phone: `blockedFriends` in the connected-library snapshot.
  - **Hide**: the existing "hide friend" (Accounts → Hidden).
- **Play along:** the old "Follow" button on a friend's history was actually play-along (songs they start play next). It's now a switch under the tiles for friends in your list, so nothing is lost.
- **Full-width Shuffle:** the action row is now one full-width Shuffle button.
- **Tap a profile picture** (friend history header or anyone's profile header) to open it full screen.
  - Pinch to zoom (1–5×), drag to move, double-tap to zoom in or reset, tap outside or Close to leave.
  - **Download** saves it to Pictures/PixelPlayer (MediaStore, the same helper the stats share card uses).
- **Tab label:** "Top this week" is now **"Top"**.

## 2. Reorder handle on the right
- **Queue and playlist rows:** the shared row (`QueuePlaylistSongItem`, used by the queue and playlist pages) has its drag handle at the end. Album art always keeps its 12 dp start padding.
- **Library tab reorder sheet:** handle moved to the right of the label.
- **EQ presets reorder sheet:** handle moved after the visibility toggle.
- **Not changed:** the cover-layer strip, a horizontal row of chips where the handle sits under each chip.

## 3. Search: "Your charts" → **Live events**
- **The card:** "Live events · Your artists on tour", with a calendar icon.
- **The new screen:** upcoming shows for your 12 most-played artists of the last 30 days (Library insights), from the same Ticketmaster tour lookup the artist page uses (cached a day per artist, 3 at a time), merged and sorted by date.
  - Shows are grouped by month, with artist filter chips.
  - Tap a show to open its ticket page; tap the artist's picture to open their page.
- **States:** loading (Expressive loading indicator), offline / online extras off (goes through `NetworkAccessPolicy`, Enrichment), no tour key in this build, no listening yet, no shows.
- The Stats screen is still reachable from its other entry points.

## 4. Queue / Next / Soon bubble
- **Where:** a pill grows up out of the mini player, centred above it in the mini player's own colour, joined by a short neck that flares into the mini player's top edge.
- **What it shows:**
  - The action as its header: **Next**, **Queue**, or **Soon** ("Soon · after 3 songs").
  - Then the cover, title and artist.
- **Motion:** it grows in with a low-bounce spring, stays 2.4 s, then sinks back. A new action swaps its content in place. The layout reads the sheet position in the layout phase (no per-frame recomposition), and it respects Remove animations.
- **Covers:** song Queue / Next / Soon (sheet buttons, Soon slider, queue swipes) and whole playlists queued (playlist name, its first cover, "n songs · shuffled").
- **What it replaces:** the old "Moved to…" / "… plays after n songs" / playlist toasts.
- **When the mini player isn't showing** (full player open), a short toast says the same thing. While the queue sheet is open nothing extra shows, since the list changes in front of you.
- Friend and follow additions (not your own picks) stay quiet, as before.

## Files
- **New:**
  - `presentation/components/FriendProfileOptions.kt` (options button, Follow button, options sheet, `AvatarViewer`)
  - `presentation/components/player/QueueActionBubble.kt`
  - `presentation/viewmodel/LiveEventsViewModel.kt`
  - `presentation/screens/events/LiveEventsScreen.kt`
- **Edited:**
  - Friends: `FriendHistorySheet`, `FriendsViewModel` (`blockPerson`, `hidePerson`, blocked filter on people lists), `ConnectedLibraryRepository` (`blockedFriends`, `blockFriends`, unblock on restore)
  - Search and navigation: `Screen` (LiveEvents), `AppNavigation`, `SearchHomeContent`, `SearchScreen`
  - Reorder handles: `QueueBottomSheet`, `ReorderTabsSheet`, `ReorderPresetsSheet`
  - Queue bubble: `UnifiedPlayerSheetV2`, `PlayerViewModel`
- **Strings:**
  - `strings_library.xml`: friend_options_*, avatar_viewer_*, friend_sheet_tab_top → "Top"
  - `strings_screens.xml`: live_events_*, queue_bubble_*

## Verification
| Check | Result |
|---|---|
| Compiled / run | **No / No** |
| ktlint 1.3.1 parse, 16 Kotlin files | No parse errors |
| Strings | All values XML well-formed, no duplicate names, every new string / plural defined |
| Line endings | No CR CR LF anywhere; edited files keep their own endings |

## Please run
`./gradlew :app:compileDebugKotlin`

## Needs on-device testing
- **Friend history:** ⋮ opens the sheet, Follow/Unfollow flips on Spotify, Block needs two taps and closes the sheet, Hide closes it, the Play along switch works, Shuffle is full width.
- **Someone you don't follow** (from followers / following): the Follow button shows and turns into ⋮ once followed.
- **Picture viewer:** zoom, pan, double-tap, Download (check Pictures/PixelPlayer).
- **Reorder:** drag from the right handle in the queue, a playlist, the Library tab sheet and the EQ presets sheet.
- **Search → Live events:** loading, then shows (needs the Ticketmaster key in the build). Ticket links and artist pages open.
- **Queue bubble:** Queue / Next / Soon from song options, queue swipes and a playlist. Check the neck lines up with the mini player at different nav-bar states, and the toast fallback with the full player open.

## Assumptions
- **"Follow" in the options sheet** means following on Spotify. The old button was play-along, which kept a switch in the sheet.
- **"Block"** is local to PixelPlayer (there's no Spotify block call). Undo it from Accounts → Hidden.
- **"Live events"** is your top artists' tour dates; there was no events screen before.
