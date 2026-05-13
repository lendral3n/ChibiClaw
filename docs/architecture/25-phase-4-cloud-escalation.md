# 25 — Phase 4: Cloud Escalation

**Durasi:** 1.5 minggu
**Tujuan:** Multi-adapter LLM. Cascade Gemma local → Gemini API free → Claude.ai web reverse → ChatGPT web reverse. LLM yang putus kapan escalate via `escalate_to_cloud` tool.

---

## Outcome

- GeminiFreeAdapter (Google AI Studio API key, free tier 1500 req/day)
- ClaudeWebAdapter (reverse-engineered claude.ai cookie session via WebView headless once-off)
- GPTWebAdapter (reverse-engineered chatgpt.com)
- InferenceRouter dengan task pinning + cascade fallback
- Tool `escalate_to_cloud(reason, target)` registered
- AdapterQuotaTracker (Room) per adapter
- Setup wizard step per cloud (Gemini paste API key, Claude+GPT WebView login)
- Settings UI: AI Engine dengan adapter status + cloud activity indicator

**Test target:** task complex yang Gemma kewalahan (mis. "buatkan summary email terakhir + classify priority") → LLM emit escalate_to_cloud → Gemini call → success.

---

## Deliverable per Minggu

### Minggu 1: Gemini + Router + Quota

**M1.1: GeminiFreeAdapter**
- `ai/llm/adapters/GeminiFreeAdapter.kt` implement InferenceAdapter
- REST API endpoint `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent`
- Header: `?key={apiKey}`
- Payload: contents, systemInstruction, generationConfig dengan responseSchema (constrained JSON)
- Parse response: candidates[0].content.parts[0].text

**M1.2: InferenceRouter**
- `ai/llm/InferenceRouter.kt`
- selectAdapter(task) → check pinning → default gemma
- escalate(target, task) → check available → pin + return
- ConcurrentHashMap untuk task pinning

**M1.3: AdapterQuotaTracker**
- `ai/llm/AdapterQuotaTracker.kt`
- Room table: `model_config` dengan keys per adapter quota
- Reset logic (daily for Gemini free)
- markExhausted, tryConsume, hasQuota

**M1.4: escalate_to_cloud tool**
- `agent/tools/impl/EscalateToolHandler.kt`
- Args: reason, target (GEMINI/CLAUDE/GPT)
- Action: call router.escalate(target) → return success with adapter info
- Safety: MEDIUM, confirm kalau cloud_mode=opt-in

**M1.5: Setup wizard Gemini**
- Step: "Pakai Gemini gratis (1500 req/day)?"
- Intent ke `https://aistudio.google.com/apikey` via Custom Tab
- User create key, paste
- Test call → kalau sukses save di EncryptedSharedPreferences

### Minggu 2 (0.5w): Claude + GPT web reverse

**M2.1: WebView headless login flow**
- `ui/setup/CloudLoginWebViewScreen.kt`
- Modal WebView ke target URL (claude.ai/login atau chat.openai.com/auth/login)
- JavaScript bridge listen URL_CHANGED
- Saat URL match success pattern (e.g. `claude.ai/chats`), trigger extract:
  - All cookies via `CookieManager.getInstance().getCookie(url)`
  - localStorage values via JS injection (`window.localStorage.getItem('...')`)
  - DOM-derived data (clientSha fingerprint)
- Save to EncryptedSharedPreferences

**M2.2: ClaudeWebAdapter**
- `ai/llm/adapters/ClaudeWebAdapter.kt`
- Endpoint: `https://claude.ai/api/organizations/{org}/chat_conversations/{conv}/completion`
- Headers: Cookie, anthropic-client-sha, anthropic-client-version, anthropic-device-id, User-Agent
- Pre-create persistent conversation di setup (avoid create new per call)
- Parse SSE streaming response

**M2.3: GPTWebAdapter**
- Similar pattern di chatgpt.com
- Endpoint: `https://chatgpt.com/backend-api/conversation`
- Headers: Cookie, openai-conversation-id, etc

**M2.4: Session rotator + validator**
- Periodic ping (every 24h) ke mundane endpoint untuk verify session
- On 401: notify user re-login

**M2.5: Settings UI**
- AI Engine screen dengan adapter status cards:
  - Gemma 4 4B (Local) [Active]
  - Gemini Flash (Free) [Available, 234/1500 used today]
  - Claude.ai (Web) [Session expires in 12 days] [Re-login]
  - ChatGPT (Web) [Not configured] [Login now]

---

## Modul Phase 4

```
app/src/main/java/com/chibiclaw/ai/llm/
├── adapters/
│   ├── GeminiFreeAdapter.kt
│   ├── ClaudeWebAdapter.kt
│   ├── GPTWebAdapter.kt
│   └── ClaudeSessionRotator.kt
├── InferenceRouter.kt
├── AdapterQuotaTracker.kt
└── webview/
    ├── CloudLoginWebView.kt
    └── SessionExtractor.kt

app/src/main/java/com/chibiclaw/agent/tools/impl/
└── EscalateToolHandler.kt

app/src/main/java/com/chibiclaw/ui/settings/
└── AiEngineSettingsScreen.kt
```

---

## Session Data Structures

```kotlin
@Serializable
data class ClaudeWebSession(
    val orgId: String,
    val userId: String,
    val activeConvId: String,
    val cookies: List<String>,
    val clientSha: String,
    val clientVersion: String,
    val deviceId: String,
    val userAgent: String,
    val createdAt: Instant,
    val lastValidatedAt: Instant,
    val maxAge: Duration = Duration.ofDays(14),
)

@Serializable
data class GPTWebSession(
    val userId: String,
    val cookies: List<String>,
    val conversationId: String?,
    val accessToken: String,
    val createdAt: Instant,
    // ...
)
```

Stored di EncryptedSharedPreferences as JSON string.

---

## escalate_to_cloud Tool Spec

```yaml
name: escalate_to_cloud
description: |
  Signal bahwa task butuh adapter LLM lebih kuat dari local Gemma.
  Pakai saat: reasoning panjang, multi-step kompleks, atau ambiguous task.
  
args:
  reason: string  (e.g. "task butuh long-context analysis")
  target: enum [GEMINI, CLAUDE, GPT]  (default: GEMINI)
  
capability:
  latencyMs: 100  # ini signal, bukan cloud call beneran
  cost: LOW (signal); subsequent cloud call HIGH

safety:
  severity: MEDIUM
  reason: "Task data ringkasan akan transit ke cloud"
  confirmationPrompt: "Kirim task ke {target}? Privasi: data akan transit"
  preAuthorizable: true  # via standing instruction atau user setting
```

LLM emit:
```json
{
  "thought": "Task ini butuh reasoning panjang, Gemma struggle. Escalate ke Claude.",
  "tool_calls": [{
    "tool": "escalate_to_cloud",
    "args": {"reason": "long reasoning needed", "target": "CLAUDE"}
  }],
  "next": "continue"
}
```

Tool execute:
1. Check `cloud_mode` setting (opt-in default)
2. Kalau opt-in: show confirmation overlay
3. Check adapter available
4. Pin adapter to task
5. Return ToolResult.Success with `switched_to` info

Next iteration: AgentRuntime invoke selectAdapter(task) → return pinned ClaudeWebAdapter.

---

## Risk

| Risk | Mitigasi |
|------|----------|
| Anthropic/OpenAI signature rotate | Hook untuk re-extract dari WebView setiap session refresh; user re-login wizard |
| Account ban | Rate limit ketat (1 call / 30s); user disclosure; fallback Gemini free |
| Gemini free quota exhaustion | Track quota Room; UI warning at 80%; pause until reset |
| Session expired mid-task | Graceful re-prompt user via await_user("Login Claude.ai ulang?") |
| Cloud cost (kalau pivot ke API key) | Phase 4 personal: free tier + reverse-engineered. Cost N/A unless productize |
| WebView capability di Android 17+ | Test compatibility; alternative: stand-alone OAuth proxy app |

---

## Performance Target

| Metric | Target |
|--------|--------|
| Gemini Flash call (cold) | <3s |
| Gemini Flash streaming first byte | <600ms |
| Claude web call (cold) | <5s |
| Session extraction setup (WebView once-off) | <30s |
| Escalation decision overhead (tool dispatch) | <100ms |
| Quota check overhead | <50ms |

---

## Definition of Done

- [ ] Gemini Flash adapter call works (test via debug UI)
- [ ] Free tier quota tracker accurate (test 5 calls, observe count)
- [ ] Claude web WebView login extract session works (test extract → call API → response)
- [ ] GPT web similar
- [ ] InferenceRouter cascade test: simulate Gemini quota exhausted → fallback Claude → fallback GPT
- [ ] escalate_to_cloud tool emit → router pin → next iteration uses cloud adapter
- [ ] AuditLog populated with LLM_CALL_CLOUD entries dengan adapter id
- [ ] AI Engine settings screen functional (status display, re-login buttons)
- [ ] No regression Gemma local path

---

## Next: [26-phase-5-vision.md](26-phase-5-vision.md)
