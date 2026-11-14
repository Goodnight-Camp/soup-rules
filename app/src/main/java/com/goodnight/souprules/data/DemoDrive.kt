package com.goodnight.souprules.data

import android.content.Context
import com.goodnight.souprules.core.*
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.time.Instant

/**
 * 에뮬레이터에는 차속·깜빡이·위치가 없으므로, 제주 예시 구역을 지나는 주행을 재생합니다.
 * 각 행: {"t":초,"speed":km/h?,"gear":"D"?,"turn":"RIGHT"?,"lat":..,"lng":..}
 * 시각은 파일의 "start"(KST 기준 평일 오전, 버스전용차로 운영 시간) + t초로 엔진에 넘깁니다.
 */
class DemoDrive(private val context:Context) {
 data class Frame(val t:Long,val signals:List<Signal>)

 fun load(asset:String="demo/jeju-drive.json"):Pair<Instant,List<Frame>> {
  val root=org.json.JSONObject(context.assets.open(asset).bufferedReader().use { it.readText() })
  val start=Instant.parse(root.getString("start"))
  val rows=root.getJSONArray("frames")
  return start to parse(rows)
 }

 private fun parse(rows:JSONArray)=(0 until rows.length()).map { i->
  val o=rows.getJSONObject(i)
  Frame(o.getLong("t"),buildList {
   if(o.has("lat")) add(Signal.Position(GeoPoint(o.getDouble("lat"),o.getDouble("lng"))))
   if(o.has("gear")) add(Signal.Shift(Gear.valueOf(o.getString("gear"))))
   if(o.has("turn")) add(Signal.Turn(TurnSignal.valueOf(o.getString("turn"))))
   if(o.has("speed")) add(Signal.Speed(o.getDouble("speed")))
   add(Signal.Tick)
  })
 }

 /** [speedup]배 빠르게 재생합니다. 음성 안내가 겹치지 않고 들리도록 기본은 2.5배(약 2분). */
 suspend fun play(speedup:Double=2.5,accept:(Signal,Instant)->Unit) {
  val (start,frames)=load()
  var last=0L
  for(f in frames) {
   delay(((f.t-last)*1000/speedup).toLong().coerceAtLeast(0)); last=f.t
   val at=start.plusSeconds(f.t)
   f.signals.forEach { accept(it,at) }
  }
 }
}
