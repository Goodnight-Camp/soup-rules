# 빌드된 APK

| 파일 | 내용 |
| --- | --- |
| `souprules-debug.apk` | 디버그 빌드(versionName 0.1.0). 콕핏 연결(DebugLink)과 adb 디버그 신호가 들어 있습니다 |

- 빌드일: 2026-11-15
- 포함한 코드: `feat/cockpit-case-scenarios`(`6d310b8`, 사례 시나리오 B·C)에 `feat/alert-visuals-sound-main`(`f1c4d26`, PR #3 아이콘·효과음·헤드업 알림)을 합친 상태(로컬 병합 `9ea36b6`)
- 확인: 병합 상태에서 단위 테스트 100개 통과
- SHA-256: `464a17b0a7252303989c02319bc480c79267c01c794f75c7a5461eefd2785cd6`
- 크기: 약 55MB. 디버그 빌드라 축소(minify)를 하지 않았습니다

## 설치

```powershell
adb install -r apk/souprules-debug.apk
adb shell am start -n com.goodnight.souprules/.MainActivity
```

처음 실행할 때 P 상태에서 위치·마이크·알림·차속 권한을 허용합니다. AAOS는 사용자별 권한이라 안 되면 `adb shell pm grant --user 10 com.goodnight.souprules android.permission.ACCESS_FINE_LOCATION`(CAR_SPEED도 같은 방식)을 실행합니다.

## 주의

- 서명은 Android 디버그 키입니다. 배포용이 아닙니다.
- Pleos Connect 차량 또는 Pleos 에뮬레이터(`android.hardware.type.automotive`)에서만 설치됩니다.
- 코드가 바뀌면 이 파일은 자동으로 갱신되지 않습니다. `.\gradlew.bat :app:assembleDebug` 결과(`app/build/outputs/apk/debug/app-debug.apk`)를 다시 복사하세요.
