package com.goodnight.souprules.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class LanguageTest {
 @Test fun everyLanguageHasEveryMessage() {
  Lang.entries.forEach { assertEquals("$it 누락",emptyList<Msg>(),Strings.missing(it)) }
 }

 @Test fun placeholdersMatchEnglish() {
  Lang.entries.forEach { lang-> Msg.entries.forEach { msg->
   assertEquals("$lang $msg",Strings.placeholders(Strings.get(Lang.EN,msg)),Strings.placeholders(Strings.get(lang,msg)))
  } }
 }

 @Test fun renderFillsArgs() {
  assertEquals("Slow down. 45 km/h in a 30 school zone.",Strings.render(Lang.EN,Msg.SCHOOL_SLOW_DOWN,mapOf("speed" to "45","limit" to "30")))
  assertEquals("70,000원",Strings.amount(Lang.KO,70_000))
 }

 @Test fun tagParsing() {
  assertEquals(Lang.ZH_HANT,Lang.fromTag("zh-HK"))
  assertEquals(Lang.ZH_HANT,Lang.fromTag("zh_TW"))
  assertEquals(Lang.ZH_HANT,Lang.fromTag("zh-Hant"))
  assertEquals(Lang.ZH_HANS,Lang.fromTag("zh-CN"))
  assertEquals(Lang.ZH_HANS,Lang.fromTag("zh-Hans-SG"))
  assertEquals(Lang.EN,Lang.fromTag("en-SG"))
  assertEquals(Lang.MS,Lang.fromTag("ms-MY"))
  assertEquals(Lang.ID,Lang.fromTag("in-ID"))
  assertEquals(Lang.ID,Lang.fromTag("id"))
  assertNull(Lang.fromTag("fr-FR"))
  assertNull(Lang.fromTag(null))
 }

 @Test fun countryDefaults() {
  assertEquals(Lang.EN,CountryLanguage.of("sg"))
  assertEquals(Lang.MS,CountryLanguage.of("MY"))
  assertEquals(Lang.ZH_HANT,CountryLanguage.of("HK"))
  assertEquals(Lang.EN,CountryLanguage.of("DE"))
  assertNull(CountryLanguage.of("XYZ"))
  assertTrue(CountryLanguage.idpNotRecognized("CN"))
  assertFalse(CountryLanguage.idpNotRecognized("SG"))
 }

 @Test fun resolverPriority() {
  assertEquals(Lang.JA,LanguageResolver.resolve(Lang.JA,Lang.MS,"th-TH"))
  assertEquals(Lang.MS,LanguageResolver.resolve(null,Lang.MS,"th-TH"))
  assertEquals(Lang.TH,LanguageResolver.resolve(null,null,"th-TH"))
  // 렌터카 기기 언어가 한국어면 외국인 운전자 기준 영어
  assertEquals(Lang.EN,LanguageResolver.resolve(null,null,"ko-KR"))
  assertEquals(Lang.EN,LanguageResolver.resolve(null,null,"fr-FR"))
 }

 @Test fun voiceFallsBackToEnglishThenNothing() {
  val v=VoiceNegotiator(Lang.MS)
  assertEquals("ms-MY",v.current!!.locale.toLanguageTag()); assertFalse(v.degraded)
  assertEquals(Lang.EN,v.fail()!!.lang); assertTrue(v.degraded)
  assertEquals("en-GB",v.fail()!!.locale.toLanguageTag())
  assertNull(v.fail()); assertTrue(v.exhausted)
  assertNull(v.fail())
 }

 @Test fun hantTriesTaiwanThenHongKong() {
  assertEquals(listOf("zh-TW","zh-HK","en-US","en-GB"),VoiceNegotiator(Lang.ZH_HANT).chain.map { it.locale.toLanguageTag() })
  assertEquals(listOf("en-US","en-GB"),VoiceNegotiator(Lang.EN).chain.map { it.locale.toLanguageTag() })
 }

 @Test fun summaryHeadlineAndLines() {
  val day=Instant.parse("2026-11-16T01:00:00Z") // 10:00 KST
  val misses=listOf(
   NearMiss(day,RuleId.SCHOOL_ZONE_SPEEDING,"s1","A",42,30),
   NearMiss(day.plusSeconds(600),RuleId.PARKING_CCTV,"c1","B"),
   NearMiss(Instant.parse("2026-11-14T01:00:00Z"),RuleId.BUS_LANE_STOP,"b1","C"),
  )
  val s=DaySummary.today(misses,day)
  assertEquals(LocalDate.of(2026,11,16),s.date)
  assertEquals(2,s.count)
  assertEquals(110_000,s.totalFineKrw)
  assertEquals("오늘 과태료가 나올 뻔한 순간 2번, 합치면 최대 약 110,000원이었어요.",s.headline(Lang.KO))
  assertEquals("10:00 · School zone: 42 km/h in a 30 zone · ~70,000 won",s.lines(Lang.EN)[0])
  assertEquals("No close calls with fines today. Nice driving.",DaySummary.today(emptyList(),day).headline(Lang.EN))
  assertTrue(DaySummary.of(misses,LocalDate.of(2026,11,16)).headline(Lang.EN).contains("2 times"))
 }
}
