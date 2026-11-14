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
   // 가상 내비(tools/cockpit) 중계 서버 주소. adb reverse가 안 되는 에뮬레이터는 ws://10.0.2.2:8765 로 바꿉니다.
   buildConfigField("String", "COCKPIT_WS", "\"ws://localhost:8765\"")
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
