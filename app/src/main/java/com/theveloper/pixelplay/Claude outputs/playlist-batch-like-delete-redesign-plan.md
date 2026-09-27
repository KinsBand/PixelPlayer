# Playlist plan: batch like, delete YT Music playlists, new header + full download (2026-09-22)

Three pieces of work on the playlist screen, all built on code that's already in the app. Nothing here is built yet.

---

## 1. Batch select + like (hold a song to start selecting)

**What you get**
- **Hold any song** → selection mode starts (with a small vibration). The song gets a tick and a tinted row.
- After that, **tapping songs selects or unselects them** (it doesn't play them). Hold-and-drag down the list to select a run of songs (optional, phase 2).
- The **top bar changes** to: ✕ (exit) · "12 selected" · **Select all** / **Deselect all**.
- An **action bar slides up at the bottom**, above the mini player:
  - ♥ **Like**. It turns into **Unlike** when every selected song is already liked.
  - ⬇ **Download** selected songs
  - ＋ **Add to playlist**
  - ⏭ **Play next** / **Add to queue**
  - 🗑 **Remove from this playlist**. Only shows on playlists you can edit (local playlists and YT Music playlists).
- After liking, a snackbar says **"Liked 37 songs (5 were already liked) · Undo"**.
- Pressing back or ✕ leaves selection mode. Reorder mode and remove mode are switched off while you're selecting.

**What's already there**
- `MultiSelectionStateHolder` already has `toggleSelection`, `selectAll`, `clearSelection` and **`likeSelectedSongs` / `unlikeSelectedSongs`**. LibraryScreen uses these for the Songs tab.
- `SelectionActionRow` (the Select all row) and `MultiSelectionBottomSheet` can be reused.

**Changes**
| File | Change |
|---|---|
| `PlaylistDetailScreen.kt` | Add a selection state that belongs to this screen only. Hold a row to start selecting, tap to toggle. Swap the top bar for a selection bar and add the bottom action bar. `BackHandler` exits selection. Clear the selection when the playlist changes. |
| `QueuePlaylistSongItem` (components) | Add the `onLongClick`, `isSelectionMode` and `isSelected` parameters. Switch the row to `combinedClickable`, add a tick/checkbox slot and a tinted background when selected. |
| `MultiSelectionStateHolder.kt` | `updateFavoritesForSelection` currently calls `setFavoriteStatus` once per song. Change it to **one batched DB write** (`FavoritesDao.insertAll` / `deleteAll` in one transaction), return how many changed for the Undo, and add an `undoLastFavoriteBatch()`. |
| `FavoritesDao.kt` | Add `@Insert(onConflict = IGNORE) insertAll(list)` and `@Query("DELETE … WHERE songId IN (:ids)")`. |
| `ConnectedLibraryRepository.kt` | `observeFavorites` will now see a single diff of 100+ `yt_` ids. Make `flushLikesNow` **throttle** the YT pushes (about 3 at a time with a short gap) so YouTube doesn't rate-limit them. Failed pushes stay in `pendingLikes` and retry on the next sync, which already happens today. |

**Watch out for**
- The PlayerViewModel's `MultiSelectionStateHolder` is **shared with LibraryScreen**. Use a separate instance for this screen, or clear it in a `DisposableEffect`, so a selection doesn't carry over between screens.
- A YT playlist can contain the same video twice. Selection is by song id, so picking one copy ticks both. That's fine for liking.
- **Select all** acts on the sorted/filtered list you can see, not the raw playlist.

---

## 2. Delete YouTube Music playlists, and the phone remembers

**Problem today:** `deletePlaylist()` returns early for connected playlists, and the delete option is hidden for them (`PlaylistDetailScreen` ~line 916). Even if you removed one, the next `sync()` would import it again from `FEmusic_liked_playlists`.

**What you get**
- **Delete playlist** shows in ⋮ for YT Music (and Spotify) playlists. It also works when you hold playlists in the Library → Playlists tab to multi-select them.
- The confirm dialog:
  - **Remove from PixelPlayer** (the default). It's hidden on this phone and **stays hidden after every sync**.
  - ☐ **Also delete from my YouTube Music account** (only for playlists you own), or
  - ☐ **Also remove from my YT Music library** (for playlists by other people that you saved).
- **Settings → Connected accounts → Hidden playlists** lists removed playlists with a **Restore** button.

**Changes**
| File | Change |
|---|---|
| `ConnectedLibraryRepository.kt` | Add `deletedPlaylists: Map<String, Long>? = null` (remoteId → time removed) and `pendingRemoteDeletes: Map<String, String>? = null` to `ConnectedSnapshot`. Keep both nullable so older saved files still load with Gson, the same way `pendingLikes` does. New `deletePlaylist(id, alsoRemote)` adds the tombstone, drops the playlist and saves. `restorePlaylist(remoteId)` removes the tombstone and syncs. |
| same, `sync()` | Filter the `listed` YT and Spotify playlists by tombstones **before** `preserveYouTube` runs, so no tracks are downloaded for hidden playlists. Flush `pendingRemoteDeletes` at the start of the sync, the same way likes are flushed. |
| `YouTubeMusicRepository.kt` | `deletePlaylist(id)` → `action("playlist/delete", {playlistId})`. `removeSavedPlaylist(id)` → `action("like/removelike", {target:{playlistId}})`. `getPlaylistTracks` also returns **`owned`**: true when the browse response contains `musicEditablePlaylistDetailHeaderRenderer`. |
| `ConnectedPlaylist` | Add `owned: Boolean? = null`. This decides which checkbox shows, and read-only playlists that aren't yours stop showing edit controls. |
| `PlaylistViewModel.kt` | `deletePlaylist()` sends connected playlists to the repository instead of returning early. |
| `PlaylistDetailScreen.kt` | Show Delete for connected playlists and use the new dialog. |
| `ConnectedPlaylistsTab.kt` / Settings | Add the Hidden playlists list with Restore. |

**Notes**
- Deleting on YouTube can't be undone, so that box is **unticked by default**.
- `connected-library-v1.json` is stored in `noBackupFilesDir`, so hidden playlists are lost on reinstall. Optionally add `deletedPlaylists` to the `PlaylistsModuleHandler` backup.
- The `playlist/delete` and `removelike` endpoints need testing on a real device. The code hasn't been compiled either.

---

## 3. New playlist header (YT Music style) + download whole playlist

Based on your screenshot, **without** the profile avatar/name, the bio text and the top-right icon.

```
 ←                                   (transparent bar; shows the title once you scroll past the header)
        ┌───────────────┐
        │   big square  │            about 55% of screen width, 16dp corners, shadow
        │     cover     │
        └───────────────┘
          Playlist Name              large, bold, centred, up to 2 lines
     42 songs · 2 hr 14 min  [YT]    small source badge; no owner, no description
   (⬇)   (⤮)   ( ▶ )   (⇅)   (⋮)
 ─────────────────────────────────
  [art] Walkin' On The Sun         ⋮
        Smash Mouth • 3:26
```

- **Background:** a vertical gradient from the cover's main colour (use `PlaylistCoverColors` / the palette code the player already uses) that fades to the surface colour about 60% of the way down. In the screenshot it's purple → black.
- **Button row:** Download · Shuffle · **Play** (big white circle) · Sort · More. Sync, Edit, Rename, Export, Default transition and Delete all go inside ⋮. For connected playlists, pulling down also syncs.
- **The header scrolls with the songs.** It becomes item 0 of the `LazyColumn`. Right now it's a fixed `Column` above the list. `LargeFlexibleTopAppBar` is replaced by a slim bar that fades in once you scroll past the title.
- **Covers:** connected playlist → `coverUrl`. Local → the existing `PlaylistArtCollage` / `PlaylistCover`. **Liked Songs** → a purple-to-pink gradient square with a thumbs-up, like YT Music.
- Add songs, Remove and Reorder move out of the button row into ⋮, and hold-to-select (section 1) replaces remove mode.
- **Reorder bug to avoid:** once the header is list item 0, `onMove`'s `from.index` / `to.index` go up by 1. Subtract the header count, or the playlist will reorder by the wrong position.

### Download whole playlist
- The **⬇ button has 4 states**:
  - **Not downloaded:** an arrow.
  - **Downloading:** a ring showing progress with "18/42" in the middle. Tap to **cancel**.
  - **Partly downloaded:** the arrow with a dot. Tap to download the rest.
  - **All downloaded:** a filled ✓. Tap to open "Remove downloads" or "Keep in sync".
- **Before a big download:** for more than 50 songs, or when you're on mobile data, show *"Download 212 songs (~850 MB)?"* with a **Wi-Fi only** option. The size estimate is about 4 MB per song.
- **Keep in sync** (on by default after a full download): new songs added to the playlist are downloaded on the next sync.
- Downloads keep going with the screen off and show one notification for the batch ("Downloading *Liked music* · 18/42").

| File | Change |
|---|---|
| `DownloadQueue.kt` | It's already there but nothing calls it. Add a per-song `queuedIds` / `cancelledIds` so **one playlist can be cancelled** (`cancelAll()` today stops every download). Skip songs that are already downloaded or local (`content://`). Expose `progressFor(songIds): Flow<Done/Total>`. |
| `SongDownloadManager.kt` | No change to how a download works (it already skips songs that are downloaded). Use `downloadProgressMap` together with `cloudSongDao` `isDownloaded` for the ring. |
| `PixelPlayDownloadService` / `DownloadNotificationManager` | Run the queue as a foreground service while there's work, with one notification for the whole batch. |
| `PlaylistPreferencesRepository.kt` | `offlinePlaylistIds: Set<String>` for Keep in sync, plus the Wi-Fi only setting. |
| `ConnectedLibraryRepository.sync()` | After a sync, add newly added songs from offline playlists to the download queue. |
| `PlaylistViewModel.kt` | Add `downloadPlaylist`, `cancelPlaylistDownload`, `removePlaylistDownloads` and a `downloadState` flow for the open playlist. |

---

## Build order
1. **Delete + remember** (section 2). It's small, affects the data only, and is easy to test with a sync.
2. **Batch select + like** (section 1). The throttled YT like push is the risky part.
3. **Header redesign** (section 3, top half). Mostly layout work, plus the reorder index fix.
4. **Full playlist download** (section 3, bottom half). The queue, the service and the button states.

## Test checklist
- Like 150 songs from a YT playlist → they're liked in YT Music within a minute or two, with no 429 errors in the log, and **Undo** reverses them.
- Remove a YT playlist → sync 3 times → it doesn't come back. Restore → it comes back.
- Delete a playlist you own on YT → it's gone at music.youtube.com. Remove a saved playlist → it's gone from the YT library only.
- Reorder a song with the new header → it lands in the right place (checks the index fix).
- Download 40 songs → the ring counts up. Cancel halfway → the rest stop and the other download queues keep going. Turn on airplane mode → songs play offline.
- Liked Songs, folder playlists and generated mixes still open. Read-only playlists hide Remove and Delete.

## Open questions
- **Button row:** the plan uses 5 buttons (⬇ ⤮ ▶ ⇅ ⋮). You could match YT exactly with 3 (⬇ ▶ ⋮) and put Shuffle and Sort inside ⋮.
- Should **Remove from PixelPlayer** also work on Spotify playlists? It comes free with the same tombstone.
