package com.goodnight.souprules.core

import java.util.Locale

/**
 * 운전자 언어. 렌터카 이용 외국인 상위 국가(싱가포르·말레이시아·홍콩, 2018 제주)와 협약국 관광객·국내 거주 외국인을 기준으로 골랐습니다.
 * [voiceTags]는 Pleos STT/TTS에 넘길 로케일 후보 순서입니다(앞에서부터 시도).
 */
enum class Lang(val tag:String,val nativeName:String,val voiceTags:List<String>) {
 EN("en","English",listOf("en-US","en-GB")),
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

 companion object {
  /** "zh-HK", "zh_TW", "zh-Hant-HK", "en-SG", "in-ID"(구형 인도네시아어 코드) 등 → Lang. */
  fun fromTag(tag:String?):Lang? {
   if(tag.isNullOrBlank()) return null
   val l=Locale.forLanguageTag(tag.trim().replace('_','-'))
   val language=l.language.lowercase().let { if(it=="in") "id" else it }
   return when(language) {
    "en"->EN; "ko"->KO; "ja"->JA; "ms"->MS; "th"->TH; "vi"->VI; "id"->ID
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

/** 면허 발급국(ISO 3166-1 alpha-2) → 기본 언어. 렌터카 체크인 정보로 언어를 미리 맞출 때 씁니다. */
object CountryLanguage {
 private val map=mapOf(
  "SG" to Lang.EN,"MY" to Lang.MS,"BN" to Lang.MS,
  "HK" to Lang.ZH_HANT,"TW" to Lang.ZH_HANT,"MO" to Lang.ZH_HANT,"CN" to Lang.ZH_HANS,
  "JP" to Lang.JA,"TH" to Lang.TH,"VN" to Lang.VI,"ID" to Lang.ID,"KR" to Lang.KO,
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
