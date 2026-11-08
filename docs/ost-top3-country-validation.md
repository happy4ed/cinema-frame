# Deezer OST / country validation — 2026-11-08

**Overall: INCOMPLETE. No mock result is presented as real playback.**

| Item | Status | Evidence |
|---|---|---|
| Deezer album/tracks/detail and authenticated Spotify ISRC search | Complete | [API excerpt](evidence/2026-11-08/api-preflight.json) |
| Two requests: 20 films each; 5 distinct films actually played per request | Complete for transport/identity, not musical-style accuracy | JSON evidence below |
| Country migration, 24-country UI, co-production filtering | Implemented; mock tests pass | CountryContractRegression / OstPolicyRegression |
| Full Wikidata mapping | Incomplete | WDQS first full query timed out mid-response; scoped query returned HTTP 429: Aggressively rate-limiting to 1 req / min - this rule was created during active wdqs outage |
| Jazz/mood accuracy | Incomplete | IMDb Music is a broad candidate projection; The Pianist and other non-jazz recordings were included. Rain/epic are displayed, not measured audio attributes. |

## Implemented rules

- Candidate catalogue only: rating >=7, votes >=10000, explicit decade and all explicit film genres. Sort rating descending, votes descending, IMDb ID ascending; at most 20.
- Music-style-only requests currently project to IMDb Music. This is disclosed in the profile; it does NOT establish jazz/dance/etc. style accuracy. No hidden claim of an acoustic classifier.
- Exact normalized film title plus original soundtrack/score designation in Deezer album title. This is metadata evidence, not an independently certified publisher/film relationship. Remakes and similarly named releases remain a validation limitation.
- Rank descending, Deezer track ID ascending tie-break; take 3 BEFORE Spotify resolution. Missing/invalid ISRC or no exact playable Spotify ISRC: skip, do not substitute rank 4 or fuzzy-title recordings.
- Movie round-robin: rank-1 recording of each film, then rank-2, then rank-3. Duplicate Spotify URIs removed. Immediate same-film transitions skipped. At exhaustion restart the list while retaining the adjacent-film guard.
- Cache: database ost_cache, metadata source and fetched_at/expires_at, seven days. Old seed-albums/album/style caches are not used by the active playback pipeline.
- Country arrays are persisted independently of the enable toggle. Any matching co-production country excludes the film; unknown country stays eligible. Legacy India preference migrates to IN.
- Full P495/P345/P297 mapping failed, so installed code explicitly marks legacy India-only mapping incomplete and retains the existing India protection. No partial WDQS response was written to movies.json.

## Real API preflight excerpt

```json
{
  "deezer_album_search": {
    "id": 236058892,
    "title": "Whiplash (Original Motion Picture Soundtrack)",
    "tracklist": "https://api.deezer.com/album/236058892/tracks"
  },
  "deezer_track_detail": {
    "id": 1398706542,
    "title": "Snare Liftoff - I Want to Be One of the Greats",
    "rank": 340080,
    "isrc": "US3M51428601"
  },
  "spotify_search": {
    "query": "isrc:US3M51428601",
    "http": 200,
    "tracks": [
      {
        "uri": "spotify:track:4gCr9LKQ74aMTvNZpA2UpI",
        "is_playable": true,
        "external_ids": {
          "isrc": "US3M51428601"
        }
      },
      {
        "uri": "spotify:track:6ndNBJgWBjkjNBSZq540rG",
        "is_playable": true,
        "external_ids": {
          "isrc": "US3M51428601"
        }
      }
    ]
  }
}
```

## Real playback evidence

### 비 오는 날 재즈

Interpreted profile:
```json
{
  "excluded_countries": [
    "IN"
  ],
  "country_exclusions_enabled": true,
  "excluded_movies": [],
  "pipeline": "deezer-isrc-v1",
  "genres": [
    "Music"
  ],
  "music_style": "jazz",
  "style": "catalogue",
  "music_mood": "rainy",
  "energy": "calm",
  "interpretation_note": "음악 스타일을 IMDb Music 영화 후보로 변환(음악 스타일 일치 보장은 아님) · 분위기는 해석해 표시하며 Deezer rank는 분위기 점수가 아닙니다"
}
```
20 candidate films; 21 playable exact-ISRC recordings. These are real account/API and engine telemetry results, not mocks.

| Film / IMDb ID | Deezer album | Deezer top3 track | Deezer 순위 (rank) | ISRC | Spotify URI | is_playable / skip |
|---|---|---|---:|---|---|---|
| Whiplash / tt2582802 | 236058892 | Caravan | 573332 | US3M51428606 | spotify:track:2Xtsv7BUMrNodQWH2JPOc0 | True |
| Whiplash / tt2582802 | 236058892 | Whiplash | 543363 | US3M51428604 | spotify:track:0v0oL77zDnGTdRwB2q6OIm | True |
| Whiplash / tt2582802 | 236058892 | Fletcher's Song In Club | 456520 | US3M51428605 | spotify:track:0ZFortLKtHTKy7YtuJiubt | True |
| The Pianist / tt0253474 | 97783 | Ballade No. 1 in G Minor, Op. 23 | 495879 | USSM10209789 | spotify:track:2YZmsX1eLeJmbkpyjd30ei | True |
| The Pianist / tt0253474 | 97783 | Nocturne, B. 49: Lento con gran espressione in C-Sharp Minor | 375057 | USSM10209788 | spotify:track:664fcA80WZzNL7uJWcCkLe | True |
| The Pianist / tt0253474 | 97783 | Prélude in E minor, Op. 28, No. 4 | 362869 | USLIC0600307 | spotify:track:1Xf4sNtMKc1xiUhuIPAUg2 | True |
| Samsara / tt0770802 | 120441762 | Maniwall (Remastered) | 99622 | FR00V1900069 | spotify:track:01wVkbXvhS4BxQxScjsgUA | True |
| Samsara / tt0770802 | 120441762 | The Stick and the River (Remastered) | 94068 | FR00V1900084 | spotify:track:3yBz9oXy7NMuymSu0GluC8 | True |
| Samsara / tt0770802 | 120441762 | Pema's Theme (Remastered) | 93056 | FR00V1900068 | spotify:track:2SWVd1Ig3TJBxR6cotvY52 | True |
| Searching for Sugar Man / tt2125608 | 70552812 | Can't Get Away | 406150 | USUM71810952 | spotify:track:6pmDqTidfGwyMtUXPogaOM | True |
| Searching for Sugar Man / tt2125608 | 70552812 | Street Boy | 368705 | USUM71810953 | spotify:track:1qckRGY4rDziXQv86ofYzW | True |
| Searching for Sugar Man / tt2125608 | 70552812 | I'll Slip Away | 356672 | USUM71810954 | spotify:track:5TFaVFcq4sblM4fW5wzG2Y | True |
| The Red Shoes / tt0040725 | 7112480 | Main Title | 68226 | QMDA71316326 | spotify:track:3A0qI4nOGb5Duez9pdDTWb | True |
| The Red Shoes / tt0040725 | 7112480 | "Heart of Fire" Ouverture | 56411 | QMDA71316327 | spotify:track:1TmdMYWHv0Dlog928UvcCb | True |
| The Red Shoes / tt0040725 | 7112480 | Irina's Returns | 29387 | QMDA71316343 | spotify:track:024tMOmLXyG0orAZh81yCt | True |

| Actual engine URI | Poster movie ID | Film |
|---|---|---|
| spotify:track:2Xtsv7BUMrNodQWH2JPOc0 | tt2582802 | Whiplash |
| spotify:track:2YZmsX1eLeJmbkpyjd30ei | tt0253474 | The Pianist |
| spotify:track:01wVkbXvhS4BxQxScjsgUA | tt0770802 | Samsara |
| spotify:track:6pmDqTidfGwyMtUXPogaOM | tt2125608 | Searching for Sugar Man |
| spotify:track:3A0qI4nOGb5Duez9pdDTWb | tt0040725 | The Red Shoes |

[All 20 film results and skip reasons](evidence/2026-11-08/rainy-jazz-audit.json) · [Actual playback and poster URLs](evidence/2026-11-08/rainy-jazz-playback.json)

### 80년대 SF 웅장한

Interpreted profile:
```json
{
  "excluded_countries": [
    "IN"
  ],
  "country_exclusions_enabled": true,
  "excluded_movies": [],
  "pipeline": "deezer-isrc-v1",
  "genres": [
    "Sci-Fi"
  ],
  "music_style": "",
  "style": "catalogue",
  "music_mood": "",
  "energy": "epic",
  "era": 1980,
  "interpretation_note": "명시한 영화 장르만 적용 · 분위기는 해석해 표시하며 Deezer rank는 분위기 점수가 아닙니다"
}
```
20 candidate films; 19 playable exact-ISRC recordings. These are real account/API and engine telemetry results, not mocks.

| Film / IMDb ID | Deezer album | Deezer top3 track | Deezer 순위 (rank) | ISRC | Spotify URI | is_playable / skip |
|---|---|---|---:|---|---|---|
| The Thing / tt0084787 | 77653052 | Humanity (Pt. 2) | 275128 | USUM71603921 | spotify:track:2UucrpQAWbJSpApvn6BZ2j | True |
| The Thing / tt0084787 | 77653052 | Humanity (Pt. 1) | 257604 | USUM71603915 | spotify:track:6hmVYOWXaSYLxZZVXWNun8 | True |
| The Thing / tt0084787 | 77653052 | Shape | 241119 | USUM71603916 | spotify:track:4xJWEDgTgcppRXWmFVQalr | True |
| The Terminator / tt0088247 | 133937082 | Main Title - The Terminator | 397835 | US5941405749 | spotify:track:6jNFXIiJDOvSzFqQOk0Gt9 | True |
| The Terminator / tt0088247 | 133937082 | The Terminator Theme (Extended Version) | 372634 | US5941405750 | spotify:track:4YrVtcY2gXTqJzRitYOLwH | True |
| The Terminator / tt0088247 | 133937082 | Terminator Arrival / Reese Chased / Sarah on Motorbike | 265860 | US5941405698 | spotify:track:2cJojSknndB8DjLfr9BMir | True |
| E.T. the Extra-Terrestrial / tt0083866 | 9510820 | Flying (From "E.T. The Extra-Terrestrial" Soundtrack) | 371132 | USUM71416991 | spotify:track:7lMb4MqMMBbApnMWHp1wC5 | True |
| E.T. the Extra-Terrestrial / tt0083866 | 9510820 | Three Million Light Years From Home (From "E.T. The Extra-Terrestrial" Soundtrack) | 191454 | USUM71416987 | spotify:track:1BwEs0zhm9avrXW4TFoE8a | True |
| E.T. the Extra-Terrestrial / tt0083866 | 9510820 | Over The Moon (From "E.T. The Extra-Terrestrial" Soundtrack) | 182845 | USUM71417065 | spotify:track:047uCf5LHj85fdBcGniA1e | True |
| Back to the Future Part II / tt0096874 | 11603330 | Back To Back / It's Your Kids (From “Back To The Future Pt. II” Original Score) | 203195 | USQ4E1501748 | — | no_exact_playable_spotify_isrc |
| Back to the Future Part II / tt0096874 | 11603330 | Main Title (Extended Version / From “Back To The Future Pt. II” Original Score) | 188580 | USQ4E1501749 | — | no_exact_playable_spotify_isrc |
| Back to the Future Part II / tt0096874 | 11603330 | End Title (From “Back To The Future Pt. II” Original Score) | 164067 | USMC18930708 | spotify:track:2tf6ooAc2ofanHtzAqG6O5 | True |
| RoboCop / tt0093870 | 133938582 | Rock Shop | 206250 | US5941405169 | spotify:track:1tiV6OkVfcWCYVTWKoRNRT | True |
| RoboCop / tt0093870 | 133938582 | Drive Montage | 204149 | US5941405162 | spotify:track:5GW8NxoUag5zx7ArTURDEj | True |
| RoboCop / tt0093870 | 133938582 | Across The Board/End Credits | 181313 | US5941405177 | spotify:track:12Ai0awAqHfJtLosPVA18Y | True |

| Actual engine URI | Poster movie ID | Film |
|---|---|---|
| spotify:track:2UucrpQAWbJSpApvn6BZ2j | tt0084787 | The Thing |
| spotify:track:6jNFXIiJDOvSzFqQOk0Gt9 | tt0088247 | The Terminator |
| spotify:track:7lMb4MqMMBbApnMWHp1wC5 | tt0083866 | E.T. the Extra-Terrestrial |
| spotify:track:2tf6ooAc2ofanHtzAqG6O5 | tt0096874 | Back to the Future Part II |
| spotify:track:1tiV6OkVfcWCYVTWKoRNRT | tt0093870 | RoboCop |

[All 20 film results and skip reasons](evidence/2026-11-08/eighties-sf-audit.json) · [Actual playback and poster URLs](evidence/2026-11-08/eighties-sf-playback.json)

## Country execution (incomplete data)

With the 1980s Sci-Fi profile: no exclusion 26 candidates; CN-only 26; HK-only 26. Returned sample countries are empty arrays. This does not verify real CN/HK exclusion: the catalogue lacks the full country mapping.

Legacy mapping only: total 9,690; known Indian films 736; other/unmapped 8,954. These are NOT the requested completed Wikidata mapping statistics.

[Actual country API results and sample countries](evidence/2026-11-08/country-live-incomplete.json)

## Tests

Mock fixtures only for unit tests; no mocking in the two live playback logs. Existing tests were not weakened or removed.

Commands from H:\Dev\cinema-frame (PowerShell):
```powershell
$cp='C:\Users\happy\Documents\Codex\2026-11-01\new-chat\work\json-test.jar;build/android/classes;C:\Users\happy\AppData\Local\Android\Sdk\platforms\android-34\android.jar'
& 'C:\Program Files\Java\jdk-17\bin\javac.exe' -encoding UTF-8 -cp $cp -d work/tests tests/java/CountryContractRegression.java tests/java/OstPolicyRegression.java
& 'C:\Program Files\Java\jdk-17\bin\java.exe' -cp "work/tests;$cp" com.happy.cinemaframe.CountryContractRegression
& 'C:\Program Files\Java\jdk-17\bin\java.exe' -cp "work/tests;$cp" com.happy.cinemaframe.OstPolicyRegression
node --test tests/volume.test.cjs
./build.ps1 -JavaBin 'C:\Program Files\Java\jdk-17\bin'
```

Before production changes: `AssertionError: CN co-production must be excluded`. After:
```text
PASS CountryContractRegression (MOCK): co-production, CN/HK separation, unknown retained
PASS OstPolicyRegression (MOCK): interpretation, migration, rank top3, missing-rank stop, exact playable ISRC, 7-day expiry, 24 countries
PASS: +/- 1%, hold repeat, release/cancel/blur stop, bounds, latest queued volume
```

## Changed files

- FrameCore.java: active Deezer/ISRC pipeline, persistent metadata cache, actual queue, audit API, country API and migration.
- DeezerOst.java: album/track metadata lookup, rank top3, exact playable Spotify ISRC matching and skip evidence.
- OstPolicy.java: pure parsing/filtering/ranking/cache validation.
- android/assets/index.html: 24-country picker and enable toggle, selected-first ordering, count, interpreted profile, Deezer rank queue display.
- tools/refresh_catalog.py: country enrichment and mapping statistics; failed network fetch leaves original catalogue intact.
- CountryContractRegression.java, OstPolicyRegression.java: explicitly mocked regression fixtures.
- This report and sanitized evidence files. No signing keys, tokens, or movies.json included.

## Remaining decisions / limits

Wikidata outage/rate limit must clear or an authorized paced/resumable refresh must be run. Accurate musical style selection requires an approved source/rule beyond IMDb film genres. The current metadata projection does not meet that part of the requested behavior; this change must not be described as complete.
