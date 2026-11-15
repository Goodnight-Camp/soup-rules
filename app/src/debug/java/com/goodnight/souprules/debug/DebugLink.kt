package com.goodnight.souprules.debug

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.goodnight.souprules.BuildConfig
import com.goodnight.souprules.SoupApp
import com.goodnight.souprules.service.CoachService
import com.goodnight.souprules.core.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * 가상 내비(tools/cockpit) 링크. 디버그 빌드 전용이며 release에는 포함되지 않습니다.
 * ws://…/?role=app 에 붙어 서버의 begin/signal/end를 Coach의 시뮬레이션 세션으로 넘기고,
 * 앱이 실제로 판단한 결과(coach.ui)를 state로 돌려줍니다. 웹은 받은 것만 그립니다.
 * 끊기면 2초 간격으로 재접속하고, 시뮬레이션이 열려 있으면 차속 0·P·endSim으로 정리합니다.
 */
object DebugLink {
 private const val TAG="SoupDebugLink"
 private val main=Handler(Looper.getMainLooper())
 private val client=OkHttpClient.Builder().pingInterval(10,TimeUnit.SECONDS).build()
 private var app:SoupApp?=null
 private var ws:WebSocket?=null
 private var connected=false
 private var started=false
 // 서버 주소 후보(쉼표로 여러 개). 순서대로 시도하고, 붙은 곳은 끊겨도 먼저 재시도합니다.
 private val urls=BuildConfig.COCKPIT_WS.split(",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("ws://localhost:8765") }
 private var urlIndex=0
 // state는 최소 간격 100ms로 묶어 보냅니다. summary는 묶이면서 사라지지 않게 따로 보관합니다.
 private var sendQueued=false
 private var pendingSummary=false

 /** 메인 스레드에서 한 번만. [DebugLinkProvider]가 프로세스 시작 때 부릅니다. */
 fun start(app:SoupApp) {
  if(started) return
  started=true
  this.app=app
  app.scope.launch { app.coach.ui.collect { queueState() } }
  connect()
 }

 private fun connect() {
  val base=urls[urlIndex%urls.size]
  val url=base+"/?role=app"
  Log.i(TAG,"가상 내비 접속 시도 $url")
  ws=client.newWebSocket(Request.Builder().url(url).build(),object:WebSocketListener() {
   override fun onOpen(webSocket:WebSocket,response:Response) {
    main.post {
     connected=true
     Log.i(TAG,"가상 내비 연결됨 $base")
     // caps.simZones: begin에 실린 구역으로 판단할 수 있음. zoneIds: 앱에 들어 있는 구역(서버가 차이를 진단).
     webSocket.send(JSONObject().put("type","hello").put("app","souprules").put("version",BuildConfig.VERSION_NAME)
      .put("caps",JSONArray(listOf("simZones","lang")))
      .put("zoneIds",JSONArray(app?.coach?.bundledZoneIds.orEmpty())).toString())
     // 차량 신호 서비스가 꺼져 있으면(권한 허용 직후·재설치 직후 등) 여기서 다시 시작합니다.
     // 위치 권한이 없으면 시작할 수 없고, 그 사실은 state.app으로 웹에 보입니다.
     app?.let { if(!CoachService.running) CoachService.start(it) }
     queueState()
     // 서비스가 막 시작됐다면 그 결과가 웹에 보이도록 잠시 뒤 한 번 더 보냅니다.
     main.postDelayed({ queueState() },1500)
    }
   }
   override fun onMessage(webSocket:WebSocket,text:String) {
    main.post { runCatching { handle(JSONObject(text)) }.onFailure { Log.e(TAG,"메시지 처리 실패: $text",it) } }
   }
   override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?) { main.post { onDisconnected() } }
   override fun onClosed(webSocket:WebSocket,code:Int,reason:String) { main.post { onDisconnected() } }
  })
 }

 private fun onDisconnected() {
  val wasConnected=connected
  connected=false; ws=null
  val coach=app?.coach
  // 주행 재생 중 서버가 죽으면 앱이 D 상태로 방치되지 않게 정리합니다(세션이 없으면 전부 무시됨).
  if(wasConnected && coach!=null && coach.ui.value.demo) {
   val now=Instant.now()
   coach.feedSim(Signal.Speed(0.0),now)
   coach.feedSim(Signal.Shift(Gear.P),now)
   coach.endSim()
   Log.i(TAG,"연결 끊김: 시뮬레이션 정리")
  }
  if(!wasConnected) urlIndex++   // 접속 자체가 실패했을 때만 다음 후보로. 붙었다 끊긴 곳은 그대로 재시도.
  main.postDelayed({ if(ws==null) connect() },2000)
 }

 private fun handle(msg:JSONObject) {
  val coach=app?.coach?:return
  when(msg.optString("type")) {
   "begin"->{
    pendingSummary=false
    coach.beginSim(
     hipass=msg.optBoolean("hipass",true),
     feedReal=msg.optString("mode")=="sdk",   // SDK 주행: 에뮬레이터 차량 조작(실신호)을 sim 엔진으로
     virtualNow=msg.optString("virtualStart").takeIf { it.isNotEmpty() }?.let { Instant.parse(it) },
     // 시나리오 운전자 언어(예: 사례 1은 zh-Hant, 사례 2는 fr). 없으면 앱의 현재 언어 그대로.
     lang=Lang.fromTag(msg.optString("lang").takeIf { it.isNotEmpty() }),
     // 서버(저장소)의 최신 구역. 앱에 든 구역이 옛것이어도 시나리오 구역에서 안내가 나오게 합니다.
     simZones=msg.optJSONObject("zones")?.let { z-> runCatching { ZoneJson.parse(z.toString()) }
      .onSuccess { Log.i(TAG,"시뮬레이션 구역 ${it.zones.size}개(서버 제공)") }
      .onFailure { Log.w(TAG,"서버 구역 읽기 실패, 앱 구역 사용: ${it.message}") }.getOrNull() },
    )
   }
   "end"->coach.endSim()
   "signal"->{
    val at=Instant.parse(msg.getString("at"))
    var summary=false
    // 필드 순서: 위치 → 기어 → 깜빡이 → 차속 → Tick (DemoDrive.parse와 동일)
    if(msg.has("lat")) summary=coach.feedSim(Signal.Position(GeoPoint(msg.getDouble("lat"),msg.getDouble("lng"))),at)||summary
    if(msg.has("gear")) summary=coach.feedSim(Signal.Shift(Gear.valueOf(msg.getString("gear"))),at)||summary
    if(msg.has("turn")) summary=coach.feedSim(Signal.Turn(TurnSignal.valueOf(msg.getString("turn"))),at)||summary
    if(msg.has("steer")) summary=coach.feedSim(Signal.Steering(msg.getDouble("steer")),at)||summary
    if(msg.has("speed")) summary=coach.feedSim(Signal.Speed(msg.getDouble("speed")),at)||summary
    summary=coach.feedSim(Signal.Tick,at)||summary
    if(summary) pendingSummary=true
    queueState()
   }
  }
 }

 private fun queueState() {
  if(!connected||sendQueued) return
  sendQueued=true
  main.postDelayed({ sendQueued=false; sendState() },100)
 }

 private fun granted(p:String)=app?.checkSelfPermission(p)==android.content.pm.PackageManager.PERMISSION_GRANTED

 private fun sendState() {
  val w=ws?:return
  val ui=app?.coach?.ui?.value?:return
  val v=ui.vehicle
  val o=JSONObject()
   .put("type","state")
   .put("sim",ui.demo)
   .put("lang",ui.lang.tag)
   .put("vehicle",JSONObject().apply {
    v.speedKmh?.let { put("speed",it) }
    put("gear",v.gear.name)
    put("turn",v.turn.name)
    v.position?.let { put("lat",it.lat); put("lng",it.lng) }
   })
   .put("alert",ui.current?.alert?.let { a->
    JSONObject()
     .put("rule",a.rule.name)
     .put("severity",a.severity.name)
     .put("text",Strings.render(ui.lang,a))
     .put("at",a.at.toString())
     .apply { a.zoneId?.let { put("zoneId",it) } }
   }?:JSONObject.NULL)
   .put("zones",JSONArray(ui.activeZones.map { it.id }))
   .put("today",JSONObject()
    .put("count",ui.today.count)
    .put("totalFineKrw",ui.today.totalFineKrw)
    .put("items",JSONArray(ui.today.items.sortedBy { it.at }.map { m->
     JSONObject()
      .put("rule",m.rule.name)
      .put("fineKrw",m.fineKrw)
      .put("at",m.at.toString())
      .apply {
       m.zoneId?.let { put("zoneId",it) }
       m.zoneName?.let { put("zoneName",it) }
       m.speedKmh?.let { put("speedKmh",it) }
       m.limitKmh?.let { put("limitKmh",it) }
      }
    })))
   .put("summary",pendingSummary)
   // 진단: 웹이 "연결은 됐는데 기어가 안 바뀜"의 원인을 보여 줄 수 있게 합니다.
   .put("app",JSONObject()
    .put("service",CoachService.running)
    .put("carSignal",ui.carConnected)
    .put("perms",JSONObject()
     .put("location",granted(android.Manifest.permission.ACCESS_FINE_LOCATION))
     .put("carSpeed",granted("android.car.permission.CAR_SPEED"))
     .put("mic",granted(android.Manifest.permission.RECORD_AUDIO))))
  pendingSummary=false
  w.send(o.toString())
 }
}
