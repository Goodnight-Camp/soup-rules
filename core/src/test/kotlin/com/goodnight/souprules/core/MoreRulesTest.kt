package com.goodnight.souprules.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import kotlin.math.cos
import kotlin.math.sin

/** 2차 규칙: 방향지시등·안전띠·회전교차로·횡단보도·주정차 금지 지점·비보호 좌회전·하이패스·통학버스. */
class MoreRulesTest {
 private val t0=Instant.parse("2026-11-15T23:30:00Z") // 월 08:30 KST
 private fun at(sec:Long)=t0.plusSeconds(sec)
 private val base=GeoPoint(33.50,126.50)
 private fun off(p:GeoPoint,northM:Double,eastM:Double)=GeoPoint(p.lat+northM/111195.0,p.lng+eastM/(111195.0*cos(Math.toRadians(p.lat))))

 private fun CoachEngine.run(sec:Long,vararg signals:Signal):Step {
  var a=listOf<Alert>(); var m=listOf<NearMiss>(); var s=false
  signals.forEach { val r=accept(it,at(sec)); a=a+r.alerts; m=m+r.nearMisses; s=s||r.summaryDue }
  return Step(a,m,s)
 }
 private fun engine(vararg z:Zone)=CoachEngine(ZoneIndex(z.toList()))
 private fun List<Step>.rules()=flatMap { it.alerts.map { a->a.rule } }
 private fun List<Step>.misses()=flatMap { it.nearMisses.map { m->m.rule } }

 // ── 안전띠 ──
 @Test fun driverSeatbeltUrgentOncePerTripRecord() {
  val e=engine()
  e.run(0,Signal.Seat(0,buckled=false),Signal.Shift(Gear.D))
  val s=e.run(1,Signal.Speed(20.0))
  assertEquals(RuleId.SEATBELT_DRIVER,s.alerts.single().rule); assertEquals(Severity.URGENT,s.alerts.single().severity)
  assertEquals(Fines.SEATBELT,s.nearMisses.single().fineKrw)
  assertTrue(e.run(30,Signal.Speed(20.0)).alerts.isEmpty())
  val again=e.run(61,Signal.Speed(20.0))
  assertEquals(RuleId.SEATBELT_DRIVER,again.alerts.single().rule); assertTrue(again.nearMisses.isEmpty())
  assertTrue(e.run(62,Signal.Seat(0,buckled=true),Signal.Speed(20.0)).alerts.isEmpty())
 }

 @Test fun rearSeatbeltOnlyForOccupiedSeats() {
  val e=engine()
  e.run(0,Signal.Seat(0,buckled=true),Signal.Seat(5,occupied=false,buckled=false),Signal.Shift(Gear.D))
  assertTrue(e.run(1,Signal.Speed(30.0)).alerts.isEmpty())
  val s=e.run(2,Signal.Seat(4,occupied=true,buckled=false),Signal.Speed(30.0))
  assertEquals(RuleId.SEATBELT_PASSENGER,s.alerts.single().rule)
 }

 // ── 깜빡이 없는 회전 ──
 private fun cornerDrive(e:CoachEngine,speed:Double,stepM:Double,signal:Boolean):List<Step> {
  val out=mutableListOf(e.run(0,Signal.Shift(Gear.D),Signal.Speed(speed),Signal.Position(base)))
  var p=base; var t=1L
  if(signal) out+=e.run(t,Signal.Turn(TurnSignal.RIGHT))
  repeat(4) { p=off(p,stepM,0.0); out+=e.run(t++,Signal.Position(p)) }
  repeat(4) { p=off(p,0.0,stepM); out+=e.run(t++,Signal.Position(p)) }
  if(signal) out+=e.run(t,Signal.Turn(TurnSignal.OFF))
  return out
 }

 @Test fun slowTurnWithoutSignalIsNearMiss() {
  val steps=cornerDrive(engine(),15.0,5.0,signal=false)
  assertEquals(listOf(RuleId.TURN_NO_SIGNAL),steps.misses())
  assertTrue(RuleId.TURN_NO_SIGNAL in steps.rules())
 }

 @Test fun turnWithSignalIsFine() {
  assertTrue(cornerDrive(engine(),15.0,5.0,signal=true).misses().isEmpty())
 }

 @Test fun fastCurveIsNotATurn() {
  assertTrue(cornerDrive(engine(),60.0,17.0,signal=false).misses().isEmpty())
 }

 @Test fun steeringAngleDecidesWhenAvailable() {
  val e=engine()
  e.run(0,Signal.Steering(10.0)) // 운전대 신호가 있는 차량: 작은 각도면 곡선 도로
  assertTrue(cornerDrive(e,15.0,5.0,signal=false).misses().isEmpty())
 }

 // ── 깜빡이 없는 차로 변경(추정) ──
 private fun laneChange(e:CoachEngine,degs:List<Double>,signalAt:Int?=null):List<Step> {
  val out=mutableListOf(e.run(0,Signal.Shift(Gear.D),Signal.Speed(60.0)))
  degs.forEachIndexed { i,d->
   if(signalAt==i) out+=e.run(i+1L,Signal.Turn(TurnSignal.LEFT))
   out+=e.run(i+1L,Signal.Steering(d))
  }
  return out
 }
 private val sCurve=listOf(0.0,6.0,8.0,3.0,-5.0,-6.0,-2.0,0.5)

 @Test fun laneChangeWithoutSignal() {
  val s=laneChange(engine(),sCurve)
  assertEquals(listOf(RuleId.LANE_CHANGE_NO_SIGNAL),s.misses())
 }
 @Test fun laneChangeWithSignalIsFine() { assertTrue(laneChange(engine(),sCurve,signalAt=1).misses().isEmpty()) }
 @Test fun smallCorrectionsAreNotLaneChanges() { assertTrue(laneChange(engine(),listOf(0.0,2.0,-2.0,1.0,0.0)).misses().isEmpty()) }
 @Test fun bigSteeringIsNotLaneChange() { assertTrue(laneChange(engine(),listOf(0.0,40.0,10.0,-20.0,0.0)).misses().isEmpty()) }

 // ── 회전교차로 ──
 private val roundaboutCenter=GeoPoint(33.52,126.52)
 private val roundabout=Zone("ra",ZoneKind.ROUNDABOUT,"회전",ZoneShape.Circle(roundaboutCenter,25.0))
 private fun around(deg:Double)=off(roundaboutCenter,15*sin(Math.toRadians(deg)),15*cos(Math.toRadians(deg)))

 @Test fun roundaboutApproachExplainsDirection() {
  val e=engine(roundabout)
  e.run(0,Signal.Shift(Gear.D),Signal.Speed(20.0))
  val s=e.run(1,Signal.Position(off(roundaboutCenter,-50.0,0.0)))
  assertEquals(listOf(RuleId.ROUNDABOUT_ENTER),s.alerts.map { it.rule })
 }

 @Test fun clockwiseIsWrongWay() {
  val e=engine(roundabout)
  e.run(0,Signal.Shift(Gear.D),Signal.Speed(20.0),Signal.Position(off(roundaboutCenter,-50.0,0.0)))
  val steps=listOf(-90.0,-135.0,-180.0,135.0).mapIndexed { i,a->e.run(i+1L,Signal.Position(around(a))) }
  assertTrue(RuleId.ROUNDABOUT_WRONG_WAY in steps.rules())
  assertEquals(listOf(RuleId.ROUNDABOUT_WRONG_WAY),steps.misses())
 }

 @Test fun counterClockwiseIsFineAndNotATurn() {
  val e=engine(roundabout)
  e.run(0,Signal.Shift(Gear.D),Signal.Speed(15.0),Signal.Position(off(roundaboutCenter,-50.0,0.0)))
  val steps=listOf(-90.0,-45.0,0.0,45.0,90.0,135.0).mapIndexed { i,a->e.run(i+1L,Signal.Position(around(a))) }
  assertTrue(steps.misses().isEmpty())
 }

 // ── 횡단보도 ──
 private val school=Zone("s",ZoneKind.SCHOOL_ZONE,"초",ZoneShape.Circle(base,300.0),limitKmh=30)
 private val crosswalk=Zone("x",ZoneKind.CROSSWALK,"횡단보도",ZoneShape.Circle(off(base,100.0,0.0),6.0))

 private fun passCrosswalk(e:CoachEngine,speeds:List<Double>):List<Step> {
  val out=mutableListOf(e.run(0,Signal.Shift(Gear.D),Signal.Speed(speeds.first()),Signal.Position(off(base,40.0,0.0))))
  speeds.forEachIndexed { i,v-> out+=e.run(i+1L,Signal.Speed(v),Signal.Position(off(base,60.0+i*10.0,0.0))) }
  out+=e.run(50,Signal.Position(off(base,200.0,0.0)))
  return out
 }

 @Test fun schoolZoneUnsignalizedCrosswalkMustAlwaysStop() {
  val steps=passCrosswalk(engine(school,crosswalk),listOf(25.0,25.0,20.0,20.0,20.0,25.0))
  assertTrue(RuleId.SCHOOL_CROSSWALK in steps.rules())
  assertEquals(listOf(RuleId.SCHOOL_CROSSWALK_NO_STOP),steps.misses())
 }

 @Test fun stoppingAtSchoolCrosswalkIsFine() {
  val steps=passCrosswalk(engine(school,crosswalk),listOf(25.0,10.0,0.0,8.0,15.0,25.0))
  assertTrue(steps.misses().isEmpty())
 }

 @Test fun signalizedCrosswalkInSchoolZoneHasNoAlwaysStopRule() {
  val steps=passCrosswalk(engine(school,crosswalk.copy(signalized=true)),listOf(25.0,25.0,25.0,25.0,25.0,25.0))
  assertFalse(RuleId.SCHOOL_CROSSWALK in steps.rules()); assertTrue(steps.misses().isEmpty())
 }

 @Test fun crosswalkRightAfterRightTurnIsAnnounced() {
  val e=engine(crosswalk)
  e.run(0,Signal.Shift(Gear.D),Signal.Speed(20.0),Signal.Position(base),Signal.Turn(TurnSignal.RIGHT))
  e.run(3,Signal.Turn(TurnSignal.OFF))
  val s=e.run(5,Signal.Position(off(base,70.0,0.0)))
  assertEquals(RuleId.CROSSWALK_AFTER_TURN,s.alerts.single().rule)
 }

 @Test fun ordinaryCrosswalkWithoutTurnIsSilent() {
  val e=engine(crosswalk)
  e.run(0,Signal.Shift(Gear.D),Signal.Speed(40.0),Signal.Position(base))
  assertTrue(e.run(5,Signal.Position(off(base,70.0,0.0))).alerts.isEmpty())
 }

 // ── 어린이 통학버스 ──
 @Test fun schoolBusTipOnFirstSchoolZoneOnly() {
  val other=school.copy(id="s2",shape=ZoneShape.Circle(off(base,2000.0,0.0),100.0))
  val e=engine(school,other)
  e.run(0,Signal.Shift(Gear.D),Signal.Speed(20.0))
  assertEquals(listOf(RuleId.SCHOOL_ZONE_ENTER,RuleId.SCHOOL_BUS_TIP),e.run(1,Signal.Position(base)).alerts.map { it.rule })
  assertEquals(listOf(RuleId.SCHOOL_ZONE_ENTER),e.run(2,Signal.Position(off(base,2000.0,0.0))).alerts.map { it.rule })
 }

 // ── 주정차 금지 지점 ──
 @Test fun parkingNextToHydrantIsUrgentAndDoubled() {
  val hydrant=Zone("h",ZoneKind.NO_STOPPING,"소화전",ZoneShape.Circle(base,5.0),reason=NoStopReason.HYDRANT)
  val corner=Zone("c",ZoneKind.NO_STOPPING,"모퉁이",ZoneShape.Circle(base,5.0),reason=NoStopReason.CORNER)
  val e=engine(corner,hydrant)
  e.run(0,Signal.Shift(Gear.D),Signal.Speed(10.0),Signal.Position(base))
  val p=e.run(5,Signal.Speed(0.0),Signal.Shift(Gear.P))
  assertEquals(Msg.NO_STOPPING_HYDRANT,p.alerts.single().msg)
  assertEquals(Fines.HYDRANT_PARKING,p.nearMisses.single().fineKrw)
  assertEquals("HYDRANT",p.nearMisses.single().detail)
 }

 @Test fun busStopAndCrosswalkMessages() {
  for((reason,msg) in listOf(NoStopReason.BUS_STOP to Msg.NO_STOPPING_BUS_STOP,NoStopReason.CROSSWALK to Msg.NO_STOPPING_CROSSWALK,NoStopReason.CORNER to Msg.NO_STOPPING_CORNER)) {
   val e=engine(Zone("n",ZoneKind.NO_STOPPING,"n",ZoneShape.Circle(base,10.0),reason=reason))
   e.run(0,Signal.Shift(Gear.D),Signal.Speed(10.0),Signal.Position(base))
   val p=e.run(5,Signal.Shift(Gear.P))
   assertEquals(msg,p.alerts.single().msg); assertEquals(Fines.ILLEGAL_PARKING,p.nearMisses.single().fineKrw)
  }
 }

 // ── 좌회전 ──
 @Test fun unprotectedLeftExplainedWhenSignallingLeft() {
  val e=engine(Zone("l",ZoneKind.UNPROTECTED_LEFT,"비보호",ZoneShape.Circle(base,20.0)))
  e.run(0,Signal.Shift(Gear.D),Signal.Speed(20.0),Signal.Position(off(base,-40.0,0.0)))
  assertEquals(listOf(RuleId.UNPROTECTED_LEFT),e.run(1,Signal.Turn(TurnSignal.LEFT)).alerts.map { it.rule })
 }

 @Test fun leftTurnTipOnceWithoutZone() {
  val e=engine()
  e.run(0,Signal.Shift(Gear.D),Signal.Speed(20.0),Signal.Position(base))
  assertEquals(listOf(RuleId.LEFT_TURN_TIP),e.run(1,Signal.Turn(TurnSignal.LEFT)).alerts.map { it.rule })
  e.run(2,Signal.Turn(TurnSignal.OFF))
  assertTrue(e.run(3,Signal.Turn(TurnSignal.LEFT)).alerts.isEmpty())
 }

 // ── 하이패스 ──
 private val toll=Zone("t",ZoneKind.TOLL_PLAZA,"요금소",ZoneShape.Circle(base,30.0),limitKmh=30)
 private fun approachToll(hipass:Boolean?):List<Step> {
  val e=engine(toll).also { it.hipass=hipass }
  return listOf(e.run(0,Signal.Shift(Gear.D),Signal.Speed(80.0),Signal.Position(off(base,-250.0,0.0))),
   e.run(10,Signal.Speed(45.0),Signal.Position(base)))
 }
 @Test fun tollMessageDependsOnHipass() {
  assertEquals(Msg.TOLL_HIPASS_UNKNOWN,approachToll(null)[0].alerts.single().msg)
  assertEquals(Msg.TOLL_HIPASS_NO,approachToll(false)[0].alerts.single().msg)
  assertEquals(Msg.TOLL_HIPASS_YES,approachToll(true)[0].alerts.single().msg)
 }
 @Test fun hipassLaneSpeedWarning() {
  assertEquals(listOf(RuleId.TOLL_SLOW_DOWN),approachToll(true)[1].alerts.map { it.rule })
  assertTrue(approachToll(null)[1].alerts.isEmpty())
 }

 @Test fun noStoppingNeedsReasonInJson() {
  assertThrows(IllegalArgumentException::class.java) {
   ZoneJson.parse("""{"zones":[{"id":"n","kind":"NO_STOPPING","circle":{"lat":1,"lng":1,"radiusM":5}}]}""")
  }
  val z=ZoneJson.parse("""{"zones":[{"id":"n","kind":"NO_STOPPING","reason":"BUS_STOP","circle":{"lat":1,"lng":1,"radiusM":10}},
   {"id":"x","kind":"CROSSWALK","signalized":true,"schoolZone":true,"circle":{"lat":1,"lng":1,"radiusM":6}}]}""").zones
  assertEquals(NoStopReason.BUS_STOP,z[0].reason); assertTrue(z[1].signalized); assertTrue(z[1].schoolZone)
 }

 @Test fun angleHelpers() {
  assertEquals(90.0,CoachEngine.bearing(base,off(base,0.0,100.0)),0.5)
  assertEquals(-20.0,CoachEngine.angleDiff(10.0,350.0),1e-9)
  assertEquals(30.0,CoachEngine.angleDiff(350.0,20.0),1e-9)
 }
}
