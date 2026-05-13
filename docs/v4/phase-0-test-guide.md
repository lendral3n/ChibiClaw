# Phase 0 — Validate v3 Test Guide

**Goal**: Pastikan ChibiClaw v3 baseline jalan di Xiaomi 17 Pro Max kamu sebelum invest 16 minggu di v4.

**Estimated time**: 60-90 menit total (15 menit setup + 45-60 menit test).

**Device target**: Xiaomi 17 Pro Max, HyperOS, China ROM (Play Protect certified).

---

## Step 1 — Transfer APK ke HP (5 menit)

APK lokasi di laptop:
```
/Users/lendra/Documents/codeV/ChibiClaw/app/build/outputs/apk/debug/app-debug.apk
```
Size: 345 MB.

Pilih transfer method paling enak:

### Method A: USB cable + adb
```bash
# Di laptop, dengan HP connected via USB + USB debugging ON:
adb install /Users/lendra/Documents/codeV/ChibiClaw/app/build/outputs/apk/debug/app-debug.apk
```

### Method B: USB cable, manual file copy
1. Connect HP via USB
2. Allow file transfer mode di HP
3. Copy `app-debug.apk` ke folder `Download/` di HP
4. Di HP, buka File Manager → Download → tap `app-debug.apk` → install
5. Allow "Install from unknown source" kalau diminta

### Method C: WiFi transfer (Mi Drop / Quick Share / Send Anywhere)
1. Open Mi Drop / Quick Share di HP
2. Receive mode
3. Di laptop, kirim APK via Mi Share (kalau ada Mi Mac client) atau pakai web transfer
4. Install di HP

### Method D: Cloud upload (Google Drive, Dropbox)
1. Upload APK ke Drive
2. Di HP, download dari Drive
3. Install

**Saran**: Method A paling cepat kalau USB debugging sudah ON. Method B paling general-purpose.

---

## Step 2 — HyperOS Permission Setup (10 menit, KRITIS)

**JANGAN skip ini. Tanpa setup ini, ChibiClaw akan dimatikan HyperOS dalam 5-15 menit.**

### 2a. Battery + Background

**Settings → Apps → Manage apps → ChibiClaw**:

| Toggle | Set ke |
|--------|--------|
| Battery saver | "No restrictions" / "Tidak ada batasan" |
| Autostart | ON |
| Display pop-up windows while running in background | ON ⚠️ Critical untuk ConfirmationOverlay |
| Display over other apps | ON |
| Notifications → Show on lock screen | ON |
| Notifications → all categories | Allow |
| Mobile data → Background data | Unrestricted |
| WiFi → Background data | Unrestricted |

**Settings → Battery → Battery saver → Choose apps**:
- ChibiClaw → "No restrictions"

### 2b. Permission grants

Buka ChibiClaw, lalu setup wizard akan minta:

| Permission | Required | Granted in HyperOS path |
|-----------|----------|-------------------------|
| Microphone | Ya (untuk voice) | Allow when prompted |
| Notifications | Ya | Allow |
| Display over other apps | Ya | Settings akan redirect, toggle ChibiClaw ON |
| Accessibility Service | Ya | Settings → Additional settings → Accessibility → Installed apps → ChibiClaw → ON. **Confirm 2x** karena Xiaomi extra prompt warning. |
| MediaProjection (screen capture) | Per-session | Akan diminta saat tool vision dipakai |

### 2c. Lock in Recent apps (penting!)

1. Buka ChibiClaw
2. Tap home → swipe up untuk recent apps
3. Tap ikon padlock di card ChibiClaw → "Locked"

Lock prevents Xiaomi swipe-to-clear killing the service.

---

## Step 3 — Initial Onboarding (5-15 menit)

Tergantung ada cache atau tidak.

### Setup wizard
1. Tap "Mulai Setup"
2. Pilih ROM: kalau detect Xiaomi China, kasih tau saya screenshot apa yang muncul
3. Mode selection: pilih **"USER"** (bukan DEV)
4. Bootstrap akan mulai

### Bootstrap (model download)
- **Pertama kali**: download Gemma 4 model (4GB). **Pastikan WiFi connected**, jangan pakai mobile data (boros).
- Progress bar harus bergerak. Kalau stuck >2 menit di 0%, restart app.
- Selesai loading → Home screen muncul

### Home screen
Verify:
- ModelStatusCard tampil "Gemma X · READY"
- Accessibility indicator: hijau "Accessibility aktif"
- Floating action button "Chat" muncul
- Tidak crash

**Kalau crash atau stuck >5 menit di sini, kirim screenshot. STOP test.**

---

## Step 4 — 5 Critical Journey Test

Catat hasil di table di bawah setiap test.

### Test 1: Smoke — boot sequence
**Sudah lewat di Step 3 di atas.**

Pass criteria:
- [ ] App tidak crash dari open sampai Home
- [ ] Engine status "READY" di ModelStatusCard
- [ ] Accessibility indicator hijau

### Test 2: Simple command "nyalakan senter"

**Steps**:
1. Tap Floating Action Button "Chat"
2. Ketik: `nyalakan senter`
3. Tap kirim (paper plane icon)
4. Tunggu response (~3-5 detik)

**Pass criteria**:
- [ ] User message muncul di chat sebagai bubble user
- [ ] Status header berubah jadi "Fuu sedang berpikir..." (warna primary)
- [ ] Senter HP nyala dalam ≤10 detik
- [ ] Response Fuu muncul di chat (mis. "Senter sudah dinyalakan")
- [ ] Status kembali ke "Ready" setelah selesai

**Kalau fail**, catat: status apa, response apa, dan apakah senter nyala atau tidak.

### Test 3: Multi-step — "buka TikTok lalu cari ikan"

⚠️ **Pre-req**: TikTok harus terinstall di HP. Kalau belum, install dari Play Store dulu.

**Steps**:
1. Di Chat, ketik: `buka TikTok lalu cari ikan`
2. Tap kirim
3. Observe:
   a. TikTok terbuka
   b. Search box ter-tap dan terisi "ikan"
4. Test stop button: ulangi command, tap **stop** (red square button) di tengah eksekusi

**Pass criteria**:
- [ ] TikTok terbuka dalam ≤10 detik
- [ ] Search "ikan" otomatis dimasukkan ke search box (atau Fuu kasih tahu kalau accessibility limited)
- [ ] Stop button bisa dipencet, command abort, status kembali Idle

**Kalau TikTok terbuka tapi search tidak otomatis**: ini expected karena TikTok anti-accessibility. ChibiClaw v3 belum punya vision-first (planned di v4 Phase 3). Catat hasil partial pass.

### Test 4: Safety gate — kirim SMS dengan konfirmasi

⚠️ **Pre-req**: Ada nomor target untuk SMS (boleh nomor sendiri di kartu kedua, atau nomor teman yang siap).

**Test 4a: User reject (tap Tidak)**
1. Di Chat, ketik: `kirim SMS ke 081xxxxxxxxxx isi: tes ChibiClaw`
2. Tap kirim
3. ConfirmationOverlay muncul di bawah layar dengan "HIGH SEVERITY" badge
4. Tap **"Tidak"**

Pass criteria 4a:
- [ ] Overlay muncul dalam ≤5 detik
- [ ] Badge "HIGH SEVERITY" warna oranye/merah
- [ ] Tap "Tidak" → overlay hilang
- [ ] **SMS TIDAK terkirim** (cek di app SMS / kontak target)
- [ ] Response Fuu di chat: "denied: user_cancelled_sms" atau similar

**Test 4b: User approve (tap Ya, Lanjut)**
1. Repeat command
2. Saat overlay muncul, tap **"Ya, Lanjut"**

Pass criteria 4b:
- [ ] SMS terkirim ke nomor target (verify di app SMS)
- [ ] Response Fuu: "OK terkirim" atau similar

**Test 4c: Auto-deny timeout**
1. Repeat command
2. Saat overlay muncul, **diamkan tidak ditekan**
3. Tunggu 30 detik

Pass criteria 4c:
- [ ] Overlay hilang otomatis setelah ~30 detik
- [ ] SMS tidak terkirim
- [ ] Response Fuu: "denied" atau similar

### Test 5: Error path — engine not ready

**Steps**:
1. Settings → AI Engine → tap **"Unload Model"** (atau similar)
2. Tunggu engine status berubah jadi "UNLOADED"
3. Kembali ke Chat
4. Ketik perintah apa pun: `apa kabar` atau `nyalakan senter`
5. Tap kirim

**Pass criteria**:
- [ ] Tidak crash
- [ ] Response: "Model belum siap. Coba lagi." atau similar graceful error
- [ ] Setelah re-load model di Settings, command kembali jalan

---

## Step 5 — Regression Checklist (10 menit)

Cek 7 fitur yang sudah ada di v3, pastikan tidak broken:

- [ ] **Notification listener**: Settings → Notification Triggers. Add 1 trigger. Verify saat notif app yang trigger masuk, ChibiClaw process (cek log atau response)
- [ ] **Cron tasks**: Settings → Scheduled Tasks. Buat task one-shot 1 menit ke depan dengan command `nyalakan senter`. Tunggu, verify execute
- [ ] **DevConsole**: Buka Dev Mode toggle (3× tap version di Settings → About) → bottom nav → Dev Console. Verify logs tampil dengan tag (AGENT, TOOL, dll)
- [ ] **Dashboard stats**: Setelah test 2-4, kembali Home. Verify Tasks count > 0, Success Rate %, Errors count akurat
- [ ] **Floating overlay**: Saat command berjalan, verify floating bubble muncul (kanan bawah). Saat idle, hilang
- [ ] **Quick Settings tile**: Pull down notification → tap "Edit tiles" → drag ChibiClaw tile ke active. Tap tile → kill switch toggle
- [ ] **AIDL whitelist**: (Skip kalau tidak punya 3rd party app yang call ChibiClaw via AIDL)

---

## Step 6 — Test Result Report

Copy template di bawah, isi hasil, kirim ke saya:

```markdown
# Phase 0 Test Run — 2026-MM-DD

**Device**: Xiaomi 17 Pro Max (Snapdragon 8 Elite Gen 5, 16GB RAM, China ROM Play Protect certified)
**HyperOS version**: ___ (isi)
**APK**: app-debug.apk (v3, 345 MB)

## Setup Phase
- [ ] APK install: [✓ / ✗] - method: ___
- [ ] HyperOS permission setup completed
- [ ] Onboarding completed: [✓ / ✗] - durasi model download: ___ menit

## Test Results

### Test 1: Boot sequence
Status: [PASS / FAIL / PARTIAL]
Notes: 

### Test 2: Simple command (nyalakan senter)
Status: [PASS / FAIL / PARTIAL]
Latency: ___ detik
Notes:

### Test 3: Multi-step (buka TikTok cari ikan)
Status: [PASS / FAIL / PARTIAL]
Notes (TikTok opens? Search filled? Stop works?):

### Test 4: Safety gate SMS
- 4a (reject): [PASS / FAIL]
- 4b (approve): [PASS / FAIL]
- 4c (timeout): [PASS / FAIL]
Notes:

### Test 5: Error path (engine not ready)
Status: [PASS / FAIL]
Notes:

## Regression Checklist
- [ ] Notification listener
- [ ] Cron tasks
- [ ] DevConsole
- [ ] Dashboard stats
- [ ] Floating overlay
- [ ] Quick Settings tile
- [ ] AIDL whitelist (skip if N/A)

## Bugs Found

| Severity | Description | Steps to reproduce |
|----------|-------------|---------------------|
| P0/P1/P2 | ... | ... |

## Performance Observations
- Wake word battery (kalau test continuous): N/A in v3 (no wake word yet)
- Inference latency offline: ___ detik average for simple command
- Crash rate: ___ in N hours session
- App memory peak: ___ GB (cek di Settings → Apps → ChibiClaw → Battery & memory)

## Overall Verdict
- [ ] v3 baseline SOLID, lanjut ke Phase 1 v4
- [ ] v3 baseline OK with minor issues, fix dulu sebelum Phase 1
- [ ] v3 baseline BROKEN, butuh major debug
```

---

## Decision After Test

**Kalau hasil ≥80% PASS, 0 P0 bug**:
→ ✅ Lanjut Phase 1 v4 (multi-adapter foundation)

**Kalau ada P0/P1 bug**:
→ Fix dulu di v3 codebase, lalu re-test affected scenarios.

**Kalau lebih dari 50% test FAIL**:
→ Major investigation. Mungkin issue setup HyperOS, mungkin issue ROM compatibility. Pause v4 plan.

---

## FAQ

**Q: Senter saya nyala tapi tidak via Fuu, kenapa?**
A: System control flashlight pakai CameraManager.setTorchMode (bukan Intent). Di Xiaomi mungkin butuh extra permission. Cek logcat dengan tag "TOOL" atau "SystemApiExecutor".

**Q: TikTok terbuka tapi search tidak otomatis terisi**
A: Expected di v3. TikTok deteksi accessibility automation, refuse to expose UI tree. v4 Phase 3 vision-first akan handle ini.

**Q: ConfirmationOverlay tidak muncul saat SMS test**
A: Likely "Display pop-up windows while running in background" belum ON di Settings. Re-check Step 2a.

**Q: App crash saat onboarding**
A: Kemungkinan model download corrupt atau insufficient storage. Clear app data, try again. Pastikan 6GB+ storage free.

**Q: Wake word kapan?**
A: v4 Phase 2. v3 masih text-only chat.

**Q: HyperOS battery saver matikan ChibiClaw setelah 10 menit**
A: Re-check Step 2a. Pastikan "No restrictions" + "Locked in recent apps". Kalau masih terjadi, screenshot Settings yang relevan.

---

## Saya Standby

Setelah kamu test, kirim:
1. Test Result Report (filled markdown template)
2. Screenshot untuk fail/partial scenarios kalau ada
3. Logcat output (kalau crash) — pakai Catlog atau MatLog app

Saya analisa, kasih fix kalau ada bug, dan green-light Phase 1 kickoff.
