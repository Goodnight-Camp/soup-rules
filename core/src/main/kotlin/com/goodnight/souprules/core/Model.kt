package com.goodnight.souprules.core

import java.time.Instant
import java.time.ZoneId

/** 한국 단속 시간표는 모두 한국 시각 기준입니다. */
val KST: ZoneId = ZoneId.of("Asia/Seoul")

enum class Gear { P, R, N, D, UNKNOWN;
 companion object {
  /** AAOS VehicleGear: NEUTRAL=1, REVERSE=2, PARK=4, DRIVE=8, 수동 1~9단=16 이상. */
  fun fromAaos(value:Int):Gear=when { value==4->P; value==2->R; value==1->N; value>=8->D; else->UNKNOWN }
 }
}

enum class TurnSignal { OFF, LEFT, RIGHT;
 companion object {
  /** AAOS TURN_SIGNAL_STATE: NONE=0, RIGHT=1, LEFT=2. */
  fun fromAaos(value:Int):TurnSignal=when(value) { 1->RIGHT; 2->LEFT; else->OFF }
 }
}

data class GeoPoint(val lat:Double,val lng:Double)

/** 차량·위치에서 들어오는 신호. 숫자(속도·위치)는 센서에서만 옵니다. */
sealed interface Signal {
 data class Speed(val kmh:Double):Signal
 data class Shift(val gear:Gear):Signal
 data class Turn(val signal:TurnSignal):Signal
 data class Position(val point:GeoPoint):Signal
 /** 운전대 각도(도). 부호 규약은 차량마다 다를 수 있어 방향이 바뀌는 순간만 씁니다. */
 data class Steering(val degrees:Double):Signal
 /** 좌석별 착석·안전띠. [seat]는 열×3+칸(0=1열 왼쪽=운전석). null은 모름. */
 data class Seat(val seat:Int,val occupied:Boolean?=null,val buckled:Boolean?=null):Signal
 /** 시간 경과만 알립니다(주차 단속 예고·요약 시점 판단). */
 data object Tick:Signal
}

data class VehicleState(
 val speedKmh:Double?=null,
 val gear:Gear=Gear.UNKNOWN,
 val turn:TurnSignal=TurnSignal.OFF,
 val position:GeoPoint?=null,
 val at:Instant?=null,
)
