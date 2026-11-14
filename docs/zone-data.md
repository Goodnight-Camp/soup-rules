# 구역 데이터

앱은 `app/src/main/assets/zones/*.json` 하나를 읽습니다(`ZoneRepository.ASSET`). 지금 들어 있는 `jeju-sample.json`은 **데모용 예시**이며 실제 단속 위치가 아닙니다. 예시 구역은 `tools/gen_demo.py`가 데모 주행과 함께 만듭니다.

## 형식

```json
{
 "version": 1,
 "sample": false,
 "zones": [
  {"id": "school-jeju-0001", "kind": "SCHOOL_ZONE", "name": "OO초등학교", "limitKmh": 30,
   "circle": {"lat": 33.50, "lng": 126.53, "radiusM": 300},
   "schedule": [{"days": "MON-FRI", "start": "08:00", "end": "20:00"}]},
  {"id": "bus-jeju-0001", "kind": "BUS_LANE", "name": "OO로 가로변",
   "corridor": {"halfWidthM": 12, "points": [[33.50, 126.50], [33.51, 126.51]]},
   "schedule": [{"days": "MON-FRI", "start": "07:00", "end": "09:00"}, {"days": "MON-FRI", "start": "17:00", "end": "19:00"}]},
  {"id": "cctv-jeju-0001", "kind": "PARKING_ENFORCEMENT", "name": "OO사거리", "graceMinutes": 5,
   "circle": {"lat": 33.49, "lng": 126.49, "radiusM": 50}},
  {"id": "turn-jeju-0001", "kind": "RIGHT_TURN", "name": "OO교차로", "rightTurnSignal": true,
   "circle": {"lat": 33.48, "lng": 126.48, "radiusM": 20}}
 ]
}
```

| 필드 | 뜻 |
| --- | --- |
| `kind` | `SCHOOL_ZONE` · `BUS_LANE` · `PARKING_ENFORCEMENT` · `RIGHT_TURN` |
| `circle` / `corridor` | 원(중심·반경 m) 또는 도로 중심선 + 반폭(m). 둘 중 하나 |
| `schedule` | 없으면 상시. `days`: `ALL`, `MON-FRI`, `SAT,SUN`, `FRI-MON`. `end`가 `start`보다 이르면 자정을 넘깁니다. 같으면 그 요일 종일입니다. 시각은 KST입니다 |
| `limitKmh` | 어린이보호구역 제한속도. 없으면 30 |
| `graceMinutes` | 주정차 단속 유예(분). 0이면 즉시 단속. 없으면 보수적으로 1분 |
| `rightTurnSignal` | 우회전 신호등(적색 화살표)이 있는 교차로 |
| `sample` | 예시 데이터 표시. 파일 단위로 줄 수도 있고, 하나라도 있으면 화면에 경고를 띄웁니다 |

`id`는 파일 안에서 겹치면 안 됩니다(읽을 때 거부). 제한속도는 10~120, 유예는 0~60분만 받습니다.

## 실제 데이터로 바꾸기 (할 일)

공공데이터포털의 표준데이터가 후보입니다. 열 이름은 내려받은 파일로 확인한 뒤 변환기를 작성하세요.

- 전국어린이보호구역표준데이터: 대상시설 위도·경도 → `SCHOOL_ZONE` 원(반경은 보통 시설 주변 300m). 시간제 속도 제한 구역은 `schedule`로 넣습니다.
- 전국주정차단속위치표준데이터 또는 지자체 CCTV 목록: 위치·단속 시간·유예 → `PARKING_ENFORCEMENT`.
- 버스전용차로: 지자체(제주도 등) 구간·운영 시간 자료 → `BUS_LANE` 구간.
- 우회전 신호등 설치 교차로: 경찰청·지자체 목록 → `RIGHT_TURN`(`rightTurnSignal: true`). 횡단보도가 있는 일반 우회전 지점은 도로 데이터에서 뽑아야 합니다.

실제 데이터에는 `"sample": false`를 주고, 출처와 기준일을 파일의 `_source` 같은 필드에 남기세요. 로더는 모르는 필드를 무시합니다.
