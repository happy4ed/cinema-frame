# Cinema Frame

Lenovo Smart Frame에서 영화 포스터와 Spotify OST를 직접 재생하고, Android 폰으로 영화·장르·시대·분위기를 선택하는 개인용 프로토타입입니다.

**프레임이 직접 재생합니다.** 폰은 리모컨이며 PC 서버나 별도 LibreThing 앱 설치는 필요하지 않습니다. 프레임에 전원과 인터넷이 있으면 폰 앱을 종료해도 선곡과 재생이 이어집니다. 현재 리모컨은 같은 LAN에서만 연결합니다.

## 기능

- IMDb 영화 메타데이터에서 인기 영화 후보를 선택하고 MusicBrainz 관계를 따라 Spotify OST 앨범을 찾습니다.
- 영화·장르·시대·분위기 요청, 한국어 영화명 검색, 특정 영화 제외, 폰의 시스템 음성 입력을 지원합니다.
- 같은 영화 연속 재생을 막고 들은 곡을 기록합니다. 고정된 150편 재생목록은 아닙니다.
- 포스터는 매번 요청해 메모리에 표시합니다. 앱에서 포스터나 음악을 파일 캐시로 저장하지 않습니다. 메타데이터와 재생 이력은 저장합니다.
- 폰에서 포스터 표시를 폭 맞춤(위아래 검은 여백), 높이 맞춤(좌우 잘림 허용), 블러로 채우기(확대·블러 배경 위에 폭 맞춤 원본을 상단 정렬) 중 선택합니다. 선택은 프레임에 저장되며 전환 중 음악과 포스터 다운로드는 다시 시작하지 않습니다.
- 하단 정보를 곡 이름 → 음악가 → 컬러 장르·평점 → 영화 제목 순서로 표시합니다. 곡 이름은 Wanted Sans ExtraBold, 음악가는 Wanted Sans Bold, 작은 영화 정보는 Pretendard SemiBold를 사용하며 화면 맨 아래 4px 진행 선을 표시합니다.
- 앱 자체 볼륨, 일시정지·재개, 다음 영화, 재생 위치 이동을 지원합니다. 시스템 음량은 변경하지 않습니다.
- 중앙 연결 코드, 폰에서 Spotify 승인, 연결 및 재생 상태 복원을 제공합니다.

실기 검증: Lenovo CD-3L501F / Android 10, ARM64. 최소 Android 8입니다. Spotify Premium이 필요합니다.

## 처음 시작하기

1. 아래 절차로 APK를 빌드합니다. 이 저장소에는 개인 기기에서 추출한 로그인 정보나 사전 인증된 APK가 없습니다.
2. 프레임에 `Cinema-Frame.apk`, 폰에 `Cinema-Remote.apk`를 설치합니다. 순정 프레임에 APK를 처음 설치하는 경로는 별도로 준비해야 합니다. 부트로더 언락이나 초기화는 이 앱의 요구사항이 아닙니다.
3. 같은 Wi-Fi에서 두 앱을 실행합니다. 폰에서 검색된 프레임을 선택하거나 프레임의 IP 주소를 입력합니다.
4. 프레임 중앙에 표시된 코드를 입력합니다.
5. 폰의 Spotify 링크에서 본인 Premium 계정으로 승인하고 앱으로 돌아옵니다.
6. 원하는 영화나 분위기를 입력합니다. 앱 볼륨은 시스템 볼륨을 넘을 수 없으므로 시스템이 음소거이면 먼저 해제해야 합니다.

업데이트할 때는 같은 서명 키로 덮어쓰세요. 앱 삭제·데이터 삭제 시 연결과 이력이 사라집니다. 첫 빌드가 만든 서명 키는 개인적으로 보관하고 Git에 올리지 마세요.

## 빌드

Windows PowerShell, Python 3, JDK 17, Android SDK Platform 34 / Build Tools 35.0.0이 필요합니다.

```powershell
python tools/refresh_catalog.py
./build.ps1 -SdkPath "C:/Android/Sdk" -JavaBin "C:/Program Files/Java/jdk-17/bin"
```

첫 명령은 IMDb 데이터셋을 다운로드하므로 시간이 걸립니다. 이용 조건을 확인한 뒤 실행하세요. 영화 데이터셋과 이를 포함한 사전 빌드 APK는 공개 소스 저장소에서 제외했습니다. 로컬 빌드는 `data/movies.json`과 `android/assets/movies.json`을 생성하며 둘 다 Git에서 무시됩니다.

ARM64 재생 엔진과 해당 상위 프로젝트 소스 묶음은 포함되어 있습니다. 출처, 해시 및 재현성의 한계는 [third-party/NOTICE.txt](third-party/NOTICE.txt)에 기록했습니다. 기존 테스트 기기에 설치된 앱의 서명 키는 공개하지 않으므로 다른 개발자가 만든 APK로 그 기기의 앱을 덮어쓸 수는 없습니다.

## Home Assistant

**전용 HACS 통합을 제공합니다.** HACS 사용자 지정 저장소에 이 저장소 URL을 종류 **통합**으로 추가하고 설치·HA 재시작 후, 기기 및 서비스에서 Cinema Frame을 추가하세요. 프레임 주소와 화면의 6자리 코드로 연결합니다.

`media_player`(재생·일시정지·다음·seek·앱 볼륨·포스터), `select`(폭/높이/블러), `text`(영화·무드 요청)를 제공합니다. 별도 애드온이나 MQTT는 필요하지 않습니다. 기존 Immich·Fully Kiosk와 독립적으로 동작합니다. [설치·자동화·Assist·공존 안내](docs/home-assistant.md)를 참고하세요. 기존 [REST YAML 예시](home-assistant/cinema_frame.yaml)는 수동 연결용으로 남겨두었습니다.

## 현재 한계

- 자연어와 무드는 규칙 기반입니다. Gemini/LLM, 모든 곡의 BPM·오디오 특징 분석, 실시간 인기 순위는 구현하지 않았습니다.
- 모든 영화의 OST 연결이나 검증된 대표곡 세 곡을 보장하지 않습니다. 외부 검색·메타데이터 응답 변경에 영향을 받습니다.
- 공식 Spotify SDK 인증 제품이 아닌 오픈소스 재생 엔진 기반 개인 테스트입니다. Spotify 개발자 Client ID, TMDB 키, Gemini 키는 현재 요구하지 않습니다.
- 자동 부팅 실행, 장시간 네트워크 단절, 메모리 회수 상황은 추가 검증이 필요합니다. 2.5D 영상은 미구현입니다.
- 로컬 HTTP API는 신뢰하는 LAN용입니다. 인터넷에 직접 포트 포워딩하지 마세요. 서버 인증 구조와 원격 사용 경계는 [SECURITY.md](SECURITY.md)를 참고하세요.

## 라이선스

앱 코드는 GPLv3입니다. 폰트는 각각 SIL OFL입니다. 영화 메타데이터·포스터·음악에 이 소프트웨어 라이선스가 적용되는 것은 아닙니다. [IMDb 데이터 이용 안내](https://developer.imdb.com/non-commercial-datasets/), MusicBrainz와 Spotify의 이용 조건 및 콘텐츠 권리는 별도입니다.
