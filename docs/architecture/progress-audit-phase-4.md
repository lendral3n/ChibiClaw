# Phase 4 — Cloud Escalation: Progress Audit

> Tanggal: 2026-05-14 · Build: `:app:assembleDebug` ✅ SUCCESSFUL 50s

## Ringkasan eksekusi

Phase 4 menambahkan **3 cloud adapter** + **InferenceRouter cascade** + **quota tracker (Room)**
+ **`escalate_to_cloud` tool** + **3 setup wizard step** (Gemini key, Claude WebView,
GPT WebView) + **AI Engine Settings screen**. Total ~13 file baru + 7 modified.

LLM dapat emit `escalate_to_cloud(reason, target)` → SafetyGate (MEDIUM severity) konfirmasi user →
Router pin per-task adapter → iterasi berikutnya AgentRuntime pakai cloud adapter.

---

## Cakupan per work-package

### W1.1 — GeminiFreeAdapter — **100%**

| File | Status |
| --- | --- |
| `ai/llm/adapters/GeminiFreeAdapter.kt` | ✅ POST `gemini-2.5-flash:generateContent`, responseMimeType=json |
| OkHttp transport + safetySettings BLOCK_ONLY_HIGH | ✅ |
| Quota integration: hasQuota / recordSuccess / markExhausted | ✅ |
| HTTP error mapping (401/403=AUTH_EXPIRED, 429=RATE_LIMITED+exhaust, 5xx=NETWORK) | ✅ |

API key di `SecurePreferences["gemini_api_key"]`. Daily quota 1500 tracked.

### W1.2 — InferenceRouter cascade — **100%**

Cascade order: Pin → Gemma local → Gemini free → Claude web → GPT web → Stub.
`escalate(target, taskId)` pin task ke target, fail-soft kalau tidak available.
`allAdapters()` expose ke Settings UI untuk status display.

### W1.3 — AdapterQuotaTracker + Room — **100%**

| File | Status |
| --- | --- |
| `data/database/ModelConfigEntity.kt` | ✅ kotlinx.datetime.Instant fields |
| `data/database/ModelConfigDao.kt` | ✅ CRUD + incrementUsage + resetDaily + markExhausted |
| `data/database/AppDatabase.kt` | ✅ v2 → v3 (fallbackToDestructive) |
| `di/AppModule.kt` provideModelConfigDao | ✅ |
| `ai/llm/AdapterQuotaTracker.kt` | ✅ daily reset by calendar date local TZ |

Schema v3 — `model_config` table. Daily reset trigger: `today > resetDate`.

### W1.4 — escalate_to_cloud tool — **100%**

| File | Status |
| --- | --- |
| `agent/tools/impl/EscalateToolHandler.kt` | ✅ MEDIUM severity, preAuthorizable |
| `ToolsModule` registration `@StringKey("escalate_to_cloud")` | ✅ |
| `ToolDispatcher` stamp `__taskId` ke args sebelum execute | ✅ |

Tool delegate ke `router.escalate(target, taskId)`. Success returns
`{switched_to, display_name, reason}` — LLM observe lalu lanjut iterasi pakai pinned.

### W1.5 — Setup wizard Gemini — **100%**

`GeminiSetupScreen.kt` — open `aistudio.google.com/apikey` via Intent VIEW,
paste field tersedia, save ke EncryptedSharedPreferences. Skip option ada.

### W2.1 — CloudLoginWebView + SessionExtractor — **100%**

| File | Status |
| --- | --- |
| `ai/llm/webview/CloudLoginWebView.kt` | ✅ AndroidView + JS bridge `window.ChibiBridge.onExtracted(json)` |
| `ai/llm/webview/SessionExtractor.kt` | ✅ Cookie + JS payload → ClaudeWebSession/GPTWebSession persist |
| `CloudLoginScripts.CLAUDE_EXTRACT` | ✅ fetch /api/organizations + chat_conversations + meta |
| `CloudLoginScripts.GPT_EXTRACT` | ✅ fetch /api/auth/session → accessToken + user.id |

CookieManager.getCookie(url) di-pair dengan JS-extracted data (orgId, clientSha,
userAgent). Persist sebagai JSON di SecurePreferences.

### W2.2 — ClaudeWebAdapter — **100%**

| File | Status |
| --- | --- |
| `ai/llm/session/CloudSession.kt` | ✅ ClaudeWebSession + GPTWebSession data classes |
| `ai/llm/adapters/ClaudeWebAdapter.kt` | ✅ POST /chat_conversations/{conv}/completion, SSE parse |

Headers: Cookie, anthropic-client-sha, anthropic-client-version, anthropic-device-id,
User-Agent. SSE aggregation untuk completion text.

### W2.3 — GPTWebAdapter — **100%**

`ai/llm/adapters/GPTWebAdapter.kt` — POST `/backend-api/conversation`, Bearer
accessToken + Cookie. SSE parse extracts longest `parts[0]` text across frames.

### W2.4 — CloudSessionRotator — **100%**

`ai/llm/adapters/CloudSessionRotator.kt` — `validateClaude()` ping
`/api/organizations`, `validateGPT()` ping `/api/auth/session`. Update
`lastValidatedAtMs` saat sukses. Untuk Settings UI manual + future WorkManager
24h periodic (defer Phase 9 polish).

### Setup wizard cloud (Claude + GPT) — **100%**

| File | Status |
| --- | --- |
| `ui/setup/ClaudeWebSetupScreen.kt` | ✅ WebView modal + extract + persist |
| `ui/setup/GPTWebSetupScreen.kt` (samefile) | ✅ idem |
| `ui/setup/SetupNavigator.kt` 9-step | ✅ Privacy → Overlay → Vendor → A11y → Shizuku → Gemini → Claude → GPT → Done |
| MainActivity inject SessionExtractor + SecurePreferences | ✅ |

### W2.5 — AI Engine Settings — **100%**

`ui/settings/AiEngineSettingsScreen.kt` — list semua adapter via
`router.allAdapters()`, per-card: title, available, quota (used/limit), totals,
exhausted warning, validate button (Claude/GPT only), clear session button.
Wired di MainActivity NavHost route `settings/ai_engine`.

---

## Adapter catalog Phase 4 — final

| Adapter ID | Display | Local | Context | Auth | Quota |
| --- | --- | --- | --- | --- | --- |
| `gemma_local` | Gemma 4 4B (Local) | ✅ | 128k | none | unlimited |
| `gemini_free` | Gemini 2.5 Flash (Free) | ❌ | 1M | API key | 1500/day |
| `claude_web` | Claude.ai (Web Session) | ❌ | 200k | cookie+sha | unlimited |
| `gpt_web` | ChatGPT (Web Session) | ❌ | 128k | Bearer+cookie | unlimited |
| `stub` | Stub (Dev) | ✅ | 4k | none | unlimited |

Cascade order saat `router.selectAdapter(task)` tanpa pin:
1. Gemma (kalau model file present)
2. Gemini (kalau API key + quota)
3. Claude web (kalau session valid)
4. GPT web (kalau session valid)
5. Stub (always-on fallback)

---

## Build verification

```
> Task :app:assembleDebug
BUILD SUCCESSFUL in 50s
43 actionable tasks: 11 executed, 32 up-to-date
```

Pre-existing warnings unchanged (Room schemaLocation, jetifier, AgentRuntime cast).

---

## Risiko residual + mitigasi

| Risiko | Mitigasi sekarang | Defer ke |
| --- | --- | --- |
| Anthropic/OpenAI signature rotate | SessionExtractor re-extract dari WebView; Settings clear-session button | re-login flow auto-trigger on 401 (Phase 9) |
| Account suspend | Rate-limit caller-side (AgentRuntime per-task throttle TBD), single-account user | per-call cooldown 30s (Phase 9 polish) |
| Gemini quota habis | exhaustedAt flag → cascade ke adapter lain otomatis | UI warning 80% (Phase 9) |
| WebView capability di Android 17+ | Current test darwin 25; test pada physical Xiaomi 17 Pro Max | Manual test Phase 9 |
| Session validate background | Manual button di Settings | WorkManager 24h periodic ping (Phase 9) |
| Streaming belum live | Single-shot complete() bekerja; aggregate SSE parsed | Per-token stream emit (Phase 9 atau saat LiteRT-LM/web SSE refactor) |

---

## Yang belum dikerjakan (defer eksplisit)

1. **Cloud-aware rate limiter** per adapter (1 call/30s Claude/GPT) — defer Phase 9.
2. **WorkManager periodic session validate** — defer Phase 9 (manual via Settings cukup MVP).
3. **AuditLog LLM_CALL_CLOUD type** — masih pakai TOOL_EXECUTED generik untuk escalate. Defer Phase 9.
4. **Streaming proper per-token** — adapter return aggregated text. Acceptable untuk Phase 4 — UX akan terlihat sekali jadi.
5. **cloud_mode opt-in setting** — sementara escalate_to_cloud MEDIUM ke SafetyGate selalu konfirm. Defer Phase 6 (StandingInstruction pre-auth).
6. **Pre-create persistent Claude conversation** — masih pakai activeConvId dari /chat_conversations[0]. Kalau kosong, tool gagal AUTH_EXPIRED. Phase 9 add explicit POST /chat_conversations untuk create new.

---

## Sign-off

✅ Build verified.
✅ Cascade router operational.
✅ 3 cloud adapters wired, gracefully handle absent session/key.
✅ Setup wizard 9-step lengkap.
✅ AI Engine Settings live.

Phase 4 ready untuk commit. Selanjutnya Phase 5 (Vision) sesuai
[20-phase-roadmap.md](20-phase-roadmap.md).
