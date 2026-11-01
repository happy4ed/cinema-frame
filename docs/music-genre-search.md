# Music genres and background playback

The phone controller now separates **음악 장르** from **영화 장르**. Dance, jazz, rock, electronic, hip-hop, classical, blues, ambient, R&B and pop requests use Spotify **track** search with the `genre:` filter. Ballad is handled with Spotify text search because its literal genre filter returned no results: happy requests use `romantic ballad soundtrack` and `happy ballad soundtrack`, sad requests use `sad ballad soundtrack`. This is provider text relevance, not a validated audio mood classification. Cached ballads from a different requested mood cannot satisfy the new request. Soundtrack and motion-picture album queries reduce unrelated results. Search pages rotate and matching recordings are cached in the frame's private database. This is an open-ended search, not a fixed playlist.

As requested, Spotify's genre classification is the authority. There is no second music database, LLM call, audio feature analysis or hand-picked genre playlist. Spotify may classify a song broadly; these labels are not a promise of a particular BPM. Film matching requires an exact normalized soundtrack album title or an explicit `From "Film"` track credit. Ambiguous film titles are rejected. TV/game soundtracks and unrelated compilations without a film match are skipped.

Only recordings returned for the requested genre/mood query enter its candidate pool. Legacy albums, movie genres, the first four album tracks and title keywords cannot bypass this requirement, including when playback history rolls over. A new music request pauses the previous selection while searching. If no matching soundtrack exists, it keeps searching without substituting another music genre. Existing decade/country exclusions remain active. For music searches, popular films rated at least 6 with 50,000 votes are also eligible (in addition to the original 7/10,000 rule), so well-known soundtracks such as Saturday Night Fever are not excluded by the film's rating.

State includes `profile.music_style`, `music_match_count`, per-track `music_styles` and `style_evidence` (the actual Spotify query). `spotify_discovery` exposes batch results. The authenticated diagnostic `POST /api/music/search {"text":"댄스"}` returns the provider's search metadata, never credentials.

Leaving Cinema Frame for Fully or another app pauses playback. A playback-loop guard also prevents hidden resume/new-track commands from making sound. Returning to the frame leaves playback paused until an explicit Play request. This does not change system volume, Spotify credentials or Fully settings.

Validated on the frame: dance → Shrek 2 / Funkytown and Saturday Night Fever / Stayin' Alive; jazz → Whiplash / Caravan; rock → That Thing You Do! / That Thing You Do!; matching IMDb posters and confirmed playback. Fully transition paused within about 3 seconds; hidden resume remained paused; explicit foreground resume worked. These are provider classification and device playback checks, not a claim of human listening verification for every result.

Reference: [Spotify search filters](https://developer.spotify.com/documentation/web-api/reference/search). Host regression: `tests/java/MusicStyleRegression.java` plus the existing selection tests.

The frame now shows the accepted request and searching status when no track is available, instead of displaying the initial “Your next scene” prompt. An accepted request closes the pairing overlay. Request generation changes are atomic; page cursors advance only after processing, so an interrupted search cannot skip its first results. HA and phone share this request path; no HACS reinstall is needed for this receiver update.

Additional device check: “발라드 행복” displayed the Armageddon poster and confirmed playback of Aerosmith / I Don’t Want to Miss a Thing, selected by Spotify romantic-ballad search.

## Expanded discovery and rate control

Spotify playlist search now supplements track search. For ballads, the frame searches movie-ballad playlists in English and Korean, fetches public playlist metadata and matches their recordings to films. Playlist IDs are discovered at runtime, not hard-coded. Playlist labels/search relevance are curator/provider metadata, not measured audio features. This can include varied interpretations of a style.

Film credits such as `From the Film "Pretty Woman"` and `OST` album suffixes are recognized. IMDb title suggestions can resolve soundtrack titles in another language to an existing catalogue film. Unknown/ambiguous film matches remain excluded; this does not yet cover every Korean film or every soundtrack worldwide.

Playlist search results are cached; different query families each get a playlist turn. Metadata lookups are bounded per playlist. Music discovery runs at most once per two minutes for the same profile, independently of the Next button. Spotify Retry-After is persisted across process restarts. Cached tracks remain playable during search backoff.

`music_movie_count` and `music_match_count` show actual distinct matched films and recordings in the phone UI. `/api/music/candidates` exposes authenticated candidate provenance for verification.

Device verification after expansion: 37 distinct films / 56 soundtrack candidates; 12 consecutive confirmed plays used 12 different films and 12 different track URIs, with no consecutive film repetition. Included Don Juan DeMarco, Days of Thunder, Breakfast at Tiffany's, Deadpool 2, The Rescuers, Cinema Paradiso and The Bodyguard. This verifies playback diversity, not that every public playlist's genre label is perfect.
