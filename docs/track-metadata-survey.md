# 곡 태그·분위기 데이터 조사 — 2026-11-08

**실제 API 응답 조사. 앱 필터는 변경하지 않음. BPM은 사용자 요청에 따라 향후 필터에서 제외.**

| 항목 | 비 오는 날 재즈 | 80년대 SF 웅장한 |
|---|---:|---:|
| 후보 곡 | 20 | 20 |
| 표본 확보까지 조회한 영화 | 22 | 20 |
| Last.fm 태그 있는 곡 | 0 | 0 |
| MusicBrainz 태그 있는 곡 | 7 | 5 |
| 유효 BPM (>0) | 8 | 9 |
| BPM 0 (미상) | 12 | 11 |
| 고유 ISRC→MBID로 Last.fm 추가 조회 | 9 | 15 |

## 표본 방식

평점 7 이상·투표 1만 이상, 현재 인도 제외 설정 유지. 평점 내림차순·투표 내림차순으로 조회했다. 재즈 표본은 영화 Music 장르로 좁히지 않았다. SF 표본은 명시된 1980~1989년·Sci-Fi 조건만 적용했다. 각 영화에서 제목이 일치하는 Deezer OST 앨범의 rank 상위 3곡을 조사하고, 앞에서부터 서로 다른 ISRC 20개를 택했다. 이번 표본은 태그 필터와 Spotify 재생 가능 여부 검사를 적용하기 전의 조사 후보이며, 재생 목록으로 사용하지 않았다. 표본 수집 상한은 영화 100편이었다.

Last.fm: `track.getTopTags`, `autocorrect=0`. 원래 제목·아티스트 조회에 추가하여 같은 ISRC에 유일하게 연결된 MBID, 그리고 그 MBID의 정식 제목·아티스트로도 조회했다. 모두 태그가 비어 있었다. 공식 API 문서 예제 Radiohead / Paranoid Android는 태그 10개를 반환하여 키·API 동작을 확인했다. 이 예제는 후보 40곡에 포함하지 않았고 선곡에도 사용하지 않았다.

MusicBrainz: `/ws/2/isrc/{isrc}?inc=artists+tags&fmt=json`. 곡(recording) 자체의 양수 득표 태그만 집계했다. 아티스트나 앨범 태그를 곡 태그로 대신하지 않았다.

BPM 필드는 40곡 모두 있었지만 23곡은 0이었다. 유효 값은 재즈 표본 90.1~172.3(중앙값 111.92), SF 표본 89.88~161.5(중앙값 159.01). 이는 값의 제공 분포일 뿐, 감정 정확도를 검증한 결과가 아니다. 사용자 지시에 따라 BPM 필터는 제안에서 제거했다.

## 정규화 검토

앱에 반영하지 않은 검토 사전이다. 표기 정리와 의미 확장 규칙을 구분한다.

| 단계 | 후보 규칙 | 상태 |
|---|---|---|
| 표기 | NFKC, 소문자, `_ / -` 구분자 정리 | 검토안 |
| 명시적 장르 | 독립 단어 jazz/jazzy/재즈 및 이를 포함한 복합 태그 | 검토안 |
| 하위 장르 | bebop/swing/big band 및 재즈 하위 장르를 jazz로 분류 | 사용자 승인: 넓은 재즈 요청에 포함, 구체적인 하위 장르 요청은 좁힘 |
| 차분 | calm/chill/chillout/chilled/melancholy/melancholic/차분/잔잔 | 검토안 |
| 경쾌 | upbeat/cheerful/경쾌 | 검토안; BPM/fast/uptempo 제외 |
| 웅장 | epic 계열 | 엄격안 |
| 오케스트라·영화풍 | orchestral/cinematic | 웅장과 별도 차원으로 둘 것을 제안 |

기존 제안의 넓은 웅장 규칙(epic OR orchestral OR cinematic)으로 정규화만 시험하면 첫 표본의 재즈 태그 인식은 0→1곡, cinematic 태그 인식은 0→3곡이었다. 그러나 차분 태그가 없어 비 오는 날 재즈의 결합 조건은 0/20이었다. SF 표본의 웅장 조건도 0/20이었다. 이는 이 표본의 결과이며 전체 OST에 태그가 없다는 결론은 아니다.

태그 없는 곡을 추정으로 분류하지 않는다. `classical → calm`, `soundtrack → epic` 규칙은 만들지 않는다. 원문 태그, 정규화 결과, 적용한 규칙 ID와 출처를 보존하는 방식을 제안한다.

조회 범위 수정안: 영화 최대 1,000편, 각 영화 Deezer top3로 최대 3,000곡, 목표 20곡. 통과 곡부터 점진적으로 제공하고 백그라운드에서 채운다. 부족 시 실제 수와 사유를 표시한다. **사전과 범위는 사용자 검토 중이며 아직 구현하지 않았다.**

## 곡별 실제 값

### 비 오는 날 재즈

| 영화 | 곡 | ISRC | Deezer BPM | MusicBrainz 곡 태그 | Last.fm 곡 태그 |
|---|---|---|---:|---|---|
| The Godfather | Love Theme From "The Godfather" | USMC17251835 | 133.8 | big band, cinematic classical, film score, general film music, movie soundtrack, score, soundtrack, stage & screen | 없음 |
| The Godfather | The Godfather Waltz (Main Title) | USMC17251829 | 111.14 | big band, cinematic classical, film score, general film music, jazz_easy listening_big band, movie soundtrack, score, soundtrack, stage & screen | 없음 |
| The Godfather | The Godfather Waltz | USMC17251836 | 100.35 | big band, cinematic classical, film score, general film music, movie soundtrack, score, soundtrack, stage & screen | 없음 |
| You Can See Everything | V8 | USA2P2671929 | 0 (미상) | 없음 | 없음 |
| You Can See Everything | You Can See Everything | USA2P2671909 | 0 (미상) | 없음 | 없음 |
| You Can See Everything | Starlight | USA2P2671912 | 0 (미상) | 없음 | 없음 |
| The Dark Knight | The Dark Knight (Original Motion Picture Soundtrack) [Live] | TCAHH2378814 | 0 (미상) | 없음 | 없음 |
| Schindler's List | Schindler's List (Original Motion Picture Soundtrack) | TCAEO1929285 | 0 (미상) | 없음 | 없음 |
| The Godfather Part II | Ninna-Nanna A Michele | USUG10600332 | 90.1 | score, soundtrack | 없음 |
| The Godfather Part II | Main Title/The Immigrant | USUG10600328 | 172.3 | movie soundtrack, score, soundtrack | 없음 |
| The Godfather Part II | The Brothers Mourn | USUG10600335 | 112.7 | movie soundtrack, score, soundtrack | 없음 |
| David Attenborough: A Life on Our Planet | Life's Talent For Change | GBUM72000734 | 0 (미상) | 없음 | 없음 |
| David Attenborough: A Life on Our Planet | We Must Rewild The World | GBUM72000744 | 0 (미상) | 없음 | 없음 |
| David Attenborough: A Life on Our Planet | A Place Beyond Imagination | GBUM72000728 | 0 (미상) | 없음 | 없음 |
| O.J.: Made in America | Beyond Football | USLS51795628 | 107.11 | 없음 | 없음 |
| O.J.: Made in America | Made in America | USLS51795601 | 119.84 | 없음 | 없음 |
| O.J.: Made in America | Control | USLS51795607 | 0 (미상) | 없음 | 없음 |
| Inception | Inception (An Original Soundtrack) | QZMEQ2599749 | 0 (미상) | 없음 | 없음 |
| Fight Club | Fight Club (Original Soundtrack from 'Oldskool Heroes') | NLMP11900009 | 0 (미상) | 없음 | 없음 |
| The Good, the Bad and the Ugly | The Trio - Il Triello (Extended Version) | ITC460200123 | 0 (미상) | classical, contemporary classical, score, soundtrack | 없음 |

[원본 값·곡 ID·조회 결과](evidence/2026-11-08/metadata-survey/rainy-jazz-survey.json)

### 80년대 SF 웅장한

| 영화 | 곡 | ISRC | Deezer BPM | MusicBrainz 곡 태그 | Last.fm 곡 태그 |
|---|---|---|---:|---|---|
| The Thing | Humanity (Pt. 2) | USUM71603921 | 0 (미상) | 없음 | 없음 |
| The Thing | Humanity (Pt. 1) | USUM71603915 | 89.88 | 없음 | 없음 |
| The Thing | Shape | USUM71603916 | 0 (미상) | 없음 | 없음 |
| The Terminator | Main Title - The Terminator | US5941405749 | 0 (미상) | 없음 | 없음 |
| The Terminator | The Terminator Theme (Extended Version) | US5941405750 | 0 (미상) | 없음 | 없음 |
| The Terminator | Terminator Arrival / Reese Chased / Sarah on Motorbike | US5941405698 | 0 (미상) | 없음 | 없음 |
| E.T. the Extra-Terrestrial | Flying (From "E.T. The Extra-Terrestrial" Soundtrack) | USUM71416991 | 160.2 | classical, soundtrack, soundtracks | 없음 |
| E.T. the Extra-Terrestrial | Three Million Light Years From Home (From "E.T. The Extra-Terrestrial" Soundtrack) | USUM71416987 | 115.5 | 없음 | 없음 |
| E.T. the Extra-Terrestrial | Over The Moon (From "E.T. The Extra-Terrestrial" Soundtrack) | USUM71417065 | 161.5 | classical, soundtrack, soundtracks | 없음 |
| Back to the Future Part II | Back To Back / It's Your Kids (From “Back To The Future Pt. II” Original Score) | USQ4E1501748 | 159.63 | 없음 | 없음 |
| Back to the Future Part II | Main Title (Extended Version / From “Back To The Future Pt. II” Original Score) | USQ4E1501749 | 159.01 | 없음 | 없음 |
| Back to the Future Part II | End Title (From “Back To The Future Pt. II” Original Score) | USMC18930708 | 160.25 | movie soundtrack, score, soundtrack | 없음 |
| RoboCop | Rock Shop | US5941405169 | 0 (미상) | 없음 | 없음 |
| RoboCop | Drive Montage | US5941405162 | 0 (미상) | 없음 | 없음 |
| RoboCop | Across The Board/End Credits | US5941405177 | 0 (미상) | 없음 | 없음 |
| Star Trek IV: The Voyage Home | Main Title | USMC18622115 | 116.46 | 없음 | 없음 |
| Star Trek IV: The Voyage Home | Home Again: End Credits | USMC18622125 | 118.8 | 없음 | 없음 |
| Star Trek IV: The Voyage Home | Chekov's Run | USMC18622121 | 0 (미상) | 없음 | 없음 |
| Escape from New York | Main Title | GBAJC0002929 | 0 (미상) | ambient, electronic, experimental, minimal, movie soundtrack, score, soundtrack | 없음 |
| Escape from New York | The Bank Robbery | GBAJC0002930 | 0 (미상) | ambient, electronic, experimental, minimal, movie soundtrack, score, soundtrack | 없음 |

[원본 값·곡 ID·조회 결과](evidence/2026-11-08/metadata-survey/eighties-sf-survey.json)

## 재실행

```powershell
python tools/survey_track_metadata.py
```

API 키는 `work/lastfm-api-key.txt`에서만 읽는다. 키 및 키가 포함된 요청 URL은 증거에 저장하지 않는다. 기존 응답 체크포인트를 재사용하므로 같은 조회를 반복하지 않는다.

## 공식 문서

- https://www.last.fm/api/show/track.getTopTags
- https://musicbrainz.org/doc/MusicBrainz_API
- https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting


## 후속 검토: 태그에서 후보를 먼저 가져오기

사용자는 bebop, swing, big band 등을 재즈에 포함하도록 승인했다. 좁은 문자열 일치만 고집하지 않는다. 다만 영화/곡 목록 자체를 수동으로 선정하지는 않는다.

실제 Last.fm `tag.getTopTags` 조회에서 1,000개 태그를 받았다(`offset=0`, `num_res=1000`). 응답 메타데이터의 전체 태그 수는 2,896,448이다. 전체를 내려받았다고 주장하지 않는다. 50개 단위 offset 조회도 실제 동작을 확인했다. 이 추가 파라미터들은 현재 공식 메서드 설명에 명시되지 않으므로 구현 시 반환 offset/count를 검증해야 한다.

받은 목록에는 jazz, jazz fusion, Smooth Jazz, swing, acid jazz, nu jazz, vocal jazz, free jazz, bebop, Big Band, Jazz Rock, cool jazz, latin jazz, Hard Bop 등이 있다. 개인 분류로 보이는 태그도 섞여 있으므로 글자 포함 여부만으로 모든 태그를 OR에 넣지 않는다.

`tag.getTopTracks`를 jazz/bebop/swing/big band에 각각 호출해 5곡씩, 합계 20개의 서로 다른 후보를 받았다. **영화 OST 및 재생 가능 여부 검증 전**이며 앱 선곡에 넣지 않았다. 일부 응답은 `total=0` 또는 `total=3`인데 5곡을 반환하므로 페이지 총량 메타데이터를 그대로 믿지 않고 실제 반환 목록과 빈 페이지를 검사해야 한다.

`tag.getSimilar(jazz)`는 빈 목록을 반환했다. 자동 유사 태그 검색 기능이 실용적으로 작동한다고 주장하지 않는다. Deezer `/genre`는 22개 항목(All 포함)을 반환했으며 세부 곡 태그 사전의 대체재는 아니다.

### 검토할 설계

1. 공급자 주요 태그 + 곡에서 관측한 태그를 사전으로 유지한다.
2. 한국어 요청을 장르 계층/동의어/태그 의미로 연결한다. 재즈는 하위 장르를 포함하고 빅밴드는 그 하위 범위로 좁힌다.
3. 같은 의도 안의 태그별 후보를 합집합(OR)으로 모은다. 별도 태그마다 API를 호출해 병합하는 방식이며 한 번의 API OR 연산을 지원한다고 가정하지 않는다.
4. 곡의 영화 OST 관계를 확인하고, 기존 Deezer top3·ISRC·Spotify 재생 가능성 규칙을 적용한다. 일반 재즈 곡을 곧바로 영화 OST로 인정하지 않는다.
5. 원문 요청·확장된 태그·후보 출처·제외 사유를 남긴다. 영화 평점은 OST 검증된 영화에 적용한다.

이 방식은 영화 1,000편을 무조건 훑기 전에 관련 곡을 발굴하는 방법이다. 실제 OST와 이어지는 비율은 아직 측정하지 않았고, 구현은 하지 않았다. BPM은 사용하지 않는다.

실제 응답: [태그 1,000개](evidence/2026-11-08/tag-discovery/lastfm-top1000-tags.json), [재즈 후보](evidence/2026-11-08/tag-discovery/tag-toptracks-jazz.json), [비밥 후보](evidence/2026-11-08/tag-discovery/tag-toptracks-bebop.json), [스윙 후보](evidence/2026-11-08/tag-discovery/tag-toptracks-swing.json), [빅밴드 후보](evidence/2026-11-08/tag-discovery/tag-toptracks-big-band.json).

공식 메서드 설명: https://www.last.fm/api/show/tag.getTopTags , https://www.last.fm/api/show/tag.getTopTracks , https://www.last.fm/api/show/tag.getSimilar .
