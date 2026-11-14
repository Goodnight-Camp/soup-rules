package com.goodnight.souprules.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.goodnight.souprules.service.CoachService

/**
 * 디버그 빌드 전용: adb로 차량 신호를 흉내 냅니다(release 매니페스트에는 없음).
 * 예) adb shell am broadcast -n com.goodnight.souprules/.debug.DebugSignalReceiver -a com.goodnight.souprules.DEBUG --es gear D --es speed 42 --es lat 33.4996 --es lng 126.5312
 * 값: gear(P/R/N/D) · turn(OFF/LEFT/RIGHT) · speed(km/h) · steer(도) · seat(0=운전석) + occupied/buckled(true/false) · lat,lng · event(demo|summary|lang|stt|voice_probe) + value
 * 음성 지원 확인: --es event voice_probe → logcat 태그 SoupVoiceProbe, files/voice-probe.txt
 */
class DebugSignalReceiver:BroadcastReceiver() {
 override fun onReceive(context:Context,intent:Intent) {
  val forward=Intent(context,CoachService::class.java).setAction(CoachService.DEBUG_SIGNAL)
  intent.extras?.let { forward.putExtras(it) }
  runCatching { context.startService(forward) }.onFailure { context.startForegroundService(forward) }
 }
}
