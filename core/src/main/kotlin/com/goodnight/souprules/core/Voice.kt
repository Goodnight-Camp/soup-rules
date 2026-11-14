package com.goodnight.souprules.core

import java.util.Locale

/** 음성 엔진에 넘길 로케일과, 그 로케일로 말할 문구의 언어. */
data class VoiceOption(val locale:Locale,val lang:Lang)

/**
 * 운전자 언어 → Pleos STT/TTS 로케일 후보 순서.
 * 엔진이 UNAVAILABLE_LANGUAGE(또는 setLocale 예외)로 거부하면 [fail]로 다음 후보로 넘어갑니다.
 * 운전자 언어가 안 되면 영어로 말하고(문구도 영어), 영어도 안 되면 음성을 끄고 화면만 씁니다.
 * 한국어로는 내려가지 않습니다(외국인 운전자에게 의미가 없음). 운전자가 한국어를 고른 경우만 한국어입니다.
 */
class VoiceNegotiator(val wanted:Lang) {
 val chain:List<VoiceOption> =buildList {
  wanted.voiceLocales.forEach { add(VoiceOption(it,wanted)) }
  if(wanted!=Lang.EN) Lang.EN.voiceLocales.forEach { add(VoiceOption(it,Lang.EN)) }
 }
 private var index=0
 val current:VoiceOption? get()=chain.getOrNull(index)
 /** 운전자 언어가 아닌 언어로 말하고 있음(화면에 안내). */
 val degraded get()=current?.lang!=wanted
 val exhausted get()=current==null
 /** 현재 후보를 버리고 다음 후보를 돌려줍니다. 남은 후보가 없으면 null. */
 fun fail():VoiceOption? { if(index<chain.size) index++; return current }
}

/** 말할 문장 1건. [key]가 같은 안내는 겹쳐 쌓지 않습니다. */
data class Announcement(val text:String,val severity:Severity,val key:String)

sealed interface QueueAction {
 /** 바로 말하기. interrupt=true면 지금 말하던 것을 끊습니다. */
 data class Speak(val item:Announcement,val interrupt:Boolean):QueueAction
 data object Queued:QueueAction
 data object Dropped:QueueAction
}

/**
 * 주행 중 음성 안내 순서: 긴급(URGENT)은 덜 급한 말을 끊고 먼저, 같은 안내는 중복 없이, 대기열은 짧게.
 * 오래 쌓인 팁(INFO)은 상황이 지나 의미가 없으므로 넘치면 버립니다.
 */
class AnnouncementQueue(private val maxPending:Int=3) {
 var speaking:Announcement?=null; private set
 private val pending=ArrayList<Announcement>()
 val pendingCount get()=pending.size

 fun offer(a:Announcement):QueueAction {
  val now=speaking
  if(now?.key==a.key || pending.any { it.key==a.key }) return QueueAction.Dropped
  if(now==null) { speaking=a; return QueueAction.Speak(a,false) }
  if(a.severity==Severity.URGENT && now.severity<Severity.URGENT) { speaking=a; return QueueAction.Speak(a,true) }
  val at=pending.indexOfFirst { it.severity<a.severity }.let { if(it<0) pending.size else it }
  pending.add(at,a)
  while(pending.size>maxPending) {
   val drop=pending.indexOfLast { it.severity==Severity.INFO }.let { if(it<0) pending.lastIndex else it }
   val removed=pending.removeAt(drop)
   if(removed===a) return QueueAction.Dropped
  }
  return QueueAction.Queued
 }

 /** 지금 말하던 것이 끝났을 때. 다음에 말할 것을 돌려줍니다. */
 fun done():Announcement? { speaking=pending.removeFirstOrNull(); return speaking }

 fun clear() { speaking=null; pending.clear() }
}

sealed interface Command {
 data object Repeat:Command
 data object Summary:Command
 data object Mute:Command
 data object Unmute:Command
 data object Help:Command
 data class SwitchLanguage(val lang:Lang):Command
}

/**
 * STT 결과 → 명령. 운전자 언어로 말하지만, 어느 언어로 인식되든 같은 명령이 되도록 모든 언어의 낱말을 함께 봅니다.
 * "소리 켜"(UNMUTE)가 "꺼"(MUTE)보다, "ミュート解除"가 "ミュート"보다 먼저 맞도록 순서를 지킵니다.
 */
object CommandParser {
 private val languageNames:List<Pair<String,Lang>> =listOf(
  "bahasa indonesia" to Lang.ID,"indonesian" to Lang.ID,"indonesia" to Lang.ID,
  "bahasa melayu" to Lang.MS,"melayu" to Lang.MS,"malay" to Lang.MS,
  "english" to Lang.EN,"영어" to Lang.EN,"英語" to Lang.EN,"英语" to Lang.EN,"อังกฤษ" to Lang.EN,"tiếng anh" to Lang.EN,"inggeris" to Lang.EN,"inggris" to Lang.EN,
  "한국어" to Lang.KO,"korean" to Lang.KO,"韓国語" to Lang.KO,"韩语" to Lang.KO,"韓語" to Lang.KO,"เกาหลี" to Lang.KO,"tiếng hàn" to Lang.KO,
  "日本語" to Lang.JA,"にほんご" to Lang.JA,"japanese" to Lang.JA,"일본어" to Lang.JA,"日语" to Lang.JA,"日語" to Lang.JA,"ญี่ปุ่น" to Lang.JA,"tiếng nhật" to Lang.JA,
  "繁體" to Lang.ZH_HANT,"繁体" to Lang.ZH_HANT,"廣東話" to Lang.ZH_HANT,"广东话" to Lang.ZH_HANT,"粵語" to Lang.ZH_HANT,"traditional chinese" to Lang.ZH_HANT,"cantonese" to Lang.ZH_HANT,
  "简体" to Lang.ZH_HANS,"普通话" to Lang.ZH_HANS,"普通話" to Lang.ZH_HANS,"中文" to Lang.ZH_HANS,"mandarin" to Lang.ZH_HANS,"chinese" to Lang.ZH_HANS,"중국어" to Lang.ZH_HANS,"中国語" to Lang.ZH_HANS,
  "ภาษาไทย" to Lang.TH,"thai" to Lang.TH,"태국어" to Lang.TH,
  "tiếng việt" to Lang.VI,"vietnamese" to Lang.VI,"베트남어" to Lang.VI,
 )
 private val unmute=listOf("unmute","voice on","sound on","소리 켜","음성 켜","ミュート解除","音声オン","取消静音","打开声音","取消靜音","打開聲音","buka suara","hidupkan suara","bunyi semula","nyalakan suara","เปิดเสียง","bật tiếng","bật giọng")
 private val mute=listOf("mute","quiet","silence","be quiet","조용","음소거","소리 꺼","음성 꺼","ミュート","静かに","静音","安静","靜音","安靜","senyap","diam","bisukan","เงียบ","ปิดเสียง","im lặng","tắt tiếng")
 private val repeat=listOf("repeat","again","say that again","what","다시","반복","뭐라고","もう一度","繰り返","なんて","再说","重复","再說","重複","ulang","sekali lagi","อีกครั้ง","พูดซ้ำ","nhắc lại","lặp lại")
 private val summary=listOf("summary","today","how did i do","요약","오늘","まとめ","今日","总结","總結","今天","ringkasan","hari ini","สรุป","วันนี้","tóm tắt","hôm nay")
 private val help=listOf("help","도움","도와","ヘルプ","助けて","帮助","幫助","bantuan","tolong","ช่วย","trợ giúp","giúp")

 fun parse(text:String?):Command? {
  val t=text?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?:return null
  languageNames.firstOrNull { (name,_)->name in t }?.let { return Command.SwitchLanguage(it.second) }
  if(unmute.any { it in t }) return Command.Unmute
  if(mute.any { word(t,it) }) return Command.Mute
  if(summary.any { it in t }) return Command.Summary
  if(help.any { word(t,it) }) return Command.Help
  if(repeat.any { word(t,it) }) return Command.Repeat
  return null
 }

 /** 라틴 문자 낱말은 낱말 경계로("what"이 "whatever"에 맞지 않게), 그 밖의 문자는 부분 일치로 봅니다. */
 private fun word(text:String,w:String):Boolean {
  if(w.any { it.code>0x24F }) return w in text
  return Regex("(^|[^\\p{L}])${Regex.escape(w)}($|[^\\p{L}])").containsMatchIn(text)
 }
}
