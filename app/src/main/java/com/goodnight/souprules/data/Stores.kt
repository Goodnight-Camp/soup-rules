package com.goodnight.souprules.data

import android.content.Context
import com.goodnight.souprules.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

/**
 * 언어·음성 설정. 렌트마다 운전자가 바뀌므로 렌트 시작(체크인)에서 [startRental]로 초기화합니다.
 */
class Prefs(context:Context) {
 private val sp=context.getSharedPreferences("soup_prefs",Context.MODE_PRIVATE)
 var manualLang:Lang?
  get()=sp.getString("manual",null)?.let { runCatching { Lang.valueOf(it) }.getOrNull() }
  set(v) { sp.edit().putString("manual",v?.name).apply() }
 var rentalLang:Lang?
  get()=sp.getString("rental",null)?.let { runCatching { Lang.valueOf(it) }.getOrNull() }
  set(v) { sp.edit().putString("rental",v?.name).apply() }
 var muted:Boolean
  get()=sp.getBoolean("muted",false)
  set(v) { sp.edit().putBoolean("muted",v).apply() }
 var consented:Boolean
  get()=sp.getBoolean("consent",false)
  set(v) { sp.edit().putBoolean("consent",v).apply() }

 fun language(systemTag:String?)=LanguageResolver.resolve(manualLang,rentalLang,systemTag)

 /** 새 렌트: 이전 운전자의 언어 선택·음소거를 지우고 체크인 정보로 언어를 맞춥니다. */
 fun startRental(lang:Lang?) { sp.edit().remove("manual").remove("muted").putString("rental",lang?.name).apply() }
}

/**
 * 과태료 직전 기록. 화면의 '오늘' 요약과 정차 시 음성 요약에 씁니다.
 * 좌표는 저장하지 않고 구역 id·이름만 남깁니다. 7일이 지나면 지웁니다.
 */
class NearMissStore(context:Context) {
 private val sp=context.getSharedPreferences("soup_near_miss",Context.MODE_PRIVATE)
 private val keep=Duration.ofDays(7)

 fun all():List<NearMiss> =runCatching { decode(JSONArray(sp.getString("items","[]"))) }.getOrDefault(emptyList())

 fun add(items:List<NearMiss>,now:Instant=Instant.now()):List<NearMiss> {
  val next=(all()+items).filter { Duration.between(it.at,now)<keep }
  sp.edit().putString("items",encode(next).toString()).apply()
  return next
 }

 fun clear() { sp.edit().remove("items").apply() }

 companion object {
  fun encode(items:List<NearMiss>)=JSONArray().apply { items.forEach { m->
   put(JSONObject().put("at",m.at.toString()).put("rule",m.rule.name).put("zoneId",m.zoneId).put("zoneName",m.zoneName)
    .put("speed",m.speedKmh).put("limit",m.limitKmh).put("fine",m.fineKrw))
  } }
  fun decode(a:JSONArray)=(0 until a.length()).mapNotNull { i-> runCatching {
   val o=a.getJSONObject(i)
   NearMiss(Instant.parse(o.getString("at")),RuleId.valueOf(o.getString("rule")),o.optString("zoneId").ifEmpty { null },o.optString("zoneName").ifEmpty { null },
    if(o.has("speed")) o.getInt("speed") else null,if(o.has("limit")) o.getInt("limit") else null,o.getInt("fine"))
  }.getOrNull() }
 }
}

object ZoneRepository {
 /** 앱에 넣은 구역 파일. 실제 공공데이터로 바꾸는 방법은 docs/zone-data.md 참고. */
 const val ASSET="zones/jeju-sample.json"
 fun load(context:Context):ZoneIndex=runCatching {
  context.assets.open(ASSET).bufferedReader().use { ZoneJson.parse(it.readText()) }
 }.onFailure { android.util.Log.e("SoupZones","구역 데이터 읽기 실패",it) }.getOrDefault(ZoneIndex.EMPTY)
}
