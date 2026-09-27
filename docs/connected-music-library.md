# Connected music library

Implemented 2026-09-16 in the existing Android app.

## Setup

In Settings → Accounts, connect either or both services. Spotify uses your developer client ID (never a client secret), PKCE, random state, and a five-minute loopback listener bound only to 127.0.0.1. Register all three of `http://127.0.0.1:8888/spotify-callback`, `http://127.0.0.1:8889/spotify-callback` and `http://127.0.0.1:8890/spotify-callback` in the Spotify dashboard. Sign-in binds the first of those ports that is free and sends that exact URI, so every one has to be registered. Spotify's documentation describes registering a loopback redirect without a port and supplying the port at authorization time, but the developer dashboard rejects the portless form with "This redirect URI is not secure", so the ports are fixed in the app instead. If Android kills the app while the browser is open, start sign-in again.

YouTube Music uses the explicitly requested unofficial browser-session method. Paste the Cookie request-header value from a signed-in music.youtube.com browser session inside the app; do not paste it into chat. Account index defaults to zero. The session is checked by fetching the library. Sessions can expire and web response changes can require an app update. No Google password is collected.

Both credential stores use Android Keystore-backed encryption and are excluded from cloud backups and device transfers. No plaintext fallback is used. Old plaintext credentials are cleared on reconnect/logout rather than silently reusing them.

## Library behavior

- Library → Playlists shows a combined Liked Songs card, manually created local playlists, connected playlists, and Friends.
- Provider likes are metadata, not downloads. Local likes and provider likes are combined using IDs, recording codes, and conservative title/artist/duration matching. Version labels are retained. Local liking does not modify provider accounts.
- Spotify follows all `next` pages. The new `items` endpoint and wrapper are supported, with a legacy endpoint fallback only for HTTP 404.
- YouTube Music reads playlist/song renderers and continuation pages, obtains the web client version from the service, and does not invent song titles or durations.
- Deleted/unavailable rows are retained as unavailable. A failed playlist keeps its previous tracks and displays its error. A failed provider sync retains its previous snapshot.
- Snapshots are atomically saved in app-private no-backup storage for offline browsing. Explicit Sync and pull-to-refresh update them. Connecting also starts a sync.
- Friends can be added by public playlist URL. Spotify library playlist owners other than the current user/Spotify are grouped automatically. Tap a friend's heading to change its local alias. Reuse the same friend name to group another manually added playlist, including a playlist from the other service.
- Source playlists are read-only in PixelPlayer; editing friend names changes only local organization. Cards show small monochrome source badges, aligned right and vertically centered. Track screens link back to the provider.
- Playback and downloads use PixelPlayer's existing YouTube resolver, based on the search/stream-manifest separation examined in the requested Spotube plugin. This does not execute Spotube `.smplug` files or make this an official provider playback integration.

## Provider limits found in research

Spotify development-mode rules restrict playlist contents to playlists the authenticated user owns or collaborates on. Other public playlist metadata can be visible while tracks are inaccessible. The app displays that failure; it cannot guarantee every friend's track. There is no automatic friend graph import. Development apps also require account allowlisting and eligibility under Spotify's current rules.

The full YouTube Music browser-session interface is unofficial. Official Spotify/YouTube APIs do not confer a general right to free, ad-free downloads. Cached local searches can be quick, but no fixed millisecond latency is promised for remote searches, authentication, or uncached stream resolution.

## Primary references

- Spotify redirects: https://developer.spotify.com/documentation/web-api/concepts/redirect_uri
- Spotify PKCE: https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow
- Spotify 2026 access changes: https://developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide
- Spotify design guidance: https://developer.spotify.com/documentation/design
- YouTube Music browser authentication: https://ytmusicapi.readthedocs.io/en/stable/setup/browser.html
- YouTube Music library implementation reference: https://github.com/sigma67/ytmusicapi/blob/main/ytmusicapi/mixins/library.py
- Requested Spotube audio source: https://github.com/KRTirtho/spotube-plugin-youtube-audio

## Verification

Focused JVM tests cover cross-provider like matching, version preservation, unavailable rows, YouTube renderer metadata, continuation forms, and Spotify pagination. Real-account sign-in, provider access, and device playback require account credentials and runtime verification; fixture tests alone cannot establish that live provider responses still match.
