package com.goodnight.souprules.core

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.*

enum class RuleId {
 // 우회전·횡단보도
 RIGHT_TURN_TIP, RIGHT_TURN_ZONE, RIGHT_TURN_NO_STOP, CROSSWALK_AFTER_TURN,
 // 어린이보호구역
 SCHOOL_ZONE_ENTER, SCHOOL_ZONE_SPEEDING, SCHOOL_ZONE_PARKING, SCHOOL_CROSSWALK, SCHOOL_CROSSWALK_NO_STOP, SCHOOL_BUS_TIP,
 // 버스전용차로
 BUS_LANE_ACTIVE, BUS_LANE_STOP,
 // 주정차
 PARKING_CCTV, PARKING_REMINDER, PARKING_TIP, NO_STOPPING,
 // 방향지시등
 TURN_NO_SIGNAL, LANE_CHANGE_NO_SIGNAL,
 // 안전띠
 SEATBELT_DRIVER, SEATBELT_PASSENGER,
 // 회전교차로
 ROUNDABOUT_ENTER, ROUNDABOUT_WRONG_WAY,
 // 좌회전
 LEFT_TURN_TIP, UNPROTECTED_LEFT,
 // 요금소
 TOLL_PLAZA, TOLL_SLOW_DOWN,
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

/** 과태료가 나올 뻔한 순간. 금액은 [Fines]의 예상치입니다. [detail]은 규칙별 보조 정보(주정차 금지 사유 등). */
data class NearMiss(
 val at:Instant,
 val rule:RuleId,
 val zoneId:String?,
 val zoneName:String?,
 val speedKmh:Int?=null,
 val limitKmh:Int?=null,
 val detail:String?=null,
 val fineKrw:Int=Fines.of(rule,speedKmh,limitKmh,detail),
)

data class Step(val alerts:List<Alert>,val nearMisses:List<NearMiss>,val summaryDue:Boolean) {
 val isEmpty get()=alerts.isEmpty()&&nearMisses.isEmpty()&&!summaryDue
}

data class CoachConfig(
 /** 어린이보호구역은 경계 30m 전부터 안내합니다. */
 val schoolApproachM:Double=30.0,
 /** 우회전 지점은 50m 전부터 봅니다. */
 val rightTurnApproachM:Double=50.0,
 /** 횡단보도는 40m 전부터 봅니다. */
 val crosswalkApproachM:Double=40.0,
 /** 회전교차로는 바깥 경계 40m 전에 안내합니다. */
 val roundaboutApproachM:Double=40.0,
 /** 요금소는 300m 전에 안내합니다. */
 val tollApproachM:Double=300.0,
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
 /** 우회전 뒤 이 시간 안의 횡단보도는 '우회전 후 횡단보도'로 안내합니다. */
 val afterTurnSec:Long=20,
 /** 유예 시간을 모르는 단속 구간은 짧게(보수적으로) 잡습니다. */
 val defaultGraceMinutes:Int=1,
 /** P를 이만큼 유지하면 오늘 요약을 냅니다. */
 val summaryAfterParkSec:Long=10,
 /** 이만큼 주행한 뒤의 P만 요약 대상입니다. */
 val minDriveSec:Long=30,
 // ── 방향지시등 ──
 /** 이 시간 안에 진행 방향이 이만큼 바뀌면 회전으로 봅니다. */
 val turnWindowSec:Long=12,
 val turnMinDegrees:Double=60.0,
 /** 교차로 회전은 느립니다. 운전대 각도가 없을 때는 이 속도 이하로 돈 경우만 회전으로 봅니다(곡선 도로 제외). */
 val turnMaxKmh:Double=30.0,
 /** 운전대 각도가 있으면 이만큼 넘게 돌린 경우만 회전으로 봅니다. */
 val turnMinSteeringDeg:Double=90.0,
 /** 회전 시작 전 이 시간 안에 깜빡이를 켰으면 사용한 것으로 봅니다. */
 val signalLeadSec:Long=5,
 /** 차로 변경: 이 속도 이상에서 운전대를 한쪽으로 이만큼, 반대로 이만큼 돌렸다가 가운데로 돌아오는 경우. */
 val laneChangeMinKmh:Double=40.0,
 val laneChangeSteeringDeg:Double=4.0,
 val laneChangeWindowSec:Long=6,
 /** 차로 변경 추정은 오탐이 있을 수 있어 자주 말하지 않습니다. */
 val laneChangeCooldownSec:Long=120,
 // ── 안전띠 ──
 val seatbeltMinKmh:Double=10.0,
 val seatbeltRepeatSec:Long=60,
 // ── 회전교차로 ──
 /** 회전교차로 안에서 시계 방향으로 이만큼 돌면 역방향입니다. */
 val roundaboutWrongWayDeg:Double=45.0,
)

/**
 * 한국에만 있는 도로 규칙을 '위반 직전'에 알려 주는 상태 기계.
 * 입력은 실제 차속·깜빡이·기어·위치·운전대·안전띠이고, 출력은 안내(Alert)와 과태료 직전 기록(NearMiss)입니다.
 * 시각은 매 신호에 함께 받으므로 데모 재생과 테스트에서도 같은 규칙이 돕니다.
 */
class CoachEngine(zones:ZoneIndex,private val config:CoachConfig=CoachConfig()) {
 var zones:ZoneIndex=zones
  set(value) { field=value; visits.clear(); turn=null }
 /** 렌터카 회사가 알려 준 하이패스 단말 장착 여부. null이면 모름(Pleos Toll API로는 확인할 수 없음). */
 var hipass:Boolean?=null
 var state=VehicleState(); private set

 private class Visit(val zone:Zone) {
  var announced=false
  // 스쿨존 과속
  var speedingSince:Instant?=null
  var lastSpeedingAlert:Instant?=null
  var maxSpeed=0.0
  var confirmed=false
  var nearMissLogged=false
  // 횡단보도 일시정지
  var minSpeed=Double.MAX_VALUE
  var reachedCore=false
  // 회전교차로 방향
  var lastAngle:Double?=null
  var angleSum=0.0
 }
 private class TurnEpisode(val zone:Zone,val startedAt:Instant,var minSpeed:Double) { var reachedCore=false }
 private data class HeadingSample(val at:Instant,val bearing:Double,val speed:Double)
 private data class SteerSample(val at:Instant,val deg:Double)
 private class SeatState(var occupied:Boolean?=null,var buckled:Boolean?=null)

 private val visits=LinkedHashMap<String,Visit>()
 private var turn:TurnEpisode?=null
 private val tipsGiven=mutableSetOf<RuleId>()
 private var parkedAt:Instant?=null
 private var parkedZone:Zone?=null
 private var reminderSent=false
 private var summarized=false
 private var movingSince:Instant?=null
 private var droveEnough=false
 // 방향지시등
 private var lastSignalOnAt:Instant?=null
 private var lastRightTurnAt:Instant?=null
 private var lastHeadingPoint:GeoPoint?=null
 private var lastHeadingAt:Instant?=null
 private val headings=ArrayDeque<HeadingSample>()
 private var lastTurnAt:Instant?=null
 private val steering=ArrayDeque<SteerSample>()
 private var steeringSeen=false
 /** 마지막 운전대 값(바뀔 때만 들어오므로 유지 값으로 씁니다). */
 private val lastSteer:SteerSample? get()=steering.lastOrNull()
 private var lastLaneChangeAt:Instant?=null
 // 안전띠
 private val seats=HashMap<Int,SeatState>()
 private val beltAlertAt=HashMap<RuleId,Instant>()
 private val beltLogged=mutableSetOf<RuleId>()

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
   is Signal.Steering,is Signal.Seat,Signal.Tick->state
  }.copy(at=at)
  if(state.turn!=TurnSignal.OFF) lastSignalOnAt=at
  when(signal) {
   is Signal.Position->{ updateVisits(at,out); updateHeading(signal.point,at,out) }
   is Signal.Shift->if(signal.gear!=prev.gear) onShift(prev.gear,signal.gear,at,out)
   is Signal.Turn->{
    if(signal.signal==TurnSignal.RIGHT && prev.turn!=TurnSignal.RIGHT) onRightSignal(at,out)
    if(signal.signal==TurnSignal.LEFT && prev.turn!=TurnSignal.LEFT) onLeftSignal(at,out)
    if(prev.turn==TurnSignal.RIGHT && signal.signal!=TurnSignal.RIGHT) lastRightTurnAt=at
   }
   is Signal.Steering->onSteering(signal.degrees,at,out)
   is Signal.Seat->seats.getOrPut(signal.seat) { SeatState() }.also { s-> signal.occupied?.let { s.occupied=it }; signal.buckled?.let { s.buckled=it } }
   else->Unit
  }
  announceVisits(at,out)
  checkSpeeding(at,out)
  updateTurn(at,out)
  trackCrosswalks()
  checkSeatbelts(at,out)
  trackDriving(at)
  checkParked(at,out)
  return out.step()
 }

 // ── 구역 출입 ──
 private fun enterMargin(kind:ZoneKind)=when(kind) {
  ZoneKind.SCHOOL_ZONE->config.schoolApproachM
  ZoneKind.RIGHT_TURN,ZoneKind.UNPROTECTED_LEFT->config.rightTurnApproachM
  ZoneKind.CROSSWALK->config.crosswalkApproachM
  ZoneKind.ROUNDABOUT->config.roundaboutApproachM
  ZoneKind.TOLL_PLAZA->config.tollApproachM
  ZoneKind.BUS_LANE,ZoneKind.PARKING_ENFORCEMENT,ZoneKind.NO_STOPPING->0.0
 }

 private fun updateVisits(at:Instant,out:Out) {
  val p=state.position?:return
  for(zone in zones.zones) {
   val d=zone.shape.distanceM(p); val margin=enterMargin(zone.kind)
   val visit=visits[zone.id]
   if(visit==null && d<=margin) visits[zone.id]=Visit(zone)
   else if(visit!=null && d>margin+config.exitHysteresisM) { finishVisit(visit,at,out); visits.remove(zone.id) }
  }
 }

 private fun finishVisit(v:Visit,at:Instant,out:Out) {
  finishSpeeding(v,at,out)
  if(v.zone.kind==ZoneKind.CROSSWALK && isSchoolCrosswalk(v.zone) && v.reachedCore && v.minSpeed>config.stopKmh) {
   out.miss(NearMiss(at,RuleId.SCHOOL_CROSSWALK_NO_STOP,v.zone.id,v.zone.name,v.minSpeed.toInt()))
   out.alert(Alert(RuleId.SCHOOL_CROSSWALK_NO_STOP,Severity.INFO,Msg.SCHOOL_CROSSWALK_NO_STOP,emptyMap(),at,v.zone.id))
  }
 }

 private val moving get()=(state.speedKmh?:0.0)>=config.moveKmh && state.gear!=Gear.P

 /** 신호 없는 횡단보도 + 어린이보호구역(데이터 표시 또는 SCHOOL_ZONE 안). */
 private fun isSchoolCrosswalk(z:Zone):Boolean {
  if(z.kind!=ZoneKind.CROSSWALK || z.signalized) return false
  if(z.schoolZone) return true
  val c=(z.shape as? ZoneShape.Circle)?.center ?: (z.shape as? ZoneShape.Corridor)?.points?.first() ?: return false
  return zones.within(c,0.0,ZoneKind.SCHOOL_ZONE).isNotEmpty()
 }

 private fun recentRightTurn(at:Instant)=lastRightTurnAt?.let { Duration.between(it,at).seconds<=config.afterTurnSec } ?: false

 private fun announceVisits(at:Instant,out:Out) {
  if(state.gear==Gear.P) return
  for(v in visits.values) {
   if(v.announced || !v.zone.schedule.activeAt(at)) continue
   when(v.zone.kind) {
    ZoneKind.SCHOOL_ZONE->{
     val limit=v.zone.limitKmh?:30
     val over=(state.speedKmh?:0.0)>limit
     out.alert(Alert(RuleId.SCHOOL_ZONE_ENTER,if(over) Severity.WARN else Severity.INFO,Msg.SCHOOL_ENTER,mapOf("limit" to "$limit"),at,v.zone.id))
     // 어린이 통학버스: 차량이 버스를 알아볼 수 없으므로 스쿨존 첫 진입 때 규칙만 한 번 설명합니다.
     if(tipsGiven.add(RuleId.SCHOOL_BUS_TIP)) out.alert(Alert(RuleId.SCHOOL_BUS_TIP,Severity.INFO,Msg.SCHOOL_BUS_TIP,emptyMap(),at))
     v.announced=true
    }
    ZoneKind.BUS_LANE->if(moving) {
     val end=v.zone.schedule.endsAt(at)
     out.alert(if(end==null) Alert(RuleId.BUS_LANE_ACTIVE,Severity.INFO,Msg.BUS_LANE_ALL_DAY,emptyMap(),at,v.zone.id)
      else Alert(RuleId.BUS_LANE_ACTIVE,Severity.INFO,Msg.BUS_LANE_ACTIVE,mapOf("until" to hhmm(end)),at,v.zone.id))
     v.announced=true
    }
    ZoneKind.CROSSWALK->when {
     isSchoolCrosswalk(v.zone)->if(moving) {
      out.alert(Alert(RuleId.SCHOOL_CROSSWALK,Severity.WARN,Msg.SCHOOL_CROSSWALK_STOP,emptyMap(),at,v.zone.id)); v.announced=true
     }
     // 우회전 직후 횡단보도: 보행자가 있으면 일시정지(차량은 보행자를 볼 수 없어 안내만 합니다).
     recentRightTurn(at) && (state.speedKmh?:0.0)>=10.0->{
      out.alert(Alert(RuleId.CROSSWALK_AFTER_TURN,Severity.WARN,Msg.CROSSWALK_AFTER_TURN,emptyMap(),at,v.zone.id)); v.announced=true
     }
    }
    ZoneKind.ROUNDABOUT->if(moving) {
     out.alert(Alert(RuleId.ROUNDABOUT_ENTER,Severity.WARN,Msg.ROUNDABOUT_ENTER,emptyMap(),at,v.zone.id)); v.announced=true
    }
    ZoneKind.UNPROTECTED_LEFT->if(state.turn==TurnSignal.LEFT) {
     tipsGiven+=RuleId.LEFT_TURN_TIP
     out.alert(Alert(RuleId.UNPROTECTED_LEFT,Severity.INFO,Msg.UNPROTECTED_LEFT,emptyMap(),at,v.zone.id)); v.announced=true
    }
    ZoneKind.TOLL_PLAZA->if(moving) {
     val msg=when(hipass) { true->Msg.TOLL_HIPASS_YES; false->Msg.TOLL_HIPASS_NO; null->Msg.TOLL_HIPASS_UNKNOWN }
     out.alert(Alert(RuleId.TOLL_PLAZA,Severity.INFO,msg,emptyMap(),at,v.zone.id)); v.announced=true
    }
    else->v.announced=true
   }
  }
  checkTollSpeed(at,out)
  checkRoundabouts(at,out)
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

 // ── 횡단보도 일시정지(스쿨존 신호 없는 횡단보도) ──
 private fun trackCrosswalks() {
  val p=state.position
  for(v in visits.values) {
   if(v.zone.kind!=ZoneKind.CROSSWALK) continue
   state.speedKmh?.let { v.minSpeed=minOf(v.minSpeed,it) }
   if(p!=null && v.zone.shape.distanceM(p)<=0.0) v.reachedCore=true
  }
 }

 // ── 요금소 ──
 private fun checkTollSpeed(at:Instant,out:Out) {
  if(hipass!=true) return
  val p=state.position?:return
  val speed=state.speedKmh?:return
  for(v in visits.values) {
   if(v.zone.kind!=ZoneKind.TOLL_PLAZA || v.nearMissLogged) continue
   val limit=v.zone.limitKmh?:30
   if(v.zone.shape.distanceM(p)<=0.0 && speed>=limit+10) {
    v.nearMissLogged=true
    out.alert(Alert(RuleId.TOLL_SLOW_DOWN,Severity.WARN,Msg.TOLL_SLOW_DOWN,mapOf("limit" to "$limit"),at,v.zone.id))
   }
  }
 }

 // ── 회전교차로 방향(한국: 반시계) ──
 private fun checkRoundabouts(at:Instant,out:Out) {
  val p=state.position?:return
  for(v in visits.values) {
   val z=v.zone
   if(z.kind!=ZoneKind.ROUNDABOUT) continue
   val c=(z.shape as? ZoneShape.Circle)?.center?:continue
   if(z.shape.distanceM(p)>0.0) { v.lastAngle=null; continue }
   val k=cos(Math.toRadians(c.lat))
   val x=Math.toRadians(p.lng-c.lng)*k; val y=Math.toRadians(p.lat-c.lat)
   if(hypot(x,y)==0.0) continue
   val angle=Math.toDegrees(atan2(y,x))
   v.lastAngle?.let { v.angleSum+=angleDiff(it,angle) }
   v.lastAngle=angle
   // 수학 좌표(동=x, 북=y)에서 각이 줄면 시계 방향입니다.
   if(v.angleSum<=-config.roundaboutWrongWayDeg && !v.nearMissLogged) {
    v.nearMissLogged=true
    out.alert(Alert(RuleId.ROUNDABOUT_WRONG_WAY,Severity.URGENT,Msg.ROUNDABOUT_WRONG_WAY,emptyMap(),at,z.id))
    out.miss(NearMiss(at,RuleId.ROUNDABOUT_WRONG_WAY,z.id,z.name))
   }
  }
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

 private fun onLeftSignal(at:Instant,out:Out) {
  if(state.gear==Gear.P) return
  val p=state.position
  val unprotected=p!=null && zones.within(p,config.rightTurnApproachM,ZoneKind.UNPROTECTED_LEFT).isNotEmpty()
  if(!unprotected && tipsGiven.add(RuleId.LEFT_TURN_TIP))
   out.alert(Alert(RuleId.LEFT_TURN_TIP,Severity.INFO,Msg.TIP_LEFT_TURN,emptyMap(),at))
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
  if(episode.reachedCore) lastRightTurnAt=at
  if(episode.reachedCore && episode.minSpeed>config.stopKmh && state.gear!=Gear.P) {
   out.miss(NearMiss(episode.startedAt,RuleId.RIGHT_TURN_NO_STOP,episode.zone.id,episode.zone.name,episode.minSpeed.toInt()))
   out.alert(Alert(RuleId.RIGHT_TURN_NO_STOP,Severity.INFO,Msg.RIGHT_TURN_NO_STOP,emptyMap(),at,episode.zone.id))
  }
 }

 // ── 깜빡이 없는 회전 (진행 방향 변화) ──
 private fun signalUsedSince(t:Instant)=state.turn!=TurnSignal.OFF || (lastSignalOnAt?.let { !it.isBefore(t) } ?: false)

 private fun updateHeading(p:GeoPoint,at:Instant,out:Out) {
  val speed=state.speedKmh?:return
  val last=lastHeadingPoint; val lastAt=lastHeadingAt
  if(last==null || lastAt==null) { lastHeadingPoint=p; lastHeadingAt=at; return }
  val moved=Geo.distanceM(last,p)
  if(moved<4.0) return
  lastHeadingPoint=p; lastHeadingAt=at
  // GPS 튐(속도로 갈 수 없는 거리)은 방향 기록을 버립니다. 튄 점과의 방위가 가짜 U턴으로 보이지 않게 합니다.
  val dt=maxOf(1.0,Duration.between(lastAt,at).toMillis()/1000.0)
  if(moved>maxOf(30.0,speed/3.6*dt*3)) { headings.clear(); return }
  if(state.gear!=Gear.D) { headings.clear(); return }
  headings.addLast(HeadingSample(at,bearing(last,p),speed))
  while(headings.isNotEmpty() && Duration.between(headings.first().at,at).seconds>config.turnWindowSec) headings.removeFirst()
  // 회전교차로 안은 방향이 계속 바뀌므로 회전 판단에서 뺍니다(진출 깜빡이는 진입 안내에서 설명).
  if(visits.values.any { it.zone.kind==ZoneKind.ROUNDABOUT }) { headings.clear(); return }
  if(headings.size<2) return
  val change=angleDiff(headings.first().bearing,headings.last().bearing)
  if(abs(change)<config.turnMinDegrees) return
  if(lastTurnAt?.let { Duration.between(it,at).seconds<config.turnWindowSec } == true) return
  val start=headings.first().at
  val slow=headings.minOf { it.speed }<=config.turnMaxKmh
  // 운전대 값은 바뀔 때만 오므로, 창 이전의 마지막 값도 그 구간 동안 유지된 것으로 봅니다.
  val steerMax=(steering.filter { !it.at.isBefore(start) }+listOfNotNull(lastSteer)).maxOfOrNull { abs(it.deg) }
  val isIntersectionTurn=if(steeringSeen && steerMax!=null) steerMax>=config.turnMinSteeringDeg else slow
  headings.clear(); lastTurnAt=at
  if(!isIntersectionTurn) return
  if(change>0) lastRightTurnAt=at
  // 우회전 지점 에피소드 중이면 그쪽에서 판단합니다.
  if(signalUsedSince(start.minusSeconds(config.signalLeadSec))) return
  out.miss(NearMiss(start,RuleId.TURN_NO_SIGNAL,null,null))
  out.alert(Alert(RuleId.TURN_NO_SIGNAL,Severity.INFO,Msg.TURN_NO_SIGNAL,emptyMap(),at))
 }

 // ── 깜빡이 없는 차로 변경 (운전대 S자, 추정) ──
 private fun onSteering(deg:Double,at:Instant,out:Out) {
  steeringSeen=true
  steering.addLast(SteerSample(at,deg))
  while(steering.isNotEmpty() && Duration.between(steering.first().at,at).seconds>maxOf(config.laneChangeWindowSec,config.turnWindowSec)) steering.removeFirst()
  if((state.speedKmh?:0.0)<config.laneChangeMinKmh || state.gear!=Gear.D) return
  if(abs(deg)>1.5) return // 가운데로 돌아온 순간에 판단
  val window=steering.filter { Duration.between(it.at,at).seconds<=config.laneChangeWindowSec }
  val t=config.laneChangeSteeringDeg
  val firstHigh=window.indexOfFirst { abs(it.deg)>=t }.takeIf { it>=0 }?:return
  val sign=sign(window[firstHigh].deg)
  val opposite=window.drop(firstHigh+1).any { sign(it.deg)==-sign && abs(it.deg)>=t }
  if(!opposite) return
  // 큰 각도(교차로 회전·급커브)는 차로 변경이 아닙니다.
  if(window.any { abs(it.deg)>=config.turnMinSteeringDeg/3 }) return
  if(lastLaneChangeAt?.let { Duration.between(it,at).seconds<config.laneChangeCooldownSec } == true) { steering.clear(); steering.addLast(SteerSample(at,deg)); return }
  val start=window[firstHigh].at
  steering.clear(); steering.addLast(SteerSample(at,deg))
  if(signalUsedSince(start.minusSeconds(3))) return
  lastLaneChangeAt=at
  out.miss(NearMiss(start,RuleId.LANE_CHANGE_NO_SIGNAL,null,null))
  out.alert(Alert(RuleId.LANE_CHANGE_NO_SIGNAL,Severity.INFO,Msg.LANE_CHANGE_NO_SIGNAL,emptyMap(),at))
 }

 // ── 안전띠(전 좌석) ──
 private fun checkSeatbelts(at:Instant,out:Out) {
  if((state.speedKmh?:0.0)<config.seatbeltMinKmh || state.gear==Gear.P) return
  val driver=seats[0]?.buckled==false
  // 동승석은 착석이 확인된 좌석만 봅니다(빈 좌석의 안전띠 센서는 '미착용'으로 나옵니다).
  val passenger=seats.any { (i,s)->i!=0 && s.occupied==true && s.buckled==false }
  fun remind(rule:RuleId,msg:Msg,severity:Severity) {
   val last=beltAlertAt[rule]
   if(last==null || Duration.between(last,at).seconds>=config.seatbeltRepeatSec) {
    beltAlertAt[rule]=at
    out.alert(Alert(rule,severity,msg,emptyMap(),at))
   }
   if(beltLogged.add(rule)) out.miss(NearMiss(at,rule,null,null))
  }
  if(driver) remind(RuleId.SEATBELT_DRIVER,Msg.SEATBELT_DRIVER,Severity.URGENT)
  if(passenger) remind(RuleId.SEATBELT_PASSENGER,Msg.SEATBELT_PASSENGER,Severity.WARN)
 }

 // ── 주정차 ──
 private fun onShift(from:Gear,to:Gear,at:Instant,out:Out) {
  if(to!=Gear.P) { parkedAt=null; parkedZone=null; reminderSent=false; summarized=false; return }
  parkedAt=at; reminderSent=false; summarized=false
  // 안전띠 기록은 운행(P까지) 단위입니다.
  beltLogged.clear()
  visits.values.forEach { finishSpeeding(it,at,out) }
  // 서비스 시작 시 이미 P였던 경우(UNKNOWN→P)는 주차 안내를 하지 않습니다.
  if(from!=Gear.D && from!=Gear.R && from!=Gear.N) return
  val p=state.position
  val here=p?.let { zones.within(it,config.parkToleranceM).filter { z->z.schedule.activeAt(at) } }.orEmpty()
  val school=here.firstOrNull { it.kind==ZoneKind.SCHOOL_ZONE }
  val noStop=here.filter { it.kind==ZoneKind.NO_STOPPING }.maxByOrNull { Fines.of(RuleId.NO_STOPPING,detail=it.reason?.name) }
  val bus=here.firstOrNull { it.kind==ZoneKind.BUS_LANE }
  val cctv=here.filter { it.kind==ZoneKind.PARKING_ENFORCEMENT }.minByOrNull { it.graceMinutes?:config.defaultGraceMinutes }
  when {
   school!=null->{
    out.alert(Alert(RuleId.SCHOOL_ZONE_PARKING,Severity.URGENT,Msg.SCHOOL_PARKING,emptyMap(),at,school.id))
    out.miss(NearMiss(at,RuleId.SCHOOL_ZONE_PARKING,school.id,school.name))
   }
   noStop!=null->{
    val msg=when(noStop.reason) { NoStopReason.HYDRANT->Msg.NO_STOPPING_HYDRANT; NoStopReason.CORNER->Msg.NO_STOPPING_CORNER; NoStopReason.BUS_STOP->Msg.NO_STOPPING_BUS_STOP; else->Msg.NO_STOPPING_CROSSWALK }
    out.alert(Alert(RuleId.NO_STOPPING,Severity.URGENT,msg,emptyMap(),at,noStop.id))
    out.miss(NearMiss(at,RuleId.NO_STOPPING,noStop.id,noStop.name,detail=noStop.reason?.name))
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
  /** -180..180. 양수 = 시계 방향(나침반 방위각 증가) = 오른쪽. */
  fun angleDiff(from:Double,to:Double)=((to-from+540.0)%360.0)-180.0
  /** 나침반 방위각(북=0, 동=90). */
  fun bearing(a:GeoPoint,b:GeoPoint):Double {
   val f1=Math.toRadians(a.lat); val f2=Math.toRadians(b.lat); val dl=Math.toRadians(b.lng-a.lng)
   val y=sin(dl)*cos(f2); val x=cos(f1)*sin(f2)-sin(f1)*cos(f2)*cos(dl)
   return (Math.toDegrees(atan2(y,x))+360.0)%360.0
  }
 }
}
