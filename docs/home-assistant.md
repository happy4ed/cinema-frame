# Cinema Frame와 Home Assistant

조사일: 2026-11-01. 현재 설정을 초기화하거나 펌웨어를 바꾸지 않고 연동하는 방향입니다.

## 현재 앱을 직접 제어하는 방법

Cinema Frame의 LAN HTTP API에 Home Assistant의 공식 [RESTful Command](https://www.home-assistant.io/integrations/rest_command/)와 [RESTful Sensor](https://www.home-assistant.io/integrations/rest/)를 연결할 수 있습니다. 별도 Spotify 개발자 계정이나 프레임의 Spotify 로그인 정보가 필요하지 않습니다.

1. 프레임 IP가 바뀌지 않도록 공유기에서 DHCP 예약을 설정합니다. HA 서버가 프레임 TCP 8787에 접근할 수 있어야 합니다.
2. 프레임과 같은 LAN의 PC에서 `python tools/pair_home_assistant.py --host FRAME_IP`를 실행합니다. 프레임에 표시된 코드를 입력하면 `secrets.local.yaml`이 만들어집니다. 코드는 숨겨서 입력하고 세션 값은 콘솔에 출력하지 않습니다.
3. 생성된 항목을 Home Assistant의 기존 `/config/secrets.yaml`에 병합합니다. 기존 파일 전체를 덮어쓰지 마세요. 생성 파일을 Git이나 채팅에 붙이지 마세요.
4. [cinema_frame.yaml](../home-assistant/cinema_frame.yaml)을 `/config/packages/cinema_frame.yaml`로 복사합니다. `configuration.yaml`의 기존 `homeassistant:` 아래 `packages: !include_dir_named packages`를 병합합니다. 같은 최상위 키를 중복해서 만들지 마세요.
5. Home Assistant에서 설정 검사 후 다시 시작합니다. 상태 센서와 세 개의 재생 스크립트가 생성됩니다.

개발자 도구 → 작업에서 다음 예를 사용할 수 있습니다.

```yaml
action: rest_command.cinema_frame_request
data:
  text: "라라랜드 같은 음악"
```

```yaml
action: rest_command.cinema_frame_volume
data:
  value: 30  # 앱 볼륨 0~100; 프레임 전체 볼륨 아님
```

위치 이동은 `rest_command.cinema_frame_seek`에 `position_ms`와 현재 `/api/state`의 `version`을 전달합니다. 센서의 `version` 속성을 사용할 수 있지만 곡 전환 직후에는 5초 폴링 지연이 있으므로 거절되면 갱신 후 다시 요청하세요. 이전 곡에서 발생한 요청이 새 곡을 움직이지 않게 하는 검사입니다.

`script.cinema_frame_pause`, `script.cinema_frame_resume`, `script.cinema_frame_next`를 대시보드 버튼이나 자동화에 연결할 수 있습니다. 요청 문장은 REST payload에서 JSON으로 인코딩됩니다. 스마트 프레임의 화면 전원·밝기 제어는 이 API에 아직 없습니다.

**검증 범위:** 앱 HTTP API의 인증, 재생·볼륨·seek는 실기 테스트했습니다. 이 YAML은 문법과 템플릿을 확인한 예시이며 실제 Home Assistant 인스턴스에서 로드·자동화 실행은 아직 검증하지 않았습니다. 전용 HACS `media_player` 통합은 후속 작업입니다.

## 기존 Lenovo 관련 프로젝트

| 프로젝트 | 대상과 용도 | 이 앱과의 관계 |
| --- | --- | --- |
| [Immich HA Smart Frame Controller](https://github.com/hyungyunlim/immich-ha-sa) | README에서 Lenovo Smart Frame을 명시. Fully Kiosk/FreeKiosk, Immich, immich-kiosk 기반 사진 액자를 HA에서 제어. 애드온과 HACS 통합 제공 | 찾던 기존 프로젝트일 가능성이 있습니다. 사진/화면 제어 참고에 적합하며 Cinema Frame의 OST 선곡 API를 바로 지원하는 것은 아닙니다. |
| [Fully Kiosk 공식 통합](https://www.home-assistant.io/integrations/fully_kiosk/) | Android 기기 화면 켜기·끄기, 밝기, 기기 음량 등 | 화면 하드웨어 제어를 별도로 붙이는 후보. Remote Admin과 Fully Plus가 필요합니다. 실제 프레임에서 Cinema 앱과 병행 동작 검증 필요. |
| [LANovo](https://github.com/ygelfand/LANovo) | Lenovo Smart Display 7/8/10, ThinkSmart View 등. HA 음성 위성·대시보드·미디어 | 지원표에 CD-3L501F Smart Frame이 없습니다. 설치는 언락된 기기와 디버그 펌웨어를 요구하므로 현재 프레임에 그대로 적용할 대상으로 보지 않습니다. |

## 추천 순서

1. 현재 앱 API + REST package로 음악 제어를 먼저 연결합니다. 기존 설정을 바꾸거나 프레임을 초기화하지 않습니다.
2. 전용 HACS 통합에 코드 페어링과 `media_player`(재생·일시정지·다음·seek·앱 볼륨), 요청용 text entity를 구현합니다. 이 단계는 아직 구현하지 않았습니다.
3. 화면 전원·밝기는 Fully Kiosk/FreeKiosk의 실제 하드웨어 동작을 확인한 뒤 추가합니다.

집 밖에서는 인증된 Home Assistant 원격 접속이나 VPN을 통해 HA가 집 안 프레임을 조작하도록 구성할 수 있습니다. Cinema Frame의 HTTP 포트 자체를 인터넷에 공개하지 마세요. HA 음성 Assist와 요청을 연결하는 것도 후속 자동화 단계이며 현재 앱의 자유로운 AI 음성 이해 기능을 의미하지 않습니다.
