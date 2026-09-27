# Song videos and alternate performances

Open Now Playing and select **Video**. PixelPlayer searches YouTube using the current song's title and display artist, for local files, downloads, and streamed tracks alike. Search results are video metadata; video playback uses the YouTube embedded player.

Beside **Quality: Auto**, **Version** offers Music Video, Live Performance, Lyric Video, Acoustic, Remix, Covers, and All versions. Selecting a preset searches that version and loads the best ranked result from the beginning. **Choose another video** shows thumbnails, titles, and channels; manual choices are remembered separately per title, artist, and preset. The original queue and track remain intact.

Search uses the YouTube Data API when YOUTUBE_API_KEY is configured; otherwise it uses the existing NewPipe engine's general video search (not music_songs). Results have a bounded five-minute memory cache, with shorter caching for empty results. Search and embedded playback can fail independently. Blocked or removed embeds advance to the next candidate; other errors offer Retry, alternate results, or a YouTube link. No playable match is guaranteed.

Audio continues while the initial search runs. The audio service pauses before a video loads. Returning to Audio restores the original song's saved position and playing/paused state, without attempting to synchronize alternate performances. Native YouTube controls operate the video timeline; the original song timeline is hidden in Video mode. Collapsing the player returns to audio. Backgrounding pauses the video. Video playback is on the phone; disconnect casting before entering Video mode.

## Verification

Focused tests:

    ./gradlew :app:testDebugUnitTest --tests '*TrackVideoRankingTest' --tests '*SongVideoViewModelTest' --tests '*SongVideoPlaybackControllerTest'

Device checks:

- Play a local song and a streamed song, open Video, select Live, and choose another result.
- Return to Audio; verify the original queue, saved position, and original playing/paused state.
- Skip rapidly while searches are pending; an old result must not replace the current song.
- Exercise offline search, an unavailable embed, retry, and a preset with no results.
- Rotate, collapse, background, and foreground the player. Check resource release and absence of simultaneous audio.
- Confirm the preset menu and real YouTube controls remain accessible in portrait and landscape.

Manual quality selection is not implemented: the embedded player chooses quality automatically. NewPipe search depends on YouTube's current site behavior.
