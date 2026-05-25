# DONOFF — 졸음 운전 방지 앱

> **D**on't fall **O**ff — 전면 카메라와 스마트밴드 / 갤럭시 워치를 활용해  
> 졸음·수면 상태를 실시간으로 감지하고 화면·진동·경고음으로 경보하는 Android 앱

---

## 목차

1. [소개](#-소개)
2. [스크린샷](#-스크린샷)
3. [주요 기능](#-주요-기능)
4. [기술 스택](#-기술-스택)
5. [아키텍처](#-아키텍처)
6. [프로젝트 구조](#-프로젝트-구조)
7. [실행 방법](#-실행-방법)
8. [민감 정보 처리](#-민감-정보-처리)
9. [라이선스](#-라이선스)

---

## 📖 소개

DONOFF는 **Android 스마트폰**과 **Galaxy Watch 4** 두 플랫폼으로 구성된 졸음 운전 방지 시스템입니다.

- **스마트폰 앱**: 전면 카메라로 MediaPipe Face Mesh를 실행해 EAR(Eye Aspect Ratio)를 실시간 계산, 눈 감김 패턴으로 졸음·수면 상태를 판별합니다.  
- **Wear OS 앱**: Galaxy Watch 4 내장 심박수 센서로 심박수를 모니터링하고, 롤링 평균 대비 급격한 하락 시 독립적으로 경보를 발생시킵니다.

---

## 📷 스크린샷

> `screenshots/` 폴더에 이미지를 추가하면 아래 링크가 자동으로 연결됩니다.

| 시작 화면 | 모니터링 화면 | 졸음 경보 | 워치 메인 |
|:---------:|:-------------:|:---------:|:---------:|
| ![splash](screenshots/splash.png) | ![state](screenshots/state.png) | ![alert](screenshots/alert.png) | ![watch](screenshots/watch.png) |

---

## ✨ 주요 기능

### 스마트폰 앱

#### 🔐 인증
- Firebase Authentication 기반 **이메일 회원가입 / 로그인**
- 비밀번호 찾기, 약관 동의 다이얼로그
- 스플래시 화면 및 자동 로그인 유지

#### 👁 피로 감지 (StateActivity)

| 단계 | 조건 | 화면 출력 |
|------|------|-----------|
| 눈 감음 | EAR < 0.13 | "Blink" 즉시 표시 |
| 1초 지속 | 계속 감고 있음 | "1" |
| 2초 지속 | 계속 감고 있음 | "2" |
| 3초 지속 | 계속 감고 있음 | "Long Closed Eyes!!" |
| 눈 뜸 | EAR ≥ 0.13 | 텍스트 즉시 초기화 |

**상태 전환 로직**

```
첫 번째 Long Closed Eyes → DROWSY (1분 타이머 시작)
  └─ 1분 이내 두 번째 Long Closed Eyes → SLEEP
  └─ 1분 경과 → 타이머 초기화 (다음 감지는 다시 DROWSY부터)
```

| 상태 | 텍스트 색상 | 화면 | 경보음 | 진동 |
|------|------------|------|--------|------|
| 정상 (Normal) | 흰색 | 투명 | 없음 | 없음 |
| 졸음 (Drowsy) | 노란색 | 빨간 오버레이 750ms 깜빡임 | 보통 (2.5초 주기) | 보통 (3초 주기) |
| 수면 (Sleep) | 빨간색 | 빨간 오버레이 180ms 깜빡임 | 강함 (0.8초 주기) | 강함 (1.2초 주기) |

> 각 경보는 30초 쿨다운이 적용되며, 설정 다이얼로그에서 항목별 ON/OFF 가능

#### ⚙️ 설정
- **얼굴화면 설정**: 눈·눈썹·동공·입술·Face Mesh·윤곽선 레이어 표시 여부 및 색상 개별 설정
- **알람 설정**: 화면 깜빡임 / 진동 / 경고음 독립 ON/OFF

#### 💓 심박수 모니터링
- BLE(Bluetooth Low Energy)로 스마트밴드와 연결
- 심박수 실시간 표시 및 라인 차트(MPAndroidChart)
- InfluxDB로 심박수 데이터 전송 및 이력 조회 그래프

#### 🗺 얼굴 가이드라인
- 화면 내 원형 가이드라인 안에 얼굴 위치·거리 적합 여부 실시간 판별
- 부적합 시 Face Mesh 비활성화 및 피로 감지 초기화

---

### ⌚ Wear OS 앱 (Galaxy Watch 4)

#### 화면 구성

| 화면 | 설명 | 주요 애니메이션 |
|------|------|----------------|
| **시작** | 심장 아이콘 터치로 시작 | 파문(Ripple) 3중 확장 + 심장 박동 |
| **로딩** | 센서 초기화 & 권한 요청 | ECG 파형이 좌→우로 이동 (AnimatedVectorDrawable) + 도트 텍스트 |
| **메인** | 실시간 BPM 표시 | 심박수 수신마다 심장 박동, 숫자 카운팅 애니메이션 |

#### 졸음 감지
- **롤링 평균** 최근 20개 샘플 기반
- 현재 심박수 < 평균 × **0.85** (15% 이상 하락) → 졸음 판정
- 최소 5개 샘플 수집 후 비교 시작
- 경보: 패턴 진동 + 긴급 경보음 1.5초
- **30초 쿨다운** 적용

---

## 🛠 기술 스택

### 공통
| 항목 | 기술 |
|------|------|
| 언어 | Kotlin |
| IDE | Android Studio |
| 비동기 | Kotlin Coroutines |
| 빌드 | Gradle (AGP 7.2.2) |

### 스마트폰 앱 (`app` 모듈)
| 항목 | 기술 |
|------|------|
| UI | ViewBinding, Material Components |
| 화면 전환 | Jetpack Navigation Component |
| 카메라 / 비전 | MediaPipe Face Mesh |
| 차트 | MPAndroidChart |
| 인증 | Firebase Authentication |
| 데이터 저장 | InfluxDB Kotlin Client |
| 연결 | Bluetooth Low Energy (GATT) |

### Wear OS 앱 (`wear` 모듈)
| 항목 | 기술 |
|------|------|
| 플랫폼 | Wear OS 3 (Galaxy Watch 4) |
| 심박수 | Android SensorManager (Sensor.TYPE_HEART_RATE) |
| UI | ViewBinding, androidx.wear |
| 애니메이션 | AnimatedVectorDrawable, ObjectAnimator, ArgbEvaluator |

---

## 🏗 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│                      스마트폰 앱                          │
│                                                         │
│  SplashActivity                                         │
│       │                                                 │
│  MainActivity ─── Navigation Graph                      │
│       ├── LoginStartFragment / LoginFragment            │
│       ├── RegisterFragment / RegisterSuccessFragment    │
│       ├── MainFragment                                  │
│       ├── ScanFragment          (BLE 스캔)              │
│       ├── GraphFragment                                  │
│       ├── GraphHrFragment       (심박수 그래프)          │
│       ├── SettingsFragment                              │
│       │     ├── FaceMeshSettingsDialog                  │
│       │     └── FatigueAlarmSettingsDialog              │
│       └── AccountFragment                               │
│                                                         │
│  StateActivity  ←─── (ScanFragment에서 Intent 이동)     │
│       ├── MediaPipe Face Mesh  (EAR 계산)               │
│       ├── BLE GATT Client      (심박수 수신)            │
│       ├── FatigueEyeState      (NORMAL / DROWSY / SLEEP)│
│       └── InfluxDB             (데이터 전송)            │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                   Wear OS 앱 (wear)                      │
│                                                         │
│  StartActivity → LoadingActivity → MainWatchActivity    │
│                       │                 │               │
│               SensorManager      RollingAverage         │
│            (TYPE_HEART_RATE)    + DrowsinessCheck       │
└─────────────────────────────────────────────────────────┘
```

---

## 📂 프로젝트 구조

```
DONOFF/
├── app/                                          # 스마트폰 앱 모듈
│   └── src/main/java/world/saloris/donoff/
│       ├── SplashActivity.kt
│       ├── MainActivity.kt
│       ├── StateActivity.kt                      # 피로 감지 핵심 로직
│       ├── LoginStartFragment.kt
│       ├── LoginFragment.kt
│       ├── RegisterFragment.kt
│       ├── RegisterSuccessFragment.kt
│       ├── FindPasswordFragment.kt
│       ├── FindPasswordDialog.kt
│       ├── TermsDialog.kt
│       ├── MainFragment.kt
│       ├── ScanFragment.kt
│       ├── GraphFragment.kt
│       ├── GraphHrFragment.kt
│       ├── SettingsFragment.kt
│       ├── FaceMeshSettingsDialog.kt
│       ├── FatigueAlarmSettingsDialog.kt
│       ├── AccountFragment.kt
│       ├── database/
│       │   ├── heartRate/  (HeartRate, HeartRateDao)
│       │   └── driverState/ (DriverState, DriverStateDao)
│       └── util/
│           ├── Constants.kt
│           ├── TutorialPrefs.kt
│           ├── user/       (Validator, MakeToast, OpenDialog)
│           ├── graph/      (차트 렌더러)
│           ├── facemesh/   (FaceMeshResultGlRenderer)
│           └── ble/        (BleListAdapter)
│
└── wear/                                         # Wear OS 앱 모듈
    └── src/main/java/world/saloris/donoff/wear/
        ├── StartActivity.kt
        ├── LoadingActivity.kt
        └── MainWatchActivity.kt
```

---

## ⚙️ 실행 방법

### 요구 사양

| 항목 | 사양 |
|------|------|
| 스마트폰 앱 minSdk | 23 (Android 6.0) |
| Wear OS 앱 minSdk | 30 (Wear OS 3) |
| 권장 기기 | Galaxy Watch 4 이상 |
| Android Studio | Arctic Fox 이상 |

### 1. 저장소 클론

```bash
git clone https://github.com/a3636a2000/Anti-Drowsy-Driving-App.git
cd Anti-Drowsy-Driving-App
```

### 2. Firebase 설정

1. [Firebase Console](https://console.firebase.google.com/)에서 Android 앱 등록
2. `google-services.json`을 `app/` 폴더에 추가  
   (참고: `app/google-services.json.example`)

### 3. InfluxDB 설정

```bash
# 예시 파일 복사
cp app/src/main/resources/influx2.properties.example \
   app/src/main/resources/influx2.properties
```

`influx2.properties`에 본인 환경의 URL · org · bucket · token 입력  
`app/src/main/java/.../util/Constants.kt`의 `ORGANIZATION`, `BUCKET`도 수정

### 4. 빌드 및 실행

Android Studio에서 **Gradle Sync** 후 대상 기기 선택:

- 스마트폰 앱 → `:app` 모듈 선택 후 Run
- 워치 앱 → `:wear` 모듈 선택 후 Galaxy Watch 4 연결 후 Run

---

## 🔒 민감 정보 처리

아래 파일은 `.gitignore`에 의해 저장소에서 제외됩니다.

| 파일 | 설명 |
|------|------|
| `app/google-services.json` | Firebase 실제 키 (→ `.example` 제공) |
| `app/src/main/resources/influx2.properties` | InfluxDB 토큰 (→ `.example` 제공) |
| `*.jks`, `*.keystore` | 서명 키스토어 |
| `keystore.properties`, `signing.properties` | 서명 설정 |
| `app/src/main/res/values-ko/` | 로컬 한글 리소스 |
| `app/src/main/res/font/notosans_kr_*.otf` | 로컬 전용 폰트 |

---

## 📄 라이선스

이 프로젝트는 [MIT License](LICENSE)를 따릅니다.
