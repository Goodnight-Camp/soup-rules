package com.goodnight.souprules.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.goodnight.souprules.core.Earcons
import com.goodnight.souprules.core.Severity

/**
 * 심각도별 효과음(긴급 3번·주의 2번·안내 1번). 소리 파일 없이 사인파를 만들어 냅니다.
 * 차량 오디오에서 내비 안내와 같은 취급을 받도록 USAGE_ASSISTANCE_NAVIGATION_GUIDANCE로 내고,
 * 재생하는 동안만 다른 소리를 줄이는(duck) 짧은 오디오 포커스를 잡습니다.
 */
class EarconPlayer(context:Context) {
 private val audio=context.getSystemService(AudioManager::class.java)
 private val main=Handler(Looper.getMainLooper())
 private val attrs=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
 private val rate=22050
 private val clips=Severity.entries.associateWith { Earcons.pcm(Earcons.of(it),rate) }
 private var track:AudioTrack?=null

 /** 재생하고 소리 길이(ms)를 돌려줍니다. 실패하면 0(음성은 바로 나갑니다). */
 fun play(severity:Severity):Long {
  val pcm=clips.getValue(severity)
  return runCatching {
   stop()
   val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()
   audio.requestAudioFocus(focus)
   val t=AudioTrack.Builder().setAudioAttributes(attrs)
    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
    .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size*2).build()
   t.write(pcm,0,pcm.size); t.play(); track=t
   val ms=Earcons.of(severity).totalMs.toLong()
   main.postDelayed({ if(track===t) stop(); audio.abandonAudioFocusRequest(focus) },ms+50)
   ms
  }.onFailure { Log.w("SoupEarcon","효과음 실패: ${it.message}") }.getOrDefault(0L)
 }

 fun stop() { track?.let { runCatching { it.stop(); it.release() } }; track=null }
}
