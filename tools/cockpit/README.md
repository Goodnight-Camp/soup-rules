# 국Rule 가상 콕핏 (Virtual Cockpit)

에뮬레이터에서 국Rule 앱을 "실제로 주행하는 것처럼" 테스트하고 시연하기 위한 개발용 도구입니다.
이 문서는 구현 지시서입니다. 저장소 `Goodnight-Camp/soup-rules` 루트에서 작업하고, 이 파일은 `tools/cockpit/README.md`에 둡니다.

## 1. 왜 만드는가

- Pleos Connect는 주행 중 앱 화면을 막습니다. 이 제한은 풀지 않습니다. 주행 중에는 음성만 나오고, 화면은 P에서만 보입니다.
- Pleos 에뮬레이터의 내비게이션은 위치를 바꿔도 움직이지 않습니다.
- 그래서 PC 브라우저에 "가상 내비 + 차량 계기판"을 띄우고, 앱과 웹소켓으로 연결합니다.
  - 웹/서버가 차량 신호(차속, 기어, 깜빡이, 위치, 시각)를 앱에 넣습니다.
  - 앱이 실제로 판단한 결과(안내 문장, 과태료 직전 기록, 요약)를 웹에 돌려줍니다.
- 웹은 위반을 판정하지 않습니다. 판정과 기록은 전부 앱의 `CoachEngine`이 합니다. 웹은 받은 것만 그립니다.

## 2. 하지 않는 것

- `distractionOptimized` 선언을 추가하지 않습니다.
- release 빌드에 네트워크 코드나 `INTERNET` 권한을 넣지 않습니다. 모든 링크 코드는 `app/src/debug`에만 둡니다.
- `core` 모듈의 규칙 로직과 기존 테스트 기대값을 바꾸지 않습니다.
- 웹에 안내 문구를 복제하지 않습니다. 문장은 앱이 `Strings.render()`로 만든 것을 그대로 받습니다.
- 실제 Pleos 내비게이션 화면처럼 꾸미지 않습니다. 화면에 "시뮬레이션 신호"라고 표기합니다.

## 3. 구조

```
[웹 콕핏] ⇄ ws ⇄ [중계 서버 · PC :8765] ⇄ ws (adb reverse) ⇄ [DebugLink → Coach]
                        │
                시나리오 재생기 + 상태 기계
```

- 시계와 상태는 중계 서버 한 곳에만 둡니다. 브라우저 타이머로 재생하지 않습니다.
- 웹은 여러 탭이 붙어도 되고, 새로고침하면 서버가 현재 상태를 다시 보내 복원합니다.

## 4. 디렉터리

```
tools/cockpit/
  README.md          이 문서
  server.js          중계 서버 + 시나리오 재생기 + 정적 파일 제공 (Node 18+, 의존성은 ws 하나)
  package.json
  web/index.html     웹 콕핏 (한 파일, 빌드 없음)
  scenarios/
    a-jeju-basic.json
    d-jeju-clean.json
    b-proposal-hk.json      (2차)
    c-proposal-fr.json      (2차)
  test/fake-app.js   앱 없이 서버·웹을 검증하는 가짜 앱 클라이언트
app/src/debug/java/com/goodnight/souprules/debug/DebugLink.kt
app/src/debug/java/com/goodnight/souprules/debug/DebugLinkProvider.kt
```

## 5. 현재 코드에서 알아야 할 것

| 항목 | 위치 | 내용 |
|---|---|---|
| 신호 모델 | `core/.../core/Model.kt` | `Signal.Speed(kmh)`, `Shift(gear)`, `Turn(signal)`, `Position(point)`, `Steering(degrees)`, `Seat(...)`, `Tick` |
| 값 | 같은 파일 | `Gear` P/R/N/D/UNKNOWN, `TurnSignal` OFF/LEFT/RIGHT |
| 진입점 | `app/.../service/Coach.kt` | `accept(signal, at)`, `startDemo()`, `ui: StateFlow<CoachUiState>` |
| 데모 분리 | 같은 파일 | `startDemo()`는 별도 `CoachEngine`을 만들고 `apply(step, engine, demo=true)`로 기록을 실제 저장소와 분리. `ui.demo == true`인 동안 `accept()`는 실제 차량 신호를 무시 |
| 디버그 입력 | `app/.../service/CoachService.kt` `debugSignal()` | adb 브로드캐스트를 받아 `coach.accept()` 호출. 현재 시각으로 판단함 |
| Coach 접근 | `SoupApp.coach` | `(application as SoupApp).coach` |
| 요약 시점 | `CoachEngine.checkParked()` | P 진입 후 10초(`summaryAfterParkSec`)가 지나고 충분히 주행했을 때 1회. D로 바꾸면 초기화 |
| 단속 재알림 | 같은 함수 | 유예 시간 1분 전, P 유지 중일 때 |
| 구역 종류 | `core/.../core/Zones.kt` `ZoneKind` | SCHOOL_ZONE, BUS_LANE, PARKING_ENFORCEMENT, RIGHT_TURN, CROSSWALK, ROUNDABOUT, NO_STOPPING, UNPROTECTED_LEFT, TOLL_PLAZA |
| 예시 구역 | `app/src/main/assets/zones/jeju-sample.json` | BUS_LANE, SCHOOL_ZONE, RIGHT_TURN, PARKING_ENFORCEMENT 4개뿐 |
| 기존 데모 | `app/src/main/assets/demo/jeju-drive.json` | `{"start": ISO시각, "frames":[{"t":초,"speed","gear","turn","lat","lng"}]}`. 기대 결과는 `DemoScenarioTest`에 고정(3건, 약 17만 원) |

시나리오 시각이 중요합니다. 버스전용차로와 주정차 단속은 운영 시간으로 판단하므로, 신호마다 시나리오 시각(`start + t`)을 함께 넘겨야 합니다.

## 6. 앱 변경

### 6.1 main: Coach에 시뮬레이션 세션 추가 (`Coach.kt`)

기존 `startDemo()`의 엔진 분리 방식을 외부 입력에도 쓸 수 있게 세 함수를 추가합니다. `startDemo()` 동작과 `DemoScenarioTest`는 그대로 둡니다.

```kotlin
/** 외부(디버그 링크)에서 넣는 시뮬레이션 주행. 실제 기록·실제 차량 신호와 섞지 않습니다. */
fun beginSim(hipass: Boolean = true)   // 새 CoachEngine 생성, demoMisses 비움, ui.demo=true, current/recent/today/activeZones 초기화
fun feedSim(signal: Signal, at: Instant) // sim 엔진에 accept → apply(step, simEngine, demo=true). 세션이 없으면 무시
fun endSim()                            // stopDemoState()와 같은 정리
```

- `beginSim()`을 다시 호출하면 엔진을 새로 만들어 초기화합니다. 이것이 프로토콜의 `reset`입니다.
- 앱 내부 데모(`demoJob`)가 돌고 있으면 `beginSim()`은 그것을 취소한 뒤 시작합니다.

### 6.2 debug: 링크 클라이언트 (`app/src/debug/...`)

- `DebugLink.kt`
  - OkHttp 웹소켓으로 `ws://localhost:8765/?role=app`에 접속합니다. 끊기면 2초 간격으로 재접속합니다.
  - 수신 메시지를 메인 스레드에서 `coach.beginSim()`, `feedSim()`, `endSim()`, `setLanguage()`로 연결합니다.
  - `coach.ui`를 구독해 바뀔 때마다 `state`를 보냅니다. 최소 간격 100ms로 묶습니다.
  - 연결이 끊긴 채 시뮬레이션 세션이 열려 있으면 차속 0, 기어 P를 넣고 `endSim()`을 호출합니다. 앱이 D 상태로 방치되지 않게 하기 위해서입니다.
- `DebugLinkProvider.kt`: 내용 없는 `ContentProvider`. `onCreate()`에서 `DebugLink`를 시작해 프로세스가 뜰 때 자동으로 붙게 합니다.
- `app/src/debug/AndroidManifest.xml`에 추가
  - `<uses-permission android:name="android.permission.INTERNET"/>`
  - `<application android:usesCleartextTraffic="true">`
  - 위 provider 등록 (`android:exported="false"`)
- `app/build.gradle.kts`: `debugImplementation("com.squareup.okhttp3:okhttp:4.12.0")`
- JSON은 이미 쓰고 있는 `org.json`을 사용합니다.

안내가 음성으로 나오려면 `CoachService`가 떠 있어야 합니다. 앱을 P 상태에서 한 번 열어 서비스를 시작한 뒤 주행을 시작합니다.

## 7. 메시지 규격 (JSON, 웹소켓 텍스트)

### 서버 → 앱

```json
{"type":"begin","scenario":"a-jeju-basic","hipass":true}
{"type":"signal","at":"2026-11-15T23:00:03Z","speed":38,"gear":"D","turn":"RIGHT","lat":33.4881,"lng":126.498,"steer":-12}
{"type":"end"}
{"type":"lang","value":"fr"}
```

- `signal`은 있는 필드만 보냅니다. 앱은 필드 순서를 위치 → 기어 → 깜빡이 → 차속 → Tick으로 처리합니다(`DemoDrive.parse`와 같은 순서). 신호마다 `Tick`을 하나 붙입니다.
- `begin`은 초기화를 겸합니다.

### 앱 → 서버 → 웹

```json
{"type":"hello","app":"souprules","version":"0.1.0"}
{"type":"state",
 "sim":true,
 "lang":"fr",
 "vehicle":{"speed":38,"gear":"D","turn":"RIGHT","lat":33.4881,"lng":126.498},
 "alert":{"rule":"RIGHT_TURN_STOP","severity":"WARN","text":"Feu rouge : ...","zoneId":"demo-right-1","at":"..."},
 "zones":["demo-right-1"],
 "today":{"count":1,"maxFine":60000,"items":[{"rule":"...","zone":"...","fine":60000}]},
 "summary":false}
```

- `alert`는 `ui.current`가 없으면 `null`입니다. `text`는 `Strings.render(lang, alert)` 결과입니다.
- `today`는 `ui.today`(`DaySummary`)에서 채웁니다. 필드 이름은 실제 클래스에 맞추되 건수와 최대 과태료는 반드시 포함합니다.
- `summary`는 요약이 나온 시점에 `true`로 한 번 보냅니다.
- `rule` 값은 `RuleId`의 실제 이름을 씁니다. 위 예시의 이름은 자리표시입니다.

### 웹 → 서버

```json
{"type":"select","scenario":"a-jeju-basic"}
{"type":"start"}
{"type":"pause"}   {"type":"resume"}   {"type":"abort"}
{"type":"speedup","value":2.5}
{"type":"lang","value":"fr"}
{"type":"mode","value":"scenario"}        // 또는 "free"
{"type":"manual","gear":"D","speed":30,"turn":"LEFT"}   // 자유 주행 모드에서만
```

### 서버 → 웹

```json
{"type":"session","phase":"IDLE","mode":"scenario","scenario":"a-jeju-basic","appConnected":true,
 "locked":false,"t":0,"duration":272,"speedup":2.5,"scenarios":[{"id":"...","title":"...","expect":{...}}]}
{"type":"frame", ...signal 그대로...}     // 지도 위 차량 이동용
{"type":"state", ...앱이 보낸 것 그대로...}
{"type":"result","pass":true,"expect":{"count":3,"maxFine":170000},"actual":{"count":3,"maxFine":170000}}
{"type":"error","code":"LOCKED","message":"주행 중에는 시나리오를 바꿀 수 없습니다."}
```

웹이 접속하면 서버는 `session`과 마지막 `state`를 바로 보냅니다.

## 8. 상태 기계 (서버)

```
IDLE ──start──▶ PREPARING ──앱이 P 보고──▶ RUNNING ⇄ PAUSED
                                             │
                          마지막 프레임 송신 ▼
                                          ENDING ──앱이 summary 보고 또는 20초 경과──▶ DONE ──ready──▶ IDLE
abort, 앱 연결 끊김: 어느 단계에서든 정리 절차 후 IDLE
```

규칙은 다음과 같습니다.

1. 시나리오, 언어, 모드 변경은 `IDLE`에서만 받습니다. 그 밖의 단계에서 오면 `error LOCKED`로 거부합니다. 잠금은 웹 버튼이 아니라 서버가 강제합니다.
2. `start`는 앱이 연결돼 있고 `IDLE`일 때만 받습니다.
3. 기어 P는 종료 조건이 아닙니다. 주정차 장면처럼 시나리오 중간에 P가 나와도 `RUNNING`을 유지합니다. 종료는 마지막 프레임 송신입니다.
4. `ENDING`에서 앱의 `summary:true`를 기다립니다. 20초(실시간) 안에 안 오면 "요약 미수신"으로 표시하고 `DONE`으로 갑니다.
5. `DONE`에서 시나리오의 `expect`와 앱의 `today`를 비교해 `result`를 보냅니다.

### 정리 절차 (시작 전과 중단 시 항상 실행)

1. `signal {speed:0, turn:"OFF"}`
2. `signal {gear:"P"}`
3. 중단이면 `end`, 시작이면 `begin`(엔진 초기화)
4. 시작이면 시나리오 첫 프레임 좌표를 보내고, 앱 `state`의 기어가 P인지 확인한 뒤 `RUNNING`으로 넘어갑니다. 3초 안에 확인되지 않으면 `error`를 보내고 `IDLE`로 돌아갑니다.

초기화 없이 다른 시나리오를 시작하면 좌표가 순간이동해 "깜빡이 없는 회전" 오탐이 생기고, 이전 기록이 섞입니다. 그래서 주행 중(D)에는 전환을 막고, 전환은 항상 중단 → 정리 → IDLE → 시작 순서로만 일어나게 합니다.

### 예외

| 상황 | 처리 |
|---|---|
| 주행 중 앱 연결 끊김 | 재생 중지, `IDLE`로 복귀, 웹에 "앱 연결 끊김" 표시. 다시 붙어도 이어서 재생하지 않음 |
| 주행 중 서버 종료 | 앱의 `DebugLink`가 끊김을 감지해 차속 0, P, `endSim()` |
| 웹 새로고침 | 서버가 `session`으로 현재 단계, 진행 시각, 잠금을 복원 |
| 시나리오 모드에서 `manual` 수신 | `error LOCKED` |

### 모드

- 시나리오 모드: 파일을 재생합니다. 수동 조작은 막습니다. 결과를 기대값과 비교합니다.
- 자유 주행 모드: 웹에서 기어, 차속, 깜빡이를 직접 조작합니다. 위치는 차속과 진행 방향으로 서버가 1초마다 계산해 보냅니다(직진, 깜빡이 방향으로 교차로에서 90도 회전하는 단순 모델이면 충분). 시각은 선택한 시작 시각에서 실시간으로 흐릅니다. 기대값 비교는 하지 않습니다.

## 9. 시나리오 파일

기존 `jeju-drive.json` 형식에 메타 정보를 더합니다.

```json
{
  "id": "a-jeju-basic",
  "title": "제주 기본 주행",
  "description": "버스전용차로, 스쿨존 과속, 우회전 미정지, 주정차 단속",
  "lang": "fr",
  "hipass": true,
  "start": "2026-11-15T23:00:00Z",
  "expect": {"count": 3, "maxFine": 170000},
  "frames": [{"t":0,"gear":"P","speed":0,"lat":33.488,"lng":126.498}, ...]
}
```

- 모든 시나리오의 첫 프레임은 `gear:"P"`, `speed:0`, 출발 좌표입니다. 서버가 로드할 때 검사하고, 어기면 목록에서 뺍니다.
- 마지막 프레임 뒤에 앱이 요약을 낼 수 있도록, 마지막 P 진입 후 최소 15초(시나리오 시간)를 둡니다.
- `expect.maxFine`은 엔진의 과태료 표로 실제 계산한 값을 적습니다. 기획서 숫자를 그대로 베끼지 않습니다.

| ID | 내용 | 우선순위 |
|---|---|---|
| a-jeju-basic | 기존 `jeju-drive.json` 프레임 그대로. 기대 3건, 약 17만 원 | 1차 |
| d-jeju-clean | A와 같은 경로를 전부 지킴(버스전용차로 미진입, 스쿨존 30 이하, 우회전 완전 정지, 단속 구간 밖 주차). 기대 0건 | 1차 |
| b-proposal-hk | 기획서 사례 1(홍콩 A씨, 繁體中文 화면·영어 음성): 깜빡이 없는 좌회전(기록), 회전교차로 사전 안내, 스쿨존 사전 안내 후 감속(기록 없음), 단속 구간 P 유예 카운트다운·재알림(기록) → 공영주차장 → 숙소 요약. 기대 2건, 70,000원 | 완료 |
| c-proposal-fr | 기획서 사례 2(프랑스 B씨, Français, 하이패스 없음): 요금소 사전 안내, 08시 버스전용차로 사전 안내, 빨간불 우회전 사전 안내 → 미정지(기록) → 우회전 직후 횡단보도 안내 → 호텔 요약. 기대 1건, 60,000원 | 완료 |

A는 `tools/gen_demo.py`가 만든 파일을 변환합니다. D는 같은 스크립트를 참고해 생성 스크립트를 추가합니다. 손으로 좌표를 적지 않습니다.

B, C는 `tools/cockpit/gen_cases.py`가 만듭니다(`python tools/cockpit/gen_cases.py`). 이 스크립트는 지역 원점 기준 미터 경로(직선·원호)를 1초 단위로 주행시켜 좌표를 계산하고, 사례용 예시 구역(`case-*`: 회전교차로, 스쿨존, 단속 구간, 요금소, 버스전용차로, 우회전 교차로, 횡단보도)을 `jeju-sample.json`에 넣습니다. 사례 1은 제주 서쪽(기존 데모 경로에서 약 15km), 사례 2는 서울에 둡니다. `tools/gen_demo.py`는 다시 실행해도 `case-*` 구역을 지우지 않습니다. 시나리오의 `lang`은 `begin` 메시지로 앱에 전달되고, 앱은 시뮬레이션 동안만 그 언어로 바꾼 뒤 `endSim()`에서 되돌립니다. 기대값은 `core`의 `CockpitScenarioTest`가 모든 시나리오 파일을 실제 `CoachEngine`으로 재생해 검증합니다. 웹 지도는 선택한 경로에서 1.5km 안의 구역만 그립니다.

(원래 계획) B, C는 예시 구역이 필요합니다. `jeju-sample.json`에 ROUNDABOUT, TOLL_PLAZA, CROSSWALK 등 예시 구역을 추가해야 하며, 구역을 추가하면 기존 `DemoScenarioTest` 결과가 달라지지 않는지 확인합니다. 달라지면 구역 위치를 A 경로에서 떨어뜨립니다. 또 B는 P가 두 번 나와 요약이 중간에 한 번 더 나옵니다. 엔진은 고치지 않고, `expect`를 최종 값 기준으로 적습니다.

## 10. 웹 콕핏 (`web/index.html`)

한 파일, 빌드 없음, 외부 요청 없이도 동작해야 합니다(시연장 네트워크 대비). 지도는 SVG 도식으로 그리고, 지도 타일은 쓰지 않습니다.

- 상단 바: 시나리오 선택, 언어 선택, 모드 전환, [주행 시작] [일시정지] [주행 중단], 배속. 잠금 상태에서는 선택 항목을 비활성화합니다.
- 배지: 앱 연결 상태, 현재 단계, "시뮬레이션 신호 · 앱 실시간 판단".
- 지도: 경로선, 구역(종류별 표시와 범례), 차량 마커. 구역은 서버가 `jeju-sample.json`을 읽어 내려줍니다.
- 차량 패널: 차속, 기어, 깜빡이. 서버가 보낸 `frame`이 아니라 앱이 돌려준 `state.vehicle` 값을 표시합니다. 중간 정차는 "정차 중 (시나리오 진행 중)"으로 표시합니다.
- 안내 카드: 앱이 보낸 문장, 언어, 심각도, 그 순간의 차속·깜빡이·구역.
- 기록: 과태료 직전 건수, 최대 금액, 항목 목록, 안내 이력(시각 포함).
- 종료 화면: 요약과 기대값 비교 결과(통과/실패).
- 진행 막대: 시나리오 시각과 전체 길이.
- 녹화용이므로 1920×1080에서 스크롤 없이 한 화면에 들어오게 합니다.

## 11. 실행

```bash
cd tools/cockpit && npm install && node server.js   # http://localhost:8765
adb reverse tcp:8765 tcp:8765                        # 안 되면 DebugLink 주소를 10.0.2.2:8765로
.\gradlew.bat :app:installDebug                      # 앱을 P에서 한 번 열어 서비스 시작
```

서버는 저장소의 `app/src/main/assets/zones/jeju-sample.json`을 직접 읽습니다. 복사본을 만들지 않습니다.

## 12. 작업 순서

1. `server.js`: 정적 파일, 웹소켓 역할 구분, 메시지 전달, 시나리오 로드와 검사
2. 상태 기계, 정리 절차, 잠금, 재생기(배속, 일시정지)
3. `test/fake-app.js`: `begin`/`signal`을 받아 그럴듯한 `state`를 돌려주는 가짜 앱. 이것으로 1~2와 웹을 앱 없이 검증
4. `web/index.html`
5. 시나리오 A 변환, D 생성
6. 앱: `Coach.beginSim/feedSim/endSim`, `DebugLink`, `DebugLinkProvider`, 디버그 매니페스트, 의존성
7. `.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug` 통과 확인
8. (2차) 자유 주행 모드, 시나리오 B와 C

## 13. 완료 기준

1. 가짜 앱만으로 시나리오 A가 끝까지 재생되고 종료 화면이 뜬다.
2. 실제 앱에서 [주행 시작]을 누르면 음성 안내가 나오고, 2초 안에 같은 문장이 웹 카드에 뜬다.
3. A의 결과가 3건, 약 17만 원으로 `result.pass == true`다. D는 0건이다.
4. 주행 중 시나리오·언어 변경 요청은 서버가 거부한다.
5. 중간 P(주정차 장면)에서 시나리오가 끝나지 않고, 단속 예고와 재알림이 나온다.
6. 주행 중단 후 다른 시나리오를 시작해도 이전 기록이 섞이지 않고 오탐 기록이 없다.
7. 주행 중 앱을 강제 종료하면 웹이 "앱 연결 끊김"을 표시하고 `IDLE`로 돌아간다. 앱을 다시 켜면 자동으로 붙는다.
8. 주행 중 웹을 새로고침해도 진행 상태가 복원된다.
9. `:app:assembleRelease`의 병합 매니페스트에 `INTERNET` 권한과 `DebugLinkProvider`가 없다.
10. 기존 `:core:test`가 전부 통과한다.
11. 에뮬레이터의 시스템 기어를 D로 바꿔 앱 화면이 막힌 상태에서도 2번이 성립한다(백그라운드 음성 확인, 수동 테스트).

## 14. 확인이 필요한 것

- `adb reverse`가 Pleos Connect 에뮬레이터에서 동작하는지. 안 되면 `10.0.2.2`.
- 디버그 빌드의 평문 웹소켓이 에뮬레이터 정책에 막히지 않는지.
- 화면이 막힌 상태에서 포그라운드 서비스와 TTS가 유지되는지(완료 기준 11).
- `DaySummary`, `RuleId`의 실제 필드와 이름. 7장의 `state` 예시는 이에 맞춰 조정합니다.
