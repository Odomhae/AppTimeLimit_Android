# Pull-to-Refresh & Background Resume Update Design

## Context

현재 appLimit은 백그라운드 서비스에서 3-10초마다 자동 폴링으로 사용량을 업데이트한다. 하지만:
- **사용자가 강제로 새로고침할 수 없다** → Pull-to-Refresh 필요
- **앱이 백그라운드에서 복귀할 때 즉시 업데이트되지 않을 수 있다** → onResume 후크 필요

이를 통해 사용자가 언제든 현재 사용량을 확인할 수 있도록 개선.

---

## Architecture

### 1. 라이브러리 추가

**libs.versions.toml:**
```toml
accompanist = "0.34.0"
```

**build.gradle.kts (app):**
```kotlin
dependencies {
    implementation("com.google.accompanist:accompanist-swiperefresh:${libs.versions.accompanist.get()}")
}
```

**선택 이유:**
- Google 공식 Material Design 라이브러리
- Material3와 호환성 우수
- 성숙하고 안정적

---

### 2. ViewModel 확장

**AppLimitViewModel.kt에 추가:**

```kotlin
fun refreshUsageNow() {
    viewModelScope.launch(Dispatchers.IO) {
        _isLoadingUsage.value = true
        try {
            // 현재 사용량 즉시 업데이트
            val currentLimits = limits.value
            if (currentLimits.isNotEmpty()) {
                _usageMap.value = currentLimits.associate { entity ->
                    entity.packageName to effectiveUsageMs(entity)
                }
            }
            _isPaused.value = PauseManager.isPaused(context)
        } finally {
            _isLoadingUsage.value = false
        }
    }
}
```

**동작:**
- `isLoadingUsage` 플래그로 로딩 상태 표시
- 사용량 맵 즉시 업데이트
- 폴링 주기를 기다리지 않음

---

### 3. HomeScreen UI 개선

**현재 구조:**
```
Column
  └─ PauseButton
  └─ Box (weight=1)
      └─ if empty: 빈 상태
      └─ else: LazyColumn (앱 리스트)
  └─ BannerAd
```

**변경 후:**
```
SwipeRefresh (onRefresh → viewModel.refreshUsageNow())
  └─ Column
      └─ PauseButton
      └─ Box (weight=1)
          └─ if empty: 빈 상태
          └─ else: LazyColumn
      └─ BannerAd
```

**구현 세부:**
- `rememberSwipeRefreshState(isRefreshing)` — 로딩 상태 추적
- `onRefresh = { viewModel.refreshUsageNow() }` — 당김 콜백
- SwipeRefresh 인디케이터가 자동으로 표시/숨김

---

### 4. MainActivity 백그라운드 복귀 처리

**MainActivity.kt에 추가:**

```kotlin
private val viewModel: AppLimitViewModel by viewModels()

override fun onResume() {
    super.onResume()
    // 앱 활성화 시 즉시 사용량 새로고침
    viewModel.refreshUsageNow()
}
```

**동작:**
- 사용자가 앱을 열거나 백그라운드에서 돌아올 때 실행
- 서비스의 자동 폴링을 기다리지 않고 즉시 업데이트

---

## Data Flow

```
┌─────────────────────────────────────────────────────────┐
│                  Pull-to-Refresh Flow                    │
├─────────────────────────────────────────────────────────┤
│ 1. 사용자 당김 (SwipeRefresh)                             │
│    ↓                                                      │
│ 2. onRefresh 콜백 → viewModel.refreshUsageNow()         │
│    ↓                                                      │
│ 3. isLoadingUsage = true                                 │
│    ↓                                                      │
│ 4. usageMap 즉시 업데이트 (effectiveUsageMs 계산)        │
│    ↓                                                      │
│ 5. isPaused 상태 확인                                     │
│    ↓                                                      │
│ 6. isLoadingUsage = false                                │
│    ↓                                                      │
│ 7. SwipeRefresh 인디케이터 자동 숨김                      │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│              백그라운드 복귀 Flow                          │
├─────────────────────────────────────────────────────────┤
│ 1. 앱 포어그라운드 복귀                                   │
│    ↓                                                      │
│ 2. MainActivity.onResume()                               │
│    ↓                                                      │
│ 3. viewModel.refreshUsageNow() (동일 로직)               │
└─────────────────────────────────────────────────────────┘
```

---

## 변경 파일

1. **libs.versions.toml** — accompanist 라이브러리 버전 추가
2. **build.gradle.kts (app)** — accompanist-swiperefresh 의존성 추가
3. **AppLimitViewModel.kt** — `refreshUsageNow()` 메서드 추가
4. **HomeScreen.kt** — SwipeRefresh로 Column 감싸기, import 추가
5. **MainActivity.kt** — `onResume()` 오버라이드, viewModel 주입

---

## Testing

### 단위 테스트
- `refreshUsageNow()`가 `isLoadingUsage` 상태를 올바르게 변경하는지
- usageMap이 즉시 업데이트되는지

### 통합 테스트
- HomeScreen에서 당겨서 새로고침 시 usageMap 업데이트
- 앱이 백그라운드에서 복귀 시 onResume 호출 확인

### 수동 테스트
1. 앱 실행 → HomeScreen 확인
2. 아래로 당기기 → 인디케이터 표시, 사용량 업데이트 확인
3. 백그라운드로 전환 (홈 버튼)
4. 다시 앱 실행 → 자동으로 사용량 새로고침 확인

---

## Notes

- accompanist는 Google이 공식 유지하는 라이브러리로 Material3 호환성이 우수함
- `refreshUsageNow()`는 기존 폴링과 독립적으로 작동하므로 중복 업데이트 가능 (문제 없음)
- `isLoadingUsage`는 이미 HomeScreen에서 LinearProgressIndicator로 사용 중이므로 추가 상태 필요 없음
