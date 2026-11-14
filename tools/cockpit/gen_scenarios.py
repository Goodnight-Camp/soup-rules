# 콕핏 시나리오 A·D를 만듭니다. 좌표는 손으로 적지 않고 전부 계산합니다.
# A: app/src/main/assets/demo/jeju-drive.json(tools/gen_demo.py 산출물)을 메타만 더해 변환.
# D: gen_demo.py와 같은 도로 모델로 전부 지키는 주행을 생성(버스전용차로 옆 일반 차로,
#    스쿨존 28km/h, 우회전 지점 완전 정지, 단속 구간 60m 앞 주차).
# 사용: python3 tools/cockpit/gen_scenarios.py
import json, math, os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
OUT = os.path.join(HERE, "scenarios")

demo = json.load(open(os.path.join(ASSETS, "demo", "jeju-drive.json")))
zones = json.load(open(os.path.join(ASSETS, "zones", "jeju-sample.json")))

# ── 도로 모델 (tools/gen_demo.py와 동일) ──
A = (33.4880, 126.4980); B = (33.4985, 126.4980)
M_LAT = 111195.0
M_LNG = 111195.0 * math.cos(math.radians(33.4985))
north = (B[0] - A[0]) * M_LAT

def zone(zid):
    return next(z for z in zones["zones"] if z["id"] == zid)

bus = zone("demo-bus-1"); school = zone("demo-school-1")
right = zone("demo-right-1"); cctv = zone("demo-cctv-1")
school_s = (school["circle"]["lat"] - A[0]) * M_LAT
school_r = school["circle"]["radiusM"]
right_r = right["circle"]["radiusM"]
park_c = north + (cctv["circle"]["lng"] - B[1]) * M_LNG
cctv_r = cctv["circle"]["radiusM"]
bus_pts = [(p[0], p[1]) for p in bus["corridor"]["points"]]
bus_half = bus["corridor"]["halfWidthM"]

def dist_m(p, q):
    return math.hypot((p[0]-q[0])*M_LAT, (p[1]-q[1])*M_LNG)

def dist_to_segment(p, a, b):
    ax, ay = (a[0]-p[0])*M_LAT, (a[1]-p[1])*M_LNG
    bx, by = (b[0]-p[0])*M_LAT, (b[1]-p[1])*M_LNG
    dx, dy = bx-ax, by-ay
    t = 0.0 if dx == dy == 0 else max(0.0, min(1.0, -(ax*dx+ay*dy)/(dx*dx+dy*dy)))
    return math.hypot(ax+t*dx, ay+t*dy)

# ── A: 기존 데모 프레임 그대로 + 메타 ──
# expect는 엔진 과태료 표로 계산한 값으로, core의 DemoScenarioTest가 고정합니다:
# 스쿨존 과속(10km/h 초과) 70,000 + 우회전 미정지 60,000 + 주정차 40,000 = 170,000원.
a = {
    "id": "a-jeju-basic",
    "title": "제주 기본 주행",
    "description": "버스전용차로, 스쿨존 과속, 우회전 미정지, 주정차 단속",
    "hipass": True,
    "start": demo["start"],
    "expect": {"count": 3, "totalFineKrw": 170000},
    "frames": demo["frames"],
}

# ── D: 같은 경로를 전부 지키는 주행 ──
OFF = 25.0        # 버스전용차로(반폭 12m) 옆 일반 차로 가로 오프셋
MERGE0, MERGE1 = 465.0, 565.0   # 전용차로 구간이 끝난 뒤 중심선으로 복귀
stop_s = north - 12.0           # 우회전 지점(반경 20m) 안에서 완전 정지
park_s = park_c - 60.0          # 단속 원(반경 40m + 허용 5m) 바깥 주차

def offset(s):
    if s < MERGE0: return OFF
    if s < MERGE1: return OFF * (MERGE1 - s) / (MERGE1 - MERGE0)
    return 0.0

def pos(s):
    if s <= north: return (A[0] + s/M_LAT, A[1] + offset(s)/M_LNG)
    return (B[0], B[1] + (s-north)/M_LNG)

def target(s, turned):
    if not turned:
        if s < school_s - school_r - 80: return 32
        if s < school_s + school_r + 20: return 28   # 제한 30 아래
        if s < stop_s - 70: return 32
        if s < stop_s - 30: return 15
        if s < stop_s - 3: return 6
        return 0
    if s < park_s - 45: return 30
    if s < park_s - 15: return 15
    return 0

frames = [{"t": 0, "gear": "P", "speed": 0, "lat": round(pos(0)[0], 6), "lng": round(pos(0)[1], 6)}]
t = 2; s = 0.0; v = 0.0
frames.append({"t": t, "gear": "D", "speed": 0})
turned = False; hold = 0; turn_state = None
while True:
    tgt = target(s, turned)
    v = min(tgt, v + 8) if v < tgt else max(tgt, v - 9)
    if v <= 0.5: v = 0.0
    s += v/3.6; t += 1
    lat, lng = pos(s)
    f = {"t": t, "speed": round(v, 1), "lat": round(lat, 6), "lng": round(lng, 6)}
    if turn_state is None and not turned and s >= stop_s - 45:
        f["turn"] = "RIGHT"; turn_state = "on"
    if turn_state == "on" and s >= north + 35:
        f["turn"] = "OFF"; turn_state = "off"
    frames.append(f)
    if v == 0 and not turned:
        hold += 1
        if hold >= 3: turned = True            # 3초 완전 정지 후 우회전 진행
    if v == 0 and turned and s > park_s - 45: break
    if t > 700: raise SystemExit("runaway")
t += 1; frames.append({"t": t, "gear": "P", "speed": 0})
for k in range(5, 45, 5): frames.append({"t": t + k})

# ── 검증: 손으로 좌표를 적지 않는 대신 기하 조건을 기계로 확인합니다 ──
pts = [(f["lat"], f["lng"]) for f in frames if "lat" in f]
# 1. 버스전용차로 미진입(경계+2m 여유)
for p in pts:
    d = min(dist_to_segment(p, bus_pts[i], bus_pts[i+1]) for i in range(len(bus_pts)-1))
    assert d > bus_half + 2, f"버스전용차로 침범 d={d:.1f} at {p}"
# 2. 스쿨존 안 속도 ≤ 제한
limit = school["limitKmh"]
for f in frames:
    if "lat" in f and dist_m((f["lat"], f["lng"]), (school["circle"]["lat"], school["circle"]["lng"])) <= school_r:
        assert f.get("speed", 0) <= limit, f"스쿨존 과속 {f}"
# 3. 우회전 원 안에서 완전 정지(speed 0)했는지
rc = (right["circle"]["lat"], right["circle"]["lng"])
stopped_in = [f for f in frames if "lat" in f and f.get("speed", 1) == 0
              and dist_m((f["lat"], f["lng"]), rc) <= right_r - 2]
assert len(stopped_in) >= 3, "우회전 지점 완전 정지 실패"
# 4. 주차 지점이 단속 원(반경+허용 5m) 바깥
last = [f for f in frames if "lat" in f][-1]
pd = dist_m((last["lat"], last["lng"]), (cctv["circle"]["lat"], cctv["circle"]["lng"]))
assert pd > cctv_r + 5 + 5, f"단속 구역에 주차 d={pd:.1f}"
# 5. 깜빡이 없는 12초 창 방향 변화 < 55도(엔진 TURN_NO_SIGNAL 기준 60도)
def bearing(p, q):
    y = (q[1]-p[1])*M_LNG; x = (q[0]-p[0])*M_LAT
    return math.degrees(math.atan2(y, x))
sig_on = set()
on = False
for f in frames:
    if f.get("turn") == "RIGHT": on = True
    if f.get("turn") == "OFF": on = False
    if on: sig_on.add(f["t"])
samples = []   # (t, bearing) 4m 이상 이동했을 때만(엔진과 동일)
prev = None
for f in frames:
    if "lat" not in f: continue
    p = (f["lat"], f["lng"])
    if prev is None or dist_m(prev[1], p) < 4.0:
        if prev is None: prev = (f["t"], p)
        continue
    samples.append((f["t"], bearing(prev[1], p)))
    prev = (f["t"], p)
for i, (t0, b0) in enumerate(samples):
    for t1, b1 in samples[i+1:]:
        if t1 - t0 > 12: break
        diff = abs((b1 - b0 + 540) % 360 - 180)
        covered = any(x in sig_on for x in range(t0 - 5, t1 + 1))
        assert diff < 55 or covered, f"깜빡이 없는 방향 변화 {diff:.0f}도 t={t0}..{t1}"

d = {
    "id": "d-jeju-clean",
    "title": "제주 준법 주행",
    "description": "같은 경로를 전부 지킴: 버스전용차로 미진입, 스쿨존 30 이하, 우회전 완전 정지, 단속 구간 밖 주차",
    "hipass": True,
    "start": demo["start"],
    "expect": {"count": 0, "totalFineKrw": 0},
    "_comment": "tools/cockpit/gen_scenarios.py가 만든 파일입니다. 직접 수정하지 마세요.",
    "frames": frames,
}

os.makedirs(OUT, exist_ok=True)
json.dump(a, open(os.path.join(OUT, "a-jeju-basic.json"), "w"), ensure_ascii=False, separators=(",", ":"))
json.dump(d, open(os.path.join(OUT, "d-jeju-clean.json"), "w"), ensure_ascii=False, separators=(",", ":"))
print("a-jeju-basic frames", len(a["frames"]), "duration", a["frames"][-1]["t"])
print("d-jeju-clean frames", len(frames), "duration", frames[-1]["t"],
      "stop_in_circle", len(stopped_in), "park_dist", round(pd, 1))
