package com.goodnight.souprules.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.goodnight.souprules.core.*
import com.goodnight.souprules.data.VoiceState
import com.goodnight.souprules.service.CoachUiState
import kotlinx.coroutines.delay

/** 안내 카드는 이 시간 동안 보여 주고 "안내 없음"으로 돌아갑니다. */
private const val ALERT_VISIBLE_MS=15_000L

data class ScreenActions(
 val onLanguage:(Lang)->Unit,
 val onMute:(Boolean)->Unit,
 val onAsk:()->Unit,
 val onSummary:()->Unit,
 val onDemo:()->Unit,
 val onGrant:()->Unit,
)

@Composable
fun CoachScreen(ui:CoachUiState,voice:VoiceState,permissionsMissing:Boolean,actions:ScreenActions) {
 val lang=ui.lang
 fun t(m:Msg,args:Map<String,String> =emptyMap())=Strings.render(lang,m,args)
 var picker by remember { mutableStateOf(false) }
 var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
 LaunchedEffect(Unit) { while(true) { delay(1000); now=SystemClock.elapsedRealtime() } }
 // 주행 중에는 언어 선택 창을 닫습니다(입력은 P에서만, 음성 명령은 언제나).
 LaunchedEffect(ui.parked) { if(!ui.parked) picker=false }

 // Pleos 분할 화면: 배정된 창 크기에 맞춰 넓으면 2열, 좁으면 1열(스크롤), 낮거나 좁으면 작은 글자로 바꿉니다.
 BoxWithConstraints(Modifier.fillMaxSize().background(SoupColors.Background).testTag("root")) {
  val layout=ScreenLayout.of(maxWidth,maxHeight)
  val gap=if(layout.compact) 10.dp else 16.dp
  Column(Modifier.fillMaxSize().padding(horizontal=if(layout.compact) 12.dp else 24.dp,vertical=if(layout.compact) 10.dp else 18.dp),verticalArrangement=Arrangement.spacedBy(gap)) {
   TopBar(ui,voice,::t,onLanguage={ if(ui.parked) picker=true },actions,layout)
   if(permissionsMissing) Banner(t(Msg.PERMISSION_NEEDED),SoupColors.Warn,action=t(Msg.GRANT),onAction=actions.onGrant)
   if(voice.degraded) Banner(t(Msg.VOICE_FALLBACK,mapOf("lang" to voice.wanted.nativeName,"voice" to (voice.speaking?.nativeName?:""))),SoupColors.Muted)
   else if(voice.unavailable && voice.ttsReady.not()) Banner(t(Msg.VOICE_UNAVAILABLE),SoupColors.Muted)
   ui.error?.let { Banner(it,SoupColors.Muted) }
   val shown=ui.current?.takeIf { now-it.shownAtElapsed<ALERT_VISIBLE_MS }
   if(layout.twoColumns) {
    Row(Modifier.fillMaxWidth().weight(1f).testTag("layout-wide"),horizontalArrangement=Arrangement.spacedBy(gap)) {
     Column(Modifier.weight(1.45f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(gap)) {
      AlertCard(shown?.alert,lang,::t,layout,Modifier.weight(1f).fillMaxWidth())
      SpeedRow(ui,layout)
     }
     TodayCard(ui,::t,actions,layout,fill=true,modifier=Modifier.weight(1f).fillMaxHeight())
    }
   } else {
    // 좁은 분할 창: 지금 안내를 맨 위에 두고 아래로 스크롤합니다.
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).testTag("layout-stacked"),verticalArrangement=Arrangement.spacedBy(gap)) {
     AlertCard(shown?.alert,lang,::t,layout,Modifier.fillMaxWidth().heightIn(min=if(layout.compact) 96.dp else 160.dp))
     SpeedRow(ui,layout)
     TodayCard(ui,::t,actions,layout,fill=false,modifier=Modifier.fillMaxWidth())
    }
   }
  }
 }
 if(picker) LanguagePicker(lang,onPick={ picker=false; actions.onLanguage(it) },onDismiss={ picker=false })
}

@Composable private fun TopBar(ui:CoachUiState,voice:VoiceState,t:(Msg,Map<String,String>)->String,onLanguage:()->Unit,actions:ScreenActions,layout:ScreenLayout) {
 val title=@Composable {
  Column {
   Text(t(Msg.APP_NAME,emptyMap()),style=if(layout.compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,color=SoupColors.Text,maxLines=1)
   val status=when { ui.demo->Msg.DEMO_RUNNING; !ui.carConnected->Msg.STATUS_WAITING; ui.parked->Msg.STATUS_PARKED; else->Msg.STATUS_DRIVING }
   Text(t(status,emptyMap()),style=MaterialTheme.typography.bodySmall,color=SoupColors.Muted,maxLines=1)
  }
 }
 val minTouch=if(layout.compact) 56.dp else 64.dp
 // 언어는 P에서만 바꿉니다. 주행 중에는 버튼이 흐려지고 음성 명령(언어 이름)으로 바꿀 수 있습니다.
 @Composable fun Buttons(m:Modifier) {
  OutlinedButton(onClick=onLanguage,enabled=ui.parked,modifier=m.heightIn(min=minTouch).testTag("language")) {
   Text(if(layout.narrow) ui.lang.nativeName else "${t(Msg.LANGUAGE,emptyMap())} · ${ui.lang.nativeName}",style=MaterialTheme.typography.labelLarge,maxLines=1)
  }
  OutlinedButton(onClick={ actions.onMute(!voice.muted) },modifier=m.heightIn(min=minTouch).testTag("mute")) {
   Text(t(if(voice.muted) Msg.VOICE_OFF else Msg.VOICE_ON,emptyMap()),style=MaterialTheme.typography.labelLarge,maxLines=1)
  }
  Button(onClick=actions.onAsk,enabled=voice.sttReady && !voice.listeningNow,modifier=m.heightIn(min=minTouch).testTag("ask")) {
   Text(t(if(voice.listeningNow) Msg.LISTENING else Msg.ASK,emptyMap()),style=MaterialTheme.typography.labelLarge,maxLines=1)
  }
 }
 if(layout.narrow) {
  // 좁은 창: 제목 아래에 버튼 줄을 나눠 놓습니다.
  Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
   title()
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) { Buttons(Modifier.weight(1f)) }
  }
 } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
  Box(Modifier.weight(1f)) { title() }
  Buttons(Modifier)
 }
}

/** 창 크기에 따른 배치. Pleos 분할 화면에서 창이 줄면 1열·작은 글자로 바꿉니다. */
data class ScreenLayout(val twoColumns:Boolean,val narrow:Boolean,val compact:Boolean) {
 companion object {
  fun of(width:Dp,height:Dp)=ScreenLayout(
   twoColumns=width>=840.dp && height>=480.dp,
   narrow=width<600.dp,
   compact=width<480.dp || height<480.dp,
  )
 }
}

@Composable private fun Banner(text:String,color:androidx.compose.ui.graphics.Color,action:String?=null,onAction:()->Unit={}) {
 Row(Modifier.fillMaxWidth().background(SoupColors.Surface,RoundedCornerShape(12.dp)).border(1.dp,SoupColors.Border,RoundedCornerShape(12.dp)).padding(horizontal=16.dp,vertical=10.dp),
  verticalAlignment=Alignment.CenterVertically) {
  Text(text,style=MaterialTheme.typography.bodyMedium,color=color,modifier=Modifier.weight(1f))
  if(action!=null) TextButton(onClick=onAction,modifier=Modifier.heightIn(min=56.dp)) { Text(action,style=MaterialTheme.typography.labelLarge) }
 }
}

@Composable private fun AlertCard(alert:Alert?,lang:Lang,t:(Msg,Map<String,String>)->String,layout:ScreenLayout,modifier:Modifier) {
 val color=alert?.let { SoupColors.of(it.severity) }?:SoupColors.Border
 // 왼쪽 심각도 띠는 drawBehind로 그려, 높이가 내용에 따라 정해지는 1열 배치에서도 끝까지 채웁니다.
 Box(modifier.background(SoupColors.Surface,RoundedCornerShape(20.dp)).border(2.dp,color,RoundedCornerShape(20.dp))
  .drawBehind { drawRect(color,size=Size(14.dp.toPx(),size.height)) }.testTag("alert"),contentAlignment=Alignment.CenterStart) {
  Column(Modifier.fillMaxWidth().padding(start=if(layout.compact) 26.dp else 42.dp,end=if(layout.compact) 14.dp else 28.dp,top=if(layout.compact) 14.dp else 28.dp,bottom=if(layout.compact) 14.dp else 28.dp)) {
   if(alert==null) {
    Text(t(Msg.ALL_CLEAR,emptyMap()),style=if(layout.compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,color=SoupColors.Muted)
   } else {
    Text(Strings.render(lang,alert),style=if(layout.compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge,color=SoupColors.Text)
    val fine=Fines.of(nearMissRule(alert.rule),alert.args["speed"]?.toIntOrNull(),alert.args["limit"]?.toIntOrNull())
    if(fine>0) Text("~${Strings.amount(lang,fine)}",style=MaterialTheme.typography.titleMedium,color=color,modifier=Modifier.padding(top=12.dp))
   }
  }
 }
}

/** 안내 → 그 안내가 막으려는 과태료 종류. */
private fun nearMissRule(r:RuleId)=when(r) {
 RuleId.RIGHT_TURN_ZONE,RuleId.RIGHT_TURN_TIP->RuleId.RIGHT_TURN_NO_STOP
 RuleId.PARKING_REMINDER->RuleId.PARKING_CCTV
 else->r
}

@Composable private fun SpeedRow(ui:CoachUiState,layout:ScreenLayout) {
 val speed=ui.vehicle.speedKmh
 val limit=ui.schoolLimit
 val over=limit!=null && speed!=null && speed>=limit+1
 Row(Modifier.fillMaxWidth().background(SoupColors.Surface,RoundedCornerShape(20.dp)).padding(horizontal=if(layout.compact) 16.dp else 28.dp,vertical=if(layout.compact) 8.dp else 16.dp),verticalAlignment=Alignment.CenterVertically) {
  Text(speed?.toInt()?.toString()?:"—",style=if(layout.compact) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayMedium,color=if(over) SoupColors.Urgent else SoupColors.Text,modifier=Modifier.testTag("speed"))
  Text(" km/h",style=MaterialTheme.typography.titleMedium,color=SoupColors.Muted)
  Spacer(Modifier.weight(1f))
  Text(when(ui.vehicle.gear) { Gear.UNKNOWN->"–"; else->ui.vehicle.gear.name },style=MaterialTheme.typography.headlineMedium,color=SoupColors.Muted,modifier=Modifier.padding(end=24.dp))
  if(limit!=null) Box(Modifier.size(if(layout.compact) 60.dp else 84.dp).background(androidx.compose.ui.graphics.Color.White,CircleShape).border(8.dp,SoupColors.Urgent,CircleShape),contentAlignment=Alignment.Center) {
   Text("$limit",style=MaterialTheme.typography.headlineMedium,color=androidx.compose.ui.graphics.Color.Black,fontWeight=FontWeight.Bold)
  }
 }
}

/** [fill]=true면 남은 높이를 채우고 목록만 스크롤합니다(2열). false면 내용 높이만큼(1열, 바깥이 스크롤). */
@Composable private fun TodayCard(ui:CoachUiState,t:(Msg,Map<String,String>)->String,actions:ScreenActions,layout:ScreenLayout,fill:Boolean,modifier:Modifier) {
 val lang=ui.lang
 Column(modifier.background(SoupColors.Surface,RoundedCornerShape(20.dp)).padding(if(layout.compact) 14.dp else 24.dp).testTag("today"),verticalArrangement=Arrangement.spacedBy(if(layout.compact) 8.dp else 12.dp)) {
  Row(verticalAlignment=Alignment.CenterVertically) {
   Text(t(Msg.TODAY,emptyMap()),style=MaterialTheme.typography.titleLarge,color=SoupColors.Text,modifier=Modifier.weight(1f))
   Text("${ui.today.count}",style=if(layout.compact) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayMedium,color=if(ui.today.count>0) SoupColors.Urgent else SoupColors.Positive,modifier=Modifier.testTag("count"))
  }
  Text(ui.today.headline(lang),style=MaterialTheme.typography.bodyLarge,color=SoupColors.Text,modifier=Modifier.testTag("headline"))
  Column(if(fill) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier,verticalArrangement=Arrangement.spacedBy(8.dp)) {
   ui.today.lines(lang).forEach { Text(it,style=MaterialTheme.typography.bodyMedium,color=SoupColors.Muted) }
  }
  if(ui.today.count>0) Text(t(Msg.FINE_NOTE,emptyMap()),style=MaterialTheme.typography.bodySmall,color=SoupColors.Muted)
  if(ui.parked && !ui.demo) {
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
    OutlinedButton(onClick=actions.onSummary,modifier=Modifier.heightIn(min=56.dp)) { Text("▶ ${t(Msg.TODAY,emptyMap())}",style=MaterialTheme.typography.labelLarge,maxLines=1) }
    OutlinedButton(onClick=actions.onDemo,modifier=Modifier.heightIn(min=56.dp).weight(1f).testTag("demo")) { Text(t(Msg.DEMO_START,emptyMap()),style=MaterialTheme.typography.labelLarge,textAlign=TextAlign.Center) }
   }
  }
  if(ui.sampleZones) Text(t(Msg.SAMPLE_NOTICE,emptyMap()),style=MaterialTheme.typography.bodySmall,color=SoupColors.Warn)
 }
}

@Composable private fun LanguagePicker(current:Lang,onPick:(Lang)->Unit,onDismiss:()->Unit) {
 Dialog(onDismissRequest=onDismiss) {
  Column(Modifier.widthIn(max=760.dp).background(SoupColors.SurfaceHigh,RoundedCornerShape(20.dp)).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
   Text(Strings.render(current,Msg.LANGUAGE),style=MaterialTheme.typography.titleLarge,color=SoupColors.Text)
   // 분할 창 폭에 맞춰 열 수가 바뀝니다.
   LazyVerticalGrid(GridCells.Adaptive(180.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    items(Lang.entries) { l->
     val selected=l==current
     Button(onClick={ onPick(l) },modifier=Modifier.heightIn(min=72.dp).testTag("lang-${l.name}"),
      colors=if(selected) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(containerColor=SoupColors.Surface,contentColor=SoupColors.Text)) {
      // 🔊 = 이 차량(Pleos 공식 로케일)에서 음성 안내까지 되는 언어. 나머지는 화면은 그 언어, 음성은 영어입니다.
      Text(if(l.voiceSupported) "${l.nativeName} 🔊" else l.nativeName,style=MaterialTheme.typography.titleMedium,textAlign=TextAlign.Center)
     }
    }
   }
  }
 }
}
