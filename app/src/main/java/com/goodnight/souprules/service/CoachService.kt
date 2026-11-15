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
import com.goodnight.souprules.ui.AppVisibility
import com.goodnight.souprules.ui.RuleIcons
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
  /** 긴급·주의 안내: 다른 앱(내비)이 앞에 있을 때 화면 위에 잠깐 뜨는 알림. 소리는 효과음·음성이 따로 냅니다. */
  private const val ALERT_CHANNEL="soup_rules_alerts"
  private const val ALERT_ID=2
  /** 화면 위 알림은 이 시간 뒤 사라집니다(지나간 안내가 남지 않게). */
  private const val ALERT_TIMEOUT_MS=10_000L
  const val DEBUG_SIGNAL="DEBUG_SIGNAL"

  /** 차량·위치 신호를 받는 이 서비스가 떠 있는지(콕핏 진단용). 꺼져 있으면 기어·차속이 앱에 들어오지 않습니다. */
  @Volatile var running=false; private set
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
  running=true
  val nm=getSystemService(NotificationManager::class.java)
  nm.createNotificationChannel(NotificationChannel(CHANNEL,"Korea Road Rules",NotificationManager.IMPORTANCE_LOW).apply { setSound(null,null) })
  nm.createNotificationChannel(NotificationChannel(ALERT_CHANNEL,"Korea Road Rules · alerts",NotificationManager.IMPORTANCE_HIGH).apply {
   description="Urgent rule alerts while another app is on screen"; setSound(null,null); enableVibration(false)
  })
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
  // 앱 화면이 보이지 않을 때(내비가 앞에 있을 때) 긴급·주의 안내는 화면 위 알림으로도 보여 줍니다.
  scope.launch {
   coach.alerts.collect { a->
    if(a.severity>=Severity.WARN && !AppVisibility.foreground) runCatching { nm.notify(ALERT_ID,buildAlert(a,coach.ui.value.lang)) }
   }
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

 /** 아이콘(규칙 그림) + 짧은 제목 + 긴 설명. 오버레이가 아닌 Android 표준 헤드업 알림입니다. */
 private fun buildAlert(a:Alert,lang:Lang):Notification {
  val visual=AlertVisuals.of(a)
  val text=Strings.render(lang,a)
  val open=PendingIntent.getActivity(this,1,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  return Notification.Builder(this,ALERT_CHANNEL).setSmallIcon(RuleIcons.of(visual.icon))
   .setContentTitle(Strings.render(lang,visual.headline,visual.args)).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
   .setColor(android.graphics.Color.parseColor(if(a.severity==Severity.URGENT) "#FF6A4D" else "#F5B83D"))
   .setAutoCancel(true).setTimeoutAfter(ALERT_TIMEOUT_MS).setOnlyAlertOnce(false).setShowWhen(false)
   .setCategory(Notification.CATEGORY_STATUS).setContentIntent(open).build()
 }

 private fun build(s:CoachUiState):Notification {
  val lang=s.lang
  val title=Strings.render(lang,Msg.APP_NAME)
  // 상태 알림도 최근 안내가 있으면 규칙 아이콘·짧은 제목을 씁니다.
  s.current?.alert?.let { a->
   val v=AlertVisuals.of(a); val body=Strings.render(lang,a)
   return Notification.Builder(this,CHANNEL).setSmallIcon(RuleIcons.of(v.icon))
    .setContentTitle(Strings.render(lang,v.headline,v.args)).setContentText(body).setStyle(Notification.BigTextStyle().bigText(body))
    .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).setCategory(Notification.CATEGORY_NAVIGATION)
    .setContentIntent(PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build()
  }
  val text=Strings.render(lang,when {
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
  running=false
  scope.cancel()
  car?.close(); location?.close()
  super.onDestroy()
 }
}
