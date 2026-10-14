# Socials icons, Gemini Nano, queue layout, export & service sheets: build notes (2026-10-14)

**Compiled: no. Run on device: no.**

## Your build errors
- The errors in your log came from an older copy of six files edited after my last round: `SpotifyRepository`, `ConnectedLibraryRepository`, `PlaylistExportBottomSheet`, `ArtistDetailScreen`, `PlaylistDetailScreen` and `ProfileScreen`.
- The compiler's line numbers sit one line behind today's `SpotifyRepository.kt`, which means a missing `}` back then. That brace has since been added. The other reported lines (`getCloudSongById`, the duplicate `@OptIn`, `pickedPreview`) are also fixed in the current files.
- The rest of the errors (`toSong`, `parseTrack`, `createPlaylist`…) all came from that one brace. You then said the build works.

## What changed
1. **Socials cards (artist page).**
   - Each card shows the service's real app icon when that app is on the phone. Otherwise it shows the brand glyph (Spotify / YT Music / Apple Music) or a brand-coloured initial.
   - The link text is gone. Tap opens; hold copies the link (haptic tick; a toast below Android 13, where the system shows no "copied" chip).
   - No manifest change was needed: the manifest's MAIN/LAUNCHER `<queries>` already makes launcher apps visible.
2. **Song options ↔ Info.**
   - **Cause:** the pager's height followed whichever page was showing, so the bottom sheet resized and its top (with the buttons) jumped.
   - **Fix:** Info is laid out ahead of time (`beyondViewportPageCount = 1`), and the pager keeps the taller page's height. Switching tabs no longer resizes the sheet.
3. **Queue (portrait).**
   - The History | Heard bar, the playing song and its mix chips are now one fixed block.
   - **Closed:** the block sits on top and Up next scrolls under it.
   - **History or Heard open:** the block slides to the bottom (a smooth spring; the layout animates, not recomposition). The past songs scroll above it, newest right above the bar, and the Clear button stays at the bottom with it.
   - **Closing:** the block slides back up and Up next jumps back to its top.
   - The mix chips are now their own row under the card (no joined strip), so they can't run into the card's curve.
   - Locate and a song change scroll Up next back to its top.
   - Landscape is unchanged.
4. **Service pop-ups → bottom sheets.**
   - Spotify, YouTube Music, Apple Music, ListenBrainz and Discord now share one `ServiceSheet`: brand icon, name, one status line, one main button.
   - Manual cookie, header, ID and token-guide options sit under "More options". Errors show in one line, with a wavy progress bar while connecting.
   - Function names are unchanged, so Profile, Settings and Accounts open the sheets without other edits.
5. **Gemini Nano (Pixel 10 Pro).**
   - **Likely cause:** the app asked AICore only for a FAST + STABLE model. When that exact pairing isn't offered, Nano showed as unsupported.
   - **Fix:** it now tries FAST/stable → FULL/stable → AICore default → FULL/preview → FAST/preview, and keeps the first that's ready (or downloadable). The real AICore error now shows in Settings instead of a generic line.
   - 5.5: new `DeviceAi.isPixel` (Google + "Pixel" model). On other phones the following are hidden:
     - the AI settings row and the Gemini Nano engine section
     - the AI playlist option and its API-key button
     - the Daily Mix AI button
     - AI playlist sheets (`showAiPlaylistSheet()` does nothing there)
   - The artist page's Ask box already only shows when Nano is ready.
6. **Export sheet.**
   - Spotify and YouTube Music tiles sit side by side, each with "Export" (or "Connect first") under it.
   - One full-width Auto-sync switch, then "Export as file" and "Transfer" side by side. While exporting, the sheet shows just the status and a wavy bar.
   - **Spotify rate limits:**
     - The web client spaces requests 120 ms apart and retries a 429 up to 3 times (Retry-After up to 30 s, else 2/4/8 s). It used to back off once, for at most 10 s.
     - The official API path gets the same retries.
     - Matches are cached per session, so re-exports don't search again.
     - Web sessions: if the public Web API refuses to create the playlist (it often throttles web-player tokens), it falls back to the web player's own playlist service (spclient playlist v2 + rootlist).
7. **Add to playlist.**
   - Typing a name no playlist has shows a "Create “name”" row in the Add-a-friend skeleton style (dashed cover with +).
   - Tap it, or press Enter, to create the playlist with the songs and close the sheet. The hint now reads "Search or name a new playlist…".
8. **Daily Mix sheet.**
   - The clear-all button only appears (sliding in) once a filter is set.
   - The filter chips have no icons, and neither does the picker pill (it shows a search glyph only while typing).
   - The screen behind the sheet gets a 3 dp blur, enough to separate it without being noticeable. It respects the "no blur" setting and needs Android 12+.
   - An open filter's list now always stays a gap below the sheet's top and scrolls inside it (it's clipped, and the room reserved above grew from 72 to 88 dp).
9. **Artist About.**
   - The bio now comes from Last.fm, then Wikipedia, then a summary from your library, so About is never empty. It shows a skeleton until loaded.
   - The Wikipedia step uses the keyless REST API: first the page MusicBrainz links (directly or via Wikidata), else a name search that only accepts music pages. It goes through `NetworkAccessPolicy` (Enrichment).
   - The library summary covers song and release counts, genres, members and best-known songs.
   - The card now says where the bio came from.

## Files
- **New:**
  - `data/ai/nano/DeviceAi.kt`
  - `presentation/components/ServiceSheet.kt`
  - `presentation/components/NewPlaylistSkeletonRow.kt`
- **Edited:**
  - Nano and AI: `MlKitNanoEngine`, `SettingsScreen`, `AiSettings`, `PlaylistCreationDialogs`, `DailyMixScreen`, `PlayerViewModel`
  - Artist page: `ArtistHubSections`, `ArtistHubRepository`, `ArtistDetailViewModel`, `ArtistPageSections`, `ArtistDetailScreen`
  - Daily Mix: `MainActivity`, `DailyMixLauncher`
  - Spotify and export: `SpotifyPathfinder`, `SpotifyWebLibrary`, `SpotifyRepository`, `PlaylistExportBottomSheet` (rewritten, same signature)
  - Sheets and queue: `SongInfoBottomSheet`, `PlaylistBottomSheet`, `AccountConnectionDialog` (rewritten, same entry point), `ServicesSettings`, `QueueBottomSheet`
- **Strings:** `strings_screens.xml` (artist_socials_copy*, artist_about_*), `strings_library.xml` (playlist_export_*, playlist_sheet_create_named), `strings_settings.xml` (service_sheet_*).

## Verification
| Check | Result |
|---|---|
| Compiled / run | **No / No** |
| ktlint 1.3.1 parse, 25 Kotlin files | No parse errors |
| Strings | All values XML well-formed, no duplicate names, every new `R.string` / `R.plurals` defined |
| Line endings | CRLF kept where files were CRLF. Fixed 5 files where my helper inserted CR CR LF lines into LF files, and fixed the helper. |

## Please run
`./gradlew :app:compileDebugKotlin`

## Compile risks to watch
- `Generation.getClient()` (no arguments) and `GenerativeModel.close()` in ML Kit genai-prompt. Both are in the documented API, but they're first-time uses in this project.
- `Layout(contents = …)` (multi-content layout, Compose UI 1.4+) in `QueueBottomSheet`.

## Needs on-device testing
- **Pixel 10 Pro:** Settings → AI → Gemini Nano should now show Ready or Download (or the real AICore error). Then try the artist Ask box.
- **Non-Pixel phone:** no AI row in Settings, no AI playlist option, no Daily Mix AI button.
- **Queue:** open/close History and Heard (the slide, newest above the bar, Clear at the bottom), and check the chips under the playing card.
- **Song sheet:** Options → Info → Options; the buttons should stay put.
- **Spotify export** of a large playlist on a web session (watch for 429s; the fallback create path is new).
- **Daily Mix:** blur strength, the clear button appearing, and a long filter list (Genre / Artist) staying under the sheet top.
- **Socials:** icons for installed apps; hold a card to copy its link.

## Open items (from before)
- **P2.6:** what should playlist pickers do with friend playlists, blends and shared mixes?
- **9.2** is blank in the roadmap.
