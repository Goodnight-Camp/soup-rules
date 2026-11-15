// 국Rule 가상 콕핏 중계 서버 + 시나리오 재생기 + 정적 파일 제공. Node 18+, 의존성은 ws 하나.
// 시계와 상태는 여기 한 곳에만 둡니다. 웹은 받은 것만 그리고, 판정은 전부 앱이 합니다.
"use strict";
const http = require("http");
const fs = require("fs");
const path = require("path");
const { WebSocketServer, WebSocket } = require("ws");

const PORT = Number(process.env.PORT || 8765);
const ROOT = path.resolve(__dirname, "..", "..");
const ZONES_FILE = path.join(ROOT, "app", "src", "main", "assets", "zones", "jeju-sample.json");
const SCENARIO_DIR = path.join(__dirname, "scenarios");
const WEB_DIR = path.join(__dirname, "web");

// ── 시나리오 로드와 검사 ──
// 규칙: 첫 프레임은 gear P + speed 0 + 출발 좌표, 마지막 P 진입 후 최소 15초. 어기면 목록에서 뺍니다.
function loadScenarios() {
  const out = new Map();
  for (const file of fs.readdirSync(SCENARIO_DIR).filter(f => f.endsWith(".json")).sort()) {
    let s;
    try { s = JSON.parse(fs.readFileSync(path.join(SCENARIO_DIR, file), "utf8")); }
    catch (e) { console.error(`[시나리오] ${file} 파싱 실패: ${e.message}`); continue; }
    const f = s.frames && s.frames[0];
    const bad =
      !s.id || !s.start || !Array.isArray(s.frames) || s.frames.length < 2 ? "필수 필드 없음"
      : f.gear !== "P" || f.speed !== 0 || f.lat == null || f.lng == null ? "첫 프레임이 P/0/좌표가 아님"
      : null;
    if (!bad) {
      const lastP = [...s.frames].reverse().find(fr => fr.gear === "P");
      const tail = s.frames[s.frames.length - 1].t - (lastP ? lastP.t : Infinity);
      if (!(tail >= 15)) { exclude(file, `마지막 P 뒤 여유가 ${tail}초 (<15초)`); continue; }
      out.set(s.id, s);
    } else exclude(file, bad);
  }
  function exclude(file, why) { console.error(`[시나리오] ${file} 제외: ${why}`); }
  return out;
}
const scenarios = loadScenarios();
if (scenarios.size === 0) console.error("[시나리오] 재생할 시나리오가 없습니다");
const zonesJson = JSON.parse(fs.readFileSync(ZONES_FILE, "utf8"));

// ── 정적 파일 ──
const MIME = { ".html": "text/html; charset=utf-8", ".js": "text/javascript", ".css": "text/css", ".json": "application/json" };
const httpServer = http.createServer((req, res) => {
  const url = (req.url || "/").split("?")[0];
  const file = path.normalize(path.join(WEB_DIR, url === "/" ? "index.html" : url));
  if (!file.startsWith(WEB_DIR) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
    res.writeHead(404); res.end("not found"); return;
  }
  // 개발용 도구라 캐시를 끕니다. 수정한 화면이 새로고침만으로 반영되게 합니다.
  res.writeHead(200, {
    "content-type": MIME[path.extname(file)] || "application/octet-stream",
    "cache-control": "no-store",
  });
  res.end(fs.readFileSync(file));
});

// ── 접속 관리 ──
const wss = new WebSocketServer({ server: httpServer });
let appWs = null;            // 앱(또는 fake-app)은 한 대만
const webs = new Set();

// ── 상태 기계 ──
// IDLE → PREPARING → RUNNING ⇄ PAUSED → ENDING → DONE → (ready) → IDLE
const S = {
  phase: "IDLE",
  mode: "scenario",
  scenarioId: scenarios.keys().next().value || null,
  speedup: 2.5,
  frameIdx: 0,               // 다음에 보낼 프레임
  simT: 0,                   // 보낸 마지막 프레임의 t(초)
  lastState: null,           // 앱이 보낸 마지막 state
  lastToday: null,
  summaryAtFrame: -1,        // summary:true를 받았을 때의 frameIdx
  notice: null,              // 웹에 보여 줄 한 줄(앱 연결 끊김 등)
  result: null,
  timer: null,               // 다음 프레임 타이머
  endingTimer: null,
  prepareTimer: null,
  sdkTimer: null,            // SDK 주행 모드: 1초마다 위치 적분
  sdkS: 0,                   // SDK 주행: 경로 위 진행 거리(m)
};

// ── SDK 주행 모드용 경로 기하: 시나리오 경로를 미터 단위로 펴서 s→좌표를 계산 ──
const M_LAT = 111195;
function routeGeom(sc) {
  const pts = sc.frames.filter(f => f.lat != null).map(f => [f.lat, f.lng]);
  const mLng = M_LAT * Math.cos(pts[0][0] * Math.PI / 180);
  const cum = [0];
  for (let i = 1; i < pts.length; i++)
    cum.push(cum[i - 1] + Math.hypot((pts[i][0] - pts[i - 1][0]) * M_LAT, (pts[i][1] - pts[i - 1][1]) * mLng));
  return {
    length: cum[cum.length - 1],
    pointAt(s) {
      s = Math.max(0, Math.min(s, this.length));
      let i = cum.findIndex(c => c >= s);
      if (i <= 0) return pts[0];
      const t = (s - cum[i - 1]) / (cum[i] - cum[i - 1] || 1);
      return [pts[i - 1][0] + (pts[i][0] - pts[i - 1][0]) * t, pts[i - 1][1] + (pts[i][1] - pts[i - 1][1]) * t];
    },
  };
}

const scenario = () => scenarios.get(S.scenarioId);

function sessionMsg() {
  const sc = scenario();
  return {
    type: "session",
    phase: S.phase,
    mode: S.mode,
    scenario: S.scenarioId,
    appConnected: !!appWs,
    locked: S.phase !== "IDLE",
    t: S.simT,
    duration: sc ? sc.frames[sc.frames.length - 1].t : 0,
    speedup: S.speedup,
    notice: S.notice,
    scenarios: [...scenarios.values()].map(s => ({
      id: s.id, title: s.title, description: s.description,
      expect: s.expect, duration: s.frames[s.frames.length - 1].t, lang: s.lang || null,
      route: s.frames.filter(f => f.lat != null).map(f => [f.lat, f.lng]),
    })),
    zones: zonesJson,
  };
}

const send = (ws, msg) => { if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(msg)); };
const toApp = msg => send(appWs, msg);
const toWebs = msg => { for (const w of webs) send(w, msg); };
const broadcastSession = () => toWebs(sessionMsg());
const webError = (ws, code, message) => send(ws, { type: "error", code, message });

function clearTimers() {
  for (const k of ["timer", "endingTimer", "prepareTimer"]) {
    if (S[k]) { clearTimeout(S[k]); S[k] = null; }
  }
  if (S.sdkTimer) { clearInterval(S.sdkTimer); S.sdkTimer = null; }
}

// 프레임 → 앱에 보낼 signal (있는 필드만)
function signalOf(frame, start) {
  const m = { type: "signal", at: new Date(Date.parse(start) + frame.t * 1000).toISOString() };
  for (const k of ["speed", "gear", "turn", "lat", "lng", "steer"]) if (frame[k] != null) m[k] = frame[k];
  return m;
}

// ── 정리 절차: 시작 전과 중단 시 항상 실행 ──
function cleanupSignals(thenEnd) {
  const at = new Date().toISOString();
  toApp({ type: "signal", at, speed: 0, turn: "OFF" });
  toApp({ type: "signal", at, gear: "P" });
  if (thenEnd) toApp({ type: "end" });
}

function toIdle(notice) {
  clearTimers();
  S.phase = "IDLE";
  S.frameIdx = 0; S.simT = 0;
  S.notice = notice || null;
  broadcastSession();
}

function abort(notice) {
  if (S.phase === "IDLE") return;
  cleanupSignals(true);
  toIdle(notice || null);
}

// ── 시작: 정리 → begin → 첫 프레임 → 앱이 P 보고하면 RUNNING ──
function start(ws) {
  const sc = scenario();
  if (S.phase !== "IDLE") return webError(ws, "LOCKED", "이미 진행 중입니다.");
  if (!appWs) return webError(ws, "NO_APP", "앱이 연결되지 않았습니다.");
  if (!sc) return webError(ws, "NO_SCENARIO", "시나리오가 없습니다.");
  S.phase = "PREPARING";
  S.notice = null; S.result = null;
  S.frameIdx = 0; S.simT = 0; S.sdkS = 0; S.lastToday = null; S.summaryAtFrame = -1;
  cleanupSignals(false);
  // SDK 주행 모드: 속도·기어·깜빡이는 앱이 에뮬레이터 SDK로 직접 읽고(실신호),
  // 서버는 보고된 속도만큼 경로 위 위치를 계산해 되돌려줍니다. 시각은 시나리오 시작 시각에서 실시간으로 흐릅니다.
  // lang: 시나리오 운전자의 언어. 앱은 시뮬레이션 동안만 그 언어로 바꾸고 끝나면 되돌립니다.
  // zones: 저장소의 구역 파일 그대로. 앱은 이 시뮬레이션 동안 자기 APK에 든 구역 대신 이것으로 판단합니다.
  // (앱을 다시 빌드하지 않아도 새 시나리오 구역에서 안내가 나오게)
  toApp({ type: "begin", scenario: sc.id, hipass: sc.hipass !== false, mode: S.mode, virtualStart: sc.start,
    ...(sc.lang ? { lang: sc.lang } : {}), zones: zonesJson });
  const warn = appZoneWarning(sc);
  if (warn) { S.notice = warn; console.warn(`[앱] ${warn}`); }
  const first = signalOf(sc.frames[0], sc.start);
  toApp(first);
  toWebs({ type: "frame", t: sc.frames[0].t, ...stripType(first) });
  S.frameIdx = 1;
  broadcastSession();
  // 3초 안에 앱 state의 기어가 P로 확인되지 않으면 IDLE로 돌아갑니다.
  S.prepareTimer = setTimeout(() => {
    toWebs({ type: "error", code: "PREPARE_TIMEOUT", message: "앱이 3초 안에 P 상태를 보고하지 않았습니다." });
    cleanupSignals(true);
    toIdle("준비 실패: 앱이 P 상태를 보고하지 않음");
  }, 3000);
}

const stripType = m => { const { type, ...rest } = m; return rest; };

function beginRunning() {
  clearTimeout(S.prepareTimer); S.prepareTimer = null;
  S.phase = "RUNNING";
  broadcastSession();
  const sc = scenario();
  if (S.mode === "sdk") startSdkDrive(sc);
  else scheduleNext(Math.max(0, (sc.frames[1].t - sc.frames[0].t) * 1000 / S.speedup));
}

// ── SDK 주행: 1초마다 앱이 보고한 실제 차속으로 경로 위 위치를 적분(실시간, 배속 없음) ──
function startSdkDrive(sc) {
  const geom = routeGeom(sc);
  const t0 = Date.parse(sc.start);
  S.sdkTimer = setInterval(() => {
    if (S.phase !== "RUNNING") return;
    const v = S.lastState && S.lastState.vehicle && S.lastState.vehicle.gear === "D"
      ? (S.lastState.vehicle.speed || 0) : 0;
    S.sdkS = Math.min(S.sdkS + v / 3.6, geom.length);
    S.simT += 1;
    const [lat, lng] = geom.pointAt(S.sdkS);
    const at = new Date(t0 + S.simT * 1000).toISOString();
    toApp({ type: "signal", at, lat: +lat.toFixed(6), lng: +lng.toFixed(6) });
    toWebs({ type: "frame", t: S.simT, at, lat: +lat.toFixed(6), lng: +lng.toFixed(6) });
    if (S.sdkS >= geom.length && !S.notice) {
      S.notice = "경로 끝에 도착했습니다. P로 주차하면 요약이 나옵니다.";
      broadcastSession();
    }
  }, 1000);
}

// ── 재생기: 서버 시계 하나로 배속·일시정지 처리 ──
function scheduleNext(delayMs) {
  clearTimeout(S.timer);
  S.timer = setTimeout(sendFrame, delayMs);
}

function sendFrame() {
  const sc = scenario();
  if (!sc || S.phase !== "RUNNING") return;
  const frame = sc.frames[S.frameIdx];
  const msg = signalOf(frame, sc.start);
  toApp(msg);
  toWebs({ type: "frame", t: frame.t, ...stripType(msg) });
  S.simT = frame.t;
  S.frameIdx++;
  if (S.frameIdx >= sc.frames.length) return toEnding();
  const dt = (sc.frames[S.frameIdx].t - frame.t) * 1000 / S.speedup;
  scheduleNext(Math.max(0, dt));
}

// ── ENDING: 마지막 P 이후의 summary를 기다립니다(이미 왔으면 바로 DONE) ──
function lastParkFrameIdx(sc) {
  for (let i = sc.frames.length - 1; i >= 0; i--) if (sc.frames[i].gear === "P") return i;
  return -1;
}

function toEnding() {
  clearTimers();
  S.phase = "ENDING";
  broadcastSession();
  const sc = scenario();
  if (S.summaryAtFrame >= lastParkFrameIdx(sc)) return toDone(true);
  S.endingTimer = setTimeout(() => toDone(false), 20000);   // 실시간 20초
}

function toDone(summaryReceived) {
  clearTimers();
  S.phase = "DONE";
  const sc = scenario();
  // SDK 주행은 사람이 모는 자유 주행이므로 기대값 비교를 하지 않습니다.
  const expect = S.mode === "sdk" ? null : (sc.expect || null);
  const today = S.lastToday || { count: 0, totalFineKrw: 0 };
  const actual = { count: today.count ?? 0, totalFineKrw: today.totalFineKrw ?? 0 };
  const pass = expect
    ? summaryReceived && actual.count === expect.count && actual.totalFineKrw === expect.totalFineKrw
    : null;
  S.result = { type: "result", pass, expect, actual, summaryReceived };
  S.notice = summaryReceived ? null : "요약 미수신";
  toApp({ type: "end" });
  broadcastSession();
  toWebs(S.result);
}

// ── 메시지 처리 ──
function onWebMessage(ws, msg) {
  const idleOnly = () => {
    if (S.phase === "IDLE") return true;
    webError(ws, "LOCKED", "주행 중에는 시나리오를 바꿀 수 없습니다.");
    return false;
  };
  switch (msg.type) {
    case "select":
      if (!idleOnly()) return;
      if (!scenarios.has(msg.scenario)) return webError(ws, "UNKNOWN_SCENARIO", `모르는 시나리오: ${msg.scenario}`);
      S.scenarioId = msg.scenario; S.notice = null; S.result = null;
      broadcastSession(); break;
    case "lang":
      // 언어는 앱의 설정입니다. 웹은 앱이 state.lang으로 보고한 값을 보여주기만 합니다.
      return webError(ws, "APP_ONLY", "언어는 앱에서만 바꿀 수 있습니다.");
    case "mode":
      if (!idleOnly()) return;
      if (msg.value !== "scenario" && msg.value !== "sdk")
        return webError(ws, "UNSUPPORTED", "자유 주행 모드는 2차 작업입니다.");
      S.mode = msg.value; broadcastSession(); break;
    case "manual":
      return webError(ws, "LOCKED", "시나리오 모드에서는 수동 조작을 받지 않습니다.");
    case "start": start(ws); break;
    case "pause":
      if (S.phase !== "RUNNING") return;
      clearTimeout(S.timer); S.timer = null;
      S.phase = "PAUSED"; broadcastSession(); break;
    case "resume":
      if (S.phase !== "PAUSED") return;
      S.phase = "RUNNING"; broadcastSession();
      if (S.mode !== "sdk") scheduleNext(0);   // sdk는 interval이 phase를 보고 스스로 재개
      break;
    case "abort": abort("주행 중단"); break;
    case "speedup": {
      const v = Number(msg.value);
      if (!(v >= 0.5 && v <= 20)) return webError(ws, "BAD_SPEEDUP", "배속은 0.5~20입니다.");
      S.speedup = v; broadcastSession(); break;
    }
    case "ready":   // DONE → IDLE (상태도의 ready)
      if (S.phase === "DONE") toIdle(null);
      break;
    default: webError(ws, "UNKNOWN_TYPE", `모르는 메시지: ${msg.type}`);
  }
}

// 앱이 begin의 구역을 쓰지 못하는 옛 버전이고, 시나리오에 필요한 구역이 앱에 없으면 경고합니다.
// (지도에는 구역이 보이는데 앱은 그 구역을 몰라 안내·알림이 나오지 않는 상황)
let appInfo = { caps: [], zoneIds: null };
function appZoneWarning(sc) {
  if ((appInfo.caps || []).includes("simZones")) return null;
  const k = M_LAT * Math.cos(sc.frames[0].lat * Math.PI / 180);
  const pts = sc.frames.filter(f => f.lat != null);
  const near = zonesJson.zones.filter(z => {
    const c = z.circle ? [z.circle.lat, z.circle.lng] : z.corridor ? z.corridor.points[0] : null;
    return c && pts.some(f => Math.hypot((f.lat - c[0]) * M_LAT, (f.lng - c[1]) * k) < 1500);
  }).map(z => z.id);
  const missing = appInfo.zoneIds ? near.filter(id => !appInfo.zoneIds.includes(id)) : near.filter(id => !id.startsWith("demo-"));
  if (missing.length === 0) return null;
  return `앱이 옛 버전입니다: 이 시나리오 구역(${missing.join(", ")})을 앱이 몰라 안내·알림이 나오지 않습니다. 최신 디버그 빌드를 다시 설치하세요 (.\\gradlew.bat :app:installDebug).`;
}

function onAppMessage(msg) {
  if (msg.type === "hello") {
    appInfo = { caps: msg.caps || [], zoneIds: msg.zoneIds || null };
    console.log(`[앱] hello ${msg.app || "?"} ${msg.version || ""} caps=${appInfo.caps.join(",") || "-"} 구역=${appInfo.zoneIds ? appInfo.zoneIds.length : "?"}`);
    return;
  }
  if (msg.type !== "state") return;
  S.lastState = msg;
  if (msg.today) S.lastToday = msg.today;
  if (msg.summary === true) S.summaryAtFrame = S.frameIdx;
  toWebs(msg);
  if (S.phase === "PREPARING" && msg.vehicle && msg.vehicle.gear === "P" && msg.sim === true) beginRunning();
  if (S.phase === "ENDING" && msg.summary === true) toDone(true);
  // SDK 주행: 운전자가 P로 주차해 앱이 요약을 내면 그걸로 주행 종료
  if (S.mode === "sdk" && S.phase === "RUNNING" && msg.summary === true) toDone(true);
}

wss.on("connection", (ws, req) => {
  const role = new URL(req.url, "http://x").searchParams.get("role") === "app" ? "app" : "web";
  if (role === "app") {
    if (appWs) { try { appWs.close(); } catch {} }
    appWs = ws;
    console.log("[앱] 연결됨");
    broadcastSession();
    ws.on("message", data => { try { onAppMessage(JSON.parse(data)); } catch (e) { console.error("[앱] 메시지 오류:", e.message); } });
    ws.on("close", () => {
      if (appWs !== ws) return;
      appWs = null;
      console.log("[앱] 연결 끊김");
      if (S.phase !== "IDLE") {
        clearTimers();
        toIdle("앱 연결 끊김");   // 다시 붙어도 이어서 재생하지 않습니다
      } else broadcastSession();
    });
  } else {
    webs.add(ws);
    send(ws, sessionMsg());                 // 새로고침 복원
    if (S.lastState) send(ws, S.lastState);
    if (S.result && S.phase === "DONE") send(ws, S.result);
    ws.on("message", data => { try { onWebMessage(ws, JSON.parse(data)); } catch (e) { webError(ws, "BAD_JSON", e.message); } });
    ws.on("close", () => webs.delete(ws));
  }
});

httpServer.listen(PORT, () => {
  console.log(`콕핏 서버: http://localhost:${PORT} (시나리오 ${scenarios.size}개, 구역 ${zonesJson.zones.length}개)`);
});
