# Corner Dial (코너 다이얼) for Pleos Connect

> **SDV 대화면 디스플레이 환경에서 주행 중 시선 분산을 최소화하는 가상 코너 로터리 제어기**

---

## 📱 앱 소개
* **앱 이름**: Corner Dial (코너 다이얼)
* **한 줄 소개**: 숨겨진 화면 코너 조작으로 주행 중 시선 분산 없이 안전하게 공조 및 미디어를 제어하는 가상 다이얼 앱
* **핵심 가치**: 물리 버튼 부재로 인한 전방 주시 태만을 방지하고, 화면 면적 낭비 없는 모서리 롱프레스(Hold) & 회전(Rotate) 인터랙션 제공

---

## 🖥 개발 및 테스트 환경 (Environment & Specs)
공모전 심사 및 검증 시 아래 환경을 권장합니다.

* **Pleos Connect 에뮬레이터 버전**: Pleos Connect v2.0 (Android 14 / API Level 34)
* **화면 규격 (Display Specs)**: 
  * 디스플레이 형태: 가로 와이드 콕핏 디스플레이 (Wide Center Information Display)
  * 해상도 / 밀도: 1920 x 720 (또는 사용하신 에뮬레이터 해상도 입력) / mdpi (가로 600dp 이상 Dual Dial Card Layout 대응)
* **필수 런타임 권한 (Required Permissions)**:
  * `SYSTEM_ALERT_WINDOW` (다른 앱 위에 그리기 권한)
  * `FOREGROUND_SERVICE` 및 `FOREGROUND_SERVICE_SPECIAL_USE` (백그라운드 안정 구동)

---

## 🚀 실행 방법 (Getting Started)

### 1. 소스코드 빌드 및 실행
```bash
# 1) 저장소 클론
git clone [https://github.com/](https://github.com/)[YOUR_USERNAME]/[REPO_NAME].git

# 2) 프로젝트 폴더 이동 후 디버그 APK 빌드 (Windows)
gradlew assembleDebug

# Mac / Linux
./gradlew assembleDebug