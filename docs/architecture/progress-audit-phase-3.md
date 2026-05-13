# Phase 3 — Tools Mid: Progress Audit

> Tanggal: 2026-05-13 · Status build: `:app:assembleDebug` ✅ SUCCESSFUL · APK debug 54s

## Ringkasan eksekusi

Phase 3 — Tools Mid menambahkan **10 tools baru** ke catalog (accessibility, shizuku
privileged, world/messaging) plus **SafetyGate** untuk HIGH severity tools dengan
**ConfirmationOverlay** auto-deny 30 detik. Setup wizard ditambah 2 step (Accessibility
+ Shizuku) supaya user paham trade-off privileged access.

Target Phase 3 di [`24-phase-3-tools-mid.md`](24-phase-3-tools-mid.md): **100% delivered untuk W1–W3 + SafetyGate**, sisanya
(observability deep, per-tool unit test) di-defer ke Phase 9 sesuai keputusan user
("skip semua automated test, manual test di Phase 9 saja").

---

## Cakupan per work-package

### W1 — Accessibility Service + 4 a11y tools — **100%**

| File | Status |
| --- | --- |
| `accessibility/ChibiAccessibilityService.kt` | ✅ created |
| `accessibility/a11y/A11yTreeWalker.kt` | ✅ findNode / click / typeIntoFocused / scroll / tapAt |
| `agent/tools/impl/A11yClickTool.kt` | ✅ MEDIUM severity |
| `agent/tools/impl/A11yTypeTool.kt` | ✅ MEDIUM severity |
| `agent/tools/impl/A11yDescribeScreenTool.kt` | ✅ LOW severity, max 50 nodes |
| `agent/tools/impl/A11yScrollTool.kt` | ✅ MEDIUM severity |
| `AndroidManifest.xml` a11y declaration | ✅ service + config XML |
| `res/xml/accessibility_service_config.xml` | ✅ recreated (Phase 0 legacy deleted) |
| String resources `accessibility_service_*` | ✅ ID/EN |

**Trade-off catat:** TreeWalker pakai `AccessibilityNodeInfo` recursive search dengan
batas 50 nodes — cukup buat Compose/native, layout WebView besar bisa miss. Phase 5
vision-fallback untuk kasus ini.

### W2 — Shizuku privileged + 3 tools — **100%**

| File | Status |
| --- | --- |
| `permissions/ShizukuManager.kt` | ✅ singleton, lazy bind, mutex-protected |
| `permissions/ChibiShizukuService.kt` | ✅ UserService Stub (proses Shizuku UID 2000) |
| `aidl/com/chibiclaw/api/IChibiShizukuService.aidl` | ✅ exec(cmd, timeout) + destroy() |
| `agent/tools/impl/ShizukuExecTool.kt` | ✅ HIGH severity |
| `agent/tools/impl/ShizukuForceStopTool.kt` | ✅ HIGH severity |
| `agent/tools/impl/ShizukuGrantPermissionTool.kt` | ✅ HIGH severity |
| Gradle deps `shizuku-api` 13.1.5 + `shizuku-provider` | ✅ |

**Fix build:** secondary `constructor()` di `ChibiShizukuService` di-remove karena
clash dengan implicit primary dari `class … : IChibiShizukuService.Stub()`. Shizuku
binder hanya butuh no-arg constructor — implicit primary sudah cukup.

**Graceful fallback:** semua shizuku tool cek `ShizukuManager.acquireService()` —
kalau null (service tidak running atau permission belum granted), return
`ErrorClass.NOT_AVAILABLE` dengan `recoveryHint` ke setup wizard.

### W3 — Messaging + Intent + Notifications — **100%**

| File | Status |
| --- | --- |
| `agent/tools/impl/MessagingTool.kt` | ✅ SMS/WhatsApp/Telegram, HIGH severity |
| `agent/tools/impl/IntentSendTool.kt` | ✅ generic ACTION_*, MEDIUM |
| `agent/tools/impl/WorldGetNotificationsTool.kt` | ✅ LOW, snapshot dari NotificationListener |
| `accessibility/ChibiNotificationListener.kt` | ✅ buffer Last-N + isConnected() |

**Catat keamanan:** WorldGetNotificationsTool text dipotong 200 char di output —
defense in depth supaya OTP/banking secret tidak bocor full ke LLM context.
Tetap MEDIUM severity karena LLM bisa lihat title/text apa adanya.

### SafetyGate + ConfirmationOverlay — **100%**

| File | Status |
| --- | --- |
| `agent/tools/safety/SafetyGate.kt` | ✅ requestApproval suspend, 30s default timeout |
| `service/overlay/ConfirmationOverlay.kt` | ✅ bottom-sheet Compose dengan countdown |
| `service/overlay/OverlayWindowManager.kt` `showConfirmation()` | ✅ MATCH_PARENT + FLAG_DIM_BEHIND |
| `agent/tools/ToolDispatcher.kt` SafetyGate wiring | ✅ HIGH → request → ToolResult.UserDenied jika reject |
| `ToolResult.UserDenied` field `reason` | ✅ default string ID |

**UX:** Modal selalu di-render via Main dispatcher (`withContext(Dispatchers.Main)`
di SafetyGate) karena `WindowManager.addView` thread-affine.
Auto-deny coroutine pakai `delay(100ms)` loop supaya UI countdown smooth.

### Setup wizard polish — **100%**

| File | Status |
| --- | --- |
| `ui/setup/AccessibilitySetupScreen.kt` | ✅ launch Settings.ACTION_ACCESSIBILITY_SETTINGS |
| `ui/setup/ShizukuSetupScreen.kt` | ✅ live status (service + permission) via polling 1.5s |
| `ui/setup/SetupNavigator.kt` | ✅ 5-step (Privacy → Overlay → Vendor → A11y → Shizuku → Done) |
| `MainActivity` inject `ShizukuManager` | ✅ |

---

## Tools catalog Phase 3 — final

| Tool name | Severity | Permission required | LLM-visible |
| --- | --- | --- | --- |
| `a11y_click` | MEDIUM | Accessibility | ✅ |
| `a11y_type` | MEDIUM | Accessibility | ✅ |
| `a11y_describe_screen` | LOW | Accessibility | ✅ |
| `a11y_scroll` | MEDIUM | Accessibility | ✅ |
| `shizuku_exec` | **HIGH** | Shizuku | ✅ |
| `shizuku_force_stop` | **HIGH** | Shizuku | ✅ |
| `shizuku_grant_permission` | **HIGH** | Shizuku | ✅ |
| `messaging` | **HIGH** | SEND_SMS (SMS) | ✅ |
| `intent_send` | MEDIUM | none | ✅ |
| `world_get_notifications` | MEDIUM | NotificationListener | ✅ |

Catalog ter-advertise otomatis ke LLM via `ContextBuilder.build()` →
`toolRegistry.availableSpecs()`. Tidak ada hardcoded list di prompt.

---

## Build verification

```
> Task :app:assembleDebug
BUILD SUCCESSFUL in 54s
43 actionable tasks: 11 executed, 32 up-to-date
```

Warnings non-blocking:
- KSP Room export schema (pre-existing dari Phase 0)
- `AgentRuntime.kt:131` "No cast needed" (pre-existing dari Phase 1)
- AGP "jetifier deprecated" (pre-existing)

---

## Yang belum dikerjakan (defer eksplisit)

1. **Per-tool unit test** → Phase 9 (user decision: skip automated test).
2. **NotificationListener buffer persistence** → Phase 7 memory mature.
3. **Pre-authorize via StandingInstruction** → Phase 6 (placeholder
   `shouldGate()` sudah expose `task.channel` hook).
4. **a11y_describe_screen vision fallback** → Phase 5 (kalau tree kosong/oversize).
5. **Shizuku auto-detect Sui** → cukup `Shizuku.pingBinder()` saat ini; Sui
   compatibility tested manual di Phase 9.

---

## Risiko residual

- **Accessibility revoke**: kalau user disable A11y service di tengah task, tool
  return ErrorClass.NOT_AVAILABLE. AgentRuntime akan iterate dan LLM bisa
  decide pakai intent_send alternatif. Belum ada UI notice ke user — Phase 9 add.
- **Shizuku service restart**: kalau Shizuku process killed, binder cached di
  ShizukuManager invalid sampai tool re-call. `acquireService()` re-bind
  on-demand sehingga single-failure auto-recover.
- **ConfirmationOverlay collision**: dua HIGH tool simultan → second request
  langsung auto-deny (early-return `confirmView != null`). LLM observe error
  dan bisa retry. Bukan deadlock.

---

## Sign-off

✅ Build verified.
✅ Tools wired ke registry via DI.
✅ SafetyGate aktif untuk HIGH severity (dispatch path).
✅ Setup wizard 5-step lengkap.

Phase 3 ready untuk commit. Selanjutnya Phase 4 (Cloud Escalation) sesuai
roadmap di [`20-phase-roadmap.md`](20-phase-roadmap.md).
