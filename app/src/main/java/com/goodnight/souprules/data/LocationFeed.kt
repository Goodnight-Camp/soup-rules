package com.goodnight.souprules.data

import android.content.Context
import android.util.Log
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import com.goodnight.souprules.core.GeoPoint

/**
 * Pleos FusedLocation 연속 위치. 좌표는 구역 판단에만 쓰고 저장하지 않습니다(과태료 직전 기록에도 구역 이름만 남김).
 */
class LocationFeed(context:Context,private val onPoint:(GeoPoint)->Unit) {
 private val sdk=FusedLocation(context)
 private var started=false
 private val listener=object:FusedLocationListener {
  override fun onChanged(location:LocationData?) { location?.let { onPoint(GeoPoint(it.latitude,it.longitude)) } }
  override fun onError(exception:Exception) { Log.w("SoupLocation","위치 오류: ${exception.message}") }
 }
 fun start() {
  if(started) return
  runCatching {
   if(!sdk.isSupported()) { Log.w("SoupLocation","FusedLocation 미지원"); return }
   sdk.initialize(); sdk.registerFusedLocationCallback(listener); started=true
  }.onFailure { Log.w("SoupLocation","FusedLocation 시작 실패: ${it.message}") }
 }
 fun close() {
  runCatching { if(started) sdk.unregisterFusedLocationCallback(listener) }
  runCatching { sdk.release() }
  started=false
 }
}
