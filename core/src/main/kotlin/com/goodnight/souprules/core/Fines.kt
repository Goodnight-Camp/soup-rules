package com.goodnight.souprules.core

/**
 * 승용차 기준 예상 금액(원). 무인 단속 과태료(차주 부과) 기준이며, 경찰 현장 단속 범칙금은 다를 수 있습니다.
 * 근거: 도로교통법 시행령 별표 6·7(2024년 기준). 화면에는 항상 "예상"으로 표시합니다.
 */
object Fines {
 /** 어린이보호구역 속도위반 과태료(승용): 20 이하 7만, 40 이하 10만, 60 이하 13만, 초과 16만. */
 fun schoolZoneSpeeding(overKmh:Double):Int=when {
  overKmh<=0->0
  overKmh<=20->70_000
  overKmh<=40->100_000
  overKmh<=60->130_000
  else->160_000
 }
 /** 어린이보호구역 주정차 위반 과태료(승용, 일반 4만원의 3배). */
 const val SCHOOL_ZONE_PARKING=120_000
 /** 일반 주정차 위반 과태료(승용). */
 const val ILLEGAL_PARKING=40_000
 /** 버스전용차로 통행·정차 위반 과태료(일반도로, 승용). */
 const val BUS_LANE=50_000
 /** 우회전 시 일시정지 위반(보행자 보호 의무 위반 범칙금, 승용). 적색 신호 무인 단속은 과태료 7만원. */
 const val RIGHT_TURN=60_000

 fun of(rule:RuleId,speedKmh:Int?=null,limitKmh:Int?=null):Int=when(rule) {
  RuleId.SCHOOL_ZONE_SPEEDING->if(speedKmh!=null&&limitKmh!=null) schoolZoneSpeeding((speedKmh-limitKmh).toDouble()) else 0
  RuleId.SCHOOL_ZONE_PARKING->SCHOOL_ZONE_PARKING
  RuleId.PARKING_CCTV->ILLEGAL_PARKING
  RuleId.BUS_LANE_STOP->BUS_LANE
  RuleId.RIGHT_TURN_NO_STOP->RIGHT_TURN
  else->0
 }

 /** 70000 → "70,000" (언어와 무관하게 쉼표 구분). */
 fun format(won:Int):String="%,d".format(java.util.Locale.ROOT,won)
}
