// README 13장 완료 기준 1, 4, 5, 6, 7, 8을 가짜 앱으로 자동 확인합니다.
// 서버와 fake-app.js를 실제 프로세스로 띄우고, 웹 역할 클라이언트로 조작·관찰합니다.
// 사용: node test/check.js   (tools/cockpit 에서)
"use strict";
const { spawn } = require("child_process");
const path = require("path");
const WebSocket = require("ws");

const PORT = Number(process.env.PORT || 8765);
const DIR = path.join(__dirname, "..");
const results = [];
const report = (no, name, pass, how) => {
  results.push({ no, pass });
  console.log(`\n[기준 ${no}] ${pass ? "통과" : "실패"} — ${name}\n  확인: ${how}`);
};

const sleep = ms => new Promise(r => setTimeout(r, ms));
function child(script) {
  const p = spawn(process.execPath, [script], { cwd: DIR, stdio: ["ignore", "pipe", "pipe"] });
  p.stdout.on("data", d => process.stdout.write(`  | ${d}`.replace(/\n(?!$)/g, "\n  | ")));
  p.stderr.on("data", d => process.stderr.write(`  ! ${d}`));
  return p;
}

// 웹 역할 클라이언트: 받은 메시지를 모아 두고 조건을 기다립니다.
function webClient() {
  const c = { msgs: [], session: null, phase: "IDLE", ws: null, open: false };
  c.ws = new WebSocket(`ws://localhost:${PORT}/?role=web`);
  c.ws.on("open", () => { c.open = true; });
  c.ws.on("message", d => {
    const m = JSON.parse(d);
    c.msgs.push(m);
    if (m.type === "session") { c.session = m; c.phase = m.phase; }
  });
  c.ws.on("error", () => {});
  c.send = m => c.ws.send(JSON.stringify(m));
  c.waitFor = async (fn, ms, what) => {
    const end = Date.now() + ms;
    while (Date.now() < end) {
      const hit = fn(c);
      if (hit) return hit;
      await sleep(50);
    }
    throw new Error(`시간 초과(${ms}ms): ${what}`);
  };
  return c;
}
const lastResult = c => [...c.msgs].reverse().find(m => m.type === "result");

async function main() {
  console.log("서버 기동…");
  const server = child("server.js");
  let fake = null;
  try {
    let w1 = null;
    for (let i = 0; i < 10 && !w1; i++) {
      const c = webClient();
      w1 = await c.waitFor(x => x.open && x.session, 1000, "서버 접속")
        .then(() => c).catch(() => { c.ws.close(); return null; });
      if (!w1) await sleep(300);
    }
    if (!w1) throw new Error("서버에 접속할 수 없습니다");

    console.log("fake-app 기동…");
    fake = child(path.join("test", "fake-app.js"));
    await w1.waitFor(c => c.session.appConnected, 5000, "앱 연결");

    // ── 시나리오 A 재생 (배속 10) ──
    w1.send({ type: "speedup", value: 10 });
    w1.send({ type: "select", scenario: "a-jeju-basic" });
    await w1.waitFor(c => c.session.scenario === "a-jeju-basic" && c.session.speedup === 10, 3000, "A 선택");
    w1.send({ type: "start" });
    await w1.waitFor(c => c.phase === "RUNNING", 8000, "RUNNING 진입");

    // 기준 4: 주행 중 시나리오·모드 변경은 LOCKED, 언어 변경 메시지는 언제나 거부(앱 전용 설정)
    const before = w1.msgs.filter(m => m.type === "error").length;
    w1.send({ type: "select", scenario: "d-jeju-clean" });
    w1.send({ type: "mode", value: "scenario" });
    w1.send({ type: "lang", value: "fr" });
    const rejected = await w1.waitFor(c => {
      const errs = c.msgs.filter(m => m.type === "error").slice(before);
      return errs.filter(m => m.code === "LOCKED").length >= 2 && errs.some(m => m.code === "APP_ONLY");
    }, 3000, "변경 거부").then(() => true).catch(() => false);
    report(4, "주행 중 시나리오·모드 변경 거부", rejected && w1.session.scenario === "a-jeju-basic",
      "RUNNING 중 select/mode → error LOCKED 2건, lang → error APP_ONLY(언어는 앱 전용), 선택이 a-jeju-basic 그대로");

    // 기준 8: 주행 중 새로 접속(새로고침)해도 진행 상태 복원
    await w1.waitFor(c => c.msgs.some(m => m.type === "frame" && m.t > 10), 20000, "진행 t>10");
    const w2 = webClient();
    const s2 = await w2.waitFor(c => c.session, 3000, "두 번째 웹 접속");
    report(8, "웹 새로고침 시 진행 상태 복원",
      s2.phase === "RUNNING" && s2.locked === true && s2.t > 0 && s2.scenario === "a-jeju-basic",
      `새 웹 접속 직후 session: phase=${s2.phase}, locked=${s2.locked}, t=${s2.t}`);
    w2.ws.close();

    // 기준 5: 중간 P에서 시나리오가 끝나지 않고 단속 예고·재알림이 나온다
    // (A의 주차 장면: P 진입 뒤에도 84초(시나리오 시간) 동안 RUNNING이 유지되어야 합니다)
    const phaseOf = () => w1.phase;
    let parkedWhileRunning = false, cctvWhileRunning = false, reminderWhileRunning = false;
    const watcher = setInterval(() => {
      const st = [...w1.msgs].reverse().find(m => m.type === "state");
      if (!st || phaseOf() !== "RUNNING") return;
      if (st.vehicle && st.vehicle.gear === "P") parkedWhileRunning = true;
      if (st.alert && st.alert.rule === "PARKING_CCTV") cctvWhileRunning = true;
      if (st.alert && st.alert.rule === "PARKING_REMINDER") reminderWhileRunning = true;
    }, 40);

    // 기준 1: 가짜 앱만으로 A가 끝까지 재생되고 종료 화면(result)이 뜬다
    await w1.waitFor(c => lastResult(c), 90000, "A result 수신");
    clearInterval(watcher);
    const rA = lastResult(w1);
    report(1, "가짜 앱만으로 시나리오 A 완주 + 종료 화면",
      w1.phase === "DONE" && !!rA,
      `DONE 도달, result 수신: pass=${rA.pass}, actual=${JSON.stringify(rA.actual)} (웹 종료 화면은 result로 뜸)`);
    report(5, "중간 P에서 계속 재생 + 단속 예고·재알림",
      parkedWhileRunning && cctvWhileRunning && reminderWhileRunning,
      `RUNNING 중 기어 P 관측=${parkedWhileRunning}, PARKING_CCTV=${cctvWhileRunning}, PARKING_REMINDER=${reminderWhileRunning}`);

    // 기준 6: 주행 중단 후 다른 시나리오(D)를 시작해도 이전 기록이 섞이지 않고 오탐이 없다
    // A를 다시 시작해 기록이 쌓인 시점(스쿨존 과속 이후)에 중단하고 D를 재생합니다.
    w1.send({ type: "ready" });
    await w1.waitFor(c => c.phase === "IDLE", 3000, "IDLE 복귀");
    const aStart = w1.msgs.length;
    w1.send({ type: "start" });
    await w1.waitFor(c => c.phase === "RUNNING", 8000, "A 재주행 RUNNING");
    await w1.waitFor(
      c => c.msgs.slice(aStart).some(m => m.type === "state" && m.sim && m.today && m.today.count >= 1),
      60000, "A 기록 1건 이상");
    w1.send({ type: "abort" });
    await w1.waitFor(c => c.phase === "IDLE", 3000, "중단 후 IDLE");
    const dStart = w1.msgs.length;
    w1.send({ type: "select", scenario: "d-jeju-clean" });
    await w1.waitFor(c => c.session.scenario === "d-jeju-clean", 3000, "D 선택");
    w1.send({ type: "start" });
    await w1.waitFor(c => c.msgs.slice(dStart).some(m => m.type === "result"), 90000, "D result 수신");
    const dMsgs = w1.msgs.slice(dStart);
    const rD = [...dMsgs].reverse().find(m => m.type === "result");
    const dStates = dMsgs.filter(m => m.type === "state" && m.sim);
    const dirty = dStates.some(m => m.today && m.today.count > 0);
    report(6, "주행 중단 후 다른 시나리오에서 기록이 섞이지 않음",
      rD.actual.count === 0 && rD.actual.totalFineKrw === 0 && !dirty,
      `A를 기록 1건 이상에서 중단 → D 완주: actual=${JSON.stringify(rD.actual)}, D 재생 중 today.count>0 없음=${!dirty}, pass=${rD.pass}`);

    // 기준 7: 주행 중 앱 강제 종료 → "앱 연결 끊김" + IDLE, 재기동하면 자동 재접속
    w1.send({ type: "ready" });
    await w1.waitFor(c => c.phase === "IDLE", 3000, "IDLE 복귀");
    w1.send({ type: "select", scenario: "a-jeju-basic" });
    w1.send({ type: "start" });
    await w1.waitFor(c => c.phase === "RUNNING", 8000, "재주행 RUNNING");
    await sleep(2000);
    fake.kill("SIGKILL");
    const sDrop = await w1.waitFor(
      c => c.session && !c.session.appConnected && c.phase === "IDLE", 5000, "끊김 감지")
      .then(() => w1.session).catch(() => null);
    fake = child(path.join("test", "fake-app.js"));
    const reattached = await w1.waitFor(c => c.session.appConnected, 8000, "재접속")
      .then(() => true).catch(() => false);
    report(7, "앱 강제 종료 시 IDLE 복귀·표시, 재기동 시 자동 접속",
      !!sDrop && (sDrop.notice || "").includes("앱 연결 끊김") && reattached,
      `kill 후 session: phase=${sDrop && sDrop.phase}, notice="${sDrop && sDrop.notice}" → fake-app 재기동 2초 내 appConnected=true`);

    // 추가: SDK 주행 모드 — 실차(에뮬레이터) 신호가 속도·기어를 정하고, 서버는 위치만 적분
    w1.send({ type: "select", scenario: "a-jeju-basic" });
    w1.send({ type: "mode", value: "sdk" });
    await w1.waitFor(c => c.session.mode === "sdk", 3000, "sdk 모드 선택");
    const sdkStart = w1.msgs.length;
    w1.send({ type: "start" });
    await w1.waitFor(c => c.phase === "RUNNING", 8000, "sdk RUNNING");
    const moved = await w1.waitFor(c => {
      const fr = c.msgs.slice(sdkStart).filter(m => m.type === "frame" && m.lat != null);
      return fr.length >= 3 && Math.abs(fr[fr.length - 1].lat - fr[0].lat) > 1e-4;
    }, 30000, "차량 이동").then(() => true).catch(() => false);
    const rS = await w1.waitFor(c => c.msgs.slice(sdkStart).find(m => m.type === "result"), 150000, "sdk result");
    report("SDK", "SDK 주행 모드(실차 신호 → 서버 위치 적분 → 앱 판단)",
      moved && rS.pass === null && rS.summaryReceived && rS.actual.count >= 1,
      `가상 운전자 55km/h 주행: 이동=${moved}, P 주차 후 요약 수신=${rS.summaryReceived}, 기록 ${rS.actual.count}건(스쿨존 과속), 기대값 비교 없음(pass=null)`);
    w1.send({ type: "ready" });
    await w1.waitFor(c => c.phase === "IDLE", 3000, "sdk 후 IDLE 복귀");
    w1.send({ type: "mode", value: "scenario" });

    w1.ws.close();
  } finally {
    if (fake) fake.kill();
    server.kill();
  }

  console.log("\n────────────────");
  const fails = results.filter(r => !r.pass);
  const names = rs => rs.map(r => r.no).sort((a, b) => String(a).localeCompare(String(b), undefined, { numeric: true })).join(", ");
  console.log(fails.length === 0 ? `완료 기준 ${names(results)} 전부 통과` : `실패: 기준 ${names(fails)}`);
  process.exit(fails.length === 0 ? 0 : 1);
}

main().catch(e => { console.error("검사 실패:", e.message); process.exit(1); });
