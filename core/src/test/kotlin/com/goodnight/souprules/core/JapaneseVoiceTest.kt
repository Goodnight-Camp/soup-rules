package com.goodnight.souprules.core

import org.junit.Assert.*
import org.junit.Test

/** 일본어 STT 결과는 한자·히라가나·가타카나 어느 표기로도 올 수 있습니다. */
class JapaneseVoiceTest {
 @Test fun repeatVariants() {
  listOf("もう一度","もういちど","もう1度お願いします","もっかい","繰り返して","くりかえして","リピート").forEach {
   assertEquals(it,Command.Repeat,CommandParser.parse(it))
  }
 }
 @Test fun summaryVariants() {
  listOf("まとめ","今日はどうだった","きょうのまとめ","サマリー").forEach { assertEquals(it,Command.Summary,CommandParser.parse(it)) }
 }
 @Test fun muteAndUnmuteVariants() {
  listOf("ミュート","みゅーと","静かに","しずかにして","黙って","音を消して","おとをけして").forEach { assertEquals(it,Command.Mute,CommandParser.parse(it)) }
  listOf("ミュート解除","みゅーとかいじょ","音声オン","音を出して","おとをだして").forEach { assertEquals(it,Command.Unmute,CommandParser.parse(it)) }
 }
 @Test fun helpVariants() {
  listOf("ヘルプ","へるぷ","助けて","たすけて").forEach { assertEquals(it,Command.Help,CommandParser.parse(it)) }
 }
 @Test fun languageNamesInJapanese() {
  assertEquals(Command.SwitchLanguage(Lang.EN),CommandParser.parse("英語にして"))
  assertEquals(Command.SwitchLanguage(Lang.EN),CommandParser.parse("えいごにして"))
  assertEquals(Command.SwitchLanguage(Lang.KO),CommandParser.parse("韓国語"))
  assertEquals(Command.SwitchLanguage(Lang.KO),CommandParser.parse("かんこくご"))
  assertEquals(Command.SwitchLanguage(Lang.ZH_HANS),CommandParser.parse("中国語"))
  assertEquals(Command.SwitchLanguage(Lang.ZH_HANT),CommandParser.parse("繁体字"))
  assertEquals(Command.SwitchLanguage(Lang.TH),CommandParser.parse("タイ語"))
  assertEquals(Command.SwitchLanguage(Lang.VI),CommandParser.parse("ベトナム語"))
  assertEquals(Command.SwitchLanguage(Lang.ID),CommandParser.parse("インドネシア語"))
  assertEquals(Command.SwitchLanguage(Lang.MS),CommandParser.parse("マレー語"))
  assertEquals(Command.SwitchLanguage(Lang.JA),CommandParser.parse("にほんご"))
 }
 @Test fun japaneseVoiceChainTriesJaJpThenEnglish() {
  assertEquals(listOf("ja-JP","en-US","en-GB"),VoiceNegotiator(Lang.JA).chain.map { it.locale.toLanguageTag() })
 }
 @Test fun probeTargetsAndReport() {
  val t=VoiceProbe.targets().map { it.first }
  assertEquals(t.distinct(),t)
  assertTrue(listOf("ja-JP","zh-CN","zh-TW","zh-HK","ko-KR","en-US").all { it in t })
  val r=listOf(VoiceProbeResult("ko-KR",Lang.KO,ProbeOutcome.OK,ProbeOutcome.OK),
   VoiceProbeResult("ja-JP",Lang.JA,ProbeOutcome.OK,ProbeOutcome.UNAVAILABLE_LANGUAGE),
   VoiceProbeResult("th-TH",Lang.TH,ProbeOutcome.LOCALE_REJECTED,ProbeOutcome.ERROR,sttDetail="MIC_ERROR"))
  assertEquals("음성 지원 확인 · 말하기: ko-KR, ja-JP · 듣기: ko-KR",VoiceProbe.summary(r))
  val table=VoiceProbe.table(r)
  assertTrue(table.contains("ja-JP   | OK                   | UNAVAILABLE_LANGUAGE"))
  assertTrue(table.contains("ERROR(MIC_ERROR)"))
 }
}
