package com.goodnight.souprules.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import com.goodnight.souprules.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit

private const val TAG="SoupVoice"

/** 화면에 보여 줄 음성 상태. */
data class VoiceState(
 val wanted:Lang=Lang.EN,
 /** TTS가 실제로 말하는 언어. null이면 음성 안내 불가(화면만). */
 val speaking:Lang?=null,
 /** STT가 실제로 듣는 언어. */
 val listening:Lang?=null,
 val ttsReady:Boolean=false,
 val sttReady:Boolean=false,
 val muted:Boolean=false,
 val listeningNow:Boolean=false,
) {
 val degraded get()=ttsReady && speaking!=null && speaking!=wanted
 val unavailable get()=ttsReady.not() || speaking==null
}

interface Voice {
 val state:StateFlow<VoiceState>
 fun setLanguage(lang:Lang)
 fun setMuted(muted:Boolean)
 /** [render]는 TTS가 실제로 말하는 언어로 문장을 만듭니다(영어로 내려간 경우 영어 문구). */
 fun say(key:String,severity:Severity,render:(Lang)->String)
 /** 한 번 듣고 최종 결과를 돌려줍니다. 실패하면 null. */
 fun listen(onResult:(String?)->Unit)
 fun close()
}

/**
 * Pleos SpeechToText/TextToSpeech 3.3.1(ON_DEVICE)로 운전자 언어 음성을 냅니다.
 * - 로케일은 [VoiceNegotiator] 순서로 시도하고, setLocale 예외나 UNAVAILABLE_LANGUAGE 오류가 나면 다음 후보로 넘어가 같은 문장을 다시 만듭니다.
 * - 리스너는 initialize 성공 뒤에 붙입니다(초기화 전 등록 시 lateinit 예외, hyundai-autolog 실측).
 * - SDK 콜백은 바인더 스레드에서 올 수 있어 모두 메인 스레드로 옮깁니다.
 */
class PleosVoice(context:Context):Voice {
 private val main=Handler(Looper.getMainLooper())
 private val tts=TextToSpeech(context,TtsMode.ON_DEVICE)
 private val stt=SpeechToText(context,SttMode.ON_DEVICE)
 private val _state=MutableStateFlow(VoiceState())
 override val state=_state.asStateFlow()
 private var ttsNeg=VoiceNegotiator(Lang.EN)
 private var sttNeg=VoiceNegotiator(Lang.EN)
 private val queue=AnnouncementQueue()
 private val renderers=HashMap<String,(Lang)->String>()
 private var sttResult:((String?)->Unit)?=null
 private var sttRetried=false

 private val ttsEvents=object:EventListener {
  override fun onDone() { main.post { next() } }
  override fun onStop()=Unit
  override fun onStart()=Unit
  override fun onReady()=Unit
  override fun onUpdatedRms(rms:Double)=Unit
  override fun onError(error:TtsError) { main.post {
   Log.w(TAG,"TTS 오류 $error (${ttsNeg.current?.locale})")
   if(error==TtsError.UNAVAILABLE_LANGUAGE) { ttsNeg.fail(); applyTtsLocale(); queue.speaking?.let { respeak(it) } } else next()
  } }
 }

 private val sttEvents=object:ResultListener {
  override fun onUpdated(text:String,isFinal:Boolean) { if(isFinal) main.post { finishListen(text) } }
  override fun onError(error:SttError) { main.post {
   Log.w(TAG,"STT 오류 $error (${sttNeg.current?.locale})")
   if(error==SttError.UNAVAILABLE_LANGUAGE && !sttRetried) { sttRetried=true; sttNeg.fail(); if(applySttLocale()) { runCatching { stt.request() }.onFailure { finishListen(null) } } else finishListen(null) }
   else finishListen(null)
  } }
  override fun onUpdatedEpdData(start:Long,end:Long)=Unit
  override fun onStartedRecognition()=Unit
  override fun onEndedRecognition()=Unit
  override fun onReady()=Unit
  override fun onUpdatedRms(rms:Double)=Unit
 }

 init {
  runCatching { tts.initialize(object:TtsInit {
   override fun onSuccess() { main.post { tts.addEventListener(ttsEvents); _state.value=_state.value.copy(ttsReady=true); applyTtsLocale(); greet() } }
   override fun onFailure(error:TtsError) { Log.w(TAG,"TTS 초기화 실패 $error"); main.post { _state.value=_state.value.copy(ttsReady=false) } }
  }) }.onFailure { Log.w(TAG,"TTS 초기화 예외 ${it.message}") }
  runCatching { stt.initialize(object:SttInit {
   override fun onSuccess() { main.post { stt.addListener(sttEvents); _state.value=_state.value.copy(sttReady=true); applySttLocale() } }
   override fun onFailure(error:SttError) { Log.w(TAG,"STT 초기화 실패 $error"); main.post { _state.value=_state.value.copy(sttReady=false) } }
  }) }.onFailure { Log.w(TAG,"STT 초기화 예외 ${it.message}") }
 }

 override fun setLanguage(lang:Lang) {
  if(lang==_state.value.wanted && _state.value.speaking!=null) return
  ttsNeg=VoiceNegotiator(lang); sttNeg=VoiceNegotiator(lang)
  _state.value=_state.value.copy(wanted=lang)
  runCatching { tts.stop() }; queue.clear(); renderers.clear()
  if(_state.value.ttsReady) { applyTtsLocale(); greet() }
  if(_state.value.sttReady) applySttLocale()
 }

 /** 언어를 정하면 인사말을 먼저 말해 봅니다. 그 로케일이 안 되면 이 단계에서 다음 후보로 내려갑니다. */
 private fun greet() {
  val wanted=_state.value.wanted
  say("greeting",Severity.INFO) { spoken->
   val hello=Strings.render(spoken,Msg.GREETING)
   if(spoken==wanted) hello else "$hello ${Strings.render(spoken,Msg.VOICE_FALLBACK,mapOf("lang" to wanted.nativeName,"voice" to spoken.nativeName))}"
  }
 }

 /** 지금 후보 로케일을 TTS에 적용합니다. 예외가 나면 다음 후보. 후보가 없으면 음성 불가. */
 private fun applyTtsLocale():Boolean {
  while(true) {
   val opt=ttsNeg.current ?: run { _state.value=_state.value.copy(speaking=null); queue.clear(); return false }
   if(runCatching { tts.setLocale(opt.locale) }.onFailure { Log.w(TAG,"TTS 로케일 거부 ${opt.locale}: ${it.message}") }.isSuccess) {
    _state.value=_state.value.copy(speaking=opt.lang); return true
   }
   ttsNeg.fail()
  }
 }

 private fun applySttLocale():Boolean {
  while(true) {
   val opt=sttNeg.current ?: run { _state.value=_state.value.copy(listening=null); return false }
   if(runCatching { stt.setLocale(opt.locale) }.onFailure { Log.w(TAG,"STT 로케일 거부 ${opt.locale}: ${it.message}") }.isSuccess) {
    _state.value=_state.value.copy(listening=opt.lang); return true
   }
   sttNeg.fail()
  }
 }

 override fun setMuted(muted:Boolean) {
  _state.value=_state.value.copy(muted=muted)
  if(muted) { runCatching { tts.stop() }; queue.clear(); renderers.clear() }
 }

 override fun say(key:String,severity:Severity,render:(Lang)->String) {
  val s=_state.value
  val lang=s.speaking ?: return
  if(!s.ttsReady || (s.muted && key!="unmuted")) return
  val item=Announcement(render(lang),severity,key)
  when(val action=queue.offer(item)) {
   is QueueAction.Speak->{ renderers[key]=render; if(action.interrupt) runCatching { tts.stop() }; speakNow(action.item) }
   QueueAction.Queued->renderers[key]=render
   QueueAction.Dropped->Unit
  }
 }

 private fun speakNow(a:Announcement) {
  runCatching { tts.speak(a.text,true) }.onFailure { Log.w(TAG,"TTS speak 실패 ${it.message}"); main.post { next() } }
 }

 /** 언어가 바뀐 뒤(폴백) 같은 안내를 새 언어로 다시 말합니다. */
 private fun respeak(old:Announcement) {
  val lang=_state.value.speaking ?: return
  val render=renderers[old.key] ?: return next()
  queue.clear(); renderers.clear()
  say(old.key,old.severity,render)
  Log.i(TAG,"TTS 폴백 → $lang")
 }

 private fun next() {
  queue.speaking?.let { renderers.remove(it.key) }
  val n=queue.done() ?: return
  val render=renderers[n.key]; val lang=_state.value.speaking
  speakNow(if(render!=null && lang!=null) n.copy(text=render(lang)) else n)
 }

 override fun listen(onResult:(String?)->Unit) {
  val s=_state.value
  if(!s.sttReady || s.listening==null) { onResult(null); return }
  sttResult?.invoke(null)
  sttResult=onResult; sttRetried=false
  // 안내 음성을 마이크가 다시 듣지 않게 먼저 멈춥니다.
  runCatching { tts.stop() }; queue.clear(); renderers.clear()
  _state.value=s.copy(listeningNow=true)
  runCatching { stt.request() }.onFailure { Log.w(TAG,"STT 요청 실패 ${it.message}"); finishListen(null) }
  main.postDelayed({ if(sttResult===onResult) { runCatching { stt.stop() }; finishListen(null) } },8000)
 }

 private fun finishListen(text:String?) {
  val cb=sttResult ?: return
  sttResult=null
  _state.value=_state.value.copy(listeningNow=false)
  cb(text?.trim()?.takeIf { it.isNotEmpty() })
 }

 override fun close() {
  main.removeCallbacksAndMessages(null)
  runCatching { tts.stop(); stt.stop() }
  runCatching { tts.removeEventListener(ttsEvents); stt.removeListener(sttEvents) }
  runCatching { tts.release(); stt.release() }
 }
}

/** 음성 SDK가 없는 환경(테스트·화면 미리보기)용. */
class SilentVoice:Voice {
 override val state=MutableStateFlow(VoiceState())
 override fun setLanguage(lang:Lang) { state.value=state.value.copy(wanted=lang) }
 override fun setMuted(muted:Boolean) { state.value=state.value.copy(muted=muted) }
 override fun say(key:String,severity:Severity,render:(Lang)->String)=Unit
 override fun listen(onResult:(String?)->Unit)=onResult(null)
 override fun close()=Unit
}
