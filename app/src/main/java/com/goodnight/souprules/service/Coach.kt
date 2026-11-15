package com.goodnight.souprules.service

import android.os.SystemClock
import android.util.Log
import com.goodnight.souprules.core.*
import com.goodnight.souprules.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Instant

data class ShownAlert(val alert:Alert,val shownAtElapsed:Long)

data class CoachUiState(
 val lang:Lang=Lang.EN,
 val vehicle:VehicleState=VehicleState(),
 val current:ShownAlert?=null,
 val recent:List<Alert> =emptyList(),
 val today:DaySummary=DaySummary(java.time.LocalDate.now(KST),emptyList()),
 val activeZones:List<Zone> =emptyList(),
 val demo:Boolean=false,
 val sampleZones:Boolean=false,
 val carConnected:Boolean=false,
 val error:String?=null,
) {
 val parked get()=vehicle.gear==Gear.P || (demo.not() && vehicle.gear==Gear.UNKNOWN && vehicle.speedKmh.let { it==null || it<1.0 })
 val schoolLimit get()=activeZones.firstOrNull { it.kind==ZoneKind.SCHOOL_ZONE }?.limitKmh
}

/**
 * 앱 전체에서 하나: 엔진·음성·기록을 묶습니다. 서비스는 차량·위치 신호를 넣고, 화면은 상태를 보고 명령만 보냅니다.
 * 모든 호출은 메인 스레드에서 합니다.
 */
class Coach(
 private val prefs:Prefs,
 private val store:NearMissStore,
 val voice:Voice,
 private val zones:ZoneIndex,
 private val systemTag:()->String?,
 private val scope:CoroutineScope,
) {
 private var engine=newEngine()
 private fun newEngine()=CoachEngine(zones).also { it.hipass=prefs.hipass }
 private val _ui=MutableStateFlow(CoachUiState(sampleZones=zones.hasSample))
 val ui:StateFlow<CoachUiState> =_ui.asStateFlow()
 private val _alerts=MutableSharedFlow<Alert>(extraBufferCapacity=16)
 /** 새 안내가 나올 때마다(앱이 뒤에 있을 때 화면 위 알림을 띄우는 데 씀). */
 val alerts:SharedFlow<Alert> =_alerts.asSharedFlow()
 private var demoJob:Job?=null
 private var demoMisses=listOf<NearMiss>()
 private var lastSpoken:Alert?=null

 init {
  val lang=prefs.language(systemTag())
  _ui.value=_ui.value.copy(lang=lang,today=DaySummary.today(store.all(),Instant.now()))
  voice.setLanguage(lang)
  voice.setMuted(prefs.muted)
 }

 val lang get()=_ui.value.lang

 /** 실제 차량 신호. 데모 재생 중에는 섞지 않습니다. SDK 주행 세션이면 sim 엔진으로 넘깁니다. */
 fun accept(signal:Signal,at:Instant=Instant.now()) {
  if(_ui.value.demo) {
   if(simFeedsReal) feedSim(signal,at.plus(simOffset))
   return
  }
  if(signal is Signal.Speed||signal is Signal.Shift) _ui.value=_ui.value.copy(carConnected=true)
  apply(engine.accept(signal,at),engine,demo=false)
 }

 fun reportError(message:String) { _ui.value=_ui.value.copy(error=message) }

 private fun apply(step:Step,e:CoachEngine,demo:Boolean) {
  var s=_ui.value.copy(vehicle=e.state,activeZones=e.activeZones)
  if(step.nearMisses.isNotEmpty()) {
   val all=if(demo) (demoMisses+step.nearMisses).also { demoMisses=it } else store.add(step.nearMisses)
   s=s.copy(today=DaySummary.today(all,e.state.at?:Instant.now()))
  }
  step.alerts.forEach { a->
   s=s.copy(current=ShownAlert(a,SystemClock.elapsedRealtime()),recent=(listOf(a)+s.recent).take(6))
   lastSpoken=a
   voice.say(a.key,a.severity,earcon=true) { l->Strings.render(l,a) }
   _alerts.tryEmit(a)
  }
  _ui.value=s
  if(step.summaryDue) speakSummary(detailed=false)
 }

 // ── 운전자 명령 ──
 /** 화면에서 고른 언어(P에서만) 또는 음성 명령. 이번 렌트 동안 유지됩니다. */
 fun setLanguage(lang:Lang) {
  prefs.manualLang=lang
  _ui.value=_ui.value.copy(lang=lang)
  voice.setLanguage(lang)
 }

 /** 렌터카 체크인: 면허 발급국 또는 언어 태그로 시작 언어를 정하고 이전 운전자 기록을 지웁니다. */
 fun startRental(lang:Lang?,hipass:Boolean?=null) {
  prefs.startRental(lang,hipass)
  store.clear(); demoMisses=emptyList()
  engine=newEngine()
  val resolved=prefs.language(systemTag())
  _ui.value=CoachUiState(lang=resolved,sampleZones=zones.hasSample,today=DaySummary.today(emptyList(),Instant.now()))
  voice.setMuted(false)
  voice.setLanguage(resolved)
 }

 fun setMuted(muted:Boolean) {
  prefs.muted=muted
  voice.setMuted(muted)
  if(!muted) voice.say("unmuted",Severity.INFO) { Strings.render(it,Msg.UNMUTED) }
 }

 fun repeat() {
  val a=lastSpoken ?: return voice.say("help",Severity.INFO) { Strings.render(it,Msg.HELP) }
  voice.say("repeat:${a.key}:${SystemClock.elapsedRealtime()}",Severity.WARN) { l->Strings.render(l,a) }
 }

 /** 주행 중에는 첫 문장만, 정차 중에는 항목까지 읽습니다. */
 fun speakSummary(detailed:Boolean=_ui.value.parked) {
  val today=_ui.value.today
  voice.say("summary",Severity.WARN) { l->
   if(detailed && today.count>0) (listOf(today.headline(l))+today.items.sortedBy { it.at }.map { DaySummary.describe(l,it) }).joinToString(". ")
   else today.headline(l)
  }
 }

 fun listen() {
  voice.listen { text->
   Log.i("SoupCoach","음성 명령 ${text?.length?:0}자")
   when(val c=CommandParser.parse(text)) {
    Command.Repeat->repeat()
    Command.Summary->speakSummary()
    Command.Mute->{ voice.say("unmuted",Severity.INFO) { Strings.render(it,Msg.MUTED) }; scope.launch { delay(2500); setMuted(true) } }
    Command.Unmute->setMuted(false)
    Command.Help->voice.say("help",Severity.INFO) { Strings.render(it,Msg.HELP) }
    is Command.SwitchLanguage->setLanguage(c.lang)
    null->voice.say("not-understood",Severity.INFO) { Strings.render(it,Msg.NOT_UNDERSTOOD) }
   }
  }
 }

 // ── 음성 지원 확인(디버그) ──
 /** 9개 언어의 차량 음성 지원을 확인하고 로그·파일·화면에 남깁니다. */
 fun probeVoices(onDone:(List<VoiceProbeResult>)->Unit={}) {
  scope.launch {
   _ui.value=_ui.value.copy(error="음성 지원 확인 중…")
   val r=voice.probe()
   Log.i("SoupVoiceProbe","\n"+VoiceProbe.table(r))
   _ui.value=_ui.value.copy(error=VoiceProbe.summary(r))
   onDone(r)
  }
 }

 // ── 데모 ──
 fun startDemo(demo:DemoDrive) {
  if(demoJob?.isActive==true) return
  demoMisses=emptyList()
  // 데모는 하이패스 장착 차량으로 가정합니다.
  val e=CoachEngine(zones).also { it.hipass=true }
  _ui.value=_ui.value.copy(demo=true,current=null,recent=emptyList(),today=DaySummary.today(emptyList(),Instant.now()),activeZones=emptyList())
  demoJob=scope.launch {
   try {
    demo.play { signal,at-> apply(e.accept(signal,at),e,demo=true) }
    delay(15_000)
   } catch(t:Throwable) { if(t is CancellationException) throw t; Log.e("SoupCoach","데모 실패",t) }
   finally { stopDemoState() }
  }
 }

 fun stopDemo() { demoJob?.cancel() }

 private fun stopDemoState() {
  if(simEngine!=null) return // 취소된 demoJob의 정리가 진행 중인 시뮬레이션 세션을 덮어쓰지 않게
  demoMisses=emptyList()
  _ui.value=_ui.value.copy(demo=false,current=null,recent=emptyList(),vehicle=engine.state,activeZones=engine.activeZones,today=DaySummary.today(store.all(),Instant.now()))
 }

 // ── 시뮬레이션 (디버그 링크) ──
 private var simEngine:CoachEngine?=null
 /** SDK 주행 모드: 실제 차량 신호(에뮬레이터 조작)를 sim 엔진으로 넘깁니다. */
 private var simFeedsReal=false
 /** 시나리오 시각과 실제 시각의 차. 운영 시간(버스전용차로 등) 판단을 시나리오 기준으로 맞춥니다. */
 private var simOffset:java.time.Duration=java.time.Duration.ZERO
 /** 시뮬레이션 동안만 쓴 언어. 끝나면 이 값(시작 전 언어)으로 되돌립니다. 운전자 설정(prefs)은 건드리지 않습니다. */
 private var simPrevLang:Lang?=null

 /**
  * 외부(디버그 링크)에서 넣는 시뮬레이션 주행. 실제 기록·실제 차량 신호와 섞지 않습니다. 다시 부르면 초기화(reset)입니다.
  * [feedReal]이면 실제 차량 신호도 sim 엔진으로 들어오고(SDK 주행 모드), [virtualNow]로 시나리오 시각을 맞춥니다.
  * [lang]을 주면 시나리오 운전자의 언어로 화면·음성을 바꾸고, [endSim]에서 원래 언어로 되돌립니다.
  */
 fun beginSim(hipass:Boolean=true,feedReal:Boolean=false,virtualNow:Instant?=null,lang:Lang?=null) {
  demoJob?.cancel()
  demoMisses=emptyList()
  simFeedsReal=feedReal
  simOffset=if(virtualNow!=null) java.time.Duration.between(Instant.now(),virtualNow) else java.time.Duration.ZERO
  simEngine=CoachEngine(zones).also { it.hipass=hipass }
  _ui.value=_ui.value.copy(demo=true,current=null,recent=emptyList(),today=DaySummary.today(emptyList(),Instant.now()),activeZones=emptyList())
  if(lang!=null) {
   if(simPrevLang==null) simPrevLang=_ui.value.lang
   _ui.value=_ui.value.copy(lang=lang)
   voice.setLanguage(lang)
  }
 }

 /** sim 엔진에 신호를 넣습니다. 요약 시점이면 true. 세션이 없으면 무시합니다. */
 fun feedSim(signal:Signal,at:Instant):Boolean {
  val e=simEngine?:return false
  val step=e.accept(signal,at)
  apply(step,e,demo=true)
  return step.summaryDue
 }

 fun endSim() {
  if(simEngine==null) return
  simEngine=null
  simFeedsReal=false
  simOffset=java.time.Duration.ZERO
  stopDemoState()
  simPrevLang?.let { prev-> simPrevLang=null; _ui.value=_ui.value.copy(lang=prev); voice.setLanguage(prev) }
 }
}
