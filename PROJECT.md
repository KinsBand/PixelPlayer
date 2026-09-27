# Project: PixelPlayer-Clean-

## Architecture
Android music player supporting local and online song playback, stream resolution, song liking/favorites management, and downloading / library synchronization.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | R1 Playback & Stream Resolution | Online song playback, stream URL resolution, ExoPlayer/MediaSession integration | None | DONE |
| 2 | R2 Online Favorites / Like | Online song like/unlike toggle, database/preference persistence, UI state sync | None | DONE |
| 3 | R3 Online Download & Library Sync | Download audio files, progress/notifications, local library sync | None | DONE |

## Interface Contracts
- Online song playback service interface & stream resolver
- Favorites/Repository interface for online & local tracks
- DownloadManager & MediaStore / Library sync service

## Code Layout
- Android application codebase (Kotlin/Java, Gradle build)
