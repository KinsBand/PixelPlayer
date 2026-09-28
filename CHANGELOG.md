# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed
- **Full screen:** with "Hide status bar" / "Hide gesture bar" on, the bars no longer come back and stay after the back gesture (e.g. on Pixel) or a screen transition, and they stay hidden while a bottom sheet (lyrics menu, song options…) is open; any time they reappear they're hidden again straight away. The two switches moved from General to Appearance.
- **Lyrics:** in face-to-face mode the section pills beside the play / pause button have room for their names ("Chorus" was cut to "Cho…").
- **Search:** the "Because you listen to…" suggestions are compact single-line rows instead of tall, widely spaced ones.
- **Streaming:** on weak mobile data, online songs no longer play for a moment, stop for a long time and then fail with a playback error. The local stream proxy passed audio on to the player only in 1 MB bursts, so the player ran dry, timed out and reopened while the abandoned downloads kept competing for the connection. Audio now reaches the player as it arrives, a stalled connection is replaced within 10 s from the same byte, abandoned downloads stop, and the player waits for the proxy instead of timing out after 8 s.
- **Streaming:** when the connection can't keep up, playback no longer stops every second: after repeated buffering it waits for a few more seconds of audio before resuming. The next song is only prepared once the playing song has what it needs.
- **Streaming:** first plays of online songs no longer fail and fall back to slow extraction when the fast manifest request takes more than 200 ms (the winning manifest was being discarded).
- **Streaming:** after switching between Wi-Fi and mobile data, songs no longer hit refused stream URLs or get stuck on the slow extraction path.

### Changed
- **Streaming:** a cold manifest is one request instead of two. Visitor data is kept and reused, and the player response is trimmed from ~15 KB to ~3.4 KB.
- **Streaming:** resuming after a long pause plays the 7 s rewind from memory instead of refetching the song.
- **Streaming:** the two songs after the next one also get their manifests ready (three in all), and the next song's cover is prefetched.
- **Search:** opening Search pre-opens the search and playback connections, so the first query and first tap skip the connection setup.
- **Lyrics:** livelier transitions. A new current line rises into place and lands with a small spring; the word being sung lifts and swells while it's held and settles as the next one starts; in letter mode a soft wave of lifted letters follows the voice, with a glow on the wipe edge. The unsung copy moves with the sung one so nothing ghosts. Turned off by the system "Remove animations" setting.
- **Lyrics:** adaptive expressive typography now visibly changes per song. Each song gets its own size, line height, tracking and base weight, per-word emphasis is stronger, and the motion above (how far words jump, how bouncy lines land) follows the song. Songs without an audio analysis get their voice from their genre and lyric pacing instead of all looking the same.
- **Lyrics:** face-to-face mode shows the minimised song bar (cover, title, next song) instead of the full card; the section pill beside the centre line stays. Tapping the current song leaves face-to-face mode.
### Added
- **Audio details:** Versions also searches online for every version of the song (studio, live, acoustic, remixes and covers), listed under the library's versions. Tapping one plays it.
- **Lyrics:** sideways, the lyrics screen splits in two: the song card (full or minimised, with the song structure) and the controls on one half, the lyrics on the other using its full height. Settings → Lyrics (and the lyrics menu) can put the lyrics on the left instead.
- **Lyrics:** face-to-face mode sideways puts the two readers' halves side by side (the left one turned 180°), each with its section pill at the top and the minimised song bar at the bottom, and a play / pause button on the centre line.
- **Lyrics:** face-to-face mode has a small play / pause button where the dot on the centre line was.
- **Queue:** sideways, the queue is a sidebar: the left half stays put with Next up, tracks lined up, Listen, the playing song with its mix buttons, History and as many next songs as fit; the queue carries on in the right half, which scrolls over the full height, with the toolbar at its bottom.
- **Queue:** sideways, the queue's options menu and the "removed · Undo" bar open in the right half with the toolbar instead of across the seam.
- **Player:** sideways, the Song / Video switch sits above the album cover (the cover shrinks a little to make room), and the button beside Lyrics opens the song options instead of repeating the queue button.
- **Lyrics:** the top song card can collapse into a compact Now / Next bar (swipe up or the chevron; swipe down or the arrow to expand). Tapping Next skips to that song straight away, and when a song ends the bar slides left as the queue moves on.
- **Lyrics:** a + button (toolbar, and next to the arrow in immersive) opens Search to add a song. Tapping a song there opens a small Play / Next / Soon / Queue sheet; picking one goes straight back to the lyrics with a short "Playing next ✓" style confirmation. Other buttons on song cards work as before.
- **Queue:** Play Soon — lands after the current song and anything already added with Play next, at most three songs ahead.
- **Lyrics:** quick reactions in the bottom corners (❤️ 🔥 👌 on the left, 😐 🥱 👎 on the right). Tap to open, or press and slide up to pick; closes after 3 s idle or on a tap elsewhere. Reactions are stored as their own signals for the mix (not likes); 😐 and 👎 re-plan upcoming automatic picks.
- **Lyrics:** Expressive typography (beside Immersive lyrics, in the lyrics menu and Settings → Lyrics): font, size (now up to Extra large), weight and line spacing. The current line always stays a step bolder and the active card grows with the text.

- **Metadata:** online songs you open, download or save are also looked up on MusicBrainz (by the exact recording's ISRC when known): original release year, community genres and tags, composer, lyricist and MusicBrainz ids. Deezer, iTunes and MusicBrainz are merged by weighted vote per field, and how much they agreed is kept with each value. Downloads get composer, lyricist, language and MusicBrainz ids in their tags.
- **Lyrics:** Lyricsfile support (YAML with plain, line-synced and word-synced lyrics): `.lyrics` files can be imported, a `.lyrics` file is saved next to the `.lrc` when lyrics are saved, and LRCLIB's word-timed lyricsfiles are used when fetching.
- **Lyrics:** Unison community lyrics (Better Lyrics' voted database) as a word- and line-synced source, matched to the exact YouTube video when there is one. It can be turned off in the lyrics sheet's advanced options.

### Changed
- **Metadata:** a song's mood can come from MusicBrainz tags without a Last.fm key, and short tag words match whole words only ("funk" is no longer "Happy").
- **Lyrics:** the credit under fetched lyrics names the provider that actually supplied them instead of always LRCLIB (Unison's lyrics always show its required attribution).
- **Lyrics:** the lyrics start right under the header whatever its size; secondary controls dim while the screen is idle; the fallback highlight colour stays in the cover art's hue instead of a fixed amber.

## [1.0.0] - KinsBand build

### Fixed
- **Artist page:** "Fans also like" artists now open (artist → artist navigation reused the same page), playlists open as a playlist page, and songs start quickly (catalog songs are matched to audio in parallel instead of one by one).
- **Home:** the mix button and generated mixes now shuffle instead of always starting from the first song.
- **Voice search:** plain dictation only fills the search bar; the song card is reserved for Hum & Sing and Listen.

### Changed
- **Accounts:** tapping a connected playlist opens it directly (no preview dialog).
- **Friends:** the dash between a friend's name and their song is gone.
- **Player:** the "not for this mix" button uses the same broken-heart icon as playlist select mode.
- **Settings:** removed the quick-preferences card, the folder back-gesture toggle (now always on), "tap background closes player", and the unused home collage options. Camera island settings moved to Appearance. "History in queue" now shows or hides the queue's History button; the queue always starts at the playing song.
- **About:** removed the maintainer, spotlight and contributor lists; GitHub links point to KinsBand/PixelPlayer.

### Added
- **Friends:** a new song sheet (Play / Play next / Add to queue tiles, like, go to artist, search). The history sheet is redesigned with **Shuffle** (the friend's whole week) and **Follow** (each new song they start plays next; stops by itself after 15 minutes of silence).
- **Downloads:** tapping download in the player opens a quality menu under the button (High = Opus, Medium = AAC 128, Low) with the size in MB and an estimated time; long-press downloads High.
- **Playlists:** friend playlists show the friend's avatar and name on the details line; blank covers fall back to the collage.
- **Widgets:** the selected widget's big live preview and a row of widget names stay fixed at the top; options scroll below with Appearance first; on/off options are icon toggles; reset moved to the top bar.
- **Settings:** the Experimental screen is merged into Player & Lyrics as short toggles; utility screens open with the condensed bar (back button and title on one row).
- **Downloads:** "Download on Wi-Fi only" for downloading all liked songs. It waits for Wi-Fi, asks with a notification before starting, shows one progress notification with Cancel, and pauses if Wi-Fi drops.

## [0.7.5-beta] - 2026-06-13

### Added
- **Google Drive:** Added Google Drive support and improved player lifecycle management.
- **AI Lyrics:** Integrated AI lyrics translation logic in `LyricsStateHolder` and user preferences.
- **Gemma:** Deleted old Gemini model IDs and integrated Gemma model support.
- **Diagnostics:** Added a lag diagnostic tool.
- **Search:** Added multi-selection support to the Search screen.
- **UI:** Added outlined button style for AOD screen.
- **Connectivity:** Added support for HTTP URLs on local-network Navidrome and Jellyfin hosts.
- **Localization:** Added Arabic and Turkish language support, and unrecognized languages.

### Changed
- **Battery Optimization:** Drastically reduced battery consumption via audio offload and adaptive UI polling.
- **Queue System:** Refactored shuffle, queue reordering, and playback orchestration to `QueueStateHolder` using explicit queue indices.
- **Transitions & Animations:** Implemented Material 3 Expressive motion curves for player, queue sheet, and screen transitions.
- **Architecture:** Decomposed `MusicService` and modularized `PlayerViewModel` state listeners.
- **Library Sync:** Optimized library sync with throttled scans and faster artwork loading.
- **Database:** Migrated database to version 42 and updated Navidrome schema.
- **Equalizer:** Added "Save New" action and improved layout.
- **Localization:** Refactored app localization, resource cleanup, and UI text wrapping.
- **Dependencies:** Bumped dependencies including `kotlinx-collections-immutable`, `okhttp`, and Gradle plugins.

### Fixed
- **Playback:** Resolved buffering issues, song skipping lags, and unnecessary recompositions during playback.
- **Media Store Sync:** Improved MediaStore URI resolution, external song deletion, and Android 11+ storage volume resolution.
- **Lyrics & Metadata:** Fixed Chinese lyrics detection, pinyin tone suffixes, and batch metadata/artwork editing consistency.
- **UI:** Fixed marquee text fade glitches, navigation bar corner behavior, blur issues, scrollbar bugs, and layout/padding insets.
- **Other:** Fixed backup playlist update issues and startup AI provider errors.

### New Contributors
- @YtMechnij made their first contribution in https://github.com/theovilardo/PixelPlayer/pull/2106
- @juinc made their first contribution in https://github.com/theovilardo/PixelPlayer/pull/2109
- @ZL114514 made their first contribution in https://github.com/theovilardo/PixelPlayer/pull/2159
- @aliabbasov99 made their first contribution in https://github.com/theovilardo/PixelPlayer/pull/2262
- @Hisham-Alzamzami made their first contribution in https://github.com/theovilardo/PixelPlayer/pull/2335


## [0.7.0-beta] - 2026-05-25

### Added
- **AI:** Groq AI and OpenRouter (experimental) with token optimization and AI-powered playlist generation.
- **Cloud & Streaming:** Jellyfin support.
- Direct song synchronization from server albums in Navidrome.
- Standardized branding for NetEase Music.
- **Lyrics:** Synchronized translation with a dedicated toggle and Kugou LRC format support.
- Text alignment customization and improvements to TTML parsing.
- Advanced romanization for Japanese characters.
- **UI/UX:** Redesigned queue sheet and "Recently Played" pills with a dynamic palette.
- Marquee support for long titles and a compact mode for the navigation bar.
- New horizontal timeline for monthly statistics and multi-artist support.
- **Telegram:** Native support for topics, playlist display, and reactive updates.

### Changed
- **Audio Engine:** Complete overhaul with support for MIDI, improvements to ALAC/M4A/Opus, and decoder optimization (including Samsung-specific decoders).
- **Energy Efficiency:** Drastically reduced battery consumption and thermal optimization through UI task gates.
- **Database and Cache:** Massive optimizations to queries, cover art cache controller v3, and support for Scoped Storage.
- **Startup:** Improved load times through optimized generation of Baseline Profiles.
- Project license changed from MIT to Proprietary License.

### Fixed
- **Playback:** Fixed stuttering in Opus/MP3, errors in ReplayGain during crossfades, and flickering during album art changes.
- **Navigation:** Fixed navigation loops in Telegram and improved screen entry/exit animations.
- **Stability:** Eliminated crashes on Android 12+, fixed memory leaks (ANRs), and improved exception handling in background services.
- **Security:** CI hardening, encryption of cloud storage credentials, and media server access control.

### Localization
- 🇪🇸 **Spanish** | 🇫🇷 **French** | 🇷🇺 **Russian**
- 🇨🇳 **Simplified Chinese** | 🇮🇩 **Indonesian** | 🇮🇹 **Italian** | 🇩🇪 **German**

## [0.6.0-beta] - 2026-03-05

### Added
- Added Android Auto support through Media3 `MediaLibraryService`.
- Added cloud provider expansions: Telegram playlist management, NetEase sync improvements, QQ Music integration, Subsonic/Navidrome, and Google Drive streaming (WIP).
- Added a modernized backup/restore system (v3), account management, and persistent queue restoration.
- Added smarter lyrics workflows (manual fallback search + storage refactor), Recently Played, and new multi-selection flows (songs/albums/playlists).
- Added home and UI customization features: collage patterns, quick settings tiles, expressive scrollbar refinements, and new widget styles.

### Changed
- Reworked player architecture and interaction model (unified player sheet refactors, predictive back handling, gesture tuning).
- Redesigned key surfaces including Lyrics, Cast, Artist, Genre, and Daily Mix experiences.
- Refined library/search/navigation behavior with safer navigation APIs and better state restoration.
- Improved audio compatibility and metadata handling (JAudioTagger fallback, URI handling, surround/noisy behavior).
- Expanded integration UX across Telegram/NetEase/QQ login and sync flows.

### Fixed
- Fixed multiple queue/shuffle edge cases (anchored shuffle, start-at-zero shuffle, queue synchronization).
- Fixed playback interruption behavior when headphones disconnect and resolved foreground service start restrictions.
- Fixed Cast-related crash cases and improved cast reliability.
- Fixed Sleep Timer UI issues, files tab navigation, album artist crash, and state-sync regressions in settings/reorder flows.
- Fixed release build stability (`R8`) and numerous UI polish issues across bottom sheets and controls.

### Performance
- Reduced recompositions and state overhead across Player, Library, Queue, and detail screens.
- Improved startup behavior (eliminated blank flash and deferred heavy Telegram native loading off main thread).
- Optimized folder/genre/artist loading, bottom navigation responsiveness, and gesture fluidity.
- Reduced CPU/main-thread pressure and improved service/widget runtime efficiency.
- Reduced APK size using ABI splits, downloadable fonts, and SDK cleanup.

### New Contributors
- @ThatOneCalculator
- @ryan7zoom
- @LarveyOfficial
- @Dv1101
- @Sincere-Bhattarai

## [0.5.0-beta] - 2026-01-14

### Added
- Implemented 10-band Equalizer and effects suite (feat: @theovilardo)
- Added M3U playlist import/export support (feat/fix: @lostf1sh, @theovilardo)
- Integrated Deezer API for artist images (feat: @lostf1sh)
- Added Gemini AI model selection, system prompt settings, and AI playlist entry point (feat: @lostf1sh, @theovilardo)
- Added sync offset support for lyrics and multi-strategy remote search (feat/fix: @lostf1sh, @theovilardo)
- Added Baseline Profiles for improved performance (feat/fix: @theovilardo, @google-labs-julesbot)
- Added support for custom playlist covers

### Changed
- **Material 3 Expressive UI**: Modernized Settings, Stats, Player, Bottom Sheets, and dialogs (refactor: @theovilardo, @lostf1sh)
- **Library Sync**: Rebuilt initial sync flow with phase-based progress reporting and linear indicators (feat: @lostf1sh)
- **Settings Architecture**: Introduced category sub-screens and improved navigation handling (refactor/fix: @theovilardo)
- **Queue & Player**: Decoupled queue updates from scroll animations, added animated queue scrolling (feat/fix: @lostf1sh, @theovilardo)
- Improved widget previews and case-insensitive sorting logic (feat/fix: @lostf1sh, @google-labs-julesbot)

### Fixed
- Fixed casting stability, queue transitions, and reduced latency (fix: @theovilardo)
- Fixed delayed content rendering and unwanted collapses in Player Sheet (fix/refactor: @theovilardo)
- Fixed reordering issues in queue
- General crash fixes and minor UX improvements (fix: @lostf1sh, @theovilardo)

## [0.4.0-beta] - 2025-12-15

### Added
- Major navigation redesign
- New file explorer for choosing source directories
- Landscape mode (thanks to "leave this blank for now")
- New Connectivity and casting functionalities
- Seamless continuity between remote devices
- Gapless transition between songs
- Crossfade
- New Custom Transitions feature (only for playlists)
- Keep playing after closed the app
- UI Optimizations
- Improved stats feature
- Redesigned Queue control with more features
- Improved different filetypes support for playing and metadata editing
- Improved permission controller
- Minor bug fixes

## [0.3.0-beta] - 2025-10-28

### What's new
- Introduced a richer listening stats hub with deeper insights into your sessions.
- Launched a floating quick player to instantly open and preview local files.
- Added a folders tab with a tree-style navigator and playlist-ready view.

### Improvements
- Refined the overall Material 3 UI for a cleaner and more cohesive experience.
- Smoothed out animations and transitions across the app for more fluid navigation.
- Enhanced the artist screen layout with richer details and polish.
- Upgraded DailyMix and YourMix generation with smarter, more diverse selections.
- Strengthened the AI assistant to deliver more relevant playback suggestions.
- Improved search relevance and presentation for faster discovery.
- Expanded support for a broader range of audio file formats.

### Fixes
- Resolved metadata quirks so song details stay accurate everywhere.
- Restored notification shortcuts so they reliably jump back into playback.

## [0.2.0-beta] - 2024-09-15

### Added
- Chromecast support for casting audio from your device (temporarily disabled).
- In-app changelog to keep you updated on the latest features.
- Improved lyrics search
- Support for .LRC files, both embedded and external.
- Offline lyrics support.
- Synchronized lyrics (synced with the song).
- New screen to view the full queue.
- Reorder and remove songs from the queue.
- Mini-player gestures (swipe down to close).
- Added more material animations.
- New settings to customize the look and feel.
- New settings to clear the cache.

### Changed
- Complete redesign of the user interface.
- Complete redesign of the player.
- Performance improvements in the library.
- Improved application startup speed.
- The AI now provides better results.

### Fixed
- Fixed various bugs in the tag editor.
- Fixed a bug where the playback notification was not clearing.
- Fixed several bugs that caused the app to crash.

## [0.1.0-beta] - 2024-08-30

### Added
- Initial beta release of PixelPlayer Music Player.
- Local music scanning and playback (MP3, FLAC, AAC).
- Background playback using a foreground service and Media3.
- Modern UI with Jetpack Compose, Material 3, and Dynamic Color support.
- Music library organization by songs, albums, and artists.
- Home screen widget for music control.
- Real-time audio waveform visualization.
- Built-in tag editor for song metadata.
- AI-powered features using Gemini.
- Smooth in-app permission handling.
