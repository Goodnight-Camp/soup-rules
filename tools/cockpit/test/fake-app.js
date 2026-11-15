// 앱 없이 서버·웹을 검증하는 가짜 앱 클라이언트.
// 실제 CoachEngine을 흉내만 냅니다: 같은 구역 파일을 읽어 근사 판정을 하고,
// 문장은 Strings.render가 아니라 "[fake]" 표시가 붙은 영어 고정 문구입니다.
"use strict";
const fs = require("fs");
const path = require("path");
const WebSocket = require("ws");

const PORT = Number(process.env.PORT || 8765);
const URL = `ws://localhost:${PORT}/?role=app`;
const ZONES = JSON.parse(fs.readFileSync(
  path.join(__dirname, "..", "..", "..", "app", "src", "main", "assets", "zones", "jeju-sample.json"), "utf8")).zones;

const M_LAT = 111195.0;
const mLng = lat => 111195.0 * Math.cos(lat * Math.PI / 180);
function distM(lat, lng, zlat, zlng) {
  return Math.hypot((lat - zlat) * M_LAT, (lng - zlng) * mLng(zlat));
}
function zoneDist(z, lat, lng) {
  if (z.circle) return Math.max(0, distM(lat, lng, z.circle.lat, z.circle.lng) - z.circle.radiusM);
  const pts = z.corridor.points;
  let best = Infinity;
  for (let i = 0; i + 1 < pts.length; i++) {
    const k = mLng(pts[i][0]);
    const ax = (pts[i][0] - lat) * M_LAT, ay = (pts[i][1] - lng) * k;
    const bx = (pts[i + 1][0] - lat) * M_LAT, by = (pts[i + 1][1] - lng) * k;
    const dx = bx - ax, dy = by - ay;
    const t = (dx === 0 && dy === 0) ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / (dx * dx + dy * dy)));
    best = Math.min(best, Math.hypot(ax + t * dx, ay + t * dy));
  }
  return Math.max(0, best - z.corridor.halfWidthM);
}
// 운영 시간(KST). 요일 구분은 대충: MON-FRI만 봅니다. 가짜 앱이므로 충분합니다.
function activeAt(z, at) {
  if (!z.schedule) return true;
  const kst = new Date(at.getTime() + 9 * 3600 * 1000);
  const day = kst.getUTCDay(), hm = kst.getUTCHours() * 60 + kst.getUTCMinutes();
  const toMin = s => { const [h, m] = s.split(":").map(Number); return h * 60 + m; };
  return z.schedule.some(w => {
    const weekday = day >= 1 && day <= 5;
    if (w.days === "MON-FRI" && !weekday) return false;
    return hm >= toMin(w.start) && hm < toMin(w.end);
  });
}

// ── 가짜 판정 상태 ──
let sim = false, lang = "en", prevLang = null;
const vehicle = { speed: 0, gear: "P", turn: "OFF", lat: null, lng: null };
let today, alert, firedAlerts, speeding, turnEpisode, parked, movedSec, droveEnough, summarySent, lastAt;

function resetSession() {
  sim = true;
  today = { count: 0, totalFineKrw: 0, items: [] };
  alert = null; firedAlerts = new Set();
  speeding = null; turnEpisode = null; parked = null;
  movedSec = 0; droveEnough = false; summarySent = false; lastAt = null;
  vehicle.speed = 0; vehicle.gear = "P"; vehicle.turn = "OFF";
}
resetSession(); sim = false;

function say(rule, severity, text, zoneId, at) {
  alert = { rule, severity, text: `[fake] ${text}`, zoneId: zoneId || null, at: at.toISOString() };
  console.log(`[fake-app] ${severity} ${rule}: ${text}`);
}
function miss(rule, zone, fineKrw, at, extra) {
  today.items.push({ rule, zoneId: zone ? zone.id : null, zoneName: zone ? zone.name : null,
    fineKrw, at: at.toISOString(), ...extra });
  today.count = today.items.length;
  today.totalFineKrw = today.items.reduce((a, m) => a + m.fineKrw, 0);
}

const zonesOf = kind => ZONES.filter(z => z.kind === kind);
const inZone = (z, margin) => vehicle.lat != null && zoneDist(z, vehicle.lat, vehicle.lng) <= margin;

function step(at, summary) {
  const dt = lastAt ? (at - lastAt) / 1000 : 0;
  lastAt = at;
  const moving = vehicle.speed >= 5 && vehicle.gear === "D";
  if (moving) { movedSec += dt; if (movedSec >= 30) droveEnough = true; }

  // 버스전용차로: 운영 시간에 안에서 주행하면 한 번 안내
  for (const z of zonesOf("BUS_LANE")) {
    if (moving && inZone(z, 0) && activeAt(z, at) && !firedAlerts.has(z.id)) {
      firedAlerts.add(z.id);
      say("BUS_LANE_ACTIVE", "INFO", "Bus-only lane until 09:00. Please move over.", z.id, at);
    }
  }
  // 어린이보호구역: 진입 안내 + 과속(2초 유지) 경고와 기록
  for (const z of zonesOf("SCHOOL_ZONE")) {
    const limit = z.limitKmh || 30;
    if (inZone(z, 30) && !firedAlerts.has(z.id)) {
      firedAlerts.add(z.id);
      say("SCHOOL_ZONE_ENTER", "INFO", `School zone. Limit ${limit} km/h.`, z.id, at);
    }
    const over = inZone(z, 0) && vehicle.gear !== "P" && vehicle.speed >= limit + 1;
    if (over) {
      if (!speeding) speeding = { zone: z, since: at, max: 0, warned: false };
      speeding.max = Math.max(speeding.max, vehicle.speed);
      if ((at - speeding.since) / 1000 >= 2 && !speeding.warned) {
        speeding.warned = true;
        say("SCHOOL_ZONE_SPEEDING", "URGENT", `Slow down! ${Math.round(vehicle.speed)} in a ${limit} zone.`, z.id, at);
      }
    } else if (speeding && speeding.zone === z) {   // 다른 스쿨존을 검사할 때 진행 중인 과속을 지우지 않게
      if (speeding.warned)
        miss("SCHOOL_ZONE_SPEEDING", speeding.zone, 70000, at,
          { speedKmh: Math.round(speeding.max), limitKmh: limit });
      speeding = null;
    }
  }
  // 우회전: 깜빡이 켜고 50m 안이면 에피소드 시작, 원 안 최저 속도로 미정지 판정
  for (const z of zonesOf("RIGHT_TURN")) {
    if (!turnEpisode && vehicle.turn === "RIGHT" && vehicle.gear === "D" && inZone(z, 50)) {
      turnEpisode = { zone: z, min: vehicle.speed, core: inZone(z, 0) };
      say("RIGHT_TURN_ZONE", "WARN", "Right turn ahead: stop fully before turning.", z.id, at);
    }
  }
  if (turnEpisode) {
    turnEpisode.min = Math.min(turnEpisode.min, vehicle.speed);
    if (inZone(turnEpisode.zone, 0)) turnEpisode.core = true;
    if (vehicle.turn !== "RIGHT") {
      if (turnEpisode.core && turnEpisode.min > 2) {
        miss("RIGHT_TURN_NO_STOP", turnEpisode.zone, 60000, at, { speedKmh: Math.round(turnEpisode.min) });
        say("RIGHT_TURN_NO_STOP", "INFO", "You turned without a full stop.", turnEpisode.zone.id, at);
      }
      turnEpisode = null;
    }
  }
  // 주정차: P로 바꾸면 단속 구역 안인지 보고, 유예 1분 전 재알림, P 10초 뒤 요약
  if (vehicle.gear === "P" && !parked && droveEnough) {
    parked = { at, zone: null, reminded: false };
    for (const z of zonesOf("PARKING_ENFORCEMENT")) {
      if (inZone(z, 5) && activeAt(z, at)) {
        parked.zone = z;
        miss("PARKING_CCTV", z, 40000, at);
        say("PARKING_CCTV", "URGENT", `CCTV enforcement here. About ${z.graceMinutes} minutes grace.`, z.id, at);
      }
    }
  }
  if (vehicle.gear !== "P") parked = null;
  if (parked && parked.zone && !parked.reminded
      && (at - parked.at) / 1000 >= (parked.zone.graceMinutes - 1) * 60) {
    parked.reminded = true;
    say("PARKING_REMINDER", "URGENT", "One minute left before enforcement. Please move the car.", parked.zone.id, at);
  }
  if (parked && droveEnough && !summarySent && (at - parked.at) / 1000 >= 10) {
    summarySent = true;
    summary.due = true;
  }
}

// ── SDK 주행 모드용 가상 운전자: 에뮬레이터 조작판에서 사람이 속도·기어를 바꾸는 것을 흉내 ──
// (실제 앱에서는 이 값들이 Pleos/AAOS SDK에서 옵니다. 위치는 서버가 속도로 적분해 내려줍니다.)
let driver = null;
function startDriver() {
  stopDriver();
  let t = 0;
  driver = setInterval(() => {
    t++;
    if (t === 2) vehicle.gear = "D";
    if (t >= 3 && t < 58) vehicle.speed = 55;       // 스쿨존(제한 30)을 과속으로 통과
    if (t >= 58) vehicle.speed = 0;
    if (t >= 61 && vehicle.gear !== "P") vehicle.gear = "P";   // 주차 → 10초 뒤 요약
    queueState(false);
  }, 1000);
}
function stopDriver() { if (driver) { clearInterval(driver); driver = null; } }

// ── 서버 연결(끊기면 2초 간격 재접속) ──
let ws = null, sendTimer = null, pendingSummary = false;
function stateMsg(summaryDue) {
  return {
    type: "state", sim, lang,
    app: { service: true, carSignal: true, perms: { location: true, carSpeed: true, mic: true } },
    vehicle: { speed: vehicle.speed, gear: vehicle.gear, turn: vehicle.turn, lat: vehicle.lat, lng: vehicle.lng },
    alert,
    zones: ZONES.filter(z => vehicle.lat != null && zoneDist(z, vehicle.lat, vehicle.lng) <= 0).map(z => z.id),
    today,
    summary: !!summaryDue,
  };
}
// 최소 간격 100ms로 묶어 보냅니다. summary는 놓치지 않게 따로 보관합니다.
function queueState(summaryDue) {
  if (summaryDue) pendingSummary = true;
  if (sendTimer) return;
  sendTimer = setTimeout(() => {
    sendTimer = null;
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(stateMsg(pendingSummary)));
      pendingSummary = false;
    }
  }, 100);
}

function connect() {
  ws = new WebSocket(URL);
  ws.on("open", () => {
    console.log("[fake-app] 서버에 연결됨");
    // 실제 앱과 같이: 구역 전달(simZones)을 지원하고, 갖고 있는 구역 id를 알립니다.
    ws.send(JSON.stringify({ type: "hello", app: "souprules-fake", version: "0.1.0", caps: ["simZones", "lang"], zoneIds: ZONES.map(z => z.id) }));
  });
  ws.on("message", data => {
    let msg; try { msg = JSON.parse(data); } catch { return; }
    if (msg.type === "begin") {
      resetSession();
      // 실제 앱과 같이: 시나리오 언어가 오면 시뮬레이션 동안만 그 언어로, end에서 되돌립니다.
      if (msg.lang) { if (prevLang == null) prevLang = lang; lang = msg.lang; }
      if (msg.mode === "sdk") startDriver(); else stopDriver();
      console.log(`[fake-app] begin ${msg.scenario}${msg.mode === "sdk" ? " (SDK 주행: 가상 운전자)" : ""}`);
      queueState(false); return;
    }
    if (msg.type === "end") {
      sim = false; alert = null; stopDriver();
      if (prevLang != null) { lang = prevLang; prevLang = null; }
      console.log("[fake-app] end"); queueState(false); return;
    }
    // 언어는 앱 자체 설정입니다. 서버에서 바꾸지 않고 state.lang으로 보고만 합니다.
    if (msg.type !== "signal") return;
    // 필드 순서: 위치 → 기어 → 깜빡이 → 차속 (DemoDrive.parse와 동일)
    if (msg.lat != null) { vehicle.lat = msg.lat; vehicle.lng = msg.lng; }
    if (msg.gear != null) vehicle.gear = msg.gear;
    if (msg.turn != null) vehicle.turn = msg.turn;
    if (msg.speed != null) vehicle.speed = msg.speed;
    if (!sim) return;   // 세션 밖 신호는 무시(정리 신호 등)
    const summary = { due: false };
    step(new Date(msg.at), summary);
    queueState(summary.due);
  });
  ws.on("close", () => { ws = null; setTimeout(connect, 2000); });
  ws.on("error", () => {});
}
connect();
console.log(`[fake-app] ${URL} 에 접속 시도, 구역 ${ZONES.length}개 로드`);
