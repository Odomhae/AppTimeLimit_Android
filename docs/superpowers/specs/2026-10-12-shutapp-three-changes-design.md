# ShutApp 3가지 변경 설계

작성일: 2026-10-12

## 개요

ShutApp(com.odom.applimit)에 대한 3가지 동작 변경:

1. 차단 오버레이에서 "15분 추가(스누즈)" 버튼 제거
2. 권한 설정 화면을 "항목별 버튼"에서 "버튼 하나로 자동 연결되는 마법사"로 변경
3. 선택(활성화)된 제한 앱이 없을 때 포그라운드 "사용시간 체크 중" 알림을 띄우지 않음

DB 스키마 변경 없음. DB는 버전 4 유지.

---

## 변경 1 — 15분 추가(스누즈) 버튼 제거

### 범위

버튼과 그 버튼 전용 배선만 제거한다. **데이터 계층은 손대지 않는다.**

### 수정

- `overlay/BlockingOverlayManager.kt`
  - `show(...)`의 스누즈 `Button`(현재 110~118줄) 제거.
  - `show(...)` 시그니처에서 `onSnooze: () -> Unit` 파라미터 제거(이 버튼 전용이므로 함께 정리).
- `service/UsageMonitorService.kt`
  - `show(...)` 호출 시 넘기던 `onSnooze` 람다(현재 128~135줄의 블록) 제거.
  - `SNOOZE_MINUTES` 상수 제거.
  - `effectiveLimit = limit.limitMinutes + limit.snoozedMinutes` 계산식은 **그대로 유지**(DB 무변경 원칙). `snoozedMinutes`는 항상 0이므로 동작은 `limitMinutes`와 동일.
- `res/values/strings.xml`, `res/values-ko/strings.xml`
  - `snooze_button` 문자열은 남겨도 무해하나, 더 이상 참조되지 않으므로 제거한다(리소스 정리). 참조가 전부 사라졌는지 확인 후 제거.

### 유지(수정 금지)

- `data/AppLimitEntity.kt`의 `snoozedMinutes` 필드
- `data/AppLimitDao.kt`의 `resetDailyNotificationFlags()` SQL(`snoozedMinutes = 0` 포함)
- `data/AppDatabase.kt`의 버전(4)과 마이그레이션
- `ui/AppLimitViewModel.kt`의 `resetUsage()`(`snoozedMinutes = 0` 포함)

### 확인 기준

- 차단 오버레이에 "셧앱 열기" 버튼과 ✕ 닫기 버튼만 보인다.
- 빌드 성공, `onSnooze` 미사용 경고/오류 없음.
- 기존 차단/해제 동작은 변화 없음.

---

## 변경 2 — 자동 연결 권한 마법사

### 목표

3개 권한을 한 번의 사용자 동작(버튼 하나)으로 순차 요청한다. Android 제약상 단일 시스템 팝업으로 동시에 받을 수 없으므로(사용정보 접근·다른 앱 위에 표시는 설정 화면 경유 필수), 버튼 하나가 미허용 권한을 순서대로 자동 진행시킨다.

### 대상 파일

`ui/PermissionSetupScreen.kt` 재작성. `checkAllPermissionsGranted`, `hasUsageStatsPermission` 헬퍼는 그대로 재사용.

### 동작

- 상태: 어떤 권한이 허용됐는지 재조회하는 `buildPermissionList(context)` 결과를 보관.
- 화면 구성:
  - 안내 문구
  - 3개 항목 상태 리스트(각 항목에 ✓/○ 아이콘 + 제목). **항목별 허용 버튼 없음.**
  - 하단에 기본 버튼 하나(예: "권한 허용"). 모두 허용되면 비활성/숨김 또는 "완료".
- 버튼 클릭 시 **아직 허용되지 않은 첫 번째 권한**을 요청:
  1. 알림(`POST_NOTIFICATIONS`): Android 13+ 에서 `rememberLauncherForActivityResult(RequestPermission)`로 런타임 팝업. 13 미만이거나 이미 허용이면 건너뛰고 다음 단계로.
  2. 사용정보 접근(`PACKAGE_USAGE_STATS`): `Intent(ACTION_USAGE_ACCESS_SETTINGS)`를 `StartActivityForResult`로 실행.
  3. 다른 앱 위에 표시(`SYSTEM_ALERT_WINDOW`): `Intent(ACTION_MANAGE_OVERLAY_PERMISSION, package:...)`를 `StartActivityForResult`로 실행.
- 재확인/자동 진행:
  - 런타임 팝업 콜백에서 `buildPermissionList` 재조회.
  - 설정 화면은 콜백만으로는 신뢰도가 낮으므로 **`Lifecycle` `ON_RESUME` 옵저버**(`LifecycleEventObserver` + `LocalLifecycleOwner`)로 화면 복귀 시 재조회.
  - 재조회 후 `LaunchedEffect(permissions)`가 다음 미허용 권한이 있으면 자동으로 다음 단계를 트리거(사용자가 버튼을 다시 누르지 않아도 다음 설정 화면이 열림). 전부 허용이면 `onAllGranted()` 호출.
- 진행 표시: 상태 리스트의 ✓ 개수로 "2/3" 식 표현 가능(문자열 추가 선택).

### 설계 노트

- 자동 진행은 "미허용 권한이 남아 있고, 방금 한 단계가 끝났다"는 조건에서만 동작하도록 하여 무한 루프/중복 실행을 방지한다(진행 중 플래그 또는 단계 인덱스로 가드).
- 알림 권한은 영구 거부("다시 묻지 않음") 가능성이 있으므로, 거부되어도 흐름이 멈추지 않고 다음 권한으로 진행하며 상태 리스트에 ○로 남는다. 최종 `onAllGranted`는 모두 ✓일 때만 호출되므로 미허용 시 홈으로 넘어가지 않는다(기존 `NavGraph` 라우팅과 동일).

### 확인 기준

- 버튼 1회 클릭으로 알림 팝업 → (복귀) 사용정보 설정 → (복귀) 오버레이 설정 순으로 자동 진행된다.
- 이미 허용된 권한은 자동으로 건너뛴다.
- 세 권한 모두 허용되면 자동으로 홈으로 이동한다.
- 설정 화면에서 돌아올 때 상태가 즉시 갱신된다.

---

## 변경 3 — 선택된 앱이 없을 때 포그라운드 알림 안 띄우기

### 근거

포그라운드 서비스는 실행 중 알림 게시가 필수다. 따라서 "알림 숨김" = "감시 대상이 없으면 서비스를 실행하지 않음".

### 수정

- `service/UsageMonitorService.kt`
  - `getEnabledLimits()`가 비어 있으면 `stopForeground(STOP_FOREGROUND_REMOVE)` 후 `stopSelf()` 호출하는 로직 추가.
  - 실행 지점:
    - `onCreate`/`onStartCommand`의 최초 `startForeground` 직후 즉시 확인(비어 있으면 바로 종료 → 알림 잠깐 깜빡 후 사라짐, 허용됨).
    - 각 `monitorOnce()` 끝에서도 확인(마지막 앱 삭제 시 서비스가 스스로 종료).
  - `monitorOnce()`의 기존 "limits 비어 있으면 overlay.hide() 후 return" 분기에 종료 처리를 통합.
- `ui/AppLimitViewModel.kt`
  - `upsertLimit(...)`에서 제한 추가/갱신 성공 후 `UsageMonitorService`를 시작(`startForegroundService`/`startService`, 기존 `HomeScreen`의 기동 로직과 동일한 버전 분기). 빈 상태에서 첫 앱을 추가하면 서비스가 다시 시작된다.
- `ui/HomeScreen.kt`
  - 기존 `LaunchedEffect(Unit)` 서비스 기동은 유지. 제한이 없으면 서비스가 즉시 스스로 종료되므로 빈 홈에서는 알림이 잠깐 떴다가 사라진다.

### 범위 밖(영향 없음)

- 주말 일시정지(`PauseManager`): 앱이 선택되어 있는 한 일시정지 중에도 알림 유지.

### 확인 기준

- 활성 제한 앱이 0개일 때: 알림이 잠깐 보였다가 사라지고, 이후 지속 표시되지 않는다.
- 앱을 추가하면 서비스가 다시 시작되고 알림이 다시 표시되며 감시가 재개된다.
- 마지막 앱을 삭제하면 다음 `monitorOnce` 주기에 서비스가 종료되고 알림이 사라진다.

---

## 영향 받는 파일 요약

| 파일 | 변경 |
|---|---|
| `overlay/BlockingOverlayManager.kt` | 스누즈 버튼 + `onSnooze` 파라미터 제거 |
| `service/UsageMonitorService.kt` | `onSnooze` 람다·`SNOOZE_MINUTES` 제거; 빈 제한 시 `stopForeground`+`stopSelf` |
| `ui/PermissionSetupScreen.kt` | 자동 연결 권한 마법사로 재작성 |
| `ui/AppLimitViewModel.kt` | `upsertLimit` 성공 후 서비스 시작 |
| `ui/HomeScreen.kt` | 변경 없음(기존 `LaunchedEffect(Unit)` 서비스 기동 그대로 유지) |
| `res/values/strings.xml`, `res/values-ko/strings.xml` | `snooze_button` 제거, (선택)진행 표시 문자열 추가 |

DB(`AppLimitEntity`, `AppLimitDao`, `AppDatabase`) 및 `DailyResetWorker`는 수정하지 않는다.
