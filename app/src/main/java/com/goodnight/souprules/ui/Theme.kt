package com.goodnight.souprules.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodnight.souprules.core.Severity

/**
 * 주행 화면 다크 테마. Goodnight Camp 공용 DESIGN.md의 밤 토큰(깊은 네이비 바탕, 시안 강조)을 따릅니다.
 * 색은 안내 심각도에만 씁니다: 시안=안내, 호박=주의, 주홍=과태료 직전.
 */
object SoupColors {
 val Background=Color(0xFF0B1322)
 val Surface=Color(0xFF121D30)
 val SurfaceHigh=Color(0xFF1A2840)
 val Border=Color(0xFF22334D)
 val Accent=Color(0xFF00AAD2)
 val OnAccent=Color(0xFF06121F)
 val Text=Color(0xFFE8EEF6)
 val Muted=Color(0xFF8FA3BD)
 val Warn=Color(0xFFF5B83D)
 val Urgent=Color(0xFFFF6A4D)
 val Positive=Color(0xFF2BD98A)

 fun of(s:Severity)=when(s) { Severity.INFO->Accent; Severity.WARN->Warn; Severity.URGENT->Urgent }
}

private val scheme=darkColorScheme(
 primary=SoupColors.Accent,onPrimary=SoupColors.OnAccent,
 background=SoupColors.Background,onBackground=SoupColors.Text,
 surface=SoupColors.Surface,onSurface=SoupColors.Text,
 surfaceVariant=SoupColors.SurfaceHigh,onSurfaceVariant=SoupColors.Muted,
 outline=SoupColors.Border,outlineVariant=SoupColors.Border,
 error=SoupColors.Urgent,onError=Color.Black,
 surfaceContainer=SoupColors.Surface,surfaceContainerHigh=SoupColors.SurfaceHigh,surfaceContainerHighest=SoupColors.SurfaceHigh,
)

private fun type(size:Int,line:Int,weight:FontWeight=FontWeight.Normal)=TextStyle(fontFamily=FontFamily.SansSerif,fontSize=size.sp,lineHeight=line.sp,fontWeight=weight)

private val typography=Typography(
 displayLarge=type(96,104,FontWeight.Bold),displayMedium=type(56,64,FontWeight.Bold),
 headlineLarge=type(36,46,FontWeight.Bold),headlineMedium=type(30,40,FontWeight.Bold),
 titleLarge=type(24,32,FontWeight.Bold),titleMedium=type(20,28,FontWeight.Medium),
 bodyLarge=type(20,30),bodyMedium=type(18,26),bodySmall=type(15,22),
 labelLarge=type(18,24,FontWeight.Medium),labelMedium=type(15,20,FontWeight.Medium),
)

@Composable fun SoupTheme(content:@Composable ()->Unit) {
 MaterialTheme(colorScheme=scheme,typography=typography,
  shapes=Shapes(small=RoundedCornerShape(10.dp),medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(20.dp)),content=content)
}
