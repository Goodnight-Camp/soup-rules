package com.goodnight.souprules.core

import kotlin.math.*

object Geo {
 private const val EARTH_M=6_371_000.0

 fun distanceM(a:GeoPoint,b:GeoPoint):Double {
  val dLat=Math.toRadians(b.lat-a.lat); val dLng=Math.toRadians(b.lng-a.lng)
  val h=sin(dLat/2).pow(2)+cos(Math.toRadians(a.lat))*cos(Math.toRadians(b.lat))*sin(dLng/2).pow(2)
  return 2*EARTH_M*asin(sqrt(h.coerceIn(0.0,1.0)))
 }

 /** p에서 선분 ab까지의 거리. 수 km 이내 구간이므로 p 기준 평면 근사를 씁니다. */
 fun distanceToSegmentM(p:GeoPoint,a:GeoPoint,b:GeoPoint):Double {
  val k=cos(Math.toRadians(p.lat))
  fun x(q:GeoPoint)=Math.toRadians(q.lng-p.lng)*k*EARTH_M
  fun y(q:GeoPoint)=Math.toRadians(q.lat-p.lat)*EARTH_M
  val ax=x(a); val ay=y(a); val dx=x(b)-ax; val dy=y(b)-ay
  val len2=dx*dx+dy*dy
  val t=if(len2==0.0) 0.0 else ((-ax*dx-ay*dy)/len2).coerceIn(0.0,1.0)
  return hypot(ax+t*dx,ay+t*dy)
 }

 fun distanceToPolylineM(p:GeoPoint,points:List<GeoPoint>):Double {
  require(points.isNotEmpty()) { "빈 경로" }
  if(points.size==1) return distanceM(p,points[0])
  return points.zipWithNext { a,b->distanceToSegmentM(p,a,b) }.min()
 }
}
