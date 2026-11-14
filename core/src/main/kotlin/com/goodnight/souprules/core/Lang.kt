package com.goodnight.souprules.core

import java.util.Locale

/**
 * 운전자 언어. 렌터카 이용 외국인 상위 국가(싱가포르·말레이시아·홍콩, 2018 제주)와 협약국 관광객·국내 거주 외국인을 기준으로 골랐습니다.
 * [voiceTags]는 그 언어의 로케일 후보입니다. 실제로 엔진에 넘기는 것은 [OfficialVoice]에 있는 로케일뿐이고,
 * 나머지 언어는 화면 문구만 운전자 언어로 보여 주고 음성은 영어로 안내합니다.
 */
enum class Lang(val tag:String,val nativeName:String,val voiceTags:List<String>) {
 EN("en","English",listOf("en-US")),
 FR("fr","Français",listOf("fr-CA")),
 ES("es","Español",listOf("es-US","es-MX")),
 KO("ko","한국어",listOf("ko-KR")),
 JA("ja","日本語",listOf("ja-JP")),
 ZH_HANS("zh-Hans","简体中文",listOf("zh-CN")),
 /** 대만·홍콩·마카오. 홍콩 음성(zh-HK)은 광둥어일 수 있어 대만 표준(zh-TW)을 먼저 시도합니다. */
 ZH_HANT("zh-Hant","繁體中文",listOf("zh-TW","zh-HK")),
 MS("ms","Bahasa Melayu",listOf("ms-MY")),
 TH("th","ไทย",listOf("th-TH")),
 VI("vi","Tiếng Việt",listOf("vi-VN")),
 ID("id","Bahasa Indonesia",listOf("id-ID"));

 val voiceLocales:List<Locale> get()=voiceTags.map(Locale::forLanguageTag)
 /** Pleos 공식 STT/TTS 로케일로 이 언어 음성을 낼 수 있는지. */
 val voiceSupported get()=voiceTags.any { it in OfficialVoice.locales }

 companion object {
  /** "zh-HK", "zh_TW", "zh-Hant-HK", "en-SG", "in-ID"(구형 인도네시아어 코드) 등 → Lang. */
  fun fromTag(tag:String?):Lang? {
   if(tag.isNullOrBlank()) return null
   val l=Locale.forLanguageTag(tag.trim().replace('_','-'))
   val language=l.language.lowercase().let { if(it=="in") "id" else it }
   return when(language) {
    "en"->EN; "ko"->KO; "ja"->JA; "ms"->MS; "th"->TH; "vi"->VI; "id"->ID; "fr"->FR; "es"->ES
    "zh"->when {
     l.script.equals("Hant",true)->ZH_HANT
     l.script.equals("Hans",true)->ZH_HANS
     l.country.uppercase() in setOf("TW","HK","MO")->ZH_HANT
     else->ZH_HANS
    }
    else->null
   }
  }
 }
}

/**
 * Pleos SpeechToText/TextToSpeech SDK 3.3.1이 공식 지원하는 로케일(STT·TTS 동일, Pleos 문서).
 * 목록 밖의 로케일은 엔진이 받아들이더라도 다른 언어 목소리로 읽을 수 있어 시도하지 않습니다.
 * 실제 차량에서 더 지원되는지는 디버그 voice_probe로 따로 확인합니다.
 */
object OfficialVoice {
 val locales=setOf("ko-KR","en-US","fr-CA","es-US","es-MX")
}

/** 면허 발급국(ISO 3166-1 alpha-2) → 기본 언어. 렌터카 체크인 정보로 언어를 미리 맞출 때 씁니다. */
object CountryLanguage {
 private val map=mapOf(
  "SG" to Lang.EN,"MY" to Lang.MS,"BN" to Lang.MS,
  "HK" to Lang.ZH_HANT,"TW" to Lang.ZH_HANT,"MO" to Lang.ZH_HANT,"CN" to Lang.ZH_HANS,
  "JP" to Lang.JA,"TH" to Lang.TH,"VN" to Lang.VI,"ID" to Lang.ID,"KR" to Lang.KO,
  // 프랑스어권(캐나다는 영어·프랑스어 공용이라 영어로 시작하고 직접 고르게 함)
  "FR" to Lang.FR,"BE" to Lang.FR,"LU" to Lang.FR,"MC" to Lang.FR,
  // 스페인어권
  "ES" to Lang.ES,"MX" to Lang.ES,"AR" to Lang.ES,"CL" to Lang.ES,"CO" to Lang.ES,"PE" to Lang.ES,"UY" to Lang.ES,"PY" to Lang.ES,
  "VE" to Lang.ES,"EC" to Lang.ES,"BO" to Lang.ES,"GT" to Lang.ES,"CR" to Lang.ES,"PA" to Lang.ES,"DO" to Lang.ES,"HN" to Lang.ES,
  "SV" to Lang.ES,"NI" to Lang.ES,"CU" to Lang.ES,
 )
 fun of(country:String?):Lang?=country?.trim()?.uppercase()?.takeIf { it.length==2 }?.let { map[it]?:Lang.EN }

 /**
  * 중국은 제네바(1949)·비엔나(1968) 도로교통협약 비가입국이라 국제운전면허로 국내 렌트가 어렵습니다.
  * 체크인 화면이 안내할 수 있게 표시만 하며 앱 기능은 막지 않습니다(국내 면허 보유 거주자는 이용 가능).
  */
 fun idpNotRecognized(country:String?)=country?.trim()?.uppercase()=="CN"
}

object LanguageResolver {
 /**
  * 우선순위: 운전자가 직접 고른 언어 → 렌터카 체크인으로 받은 언어 → 기기 언어(한국어 제외) → 영어.
  * 렌터카 차량의 기기 언어는 보통 한국어이고 이 앱의 대상은 외국인 운전자이므로, 기기 언어가 한국어면 영어로 시작합니다.
  */
 fun resolve(manual:Lang?,rental:Lang?,systemTag:String?):Lang=
  manual?:rental?:Lang.fromTag(systemTag)?.takeIf { it!=Lang.KO }?:Lang.EN
}
