# Spotify official catalog search

Playback login and catalog search authorization are separate. Cinema Frame uses the official Spotify Web API `/v1/search?type=album` for movie soundtrack discovery after this setup; MusicBrainz remains a fallback. Authentication uses OAuth authorization code with PKCE (S256), with no client secret.

1. Sign into https://developer.spotify.com/dashboard with your Premium account and create an app using Web API.
2. Register exactly `http://127.0.0.1:43871/callback` as its redirect URI. Save it.
3. Install the updated Cinema Remote APK on your Android phone and pair it with the frame.
4. In **Spotify 공식 검색 연결**, enter your app's Client ID and press **Spotify 검색 연결**. The phone opens Spotify in your browser and listens only on its own loopback address for the callback.
5. Approve access and return to Cinema Remote. The frame exchanges the code, stores its refresh token in private app storage and refreshes access tokens automatically. The phone and PC are not required for ongoing searches.

Only the frame's authenticated controller can start/complete search authorization. A random ten-minute state and PKCE verifier protect the exchange. Tokens and verifiers are never included in `/api/state`, entity attributes, source code or logs. Do not publish private app storage, backups, or HTTP captures. Use a trusted LAN for the frame-controller connection.

The soundtrack lookup matches the movie title to Spotify album titles and requires a soundtrack/score marker (or an exact title). It is not a popularity ranking, an audio analysis service, or a guarantee that every Spotify result is the original soundtrack. The existing album parser still selects a main theme when named, plus representative candidates from album order; these are not verified popularity top-three rankings. Rate limiting honors Retry-After. Movies already linked to an album keep that link.

Selection no longer discards lower-scoring eligible movies. It prioritizes movies outside the recent 15-film window when available; fresh choices are weighted gently by metadata similarity. A new explicit dance request clears stale film anchors such as La La Land. The IMDb eligibility threshold is rating 7+ with 10,000+ votes, while decade and explicit exclusions remain enforced. Instrumental and mood classification are metadata/title heuristics and are not reliable acoustic genre detection.

References:
- https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow
- https://developer.spotify.com/documentation/web-api/reference/search


## Spotify-first discovery

Each background discovery batch now searches Spotify for soundtrack albums first, using style-specific queries plus `original motion picture soundtrack`. It rotates result pages instead of permanently reusing the first ten results. Album titles with soundtrack markers are matched to IMDb title/original-title metadata; ambiguous remakes require a matching release year, and unmatched compilations are skipped. IMDb popularity, decade and exclusion filters still apply. Existing movie-title searches and MusicBrainz remain fallbacks.

The authenticated `/api/state` response includes `spotify_discovery` with the last query, result count, matched/new movie counts and examples. Selected items expose `album_source` and `album_url` alongside the movie, track and fetched IMDb poster, so the source can be checked. These contain no OAuth credentials. Tapping Next no longer cancels an in-progress discovery batch; changing the request still cancels searches for the old request.

This is catalog text matching, not audio similarity analysis. An exact soundtrack/movie match does not guarantee a song has the requested tempo or acoustic mood. Spotify-first matching currently uses the installed IMDb metadata catalogue; it does not map every album or every movie in Spotify.
