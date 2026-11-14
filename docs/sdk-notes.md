# SDK 확인 기록

확인일: 2026-11-14. Pleos Maven(`nexus-playground.pleos.ai`)의 AAR 공개 서명과 `RequiresPermission` 어노테이션을 `javap`로 확인했습니다. 사용 패턴은 hyundai-autolog(`app/src/main/java/com/goodnight/autolog/data/*Source.kt`, `docs/sdk-notes.md`)를 따랐습니다.

## Vehicle 2.3.2

| 쓰는 것 | API | 권한 |
| --- | --- | --- |
| 깜빡이 | `vehicle.getTurnSignal()`: `registerTurnSignalState(TurnSignalStateListener)`, `getCurrentTurnSignalState(onComplete, onFailed)`, `unregisterTurnSignalState`. 값은 `TurnSignalValue` OFF/RIGHT/LEFT이고 리스너 인자는 nullable입니다. | `pleos.car.permission.CAR_LIGHTS` |
| (쓰지 않음) 기어 | `getGear()`는 비권장입니다. autolog와 마찬가지로 AAOS GEAR_SELECTION을 씁니다. | `pleos.car.permission.CAR_DRIVE` |
| 운전대 각도 | `vehicle.getSteeringWheel()`: `registerSteeringWheelAngle(SteeringWheelAngleListener)`, `onSteeringWheelAngleUpdated(Float?)`(도). 부호 규약은 확인하지 못해 방향이 바뀌는 것만 씁니다. | `pleos.car.permission.CAR_DRIVE` |
| 안전띠·착석 | `vehicle.getSeat()`: `registerSeatBeltBuckledState(SeatArea, SeatBeltBuckledStateListener)`, `registerSeatOccupancy(SeatArea, SeatOccupancyListener)`, 인자는 `Boolean?`. `SeatArea(SeatValue.ROW_1_LEFT …)`로 좌석마다 등록합니다. 지원하지 않는 좌석은 등록 실패를 무시합니다. | `pleos.car.permission.CAR_SEATS` |
| (쓰지 않음) 요금소 | `vehicle.getToll()`에는 ETCS 안내 음량 get/set/register만 있고 하이패스 카드 장착 여부는 없습니다. 그래서 렌터카 체크인의 `hipass` 값을 씁니다. | `pleos.car.permission.CAR_TOLL` |

- 차속 API는 Vehicle SDK에 없습니다(`DrivingMode`에는 크루즈 목표 속도만 있음). 차속은 AAOS로 읽습니다.
- 빈 좌석의 안전띠 센서는 '미착용'으로 나오므로, 동승석은 착석이 확인된 좌석만 판단합니다.
- `Vehicle.initialize()` 직후에는 API가 준비되지 않았을 수 있습니다. autolog처럼 1.5초 뒤 등록합니다.
- 차량 제어 API와 CONTROL 권한은 쓰지 않습니다.

## AAOS CarPropertyManager

| 속성 | 단위·값 | 권한 |
| --- | --- | --- |
| `PERF_VEHICLE_SPEED` | m/s(×3.6 → km/h), `SENSOR_RATE_UI` | `android.car.permission.CAR_SPEED`(**런타임 권한**, 화면에서 요청) |
| `GEAR_SELECTION` | NEUTRAL=1, REVERSE=2, PARK=4, DRIVE=8, 수동 16 이상은 D로 봄 | `CAR_POWERTRAIN` |
| `TURN_SIGNAL_STATE` | NONE=0, RIGHT=1, LEFT=2 | `CAR_EXTERIOR_LIGHTS`(시스템 권한일 수 있음. 실패해도 Pleos 경로가 있어 필수로 보지 않음) |

## 음성 SpeechToText / TextToSpeech 3.3.1

- `SpeechToText(context, Mode.ON_DEVICE)`, `TextToSpeech(context, Mode.ON_DEVICE)`. Mode에는 HYBRID·SERVER도 있지만 `registerApp` 키가 필요해 쓰지 않습니다.
- `initialize(OnInitializeListener)`가 성공한 뒤에 `addListener`/`addEventListener`를 붙입니다(초기화 전 등록 시 lateinit 예외, autolog 실측).
- `setLocale(Locale)`: 국가를 포함한 태그를 넘깁니다. `ko`는 `unsupported locale: ko-`로 거부됩니다(autolog 2026-11-12 로그).
- 오류 enum은 STT/TTS 모두 `UNAVAILABLE_LANGUAGE`를 갖습니다. 이 오류나 `setLocale` 예외가 나면 다음 로케일 후보로 넘어갑니다(`VoiceNegotiator`).
- TTS: `speak(text, true)` → `EventListener.onDone`. 끊을 때는 `stop()`.
- STT: `request()` → `ResultListener.onUpdated(text, isFinal)`의 최종 결과만 씁니다. 8초 안에 결과가 없으면 `stop()`합니다. 듣기 전에 TTS를 멈춰 안내 음성이 다시 인식되지 않게 합니다.
- 권한: `pleos.car.permission.STT_SERVICE`, `TTS_SERVICE`, `RECORD_AUDIO`.
- Gleo AI: 앱이 직접 쓰는 Gleo 받아쓰기 API는 없습니다. 이 SDK가 caas SpeechService(Gleo 모델 공유)에 바인딩하는 공개 경로입니다(autolog 확인 내용).
- **미확인:** 온디바이스 엔진이 ko-KR 말고 어떤 로케일을 지원하는지 모릅니다. 실차나 Pleos 에뮬레이터에서 언어별로 `UNAVAILABLE_LANGUAGE` 여부를 확인해야 합니다.

## FusedLocation 1.1.3

`isSupported` → `initialize` → `registerFusedLocationCallback(FusedLocationListener)`로 연속 위치를 받습니다. `LocationData`에는 latitude·longitude·heading·confidence가 있습니다. 좌표는 구역 판단에만 쓰고 저장하지 않습니다. 권한은 `pleos.car.permission.FUSED_LOCATION`과 정밀 위치입니다.
