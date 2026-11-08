# 프레임 자동 색인·여러 리모컨 연결 — 2026-11-08

전체 상태 **미완료**. 사용자가 68% 연결 규칙을 승인하여 프레임에 설치하고 활성화했다. 실제 요청에서 Spotify가 HTTP 429를 반환했고, 프레임에 저장된 Retry-After 종료는 **2026-11-09 09:24:06.213 KST**다. 이 시각은 가장 빠른 재시도 시각이며 성공 보장이 아니다. 제한을 해제·우회하지 않았다.

| 항목 | 상태(완료·미완료·확인 못함) | 증거 위치 |
|---|---|---|
| e8f0142 지정 feature 브랜치 푸시 | 완료 | `git push origin e8f0142:refs/heads/feature/ost-top3-country` 성공 |
| 자동 색인 설치·활성화 | 완료 | 실제 `/api/index/status`: enabled=true, strict-2026-11-08, 2,911곡·미연결 64편 |
| 체크포인트 복구 | 완료 | APK 덮어 설치/프로세스 재시작 후 영화·곡·판정·연결 세션 유지. 공장 초기화·앱 삭제 없음 |
| 매일 실제 24시간 경과·전원 재부팅 관찰 | 확인 못함 | 일간 예산/커서/부팅 서비스는 구현했으나 24시간 관찰·물리 재부팅 검사는 수행하지 않음 |
| 프레임 Gemini 추가 판정 | 완료 | 실제 프레임에서 50곡 추가 응답·SQLite 저장, 기존 50 + 요청 중 50 + 독립 백그라운드 50 = 150곡; 자동 갱신 배치 카운터 1 확인 |
| 비 오는 날 재즈 실제 재생 | 미완료 | 해석·Gemini 판정 후 Spotify ISRC 조회에서 429. 재생 URI·포스터 증거 없음 |
| 80년대 SF 웅장한 실제 재생 | 미완료 | 같은 제한 동안 새 Spotify 호출을 하지 않음. 파싱 회귀 테스트만 통과 |
| 재즈 아님 실제 버튼 → 다음 곡 → 되돌리기 | 미완료 | 새 경로의 재생을 시작하지 못해 실제 버튼 전환을 검사하지 못함 |
| 같은 ISRC의 다른 영화 재등장 방지 | 미완료 | 실제 공유 ISRC는 현재 136개 앨범에서 0개. MOCK 두 영화/동일 ISRC/대소문자/다른 스타일/되돌리기 테스트는 통과했으나 실기 증거로 대체하지 않음 |
| 여러 컨트롤러 동시 연결 | 완료 | 서로 다른 세션 두 개 + 기존 세션 동시 조회, B의 볼륨 변경을 C와 기존 세션에서 확인, 원래 볼륨 복원, 업데이트 후 연결 유지 |
| 실제 휴대폰 두 대 동시 UI 조작 | 확인 못함 | 연결된 실물 휴대폰은 한 대. 위 검사는 실제 프레임에 독립 HTTP 클라이언트 두 개를 사용한 검사 |
| 연결률 개선 연구·새 규칙 활성화 | 미완료 | 64편 사유별 후속 조사 목록 보존. 새 규칙은 활성화하지 않았고 30개 실제 표본 무오연결 검증 전 활성화 금지 |
| main 병합 | 미완료 | 병합하지 않음. 실기 미완료 상태라 병합 승인을 요청하지 않음 |

## 구현 동작

프레임 내부 `track-index.db`에 영화·곡·출처가 있는 7일 API 캐시·Gemini 판정/입력 근거/시각·미연결 사유·감사 기록을 저장한다. 기존 실제 실험의 캐시 136개 앨범을 실제 수신 시각을 유지해 옮겼다. APK에 고정 앨범/곡 목록을 넣지 않았다. 새 설치는 로컬 카탈로그와 TMDB 공급에서 직접 조사한다. API 키는 리모컨 설정에서 입력해 프레임 전용 설정에 저장하며 조회 응답/로그에 반환하지 않는다.

`FrameService`가 부팅 때 복원된다. 부팅 수신기는 서비스만 시작하고 전면 앱을 바꾸지 않는다. 90초 뒤 시작하여 작업 완료 후 60초 간격으로 갱신한다. 네트워크 요청 간격 최소 1초, Gemini 배치 간격 최소 15초, 배치 최대 50곡. 하루 작업 예산은 영화 80편·판정 20배치(최대 1,000곡)다. 중간 결과를 각 영화/배치마다 저장한다. 실패한 영화는 사유를 저장하고 하루 뒤 재시도 대상으로 우선한다. 유효한 API 캐시는 7일 동안 재사용한다.

TMDB 인기·현재 상영·시대별 목록은 매일 순회한다. 기존 IMDb 영화는 덮어쓰지 않는다. TMDB에서 새로 공급된 영화의 평점/투표수는 TMDB로 명시하며 IMDb 점수로 표시하지 않는다. 국가 필터는 기존 Wikidata 매핑을 유지한다. 새 영화의 TMDB production_countries는 보조 근거로만 보관하고 Wikidata 매핑 전에는 countries=[]인 미상으로 남겨 제외하지 않는다. 새 영화의 Wikidata 매핑은 기존 수집 도구의 후속 갱신이 필요하며 이번 프레임 작업에 자동 매핑을 추가하지 않았다. 새 공급의 선택 기준은 TMDB 7점·100표, 기존 IMDb는 7점·10,000표다.

요청은 음악 스타일을 IMDb Music 영화로 바꾸지 않는다. 명시한 SF 등의 영화 장르·시대·국가 제외만 영화 단계에 적용하고, 스타일/분위기는 신선한 Gemini 높음/중간 판정으로 곡을 거른다. 맞는 곡 중 영화별 Deezer rank top3, 영화 평점 순 라운드로빈, 정확한 ISRC와 is_playable만 통과한다. 초기 요청의 추가 판정 상한은 4×50곡, 필요한 경우 최대 16편 추가 조사이며 목표는 20곡이다. 부족하거나 API 오류면 숨기지 않고 표시한다. **Spotify 재생 가능성 검사에서 탈락해 목표 곡 수가 부족해지는 경우의 추가 확장과 실측 성능은 아직 실기 검증이 필요하다.**

아티스트 태그는 조사 순서의 힌트와 Gemini 입력 근거로만 쓴다. 최종 판단을 대신하지 않는다. 모름/낮음/7일 만료 판정은 제외한다. 화면·감사 기록의 출처는 `Gemini 판단 · Deezer 순위`다.

Spotify 제한은 독립적으로 보존하며 재시도 시각 전 새 검색을 보내지 않는다. 이 대기를 Last.fm/Gemini 전체 정지로 확장하던 초기 설치의 국소적 대기는 수정했다. 최종 앱은 Spotify 연결 갱신이 막혀도 이미 수집한 곡의 Gemini 판정을 진행한다. 요청 재시도는 서버 지정 시각 이후에만 가능하다.

## 여러 폰

새 페어링은 별도 세션을 추가한다. 기존 세션을 임의 제거하던 코드를 없앴다. 설정의 **다른 휴대폰 연결**에서 프레임 코드를 띄우고 새 폰에 입력하면 된다. 프레임은 단일 재생 상태를 공유하고 각 폰은 같은 상태·제외 목록을 조회한다. 다른 폰의 새 요청도 입력창이 편집 중이지 않을 때 반영한다. 128개 세션 한도를 넘으면 기존 연결을 지우는 대신 새 연결 오류를 표시한다.

## 테스트

`IndexPipelineRegression`을 변경 전 `e8f0142`의 OstPolicy/MusicStyle로 컴파일해 실행:

```text
AssertionError: music style incorrectly constrains film genres
```

동일 테스트를 변경 후 실제 빌드 클래스에 실행:

```text
PASS IndexPipelineRegression: MOCK style/era/subgenre/sequel/single + 30 RECORDED REAL title fixtures (not new network calls)
PASS IndexRulesRegression (MOCK): Gemini final, confidence, unknown, jazz subgenres, mood, TTL, completeness
PASS OstPolicyRegression (MOCK): interpretation, migration, rank top3, missing-rank stop, exact playable ISRC, 7-day expiry, 24 countries
PASS CountryContractRegression (MOCK): co-production, CN/HK separation, unknown retained
```

실행 명령(작업 경로 H:\Dev\cinema-frame):

```powershell
./build.ps1 -JavaBin 'C:\Program Files\Java\jdk-17\bin'
$cp='C:\Users\happy\Documents\Codex\2026-11-01\new-chat\work\json-test.jar;build/android/classes;C:\Users\happy\AppData\Local\Android\Sdk\platforms\android-34\android.jar;work/tests'
javac -encoding UTF-8 -cp $cp -d work/tests tests/java/IndexPipelineRegression.java tests/java/IndexRulesRegression.java
java -cp $cp com.happy.cinemaframe.IndexPipelineRegression
java -cp $cp com.happy.cinemaframe.IndexRulesRegression
node --check work/remote-syntax.js
git diff --check
```

이전 work/tests의 오래된 IndexRules가 클래스패스에서 우선되어 한 번 `NoSuchMethodError`가 발생했다. 테스트를 바꾸지 않고 클래스패스를 실제 최신 빌드 우선으로 수정한 뒤 통과했다. 앱 빌드·서명 확인·기존 키로 `adb install -r` 성공. 목 테스트와 기록된 응답 fixture를 실제 재생 성공으로 표현하지 않는다.

## 파일 변경

- `TrackIndex.java`, `AlbumIdentity.java`: 지속 색인·검증된 연결 규칙·미연결 기록·캐시·요청 처리.
- `FrameCore.java`, `BootReceiver.java`, `AndroidManifest.xml`: 색인 API/서비스 시작·기존 재생 연결·독립 세션 유지.
- `IndexRules.java`, `OstPolicy.java`, `MusicStyle.java`: 곡 판정/ISRC 제외·영화 장르 오투영 제거·빅밴드 요청 유지.
- `SpotifySearch.java`: 서버 지정 대기 시각 읽기.
- `android/assets/index.html`, `MainActivity.java`: 색인 설정·다른 폰 연결·판정 출처·평점 출처 표시.
- `build.ps1`, `.gitignore`, `config/music-tags.json`: 편집 가능한 사전을 빌드 시 복사, 생성 자산 제외.
- 두 Java 테스트와 이 보고서/실제 기록: 실패·성공·남은 제한 증거.

새 자동 선곡의 전체 완료 판정과 main 병합은 Spotify 제한 이후 두 요청의 재생/포스터 및 실제 제외·되돌리기 검증이 끝난 뒤에 한다.
