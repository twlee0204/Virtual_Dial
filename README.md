# Corner Dial (코너 다이얼) for Pleos Connect

> **SDV 대화면 디스플레이 환경에서 주행 중 시선 분산을 최소화하는 가상 코너 로터리 제어기**

---

## 📌 프로젝트 개요 (Overview)
* **목적**: 물리 버튼 축소로 늘어난 터치 조작 단계와 주행 중 전방 주시 태만을 방지하기 위해 디스플레이 모서리에 구현한 소프트웨어 다이얼 인터페이스
* **핵심 메커니즘**: 화면 구석 롱프레스(Hold) 후 원형 회전(Rotate) 제스처로 공조 및 미디어 볼륨 즉시 제어
* **타깃 플랫폼**: Hyundai Pleos Connect / Android Automotive OS (API 34, Android 14)

---

## ✨ 주요 기능 (Key Features)
* **Hidden Corner Overlay**: 평소에는 화면을 100% 온전하게 노출하며, 모서리 조작 시에만 활성화되는 비간섭형 시스템 오버레이
* **2-Step 오조작 방지**: 노면 진동으로 인한 오작동을 차단하기 위해 롱프레스 트리거(400ms~1s) + 극좌표계 원형 궤적 회전 감지 시에만 반응
* **Glanceable Center HUD**: 조작 시 손가락에 가려지지 않도록 화면 중앙에 대형 270도 Arc 게이지 팝업 및 자동 페이드아웃
* **Dual Zonal Control**: 좌측 코너(공조 온도 0.5℃ 스텝) / 우측 코너(미디어 볼륨) 독립 매핑
* **개인화 설정**: 3단계 조작 감도(낮음/보통/높음) 및 Pleos GUI 기반 4종 포인트 컬러 지원

---

## 🛠 기술 스택 (Tech Stack)
* **Language & Framework**: Kotlin, Android SDK (API 34 / Android 14)
* **Architecture**: MVVM, Android Lifecycle, Foreground Service (`FOREGROUND_SERVICE_SPECIAL_USE`)
* **Window & Touch**: `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`, Custom `TouchEventListener`
* **Test**: JUnit4 (단위 테스트), Espresso (계측/오버레이 터치 테스트)

---

## 📂 핵심 패키지 구조 (Project Structure)
* `MainActivity`: 반응형 대화면 콕핏 설정 UI 및 오버레이 권한 관리
* `RotaryOverlayService`: 백그라운드 상시 구동 및 시스템 전역 엣지 터치 패드 관리
* `CircularControlOverlayView`: 화면 중앙 팝업 HUD 및 수치 시각화 뷰
* `RotationTracker` / `CircularGestureTouchListener`: atan2 기반 극좌표 회전 연산, 데드존 필터링 및 햅틱(`CLOCK_TICK`) 피드백

---

## 🚀 실행 방법 (Getting Started)
1. **Repository Clone**
   ```bash
   git clone [https://github.com/](https://github.com/)[YOUR_USERNAME]/[REPO_NAME].git