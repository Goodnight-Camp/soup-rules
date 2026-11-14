package com.goodnight.souprules

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodnight.souprules.core.CountryLanguage
import com.goodnight.souprules.core.Lang
import com.goodnight.souprules.data.DemoDrive
import com.goodnight.souprules.service.CoachService
import com.goodnight.souprules.ui.AppVisibility
import com.goodnight.souprules.ui.CoachScreen
import com.goodnight.souprules.ui.ScreenActions
import com.goodnight.souprules.ui.SoupTheme

class MainActivity:ComponentActivity() {
 companion object {
  /**
   * 렌터카 체크인 연동: 대여 시스템(또는 직원 태블릿)이 차량에 보내는 시작 신호.
   * extras: "lang"(BCP-47, 예: ms-MY) 또는 "licenseCountry"(ISO 3166-1, 예: SG). 둘 다 없으면 기기 언어 규칙을 따릅니다.
   * "hipass"(true/false): 하이패스 단말 장착 여부. Pleos Toll API로는 카드 유무를 알 수 없어 렌터카 회사가 알려 줍니다.
   */
  const val ACTION_RENTAL_START="com.goodnight.souprules.RENTAL_START"
  const val PERMISSION_CAR_SPEED="android.car.permission.CAR_SPEED"
 }
 private val app get()=application as SoupApp
 private val missing=mutableStateOf(false)

 private val required get()=listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS,PERMISSION_CAR_SPEED)
 private fun refreshMissing() { missing.value=required.any { checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED } }

 private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshMissing(); CoachService.start(this) }

 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState)
  handle(intent)
  refreshMissing()
  if(missing.value) permissions.launch(required.toTypedArray()) else CoachService.start(this)
  val coach=app.coach
  setContent {
   SoupTheme {
    val ui by coach.ui.collectAsStateWithLifecycle()
    val voice by coach.voice.state.collectAsStateWithLifecycle()
    val permissionsMissing by missing
    CoachScreen(ui,voice,permissionsMissing,ScreenActions(
     onLanguage=coach::setLanguage,
     onMute=coach::setMuted,
     onAsk=coach::listen,
     onSummary={ coach.speakSummary(detailed=true) },
     onDemo={ coach.startDemo(DemoDrive(this)) },
     onGrant={ permissions.launch(required.toTypedArray()) },
    ))
   }
  }
 }

 override fun onNewIntent(intent:Intent) { super.onNewIntent(intent); setIntent(intent); handle(intent) }
 override fun onResume() { super.onResume(); refreshMissing() }
 // 화면이 보이는 동안에는 헤드업 알림을 띄우지 않습니다(같은 내용이 화면 카드로 나옴).
 override fun onStart() { super.onStart(); AppVisibility.foreground=true }
 override fun onStop() { AppVisibility.foreground=false; super.onStop() }

 private fun handle(intent:Intent?) {
  if(intent?.action!=ACTION_RENTAL_START) return
  val lang=Lang.fromTag(intent.getStringExtra("lang")) ?: CountryLanguage.of(intent.getStringExtra("licenseCountry"))
  val hipass=if(intent.hasExtra("hipass")) intent.getStringExtra("hipass")?.toBooleanStrictOrNull() ?: intent.getBooleanExtra("hipass",false) else null
  Log.i("SoupRental","렌트 시작 → ${lang?:"기기 언어 규칙"} · 하이패스 ${hipass?:"모름"}")
  app.coach.startRental(lang,hipass)
 }
}
