package com.goodnight.souprules.core

/**
 * 차량 음성 엔진이 실제로 어떤 로케일을 지원하는지 확인한 결과.
 * Pleos 문서·홍보 자료만으로는 앱용 STT/TTS SDK의 지원 언어를 알 수 없어(Gleo 비서의 다국어 지원과 별개),
 * 실차·에뮬레이터에서 직접 확인합니다.
 */
enum class ProbeOutcome {
 /** 말하기 끝(onDone) 또는 인식 시작(onStartedRecognition)까지 갔습니다. */
 OK,
 /** setLocale이 예외를 던졌습니다. */
 LOCALE_REJECTED,
 /** 엔진이 UNAVAILABLE_LANGUAGE를 돌려줬습니다. */
 UNAVAILABLE_LANGUAGE,
 /** 그 밖의 엔진 오류(마이크·권한 등). [VoiceProbeResult.detail] 참고. */
 ERROR,
 /** 제한 시간 안에 응답이 없었습니다. */
 TIMEOUT,
 /** 엔진 초기화가 안 돼 확인하지 못했습니다. */
 NOT_READY,
}

data class VoiceProbeResult(val tag:String,val lang:Lang,val tts:ProbeOutcome,val stt:ProbeOutcome,val ttsDetail:String?=null,val sttDetail:String?=null) {
 /** 운전자 언어로 쓸 수 있는지: 말하기가 되면 안내는 가능합니다(명령 인식은 STT까지 필요). */
 val speaks get()=tts==ProbeOutcome.OK
 val hears get()=stt==ProbeOutcome.OK
}

object VoiceProbe {
 /** 확인할 로케일: 모든 운전자 언어의 후보 로케일(중복 없이). */
 fun targets():List<Pair<String,Lang>> =Lang.entries.flatMap { l->l.voiceTags.map { it to l } }.distinctBy { it.first }

 /** 로그·화면용 표. */
 fun table(results:List<VoiceProbeResult>):String=buildString {
  appendLine("locale  | TTS                  | STT")
  results.forEach { r->
   append(r.tag.padEnd(8)).append("| ")
   append((r.tts.name+(r.ttsDetail?.let { "($it)" }?:"")).padEnd(21)).append("| ")
   appendLine(r.stt.name+(r.sttDetail?.let { "($it)" }?:""))
  }
 }.trimEnd()

 /** 한 줄 요약: "말하기 ja-JP, ko-KR · 듣기 ko-KR". */
 fun summary(results:List<VoiceProbeResult>):String {
  val speak=results.filter { it.speaks }.joinToString(", ") { it.tag }.ifEmpty { "없음" }
  val hear=results.filter { it.hears }.joinToString(", ") { it.tag }.ifEmpty { "없음" }
  return "음성 지원 확인 · 말하기: $speak · 듣기: $hear"
 }
}
