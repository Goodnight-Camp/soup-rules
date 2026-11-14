package com.goodnight.souprules.core

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class RuleId {
 RIGHT_TURN_TIP, RIGHT_TURN_ZONE, RIGHT_TURN_NO_STOP,
 SCHOOL_ZONE_ENTER, SCHOOL_ZONE_SPEEDING, SCHOOL_ZONE_PARKING,
 BUS_LANE_ACTIVE, BUS_LANE_STOP,
 PARKING_CCTV, PARKING_REMINDER, PARKING_TIP,
}

enum class Severity { INFO, WARN, URGENT }

/** 화면·음성 안내 1건. 문구는 [Strings]가 운전자 언어로 만듭니다. */
data class Alert(
 val rule:RuleId,
 val severity:Severity,
 val msg:Msg,
 val args:Map<String,String> =emptyMap(),
 val at:Instant,
 val zoneId:String?=null,
) { val key get()="$rule:${zoneId.orEmpty()}" }

/** 과태료가 나올 뻔한 순간. 금액은 [Fines]의 예상치입니다. */
data class NearMiss(
 val at:Instant,
 val rule:RuleId,
 val zoneId:String?,
 val zoneName:String?,
 val speedKmh:Int?=null,
 val limitKmh:Int?=null,
 val fineKrw:Int=Fines.of(rule,speedKmh,limitKmh),
)

data class Step(val alerts:List<Alert>,val nearMisses:List<NearMiss>,val summaryDue:Boolean) {
 val isEmpty get()=alerts.isEmpty()&&nearMisses.isEmpty()&&!summaryDue
}

data class CoachConfig(
 /** 어린이보호구역은 경계 30m 전부터 안내합니다. */
 val schoolApproachM:Double=30.0,
 /** 우회전 지점은 50m 전부터 봅니다. */
 val rightTurnApproachM:Double=50.0,
 /** 들어간 구역은 이만큼 더 멀어져야 나간 것으로 봅니다(GPS 흔들림). */
 val exitHysteresisM:Double=20.0,
 /** P 위치가 경계에서 이만큼 벗어나도 같은 구역으로 봅니다. */
 val parkToleranceM:Double=5.0,
 /** 이 속도 이하로 떨어지면 '멈춤'으로 봅니다. */
 val stopKmh:Double=2.0,
 /** 이 속도 이상이면 주행 중입니다. */
 val moveKmh:Double=5.0,
 /** 제한속도 초과가 이만큼 이어지면 경고합니다(순간 튐 무시). */
 val speedingGraceSec:Long=2,
 /** 과속 경고 반복 간격. */
 val speedingRepeatSec:Long=10,
 /** 우회전 깜빡이를 켜고 이 시간 안에 끝나지 않으면 판단하지 않습니다. */
 val rightTurnTimeoutSec:Long=60,
 /** 유예 시간을 모르는 단속 구간은 짧게(보수적으로) 잡습니다. */
 val defaultGraceMinutes:Int=1,
 /** P를 이만큼 유지하면 오늘 요약을 냅니다. */
 val summaryAfterParkSec:Long=10,
 /** 이만큼 주행한 뒤의 P만 요약 대상입니다. */
 val minDriveSec:Long=30,
)

/**
 * 한국에만 있는 도로 규칙을 '위반 직전'에 알려 주는 상태 기계.
 * 입력은 실제 차속·깜빡이·기어·위치이고, 출력은 안내(Alert)와 과태료 직전 기록(NearMiss)입니다.
 * 시각은 매 신호에 함께 받으므로 데모 재생과 테스트에서도 같은 규칙이 돕니다.
 */
class CoachEngine(zones:ZoneIndex,private val config:CoachConfig=CoachConfig()) {
 var zones:ZoneIndex=zones
  set(value) { field=value; visits.clear(); turn=null }
 var state=VehicleState(); private set

 private class Visit(val zone:Zone) {
  var announced=false
  var speedingSince:Instant?=null
  var lastSpeedingAlert:Instant?=null
  var maxSpeed=0.0
  /** 이번 과속이 유예 시간을 넘겼는지(순간 튐은 기록하지 않음). */
  var confirmed=false
  var nearMissLogged=false
 }
 private class TurnEpisode(val zone:Zone,val startedAt:Instant,var minSpeed:Double) { var reachedCore=false }

 private val visits=LinkedHashMap<String,Visit>()
 private var turn:TurnEpisode?=null
 private val tipsGiven=mutableSetOf<RuleId>()
 private var parkedAt:Instant?=null
 private var parkedZone:Zone?=null
 private var reminderSent=false
 private var summarized=false
 private var movingSince:Instant?=null
 private var droveEnough=false

 /** 현재 들어가 있는 구역(화면 표시용). */
 val activeZones:List<Zone> get()=visits.values.map { it.zone }

 fun accept(signal:Signal,at:Instant):Step {
  val out=Out()
  val prev=state
  state=when(signal) {
   is Signal.Speed->state.copy(speedKmh=signal.kmh.coerceAtLeast(0.0))
   is Signal.Shift->state.copy(gear=signal.gear)
   is Signal.Turn->state.copy(turn=signal.signal)
   is Signal.Position->state.copy(position=signal.point)
   Signal.Tick->state
  }.copy(at=at)
  if(signal is Signal.Position) updateVisits(at,out)
  if(signal is Signal.Shift && signal.gear!=prev.gear) onShift(prev.gear,signal.gear,at,out)
  if(signal is Signal.Turn && signal.signal==TurnSignal.RIGHT && prev.turn!=TurnSignal.RIGHT) onRightSignal(at,out)
  announceVisits(at,out)
  checkSpeeding(at,out)
  updateTurn(at,out)
  trackDriving(at)
  checkParked(at,out)
  return out.step()
 }

 // ── 구역 출입 ──
 private fun enterMargin(kind:ZoneKind)=when(kind) { ZoneKind.SCHOOL_ZONE->config.schoolApproachM; ZoneKind.RIGHT_TURN->config.rightTurnApproachM; else->0.0 }

 private fun updateVisits(at:Instant,out:Out) {
  val p=state.position?:return
  for(zone in zones.zones) {
   val d=zone.shape.distanceM(p); val margin=enterMargin(zone.kind)
   val visit=visits[zone.id]
   if(visit==null && d<=margin) visits[zone.id]=Visit(zone)
   else if(visit!=null && d>margin+config.exitHysteresisM) { finishSpeeding(visit,at,out); visits.remove(zone.id) }
  }
 }

 private val moving get()=(state.speedKmh?:0.0)>=config.moveKmh && state.gear!=Gear.P

 private fun announceVisits(at:Instant,out:Out) {
  if(state.gear==Gear.P) return
  for(v in visits.values) {
   if(v.announced || !v.zone.schedule.activeAt(at)) continue
   when(v.zone.kind) {
    ZoneKind.SCHOOL_ZONE->{
     val limit=v.zone.limitKmh?:30
     val over=(state.speedKmh?:0.0)>limit
     out.alert(Alert(RuleId.SCHOOL_ZONE_ENTER,if(over) Severity.WARN else Severity.INFO,Msg.SCHOOL_ENTER,mapOf("limit" to "$limit"),at,v.zone.id))
     v.announced=true
    }
    ZoneKind.BUS_LANE->if(moving) {
     val end=v.zone.schedule.endsAt(at)
     out.alert(if(end==null) Alert(RuleId.BUS_LANE_ACTIVE,Severity.INFO,Msg.BUS_LANE_ALL_DAY,emptyMap(),at,v.zone.id)
      else Alert(RuleId.BUS_LANE_ACTIVE,Severity.INFO,Msg.BUS_LANE_ACTIVE,mapOf("until" to hhmm(end)),at,v.zone.id))
     v.announced=true
    }
    else->v.announced=true
   }
  }
 }

 // ── 어린이보호구역 속도 ──
 private fun checkSpeeding(at:Instant,out:Out) {
  val speed=state.speedKmh?:return
  for(v in visits.values) {
   if(v.zone.kind!=ZoneKind.SCHOOL_ZONE) continue
   // 접근 구간(경계 밖 30m)은 안내만 하고, 과속 판단은 구역 안에서만 합니다.
   val inside=state.position?.let { v.zone.shape.distanceM(it)<=0.0 }?:false
   val limit=v.zone.limitKmh?:30
   if(!inside || !v.zone.schedule.activeAt(at) || state.gear==Gear.P || speed<limit+1) { finishSpeeding(v,at,out); continue }
   val since=v.speedingSince?:at.also { v.speedingSince=it }
   v.maxSpeed=maxOf(v.maxSpeed,speed)
   if(Duration.between(since,at).seconds<config.speedingGraceSec) continue
   v.confirmed=true
   val last=v.lastSpeedingAlert
   if(last==null || Duration.between(last,at).seconds>=config.speedingRepeatSec) {
    out.alert(Alert(RuleId.SCHOOL_ZONE_SPEEDING,Severity.URGENT,Msg.SCHOOL_SLOW_DOWN,mapOf("speed" to "${speed.toInt()}","limit" to "$limit"),at,v.zone.id))
    v.lastSpeedingAlert=at
   }
  }
 }

 /** 과속이 끝나면(감속·이탈·P) 그 방문에서 한 번만 최고 속도로 기록합니다. */
 private fun finishSpeeding(v:Visit,at:Instant,out:Out) {
  val since=v.speedingSince?:return
  val confirmed=v.confirmed
  v.speedingSince=null; v.confirmed=false
  if(confirmed && !v.nearMissLogged) {
   v.nearMissLogged=true
   out.miss(NearMiss(since,RuleId.SCHOOL_ZONE_SPEEDING,v.zone.id,v.zone.name,v.maxSpeed.toInt(),v.zone.limitKmh?:30))
  }
  v.maxSpeed=0.0
 }

 // ── 우회전 일시정지 ──
 private fun nearestRightTurn():Zone?=state.position?.let { p->
  zones.within(p,config.rightTurnApproachM,ZoneKind.RIGHT_TURN).minByOrNull { it.shape.distanceM(p) }
 }

 private fun onRightSignal(at:Instant,out:Out) {
  if(state.gear==Gear.P) return
  if(nearestRightTurn()==null && tipsGiven.add(RuleId.RIGHT_TURN_TIP))
   out.alert(Alert(RuleId.RIGHT_TURN_TIP,Severity.INFO,Msg.TIP_RIGHT_TURN,emptyMap(),at))
 }

 private fun updateTurn(at:Instant,out:Out) {
  val p=state.position
  val episode=turn
  if(episode==null) {
   if(state.turn!=TurnSignal.RIGHT || state.gear!=Gear.D || p==null) return
   val zone=nearestRightTurn()?:return
   turn=TurnEpisode(zone,at,state.speedKmh?:0.0).also { it.reachedCore=zone.shape.distanceM(p)<=0.0 }
   tipsGiven+=RuleId.RIGHT_TURN_TIP
   out.alert(Alert(RuleId.RIGHT_TURN_ZONE,Severity.WARN,if(zone.rightTurnSignal) Msg.RIGHT_TURN_ZONE_SIGNAL else Msg.RIGHT_TURN_ZONE,emptyMap(),at,zone.id))
   return
  }
  state.speedKmh?.let { episode.minSpeed=minOf(episode.minSpeed,it) }
  if(p!=null && episode.zone.shape.distanceM(p)<=0.0) episode.reachedCore=true
  val left=p!=null && episode.zone.shape.distanceM(p)>config.rightTurnApproachM+config.exitHysteresisM
  val signalOff=state.turn!=TurnSignal.RIGHT
  if(Duration.between(episode.startedAt,at).seconds>config.rightTurnTimeoutSec) { turn=null; return }
  if(!left && !signalOff && state.gear!=Gear.P) return
  turn=null
  if(episode.reachedCore && episode.minSpeed>config.stopKmh && state.gear!=Gear.P) {
   out.miss(NearMiss(episode.startedAt,RuleId.RIGHT_TURN_NO_STOP,episode.zone.id,episode.zone.name,episode.minSpeed.toInt()))
   out.alert(Alert(RuleId.RIGHT_TURN_NO_STOP,Severity.INFO,Msg.RIGHT_TURN_NO_STOP,emptyMap(),at,episode.zone.id))
  }
 }

 // ── 주정차 ──
 private fun onShift(from:Gear,to:Gear,at:Instant,out:Out) {
  if(to!=Gear.P) { parkedAt=null; parkedZone=null; reminderSent=false; summarized=false; return }
  parkedAt=at; reminderSent=false; summarized=false
  visits.values.forEach { finishSpeeding(it,at,out) }
  // 서비스 시작 시 이미 P였던 경우(UNKNOWN→P)는 주차 안내를 하지 않습니다.
  if(from!=Gear.D && from!=Gear.R && from!=Gear.N) return
  val p=state.position
  val here=p?.let { zones.within(it,config.parkToleranceM).filter { z->z.schedule.activeAt(at) } }.orEmpty()
  val school=here.firstOrNull { it.kind==ZoneKind.SCHOOL_ZONE }
  val bus=here.firstOrNull { it.kind==ZoneKind.BUS_LANE }
  val cctv=here.filter { it.kind==ZoneKind.PARKING_ENFORCEMENT }.minByOrNull { it.graceMinutes?:config.defaultGraceMinutes }
  when {
   school!=null->{
    out.alert(Alert(RuleId.SCHOOL_ZONE_PARKING,Severity.URGENT,Msg.SCHOOL_PARKING,emptyMap(),at,school.id))
    out.miss(NearMiss(at,RuleId.SCHOOL_ZONE_PARKING,school.id,school.name))
   }
   bus!=null->{
    out.alert(Alert(RuleId.BUS_LANE_STOP,Severity.URGENT,Msg.BUS_LANE_STOP,emptyMap(),at,bus.id))
    out.miss(NearMiss(at,RuleId.BUS_LANE_STOP,bus.id,bus.name))
   }
   cctv!=null->{
    val grace=cctv.graceMinutes?:config.defaultGraceMinutes
    out.alert(if(grace<=0) Alert(RuleId.PARKING_CCTV,Severity.URGENT,Msg.PARKING_CCTV_NOW,emptyMap(),at,cctv.id)
     else Alert(RuleId.PARKING_CCTV,Severity.URGENT,Msg.PARKING_CCTV,mapOf("minutes" to "$grace"),at,cctv.id))
    out.miss(NearMiss(at,RuleId.PARKING_CCTV,cctv.id,cctv.name))
    parkedZone=cctv
   }
   tipsGiven.add(RuleId.PARKING_TIP)->out.alert(Alert(RuleId.PARKING_TIP,Severity.INFO,Msg.PARKING_TIP,emptyMap(),at))
  }
 }

 private fun checkParked(at:Instant,out:Out) {
  val since=parkedAt?:return
  if(state.gear!=Gear.P) return
  val zone=parkedZone
  val grace=zone?.let { it.graceMinutes?:config.defaultGraceMinutes }
  if(zone!=null && grace!=null && grace>=2 && !reminderSent && Duration.between(since,at)>=Duration.ofMinutes(grace-1L)) {
   reminderSent=true
   out.alert(Alert(RuleId.PARKING_REMINDER,Severity.URGENT,Msg.PARKING_REMINDER,emptyMap(),at,zone.id))
  }
  if(!summarized && droveEnough && Duration.between(since,at).seconds>=config.summaryAfterParkSec) {
   summarized=true; droveEnough=false; movingSince=null
   out.summary=true
  }
 }

 private fun trackDriving(at:Instant) {
  if(!moving) return
  val since=movingSince?:at.also { movingSince=it }
  if(Duration.between(since,at).seconds>=config.minDriveSec) droveEnough=true
 }

 private class Out {
  val alerts=mutableListOf<Alert>(); val misses=mutableListOf<NearMiss>(); var summary=false
  fun alert(a:Alert) { alerts+=a }
  fun miss(m:NearMiss) { misses+=m }
  fun step()=Step(alerts.sortedByDescending { it.severity },misses.toList(),summary)
 }

 companion object {
  private val HHMM=DateTimeFormatter.ofPattern("HH:mm")
  fun hhmm(t:LocalTime):String=t.format(HHMM)
 }
}
