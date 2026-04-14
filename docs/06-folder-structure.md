# 06 — Folder Structure

```
chibiclaw/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── aidl/com/chibiclaw/api/
│   │   │   │   ├── IChibiService.aidl              # IPC interface utama
│   │   │   │   └── IChibiCallback.aidl              # Progress callback ke character app
│   │   │   │
│   │   │   ├── assets/
│   │   │   │   └── skills/                          # Built-in skill definitions
│   │   │   │       ├── phone_call.json
│   │   │   │       ├── sms_messaging.json
│   │   │   │       ├── whatsapp_messaging.json
│   │   │   │       ├── telegram_messaging.json
│   │   │   │       ├── email_compose.json
│   │   │   │       ├── calendar_manage.json
│   │   │   │       ├── alarm_timer.json
│   │   │   │       ├── app_launcher.json
│   │   │   │       ├── wifi_control.json
│   │   │   │       ├── bluetooth_control.json
│   │   │   │       ├── volume_control.json
│   │   │   │       ├── brightness_control.json
│   │   │   │       ├── camera_capture.json
│   │   │   │       ├── navigation.json
│   │   │   │       ├── web_search.json
│   │   │   │       └── system_info.json
│   │   │   │
│   │   │   ├── res/raw/
│   │   │   │   ├── system_prompt.txt                # Default persona prompt
│   │   │   │   └── function_schemas.json            # Function calling definitions
│   │   │   │
│   │   │   ├── kotlin/com/chibiclaw/
│   │   │   │   │
│   │   │   │   ├── gateway/                         # ══ COMMAND GATEWAY ══
│   │   │   │   │   ├── CommandGateway.kt            # Unified entry point
│   │   │   │   │   ├── CommandRequest.kt            # Request data class
│   │   │   │   │   ├── CommandQueue.kt              # Priority queue
│   │   │   │   │   ├── source/
│   │   │   │   │   │   ├── AidlCommandSource.kt     # Dari character app
│   │   │   │   │   │   ├── VoiceCommandSource.kt    # Dari voice input
│   │   │   │   │   │   ├── NotificationSource.kt    # Dari notifikasi masuk
│   │   │   │   │   │   └── CronSource.kt            # Dari scheduled tasks
│   │   │   │   │   └── CommandNormalizer.kt          # Normalize semua input ke format seragam
│   │   │   │   │
│   │   │   │   ├── safety/                          # ══ APPROVAL GATE ══
│   │   │   │   │   ├── ApprovalGate.kt              # Entry point, chain semua checks
│   │   │   │   │   ├── ApprovalPolicy.kt            # AUTO / ASK / DENY enum
│   │   │   │   │   ├── WhitelistManager.kt          # App whitelist CRUD
│   │   │   │   │   ├── SensitiveDetector.kt         # Detect password/payment fields
│   │   │   │   │   ├── SeverityClassifier.kt        # LOW / MEDIUM / HIGH / BLOCKED
│   │   │   │   │   └── ConfirmationOverlay.kt       # Overlay dialog untuk HIGH severity
│   │   │   │   │
│   │   │   │   ├── ai/                              # ══ GEMMA COGNITIVE CORE ══
│   │   │   │   │   ├── GemmaEngineManager.kt        # Load/unload model lifecycle
│   │   │   │   │   ├── GemmaInference.kt            # Inference wrapper
│   │   │   │   │   ├── ModelRouter.kt               # E2B vs E4B routing logic
│   │   │   │   │   ├── ContextAssembler.kt          # Rangkai prompt dari semua sumber
│   │   │   │   │   ├── FunctionCallParser.kt        # Parse function call JSON output
│   │   │   │   │   ├── ThinkingHandler.kt           # Handle thinking mode tokens
│   │   │   │   │   └── PromptTemplate.kt            # System prompt builder
│   │   │   │   │
│   │   │   │   ├── skills/                          # ══ SKILL SYSTEM ══
│   │   │   │   │   ├── SkillLoader.kt               # Load skill files dari assets + filesDir/custom_skills
│   │   │   │   │   ├── SkillRegistry.kt             # Registry, context string builder & lookup
│   │   │   │   │   └── SkillDefinition.kt           # Data class untuk skill JSON
│   │   │   │   │
│   │   │   │   ├── executor/                        # ══ EXECUTION ENGINE ══
│   │   │   │   │   ├── ExecutionRouter.kt           # Route ke tier yang tepat
│   │   │   │   │   ├── StepRunner.kt                # Execute step-by-step dengan verification
│   │   │   │   │   ├── ErrorRecovery.kt             # Re-plan saat gagal
│   │   │   │   │   ├── TimeoutGuard.kt              # 5s per action, max 3 retry
│   │   │   │   │   ├── KillSwitch.kt                # Emergency stop
│   │   │   │   │   │
│   │   │   │   │   ├── tier1/                       # Intent API executors
│   │   │   │   │   │   ├── IntentExecutor.kt        # Build & fire intents
│   │   │   │   │   │   ├── DeepLinkResolver.kt      # Resolve app deep links
│   │   │   │   │   │   └── IntentRegistry.kt        # Known intents per app
│   │   │   │   │   │
│   │   │   │   │   ├── tier2/                       # Content Provider executors
│   │   │   │   │   │   ├── ContactsExecutor.kt      # Read/search contacts
│   │   │   │   │   │   ├── CalendarExecutor.kt      # Read/write calendar
│   │   │   │   │   │   ├── SmsExecutor.kt           # Read SMS history
│   │   │   │   │   │   ├── MediaExecutor.kt         # Access media files
│   │   │   │   │   │   └── SystemApiExecutor.kt     # WiFi, BT, volume, brightness
│   │   │   │   │   │
│   │   │   │   │   ├── tier3/                       # Accessibility executors
│   │   │   │   │   │   ├── AccessibilityExecutor.kt # Click, type, scroll, swipe
│   │   │   │   │   │   └── VerificationLoop.kt      # Post-action UI verification
│   │   │   │   │   │
│   │   │   │   │   └── tier4/                       # Shell executors
│   │   │   │   │       └── ShizukuExecutor.kt       # ADB shell commands
│   │   │   │   │
│   │   │   │   ├── perception/                      # ══ PERCEPTION (LAZY) ══
│   │   │   │   │   ├── PerceptionRouter.kt          # Pilih Path A atau B
│   │   │   │   │   ├── accessibility/
│   │   │   │   │   │   ├── UiTreeScraper.kt         # Raw XML grabber
│   │   │   │   │   │   ├── SemanticDistiller.kt     # Token Guard: pruning + labeling
│   │   │   │   │   │   └── CoordinateExtractor.kt   # Extract clickable coordinates
│   │   │   │   │   └── vision/
│   │   │   │   │       ├── ScreenCapture.kt         # MediaProjection wrapper
│   │   │   │   │       └── GemmaVisionAnalyzer.kt   # Gemma E4B image analysis
│   │   │   │   │
│   │   │   │   ├── state/                           # ══ STATE MACHINE ══
│   │   │   │   │   ├── ChibiStateMachine.kt         # 7-state FSM
│   │   │   │   │   ├── ChibiState.kt                # State enum + valid transitions
│   │   │   │   │   └── StateObserver.kt             # StateFlow updates ke UI
│   │   │   │   │
│   │   │   │   ├── memory/                          # ══ MEMORY ══
│   │   │   │   │   ├── MemoryManager.kt             # Query & store memories
│   │   │   │   │   ├── local/
│   │   │   │   │   │   ├── dao/
│   │   │   │   │   │   │   ├── CommandHistoryDao.kt
│   │   │   │   │   │   │   ├── ConversationDao.kt
│   │   │   │   │   │   │   ├── AppPatternDao.kt
│   │   │   │   │   │   │   ├── ContactContextDao.kt
│   │   │   │   │   │   │   └── WhitelistDao.kt
│   │   │   │   │   │   ├── entity/
│   │   │   │   │   │   │   ├── CommandHistory.kt
│   │   │   │   │   │   │   ├── ConversationContext.kt
│   │   │   │   │   │   │   ├── AppPattern.kt
│   │   │   │   │   │   │   ├── ContactContext.kt
│   │   │   │   │   │   │   └── AppWhitelist.kt
│   │   │   │   │   │   └── ChibiDatabase.kt
│   │   │   │   │   └── pref/
│   │   │   │   │       └── SecurePreferences.kt
│   │   │   │   │
│   │   │   │   ├── service/                         # ══ ANDROID SERVICES ══
│   │   │   │   │   ├── ChibiService.kt              # Sticky Foreground Service
│   │   │   │   │   ├── ChibiAccessibility.kt        # Accessibility Service
│   │   │   │   │   ├── ShizukuHandler.kt            # Shizuku connection manager
│   │   │   │   │   ├── NotificationListener.kt      # Listen incoming notifications
│   │   │   │   │   └── FloatingOverlay.kt           # Kill switch + status bubble
│   │   │   │   │
│   │   │   │   ├── di/                              # ══ DEPENDENCY INJECTION ══
│   │   │   │   │   ├── AppModule.kt
│   │   │   │   │   ├── AiModule.kt
│   │   │   │   │   ├── ExecutorModule.kt
│   │   │   │   │   └── DatabaseModule.kt
│   │   │   │   │
│   │   │   │   └── ui/                              # ══ UI (COMPOSE) ══
│   │   │   │       ├── setup/
│   │   │   │       │   ├── SetupWizardScreen.kt
│   │   │   │       │   └── PermissionCheckScreen.kt
│   │   │   │       ├── dashboard/
│   │   │   │       │   ├── DashboardScreen.kt
│   │   │   │       │   ├── StatusCard.kt
│   │   │   │       │   └── ExecutionLogView.kt
│   │   │   │       ├── settings/
│   │   │   │       │   ├── AiSettingsScreen.kt
│   │   │   │       │   ├── SafetySettingsScreen.kt
│   │   │   │       │   ├── SkillEditorScreen.kt
│   │   │   │       │   └── PersonaEditorScreen.kt
│   │   │   │       └── chat/
│   │   │   │           ├── ChatScreen.kt
│   │   │   │           └── VoiceInputBar.kt
│   │   │   │
│   │   │   └── AndroidManifest.xml
│   │   │
│   │   ├── test/                                    # Unit tests
│   │   │   ├── state/ChibiStateMachineTest.kt       # FSM transition validation
│   │   │   ├── safety/SeverityClassifierTest.kt     # Severity keyword mapping
│   │   │   └── ai/PromptTemplateTest.kt             # Prompt content + injection guard
│   │   │
│   │   └── androidTest/                             # Instrumented tests
│   │       ├── GemmaInferenceTest.kt
│   │       └── IntentExecutorTest.kt
│   │
│   └── build.gradle.kts
│
├── docs/                                            # Dokumentasi (folder ini)
│   ├── README.md
│   ├── 01-architecture.md
│   ├── 02-gemma-integration.md
│   ├── ... (semua doc files)
│   └── diagrams/
│       └── ... (semua mermaid diagram files)
│
└── build.gradle.kts
```
