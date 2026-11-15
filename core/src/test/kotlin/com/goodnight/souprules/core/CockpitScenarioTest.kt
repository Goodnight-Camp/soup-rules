package com.goodnight.souprules.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * 콕핏 시나리오(tools/cockpit/scenarios)를 실제 CoachEngine으로 재생해 기대값(expect)과 안내 흐름을 고정합니다.
 * 콕핏 서버·앱 링크와 같은 신호 순서(위치 → 기어 → 깜빡이 → 운전대 → 차속 → Tick)를 씁니다.
 */
class CockpitScenarioTest {
 private val root=File("..")
 private val zones=ZoneJson.parse(File(root,"app/src/main/assets/zones/jeju-sample.json").readText())

 private data class Replay(val alerts:List<Alert>,val misses:List<NearMiss>,val summaries:Int,val expectCount:Int,val expectFine:Int,val lang:String?,val hipass:Boolean)

 private fun replay(id:String):Replay {
  val sc=JSONObject(File(root,"tools/cockpit/scenarios/$id.json").readText())
  val start=Instant.parse(sc.getString("start"))
  val engine=CoachEngine(zones).also { it.hipass=sc.optBoolean("hipass",true) }
  val frames=sc.getJSONArray("frames")
  val alerts=mutableListOf<Alert>(); val misses=mutableListOf<NearMiss>(); var summaries=0
  for(i in 0 until frames.length()) {
   val o=frames.getJSONObject(i); val at=start.plusSeconds(o.getLong("t"))
   val signals=buildList {
    if(o.has("lat")) add(Signal.Position(GeoPoint(o.getDouble("lat"),o.getDouble("lng"))))
    if(o.has("gear")) add(Signal.Shift(Gear.valueOf(o.getString("gear"))))
    if(o.has("turn")) add(Signal.Turn(TurnSignal.valueOf(o.getString("turn"))))
    if(o.has("steer")) add(Signal.Steering(o.getDouble("steer")))
    if(o.has("speed")) add(Signal.Speed(o.getDouble("speed")))
    add(Signal.Tick)
   }
   signals.forEach { val s=engine.accept(it,at); alerts+=s.alerts; misses+=s.nearMisses; if(s.summaryDue) summaries++ }
  }
  val e=sc.getJSONObject("expect")
  return Replay(alerts,misses,summaries,e.getInt("count"),e.getInt("totalFineKrw"),sc.optString("lang").ifEmpty { null },sc.optBoolean("hipass",true))
 }

 private fun List<Alert>.rules()=map { it.rule }

 @Test fun everyScenarioMatchesItsExpect() {
  File(root,"tools/cockpit/scenarios").listFiles { f-> f.extension=="json" }!!.sorted().forEach { f->
   val r=replay(f.nameWithoutExtension)
   assertEquals("${f.name} 건수",r.expectCount,r.misses.size)
   assertEquals("${f.name} 금액",r.expectFine,r.misses.sumOf { it.fineKrw })
   assertTrue("${f.name} 요약 없음",r.summaries>=1)
  }
 }

 /** 사례 1) 홍콩 A씨 · 제주 렌터카 첫날 */
 @Test fun caseHongKong() {
  val r=replay("b-proposal-hk")
  assertEquals(Lang.ZH_HANT,Lang.fromTag(r.lang))
  // 화면은 繁體中文, 음성은 공식 로케일이 없어 영어
  assertEquals(listOf("en-US"),VoiceNegotiator(Lang.ZH_HANT).chain.map { it.locale.toLanguageTag() })

  // 1. 공항 앞 첫 교차로: 깜빡이 없는 좌회전 → 사후 안내 + 기록
  // 4. 식당 앞 단속 구간 P → 유예 카운트다운 + 기록
  assertEquals(listOf(RuleId.TURN_NO_SIGNAL,RuleId.PARKING_CCTV),r.misses.map { it.rule })
  assertEquals(70_000,r.misses.sumOf { it.fineKrw })
  val turn=r.alerts.first { it.rule==RuleId.TURN_NO_SIGNAL }
  assertEquals(Severity.INFO,turn.severity)

  // 2. 회전교차로 40m 앞: 사전 안내, 역방향 없음
  assertTrue(RuleId.ROUNDABOUT_ENTER in r.alerts.rules())
  assertFalse(RuleId.ROUNDABOUT_WRONG_WAY in r.alerts.rules())

  // 3. 한림초 앞 50km/h 접근: 스쿨존 30m 앞 사전 안내(주의), 감속해 기록 없음
  val school=r.alerts.first { it.rule==RuleId.SCHOOL_ZONE_ENTER }
  assertEquals(Severity.WARN,school.severity)
  assertEquals("30",school.args["limit"])
  assertFalse(RuleId.SCHOOL_ZONE_SPEEDING in r.alerts.rules())

  // 4. "5분 뒤 단속" + 유예 1분 전 재알림
  val cctv=r.alerts.first { it.rule==RuleId.PARKING_CCTV }
  assertEquals("5",cctv.args["minutes"])
  assertTrue(RuleId.PARKING_REMINDER in r.alerts.rules())

  // 순서: 좌회전 → 회전교차로 → 스쿨존 → 단속
  val order=listOf(RuleId.TURN_NO_SIGNAL,RuleId.ROUNDABOUT_ENTER,RuleId.SCHOOL_ZONE_ENTER,RuleId.PARKING_CCTV,RuleId.PARKING_REMINDER).map { rule-> r.alerts.indexOfFirst { it.rule==rule } }
  assertEquals(order.sorted(),order)

  // 하루 요약(繁體中文): 2번, 최대 약 70,000
  val day=DaySummary.today(r.misses,r.misses.first().at)
  assertEquals("今天有2次差點被罰款，合計最多約70,000韓圜。",day.headline(Lang.ZH_HANT))
 }

 /** 사례 2) 프랑스 B씨 · 인천공항 → 서울 */
 @Test fun caseFrance() {
  val r=replay("c-proposal-fr")
  assertEquals(Lang.FR,Lang.fromTag(r.lang))
  assertFalse(r.hipass)
  assertEquals(listOf("fr-CA","en-US"),VoiceNegotiator(Lang.FR).chain.map { it.locale.toLanguageTag() })

  // 1. 요금소 300m 앞: 하이패스 카드 없음 안내
  assertEquals(Msg.TOLL_HIPASS_NO,r.alerts.first { it.rule==RuleId.TOLL_PLAZA }.msg)
  assertFalse(RuleId.TOLL_SLOW_DOWN in r.alerts.rules())

  // 2. 08시 버스전용차로: "09:00까지" 사전 안내, 차로 유지라 기록 없음
  val bus=r.alerts.first { it.rule==RuleId.BUS_LANE_ACTIVE }
  assertEquals("09:00",bus.args["until"])
  assertFalse(RuleId.BUS_LANE_STOP in r.alerts.rules())

  // 3-1. 우회전 깜빡이 + 50m 이내: 빨간불 정지선 사전 안내
  assertEquals(Msg.RIGHT_TURN_ZONE,r.alerts.first { it.rule==RuleId.RIGHT_TURN_ZONE }.msg)
  // 3-2. 차속이 0이 되지 않고 교차로 통과 → 기록
  assertEquals(listOf(RuleId.RIGHT_TURN_NO_STOP),r.misses.map { it.rule })
  assertEquals(60_000,r.misses.single().fineKrw)
  // 3-3. 우회전 직후 횡단보도 40m 앞 안내
  assertTrue(RuleId.CROSSWALK_AFTER_TURN in r.alerts.rules())
  // 깜빡이를 켰으므로 '깜빡이 없는 회전'은 없음
  assertFalse(RuleId.TURN_NO_SIGNAL in r.alerts.rules())

  val order=listOf(RuleId.TOLL_PLAZA,RuleId.BUS_LANE_ACTIVE,RuleId.RIGHT_TURN_ZONE,RuleId.CROSSWALK_AFTER_TURN).map { rule-> r.alerts.indexOfFirst { it.rule==rule } }
  assertEquals(order.sorted(),order)
  // 우회전을 마친 같은 순간에 '미정지 기록'과 '횡단보도 안내'가 함께 나옵니다. 앞을 보는 횡단보도 안내(주의)를 먼저 말합니다.
  val noStop=r.alerts.first { it.rule==RuleId.RIGHT_TURN_NO_STOP }; val cw=r.alerts.first { it.rule==RuleId.CROSSWALK_AFTER_TURN }
  assertTrue(r.alerts.indexOf(cw)<r.alerts.indexOf(noStop))
  assertTrue(cw.severity>noStop.severity)

  // 하루 요약(Français): 1번, 약 60,000
  val day=DaySummary.today(r.misses,r.misses.first().at)
  assertEquals("Aujourd'hui, vous avez failli recevoir une amende une fois, environ 60,000 wons.",day.headline(Lang.FR))
  // 프랑스어 안내 문장
  assertEquals("Virage à droite devant. Si votre feu est rouge, arrêtez-vous d'abord complètement à la ligne d'arrêt. Puis arrêtez-vous au passage piéton si quelqu'un traverse.",
   Strings.render(Lang.FR,r.alerts.first { it.rule==RuleId.RIGHT_TURN_ZONE }))
 }

 @Test fun caseZonesDoNotTouchTheJejuDemoRoute() {
  // 사례 구역은 기존 데모(A·D) 경로에서 수 km 떨어져 있어 DemoScenarioTest 결과를 바꾸지 않습니다.
  val demoPoint=GeoPoint(33.4945,126.4980)
  zones.zones.filter { it.id.startsWith("case-") }.forEach { assertTrue(it.id,it.shape.distanceM(demoPoint)>5_000) }
 }
}
