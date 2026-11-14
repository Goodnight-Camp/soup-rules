package com.goodnight.souprules.core

import org.junit.Assert.*
import org.junit.Test

class VoiceTest {
 private fun a(key:String,s:Severity=Severity.INFO)=Announcement(key,s,key)

 @Test fun speaksImmediatelyWhenIdle() {
  val q=AnnouncementQueue()
  assertEquals(QueueAction.Speak(a("x"),false),q.offer(a("x")))
 }

 @Test fun urgentInterruptsLessUrgent() {
  val q=AnnouncementQueue()
  q.offer(a("tip"))
  val u=a("speed",Severity.URGENT)
  assertEquals(QueueAction.Speak(u,true),q.offer(u))
  assertEquals(u,q.speaking)
 }

 @Test fun urgentDoesNotInterruptUrgent() {
  val q=AnnouncementQueue()
  q.offer(a("one",Severity.URGENT))
  assertEquals(QueueAction.Queued,q.offer(a("two",Severity.URGENT)))
  assertEquals("two",q.done()!!.key)
  assertNull(q.done())
 }

 @Test fun duplicatesDropped() {
  val q=AnnouncementQueue()
  q.offer(a("x")); assertEquals(QueueAction.Dropped,q.offer(a("x")))
  q.offer(a("y")); assertEquals(QueueAction.Dropped,q.offer(a("y")))
 }

 @Test fun higherSeverityJumpsQueueAndInfoIsDroppedOnOverflow() {
  val q=AnnouncementQueue(maxPending=2)
  q.offer(a("now",Severity.URGENT))
  q.offer(a("i1")); q.offer(a("w1",Severity.WARN))
  assertEquals(QueueAction.Queued,q.offer(a("w2",Severity.WARN)))
  assertEquals(2,q.pendingCount)
  assertEquals("w1",q.done()!!.key)
  assertEquals("w2",q.done()!!.key)
  assertNull(q.done())
 }

 @Test fun commands() {
  assertEquals(Command.Repeat,CommandParser.parse("Can you say that again?"))
  assertEquals(Command.Mute,CommandParser.parse("Please be quiet"))
  assertEquals(Command.Unmute,CommandParser.parse("unmute"))
  assertEquals(Command.Summary,CommandParser.parse("How did I do today"))
  assertEquals(Command.Help,CommandParser.parse("help"))
  assertEquals(Command.Unmute,CommandParser.parse("소리 켜 줘"))
  assertEquals(Command.Mute,CommandParser.parse("조용히 해"))
  assertEquals(Command.Unmute,CommandParser.parse("ミュート解除"))
  assertEquals(Command.Mute,CommandParser.parse("ミュートして"))
  assertEquals(Command.Unmute,CommandParser.parse("取消静音"))
  assertEquals(Command.Mute,CommandParser.parse("静音"))
  assertEquals(Command.Unmute,CommandParser.parse("เปิดเสียง"))
  assertEquals(Command.Mute,CommandParser.parse("ปิดเสียง"))
  assertEquals(Command.Unmute,CommandParser.parse("bật tiếng"))
  assertEquals(Command.Mute,CommandParser.parse("tắt tiếng"))
  assertEquals(Command.Summary,CommandParser.parse("ringkasan"))
  assertEquals(Command.Repeat,CommandParser.parse("ulang"))
  assertNull(CommandParser.parse("whatever"))
  assertNull(CommandParser.parse(""))
 }

 @Test fun languageSwitchByName() {
  assertEquals(Command.SwitchLanguage(Lang.MS),CommandParser.parse("Bahasa Melayu"))
  assertEquals(Command.SwitchLanguage(Lang.ID),CommandParser.parse("bahasa indonesia please"))
  assertEquals(Command.SwitchLanguage(Lang.ZH_HANT),CommandParser.parse("繁體中文"))
  assertEquals(Command.SwitchLanguage(Lang.ZH_HANS),CommandParser.parse("中文"))
  assertEquals(Command.SwitchLanguage(Lang.ZH_HANT),CommandParser.parse("Traditional Chinese"))
  assertEquals(Command.SwitchLanguage(Lang.EN),CommandParser.parse("영어로 바꿔 줘"))
  assertEquals(Command.SwitchLanguage(Lang.JA),CommandParser.parse("日本語"))
  assertEquals(Command.SwitchLanguage(Lang.TH),CommandParser.parse("ภาษาไทย"))
 }
}
