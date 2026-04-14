# 09 — UI/UX Design Notes

## Screens

### 1. Setup Wizard (First-time)
Steps:
1. Welcome + penjelasan singkat ChibiClaw
2. Download AI Model (progress bar, ~2.5GB one-time)
3. Test AI (kirim test prompt, verify Gemma jalan)
4. Enable Accessibility Service
5. Enable Notification Access (opsional)
6. Setup Shizuku (opsional, untuk Tier 4)
7. Set App Whitelist (default + custom)
8. Done — siap digunakan

### 2. Dashboard (Main Screen)
- **Status Card**: State machine indicator (IDLE/PLANNING/EXECUTING/dll) dengan warna
  - IDLE = abu-abu, PLANNING = biru, EXECUTING = hijau pulse, ERROR = merah, WAITING = kuning
- **Quick Stats**: Tasks hari ini, success rate, model status
- **Execution Log**: Real-time feed step-by-step saat task berjalan
  ```
  [1/4] ✅ Mencari kontak Budi
  [2/4] ✅ Membuka WhatsApp
  [3/4] ⏳ Mengetik pesan...
  [4/4] ⬜ Mengirim pesan
  ```
- **Recent Tasks**: Riwayat task terakhir dengan status

### 3. AI Settings
- Model Selection: E2B (Fast) / E4B (Smart) / Auto (recommended)
- Model Status: Downloaded ✅ / Size / Version
- Performance Slider: Speed ↔ Quality (quantization level)
- Context Window: Token usage meter
- Test Button: Verify inference berjalan

### 4. Safety Settings
- App Whitelist: Toggle per-app
- Severity Overrides: Per-skill severity adjustment
- Approval Policy: Global default (auto/ask)
- Sensitive Data: Toggle auto-detect
- Execution Log: Full history dengan filter

### 5. Skill Editor
- List built-in skills
- Custom skill creator (JSON editor sederhana)
- Skill enable/disable toggle
- Skill test button

### 6. Persona Editor
- System prompt editor (Fuu.md)
- Voice/tone settings
- Response language preference

### 7. Chat / Voice Interface
- Direct chat dengan ChibiClaw
- Voice input bar dengan waveform
- Inline execution status

## Floating Overlay (Always Visible saat Task Aktif)
- Bubble kecil di edge layar
- Warna berubah sesuai state
- Tap: expand → show current step + STOP button
- Long press: kill switch (emergency stop)
- Drag: bisa dipindah posisi

## Design Principles
- Mascot karakter (Fuu) muncul di key moments: setup, task completion, error
- Minimal friction: user tidak perlu masuk app untuk menggunakan ChibiClaw
- Voice-first: perintah utama lewat suara, UI hanya untuk config & monitoring
- Transparency: selalu tunjukkan apa yang sedang dilakukan ChibiClaw
