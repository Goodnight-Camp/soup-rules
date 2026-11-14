package com.goodnight.souprules

import android.app.Application
import com.goodnight.souprules.data.*
import com.goodnight.souprules.service.Coach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SoupApp:Application() {
 val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 val prefs by lazy { Prefs(this) }
 /** 서비스·화면·디버그 수신기가 같은 엔진과 음성을 쓰도록 앱에 하나만 둡니다. */
 val coach:Coach by lazy {
  Coach(prefs,NearMissStore(this),PleosVoice(this),ZoneRepository.load(this),
   { resources.configuration.locales[0]?.toLanguageTag() },scope)
 }
}
