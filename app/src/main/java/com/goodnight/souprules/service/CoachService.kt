package com.goodnight.souprules.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import com.goodnight.souprules.MainActivity
import com.goodnight.souprules.R
import com.goodnight.souprules.SoupApp
import com.goodnight.souprules.core.*
import com.goodnight.souprules.data.CarSignals
import com.goodnight.souprules.data.LocationFeed
import com.goodnight.souprules.data.PleosCarSignals
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 차량·위치 신호를 [Coach]에 넣는 포그라운드 서비스(위치 유형). 화면이 꺼져 있거나 내비가 앞에 있어도 안내가 이어집니다.
 * 알림은 상태 하나만 갱신합니다(오버레이·떠 있는 창 없음).
 */
class CoachService:Service() {
 companion object {
  private const val ID=1
  private const val CHANNEL="soup_rules_status"
  const val DEBUG_SIGNAL="DEBUG_SIGNAL"

  fun canStart(context:Context)=context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
  fun start(context:Context) { if(canStart(context)) runCatching { context.startForegroundService(Intent(context,CoachService::class.java)) }.onFailure { Log.w("SoupService","시작 실패: ${it.message}") } }
 }

 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private val coach get()=(application as SoupApp).coach
 private var car:CarSignals?=null
 private var location:LocationFeed?=null

 override fun onBind(intent:Intent?):IBinder?=null

 override fun onCreate() {
  super.onCreate()
  val nm=getSystemService(NotificationManager::class.java)
  nm.createNotificationChannel(NotificationChannel(CHANNEL,"Korea Road Rules",NotificationManager.IMPORTANCE_LOW).apply { setSound(null,null) })
  startForeground(ID,build(coach.ui.value),ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
  car=PleosCarSignals(this) { coach.reportError(it) }.also { c->
   scope.launch { c.signals.collect { coach.accept(it) } }
   c.start()
  }
  location=LocationFeed(this) { p-> scope.launch { coach.accept(Signal.Position(p)) } }.also { it.start() }
  // 주차 단속 예고·정차 요약은 시간 경과로 판단합니다.
  scope.launch { while(isActive) { delay(1000); coach.accept(Signal.Tick) } }
  scope.launch {
   coach.ui.map { Triple(it.lang,it.current?.alert,it.vehicle.gear) }.distinctUntilChanged().collectLatest { nm.notify(ID,build(coach.ui.value)) }
  }
 }

 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(intent?.action==DEBUG_SIGNAL) debugSignal(intent)
  return START_STICKY
 }

 /** 디버그 빌드의 DebugSignalReceiver가 보낸 가짜 차량 신호(adb). */
 private fun debugSignal(i:Intent) {
  if(!com.goodnight.souprules.BuildConfig.DEBUG) return
  runCatching {
   i.getStringExtra("gear")?.let { coach.accept(Signal.Shift(Gear.valueOf(it.uppercase()))) }
   i.getStringExtra("turn")?.let { coach.accept(Signal.Turn(TurnSignal.valueOf(it.uppercase()))) }
   if(i.hasExtra("speed")) coach.accept(Signal.Speed(i.getStringExtra("speed")!!.toDouble()))
   if(i.hasExtra("steer")) coach.accept(Signal.Steering(i.getStringExtra("steer")!!.toDouble()))
   if(i.hasExtra("seat")) coach.accept(Signal.Seat(i.getStringExtra("seat")!!.toInt(),i.getStringExtra("occupied")?.toBooleanStrictOrNull(),i.getStringExtra("buckled")?.toBooleanStrictOrNull()))
   if(i.hasExtra("lat")) coach.accept(Signal.Position(GeoPoint(i.getStringExtra("lat")!!.toDouble(),i.getStringExtra("lng")!!.toDouble())))
   when(i.getStringExtra("event")) {
    "demo"->coach.startDemo(com.goodnight.souprules.data.DemoDrive(this))
    "summary"->coach.speakSummary()
    "voice_probe"->coach.probeVoices { r->
     // adb shell run-as com.goodnight.souprules cat files/voice-probe.txt 로 읽습니다.
     runCatching { java.io.File(filesDir,"voice-probe.txt").writeText(VoiceProbe.table(r)+"\n"+VoiceProbe.summary(r)+"\n") }
    }
    "lang"->Lang.fromTag(i.getStringExtra("value"))?.let { coach.setLanguage(it) }
    "stt"->CommandParser.parse(i.getStringExtra("value"))?.let { Log.i("SoupService","디버그 STT → $it") }
    else->Unit
   }
  }.onFailure { Log.w("SoupService","디버그 신호 오류: ${it.message}") }
 }

 private fun build(s:CoachUiState):Notification {
  val lang=s.lang
  val title=Strings.render(lang,Msg.APP_NAME)
  val text=s.current?.alert?.let { Strings.render(lang,it) } ?: Strings.render(lang,when {
   !s.carConnected && !s.demo->Msg.STATUS_WAITING
   s.vehicle.gear==Gear.P->Msg.STATUS_PARKED
   else->Msg.STATUS_DRIVING
  })
  val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  return Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_notification)
   .setContentTitle(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
   .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
   .setCategory(Notification.CATEGORY_NAVIGATION).setContentIntent(open).build()
 }

 override fun onDestroy() {
  scope.cancel()
  car?.close(); location?.close()
  super.onDestroy()
 }
}
