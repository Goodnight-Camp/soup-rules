# 기획서 사례 시나리오 B(홍콩 A씨, 제주 첫날)·C(프랑스 B씨, 인천공항→서울)를 만듭니다.
# 좌표는 손으로 적지 않고, 지역 원점 기준 미터 경로(직선·원호)를 1초 단위로 주행시켜 계산합니다.
# 사례에 필요한 예시 구역(case-*)을 app/src/main/assets/zones/jeju-sample.json에 넣습니다(다시 실행해도 같은 결과).
# 기대값(expect)은 엔진으로 검증합니다: core CockpitScenarioTest가 이 파일들을 실제 CoachEngine으로 재생해 고정합니다.
# 사용: python tools/cockpit/gen_cases.py
import json, math, os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ZONES = os.path.join(ROOT, "app", "src", "main", "assets", "zones", "jeju-sample.json")
OUT = os.path.join(HERE, "scenarios")
M_LAT = 111195.0


class Frame:
    """지역 원점(위경도) 기준 미터 좌표(x=동, y=북) ↔ 위경도."""
    def __init__(self, lat, lng):
        self.lat, self.lng = lat, lng
        self.m_lng = M_LAT * math.cos(math.radians(lat))
    def ll(self, x, y):
        return (round(self.lat + y / M_LAT, 6), round(self.lng + x / self.m_lng, 6))


def line(p, q, step=2.0):
    n = max(1, int(math.dist(p, q) / step))
    return [(p[0] + (q[0] - p[0]) * i / n, p[1] + (q[1] - p[1]) * i / n) for i in range(1, n + 1)]

def arc(c, r, a0, a1, step_deg=6.0):
    n = max(1, int(abs(a1 - a0) / step_deg))
    return [(c[0] + r * math.cos(math.radians(a0 + (a1 - a0) * i / n)),
             c[1] + r * math.sin(math.radians(a0 + (a1 - a0) * i / n))) for i in range(1, n + 1)]


class Path:
    def __init__(self, pts):
        self.pts = pts
        self.cum = [0.0]
        for i in range(1, len(pts)):
            self.cum.append(self.cum[-1] + math.dist(pts[i - 1], pts[i]))
    @property
    def length(self): return self.cum[-1]
    def at(self, s):
        s = max(0.0, min(s, self.length))
        for i in range(1, len(self.cum)):
            if self.cum[i] >= s:
                t = (s - self.cum[i - 1]) / ((self.cum[i] - self.cum[i - 1]) or 1)
                a, b = self.pts[i - 1], self.pts[i]
                return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
        return self.pts[-1]
    def s_near(self, p):
        """경로 위에서 p에 가장 가까운 지점의 누적 거리."""
        i = min(range(len(self.pts)), key=lambda k: math.dist(self.pts[k], p))
        return self.cum[i]


class Drive:
    """1초 단위 주행. 가속 +8, 감속 -9 km/h/s (tools/gen_demo.py와 같은 차량 모델)."""
    def __init__(self, frame, path):
        self.f, self.path = frame, path
        self.t, self.s, self.v = 0, 0.0, 0.0
        lat, lng = frame.ll(*path.at(0))
        self.frames = [{"t": 0, "gear": "P", "speed": 0, "lat": lat, "lng": lng}]

    def gear(self, g, gap=2):
        self.t += gap
        self.frames.append({"t": self.t, "gear": g, "speed": 0})

    def go(self, until_s, speed_at, stop=False, signals=(), accel=8.0, decel=9.0):
        """until_s까지 주행. speed_at(s)=목표 속도. stop이면 until_s에서 멈춥니다. signals=[(s, "RIGHT"/"OFF")]."""
        sig = sorted(signals)
        while True:
            tgt = speed_at(self.s)
            if stop:
                remain = until_s - self.s
                brake = (self.v / 3.6) * (self.v / decel) / 2 + self.v / 3.6 * 0.6
                if remain <= brake + 0.5: tgt = 0
            self.v = min(tgt, self.v + accel) if self.v < tgt else max(tgt, self.v - decel)
            if self.v <= 0.5: self.v = 0.0
            self.s = min(self.path.length, self.s + self.v / 3.6)
            self.t += 1
            lat, lng = self.f.ll(*self.path.at(self.s))
            fr = {"t": self.t, "speed": round(self.v, 1), "lat": lat, "lng": lng}
            while sig and self.s >= sig[0][0]:
                fr["turn"] = sig.pop(0)[1]
            self.frames.append(fr)
            if stop and self.v == 0 and self.s > until_s - 30: return
            if not stop and self.s >= until_s: return
            if self.t > 3000: raise SystemExit("runaway")

    def wait(self, seconds, step=5):
        end = self.t + seconds
        while self.t + step <= end:
            self.t += step
            self.frames.append({"t": self.t})
        if self.t < end:
            self.t = end
            self.frames.append({"t": self.t})


def circle(fr, cid, kind, name, c, r, **extra):
    lat, lng = fr.ll(*c)
    z = {"id": cid, "kind": kind, "name": name, "circle": {"lat": lat, "lng": lng, "radiusM": r}}
    z.update(extra)
    return z

def corridor(fr, cid, kind, name, pts, half, **extra):
    z = {"id": cid, "kind": kind, "name": name, "corridor": {"halfWidthM": half, "points": [list(fr.ll(*p)) for p in pts]}}
    z.update(extra)
    return z


# ─────────────────────────────────────────────────────────────
# 사례 1) 홍콩 A씨 · 제주 렌터카 첫날 (제주 서쪽, 기존 데모 경로에서 약 15km 떨어진 곳)
#   화면 繁體中文 / 음성 English(繁體中文은 Pleos 공식 음성 로케일이 아니어서 자동으로 영어)
# ─────────────────────────────────────────────────────────────
hk = Frame(33.4660, 126.3300)
I1 = (0.0, 140.0)                       # 공항 앞 첫 교차로(좌회전)
RA_C, RA_R, RA_LANE = (-500.0, 150.0), 25.0, 15.0     # 애월 가는 길 회전교차로(바깥 반경 25m, 주행 반경 15m)
SCHOOL_C, SCHOOL_R = (-1100.0, 150.0), 150.0          # 한림 초등학교 앞 어린이보호구역
CCTV_C, CCTV_R = (-1500.0, 150.0), 40.0               # 식당 앞 길가 주정차 단속 구간(유예 5분)
LOT = (-1640.0, 150.0)                                 # 공영주차장(단속 구간 밖)
HOTEL = (-2260.0, 150.0)                               # 숙소

hk_pts = [(0.0, 0.0)] + line((0, 0), I1) \
    + arc((-10.0, 140.0), 10.0, 0, 90) \
    + line((-10.0, 150.0), (RA_C[0] + RA_LANE, 150.0)) \
    + arc(RA_C, RA_LANE, 0, 180) \
    + line((RA_C[0] - RA_LANE, 150.0), HOTEL)
hp = Path(hk_pts)
s_turn = hp.s_near(I1)
s_ra = hp.s_near((RA_C[0] + RA_LANE, 150.0))
s_school_edge = hp.s_near((SCHOOL_C[0] + SCHOOL_R, 150.0))
s_school_out = hp.s_near((SCHOOL_C[0] - SCHOOL_R, 150.0))
s_cctv = hp.s_near((CCTV_C[0] + 5.0, 150.0))
s_lot = hp.s_near(LOT)
s_hotel = hp.length

def hk_speed(s):
    if s < s_turn - 40: return 35
    if s < s_turn + 25: return 15                        # 첫 교차로 좌회전(깜빡이 없음)
    if s < s_ra - 70: return 45
    if s < s_ra + 60: return 20                          # 회전교차로
    if s < s_school_edge - 30: return 50                 # 스쿨존 30m 앞(사전 안내 지점)까지 50km/h로 접근
    if s < s_school_out: return 28                       # 사전 안내 뒤 감속해 30 이하로 통과
    return 35

hd = Drive(hk, hp)
hd.gear("D")
hd.go(s_school_out, hk_speed, decel=12.0)                # 안내를 듣고 급히(12km/h/s) 줄이는 장면
hd.go(s_cctv, lambda s: 30, stop=True)                  # 식당 앞 길가
hd.gear("P", gap=1)
hd.wait(4 * 60 + 15, step=10)                            # 5분 유예 → 4분에 재알림
hd.gear("D")
hd.go(s_lot, lambda s: 20, stop=True)                   # 공영주차장으로 이동
hd.gear("P", gap=1)
hd.wait(20)
hd.gear("D")
hd.go(s_hotel, lambda s: 40, stop=True)                 # 숙소
hd.gear("P", gap=1)
hd.wait(25)

hk_zones = [
    circle(hk, "case-hk-roundabout", "ROUNDABOUT", "예시 애월 회전교차로", RA_C, RA_R),
    circle(hk, "case-hk-school", "SCHOOL_ZONE", "예시 한림초 어린이보호구역", SCHOOL_C, SCHOOL_R, limitKmh=30),
    circle(hk, "case-hk-cctv", "PARKING_ENFORCEMENT", "예시 식당 앞 주정차 단속", CCTV_C, CCTV_R, graceMinutes=5,
           schedule=[{"days": "ALL", "start": "07:00", "end": "22:00"}]),
]
b = {
    "id": "b-proposal-hk",
    "title": "사례 1 · 홍콩 A씨 제주 첫날",
    "description": "좌측통행 20년 운전자: 깜빡이 없는 좌회전(기록), 회전교차로 사전 안내, 스쿨존 사전 안내 후 감속(기록 없음), 식당 앞 단속 구간 P(유예 카운트다운·재알림, 기록) → 공영주차장 → 숙소에서 요약",
    "lang": "zh-Hant",
    "hipass": False,
    "start": "2026-11-16T03:00:00Z",
    "expect": {"count": 2, "totalFineKrw": 70000},
    "_comment": "tools/cockpit/gen_cases.py가 만든 파일입니다. 직접 수정하지 마세요. 기대값은 core CockpitScenarioTest가 실제 엔진으로 검증합니다.",
    "frames": hd.frames,
}

# ─────────────────────────────────────────────────────────────
# 사례 2) 프랑스 B씨 · 인천공항 → 서울 (화면·음성 Français, 하이패스 카드 없음)
# ─────────────────────────────────────────────────────────────
fr = Frame(37.5400, 126.8800)
TOLL_C, TOLL_R = (600.0, 0.0), 40.0                     # 고속도로 요금소
BUS0, BUS1 = (1500.0, 0.0), (2150.0, 0.0)               # 시내 버스전용차로(평일 07~09시)
X_C, X_R = (2300.0, 0.0), 20.0                          # 빨간불 우회전 교차로
TURN_R = 12.0
CW_C, CW_R = (2300.0 + TURN_R, -60.0), 6.0              # 우회전 직후 횡단보도
FR_HOTEL = (2300.0 + TURN_R, -520.0)

fr_pts = [(0.0, 0.0)] + line((0, 0), (X_C[0] - TURN_R, 0.0)) \
    + arc((X_C[0] - TURN_R, -TURN_R), TURN_R, 90, 0) \
    + line((X_C[0], -TURN_R), FR_HOTEL)
fp = Path(fr_pts)
s_toll = fp.s_near(TOLL_C)
s_bus0, s_bus1 = fp.s_near(BUS0), fp.s_near(BUS1)
s_x = fp.s_near((X_C[0] - TURN_R, 0.0))
s_after = fp.s_near((X_C[0], -35.0))

def fr_speed(s):
    if s < s_toll - 150: return 60
    if s < s_toll + 40: return 25                        # 요금소: 일반(TC) 차로로 서행
    if s < s_bus0 - 50: return 60
    if s < s_x - 70: return 40                           # 버스전용차로 옆 원래 차로 유지
    if s < s_x + 25: return 12                           # 앞차를 따라 속도만 줄이고 우회전(완전 정지 없음)
    return 30

fd = Drive(fr, fp)
fd.gear("D")
fd.go(fp.length, fr_speed, stop=True,
      signals=[(s_x - 45, "RIGHT"), (s_after, "OFF")])
fd.gear("P", gap=1)
fd.wait(25)

fr_zones = [
    circle(fr, "case-fr-toll", "TOLL_PLAZA", "예시 고속도로 요금소", TOLL_C, TOLL_R, limitKmh=30),
    corridor(fr, "case-fr-bus", "BUS_LANE", "예시 시내 버스전용차로", [BUS0, BUS1], 12.0,
             schedule=[{"days": "MON-FRI", "start": "07:00", "end": "09:00"}, {"days": "MON-FRI", "start": "17:00", "end": "19:00"}]),
    circle(fr, "case-fr-right", "RIGHT_TURN", "예시 빨간불 우회전 교차로", X_C, X_R),
    circle(fr, "case-fr-crosswalk", "CROSSWALK", "예시 우회전 후 횡단보도", CW_C, CW_R),
]
c = {
    "id": "c-proposal-fr",
    "title": "사례 2 · 프랑스 B씨 인천공항→서울",
    "description": "하이패스 카드 없음: 요금소 사전 안내(일반 차로), 08시 버스전용차로 사전 안내(차로 유지), 빨간불 우회전 사전 안내 → 미정지 통과(기록) → 우회전 직후 횡단보도 안내 → 호텔에서 요약",
    "lang": "fr",
    "hipass": False,
    "start": "2026-11-15T23:00:00Z",
    "expect": {"count": 1, "totalFineKrw": 60000},
    "_comment": "tools/cockpit/gen_cases.py가 만든 파일입니다. 직접 수정하지 마세요. 기대값은 core CockpitScenarioTest가 실제 엔진으로 검증합니다.",
    "frames": fd.frames,
}

# ── 구역 파일 갱신: case-* 만 교체 ──
zones = json.load(open(ZONES, encoding="utf-8"))
zones["zones"] = [z for z in zones["zones"] if not z["id"].startswith("case-")] + hk_zones + fr_zones
json.dump(zones, open(ZONES, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

os.makedirs(OUT, exist_ok=True)
for sc in (b, c):
    json.dump(sc, open(os.path.join(OUT, sc["id"] + ".json"), "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print(sc["id"], "frames", len(sc["frames"]), "duration", sc["frames"][-1]["t"])
