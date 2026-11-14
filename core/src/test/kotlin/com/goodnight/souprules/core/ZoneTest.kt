package com.goodnight.souprules.core

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime

class ZoneTest {
 @Test fun haversineMatchesKnownDistance() {
  // 위도 0.001도 ≈ 111.2m
  assertEquals(111.2,Geo.distanceM(GeoPoint(33.5,126.5),GeoPoint(33.501,126.5)),0.5)
 }

 @Test fun corridorDistanceUsesNearestSegment() {
  val line=listOf(GeoPoint(33.50,126.50),GeoPoint(33.51,126.50))
  // 중심선에서 동쪽 약 93m (경도 0.001도 × cos 33.5°)
  val d=Geo.distanceToPolylineM(GeoPoint(33.505,126.501),line)
  assertEquals(92.7,d,1.0)
  assertEquals(0.0,ZoneShape.Corridor(line,100.0).distanceM(GeoPoint(33.505,126.501)),0.0)
 }

 @Test fun parseDays() {
  assertEquals(setOf(DayOfWeek.MONDAY,DayOfWeek.TUESDAY,DayOfWeek.WEDNESDAY,DayOfWeek.THURSDAY,DayOfWeek.FRIDAY),TimeWindow.parseDays("MON-FRI"))
  assertEquals(setOf(DayOfWeek.SATURDAY,DayOfWeek.SUNDAY),TimeWindow.parseDays("sat,sun"))
  assertEquals(7,TimeWindow.parseDays("ALL").size)
  assertEquals(setOf(DayOfWeek.FRIDAY,DayOfWeek.SATURDAY,DayOfWeek.SUNDAY,DayOfWeek.MONDAY),TimeWindow.parseDays("FRI-MON"))
 }

 @Test fun overnightWindowSpansMidnight() {
  val w=TimeWindow(setOf(DayOfWeek.FRIDAY),LocalTime.of(22,0),LocalTime.of(6,0))
  assertTrue(w.contains(LocalDateTime.of(2026,11,20,23,0))) // 금
  assertTrue(w.contains(LocalDateTime.of(2026,11,21,5,59))) // 토 새벽
  assertFalse(w.contains(LocalDateTime.of(2026,11,21,6,0)))
  assertFalse(w.contains(LocalDateTime.of(2026,11,19,23,0))) // 목
 }

 @Test fun scheduleUsesKoreanTime() {
  val s=Schedule(listOf(TimeWindow(TimeWindow.parseDays("MON-FRI"),LocalTime.of(7,0),LocalTime.of(9,0))))
  assertTrue(s.activeAt(Instant.parse("2026-11-15T23:30:00Z")))  // 월 08:30 KST
  assertFalse(s.activeAt(Instant.parse("2026-11-16T08:30:00Z"))) // 월 17:30 KST
  assertEquals(LocalTime.of(9,0),s.endsAt(Instant.parse("2026-11-15T23:30:00Z")))
  assertTrue(Schedule.ALWAYS.activeAt(Instant.EPOCH))
 }

 @Test fun parseZoneJson() {
  val idx=ZoneJson.parse("""
   {"version":1,"sample":true,"zones":[
    {"id":"s1","kind":"SCHOOL_ZONE","name":"A초","circle":{"lat":33.5,"lng":126.5,"radiusM":300},
     "schedule":[{"days":"MON-FRI","start":"08:00","end":"20:00"}]},
    {"id":"b1","kind":"BUS_LANE","name":"B로","corridor":{"halfWidthM":12,"points":[[33.5,126.5],[33.51,126.51]]}},
    {"id":"c1","kind":"PARKING_ENFORCEMENT","name":"C","graceMinutes":0,"circle":{"lat":33.6,"lng":126.6,"radiusM":20}},
    {"id":"r1","kind":"RIGHT_TURN","name":"D","rightTurnSignal":true,"sample":false,"circle":{"lat":33.7,"lng":126.7,"radiusM":20}}
   ]}""")
  assertEquals(4,idx.zones.size)
  assertEquals(30,idx.zones[0].limitKmh)
  assertTrue(idx.zones[0].sample)
  assertEquals(1,idx.zones[0].schedule.windows.size)
  assertTrue(idx.zones[1].shape is ZoneShape.Corridor)
  assertEquals(0,idx.zones[2].graceMinutes)
  assertTrue(idx.zones[3].rightTurnSignal)
  assertFalse(idx.zones[3].sample)
  assertEquals(listOf("s1"),idx.within(GeoPoint(33.5,126.5),kind=ZoneKind.SCHOOL_ZONE).map { it.id })
 }

 @Test(expected=IllegalArgumentException::class) fun duplicateIdsRejected() {
  ZoneJson.parse("""{"zones":[{"id":"x","kind":"RIGHT_TURN","circle":{"lat":1,"lng":1,"radiusM":1}},{"id":"x","kind":"RIGHT_TURN","circle":{"lat":1,"lng":1,"radiusM":1}}]}""")
 }

 @Test fun fines() {
  assertEquals(70_000,Fines.schoolZoneSpeeding(5.0))
  assertEquals(100_000,Fines.schoolZoneSpeeding(25.0))
  assertEquals(130_000,Fines.schoolZoneSpeeding(41.0))
  assertEquals(160_000,Fines.schoolZoneSpeeding(61.0))
  assertEquals("120,000",Fines.format(120_000))
 }

 @Test fun aaosMappings() {
  assertEquals(Gear.P,Gear.fromAaos(4)); assertEquals(Gear.D,Gear.fromAaos(8)); assertEquals(Gear.D,Gear.fromAaos(16))
  assertEquals(Gear.R,Gear.fromAaos(2)); assertEquals(Gear.UNKNOWN,Gear.fromAaos(0))
  assertEquals(TurnSignal.RIGHT,TurnSignal.fromAaos(1)); assertEquals(TurnSignal.LEFT,TurnSignal.fromAaos(2)); assertEquals(TurnSignal.OFF,TurnSignal.fromAaos(0))
 }
}
