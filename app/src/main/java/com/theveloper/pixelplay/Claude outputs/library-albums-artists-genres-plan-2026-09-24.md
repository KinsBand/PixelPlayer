# Library: Albums, Artists and a new Genres tab (plan, 2026-09-24)

**Status:** plan only. Nothing is changed in the code yet.
Based on a read of `LibraryStateHolder`, `MusicDao` (album and artist queries), `MusicRepositoryImpl`, `SongDownloadManager`, `SyncWorker`, `CloudSongDao`/`CloudSongEntity`, `ConnectedLibraryRepository`, `LibraryMediaTabs`, `LibraryScreen`, `SearchScreen` and both `LibraryTabId` enums.

---

## 1. Review: why Albums and Artists don't keep up

The tabs themselves are wired correctly. They page from Room (`getAlbumsPaginated`, `getArtistsPaginated`, cached in `LibraryViewModel`), so they refresh by themselves whenever the `songs`, `albums`, `artists` or `song_artist_cross_ref` tables change. The problem is what goes into those tables.

| # | Finding | Effect | Where |
|---|---|---|---|
| 1 | **A library sync deletes downloaded songs.** A download is written to `songs` with `source_type = 0` (LOCAL) and an id of `songId.hashCode()`. `SyncWorker`'s deletion phase compares `getAllMediaStoreSongIds()` (every `source_type = 0` row) with MediaStore's `_ID`s. A download's hash id is never a MediaStore id, so every INCREMENTAL or FULL sync removes it as "deleted", and a REBUILD wipes it too. | A download shows up in Albums, then vanishes after the next background sync. The "Liked songs (downloaded)" playlist points at ids that no longer exist. | `SyncWorker.kt` ~L170–190, `SongDownloadManager.kt` ~L246–313 |
| 2 | **Downloads never get `song_artist_cross_ref` rows.** The Artists queries (`getArtistsPaginated`, `getArtistsWithSongCountsFiltered`) join through the cross-ref table. | Downloaded songs never appear in the Artists tab or its song counts, even before a sync removes them. | `SongDownloadManager` insert block |
| 3 | **The album id is `albumTitle.hashCode()`.** It ignores the artist, and a song with no album falls back to the placeholder "YouTube Music". | Albums with the same name by different artists ("Greatest Hits", "Live") merge into one. Every download without an album lands in a single "YouTube Music" album. | `SongDownloadManager` ~L258–260 |
| 4 | **The artist id is `artist.hashCode()`**, and the whole artist string is one artist. | "Mötley Crüe" from a download and "Mötley Crüe" from a local file become two artists. "A, B feat. C" becomes one artist and ignores the delimiter settings. | same |
| 5 | Parent rows are inserted with `IGNORE`. | When a later download has better art, year or album artist, the album row keeps the first, worse values. | same |
| 6 | **The directory filter hides downloads.** The queries only let rows through if `songs.id < 0` or `parent_directory_path IN allowedDirs`. Downloads have positive ids and live in `Android/data/…/files/Music/PixelPlayer`. | With any allowed-folders rule set, downloads disappear from Albums and Artists. | every album/artist query in `MusicDao` |
| 7 | **Liked songs that stream (in-app likes, YouTube/Spotify/Apple likes) are never in Albums or Artists.** They live in `cloud_songs` ⋈ `favorites` and in the in-memory `ConnectedLibraryRepository.snapshot`, and none of the album/artist queries read either one. | Liking something has no effect on Albums or Artists. Only Liked updates. | `UnifiedLibraryRepository`, `ConnectedLibraryRepository.likedSongs` |
| 8 | Deleting a download has no reconcile step. | Empty album or artist rows can be left behind (they're hidden by the `INNER JOIN songs`, but they also block later `IGNORE` inserts that would have better data, see #5). | — |
| 9 | Artist image prefetch only runs from the non-paged `getArtists()` flow, but the tab uses the paged flow. | New artists (particularly from downloads) keep a blank avatar until something else triggers the prefetch. | `MusicRepositoryImpl.getArtists` vs `getPaginatedArtists` |
| 10 | `minTracksPerAlbum` also applies to downloads. | With the setting above 1, single-track downloads never show. This is intended, but the setting doesn't mention it. | Settings copy |

`MediaScannerConnection.scanFile` on the download: on Android 11+, `Android/data` isn't indexed, so it does nothing there. On older versions it can index the file a second time as a real MediaStore song, which gives a duplicate. **Check on the device.**

---

## 2. Target model: one "collection" that feeds every tab

The rule is **Library = local files + downloads + everything you've liked (in-app and on connected platforms).**

- `cloud_songs` becomes the one table for everything in the collection that isn't a MediaStore file. It gets these new columns (Room migration):
  - `album_artist`, `genre`, `year`, `track_number`, `disc_number` (filled by `SongMetadataGatherer`, which already fetches them)
  - `liked_at` (copied from `favorites.timestamp`)
  - `origin` (`app_like`, `yt_like`, `spotify_like`, `apple_like`, `download`)
  - `album_key` and `artist_key` (normalised grouping keys, see below)
- `ConnectedLibraryRepository` writes platform likes into `cloud_songs` on sync, so they no longer live only in memory. It also removes rows that are unliked on the platform, unless the song was downloaded or liked in the app.
- **Grouping keys:** `artist_key = normalize(primaryArtist)`, `album_key = normalize(albumArtist ?: primaryArtist) + "|" + normalize(album without "(Deluxe)"/"- Single"/"Remastered")`. `normalize` = lowercase, accents folded, "the " stripped, punctuation collapsed. Artist parsing uses the user's delimiter settings (`ArtistParsingUtils`), the same as local sync.
- **Merge rule:** if a local album or artist has the same key, streamed and downloaded songs join it. They don't make a second card. The card then reads, for example, "12 of 14 · 3 streaming".

The album and artist queries become a `UNION ALL` over:
1. `songs` (local files and downloads), which works as it does now, and
2. `cloud_songs` rows with `is_downloaded = 0` that are liked, grouped by `album_key` / `artist_key`.

Room's `PagingSource` invalidates on every table in the query. A like, an unlike, a download or a deleted download therefore refreshes both tabs straight away, with no manual refresh.

---

## 3. Phases

### Phase 0: make downloads stay put (bug fixes; small and safe; do first)
1. `SourceType.DOWNLOAD = 8`. The download insert sets it. The "Local" storage filter (`filterMode = 1`) treats `LOCAL` and `DOWNLOAD` both as "on this device".
2. `getAllMediaStoreSongIds()` → `WHERE source_type = 0` only, so it no longer includes downloads. The sync can no longer delete them.
3. **New `DownloadedLibraryReconciler`.** It runs at app start, after every sync (including REBUILD) and after a delete. For every `cloud_songs.is_downloaded = 1` row:
   - if the file exists: make sure the song, album, artist and cross-ref rows exist, and update blank fields instead of using `IGNORE`
   - if the file is missing: remove the song and cross-refs, clear `is_downloaded`, and delete album or artist rows that are left empty.

   This also repairs the downloads that past syncs have already removed.
4. Downloads look up existing artists (`getArtistIdByName`, NOCASE) and albums (same `album_key`) before creating new rows. New ids come from a namespaced 64-bit hash of the key, not `String.hashCode()`.
5. Cross-refs are written for every parsed artist, with the primary flag set.
6. No album → `SongMetadataGatherer` fills it before the insert (Deezer/iTunes already return the album). If there's still nothing, the album is the song title marked as a single, never "YouTube Music".
7. The directory filter is skipped for `source_type != 0` (the same idea as the existing `songs.id < 0` bypass).
8. Put `.nomedia` in the download folder and drop `scanFile`, so MediaStore doesn't import downloads a second time on older Android.
9. The paged artists flow triggers the image prefetch for rows it loads that have no image (debounced, capped per page).

**Check on device:** download a song, force Settings → rescan, and the song stays in Albums and Artists. Set an allowed-folders rule and downloads still show. Delete the download and the album disappears if it's now empty.

### Phase 1: likes feed Albums and Artists
- The `cloud_songs` migration and `ConnectedLibraryRepository` persistence from section 2.
- The `UNION ALL` album and artist queries (paged, list and search variants).
- **Album detail for groups with no local songs:** add a `key:` route, following the `ArtistDetail name:` precedent. It shows your songs from that album and the full online tracklist (`ArtistAggregationRepository.getOnlineAlbumWithSongs`). Missing tracks are greyed out with + Like and Download.
- Filter chips on both tabs: **All · Downloaded · Streaming · Local files**. This replaces the hidden storage filter for these two tabs.
- The Songs tab stays **files and downloads only** (the mix engine relies on `getAllSongsOnce` being local-only). Liked streams stay in Liked.

### Phase 2: Genres tab, and Genres out of Search
- Add `GENRES` to **both** enums (`data/model/LibraryTabId` and `presentation/library/LibraryTabId`), after Artists. `decodeLibraryTabOrder` appends it for existing users, so there's also a one-time migration that moves it after Artists if the user never reordered tabs. Sort options: By listening time · A–Z · Song count.
- `LibraryTabsStateHolder`: load branch for GENRES. `LibraryScreen`: pager page, sort-sheet mapping, and genre multi-select moved over from Search (`GenreMultiSelectionOptionSheet`).
- **Data:** genres come from both local tags and `cloud_songs.genre` (from the gatherer). They're mapped through `GenreTaxonomy` into **family → subgenre** (Rock → Stoner rock, Doom). The flow's `distinctUntilChanged` seed has to include counts, or new likes won't show.
- **Search blank state after the move** (following what YT Music does well in the screenshots):
  - "Recent searches" as a row of covers
  - quick cards: **New releases · Charts · Genres & moods**. The last card opens Library → Genres.
  - "You may also like" query suggestions based on your top artists.

### Phase 3: what makes it better than Spotify, Apple Music and YT Music
None of the three (see screenshots) does anything personal on these screens. They show ads, promos and generic categories. Everything below uses data PixelPlayer already has: `PlaybackStatsRepository` album, artist and genre summaries, the gatherer's metadata and `ArtistAggregationRepository`.

**Albums tab** (small shelves above the grid; each can be collapsed, and the choice is remembered)
- **Heavy rotation:** most-played albums in the last 30 days.
- **Complete these:** albums where you've liked or downloaded ≥3 tracks but not all of them. Each has a progress ring ("7/12"). Tap it to see the missing tracks.
- **Rediscover:** albums you played a lot but not in the last 6 months.
- Card badges: ✓ fully offline, **Hi-Res/Lossless** (from bitrate and sample rate), year, and a completeness ring.
- New sorts: Most played · Recently played · Recently liked/added · Completeness · Release year. **Group by decade** with sticky headers ("’90s · 23 albums").
- Chips: Full albums · EPs · Singles (from track count, or Deezer `record_type` when known).

**Artists tab**
- **Your top artists this month:** a row of round avatars with listening time.
- **New from your artists:** releases from the last 30 days by artists in your library (`ArtistAggregationRepository` releases). It's cached daily and has a dot badge when something is new.
- Each row shows: "23 songs · 4 albums · 12 h listened", the main genre as a chip, and an offline coverage bar.
- **Album artists only** toggle: hides artists who only appear as a feature. Many music collectors want this, and Apple Music hides it in Settings.
- New sorts: Listening time · Recently played · Recently added · Song count · A–Z.

**Genres tab**
- **Your genre DNA:** one stacked bar showing your share of listening time by genre family (last 90 days), which you can tap.
- Genre cards are ordered by your listening. Each card shows the genre colour (`GenreColors`), a collage of album covers (`AlbumArtCollage`), song count and the top 3 artist avatars.
- A **Moods** row from the gatherer's Last.fm moods (Chill, Energetic, Melancholy…). Estimated moods are marked with a small "~".
- Genre detail (`GenreDetailScreen`): subgenre chips, top artists, albums, songs, and **Start genre mix**, which hands off to the mix engine with that genre as the seed.
- An **Unknown genre** card with a "Fill genres" action (a `SongMetadataGatherer` batch, the same pacing as playlists).

### Phase 4: finishing touches
- Short fade/slide animation when a newly liked or downloaded album appears ("Just added" chip for 24 h).
- Cross-link each tab's empty state (for example, no genres → "Like a few songs or fill genres").
- Update the settings copy for `minTracksPerAlbum` (finding #10).

---

## 4. Performance guardrails (because of the earlier OOM work)
- Shelves are capped at 12 items and built from summary queries, never by loading the whole library into memory.
- Stats shelves refresh on `PlaybackStatsRepository.notifyStatsChanged`, debounced to 2 s, and only while the tab is visible.
- New indexes: `favorites(isFavorite, timestamp)`, `cloud_songs(album_key)`, `cloud_songs(artist_key)`, `cloud_songs(is_downloaded, origin)`.
- The reconciler works in chunks of 200 with IO dispatch, and runs a single pass at a time (Mutex).

## 5. Decisions (defaults used unless you say otherwise)
1. Streamed likes show in Albums and Artists, but **not** in Songs. They stay in Liked.
2. The Genres tab goes **after Artists**.
3. Unliking a streamed song removes it from Albums and Artists, unless it's downloaded.
4. Platform likes (YouTube/Spotify/Apple) count the same as in-app likes.

## 6. Files that will change
`SongEntity.kt` (SourceType), `SongDownloadManager.kt`, `SyncWorker.kt`, `MusicDao.kt`, `CloudSongEntity.kt` + `CloudSongDao.kt` + DB migration, `ConnectedLibraryRepository.kt`, `MusicRepositoryImpl.kt`, `LibraryStateHolder.kt`, both `LibraryTabId.kt`, `LibraryTabsStateHolder.kt`, `LibraryScreen.kt`, `LibraryMediaTabs.kt` (+ new `LibraryGenresTab.kt`, `LibraryShelves.kt`), `SearchScreen.kt`, `GenreDetailViewModel.kt`, `AppNavigation.kt`/`Screen.kt` (album `key:` route). New: `DownloadedLibraryReconciler.kt`, `CollectionKeys.kt`.
