# 국가별 제외 최종 실효 검증 — 2026-11-08

| 항목 | 상태 | 증거 |
|---|---|---|
| 24개국 P495/P345 수집 | 완료 | 국가·쪽별 체크포인트, LIMIT/OFFSET |
| 분당 1회 이하 | 완료 | 31회 요청 기록, 최소 시작 간격 61.000081062316895초 |
| 타임아웃 후 재개 | 완료 | 미국 두 번째 페이지 타임아웃 후 50,000→25,000건으로 줄여 같은 OFFSET에서 재개 |
| 데이터 반영·빌드·프레임 설치 | 완료 | 기존 서명으로 덮어 설치, 인증·설정 보존 |
| 실기 후보 검증 | 완료 | 제외 없음 26, US 6, CN 26, HK 25; 로컬 카탈로그의 예상 ID 집합과 일치 |
| 이전 설정 복원 | 완료 | 검증 도구 finally에서 원래 국가 설정 복원 |

## 매핑 통계

전체 **9690편**, 지정한 24개국 중 제작국이 확인된 영화 **9378편**, 이 범위에서 미매핑 **312편**.

미매핑은 Wikidata에 국가가 전혀 없다는 뜻이 아니다. 폴란드·소련 등 지정한 24개국 밖의 제작국도 포함한다. 해당 영화는 제외하지 않는다. 공동 제작은 아래 국가별 수에 중복 집계된다.

| 나라 | 코드 | 카탈로그 영화 수 |
|---|---|---:|
| 인도 | IN | 736 |
| 홍콩 | HK | 98 |
| 한국 | KR | 110 |
| 일본 | JP | 329 |
| 중국 | CN | 82 |
| 대만 | TW | 16 |
| 태국 | TH | 16 |
| 인도네시아 | ID | 9 |
| 미국 | US | 6330 |
| 캐나다 | CA | 316 |
| 멕시코 | MX | 84 |
| 영국 | GB | 1459 |
| 프랑스 | FR | 998 |
| 독일 | DE | 540 |
| 이탈리아 | IT | 360 |
| 스페인 | ES | 219 |
| 러시아 | RU | 39 |
| 튀르키예 | TR | 125 |
| 이란 | IR | 27 |
| 이집트 | EG | 4 |
| 나이지리아 | NG | 0 |
| 브라질 | BR | 33 |
| 아르헨티나 | AR | 34 |
| 호주 | AU | 225 |

## 80년대 SF 실제 프레임 API 결과

| 제외 설정 | 후보 수 | 검증 |
|---|---:|---|
| 없음 | 26 | 예상 26편과 ID 집합까지 일치 |
| US | 6 | 예상 6편과 ID 집합까지 일치 |
| CN | 26 | 예상 26편과 ID 집합까지 일치 |
| HK | 25 | 예상 25편과 ID 집합까지 일치 |

HK만 제외하면 `tt0083658` Blade Runner (`["HK","US"]`)가 제외됐다. CN만 제외에서는 유지됐다. P495 관계를 그대로 사용하며 중국을 홍콩·대만의 상위 국가로 확장하지 않았다.

US만 제외하고 남은 영화:

| 영화 | IMDb ID | countries |
|---|---|---|
| Brazil | tt0088846 | ["AU", "GB"] |
| The Road Warrior | tt0082694 | ["AU"] |
| Nausicaä of the Valley of the Wind | tt0087544 | ["JP"] |
| 1984 | tt0087803 | ["GB"] |
| Sexmission | tt0088083 | [] |
| Kin-dza-dza! | tt0091341 | [] |

## 테스트 명령과 출력

실기 테스트는 같은 명령으로 데이터 반영 전 실패, 반영 후 통과했다. 목(mock)이 아니다.

```powershell
python tools/verify_country_live.py --session-file 'C:\Users\happy\Documents\Codex\2026-11-01\new-chat\work\controller-session.txt'
```

반영 전:
```text
RuntimeError: Frame country mapping incomplete; refusing to claim verification
```

반영 후:
```text
NONE: 26 candidates
US: 6 candidates
CN: 26 candidates
HK: 25 candidates
PASS live country candidates: NONE / US / CN / HK; previous settings restored
```

수집기 단위 테스트는 네트워크·시간·IMDb ID를 **목 데이터**로 대체한다. 실제 국가 통계나 프레임 검증을 대신하지 않는다.

```powershell
python tests/test_country_collector.py
```
```text
test_mock_incomplete_checkpoint_cannot_replace_catalogue ... ok
test_mock_retry_after_http_date ... ok
test_mock_retry_after_pagination_resume_and_country_separation ... ok
Ran 3 tests
OK
```

## 재개 및 갱신

```powershell
python tools/wikidata_countries.py --apply
# 또는 기존 카탈로그에 국가 정보만 갱신
python tools/refresh_catalog.py --countries-only
```

기본 체크포인트 `work/wikidata-country-checkpoint.json`, 응답 페이지 `work/pages/`. 완료 페이지를 다시 받지 않는다. 수집기 OS 잠금은 동일 체크포인트의 중복 실행을 막는다. 요청 직전에 다음 허용 시각을 저장하며, HTTP 429/일시 오류의 Retry-After(초 또는 HTTP 날짜)를 준수한다. 타임아웃에는 최소 61초 후 재시도한다. 중단되면 같은 명령으로 재개한다. 원본 movies.json은 전체 완료 전 덮어쓰지 않는다.

P345 응답의 비제목 ID/잘못된 ID는 페이지의 rejected_non_title_ids에 기록하고 제외한다. OFFSET은 거절한 행을 포함한 원래 응답 행 수로 이동한다. 사용자 지정 24개국의 entity를 직접 조회하며 국가 계층을 추정해 확장하지 않는다.

User-Agent: `CinemaFrame/0.6 (https://github.com/happy4ed/cinema-frame/issues; country metadata)`

## 증거

- [전체 매핑 통계](evidence/2026-11-08/country-mapping/country-mapping-statistics.json)
- [실기 결과와 각 후보 countries](evidence/2026-11-08/country-mapping/country-live-verified.json)
- [요청 간격·페이지·재시도 기록](evidence/2026-11-08/country-mapping/request-audit.json)
- [반영 전 실패](evidence/2026-11-08/country-mapping/country-live-before-full-mapping.txt)
- [반영 후 통과](evidence/2026-11-08/country-mapping/country-live-after-full-mapping.txt)

## 변경 범위

국가 수집/체크포인트와 refresh_catalog 연결, 수집기 테스트, 실제 프레임 검증 도구, 메타데이터 조사 도구 및 보고서. 이번 변경에서 앱의 음악 스타일·분위기 필터는 수정하지 않았다. 태그 중심 OR 후보 수집은 검토 단계이며, 넓은 재즈 하위 장르 해석과 BPM 제외를 설계에 반영했다.

서명 키·Last.fm 키·토큰·movies.json·APK는 커밋하지 않는다. 사용자 지정 51a5cfc만 feature/ost-top3-country로 푸시 완료했으며 main은 변경하지 않았다. 이번 추가 커밋은 사용자 확인 전 푸시하지 않는다.
