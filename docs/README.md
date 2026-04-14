# ChibiClaw v3 — Project Documentation

> Android Backend Agent untuk Asisten Virtual, powered by Gemma 4 On-Device via LiteRT-LM.
> Terinspirasi dari OpenClaw (agent untuk desktop/server), ChibiClaw adalah versi Android-nya.

---

## Apa itu ChibiClaw?

ChibiClaw adalah backend agent yang berjalan di perangkat Android sebagai Foreground Service. Dia adalah "tangan dan otak" dari karakter asisten virtual (misalnya: Fuu). ChibiClaw memiliki akses penuh ke perangkat — bisa membuka app, mengirim pesan, menelepon, mengubah setting sistem, dan menjalankan perintah apapun yang diminta user melalui suara atau teks.

**Prinsip utama:**
- **Offline-first** — Seluruh reasoning on-device via Gemma 4 + LiteRT-LM, Rp 0 biaya
- **Intent-first execution** — Selalu gunakan Intent API sebelum eskalasi ke Accessibility/Shell
- **Skill-based** — Kemampuan agent berupa file JSON konfigurasi, bukan hardcoded
- **Safety by design** — Approval gate terinspirasi OpenClaw

## Tech Decision: LiteRT-LM (BUKAN Edge Gallery App)

ChibiClaw menggunakan **LiteRT-LM library** langsung sebagai dependency Maven:
```gradle
implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
```

BUKAN berkomunikasi dengan Edge Gallery app, BUKAN fork Edge Gallery.
Edge Gallery source code digunakan sebagai REFERENSI pattern saja.
Lihat: `11-ai-integration-guide.md` untuk detail lengkap.

## Dokumen

| # | File | Isi |
|---|------|-----|
| 01 | `01-architecture.md` | Arsitektur pipeline modular |
| 02 | `02-gemma-integration.md` | Strategi Gemma on-device, prompt, function calling schema |
| 03 | `03-execution-strategy.md` | Intent-first 4-tier hierarchy |
| 04 | `04-skill-system.md` | Skill system terinspirasi OpenClaw |
| 05 | `05-tech-stack.md` | Daftar teknologi |
| 06 | `06-folder-structure.md` | Struktur folder project |
| 07 | `07-state-machine.md` | 7-state FSM |
| 08 | `08-safety-and-approval.md` | Approval gate, whitelist, severity |
| 09 | `09-ui-design-notes.md` | Catatan desain UI/UX |
| 10 | `10-risks-and-roadmap.md` | Risiko, limitasi, roadmap |
| 11 | `11-ai-integration-guide.md` | **PANDUAN UTAMA** — LiteRT-LM integration, @Tool pattern, code examples |

## Diagram (Mermaid — `.txt` files, render di mermaid.live)

| File | Isi |
|------|-----|
| `diagrams/architecture-overview.txt` | Pipeline keseluruhan |
| `diagrams/execution-flow.txt` | Sequence diagram contoh "Balas WA Budi Otw" |
| `diagrams/execution-hierarchy.txt` | 4-tier execution strategy |
| `diagrams/state-machine.txt` | 7-state diagram |
| `diagrams/gemma-model-routing.txt` | E2B vs E4B routing |
| `diagrams/approval-gate.txt` | Safety check flow |
| `diagrams/skill-system.txt` | Skill loading & execution |
| `diagrams/perception-dual-path.txt` | Accessibility + Vision dual path |

## Instruksi untuk Claude (Development)

Saat menggunakan Claude untuk develop ChibiClaw, SELALU berikan dokumen berikut
sebagai context agar Claude tidak bingung dan menghasilkan kode yang konsisten:

### Context minimum (WAJIB setiap session):
1. `README.md` — overview + aturan project
2. `11-ai-integration-guide.md` — cara integrasi LiteRT-LM + @Tool pattern
3. `06-folder-structure.md` — dimana file harus ditempatkan
4. `03-execution-strategy.md` — 4-tier execution hierarchy

### Context tambahan (sesuai task yang dikerjakan):
- AI module → + `02-gemma-integration.md`
- Safety → + `08-safety-and-approval.md`
- UI → + `09-ui-design-notes.md`
- Skills → + `04-skill-system.md`
- State management → + `07-state-machine.md`

### Aturan WAJIB untuk Claude:
1. SELALU gunakan LiteRT-LM library (`litertlm-android`), JANGAN implement inference sendiri
2. SELALU gunakan @Tool dan @ToolParam annotation dari LiteRT-LM untuk function calling
3. SELALU coba Intent API (Tier 1) sebelum Accessibility (Tier 3) di ExecutionRouter
4. JANGAN buat koneksi ke cloud API manapun — semua on-device
5. Ikuti folder structure di `06-folder-structure.md`
6. Semua skill definitions dalam format JSON di `assets/skills/`
7. State machine HARUS 7 state sesuai `07-state-machine.md`
8. Model download dari HuggingFace litert-community, format `.litertlm`
9. Kotlin 2.0+, Jetpack Compose untuk UI, Hilt untuk DI
10. Min SDK 28, Target SDK 35

## Referensi Eksternal

| Resource | URL | Kegunaan |
|----------|-----|----------|
| LiteRT-LM repo | github.com/google-ai-edge/LiteRT-LM | Library source code |
| LiteRT-LM Android guide | ai.google.dev/edge/litert-lm/android | Official setup guide |
| Edge Gallery repo | github.com/google-ai-edge/gallery | REFERENSI pattern saja |
| Function Calling Guide | github.com/google-ai-edge/gallery/blob/main/Function_Calling_Guide.md | @Tool pattern |
| Gemma 4 model card | ai.google.dev/gemma/docs/core/model_card_4 | Model capabilities |
| LiteRT-LM models | huggingface.co/litert-community | Download .litertlm |
| OpenClaw (inspirasi) | openclaw.ai | Skill, approval, gateway patterns |
