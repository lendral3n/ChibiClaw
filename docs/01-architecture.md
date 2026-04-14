# 01 — Arsitektur ChibiClaw v3

## Filosofi

ChibiClaw v3 bukan arsitektur berlayer sekuensial seperti v1 (IPC → Perception → Cognitive → Execution). Ini adalah **pipeline modular** di mana setiap modul hanya dipanggil saat dibutuhkan.

Perbedaan mendasar: di v1, setiap perintah selalu melewati semua layer termasuk UI scanning. Di v3, perintah seperti "telepon Budi" bisa diselesaikan hanya dengan Intent API — tanpa pernah menyentuh Accessibility Service.

Lihat diagram: `diagrams/architecture-overview.txt`

## Pipeline

```
User Input → Command Gateway → Approval Gate → Gemma Core → Execution Engine
                                                    ↑              │
                                                    │              ▼
                                               Skill Files    Verification
                                               Memory DB      (opsional)
```

## Modul-modul

### 1. Command Gateway
Pintu masuk tunggal. Menangani 30+ channel lewat satu control plane.

Sumber input:
- **AIDL Bridge** — Dari character app via IPC
- **Voice Engine** — Gemma E2B/E4B native audio (on-device STT)
- **Notification Listener** — Auto-trigger dari notifikasi masuk
- **Cron Scheduler** — Tugas terjadwal
- **Widget/Overlay** — Quick command dari floating widget

Semua input dinormalisasi menjadi `CommandRequest` yang seragam:
```kotlin
data class CommandRequest(
    val id: String,
    val source: CommandSource,  // AIDL, VOICE, NOTIFICATION, CRON, WIDGET
    val rawText: String,
    val timestamp: Long,
    val priority: Priority,
    val metadata: Map<String, Any>
)
```

### 2. Approval Gate
Tiga mode policy:
- **auto** — Langsung eksekusi (LOW severity)
- **ask** — Tampilkan konfirmasi (HIGH severity)
- **deny** — Selalu tolak (BLOCKED)

Detail: `08-safety-and-approval.md` | Diagram: `diagrams/approval-gate.txt`

### 3. Gemma Cognitive Core
Dua model on-device:
- **Gemma E2B** — Intent classification, simple parsing (selalu loaded)
- **Gemma E4B** — Complex reasoning, task planning, vision (on-demand)

Detail: `02-gemma-integration.md` | Diagram: `diagrams/gemma-model-routing.txt`

### 4. Execution Engine
Strategi **Intent-First Hierarchy** — 4 tier:

| Tier | Mekanisme | Kecepatan | Contoh |
|------|-----------|-----------|--------|
| 1 | Intent API | Instan | Telepon, deep link, buka setting |
| 2 | Content Provider | Cepat | Baca kontak, kalender, SMS |
| 3 | Accessibility | Sedang | Klik tombol, ketik teks di app lain |
| 4 | Shizuku Shell | Lambat | Force stop, install APK |

Detail: `03-execution-strategy.md` | Diagram: `diagrams/execution-hierarchy.txt`

### 5. Perception Module (Lazy)
BUKAN modul yang selalu aktif. Hanya dipanggil saat Tier 3 dibutuhkan.

Dual-path:
- **Path A**: Accessibility Tree → Distiller → UI Map
- **Path B**: Screenshot → Gemma Vision (fallback saat Path A gagal)

Diagram: `diagrams/perception-dual-path.txt`

### 6. Skill System
Kemampuan agent berupa file JSON, bukan hardcoded.

Detail: `04-skill-system.md` | Diagram: `diagrams/skill-system.txt`

### 7. Memory System
Room DB + FTS5 dengan 4 tabel: command_history, conversation_context, app_patterns, contact_context.

### 8. State Machine
7 state: IDLE → PLANNING → EXECUTING → VERIFYING → ERROR_RECOVERY / WAITING_USER / PAUSED → COMPLETED

Detail: `07-state-machine.md` | Diagram: `diagrams/state-machine.txt`
