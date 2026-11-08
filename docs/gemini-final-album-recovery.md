# Gemini 최종 판정·앨범 연결 재검증 — 2026-11-08

전체 상태: **미완료**. 앨범 연결 목표 70%에 도달하지 못했다. 검증 근거가 부족한 앨범을 받아들이지 않고, 사용자의 데이터 부족 시 중단 지시에 따라 자동 색인 활성화·새 경로 실기 재생 단계에서 멈췄다. 기존 설정과 로그인은 유지했다.

| 항목 | 상태(완료·미완료·확인 못함) | 증거 위치 |
|---|---|---|
| `60e94ee` 지정 브랜치 푸시 | 완료 | `git ls-remote origin refs/heads/feature/ost-top3-country` → `60e94eeae0e1d436ffb0355204d98f705d7a37ed`; main 변경 없음 |
| Gemini 최종 판정 컴포넌트·50곡 실제 호출 | 완료 | `evidence/2026-11-08/final-judge-album-recovery/gemini-final-50.json`; 52,831ms, 높음 38·중간 8·모름/낮음 4 |
| 최종 판정의 실제 선곡 연결 | 미완료 | `GeminiJudge.java`, `IndexRules.java`는 빌드됐지만 기존 선곡 경로에 연결하지 않음 |
| 스타일별 제외 버튼·기록·되돌리기 | 미완료 | `android/assets/index.html`, `FrameCore.java` 소스·빌드 완료. 새 UI 설치/실기 제외→다음 곡→되돌리기 검증은 하지 않음 |
| 200편 대안 검색·원본 119편 실패 분류 | 완료 | `album-recovery.json`: 기존 81편 + 복구 55편 = 136편, 64편 미연결 |
| 연결률 70% 이상 | 미완료 | 136/200 = 68%, 목표 140편보다 4편 부족 |
| 30개 실제 표본 앨범 대조 | 완료 | `album-audit-30.json`: Deezer 실제 30회 조회, 제목·작곡 크레딧·발매 형태·레이블·UPC 대조에서 오연결 발견 0/30 |
| 전체 136편 오연결 0 | 확인 못함 | 표본 검사로 전체 무오류를 증명하지 않음. 음원 청취나 권리자의 독립 인증도 아님 |
| 매일 자동 색인·체크포인트·요청 시 추가 조사 | 미완료 | 미활성 초안은 로컬 `work/drafts/TrackIndex.java`에 보관. 빌드/커밋 대상에서 제외 |
| 비 오는 날 재즈 실제 재생·URI·포스터 | 미완료 | 새 최종 판정 경로의 실행 기록 없음. 이전 경로의 재생을 새 결과로 대체하지 않음 |
| 80년대 SF 웅장한 실제 재생·URI·포스터 | 미완료 | 같은 이유로 새 실행 기록 없음 |

## 실제 대안 검색과 오연결 수정

고정 200편은 앞서 사용한 IMDb 투표 수 상위 표본 그대로다. 제목/원제/TMDB 번역명, 연도, 작곡가와 TMDB 인물 별칭으로 Spotify 앨범을 검색하고 앨범 UPC를 Deezer `album/upc:<UPC>`와 대조했다. 영화나 곡을 수동으로 추가하지 않았다. 원본 응답은 출처·받은 시각·7일 만료와 함께 `work/album-recovery*/cache`에 보존한다. Spotify 각 검색은 10개 결과 범위이며 전체 카탈로그의 부재를 증명하지 않는다.

확장 검색에서 처음 얻은 **147/200(73.5%)는 폐기**했다. 30개 표본에서 《헝거게임》에 프리퀄 음반, 《그랜 토리노》에 후년 연주 싱글, 스타워즈에 재연주 싱글이 섞였다. 기존 표본은 `rejected-expanded-audit-30.json`에 남겼다. 제목 부분 일치를 제거하고 편곡·모음곡·싱글을 거르며, OST 표기가 없는 앨범은 원 발매/저작권 연도 근거까지 요구했다. 재검사에서 이전 연결 12개가 거부되고 새 검색으로 1개를 복구하여 최종 136편이 됐다.

최종 표본은 기준을 바꾸지 않고 **기존 연결 15 + 새 연결 15**, `SHA256("album-audit-v1:" + IMDb id)` 순서로 추출했다. 새 연결 집합에서 오연결이 빠졌기 때문에 이전 표본과 일부 앨범이 다르다. 표본을 수동으로 골라 바꾸지 않았다.

## 원래 미연결 119편의 최종 분류

| 관측 분류 | 편수 | 해석 범위 |
|---|---:|---|
| 대안 공급자 검색으로 복구 | 37 | 원제·번역명·연도·작곡가 검색 등을 묶어 수행. 특정 조건 하나만의 인과적 효과는 분리 측정하지 않음 |
| 제목/OST 표기 차이로 복구 | 11 | 제목 정규화·정확한 UPC 연결을 통과 |
| Music from / Inspired by 표기에서 복구 | 7 | 연결된 영화 앨범이라는 근거이며 모든 수록곡이 영화 본편에 쓰였다는 보증은 아님 |
| 검색 범위에서 검증 가능한 후보 없음 | 30 | 앨범이 전 세계에 없다는 뜻이 아님 |
| Spotify 후보는 있으나 Deezer 대응 발매/원 OST 근거 미확보 | 24 | UPC 미제공 또는 싱글·편곡·발매 근거 부족 등의 세부 기록을 보존 |
| 기타: 작곡 크레딧 근거 없음 | 10 | 편집형 OST가 포함됨. 임의 작곡가로 대체하지 않음 |
| 합계 | 119 | 복구 55 + 여전히 미연결 64 |

원제와 연도는 검색 조건으로 실제 사용했지만 독립적인 실패 원인으로 확정할 수 없어 별도 확정 편수를 만들지 않았다. 상세 영화별 query·skip·앨범 ID는 증거 JSON을 참조한다. 엄격 재검사 실행은 537.3초, 실제 Spotify 94회·Deezer 74회, 캐시 Spotify 379회·Deezer 24회였다. 앞선 두 검색 단계와 표본 30회 비용은 이 수치에 포함되지 않는다.

## Gemini 실제 결과

곡명·영화·아티스트/작곡가·상위 5개 아티스트 태그·곡 태그를 50곡 한 번에 보냈다. `gemini-2.5-flash`의 스타일·분위기·확신도·한국어 이유와 입력 근거·수신/만료 시각을 저장했다. 아티스트 태그는 최종 판정으로 쓰지 않는다. 모름/낮음은 제외하며 나머지 46곡도 각 요청의 스타일와 분위기가 맞아야 통과한다. **46곡이 모두 재즈라는 뜻은 아니다.** 모델 확신도는 실제 음악 정확도를 보증하지 않는다.

## 테스트와 빌드

모든 아래 회귀 테스트는 **MOCK**이며 실제 공급자 실행은 위 JSON과 별개다. 테스트를 실패하지 않도록 약화하지 않았다.

```text
python -X utf8 -m unittest discover -s tests -p test_album_recovery.py -v
수정 전: FAIL test_movie_title_containing_score_is_preserved
AssertionError: True is not false
수정 후: Ran 5 tests ... OK
```

《The Score》의 제목에서 `Score`를 지워 다른 제목을 허용하던 오류를 테스트로 재현한 뒤 구현을 수정했다. 같은 5개 테스트에 속편·편곡·싱글 거부와 원 발매 근거 검사도 포함한다.

```powershell
$cp='C:\Users\happy\Documents\Codex\2026-11-01\new-chat\work\json-test.jar;build/android/classes;C:\Users\happy\AppData\Local\Android\Sdk\platforms\android-34\android.jar'
javac -encoding UTF-8 -cp $cp -d work/tests android/src/com/happy/cinemaframe/IndexRules.java tests/java/IndexRulesRegression.java
java -cp "work/tests;$cp" com.happy.cinemaframe.IndexRulesRegression
```

```text
수정 전: AssertionError: invalid mood accepted
수정 후: PASS IndexRulesRegression (MOCK): Gemini final, confidence, unknown, jazz subgenres, mood, TTL, completeness
PASS OstPolicyRegression (MOCK): interpretation, migration, rank top3, missing-rank stop, exact playable ISRC, 7-day expiry, 24 countries
PASS CountryContractRegression (MOCK): co-production, CN/HK separation, unknown retained
```

`./build.ps1 -JavaBin 'C:\Program Files\Java\jdk-17\bin'` 성공. 기존 `build/cinema-test.p12` 사용. HTML script 추출 후 `node --check work/remote-syntax.js` 성공. `git diff --check` 성공. 현재 프레임에 설치한 것은 이번 조사에 필요한 인증된 Spotify 앨범 검색·상세 읽기 API를 추가한 초기 빌드다. 이후 Gemini 컴포넌트·제외 버튼 빌드까지 설치됐다고 주장하지 않는다.

## 변경 파일과 남은 결정

- `SpotifySearch.java`, `FrameCore.java`: 인증된 앨범 상세/검색 API, 스타일 제외 저장/기록/되돌리기와 큐 필터.
- `android/assets/index.html`: 현재 요청 스타일의 제외 버튼과 설정의 되돌리기 목록.
- `GeminiJudge.java`, `IndexRules.java`: 50곡 판정, 근거·시간 저장, 응답 검증과 낮음/모름/만료 거부.
- `tools/recover_ost_albums.py`, `tools/audit_recovered_albums.py`: 재개 가능한 실제 대안 검색과 고정 표본 감사.
- `tests/test_album_recovery.py`, `tests/java/IndexRulesRegression.java`, `tests/java/GeminiFinalLive.java`: 모의 회귀와 실제 호출 도구 분리.
- 이 보고서와 `docs/evidence/2026-11-08/final-judge-album-recovery/`: 성공 및 폐기 결과 보존.

검증 근거가 부족한 64편에 대해 선택지는 (1) 편집형 OST의 정식 발매 크레딧·권리자 근거를 추가 대조해 70%를 계속 목표로 삼거나, (2) 현재 검증된 68%를 초기 범위로 승인하고 나머지는 계속 미확인으로 남기는 것이다. 어느 쪽도 제목 유사곡 대체나 임의 목록을 쓰지 않는다. 현재 승인 기준을 낮춰 적용하지 않았다.

커밋 전 15개 파일에 대해 금지 경로·실제 세 API 키 문자열 유출 검사를 통과했고 `gitleaks stdin --redact`에서도 `no leaks found`를 확인했다. 서명 키·토큰·원본 movies.json은 제외했다. 이번 변경은 로컬 커밋까지만 남기며 추가 푸시는 하지 않는다.
