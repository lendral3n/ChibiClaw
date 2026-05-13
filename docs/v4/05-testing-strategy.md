# 05 — Testing Strategy

**Audience**: Engineering, QA
**Last updated**: 2026-05-10

---

## Testing Pyramid

```
               ╱╲
              ╱E2E╲                     ← 5%   (manual + scripted device test)
             ╱─────╲
            ╱ Integ ╲                  ← 25%  (Hilt + Robolectric component tests)
           ╱─────────╲
          ╱   Unit    ╲                ← 70%  (JUnit + Mockk pure logic tests)
         ╱─────────────╲
```

Coverage target by phase:
- **End of Phase 1**: 20% (focus: InferenceAdapter unit + integration)
- **End of Phase 2**: 30% (add: VoiceLayer state machine + STT/TTS units)
- **End of Phase 3**: 35% (add: ExecutionStrategy + MemoryStore + tool consolidation)

Realistic, bukan 80%. Solo dev 4-month timeline tidak feasible push higher.

---

## Layer 1: Unit Tests

### Frameworks
- JUnit 4.13.2
- Mockk 1.13.13 (Kotlin-friendly)
- kotlinx.coroutines.test 1.10.1
- Turbine 1.2.0 (Flow assertions)

### Critical Modules to Cover

#### `agent/inference/`
- `InferenceRouter.selectAdapter()` — verify mode-based selection logic
- `InferenceRouter.fallback()` — verify cloud failure → Gemma local
- Each adapter: parse SSE events → emit InferenceChunk correctly
- `CostMeter.recordUsage()` — sum, daily aggregation, budget alert

#### `agent/memory/`
- `MemoryStore.saveCommand()` → embedding generated → indexed
- `EntityResolver.resolve()` — exact match priority over semantic
- `SemanticSearcher` — cosine similarity ranking correct

#### `voice/`
- `VoiceLayer` state machine transitions: IDLE → LISTENING → PROCESSING → SPEAKING → IDLE
- Interrupt during SPEAKING → cancel TTS, transition LISTENING
- VAD threshold logic
- STT timeout handling

#### `executor/strategies/`
- `StrategySelector.select()` — blacklist app → VisionFirstStrategy
- `AccessibilityFirstStrategy.execute()` failure → fallback to Vision
- `VisionFirstStrategy.execute()` — coordinate extraction + gesture dispatch flow

#### Tools
- Each `@Tool` method: safety gate triggered for HIGH-risk
- Tool args validation (kind enum, etc.)

### Mocking Strategy

```kotlin
@Test
fun `InferenceRouter selects offline adapter when privacy mode OFFLINE`() {
    val gemma = mockk<InferenceAdapter>(relaxed = true) {
        every { id } returns "gemma-local"
        coEvery { isReady() } returns true
    }
    val claude = mockk<InferenceAdapter>(relaxed = true) {
        every { id } returns "anthropic-claude"
    }
    val privacy = mockk<PrivacyManager> {
        every { currentMode } returns PrivacyManager.Mode.OFFLINE
    }
    val devLogger = mockk<DevLogger>(relaxed = true)
    val router = InferenceRouter(
        adapters = mapOf("gemma-local" to gemma, "anthropic-claude" to claude),
        privacyManager = privacy,
        devLogger = devLogger
    )
    
    runBlocking {
        val selected = router.selectAdapter()
        assertEquals("gemma-local", selected.id)
    }
}
```

---

## Layer 2: Integration Tests

### Frameworks
- Robolectric 4.14.1 (Android components in JVM)
- Hilt Testing 2.55 (DI for tests)
- AndroidX Test 1.6.x

### Critical Flows to Cover

#### `ChibiAgent.processCommand()` end-to-end (mocked Gemma)
```kotlin
@HiltAndroidTest
class ChibiAgentIntegrationTest {
    @Inject lateinit var agent: ChibiAgent
    @Inject lateinit var commandGateway: CommandGateway
    
    // Override Gemma adapter dengan mock yang return preset tool calls
    @BindValue val gemmaLocal: InferenceAdapter = FakeGemmaAdapter(
        scriptedResponse = listOf(
            InferenceChunk.ToolCall("system_control", mapOf("target" to "flashlight", "state" to "on")),
            InferenceChunk.TextDelta("Senter dinyalakan."),
            InferenceChunk.Done(50)
        )
    )
    
    @Test
    fun `command nyalakan senter executes SystemControl tool and emits response`() = runBlocking {
        commandGateway.submitDirect("nyalakan senter", CommandSource.WIDGET)
        
        agent.messages.test {
            // expect user message
            assertEquals("nyalakan senter", awaitItem().last().text)
            // expect assistant message with tool result
            val final = awaitItem().last()
            assertEquals("Senter dinyalakan.", final.text)
        }
    }
}
```

#### Voice flow integration
```kotlin
@HiltAndroidTest
class VoiceLayerIntegrationTest {
    @Test
    fun `wake word triggers full pipeline`() = runBlocking {
        voiceLayer.start()
        
        // simulate wake word detection
        wakeWordEngine.simulateDetection()
        
        voiceLayer.state.test {
            assertEquals(VoiceState.LISTENING, awaitItem())
            
            // simulate STT result
            stt.simulateTranscript("buka WhatsApp")
            assertEquals(VoiceState.PROCESSING, awaitItem())
            
            // wait for command processing + TTS
            assertEquals(VoiceState.SPEAKING, awaitItem())
            assertEquals(VoiceState.IDLE, awaitItem())
        }
    }
}
```

#### ExecutionStrategy selection
```kotlin
@Test
fun `dispatcher uses VisionFirst for blacklist apps`() = runBlocking {
    every { perceptionRouter.getCurrentForegroundApp() } returns "com.zhiliaoapp.musically"
    
    val result = actionDispatcher.dispatch(UiInteractAction("click", "Login button", ""))
    
    verify(exactly = 1) { visionFirstStrategy.execute(any()) }
    verify(exactly = 0) { accessibilityFirstStrategy.execute(any()) }
}
```

---

## Layer 3: End-to-End / Manual Tests

### Manual Test Plan: 20 Critical User Journeys

Catat status setiap kali run di [docs/v4/test-runs/](test-runs/) folder.

#### Smoke Tests (5)
1. **Boot sequence** — app open → mode select → bootstrap → home
2. **Wake word** — say "Hey Fuu" → overlay shows LISTENING
3. **Simple voice command** — "nyalakan senter" → senter on, TTS confirms
4. **Stop button** — start long task, tap stop → abort cleanly
5. **Privacy mode toggle** — offline → cloud → input API key → cloud responses

#### Voice Quality (3)
6. **Natural Indonesian pronunciation** — Fuu say complex sentence ("Saya akan membantu kamu menyelesaikan tugas") → no robot sound
7. **Mixed language** — user say Indonesian + English keyword → STT transcribe correctly
8. **Background noise** — coffee shop ambient → wake word still detect, STT acceptable

#### Multi-Step Tasks (4)
9. **App + UI** — "buka TikTok lalu cari ikan masak" → 2-step chain works
10. **Cross-app** — "lihat email dari Bos kemudian set alarm jam meeting" → email → calendar event
11. **Conditional** — "kalau hujan, ingatkan aku bawa payung" → check weather → schedule notif
12. **Long workflow** — 5+ tool chain task, observe tool selection accuracy

#### Safety Gates (4)
13. **SMS gate** — "kirim SMS ke X" → ConfirmationOverlay → tap NO → no SMS
14. **File delete gate** — "hapus file foto.jpg" → ConfirmationOverlay → tap YES → file deleted
15. **Shizuku gate** — "force stop Telegram" → ConfirmationOverlay (kalau Shizuku active)
16. **App uninstall gate** — "uninstall app X" → ConfirmationOverlay → confirm

#### Vision-First (2)
17. **TikTok automation** — "scroll TikTok 5 kali" → vision-based scroll, accessibility blocked
18. **WhatsApp gentle** — "lihat chat ibu" → vision identify, open chat

#### Error Paths (2)
19. **Engine not ready** — unload model, try command → graceful "Model belum siap"
20. **Network down (cloud mode)** — disable WiFi, command → fallback Gemma local OR error message

### Test Run Documentation Template

```markdown
# Test Run: 2026-MM-DD

**Build**: APK v4.x-yyy
**Device**: Samsung Galaxy A55 (Android 14)
**Tester**: Lendra

## Smoke (5)
| # | Test | Pass/Fail | Notes |
|---|------|-----------|-------|
| 1 | Boot sequence | ✅ | |
| 2 | Wake word | ✅ | |
| 3 | Simple voice command | ⚠️ | Latency 6s, target <5s |
| 4 | Stop button | ✅ | |
| 5 | Privacy mode toggle | ✅ | |

... etc

## Bugs Found
- [P1] Wake word fires on "hey food" too — too sensitive
- [P2] Multi-step task lost context after 4th tool call

## Performance Metrics
- Wake word battery: 6%/hour idle (target: <5%)
- E2E voice latency p50: 4.8s offline
- E2E voice latency p50: 2.1s cloud
- Crash rate: 0% in 2-hour session
```

### Devices Test Matrix

Minimum coverage:
- 1 flagship: Samsung S24 / Pixel 9 (Android 14, 12GB RAM)
- 1 mid-range: Samsung A55 / Redmi Note 13 (Android 14, 8GB RAM)
- 1 budget: Redmi 13 / Realme C-series (Android 14, 6GB RAM)

Not in scope: foldable, tablet (defer v5+).

---

## Performance Testing

### Latency Benchmarks

Per-stage latency targets (p50 / p95):
| Stage | Offline target | Cloud target |
|-------|----------------|--------------|
| Wake word detection | <500ms / <1s | N/A |
| VAD silence detect | <100ms / <200ms | N/A |
| STT transcribe | <2s / <4s | <0.8s / <1.5s |
| Inference | <2s / <5s | <0.8s / <2s |
| Tool execution (intent) | <500ms / <1s | <500ms / <1s |
| Tool execution (vision) | <2s / <4s | <1.5s / <3s |
| TTS synthesize | <800ms / <1.5s | <1.5s / <3s |
| **End-to-end simple** | **<5s / <10s** | **<3s / <6s** |
| **End-to-end complex (multi-tool)** | **<15s / <30s** | **<8s / <15s** |

### Battery Benchmarks

| State | Target drain |
|-------|--------------|
| Idle (wake word listening) | <5%/hour |
| Active conversation (5 turns) | <2%/turn |
| Heavy multi-tool task | <3%/task |

### Memory Benchmarks

| State | Target peak |
|-------|-------------|
| Idle | <300MB |
| Active inference (Gemma 4 4B) | <4GB |
| Active vision multimodal | <5GB |

---

## Security Testing

### Tests to perform manually before stable release

1. **API key extraction** — try `adb shell run-as com.chibiclaw cat ./shared_prefs/...` → keys must be encrypted
2. **Network sniffing in offline mode** — Wireshark / mitmproxy on test device, run voice commands offline → no traffic to ChibiClaw-related domains
3. **Permission audit** — all permissions in manifest still needed? Remove unused.
4. **AIDL whitelist bypass** — third-party app try `Intent("com.chibiclaw.SERVICE")` without whitelist → must reject
5. **Database encryption** — `adb pull /data/data/com.chibiclaw/databases/chibi.db` → file should be encrypted (cannot open with sqlite3)

---

## Continuous Testing

### CI Pipeline (GitHub Actions or similar)

```yaml
on: [push, pull_request]

jobs:
  test-jvm:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 17 }
      - run: ./gradlew testDebug --no-daemon
      - run: ./gradlew lintDebug --no-daemon
  
  build-apk:
    needs: test-jvm
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 17 }
      - run: ./gradlew assembleDebug --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
  
  instrumented-test:
    runs-on: macos-latest  # has Hardware Acceleration
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 17 }
      - uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          script: ./gradlew connectedDebugAndroidTest
```

### Pre-merge gate
- All unit tests pass
- All integration tests pass
- Lint zero error
- Detekt (if added) zero error

### Pre-release gate
- All above + Manual test plan PASS in test-runs/
- Performance benchmarks meet targets
- Security checklist PASS

---

## Bug Tracking

### Severity
- **P0 (Critical)**: Crash, data loss, security vuln. SLA: fix same day.
- **P1 (High)**: Functional broken (e.g., wake word not detect). SLA: fix within 3 days.
- **P2 (Medium)**: UX issue, performance regression. SLA: fix within sprint.
- **P3 (Low)**: Cosmetic, edge case. SLA: backlog.

### Tools
- GitHub Issues (kalau project public)
- atau Linear / GitHub Projects untuk private tracking
- Daily triage: 15 min review new bugs, assign severity

---

## Beta Testing Protocol

### Recruitment (Week 12, before beta)
- 5-10 user dari personal network atau beta tester community
- Mix: 3-4 Power User Indonesia, 1-2 Hands-Free Need
- Each tester sign light agreement: feedback OK, no public sharing of build

### Onboarding (Day 1 of beta)
- Send APK link + setup guide
- Create Discord/Telegram group for live feedback
- 1-on-1 onboarding call (15 min) — observe their first run

### Daily Check-in (Day 2-14)
- Daily question: "What worked today? What didn't?"
- Triage feedback into bugs (above)

### Wrap-up (Day 14)
- Aggregate all feedback
- Survey: NPS-style "How likely you recommend Fuu to friend? 0-10"
- Identify top 3 improvements untuk stable release

---

## Quality Gates Summary

Phase 1 → Phase 2: All Phase 1 unit + integration tests pass, no regression v3 critical paths.

Phase 2 → Phase 3: E2E voice flow demo successful, manual test 1-8 pass.

Phase 3 → Beta: All 20 manual tests run at least once, ≤2 P1 bugs open.

Beta → Stable: 0 P0, ≤3 P1 open, average NPS ≥6, performance targets met on mid-range device.

---

**Next**: [06-risk-register.md](06-risk-register.md) — Risk + mitigation.
