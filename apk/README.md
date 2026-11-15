# 빌드된 APK

| 파일 | 내용 |
| --- | --- |
| `건실한 청년들_국Rule_release.apk` | 릴리스 빌드(versionName 0.1.0). 디버그 키로 서명했습니다. 제출·일반 실행용 |
| `건실한 청년들_국Rule_debug.apk` | 디버그 빌드. 가상 내비 콕핏 연결(DebugLink)과 adb 디버그 신호가 들어 있습니다. **콕핏 데모는 이 파일로 합니다** (SHA-256 `fcada3d87c041e566b1a47396fe70d2cf90bfa4005be0e3d4ab7e2d6b8bb41f0`, 약 47MB) |

- 빌드일: 2026-11-15 (핫픽스 반영)
- 소스: `main`(콕핏 진단: 차량 신호 서비스 자동 시작·웹 배지 반영 시점). 사례 시나리오 B·C, 아이콘·효과음·헤드업 알림, 시나리오 구역 전달을 포함합니다
- 확인: 단위 테스트 100개 통과. 서명은 `apksigner verify`로 확인했습니다(CN=Android Debug)
- SHA-256: `4756ec0dd72138814467542a081bfd2368447945200d166346c1560851d1bfd0`
- 크기: 약 38MB
- 위의 SHA-256·크기는 릴리스 파일 기준입니다. 릴리스 빌드에는 가상 내비 콕핏 연결(DebugLink)과 adb 디버그 신호 수신기가 들어 있지 않습니다. 콕핏 데모에는 디버그 파일을 쓰세요.

## 설치

```powershell
adb install -r "apk/건실한 청년들_국Rule_debug.apk"     # 콕핏 데모
adb install -r "apk/건실한 청년들_국Rule_release.apk"   # 제출·일반 실행
```

둘 다 같은 키로 서명해서 서로 덮어 설치할 수 있습니다. 같은 앱이므로 한 번에 하나만 설치됩니다.

## 다시 만들기

```powershell
.\gradlew.bat :app:assembleRelease :app:assembleDebug
# 결과: app/build/outputs/apk/release/건실한 청년들_국Rule_release.apk
#       app/build/outputs/apk/debug/건실한 청년들_국Rule_debug.apk
```

파일 이름은 `app/build.gradle.kts`의 `teamName`·`appName`에서 정합니다. 정식 배포 키가 생기면 같은 파일의 `release` 서명 설정을 바꾸세요.
