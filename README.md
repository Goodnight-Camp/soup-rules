# 국Rule

외국인 렌터카 운전자에게 한국에만 있는 도로 규칙을 위반 직전에 운전자의 언어로 알려 주는 Pleos Connect 앱입니다.

## 실행 방법

1. APK를 설치하고 앱을 실행합니다.

   ```powershell
   adb install -r apk/souprules-debug.apk
   adb shell am start -n com.goodnight.souprules/.MainActivity
   ```

2. 기어 P 상태에서 위치·마이크·알림·차속 권한을 허용합니다. 권한을 허용하면 차량 신호를 받는 서비스가 시작됩니다.
3. 주행 장면 확인 방법은 두 가지입니다.
   - **앱 안 데모:** P 상태에서 **제주 데모 주행 재생**을 누릅니다.
   - **가상 내비 콕핏:**

     ```powershell
     cd tools/cockpit
     npm install
     node server.js
     adb reverse tcp:8765 tcp:8765
     ```

     브라우저에서 http://localhost:8765 를 열고 시나리오를 고른 뒤 **주행 시작**을 누릅니다.

소스에서 직접 빌드하려면 `.\gradlew.bat :app:assembleDebug`를 실행합니다. 결과물은 `app/build/outputs/apk/debug/`에 생깁니다.

## 테스트 환경 및 필요한 설정

| 항목 | 내용 |
| --- | --- |
| 빌드 도구 | Android Studio, Android SDK Platform 35, JDK 17(Gradle 툴체인이 자동 다운로드) |
| SDK 경로 | 프로젝트 루트 `local.properties`에 `sdk.dir=<Android SDK 경로>` |
| 의존성 | Pleos Maven 저장소(`settings.gradle.kts`에 설정됨)에 접속할 수 있는 네트워크 |
| Pleos SDK | Vehicle 2.3.2, SpeechToText 3.3.1, TextToSpeech 3.3.1, FusedLocation 1.1.3 |
| 앱 권한 | 정밀 위치, 마이크, 알림, `android.car.permission.CAR_SPEED` |
| 콕핏 | Node.js 18 이상 |
| 자동 테스트 | `.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug` |

AAOS는 사용자별로 권한을 관리합니다. 권한 창이 뜨지 않으면 아래처럼 직접 허용한 뒤 앱을 다시 실행합니다. `CAR_SPEED`, `RECORD_AUDIO`도 같은 방식입니다.

```powershell
adb shell pm grant --user 10 com.goodnight.souprules android.permission.ACCESS_FINE_LOCATION
```

## Pleos Connect 에뮬레이터 버전과 화면 규격

| 항목 | 값 |
| --- | --- |
| 에뮬레이터 | Pleos Connect Emulator v2.1 (Android 14, API 34) |
| 화면 | 2560 × 1440 가로, 160 dpi |

## APK 파일

[`apk/souprules-debug.apk`](apk/souprules-debug.apk)
