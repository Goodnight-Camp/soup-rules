package com.goodnight.souprules.core

import org.junit.Assert.*
import org.junit.Test

/** Pleos STT/TTS 공식 로케일(ko-KR, en-US, fr-CA, es-US, es-MX)에 맞춘 프랑스어·스페인어. */
class FrenchSpanishTest {
 @Test fun voiceSupportedMatchesOfficialList() {
  assertEquals(setOf(Lang.EN,Lang.KO,Lang.FR,Lang.ES),Lang.entries.filter { it.voiceSupported }.toSet())
 }

 @Test fun voiceChains() {
  assertEquals(listOf("fr-CA","en-US"),VoiceNegotiator(Lang.FR).chain.map { it.locale.toLanguageTag() })
  assertEquals(listOf("es-US","es-MX","en-US"),VoiceNegotiator(Lang.ES).chain.map { it.locale.toLanguageTag() })
  val es=VoiceNegotiator(Lang.ES)
  assertFalse(es.degraded)
  assertEquals("es-MX",es.fail()!!.locale.toLanguageTag()); assertFalse(es.degraded)
  assertEquals(Lang.EN,es.fail()!!.lang); assertTrue(es.degraded)
 }

 @Test fun tagsAndCountries() {
  assertEquals(Lang.FR,Lang.fromTag("fr-CA")); assertEquals(Lang.FR,Lang.fromTag("fr-FR"))
  assertEquals(Lang.ES,Lang.fromTag("es-MX")); assertEquals(Lang.ES,Lang.fromTag("es-419"))
  assertEquals(Lang.FR,CountryLanguage.of("FR")); assertEquals(Lang.FR,CountryLanguage.of("BE"))
  assertEquals(Lang.ES,CountryLanguage.of("MX")); assertEquals(Lang.ES,CountryLanguage.of("ES")); assertEquals(Lang.ES,CountryLanguage.of("AR"))
  assertEquals(Lang.EN,CountryLanguage.of("US")); assertEquals(Lang.EN,CountryLanguage.of("CA"))
  assertEquals(Lang.FR,LanguageResolver.resolve(null,null,"fr-CA"))
 }

 @Test fun rendering() {
  assertEquals("Ralentissez. 45 km/h dans une zone scolaire limitée à 30.",Strings.render(Lang.FR,Msg.SCHOOL_SLOW_DOWN,mapOf("speed" to "45","limit" to "30")))
  assertEquals("Baje la velocidad. Va a 45 km/h en una zona escolar de 30.",Strings.render(Lang.ES,Msg.SCHOOL_SLOW_DOWN,mapOf("speed" to "45","limit" to "30")))
  assertEquals("70,000 wons",Strings.amount(Lang.FR,70_000))
  val day=java.time.Instant.parse("2026-11-16T01:00:00Z")
  val s=DaySummary.today(listOf(NearMiss(day,RuleId.PARKING_CCTV,"c","C"),NearMiss(day,RuleId.TURN_NO_SIGNAL,null,null)),day)
  assertEquals("Hoy estuvo cerca de una multa 2 veces, hasta unos 70,000 wones en total.",s.headline(Lang.ES))
  assertEquals("Aujourd'hui, vous avez failli recevoir une amende 2 fois, jusqu'à environ 70,000 wons au total.",s.headline(Lang.FR))
 }

 @Test fun frenchCommands() {
  assertEquals(Command.Repeat,CommandParser.parse("Répète, s'il te plaît"))
  assertEquals(Command.Repeat,CommandParser.parse("encore"))
  assertEquals(Command.Summary,CommandParser.parse("Le résumé d'aujourd'hui"))
  assertEquals(Command.Mute,CommandParser.parse("Silence"))
  assertEquals(Command.Mute,CommandParser.parse("coupe le son"))
  assertEquals(Command.Unmute,CommandParser.parse("Remets le son"))
  assertEquals(Command.Help,CommandParser.parse("Aide"))
  assertEquals(Command.SwitchLanguage(Lang.FR),CommandParser.parse("En français"))
  assertEquals(Command.SwitchLanguage(Lang.ES),CommandParser.parse("en espagnol"))
 }

 @Test fun spanishCommands() {
  assertEquals(Command.Repeat,CommandParser.parse("Repite por favor"))
  assertEquals(Command.Repeat,CommandParser.parse("otra vez"))
  assertEquals(Command.Summary,CommandParser.parse("El resumen de hoy"))
  assertEquals(Command.Mute,CommandParser.parse("Silencio"))
  assertEquals(Command.Unmute,CommandParser.parse("Quita el silencio"))
  assertEquals(Command.Unmute,CommandParser.parse("Activa el sonido"))
  assertEquals(Command.Help,CommandParser.parse("Ayuda"))
  assertEquals(Command.SwitchLanguage(Lang.ES),CommandParser.parse("En español"))
  assertEquals(Command.SwitchLanguage(Lang.FR),CommandParser.parse("en francés"))
  assertEquals(Command.SwitchLanguage(Lang.EN),CommandParser.parse("in English"))
 }
}
