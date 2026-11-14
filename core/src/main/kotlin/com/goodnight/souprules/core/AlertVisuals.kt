package com.goodnight.souprules.core

/**
 * 규칙별 그림 아이콘. 글을 읽지 않아도(음성이 영어로 나가는 언어 사용자도) 무슨 안내인지 알 수 있게 합니다.
 * 앱은 같은 이름의 벡터 아이콘(ic_rule_*)을 화면과 알림에 씁니다.
 */
enum class RuleIcon { SCHOOL, SLOW_DOWN, RIGHT_TURN, LEFT_TURN, CROSSWALK, BUS_LANE, SCHOOL_BUS, NO_STOPPING, PARKING_CAMERA, SEATBELT, ROUNDABOUT, WRONG_WAY, TURN_SIGNAL, TOLL }

/** 한눈에 읽는 2~4단어 제목 + 아이콘. 긴 설명([Alert.msg])은 그 아래 작게 둡니다. */
data class AlertVisual(val icon:RuleIcon,val headline:Msg,val args:Map<String,String> =emptyMap())

object AlertVisuals {
 fun of(a:Alert):AlertVisual {
  val limit=a.args["limit"]?.let { mapOf("limit" to it) }.orEmpty()
  return when(a.rule) {
   RuleId.RIGHT_TURN_TIP,RuleId.RIGHT_TURN_NO_STOP->AlertVisual(RuleIcon.RIGHT_TURN,Msg.H_RIGHT_STOP)
   RuleId.RIGHT_TURN_ZONE->if(a.msg==Msg.RIGHT_TURN_ZONE_SIGNAL) AlertVisual(RuleIcon.RIGHT_TURN,Msg.H_RIGHT_ARROW) else AlertVisual(RuleIcon.RIGHT_TURN,Msg.H_RIGHT_STOP)
   RuleId.CROSSWALK_AFTER_TURN->AlertVisual(RuleIcon.CROSSWALK,Msg.H_CROSSWALK)
   RuleId.SCHOOL_ZONE_ENTER->AlertVisual(RuleIcon.SCHOOL,Msg.H_SCHOOL,limit)
   RuleId.SCHOOL_ZONE_SPEEDING->AlertVisual(RuleIcon.SLOW_DOWN,Msg.H_SLOW_DOWN,limit)
   RuleId.SCHOOL_ZONE_PARKING,RuleId.BUS_LANE_STOP,RuleId.NO_STOPPING->AlertVisual(RuleIcon.NO_STOPPING,Msg.H_NO_STOP)
   RuleId.SCHOOL_CROSSWALK,RuleId.SCHOOL_CROSSWALK_NO_STOP->AlertVisual(RuleIcon.CROSSWALK,Msg.H_STOP_CROSSWALK)
   RuleId.SCHOOL_BUS_TIP->AlertVisual(RuleIcon.SCHOOL_BUS,Msg.H_SCHOOL_BUS)
   RuleId.BUS_LANE_ACTIVE->AlertVisual(RuleIcon.BUS_LANE,Msg.H_BUS_LANE)
   RuleId.PARKING_CCTV,RuleId.PARKING_REMINDER->AlertVisual(RuleIcon.PARKING_CAMERA,Msg.H_PARKING_CAMERA)
   RuleId.PARKING_TIP->AlertVisual(RuleIcon.NO_STOPPING,Msg.H_PARKING_RULES)
   RuleId.TURN_NO_SIGNAL,RuleId.LANE_CHANGE_NO_SIGNAL->AlertVisual(RuleIcon.TURN_SIGNAL,Msg.H_SIGNAL)
   RuleId.SEATBELT_DRIVER,RuleId.SEATBELT_PASSENGER->AlertVisual(RuleIcon.SEATBELT,Msg.H_SEATBELT)
   RuleId.ROUNDABOUT_ENTER->AlertVisual(RuleIcon.ROUNDABOUT,Msg.H_ROUNDABOUT)
   RuleId.ROUNDABOUT_WRONG_WAY->AlertVisual(RuleIcon.WRONG_WAY,Msg.H_WRONG_WAY)
   RuleId.LEFT_TURN_TIP->AlertVisual(RuleIcon.LEFT_TURN,Msg.H_LEFT_ARROW)
   RuleId.UNPROTECTED_LEFT->AlertVisual(RuleIcon.LEFT_TURN,Msg.H_UNPROTECTED_LEFT)
   RuleId.TOLL_PLAZA->AlertVisual(RuleIcon.TOLL,Msg.H_TOLL)
   RuleId.TOLL_SLOW_DOWN->AlertVisual(RuleIcon.SLOW_DOWN,Msg.H_SLOW_DOWN,limit)
  }
 }

 fun headline(lang:Lang,a:Alert):String=of(a).let { Strings.render(lang,it.headline,it.args) }
}

/**
 * 심각도별 효과음. 언어와 상관없이 위급한 정도가 들리도록 음성 앞에 짧게 냅니다.
 * 긴급: 높은 음 3번, 주의: 중간 음 2번, 안내: 낮은 음 1번.
 */
data class EarconSpec(val beeps:Int,val freqHz:Double,val toneMs:Int,val gapMs:Int) {
 val totalMs get()=beeps*toneMs+(beeps-1)*gapMs
}

object Earcons {
 fun of(s:Severity)=when(s) {
  Severity.URGENT->EarconSpec(3,1320.0,120,70)
  Severity.WARN->EarconSpec(2,990.0,140,90)
  Severity.INFO->EarconSpec(1,660.0,160,0)
 }

 /** 16비트 모노 PCM. 딸깍 소리가 나지 않게 각 음의 앞뒤 8ms를 부드럽게 올리고 내립니다. */
 fun pcm(spec:EarconSpec,sampleRate:Int=22050,volume:Double=0.6):ShortArray {
  val tone=sampleRate*spec.toneMs/1000; val gap=sampleRate*spec.gapMs/1000; val fade=sampleRate*8/1000
  val out=ShortArray(spec.beeps*tone+(spec.beeps-1)*gap)
  for(b in 0 until spec.beeps) {
   val start=b*(tone+gap)
   for(i in 0 until tone) {
    val env=minOf(1.0,i/fade.toDouble(),(tone-1-i)/fade.toDouble())
    out[start+i]=(kotlin.math.sin(2*Math.PI*spec.freqHz*i/sampleRate)*env*volume*Short.MAX_VALUE).toInt().toShort()
   }
  }
  return out
 }
}
