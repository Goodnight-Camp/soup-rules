# 빌드된 APK

| 파일 | 내용 |
| --- | --- |
| `건실한 청년들_국Rule_release.apk` | 릴리스 빌드(versionName 0.1.0). 디버그 키로 서명했습니다 |

- 빌드일: 2026-11-15 (핫픽스 반영)
- 소스: `main` 핫픽스(콕핏 시나리오 구역을 서버가 앱에 전달) 시점. 사례 시나리오 B·C와 아이콘·효과음·헤드업 알림을 포함합니다
- 확인: 단위 테스트 100개 통과. 서명은 `apksigner verify`로 확인했습니다(CN=Android Debug)
- SHA-256: `925cba12f18f2560dfbaf4f8f6a774c2fcc792847ccb1c749ddd4e952b5b1a84`
- 크기: 약 38MB
- 릴리스 빌드라 가상 내비 콕핏 연결(DebugLink)과 adb 디버그 신호 수신기가 들어 있지 않습니다. 콕핏 데모에는 `.\gradlew.bat :app:assembleDebug`로 만든 `건실한 청년들_국Rule_debug.apk`를 쓰세요.

## 다시 만들기

```powershell
.\gradlew.bat :app:assembleRelease
# 결과: app/build/outputs/apk/release/건실한 청년들_국Rule_release.apk
```

파일 이름은 `app/build.gradle.kts`의 `teamName`·`appName`에서 정합니다. 정식 배포 키가 생기면 같은 파일의 `release` 서명 설정을 바꾸세요.
