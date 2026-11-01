> **0.2 업데이트:** 화면 전원·밝기·전면 앱은 별도 Frame Diagnostics APK와 8788 연결을 추가하면 제어할 수 있습니다. [설치 안내](frame-hardware.md). 아래 8787 재생 연결은 그대로 유지합니다.

# Cinema Frame Home Assistant 통합

**독립 HACS 통합**입니다. 프레임 앱이 음악·선곡을 담당하고 HA는 LAN API로 제어합니다. 별도 애드온·MQTT·Spotify 개발자 계정은 필요하지 않습니다. 기존 폰 페어링과 Spotify Premium 로그인은 유지됩니다.

## 설치

1. 프레임에서 Cinema Frame을 실행합니다. 포스터 표시 선택은 앱 `0.5-test` 이상이 필요합니다. HA에서 프레임 TCP **8787**에 접근할 수 있어야 합니다.
2. HACS → 메뉴 → **사용자 지정 저장소**에 `https://github.com/happy4ed/cinema-frame`을 종류 **통합(Integration)**으로 추가합니다. HACS 기본 목록에 등록된 통합은 아닙니다.
3. Cinema Frame을 다운로드하고 HA를 재시작합니다. 프레임·폰 앱을 재설치할 필요는 없습니다.
4. 설정 → 기기 및 서비스 → 통합 추가 → **Cinema Frame**을 선택합니다.
5. 프레임 IP/호스트 이름과 포트 `8787`을 입력하고, 프레임에 표시된 6자리 코드를 입력합니다.
6. 하나의 기기 아래 미디어 플레이어·포스터 표시 선택·영화/분위기 요청 입력란이 생성됩니다.

수동 설치: `custom_components/cinema_frame` 폴더만 `/config/custom_components/cinema_frame`에 복사하고 HA를 재시작합니다. Android 소스·APK·영화 데이터셋은 HA에 복사하지 않습니다.

IP 변경 시 통합 메뉴의 **재구성**에서 주소를 변경하고 다시 페어링하세요. 기존 엔티티 식별자는 유지됩니다. 인증 무효화 시 재인증 흐름이 표시됩니다. 같은 프레임을 IP와 호스트 이름으로 중복 등록하지 마세요. 공유기의 DHCP 예약을 권장합니다.

## 기능

| 엔티티 | 기능 |
| --- | --- |
| `media_player` | 곡·음악가·영화·포스터, 재생/일시정지, 다음 영화, 위치 이동, **앱 볼륨** |
| `select` 포스터 표시 | `width` 폭 맞춤 / `height` 높이 맞춤 / `blur` 블러 배경 |
| `text` 영화·분위기 요청 | “라라랜드 같은 음악”, “1990년대 액션” 등의 요청 전달 |

상태는 3초마다 갱신합니다. 명령 전달 후 선곡/재생 엔진 처리 시간이 추가될 수 있습니다. 볼륨은 AudioTrack 앱 볼륨만 바꾸며 시스템 음소거 상태에서는 들리지 않습니다. 이전 곡·반복·임의 URL 재생·TTS는 제공하지 않습니다.

포스터는 HA가 프레임의 인증된 이미지 API에서 받아 프록시로 표시합니다. 토큰은 이미지 URL이나 엔티티 속성에 넣지 않습니다. 이 통합은 포스터 파일을 저장하지 않으며 HA·브라우저가 표시용 메모리 캐시를 사용할 수 있습니다.

## 자동화

아래 엔티티 ID는 예시입니다. HA 언어·중복 이름·사용자 변경에 따라 실제 ID가 달라지므로 기기 페이지에서 확인하세요.

```yaml
action: media_player.play_media
target:
  entity_id: media_player.cinema_frame
data:
  media_content_type: cinema_frame
  media_content_id: "라라랜드 같은 음악"
```

`media_content_type`은 반드시 `cinema_frame`입니다. Spotify URI/외부 URL을 직접 재생하는 기능이 아니라 영화·무드 문장을 요청하는 기능입니다. 요청 입력란은 `text.set_value`의 `value`로 같은 문장을 전달해도 됩니다. 입력 확정 시 새 선곡을 시작합니다.

```yaml
action: select.select_option
target:
  entity_id: select.cinema_frame_poster_display
data:
  option: blur
```

```yaml
action: media_player.volume_set
target:
  entity_id: media_player.cinema_frame
data:
  volume_level: 0.3
```

재생 위치는 `media_player.media_seek`의 `seek_position`에 **초**로 지정합니다. 곡 버전을 함께 확인하므로 곡 전환 직전의 오래된 요청은 거절될 수 있습니다.

## Assist 음성

기본 재생/일시정지/볼륨은 HA에서 미디어 플레이어를 Assist에 노출한 뒤 사용합니다. 영화·무드 요청은 **문장 트리거 → play_media 작업**으로 연결합니다.

```yaml
alias: Cinema Frame 영화 분위기 음성 요청
triggers:
  - trigger: conversation
    command:
      - "시네마에서 {mood} 틀어 줘"
      - "시네마에서 {mood} 들려줘"
actions:
  - action: media_player.play_media
    target:
      entity_id: media_player.cinema_frame
    data:
      media_content_type: cinema_frame
      media_content_id: "{{ trigger.slots.mood }}"
mode: restart
```

예: “시네마에서 라라랜드 같은 음악 틀어 줘”. 기존 HA Assist 마이크/음성 위성을 사용합니다. 이 통합이 레노버의 마이크를 음성 위성으로 만들지는 않습니다. 무드 해석 품질은 현재 앱의 검색 기능을 따릅니다.

## Immich·Fully Kiosk와 공존

- **Immich / immich-ha-sa:** 기존 가족 앨범·사진 액자. Cinema Frame의 의존성이 아닙니다.
- **Fully Kiosk 공식 통합:** 화면 전원·밝기 등 기기 제어. 기존 Remote Admin/Fully Plus 설정을 사용합니다.
- **Cinema Frame HACS:** 영화 OST·포스터·앱 볼륨·선곡 요청.

한 프레임의 전면 화면은 한 앱이 차지합니다. 사진 모드는 Cinema Frame 일시정지 → 기존 사진 앱 전면 전환, 영화 모드는 Cinema Frame 전면 전환 → 재생 재개로 자동화하세요. **이 통합 자체는 다른 앱 실행이나 화면 전원을 제어하지 않습니다.** Fully의 장치별 앱 실행 기능 또는 기존 별표 버튼을 연결해야 합니다. 화면 켜기가 Fully를 전면으로 올리는지 실제 장치에서 확인하세요.

런처 교체·Device Owner·언락·초기화는 필요하지 않습니다. 기존 사진 액자가 정상 동작한다면 재설치하지 마세요. 이전 REST package는 새 통합 검증 후 중복 자동화를 확인하고 정리할 수 있습니다. `secrets.yaml` 전체를 덮어쓰지 마세요.

## 검증과 제한

- 실기: 통합의 aiohttp 클라이언트로 상태 조회·앱 볼륨·포스터 모드·인증된 이미지·비인증 차단을 확인했습니다. 테스트 볼륨과 모드는 복원했습니다.
- CI: 실제 HA 테스트 런타임에서 엔티티 로드·표준 서비스·위치 단위 변환·입력 검증·오프라인 복구·페어링·재인증을 검사합니다. [최신 결과](https://github.com/happy4ed/cinema-frame/actions/workflows/home-assistant.yml)를 확인하세요.
- **사용자의 HA 서버에는 아직 설치하지 않았습니다.** HA 담당 에이전트가 설치와 사진/영화 모드 전환·Assist 음성을 최종 검증해야 합니다.
- 집 밖 제어는 기존의 인증된 HA 원격 접속을 사용합니다. 프레임 HTTP 포트는 직접 공개하지 않습니다. HA `.storage`에 제어 세션이 저장되므로 백업·진단자료를 공개하지 마세요.

참고: [HACS 구조](https://www.hacs.xyz/docs/publish/integration/), [HA 미디어 플레이어](https://developers.home-assistant.io/docs/core/entity/media-player/), [Fully Kiosk](https://www.home-assistant.io/integrations/fully_kiosk/), [immich-ha-sa](https://github.com/hyungyunlim/immich-ha-sa).

## 0.3.0 검색어 입력과 보내기

8787 재생 연결에 `text` **영화·분위기 검색어**와 `button` **검색어 보내기**가 있습니다. 대시보드에 둘 다 추가하세요. 입력을 저장한 다음 버튼을 눌러야 프레임으로 전송됩니다. 기존 `media_player.play_media`의 `media_content_type: cinema_frame`도 즉시 전송 방식으로 계속 지원합니다.

HACS 업데이트 후 HA를 재시작하세요. 기존 연결과 토큰을 삭제하거나 다시 페어링할 필요는 없습니다. 자동 생성된 실제 엔티티 ID를 선택하세요. Hardware(8788) 연결에는 이 재생 검색 버튼이 생기지 않습니다.
