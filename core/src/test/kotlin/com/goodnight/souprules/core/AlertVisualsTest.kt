package com.goodnight.souprules.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AlertVisualsTest {
 private val at=Instant.EPOCH

 @Test fun everyRuleHasIconAndHeadlineInEveryLanguage() {
  RuleId.entries.forEach { r->
   val v=AlertVisuals.of(Alert(r,Severity.INFO,Msg.ALL_CLEAR,mapOf("limit" to "30"),at))
   Lang.entries.forEach { l->
    val h=Strings.render(l,v.headline,v.args)
    assertFalse("$r $l 자리표시자 남음: $h",h.contains("{"))
    // 한눈에 읽는 제목: 설명 문장보다 훨씬 짧게
    assertTrue("$r $l 제목이 김: $h",h.length<=40)
   }
  }
 }

 @Test fun headlinesCarryLimit() {
  val a=Alert(RuleId.SCHOOL_ZONE_SPEEDING,Severity.URGENT,Msg.SCHOOL_SLOW_DOWN,mapOf("speed" to "45","limit" to "30"),at)
  assertEquals(RuleIcon.SLOW_DOWN,AlertVisuals.of(a).icon)
  assertEquals("Slow down to 30",AlertVisuals.headline(Lang.EN,a))
  assertEquals("30km/h로 감속",AlertVisuals.headline(Lang.KO,a))
  assertEquals("時速30キロに減速",AlertVisuals.headline(Lang.JA,a))
 }

 @Test fun rightTurnSignalVariant() {
  val signal=Alert(RuleId.RIGHT_TURN_ZONE,Severity.WARN,Msg.RIGHT_TURN_ZONE_SIGNAL,emptyMap(),at)
  val plain=Alert(RuleId.RIGHT_TURN_ZONE,Severity.WARN,Msg.RIGHT_TURN_ZONE,emptyMap(),at)
  assertEquals(Msg.H_RIGHT_ARROW,AlertVisuals.of(signal).headline)
  assertEquals(Msg.H_RIGHT_STOP,AlertVisuals.of(plain).headline)
 }

 @Test fun earconsGetMoreUrgentWithSeverity() {
  val info=Earcons.of(Severity.INFO); val warn=Earcons.of(Severity.WARN); val urgent=Earcons.of(Severity.URGENT)
  assertTrue(info.beeps<warn.beeps && warn.beeps<urgent.beeps)
  assertTrue(info.freqHz<warn.freqHz && warn.freqHz<urgent.freqHz)
  // 음성 안내를 오래 막지 않게 0.6초 안에 끝냅니다.
  assertTrue(listOf(info,warn,urgent).all { it.totalMs<=600 })
 }

 @Test fun earconPcmLengthAndFades() {
  val spec=Earcons.of(Severity.URGENT)
  val pcm=Earcons.pcm(spec,22050)
  assertEquals((spec.totalMs*22050/1000).toDouble(),pcm.size.toDouble(),3.0)
  assertEquals(0,pcm[0].toInt()) // 페이드 인
  val tone=22050*spec.toneMs/1000
  assertEquals(0,pcm[tone+5].toInt()) // 음 사이 무음
  assertTrue(pcm.maxOf { it.toInt() } in 15000..Short.MAX_VALUE.toInt())
 }
}
