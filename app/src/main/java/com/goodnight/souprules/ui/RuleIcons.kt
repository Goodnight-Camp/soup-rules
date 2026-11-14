package com.goodnight.souprules.ui

import androidx.annotation.DrawableRes
import com.goodnight.souprules.R
import com.goodnight.souprules.core.RuleIcon

/** core의 [RuleIcon] → 벡터 아이콘. 화면(심각도 색으로 칠함)과 알림 작은 아이콘에 같이 씁니다. */
object RuleIcons {
 @DrawableRes fun of(icon:RuleIcon):Int=when(icon) {
  RuleIcon.SCHOOL->R.drawable.ic_rule_school
  RuleIcon.SLOW_DOWN->R.drawable.ic_rule_slow_down
  RuleIcon.RIGHT_TURN->R.drawable.ic_rule_right_turn
  RuleIcon.LEFT_TURN->R.drawable.ic_rule_left_turn
  RuleIcon.CROSSWALK->R.drawable.ic_rule_crosswalk
  RuleIcon.BUS_LANE->R.drawable.ic_rule_bus_lane
  RuleIcon.SCHOOL_BUS->R.drawable.ic_rule_school_bus
  RuleIcon.NO_STOPPING->R.drawable.ic_rule_no_stopping
  RuleIcon.PARKING_CAMERA->R.drawable.ic_rule_parking_camera
  RuleIcon.SEATBELT->R.drawable.ic_rule_seatbelt
  RuleIcon.ROUNDABOUT->R.drawable.ic_rule_roundabout
  RuleIcon.WRONG_WAY->R.drawable.ic_rule_wrong_way
  RuleIcon.TURN_SIGNAL->R.drawable.ic_rule_turn_signal
  RuleIcon.TOLL->R.drawable.ic_rule_toll
 }
}

/** 화면이 앞에 보이는지. 보이지 않을 때만 화면 위 알림(헤드업)을 띄웁니다. */
object AppVisibility {
 @Volatile var foreground=false
}
