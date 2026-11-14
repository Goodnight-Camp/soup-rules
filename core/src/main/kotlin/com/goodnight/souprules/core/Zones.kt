package com.goodnight.souprules.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime

enum class ZoneKind {
 /** 어린이보호구역: 제한속도(보통 30km/h), 주정차 금지. */
 SCHOOL_ZONE,
 /** 버스전용차로 구간: 운영 시간에 통행·정차 금지. */
 BUS_LANE,
 /** 주정차 단속 CCTV 구간. */
 PARKING_ENFORCEMENT,
 /** 횡단보도가 있는 우회전 지점(교차로). */
 RIGHT_TURN,
}

sealed interface ZoneShape {
 /** 구역 경계까지의 거리. 안이면 0. */
 fun distanceM(p:GeoPoint):Double

 data class Circle(val center:GeoPoint,val radiusM:Double):ZoneShape {
  override fun distanceM(p:GeoPoint)=maxOf(0.0,Geo.distanceM(p,center)-radiusM)
 }
 /** 도로 중심선 + 반폭. 버스전용차로·단속 구간처럼 길쭉한 구역. */
 data class Corridor(val points:List<GeoPoint>,val halfWidthM:Double):ZoneShape {
  init { require(points.isNotEmpty()) { "구간 좌표가 없습니다" } }
  override fun distanceM(p:GeoPoint)=maxOf(0.0,Geo.distanceToPolylineM(p,points)-halfWidthM)
 }
}

/** 요일 + 시각 구간. end<start면 자정을 넘깁니다(예: 22:00~06:00). start==end면 그 요일 종일. */
data class TimeWindow(val days:Set<DayOfWeek>,val start:LocalTime,val end:LocalTime) {
 fun contains(t:LocalDateTime):Boolean {
  val day=t.dayOfWeek; val time=t.toLocalTime()
  return when {
   start==end->day in days
   start<end->day in days && time>=start && time<end
   else->(day in days && time>=start) || (day.minus(1) in days && time<end)
  }
 }
 val allDay get()=start==end

 companion object {
  private val names=DayOfWeek.entries.associateBy { it.name.take(3) }
  /** "MON-FRI", "SAT,SUN", "ALL", "MON-FRI,SUN" */
  fun parseDays(text:String):Set<DayOfWeek> {
   val t=text.trim().uppercase()
   if(t=="ALL"||t=="DAILY"||t.isEmpty()) return DayOfWeek.entries.toSet()
   return t.split(',').flatMap { part->
    val range=part.trim().split('-')
    val a=names[range[0].trim()]?:error("요일을 알 수 없습니다: $part")
    if(range.size==1) listOf(a) else {
     val b=names[range[1].trim()]?:error("요일을 알 수 없습니다: $part")
     generateSequence(a) { if(it==b) null else it.plus(1) }.toList()
    }
   }.toSet()
  }
 }
}

data class Schedule(val windows:List<TimeWindow>) {
 val always get()=windows.isEmpty()
 fun activeAt(at:Instant)=always || activeWindow(at)!=null
 fun activeWindow(at:Instant):TimeWindow?=LocalDateTime.ofInstant(at,KST).let { t->windows.firstOrNull { it.contains(t) } }
 /** 지금 적용 중인 구간의 종료 시각. 상시/종일이면 null. */
 fun endsAt(at:Instant):LocalTime?=activeWindow(at)?.takeUnless { it.allDay }?.end
 companion object { val ALWAYS=Schedule(emptyList()) }
}

data class Zone(
 val id:String,
 val kind:ZoneKind,
 val name:String,
 val shape:ZoneShape,
 val schedule:Schedule=Schedule.ALWAYS,
 /** 어린이보호구역 제한속도. */
 val limitKmh:Int?=null,
 /** 주정차 단속 유예(분). 0이면 즉시 단속(소화전·교차로 모퉁이·버스정류소·횡단보도 등). */
 val graceMinutes:Int?=null,
 /** 우회전 신호등(적색 화살표)이 있는 교차로. */
 val rightTurnSignal:Boolean=false,
 /** 데모용 예시 구역(실제 단속 위치가 아님). */
 val sample:Boolean=false,
)

class ZoneIndex(val zones:List<Zone>) {
 fun within(p:GeoPoint,marginM:Double=0.0,kind:ZoneKind?=null):List<Zone> =
  zones.filter { (kind==null||it.kind==kind) && it.shape.distanceM(p)<=marginM }
 val hasSample get()=zones.any { it.sample }
 companion object { val EMPTY=ZoneIndex(emptyList()) }
}
