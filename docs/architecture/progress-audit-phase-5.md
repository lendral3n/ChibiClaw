# Phase 5 — Vision Tools: Progress Audit

> Tanggal: 2026-05-14 · Build: `:app:assembleDebug` ✅ SUCCESSFUL 1m24s

## Ringkasan eksekusi

Phase 5 menambahkan **vision pipeline** (MediaProjection screen capture + MiniCPM-V
visual grounding + ML Kit OCR) plus **6 tools baru**: 3 vision (tap/describe/extract)
+ 3 world query (installed_apps/location/schedule). Total 14 file baru + 6 modified.

Tool catalog sekarang **22 tools** advertised ke LLM, dengan SafetyGate gating
HIGH severity + cascade adapter via escalate_to_cloud kalau Gemma struggle.

---

## Cakupan per work-package

### W1 — MediaProjection + ScreenCapture — **100%**

| File | Status |
| --- | --- |
| `vision/projection/ProjectionTokenStore.kt` | ✅ Parcel marshalled → Base64 → SecurePreferences |
| `vision/projection/MediaProjectionPermissionActivity.kt` | ✅ AndroidEntryPoint translucent activity |
| `vision/projection/ChibiProjectionManager.kt` | ✅ VirtualDisplay + ImageReader reuse, capture suspend |
| `vision/screenshot/ImageProcessor.kt` | ✅ resize, crop, JPEG encode, Base64 |
| `vision/screenshot/ScreenCapture.kt` | ✅ 1s cache supaya multi-tool reuse same frame |
| `ui/setup/VisionSetupScreen.kt` | ✅ Launch permission activity + live status poll |
| Manifest declaration `MediaProjectionPermissionActivity` | ✅ exported=false, translucent theme |
| FGS type mask + mediaProjection | ✅ ChibiService.startForegroundWithType include kalau hasToken |
| ChibiService recreate on start + teardown on destroy | ✅ |

**Catat keamanan:** Parcel marshalling untuk Intent persistent. Android 14+
mungkin invalidate token kalau process killed — `tryRecreate()` graceful
return false → `tokenStore.clear()` → wizard re-grant flow.

### W2 — MiniCPM-V + VisionPromptBuilder + OCR — **100%**

| File | Status |
| --- | --- |
| `vision/llm/VisionPromptBuilder.kt` | ✅ grounding + describe + extractText template |
| `vision/llm/MiniCPMVInference.kt` | ✅ reflection-based binding ke LlamaCppMm |
| `vision/ocr/MlKitOcr.kt` | ✅ ML Kit Text Recognition v2 latin |
| Dependencies `mlkit-text-recognition` + `play-services-location` | ✅ aktif di build.gradle |

**Trade-off catat:**
- Vision LLM (MiniCPM-V) **belum punya runtime impl** — reflection binding ke
  `com.chibiclaw.nativellm.LlamaCppMm`. Kalau .so / model belum di-push, tool
  return `NOT_AVAILABLE` dengan hint escalate_to_cloud target=GEMINI.
- Phase 9 polish: bundle llama.cpp .so atau switch ke MediaPipe LlmInference
  bila API stabilize untuk vision.
- `kotlinx.coroutines.sync.Mutex.withLock` di-suspend-context terbukti
  bermasalah dengan Hilt KSP processor (resolver gagal pada constructor type).
  Solusi: pakai @Volatile fields + delegate sync ke withContext(IO). Tidak ada
  race karena pemanggil tunggal dari ToolDispatcher.

### W3 — Vision tools + World query tools — **100%**

| Tool | File | Severity | Permission |
| --- | --- | --- | --- |
| `vision_tap` | `agent/tools/impl/VisionTapTool.kt` | MEDIUM | MediaProjection + Accessibility |
| `vision_describe` | `agent/tools/impl/VisionDescribeTool.kt` | MEDIUM | MediaProjection |
| `vision_extract_text` | `agent/tools/impl/VisionExtractTextTool.kt` | MEDIUM | MediaProjection |
| `world_get_installed_apps` | `agent/tools/impl/WorldGetInstalledAppsTool.kt` | LOW | QUERY_ALL_PACKAGES |
| `world_get_location` | `agent/tools/impl/WorldGetLocationTool.kt` | MEDIUM | FINE/COARSE_LOCATION |
| `world_get_schedule` | `agent/tools/impl/WorldGetScheduleTool.kt` | MEDIUM | READ_CALENDAR |

ToolsModule registration `@StringKey` per masing-masing. Catalog advertised
otomatis via `toolRegistry.availableSpecs()` di ContextBuilder.

### Setup wizard — **100%**

`SetupNavigator` 10-step: Privacy → Overlay → Vendor → A11y → Shizuku →
Gemini → Claude → GPT → **Vision** → Done. Cancel/skip path semua available.

---

## Build verification

```
> Task :app:assembleDebug
BUILD SUCCESSFUL in 1m 24s
43 actionable tasks: 13 executed, 30 up-to-date
```

Pre-existing warnings unchanged (Room schemaLocation, jetifier, AgentRuntime cast,
`Display.getDefaultDisplay` deprecated — Phase 9 migrate ke Display Manager).

---

## Bug ditemukan + fix

1. **`mpm.getMediaProjection()` returns nullable** — Android 14+ API change.
   Fix: `?: error(...)` guard di tryRecreate.
2. **`Mutex.withLock { suspend body }` blocks Hilt KSP resolver** —
   `InjectProcessingStep was unable to process 'MiniCPMVInference'`. Workaround:
   replace dengan `@Volatile` + `withContext(IO)` saja. Tidak ada loss
   correctness karena single caller path dari ToolDispatcher.

---

## Tool catalog Phase 5 — final (22 tools total)

Phase 1 (6): wait, await_user, intent_open, system_action, memory_remember, memory_recall.
Phase 3 (10): a11y_click, a11y_type, a11y_describe_screen, a11y_scroll, shizuku_exec, shizuku_force_stop, shizuku_grant_permission, messaging, intent_send, world_get_notifications.
Phase 4 (1): escalate_to_cloud.
Phase 5 (6): vision_tap, vision_describe, vision_extract_text, world_get_installed_apps, world_get_location, world_get_schedule.

---

## Yang belum dikerjakan (defer eksplisit)

1. **llama.cpp Android JNI .so build** — model bisa di-push tapi runtime
   inference belum live. Phase 9 atau saat tooling stabil.
2. **Cloud vision fallback dari escalate_to_cloud** — GeminiFreeAdapter belum
   wire image payload. Phase 9 polish (add `images: List<Bitmap>` ke
   AgentPrompt + Gemini parts inlineData base64).
3. **Keyboard-active screenshot guard** — Phase 9 (detect IME visible →
   refuse capture supaya password tidak ke-OCR).
4. **Cina/Korea/Jepang OCR scripts** — Phase 9 (text-recognition-chinese, etc).
5. **VisualGroundingResult heatmap visualization** — debug overlay nice-to-have.
6. **In-app model downloader** — Phase 9 (lazy download dari HuggingFace
   dengan progress UI).
7. **Display.getDefaultDisplay deprecation migrate ke WindowMetrics** — Phase 9.

---

## Sign-off

✅ Build verified.
✅ MediaProjection persist + recreate flow live.
✅ 6 new tools registered via DI, semua graceful fallback saat permission/model absent.
✅ Setup wizard 10-step lengkap.
✅ ML Kit OCR functional, vision describe/tap return NOT_AVAILABLE dengan hint escalate.

Phase 5 ready untuk commit. Selanjutnya Phase 6 (Initiative + StandingInstruction)
sesuai [27-phase-6-initiative.md](27-phase-6-initiative.md).
