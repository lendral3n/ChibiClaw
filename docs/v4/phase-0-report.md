# Phase 0 Test Report — ChibiClaw v3 Validation

**Test date**: 2026-05-__ (isi tanggal aktual test)
**Tester**: Lendra
**Goal**: Validate v3 baseline di Xiaomi 17 Pro Max sebelum Phase 1 v4 kickoff (target 2026-05-17).

---

## APK Under Test

| Field | Value |
|-------|-------|
| File | `ChibiClaw/app/build/outputs/apk/debug/app-debug.apk` |
| versionName | `3.0.0` |
| versionCode | `1` |
| applicationId | `com.chibiclaw` |
| variantName | `debug` |
| minSdk | `28` |
| Size | 365 MB (383,216,889 bytes) |
| Build date | 2026-05-12 16:27:58 WIB |
| SHA-256 | `dcc88795dc40f76e1201ee9ac6e7971fb432cfaadd4fb1d60c5901588805e42e` |
| SHA-1 | `e45736f70cf45dd3512168106dfa95d9a18fad2f` |

**Catatan komposisi build:** APK ini dibuild dari working tree yang berisi V3 refactor (tool-centric agent) + V4 scaffolding (registry, voice/vision stub). V4 stub kosong dan tidak di-wire ke main flow, jadi expected behavior = V3 baseline. Konfirmasi lewat journey test di bawah.

---

## Device Profile

| Field | Value |
|-------|-------|
| Device | Xiaomi 17 Pro Max |
| Chipset | Snapdragon 8 Elite Gen 5 |
| RAM | 16 GB |
| Storage free pre-install | ___ GB (isi) |
| ROM | HyperOS China (Play Protect certified) |
| HyperOS version | ___ (isi) |
| Android version | ___ (isi) |
| Locale | ___ (isi) |

---

## Setup Phase

### Transfer & Install
- [ ] APK transfer method: ___ (USB adb / file copy / WiFi / cloud)
- [ ] Install sukses: ✓ / ✗
- [ ] Install duration: ___ detik
- [ ] Catatan kalau install error: ___

### HyperOS Permission Setup (Step 2 di test guide)

**Battery & Background** (Settings → Apps → Manage apps → ChibiClaw):
- [ ] Battery saver → "No restrictions"
- [ ] Autostart → ON
- [ ] Display pop-up windows while running in background → ON ⚠️
- [ ] Display over other apps → ON
- [ ] Notifications → Show on lock screen → ON
- [ ] Notifications → all categories → Allow
- [ ] Mobile data → Background data → Unrestricted
- [ ] WiFi → Background data → Unrestricted

**Battery → Battery saver → Choose apps**:
- [ ] ChibiClaw → "No restrictions"

**Permission grants** (saat onboarding):
- [ ] Microphone
- [ ] Notifications
- [ ] Display over other apps
- [ ] Accessibility Service (confirm 2x)
- [ ] MediaProjection (per-session)

**Lock in Recent Apps**:
- [ ] ChibiClaw card di Recent Apps locked (padlock icon)

### Onboarding
- [ ] Setup wizard sukses tampil: ✓ / ✗
- [ ] ROM detected sebagai Xiaomi China: ✓ / ✗ (kalau ada deteksi, screenshot)
- [ ] Mode dipilih: USER
- [ ] Model download (Gemma 4 ~4GB): ✓ / ✗
- [ ] Download duration: ___ menit
- [ ] Network type saat download: WiFi / Mobile
- [ ] ModelStatusCard "Gemma X · READY": ✓ / ✗
- [ ] Accessibility indicator hijau: ✓ / ✗
- [ ] FAB "Chat" muncul: ✓ / ✗
- [ ] Crash count selama setup: ___

---

## Test Results — 5 Critical Journey

### Test 1: Boot Sequence (Smoke)
**Pass criteria:** App tidak crash dari open sampai Home; engine status READY; accessibility hijau.

| Field | Value |
|-------|-------|
| Status | [ ] PASS [ ] FAIL [ ] PARTIAL |
| Cold start duration | ___ detik |
| Notes | ___ |

### Test 2: Simple Command — "nyalakan senter"
**Pass criteria:** Status berubah "Fuu sedang berpikir...", senter HP nyala ≤10s, response Fuu muncul, status kembali Ready.

| Field | Value |
|-------|-------|
| Status | [ ] PASS [ ] FAIL [ ] PARTIAL |
| Latency total (kirim → senter nyala) | ___ detik |
| Senter aktual nyala | [ ] Ya [ ] Tidak |
| Response Fuu | ___ (quote) |
| Notes | ___ |

### Test 3: Multi-step — "buka TikTok lalu cari ikan"
**Pre-req:** TikTok terinstall.
**Pass criteria:** TikTok terbuka ≤10s, search "ikan" otomatis terisi, stop button bisa abort.

| Field | Value |
|-------|-------|
| TikTok terbuka | [ ] Ya [ ] Tidak |
| Latency TikTok open | ___ detik |
| Search "ikan" otomatis terisi | [ ] Ya [ ] Tidak [ ] Partial (Fuu kasih notice) |
| Stop button abort jalan | [ ] Ya [ ] Tidak |
| Status overall | [ ] PASS [ ] FAIL [ ] PARTIAL |
| Notes (TikTok anti-accessibility = expected partial di v3) | ___ |

### Test 4: Safety Gate — Kirim SMS
**Pre-req:** Nomor target SMS siap.

**4a — User reject (tap Tidak):**
| Field | Value |
|-------|-------|
| ConfirmationOverlay muncul ≤5s | [ ] Ya [ ] Tidak |
| Badge "HIGH SEVERITY" visible | [ ] Ya [ ] Tidak |
| Tap "Tidak" → overlay hilang | [ ] Ya [ ] Tidak |
| SMS TIDAK terkirim (verify di SMS app) | [ ] Confirmed [ ] Bocor terkirim |
| Response Fuu | ___ (quote) |
| Status | [ ] PASS [ ] FAIL |

**4b — User approve (tap Ya, Lanjut):**
| Field | Value |
|-------|-------|
| SMS terkirim ke target (verify di SMS app) | [ ] Ya [ ] Tidak |
| Response Fuu | ___ (quote) |
| Status | [ ] PASS [ ] FAIL |

**4c — Auto-deny timeout (diam 30s):**
| Field | Value |
|-------|-------|
| Overlay hilang otomatis setelah ~30s | [ ] Ya [ ] Tidak |
| Actual timeout duration | ___ detik |
| SMS tidak terkirim | [ ] Confirmed [ ] Bocor terkirim |
| Response Fuu | ___ (quote) |
| Status | [ ] PASS [ ] FAIL |

### Test 5: Error Path — Engine Not Ready
**Steps:** Unload model di Settings → AI Engine, lalu kirim command apapun.

| Field | Value |
|-------|-------|
| Status | [ ] PASS [ ] FAIL [ ] PARTIAL |
| Crash | [ ] No [ ] Yes |
| Graceful error message | ___ (quote) |
| Setelah re-load model, command jalan lagi | [ ] Ya [ ] Tidak |
| Notes | ___ |

---

## Regression Checklist (7 item)

| # | Fitur | Status | Notes |
|---|-------|--------|-------|
| 1 | Notification listener (1 trigger test) | [ ] PASS [ ] FAIL [ ] N/A | ___ |
| 2 | Cron task (1-menit one-shot) | [ ] PASS [ ] FAIL [ ] N/A | ___ |
| 3 | DevConsole (logs visible) | [ ] PASS [ ] FAIL [ ] N/A | ___ |
| 4 | Dashboard stats (tasks/success rate/errors) | [ ] PASS [ ] FAIL [ ] N/A | ___ |
| 5 | Floating overlay (visible saat command jalan) | [ ] PASS [ ] FAIL [ ] N/A | ___ |
| 6 | Quick Settings tile (kill switch) | [ ] PASS [ ] FAIL [ ] N/A | ___ |
| 7 | AIDL whitelist (skip kalau N/A) | [ ] PASS [ ] FAIL [ ] N/A | ___ |

---

## Bugs Found

| Severity | ID | Description | Steps to reproduce | Logcat tag |
|----------|----|----|----|----|
| P0/P1/P2 | - | (isi kalau ada) | - | - |

**Severity definition:**
- **P0** — Blocker: crash, data loss, security, fitur core gagal total
- **P1** — Major: fitur tidak jalan tapi ada workaround
- **P2** — Minor: visual glitch, edge case, copy typo

---

## Performance Observations

| Metric | Value |
|--------|-------|
| Inference latency offline (avg simple command) | ___ detik |
| App memory peak (Settings → Apps → ChibiClaw → Battery & memory) | ___ GB |
| Crash count selama sesi test (___ jam) | ___ |
| Battery drain selama sesi test | ___ % |

---

## Test Summary

| Metric | Count |
|--------|-------|
| Total test scenarios | 12 (5 journey + 7 regression) |
| PASS | ___ |
| FAIL | ___ |
| PARTIAL | ___ |
| **Pass percentage** | ___ % |
| **P0 bugs** | ___ |
| **P1 bugs** | ___ |
| **P2 bugs** | ___ |

---

## Gate Decision

Per `04-implementation-roadmap.md`:

| Hasil | Action |
|-------|--------|
| ≥80% PASS + 0 P0 | ✅ Phase 1 kickoff GO untuk 2026-05-17 |
| 60-79% PASS atau ada P1-only | ⚠️ Fix v3.0.1 patch, ulang journey yang FAIL |
| <60% PASS atau ada P0 | ❌ Pause, evaluate scope, mungkin v3.1 minor release |

**Decision (lingkari salah satu):**
- [ ] ✅ GO — lanjut Phase 1 v4
- [ ] ⚠️ HOLD — fix dulu sebelum kickoff
- [ ] ❌ ROLLBACK — major investigation

**Justification:** ___

---

## Sign-off

- Tester: Lendra
- Date completed: 2026-05-__
- Signature: ___

---

## Attachments

Letakkan di `docs/v4/phase-0-attachments/` (buat folder kalau perlu):
- Screenshot per FAIL/PARTIAL scenario
- Logcat output kalau ada crash (Catlog / MatLog export)
- Video recording untuk UX issue (optional)

---

## Notes / Follow-up

(Catatan bebas: observasi UX, ide perbaikan, isu yang ditemukan tapi out-of-scope test, dll)

___
