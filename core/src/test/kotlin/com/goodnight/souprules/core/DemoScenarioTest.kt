package com.goodnight.souprules.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

/** 앱에 넣은 데모 주행·예시 구역을 그대로 엔진에 돌려, 데모가 보여 줄 장면을 고정합니다. */
class DemoScenarioTest {
 private val assets=File("../app/src/main/assets")

 private fun replay():Pair<List<Alert>,List<NearMiss>> {
  val zones=ZoneJson.parse(File(assets,"zones/jeju-sample.json").readText())
  val root=JSONObject(File(assets,"demo/jeju-drive.json").readText())
  val start=Instant.parse(root.getString("start"))
  val frames=root.getJSONArray("frames")
  val engine=CoachEngine(zones)
  val alerts=mutableListOf<Alert>(); val misses=mutableListOf<NearMiss>(); var summaries=0
  for(i in 0 until frames.length()) {
   val o=frames.getJSONObject(i); val at=start.plusSeconds(o.getLong("t"))
   val signals=buildList {
    if(o.has("lat")) add(Signal.Position(GeoPoint(o.getDouble("lat"),o.getDouble("lng"))))
    if(o.has("gear")) add(Signal.Shift(Gear.valueOf(o.getString("gear"))))
    if(o.has("turn")) add(Signal.Turn(TurnSignal.valueOf(o.getString("turn"))))
    if(o.has("speed")) add(Signal.Speed(o.getDouble("speed")))
    add(Signal.Tick)
   }
   signals.forEach { val s=engine.accept(it,at); alerts+=s.alerts; misses+=s.nearMisses; if(s.summaryDue) summaries++ }
  }
  assertEquals("정차 후 요약은 한 번",1,summaries)
  return alerts to misses
 }

 @Test fun demoShowsEveryRuleInOrder() {
  val (alerts,_)=replay()
  assertEquals(listOf(
   RuleId.BUS_LANE_ACTIVE,
   RuleId.SCHOOL_ZONE_ENTER,
   RuleId.SCHOOL_ZONE_SPEEDING,
   RuleId.RIGHT_TURN_ZONE,
   RuleId.RIGHT_TURN_NO_STOP,
   RuleId.PARKING_CCTV,
   RuleId.PARKING_REMINDER,
  ),alerts.map { it.rule }.distinct())
 }

 @Test fun demoEndsWithThreeNearMisses() {
  val (_,misses)=replay()
  assertEquals(listOf(RuleId.SCHOOL_ZONE_SPEEDING,RuleId.RIGHT_TURN_NO_STOP,RuleId.PARKING_CCTV),misses.map { it.rule })
  val summary=DaySummary.today(misses,misses.first().at)
  assertEquals(3,summary.count)
  assertEquals(170_000,summary.totalFineKrw)
  assertEquals("Today you came close to a fine 3 times, up to about 170,000 won in total.",summary.headline(Lang.EN))
 }

 @Test fun everyDemoZoneIsMarkedSample() {
  val zones=ZoneJson.parse(File(assets,"zones/jeju-sample.json").readText())
  assertTrue(zones.zones.isNotEmpty())
  assertTrue(zones.zones.all { it.sample })
 }
}
