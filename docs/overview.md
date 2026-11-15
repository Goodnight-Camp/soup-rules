# soup-rules — 외국인 렌터카 운전자를 위한 한국 도로 규칙 안내

Pleos Connect / Android Automotive 앱입니다. 렌터카에 타면 운전자의 언어로 바뀌고, **한국에만 있는 규칙을 위반 직전 순간에** 화면과 음성(운전자 언어 TTS)으로 알려 줍니다. P에 정차하면 "오늘 과태료가 나올 뻔한 순간 3번, 최대 약 170,000원"처럼 정리합니다.

판단 근거는 폰 번역 앱이 볼 수 없는 차량 신호입니다. 실제 차속, 깜빡이, 기어(P 진입), 위치를 씁니다.

> 앱 화면에는 "코파일럿/파일럿" 대신 "도로 규칙 안내"라고 씁니다. 운전자 보조(ADAS)로 오해되지 않게 하려는 것입니다(hyundai-autolog PLAN.md와 같은 원칙).

## 왜

기획 메모 요약입니다(출처는 기획 문서 기준).

- 2021~25년 외국인 렌터카 이용자의 미납 과태료는 16억9097만원입니다. 미납률은 2024년에 60.7%였습니다. 운전자가 출국하면 사실상 징수할 수 없습니다.
- 제주 교통사고 중 렌터카 비율은 11.4%로, 광역자치단체 가운데 유일하게 두 자릿수입니다. 낯선 지리와 낯선 규칙이 주요 원인입니다.
- 비용을 낼 쪽은 렌터카 회사(과태료 처리·사고 손실 감소)와 관광공사·지자체입니다.
- 대상: 국제운전면허 협약국 관광객과 국내 거주 외국인입니다. 2018년 제주 기준으로 외국인 렌터카 이용자의 70%가 싱가포르·말레이시아·홍콩 관광객이었습니다. 중국은 협약 비가입국이라 국제운전면허로 렌트하기 어렵습니다(`CountryLanguage.idpNotRecognized`).

## 하는 일

| 규칙 | 감지 신호 | 안내 | 과태료 직전 기록 |
| --- | --- | --- | --- |
| 빨간불 우회전 일시정지 | 우회전 깜빡이 ON + 우회전 지점 50m 이내 | "빨간불이면 정지선에서 완전히 멈춘 뒤", 우회전 신호등이 있으면 "초록 화살표에만" | 교차로 안을 2km/h 넘게 지나가면 기록(6만원) |
| 우회전 후 횡단보도 | 우회전 직후 20초 안에 횡단보도 40m 전 | "건너거나 건너려는 사람이 있으면 멈추세요" | 없음(보행자 유무를 알 수 없음) |
| 우회전 규칙 팁 | 지점 데이터가 없어도 첫 우회전 깜빡이 | 렌트당 한 번 설명 | 없음 |
| 비보호 좌회전 | 좌회전 깜빡이 + 비보호 지점 50m 이내 | "초록불에서만, 마주 오는 차·보행자에 양보" | 없음(신호 색을 알 수 없음) |
| 좌회전 규칙 팁 | 지점이 없는 곳의 첫 좌회전 깜빡이 | "좌회전 화살표에만, 비보호 표지만 예외" | 없음 |
| 깜빡이 없는 회전 | 위치로 계산한 진행 방향이 12초 안에 60° 넘게 바뀜. 30km/h 이하 또는 운전대 90° 이상(곡선 도로 제외) + 5초 전부터 깜빡이 없음 | 회전 직후 설명 | 기록(3만원) |
| 깜빡이 없는 차로 변경(추정) | 40km/h 이상에서 운전대가 한쪽 4° 넘게, 반대쪽 4° 넘게 돌았다가 6초 안에 가운데로 돌아옴 + 깜빡이 없음 | 설명(2분에 한 번만) | 기록(3만원, "추정"으로 표시) |
| 안전띠(전 좌석) | 10km/h 이상 + 운전석 미착용, 또는 착석이 확인된 좌석 미착용 | 운전석은 긴급, 동승석은 "뒷좌석 포함 전 좌석" 설명, 60초 간격 | 운행(P까지)당 한 번(3만원) |
| 회전교차로 진입 방향 | 회전교차로 40m 전 | "반시계(오른쪽)로, 안의 차가 우선, 나갈 때 오른쪽 깜빡이" | 안에서 시계 방향으로 45° 넘게 돌면 긴급 경고 + 기록(3만원) |
| 스쿨존 30km/h | 위치가 어린이보호구역 30m 전 | 제한속도·과태료 두 배 안내 | 구역 안에서 제한+1km/h 이상이 2초 넘으면 긴급 경고(10초 간격), 방문당 1건 최고 속도로 기록(7만~16만원) |
| 스쿨존 신호 없는 횡단보도 | 보호구역 안(데이터 표시 또는 구역 포함) 신호 없는 횡단보도 40m 전 | "사람이 없어도 완전히 멈추세요" | 멈추지 않고 지나가면 기록(6만원) |
| 어린이 통학버스 | 첫 스쿨존 진입 | "빨간 등 깜빡이며 서 있으면 멈춘 뒤 서행, 앞지르기 금지" | 없음(버스를 알아볼 수 없음) |
| 버스전용차로 운영 시간 | 구간 진입 + 운영 시간(KST) + 주행 중 | "09:00까지 운영 중, 우회전 때만 진입" | 운영 중 P 진입 시 긴급 경고 + 기록(5만원) |
| 어린이보호구역 정차 | P 진입 + 구역 안(운영 시간) | 주정차 금지·세 배 | 기록(12만원) |
| 소화전·교차로 모퉁이·버스정류소·횡단보도 | P 진입 + 금지 지점(소화전·모퉁이 5m, 정류소 10m) | 사유별 경고 + "1분 뒤 누구나 신고" | 기록(소화전 8만원, 그 외 4만원) |
| 주정차 단속 구간 | P 진입 + 단속 구역 + 운영 시간 | "N분 뒤 단속" 또는 "즉시 단속" | 기록(4만원), 유예 1분 전 재알림 |
| 주차 팁 | 구역 밖 첫 P | 소화전·모퉁이 5m, 정류소 10m, 1분 신고 | 없음 |
| 하이패스 | 요금소 300m 전 | 단말이 있으면 "파란 차로, 30km/h", 없으면 "일반/TC 차로", 모르면 "카드가 있을 때만" | 단말이 있는데 차로 안에서 제한+10km/h 이상이면 경고 |
| 오늘 요약 | 30초 넘게 주행한 뒤 P를 10초 유지 | 오늘(한국 날짜) 건수·합계를 말함 | — |

### 안내가 나오는 방식

글을 읽지 않아도 알 수 있게 그림, 소리, 짧은 제목을 함께 씁니다. 음성이 영어로 나가는 언어 사용자를 위한 것입니다.

| 요소 | 내용 |
| --- | --- |
| 규칙 아이콘 | 14종(스쿨존, 감속, 우·좌회전, 횡단보도, 버스전용차로, 통학버스, 정차 금지, 단속 카메라, 안전띠, 회전교차로, 역방향, 방향지시등, 요금소). 심각도 색으로 칠합니다(`ic_rule_*`, `tools/gen_icons.py`) |
| 짧은 제목 | 2~4단어(예: "Slow down to 30", 「時速30キロに減速」). 크게 표시하고 긴 설명은 그 아래 작게 둡니다. 11개 언어 |
| 효과음 | 음성 직전에 냅니다. 긴급은 높은 음 3번, 주의는 중간 음 2번, 안내는 낮은 음 1번입니다. 소리 파일 없이 사인파로 만들고, 내비 안내 오디오 용도로 재생해 음악은 잠깐 줄입니다. 음성을 쓸 수 없어도 효과음은 나고, 음소거면 둘 다 꺼집니다 |
| 화면 위 알림 | 앱이 뒤에 있을 때(내비가 앞에 있을 때)만 띄웁니다. 긴급·주의 안내를 규칙 아이콘, 짧은 제목, 설명이 담긴 Android 표준 헤드업 알림으로 보여 주고 10초 뒤 사라집니다. 오버레이는 쓰지 않습니다 |

### 주정차 금지는 무엇으로 판단하나

**P에 넣은 순간**을 주정차 시작으로 봅니다. 그 위치를 아래 데이터와 비교합니다. 위에 있는 것이 우선입니다.

1. 어린이보호구역
2. 주정차 금지 지점(소화전·교차로 모퉁이·버스정류소·횡단보도)
3. 운영 중인 버스전용차로
4. 단속 CCTV 구간

D에서 멈춰 있는 경우(신호 대기와 구분할 수 없음)와 노면 표시(황색 실선·점선)는 판단하지 않습니다. 카메라가 없기 때문입니다. 데이터에 없는 곳에서는 첫 P에서 규칙 팁만 한 번 말합니다.

### 하이패스

Pleos `Toll` API는 ETCS 안내 음량만 제공하고 카드 장착 여부는 알려 주지 않습니다. 그래서 렌터카 체크인 신호의 `hipass` 값으로 받습니다. 값이 없으면 "카드가 있을 때만 파란 차로"라고 안내합니다.

금액은 승용차 무인단속 과태료 기준 **예상치**입니다(`core/Fines.kt`, 도로교통법 시행령 별표 6·7, 2024년). 화면에도 예상이라고 표시합니다.

## 다국어 음성(STT/TTS)

Pleos `SpeechToText`/`TextToSpeech` 3.3.1(ON_DEVICE)을 사용합니다. 사용법은 hyundai-autolog의 `VoiceSource`를 따랐습니다. 리스너는 initialize 성공 뒤에 붙이고, 로케일은 국가까지 포함한 태그로 넘기며(`ko`가 아니라 `ko-KR`), 최종 결과(`isFinal`)만 처리합니다. Gleo AI에는 앱이 직접 쓸 수 있는 받아쓰기 API가 없습니다. 이 SDK가 Gleo 모델을 쓰는 공개 경로입니다(autolog `docs/sdk-notes.md`, 2026-11-12 확인).

**지원 언어(11개):** 화면 문구는 11개 언어 모두 운전자 언어로 나옵니다. 음성은 Pleos STT/TTS SDK가 공식 지원하는 로케일(ko-KR, en-US, fr-CA, es-US, es-MX)이 있는 4개 언어만 그 언어로 나옵니다. 나머지 언어는 영어 음성으로 안내하고, 화면에 그 사실을 운전자 언어로 띄웁니다. 언어 선택 창에서는 음성까지 되는 언어에 🔊를 붙입니다.

| 언어 | 화면 | 음성(STT/TTS) 로케일 |
| --- | --- | --- |
| English | ✅ | en-US |
| 한국어 | ✅ | ko-KR |
| Français | ✅ | fr-CA |
| Español | ✅ | es-US → es-MX |
| 日本語 · 简体中文 · 繁體中文 · Bahasa Melayu · ไทย · Tiếng Việt · Bahasa Indonesia | ✅ | 공식 미지원 → 영어 음성 |

공식 목록 밖의 로케일(ja-JP 등)은 시도하지 않습니다. 엔진이 받아들이더라도 다른 언어 목소리로 읽을 위험이 있기 때문입니다. Gleo 비서의 다국어 지원과 앱용 SDK의 지원 범위는 별개입니다. 실차가 더 지원하는지는 아래 `voice_probe`로 확인한 뒤 `OfficialVoice.locales`를 넓히면 됩니다. 화면 문구와 음성 문구는 모든 언어에 같은 키로 들어 있고, 테스트가 누락과 자리표시자 불일치를 막습니다.

**운전자 언어 결정**(`LanguageResolver`)은 아래 순서를 따릅니다.
1. 운전자가 고른 언어. 화면에서는 P에서만, 음성으로는 언제든 바꿀 수 있습니다.
2. 렌터카 체크인으로 받은 언어 또는 면허 발급국(SG→영어, MY→말레이어, HK/TW→번체, FR/BE→프랑스어, ES/MX/중남미→스페인어 등. 캐나다는 영어로 시작).
3. 기기 언어. 단, 한국어면 영어로 시작합니다. 렌터카 기기는 대부분 한국어이고 대상은 외국인이기 때문입니다.
4. 영어.

**엔진이 그 언어를 지원하지 않을 때**(`VoiceNegotiator`)는 운전자 언어의 공식 로케일(예: 스페인어는 es-US, es-MX)을 차례로 시도한 뒤 영어(en-US)로 내려갑니다. `setLocale` 예외나 `UNAVAILABLE_LANGUAGE` 오류가 나면 다음 후보로 넘어가고, 같은 안내를 새 언어로 다시 만들어 말합니다. 영어로 말하게 되면 화면에 운전자 언어로 "이 차량에서는 ○○ 음성을 지원하지 않아 English로 안내합니다"를 띄웁니다. 영어도 안 되면 음성을 끄고 화면으로만 안내합니다. 한국어로는 내려가지 않습니다.

**차량이 실제로 지원하는 언어 확인** (디버그 빌드): Gleo 비서가 다국어를 지원해도, 앱이 쓰는 STT/TTS SDK가 그 언어를 지원한다는 보장은 없습니다. 국내 사양 렌터카에 일본어 음성 모델이 없을 수도 있습니다. 아래 명령으로 9개 언어의 로케일을 하나씩 시도해 결과를 남깁니다. 말하기는 인사말을 실제로 끝까지 말하면 OK이고, 듣기는 인식이 시작되면 OK입니다. `UNAVAILABLE_LANGUAGE`, `LOCALE_REJECTED`면 미지원입니다.

```powershell
adb shell am broadcast -n com.goodnight.souprules/.debug.DebugSignalReceiver -a com.goodnight.souprules.DEBUG --es event voice_probe
adb logcat -s SoupVoiceProbe SoupVoice
adb shell run-as com.goodnight.souprules cat files/voice-probe.txt
```

**말하는 순서**(`AnnouncementQueue`): 긴급 안내는 팁을 끊고 먼저 말합니다. 같은 안내는 겹쳐 쌓지 않고, 대기열이 넘치면 지난 팁부터 버립니다.

**음성 명령**은 "말하기" 버튼을 눌러 씁니다. 어느 언어로 인식되든 같은 명령이 되도록 모든 언어의 낱말을 함께 봅니다(`CommandParser`).

| 명령 | 예 |
| --- | --- |
| 다시 말하기 | repeat · 다시 · répète · repite · もう一度 · 再说 · ulang · พูดซ้ำ · nhắc lại |
| 오늘 요약 | summary · 요약 · résumé · resumen · まとめ · 总结 · ringkasan · สรุป · tóm tắt |
| 음소거 / 해제 | mute / voice on · 조용히 / 소리 켜 · silence / remets le son · silencio / activa el sonido · ミュート / ミュート解除 · 静音 / 取消静音 · เงียบ / เปิดเสียง |
| 언어 바꾸기 | "Français", "Español", "Bahasa Melayu", "繁體中文", "日本語", "English" … |

## 실행

필요한 것: Android SDK Platform 35, Pleos Maven(`settings.gradle.kts`). JDK 17은 Gradle 툴체인이 자동으로 받습니다(hyundai-autolog와 같은 설정). `local.properties`에 `sdk.dir=...`를 넣으세요.

```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.goodnight.souprules/.MainActivity
```

첫 실행에서 위치·마이크·알림·차속(`android.car.permission.CAR_SPEED`, 런타임 권한) 권한을 허용합니다. 그러면 포그라운드 서비스(위치 유형)가 차량 신호를 받기 시작합니다.

### 렌터카 체크인 연동

```powershell
adb shell am start -n com.goodnight.souprules/.MainActivity -a com.goodnight.souprules.RENTAL_START --es licenseCountry MY
adb shell am start -n com.goodnight.souprules/.MainActivity -a com.goodnight.souprules.RENTAL_START --es lang zh-HK
```

렌트를 시작하면 이전 운전자의 언어 선택, 음소거, 과태료 직전 기록을 지웁니다. 하이패스 단말 여부는 `--es hipass true`(또는 `false`)로 함께 보냅니다.

### 데모 · 디버그 신호

에뮬레이터에는 차속·깜빡이·위치가 없습니다. P 상태에서 **제주 데모 주행 재생**을 누르면 예시 구역을 지나는 4분 30초 주행을 2.5배속으로 재생합니다. 버스전용차로 안내 → 스쿨존 과속 → 우회전 미정지 → 단속 구간 P → 1분 전 재알림 → 오늘 요약(3번, 최대 약 170,000원) 순서입니다. 이 결과는 `DemoScenarioTest`가 고정합니다. 데모는 실제 기록과 섞이지 않습니다.

```powershell
# 디버그 빌드 전용
adb shell am broadcast -n com.goodnight.souprules/.debug.DebugSignalReceiver -a com.goodnight.souprules.DEBUG --es gear D --es speed 42 --es lat 33.4945 --es lng 126.4980
adb shell am broadcast -n com.goodnight.souprules/.debug.DebugSignalReceiver -a com.goodnight.souprules.DEBUG --es turn RIGHT
adb shell am broadcast -n com.goodnight.souprules/.debug.DebugSignalReceiver -a com.goodnight.souprules.DEBUG --es seat 0 --es buckled false
adb shell am broadcast -n com.goodnight.souprules/.debug.DebugSignalReceiver -a com.goodnight.souprules.DEBUG --es steer 6
adb shell am broadcast -n com.goodnight.souprules/.debug.DebugSignalReceiver -a com.goodnight.souprules.DEBUG --es event lang --es value ms-MY
# event: demo · summary · lang(value) · stt(value: 명령 파싱만 확인)
```

## 구조

- `core`(Android 의존성 없음): `CoachEngine` 규칙 상태 기계, `Zones`·`Geo`·`ZoneJson` 구역과 운영 시간(KST), `Fines`, `Strings` 9개 언어 문구, `Lang`·`LanguageResolver`·`CountryLanguage`, `VoiceNegotiator`·`AnnouncementQueue`·`CommandParser`, `DaySummary`.
- `app/data`: `PleosCarSignals`, `LocationFeed`(FusedLocation), `PleosVoice`(STT/TTS 협상), `Prefs`·`NearMissStore`·`ZoneRepository`, `DemoDrive`.
  - `PleosCarSignals`는 AAOS `PERF_VEHICLE_SPEED`·`GEAR_SELECTION`에 Pleos `TurnSignal`과 AAOS `TURN_SIGNAL_STATE`를 함께 씁니다.
- `app/service`: `Coach`는 엔진·음성·기록을 묶습니다. 앱에 하나만 있습니다. `CoachService`는 신호를 넣는 포그라운드 서비스이고, 상태 알림 하나만 씁니다.
- `app/ui`: 지금 안내(심각도 색), 차속·스쿨존 제한 표지, 오늘 요약을 보여 줍니다. 언어 선택·데모·요약 버튼은 P에서만 보입니다.
  - **분할 화면:** 방향을 고정하지 않고 배정된 창 크기에 맞춥니다(`ScreenLayout`, 최소 320×240dp). 창 크기가 바뀌어도 Activity를 다시 만들지 않습니다.

  | 창 | 배치 |
  | --- | --- |
  | 폭 840dp 이상, 높이 480dp 이상 | 2열: 왼쪽 안내·차속, 오른쪽 오늘 |
  | 그보다 좁거나 낮음 | 1열: 안내 → 차속 → 오늘, 스크롤 |
  | 폭 600dp 미만 | 버튼 줄을 제목 아래로 |
  | 폭 480dp 미만 또는 높이 480dp 미만 | 작은 글자·여백 |

엔진은 매 신호에 시각을 함께 받습니다. 그래서 실제 주행, 데모 재생, 단위 테스트가 같은 규칙으로 돕니다. 숫자(속도·위치·시각)는 센서와 시계에서만 오고, 문구는 숫자를 끼워 넣기만 합니다.

## 한계 (솔직하게)

- **구역 데이터는 예시입니다.** `assets/zones/jeju-sample.json`은 데모용 예시입니다(`"sample": true`, 화면에 경고 표시). 실제 단속 위치가 아닙니다. 공공데이터로 바꾸는 방법은 [docs/zone-data.md](docs/zone-data.md)에 있습니다.
- **GPS로 차로를 구분할 수 없습니다.** 버스전용차로 진입 자체는 판단하지 않고 운영 중이라는 안내만 합니다. 기록은 그 도로에서 P에 넣었을 때만 남깁니다.
- **신호 색과 보행자를 모릅니다.** 차량에 카메라 API가 없기 때문입니다.
  - 빨간불 우회전과 우회전 후 횡단보도: "멈추지 않고 돈 우회전"을 *가능성 있는* 과태료 직전 순간으로 기록합니다.
  - 비보호 좌회전: 안내만 하고 기록하지 않습니다.
  - 어린이 통학버스: 버스를 알아볼 수 없어, 첫 스쿨존에서 규칙만 설명합니다.
- **방향지시등 판단은 추정입니다.**
  - 회전은 GPS 진행 방향으로 봅니다. 운전대 각도가 있으면 그것으로 곡선 도로를 거르고, 없으면 30km/h 이하인지로 거릅니다.
  - 차로 변경은 운전대가 S자로 움직였는지로 봅니다.
  - 기준값은 실차 주행 기록으로 맞춰야 합니다.
- **회전교차로 통행방법 위반 금액(3만원)은 확인이 필요합니다.**
- **공휴일**은 운영 시간 계산에 넣지 않았습니다. 평일 시간표가 공휴일에도 적용된 것으로 봅니다.
- **실제 음성 지원 언어는 확인하지 못했습니다.** 위의 `voice_probe`로 실차나 Pleos 에뮬레이터에서 확인해야 합니다. Pleos 온디바이스 STT/TTS가 한국어 외에 어떤 로케일을 지원하는지 아직 모릅니다. 폴백 경로는 구현했지만 실차 확인이 필요합니다. autolog 에뮬레이터에서는 ko-KR만 확인됐습니다.
- **Pleos TurnSignal 실신호와 CAR_SPEED 권한 조합도 실차 확인이 필요합니다.**
- 이 커밋에서 확인한 범위는 다음과 같습니다. 자동차용 에뮬레이터에서 실행해 보지는 않았고, 분할 화면도 크기별 배치 규칙만 단위 테스트로 확인했습니다.
  - JVM 단위 테스트 100개(core 95개, 화면 배치 5개)
  - `assembleDebug`
  - `lintDebug`(오류 0)

SDK 확인 기록은 [docs/sdk-notes.md](docs/sdk-notes.md)에 있습니다.
