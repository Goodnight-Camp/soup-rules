package com.goodnight.souprules.core

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** "오늘 과태료가 나올 뻔한 순간 2번" — 한국 날짜 기준 하루치 요약. */
data class DaySummary(val date:LocalDate,val items:List<NearMiss>) {
 val count get()=items.size
 val totalFineKrw get()=items.sumOf { it.fineKrw }

 /** 정차 후 한 번에 말할 첫 문장. */
 fun headline(lang:Lang):String=when(count) {
  0->Strings.render(lang,Msg.SUMMARY_NONE)
  1->Strings.render(lang,Msg.SUMMARY_ONE,mapOf("amount" to Strings.amount(lang,totalFineKrw)))
  else->Strings.render(lang,Msg.SUMMARY_MANY,mapOf("count" to "$count","amount" to Strings.amount(lang,totalFineKrw)))
 }

 /** 화면 목록 한 줄: 시각 · 내용 · 예상 금액. */
 fun lines(lang:Lang):List<String> =items.sortedBy { it.at }.map { line(lang,it) }

 companion object {
  private val TIME=DateTimeFormatter.ofPattern("HH:mm")
  fun of(all:List<NearMiss>,date:LocalDate)=DaySummary(date,all.filter { LocalDate.ofInstant(it.at,KST)==date })
  fun today(all:List<NearMiss>,now:Instant)=of(all,LocalDate.ofInstant(now,KST))

  fun describe(lang:Lang,m:NearMiss):String=when(m.rule) {
   RuleId.SCHOOL_ZONE_SPEEDING->Strings.render(lang,Msg.NM_SCHOOL_SPEEDING,mapOf("speed" to "${m.speedKmh?:0}","limit" to "${m.limitKmh?:30}"))
   RuleId.RIGHT_TURN_NO_STOP->Strings.render(lang,Msg.NM_RIGHT_TURN)
   RuleId.BUS_LANE_STOP->Strings.render(lang,Msg.NM_BUS_LANE_STOP)
   RuleId.SCHOOL_ZONE_PARKING->Strings.render(lang,Msg.NM_SCHOOL_PARKING)
   else->Strings.render(lang,Msg.NM_PARKING_CCTV)
  }

  fun line(lang:Lang,m:NearMiss):String {
   val time=m.at.atZone(KST).format(TIME)
   val fine=if(m.fineKrw>0) " · ~${Strings.amount(lang,m.fineKrw)}" else ""
   return "$time · ${describe(lang,m)}$fine"
  }
 }
}
