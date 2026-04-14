# 05 — Tech Stack

## Dihapus dari v1

| Tech | Alasan |
|------|--------|
| Ktor Client | Tidak ada cloud API |
| OAuth 2.0 / AppAuth | Tidak ada cloud auth |
| Retrofit | Tidak ada REST call |
| GSON / Jackson | Diganti Kotlin Serialization |

## Ditambah di v3

| Tech | Fungsi |
|------|--------|
| LiteRT-LM | Gemma on-device runtime |
| ML Kit GenAI Prompt API | Alt runtime (AICore devices) |
| Hilt | Dependency injection |
| Kotlin Flow / StateFlow | Reactive state |
| NotificationListenerService | Listen notif masuk |
| WindowManager Overlay | Kill switch + status |
| DataStore Proto | Sering-berubah config |

## Stack Lengkap

### 1. Core
- **Kotlin 2.0+** — Coroutines, Flow, Serialization
- **Android Jetpack** — Lifecycle, Room, WorkManager, DataStore
- **Hilt** — DI
- **Min SDK 28** (Android 9) | **Target SDK 35** (Android 15)

### 2. AI Engine
- **LiteRT-LM** — Primary & ONLY runtime
  - Maven: `com.google.ai.edge.litertlm:litertlm-android:latest.release`
  - @Tool + @ToolParam annotations untuk function calling (auto reflection)
  - Engine → Conversation → Toolset pattern
  - Backend: GPU (primary), CPU (fallback), NPU (jika tersedia)
  - Model format: `.litertlm` dari HuggingFace `litert-community`
  - Referensi pattern: Edge Gallery source code
- **Gemma 4 E2B** ~1.3GB (fast) | **E4B** ~2.5GB (smart+vision)

### 3. Execution
- **Intent API** — Tier 1
- **ContentResolver** — Tier 2
- **Accessibility Service** — Tier 3
- **Shizuku API** — Tier 4
- **MediaProjection** — Screenshot fallback

### 4. Communication
- **AIDL** — IPC ke character app
- **NotificationListenerService** — Notif listener
- **BroadcastReceiver** — System events
- **Kotlin Flow** — Internal streams

### 5. Data
- **Room** — DB utama (FTS5)
- **EncryptedSharedPreferences** — User prefs
- **DataStore Proto** — Model config
- **Kotlin Serialization** — JSON parsing

### 6. Perception
- **XML Pull Parser** — UI tree parsing
- **Semantic Distiller** — Custom pruning + labeling
- **Gemma E4B Vision** — Screenshot analysis

### 7. UI
- **Jetpack Compose** — UI
- **Material 3** — Design system
- **WindowManager Overlay** — Floating bubble

### 8. Dev Tools
- Android Studio Ladybug+
- ADB
- Layout Inspector
- Android GPU Inspector
- Google AI Edge Gallery

## Module Structure

```
:app
├── :core (orchestrator, state)
├── :ai (Gemma engine, inference)
├── :safety (approval, whitelist)
├── :perception (accessibility, vision)
├── :executor (intent, content provider, accessibility, shell)
├── :skills (loader, registry)
├── :data (Room DB, prefs)
├── :service (foreground, accessibility, notification)
└── :ui (compose, overlay)
```
