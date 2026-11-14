package com.goodnight.souprules.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.DayOfWeek

class CoachEngineTest {
 /** 2026-11-16(월) 08:30 KST */
 private val t0=Instant.parse("2026-11-15T23:30:00Z")
 private fun at(sec:Long)=t0.plusSeconds(sec)

 private val schoolCenter=GeoPoint(33.5000,126.5000)
 private val school=Zone("s1",ZoneKind.SCHOOL_ZONE,"테스트초",ZoneShape.Circle(schoolCenter,100.0),limitKmh=30)
 private val turnCenter=GeoPoint(33.5100,126.5100)
 private val rightTurn=Zone("r1",ZoneKind.RIGHT_TURN,"테스트 교차로",ZoneShape.Circle(turnCenter,20.0))
 private val busLane=Zone("b1",ZoneKind.BUS_LANE,"테스트로",ZoneShape.Corridor(listOf(GeoPoint(33.52,126.52),GeoPoint(33.53,126.52)),15.0),
  Schedule(listOf(TimeWindow(TimeWindow.parseDays("MON-FRI"),LocalTime.of(7,0),LocalTime.of(9,0)))))
 private val cctv=Zone("c1",ZoneKind.PARKING_ENFORCEMENT,"테스트 CCTV",ZoneShape.Circle(GeoPoint(33.54,126.54),30.0),graceMinutes=5)
 private val far=GeoPoint(33.40,126.40)

 private fun engine(vararg zones:Zone)=CoachEngine(ZoneIndex(zones.toList()))

 private fun CoachEngine.drive(sec:Long,vararg signals:Signal):Step {
  var alerts=listOf<Alert>(); var misses=listOf<NearMiss>(); var summary=false
  signals.forEach { val s=accept(it,at(sec)); alerts=alerts+s.alerts; misses=misses+s.nearMisses; summary=summary||s.summaryDue }
  return Step(alerts,misses,summary)
 }

 @Test fun schoolZoneEntryAnnouncesLimit() {
  val e=engine(school)
  e.drive(0,Signal.Shift(Gear.D),Signal.Speed(25.0),Signal.Position(far))
  val s=e.drive(1,Signal.Position(GeoPoint(33.5005,126.5)))
  assertEquals(listOf(RuleId.SCHOOL_ZONE_ENTER),s.alerts.map { it.rule })
  assertEquals("30",s.alerts[0].args["limit"])
  assertEquals(Severity.INFO,s.alerts[0].severity)
  // 같은 방문에서는 다시 알리지 않습니다.
  assertTrue(e.drive(2,Signal.Position(GeoPoint(33.5001,126.5))).alerts.isEmpty())
 }

 @Test fun schoolZoneSpeedingWarnsAfterGraceAndLogsOneNearMissWithMaxSpeed() {
  val e=engine(school)
  e.drive(0,Signal.Shift(Gear.D),Signal.Speed(28.0),Signal.Position(schoolCenter))
  assertTrue(e.drive(1,Signal.Speed(42.0)).alerts.none { it.rule==RuleId.SCHOOL_ZONE_SPEEDING })
  val warn=e.drive(3,Signal.Speed(45.0))
  assertEquals(RuleId.SCHOOL_ZONE_SPEEDING,warn.alerts.single().rule)
  assertEquals(Severity.URGENT,warn.alerts.single().severity)
  assertEquals("45",warn.alerts.single().args["speed"])
  // 10초 안에는 반복하지 않음
  assertTrue(e.drive(5,Signal.Speed(47.0)).alerts.isEmpty())
  val slow=e.drive(6,Signal.Speed(29.0))
  val miss=slow.nearMisses.single()
  assertEquals(RuleId.SCHOOL_ZONE_SPEEDING,miss.rule)
  assertEquals(47,miss.speedKmh)
  assertEquals(70_000,miss.fineKrw)
  // 같은 방문에서 다시 과속해도 기록은 한 번
  e.drive(7,Signal.Speed(50.0)); e.drive(10,Signal.Speed(50.0))
  assertTrue(e.drive(11,Signal.Speed(20.0)).nearMisses.isEmpty())
 }

 @Test fun shortSpeedSpikeIsIgnored() {
  val e=engine(school)
  e.drive(0,Signal.Shift(Gear.D),Signal.Speed(28.0),Signal.Position(schoolCenter))
  e.drive(1,Signal.Speed(40.0))
  val s=e.drive(2,Signal.Speed(28.0))
  assertTrue(s.alerts.isEmpty()); assertTrue(s.nearMisses.isEmpty())
 }

 @Test fun schoolZoneInactiveScheduleDoesNothing() {
  val timed=school.copy(schedule=Schedule(listOf(TimeWindow(DayOfWeek.entries.toSet(),LocalTime.of(20,0),LocalTime.of(21,0)))))
  val e=engine(timed)
  e.drive(0,Signal.Shift(Gear.D),Signal.Speed(50.0),Signal.Position(schoolCenter))
  val s=e.drive(5,Signal.Speed(55.0))
  assertTrue(s.alerts.isEmpty()); assertTrue(s.nearMisses.isEmpty())
 }

 @Test fun rightTurnWithoutStoppingIsNearMiss() {
  val e=engine(rightTurn)
  e.drive(0,Signal.Shift(Gear.D),Signal.Speed(30.0),Signal.Position(GeoPoint(33.5097,126.5100)))
  val warn=e.drive(1,Signal.Turn(TurnSignal.RIGHT))
  assertEquals(listOf(RuleId.RIGHT_TURN_ZONE),warn.alerts.map { it.rule })
  e.drive(2,Signal.Speed(14.0),Signal.Position(turnCenter))
  val done=e.drive(4,Signal.Speed(16.0),Signal.Turn(TurnSignal.OFF))
  assertEquals(RuleId.RIGHT_TURN_NO_STOP,done.nearMisses.single().rule)
  assertEquals(60_000,done.nearMisses.single().fineKrw)
  assertTrue(done.alerts.any { it.rule==RuleId.RIGHT_TURN_NO_STOP })
 }

 @Test fun rightTurnWithFullStopIsFine() {
  val e=engine(rightTurn)
  e.drive(0,Signal.Shift(Gear.D),Signal.Speed(30.0),Signal.Position(GeoPoint(33.5097,126.5100)),Signal.Turn(TurnSignal.RIGHT))
  e.drive(2,Signal.Speed(0.0),Signal.Position(turnCenter))
  val done=e.drive(5,Signal.Speed(12.0),Signal.Turn(TurnSignal.OFF))
  assertTrue(done.nearMisses.isEmpty())
 }

 @Test fun signalCancelledBeforeIntersectionIsNotCounted() {
  val e=engine(rightTurn)
  e.drive(0,Signal.Shift(Gear.D),Signal.Speed(30.0),Signal.Position(GeoPoint(33.5097,126.5100)),Signal.Turn(TurnSignal.RIGHT))
  val done=e.drive(2,Signal.Turn(TurnSignal.OFF))
  assertTrue(done.nearMisses.isEmpty())
 }

 @Test fun rightTurnTipOncePerSessionWithoutZoneData() {
  val e=engine()
  e.drive(0,Signal.Shift(Gear.D),Signal.Speed(30.0),Signal.Position(far))
  assertEquals(RuleId.RIGHT_TURN_TIP,e.drive(1,Signal.Turn(TurnSignal.RIGHT)).alerts.single().rule)
  e.drive(5,Signal.Turn(TurnSignal.OFF))
  assertTrue(e.drive(9,Signal.Turn(TurnSignal.RIGHT)).alerts.isEmpty())
 }

 @Test fun busLaneAnnouncedDuringOperatingHoursWithEndTime() {
  val e=engine(busLane)
  e.drive(0,Signal.Shift(Gear.D),Signal.Speed(40.0),Signal.Position(far))
  val s=e.drive(1,Signal.Position(GeoPoint(33.525,126.52)))
  assertEquals(Msg.BUS_LANE_ACTIVE,s.alerts.single().msg)
  assertEquals("09:00",s.alerts.single().args["until"])
 }

 @Test fun busLaneOutsideHoursIsSilent() {
  val e=engine(busLane)
  val evening=Instant.parse("2026-11-16T10:00:00Z") // 19:00 KST
  e.accept(Signal.Shift(Gear.D),evening); e.accept(Signal.Speed(40.0),evening)
  assertTrue(e.accept(Signal.Position(GeoPoint(33.525,126.52)),evening).alerts.isEmpty())
 }

 @Test fun stoppingInBusLaneIsUrgentNearMiss() {
  val e=engine(busLane)
  e.drive(0,Signal.Shift(Gear.D),Signal.Speed(20.0),Signal.Position(GeoPoint(33.525,126.52)))
  val p=e.drive(5,Signal.Speed(0.0),Signal.Shift(Gear.P))
  assertEquals(RuleId.BUS_LANE_STOP,p.alerts.single().rule)
  assertEquals(Severity.URGENT,p.alerts.single().severity)
  assertEquals(Fines.BUS_LANE,p.nearMisses.single().fineKrw)
 }

 @Test fun parkingAtCctvWarnsWithGraceThenRemindsOneMinuteBefore() {
  val e=engine(cctv)
  e.drive(0,Signal.Shift(Gear.D),Signal.Speed(15.0),Signal.Position(GeoPoint(33.54,126.54)))
  val p=e.drive(10,Signal.Speed(0.0),Signal.Shift(Gear.P))
  assertEquals(Msg.PARKING_CCTV,p.alerts.single().msg)
  assertEquals("5",p.alerts.single().args["minutes"])
  assertEquals(RuleId.PARKING_CCTV,p.nearMisses.single().rule)
  assertTrue(e.drive(10+3*60,Signal.Tick).alerts.none { it.rule==RuleId.PARKING_REMINDER })
  assertEquals(RuleId.PARKING_REMINDER,e.drive(10+4*60,Signal.Tick).alerts.single().rule)
  assertTrue(e.drive(10+4*60+5,Signal.Tick).alerts.isEmpty())
 }

 @Test fun parkingInSchoolZoneBeatsOtherZones() {
  val overlap=cctv.copy(shape=ZoneShape.Circle(schoolCenter,50.0))
  val e=engine(school,overlap)
  e.drive(0,Signal.Shift(Gear.D),Signal.Speed(10.0),Signal.Position(schoolCenter))
  val p=e.drive(5,Signal.Speed(0.0),Signal.Shift(Gear.P))
  assertEquals(listOf(RuleId.SCHOOL_ZONE_PARKING),p.alerts.map { it.rule })
  assertEquals(Fines.SCHOOL_ZONE_PARKING,p.nearMisses.single().fineKrw)
 }

 @Test fun firstParkOutsideZonesGivesTipOnce() {
  val e=engine()
  e.drive(0,Signal.Shift(Gear.D),Signal.Speed(30.0),Signal.Position(far))
  assertEquals(RuleId.PARKING_TIP,e.drive(5,Signal.Shift(Gear.P)).alerts.single().rule)
  e.drive(6,Signal.Shift(Gear.D))
  assertTrue(e.drive(9,Signal.Shift(Gear.P)).alerts.isEmpty())
 }

 @Test fun alreadyParkedAtStartupIsSilent() {
  val e=engine(cctv)
  e.drive(0,Signal.Position(GeoPoint(33.54,126.54)))
  val p=e.drive(1,Signal.Shift(Gear.P))
  assertTrue(p.alerts.isEmpty()); assertTrue(p.nearMisses.isEmpty())
 }

 @Test fun summaryDueAfterDrivingThenTenSecondsInPark() {
  val e=engine()
  e.drive(0,Signal.Shift(Gear.D),Signal.Speed(40.0),Signal.Position(far))
  e.drive(40,Signal.Speed(40.0))
  e.drive(50,Signal.Speed(0.0),Signal.Shift(Gear.P))
  assertFalse(e.drive(55,Signal.Tick).summaryDue)
  assertTrue(e.drive(60,Signal.Tick).summaryDue)
  assertFalse(e.drive(70,Signal.Tick).summaryDue)
 }

 @Test fun shortHopDoesNotTriggerSummary() {
  val e=engine()
  e.drive(0,Signal.Shift(Gear.D),Signal.Speed(10.0))
  e.drive(5,Signal.Shift(Gear.P))
  assertFalse(e.drive(30,Signal.Tick).summaryDue)
 }

 @Test fun speedingEndsWhenParked() {
  val e=engine(school)
  e.drive(0,Signal.Shift(Gear.D),Signal.Speed(40.0),Signal.Position(schoolCenter))
  e.drive(3,Signal.Speed(40.0))
  val p=e.drive(4,Signal.Shift(Gear.P))
  assertTrue(p.nearMisses.any { it.rule==RuleId.SCHOOL_ZONE_SPEEDING })
 }
}
