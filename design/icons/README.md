# Cinema Frame · Cinema Remote 앱 아이콘 후보 (2026-11-08)

회장님이 후보 하나를 고르면 그 후보만 앱에 넣는다. 고르기 전에는 `android/` · `phone/` 코드와 `res/` 를 건드리지 않는다.

- 비교 페이지(폰으로 보기): https://auto-grapher.com/artifacts/cinema-frame-icons.html
- 같은 페이지 파일: `design/icons/index.html`

## 후보

| 후보 | 폴더 | 한 줄 설명 |
|---|---|---|
| A · 포스터와 진행 바 (추천) | `A-poster-bar/` | 액자 화면 그대로: 세로 포스터(액자 테두리 + 매트 틈)와 그 아래 금색 진행 바. 리모컨은 같은 포스터에 One UI 파랑 재생 버튼을 겹침 |
| B · 음표 액자 | `B-note-frame/` | 세로 액자 안의 8분음표. 리모컨은 같은 음표가 파랑 휴대폰 테두리 안에. 단색(테마 아이콘)에서는 두 앱이 비슷해 보이는 것이 약점 |
| C · 필름 C | `C-film-c/` | Cinema 의 C 를 필름 구멍 셋 낸 고리로. 리모컨은 같은 C 의 열린 쪽으로 파랑 신호 두 줄 |

두 앱의 공통 규칙: 바탕은 액자 화면 바탕 `#080D13`, 표식은 액자 금색 `#D3BD92` 과 진행 바 색 `#F5D59A` (android/src/com/happy/cinemaframe/MainActivity.java 41 · 103 · 111행). 리모컨만 리모컨 One UI 9 시안의 파랑 `#387AFF` 을 더한다. A 의 진행 바 남은 쪽 `#5B5240` 은 금색을 바탕 쪽으로 낮춘 파생값.

## 폴더 구조 (후보마다 같음)

```
<후보>/
  frame/                     # 액자 앱 Cinema Frame (android/)
  remote/                    # 폰 리모컨 Cinema Remote (phone/)
    ic_launcher_foreground.svg   # 전경 원본 · 108×108 캔버스
    ic_launcher_monochrome.svg   # 단색(테마 아이콘) 원본 · 흰색
    ic_launcher_background.svg   # 배경 #080D13
    preview-masks.png            # 원형 · 둥근 사각 · 물방울 미리보기
    res/                         # 그대로 복사해 쓰는 안드로이드 자원
      mipmap-anydpi-v26/ic_launcher.xml
      mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_foreground.png
      mipmap-{…}/ic_launcher_monochrome.png
      mipmap-{…}/ic_launcher.png         # 레거시 48dp(둥근 사각으로 잘라 둔 것)
      values/ic_launcher_colors.xml      # ic_launcher_background = #080D13
```

## 적응형 아이콘 규격

| 항목 | 값 |
|---|---|
| 전경 · 단색 층 캔버스 | 108dp × 108dp |
| 화면에 보이는 영역 | 가운데 72dp (마스크가 잘라 냄) |
| 안전 영역 | 가운데 지름 66dp 원. 모든 후보의 전경이 이 안에 있음(렌더 픽셀 실측 0px 밖) |
| 배경 | 단색 `#080D13` (`@color/ic_launcher_background`) |
| 단색(monochrome) 층 | 흰색 · 투명 바탕 PNG. 안드로이드 13+ 테마 아이콘이 알파만 쓰고 색은 바탕화면 색으로 칠함 |
| 전경 · 단색 PNG 크기 (108dp) | mdpi 108 · hdpi 162 · xhdpi 216 · xxhdpi 324 · xxxhdpi 432 px |
| 레거시 ic_launcher.png (48dp) | mdpi 48 · hdpi 72 · xhdpi 96 · xxhdpi 144 · xxxhdpi 192 px (minSdk 26 이라 실제로는 anydpi-v26 이 쓰임) |

## 넣는 방법 (고른 후보를 `<X>` 라고 할 때)

1. 자원 복사
   ```bash
   mkdir -p android/res phone/res
   cp -r design/icons/<X>/frame/res/.  android/res/
   cp -r design/icons/<X>/remote/res/. phone/res/
   ```
2. 매니페스트 — `android/AndroidManifest.xml` · `phone/AndroidManifest.xml` 의 `<application>` 에 추가
   ```xml
   android:icon="@mipmap/ic_launcher"
   android:roundIcon="@mipmap/ic_launcher"
   ```
3. 빌드 스크립트 — **지금 `build.sh` 는 aapt 에 `-S`(자원 폴더)를 넘기지 않아 `res/` 가 APK 에 들어가지 않는다.** `ARGS=(package …)` 다음 줄에 추가:
   ```bash
   [ -d "$SRC/res" ] && ARGS+=(-S "$SRC/res")
   ```
   `build.ps1` 도 같은 자리에 같은 처리를 넣는다.
4. 확인
   ```bash
   ./build.sh --unsigned
   AAPT=/mnt/efs/hikari.hong/android-sdk/build-tools/35.0.0/aapt
   $AAPT dump badging build/Cinema-Frame-unsigned.apk  | grep "application:"   # icon='res/mipmap-anydpi-v26/ic_launcher.xml'
   $AAPT dump badging build/Cinema-Remote-unsigned.apk | grep "application:"
   unzip -l build/Cinema-Frame-unsigned.apk | grep -c mipmap                   # 16
   ```
   이 저장소와 같은 aapt(build-tools 35.0.0 · android-34.jar)로 A 후보 자원을 따로 묶어 시험했다: 컴파일 성공, badging 의 icon 이 `res/mipmap-anydpi-v26/ic_launcher.xml`, `<monochrome>` 도 xmltree 에 들어감.
5. 실기 확인(폴드8): 바탕화면 아이콘 · 앱 목록 · 테마 아이콘을 켰을 때 단색 모양. targetSdk 29 에서 One UI 런처가 테마 아이콘을 쓰는지는 아직 확인 못 함.

## 다시 만들 때

원본 도형은 SVG 경로(108 캔버스)다. 색은 위 표의 값만 쓰고, autographer 금색 조리개 로고는 쓰지 않는다(결과물마다 고유 표식). 모양을 바꾸면 안전 영역(지름 66dp) 안에 있는지 다시 잰다.
