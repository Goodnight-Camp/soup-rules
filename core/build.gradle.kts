plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies {
 // Android가 org.json을 플랫폼으로 제공하므로 APK에 넣지 않습니다. JVM 테스트에서만 실제 구현을 씁니다.
 compileOnly("org.json:json:20240303")
 testImplementation("org.json:json:20240303")
 testImplementation("junit:junit:4.13.2")
}
