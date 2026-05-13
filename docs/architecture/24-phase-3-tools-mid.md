# 24 — Phase 3: Tools Mid

**Durasi:** 3 minggu
**Tujuan:** Accessibility Service + Shizuku + messaging tools untuk control HP real.

---

## Outcome

- AccessibilityService running, exposed via tools: `a11y_click`, `a11y_type`, `a11y_describe_screen`, `a11y_scroll`
- Shizuku 13.x integrated via AIDL UserService, tools: `shizuku_exec`, `shizuku_force_stop`, `shizuku_grant_permission`
- Tool `messaging` (SMS via Intent + WA via a11y/intent)
- Tools: `world_get_notifications`, `intent_send`
- Inline safety gate untuk HIGH severity (confirmation overlay)

**Test target:** "Force-stop YouTube lalu buka Spotify", "Kirim SMS ke +6281234... isi: test" dengan confirmation gate muncul.

---

## Deliverable per Minggu

### Minggu 1: Accessibility Service

**M1.1: Manifest + service**
- `<service android:name=".accessibility.ChibiAccessibilityService" android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">` 
- `<meta-data android:name="android.accessibilityservice"` pointing to `accessibility_service_config.xml`
- `accessibility_service_config.xml`: `canRetrieveWindowContent`, `flagDefaultService`, event types

**M1.2: AccessibilityService implementation**
- `accessibility/ChibiAccessibilityService.kt`
- `onAccessibilityEvent()` minimal (Phase 3 cuma respond to demand, bukan event-driven; event-driven jadi Phase 6 untuk standing instruction)
- Expose tree navigation API via Singleton accessor

**M1.3: A11y tools**
- `agent/tools/impl/A11yClickTool.kt`
  - Selector: label, contentDescription, resource_id, atau xpath-like
  - findNodeByText → performAction CLICK
  - Return Success or Error(SELECTOR_NOT_FOUND)
- `agent/tools/impl/A11yTypeTool.kt`
  - Find focused EditText node → setText
- `agent/tools/impl/A11yDescribeScreenTool.kt`
  - Walk active window root → serialize tree text representation
  - max_depth limit (5 default)
- `agent/tools/impl/A11yScrollTool.kt`
  - find scrollable → perform ACTION_SCROLL_FORWARD/BACKWARD

**M1.4: User setup wizard**
- New step: "Aktifkan Accessibility Service ChibiClaw"
- Intent ke `Settings.ACTION_ACCESSIBILITY_SETTINGS`
- Detect activation via service connection
- Disclosure: "Fuu pakai accessibility buat click/type otomatis di app yang support"

### Minggu 2: Shizuku integration

**M2.1: Shizuku SDK**
- Add deps `dev.rikka.shizuku:api:13.x` + `dev.rikka.shizuku:provider:13.x`
- Manifest `<provider android:name="rikka.shizuku.ShizukuProvider">`
- `permissions/ShizukuManager.kt`: status check, request permission, listener

**M2.2: AIDL UserService**
- Define `IChibiShizukuService.aidl`:
  ```
  interface IChibiShizukuService {
      String exec(String command);
      void destroy();
  }
  ```
- Implementation: process exec via Runtime.exec or `Shizuku.newProcess` (kalau API stable)

**M2.3: Setup wizard Shizuku (5-step)**
1. Explain what Shizuku is + risk disclosure
2. Install Shizuku app (link to Play Store / GitHub)
3. Start Shizuku via wireless ADB (instruksi Android 11+) atau via root
4. Grant permission to ChibiClaw
5. Test exec (run `echo ok`) → confirm working

**M2.4: Shizuku tools**
- `agent/tools/impl/ShizukuExecTool.kt`: generic exec
- `agent/tools/impl/ShizukuForceStopTool.kt`: `am force-stop {package}`
- `agent/tools/impl/ShizukuGrantPermissionTool.kt`: `pm grant {package} {permission}`
- Safety: HIGH severity, confirmation gate

### Minggu 3: Messaging + integration

**M3.1: Messaging tool**
- `agent/tools/impl/MessagingTool.kt`
- kind=SMS: `SmsManager.sendTextMessage()` (butuh SEND_SMS permission)
- kind=WHATSAPP: Intent `android.intent.action.SENDTO` ke `whatsapp://send?phone=...&text=...`
- kind=TELEGRAM: similar Telegram URI scheme
- Safety: HIGH, confirmation overlay wajib

**M3.2: World query tambahan**
- `agent/tools/impl/WorldGetNotificationsTool.kt`: query NotificationListenerService active notifs
- `agent/tools/impl/IntentSendTool.kt`: generic Intent send (dial, share, web open)

**M3.3: NotificationListenerService**
- `accessibility/ChibiNotificationListener.kt`
- Phase 3: register service, simple event log (no rule engine yet — Phase 6)
- Manifest + user setup wizard step

**M3.4: System prompt update**
- Tool list mention semua a11y + shizuku + messaging tools
- Capability metadata di description (latency, works-on, known-fail)
- Inline safety reminder

---

## Modul Phase 3

```
app/src/main/java/com/chibiclaw/
├── accessibility/
│   ├── ChibiAccessibilityService.kt
│   ├── ChibiNotificationListener.kt
│   └── a11y/
│       ├── A11yTreeWalker.kt
│       └── A11ySelectorResolver.kt
├── permissions/
│   ├── ShizukuManager.kt
│   ├── ShizukuConnection.kt
│   └── ChibiShizukuService.kt
├── agent/tools/impl/
│   ├── A11yClickTool.kt
│   ├── A11yTypeTool.kt
│   ├── A11yDescribeScreenTool.kt
│   ├── A11yScrollTool.kt
│   ├── ShizukuExecTool.kt
│   ├── ShizukuForceStopTool.kt
│   ├── ShizukuGrantPermissionTool.kt
│   ├── MessagingTool.kt
│   ├── IntentSendTool.kt
│   └── WorldGetNotificationsTool.kt
└── ui/setup/
    ├── AccessibilitySetupScreen.kt
    └── ShizukuSetupWizardScreen.kt
```

AIDL:
```
app/src/main/aidl/com/chibiclaw/api/
├── IChibiShizukuService.aidl
└── (Phase 6+: IChibiCallback)
```

---

## Dependencies

```kotlin
dependencies {
    // Shizuku
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
```

---

## Inline Safety Gate UI

```kotlin
@Composable
fun ConfirmationOverlay(
    tool: ToolSpec,
    args: Map<String, JsonElement>,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    autoDenyTimeoutMs: Long = 30_000,
) {
    var remaining by remember { mutableStateOf(autoDenyTimeoutMs) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(100)
            remaining -= 100
        }
        onDeny()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(/* ... */) {
            Column {
                Text("🛑 HIGH SEVERITY", color = Error)
                Text("Fuu mau lakukan ini:")
                ToolCallSummary(tool, args)
                Row {
                    Button(onClick = onDeny) { Text("Tidak") }
                    Button(onClick = onApprove) { Text("Ya, Lanjut") }
                }
                Text("Auto-deny: ${remaining / 1000}s")
            }
        }
    }
}
```

Display di overlay window di atas screen current. Compose ViewModel-less, dipanggil dari ToolDispatcher.

---

## Risk

| Risk | Mitigasi |
|------|----------|
| AccessibilityService disabled by user / vendor | Setup wizard step explicit; status check di every tool call; LLM kasih tahu user re-enable |
| Shizuku setup susah (Wireless ADB Android 11+) | 5-step wizard dengan screenshot; skip option (fitur HIGH limited) |
| Anti-accessibility apps (TikTok, WA) | Expected; LLM fallback ke vision_tap Phase 5 |
| SMS permission requirement | Runtime permission request; opt-out kalau user tidak mau |
| Confirmation overlay blocking when many HIGH tool calls in row | Pre-authorize via standing instruction (Phase 6) |
| Shizuku newProcess deprecation | Pakai UserService AIDL (more stable); check Shizuku 13.x compatibility |

---

## Definition of Done

- [ ] AccessibilityService running, status visible di Settings
- [ ] a11y_click works di Settings app (tap "About")
- [ ] a11y_type works di search field generic
- [ ] a11y_describe_screen return tree text
- [ ] Shizuku setup wizard 5-step lengkap
- [ ] shizuku_exec works (echo, am force-stop)
- [ ] Messaging SMS test sukses kirim dengan confirmation gate
- [ ] WA "kirim WA ke Budi 'test'" works via Intent
- [ ] Confirmation overlay muncul + auto-deny 30s untuk HIGH severity
- [ ] NotificationListener service registered

---

## Next: [25-phase-4-cloud-escalation.md](25-phase-4-cloud-escalation.md)
