package com.goodnight.souprules.core

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime

/**
 * 구역 데이터(JSON) 읽기. 형식은 docs/zone-data.md 참고.
 * ```
 * {"version":1,"sample":true,"zones":[
 *  {"id":"s1","kind":"SCHOOL_ZONE","name":"OO초","limitKmh":30,
 *   "circle":{"lat":33.5,"lng":126.5,"radiusM":300},
 *   "schedule":[{"days":"MON-FRI","start":"08:00","end":"20:00"}]},
 *  {"id":"b1","kind":"BUS_LANE","name":"OO로","corridor":{"halfWidthM":12,"points":[[33.5,126.5],[33.51,126.51]]}}]}
 * ```
 */
object ZoneJson {
 fun parse(text:String):ZoneIndex {
  val root=JSONObject(text)
  val fileSample=root.optBoolean("sample",false)
  val arr=root.getJSONArray("zones")
  val zones=(0 until arr.length()).map { i->zone(arr.getJSONObject(i),fileSample) }
  val dup=zones.groupBy { it.id }.filterValues { it.size>1 }.keys
  require(dup.isEmpty()) { "중복 구역 id: $dup" }
  return ZoneIndex(zones)
 }

 private fun zone(o:JSONObject,fileSample:Boolean):Zone {
  val id=o.getString("id")
  val kind=ZoneKind.valueOf(o.getString("kind"))
  val shape=when {
   o.has("circle")->o.getJSONObject("circle").let { c->ZoneShape.Circle(GeoPoint(c.getDouble("lat"),c.getDouble("lng")),c.getDouble("radiusM")) }
   o.has("corridor")->o.getJSONObject("corridor").let { c->ZoneShape.Corridor(points(c.getJSONArray("points")),c.getDouble("halfWidthM")) }
   else->error("구역 $id: circle 또는 corridor가 필요합니다")
  }
  val schedule=o.optJSONArray("schedule")?.let { s->Schedule((0 until s.length()).map { window(s.getJSONObject(it)) }) }?:Schedule.ALWAYS
  val limit=if(o.has("limitKmh")) o.getInt("limitKmh") else if(kind==ZoneKind.SCHOOL_ZONE) 30 else null
  val grace=if(o.has("graceMinutes")) o.getInt("graceMinutes") else null
  require(limit==null||limit in 10..120) { "구역 $id: 제한속도 범위 밖 $limit" }
  require(grace==null||grace in 0..60) { "구역 $id: 유예 시간 범위 밖 $grace" }
  return Zone(id,kind,o.optString("name",id),shape,schedule,limit,grace,o.optBoolean("rightTurnSignal",false),o.optBoolean("sample",fileSample))
 }

 private fun points(a:JSONArray)=(0 until a.length()).map { i->a.getJSONArray(i).let { GeoPoint(it.getDouble(0),it.getDouble(1)) } }

 private fun window(o:JSONObject)=TimeWindow(TimeWindow.parseDays(o.optString("days","ALL")),LocalTime.parse(o.optString("start","00:00")),LocalTime.parse(o.optString("end","00:00")))
}
