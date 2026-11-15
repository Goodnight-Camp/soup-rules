plugins { id("com.android.application"); kotlin("android") }
android {
 namespace = "com.goodnight.souprules"
 compileSdk = 35
 defaultConfig { applicationId = "com.goodnight.souprules"; minSdk = 33; targetSdk = 34; versionCode = 1; versionName = "0.1.0"
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true; buildConfig = true }
 composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
 useLibrary("android.car")
 packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*") }
 lint { abortOnError = true; checkReleaseBuilds = false }
 buildTypes {
  debug {
   // 가상 내비(tools/cockpit) 중계 서버 주소. DebugLink가 쉼표로 나눠 순서대로 시도합니다.
   // 기본: 로컬(adb reverse)을 먼저, 안 되면 아래 원격으로 폴백. Render 배포 후 cockpitRemote에 주소만 채우면 됩니다.
   // 통째로 바꾸려면 빌드 시 -PcockpitWs=wss://<호스트> (목록 전체를 덮어씀).
   val cockpitRemote = "wss://dashboard-cockpit.onrender.com"  // Render 배포 서버
   val cockpitWs = (project.findProperty("cockpitWs") as String?)
    ?: listOf("ws://localhost:8765", cockpitRemote).filter { it.isNotBlank() }.joinToString(",")
   buildConfigField("String", "COCKPIT_WS", "\"$cockpitWs\"")
  }
 }
}
dependencies {
 implementation(project(":core"))
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
 implementation(platform("androidx.compose:compose-bom:2024.10.01"))
 implementation("androidx.compose.material3:material3")
 implementation("ai.pleos.playground:Vehicle:2.3.2")
 implementation("ai.pleos.playground:TextToSpeech:3.3.1")
 implementation("ai.pleos.playground:SpeechToText:3.3.1")
 implementation("ai.pleos.playground:FusedLocation:1.1.3")
 debugImplementation("com.squareup.okhttp3:okhttp:4.12.0")
 testImplementation("junit:junit:4.13.2")
}
