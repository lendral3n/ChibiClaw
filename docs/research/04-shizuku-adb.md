# Shizuku Kotlin Binding Modern + ADB Shell Command Catalog untuk Tier4 ChibiClaw

> **Dokumen Riset 04** — Bagian dari seri riset arsitektur ChibiClaw v4
> **Topik:** Tier4 executor (Shizuku/root) untuk Android 14/15/16 dengan Kotlin modern + katalog ADB shell command yang siap automasi
> **Tanggal akses sumber:** 2026-05-13
> **Status:** Draft riset — bukan spesifikasi final; verifikasi tiap command di device target sebelum produksi

---

## 0. Ringkasan Eksekutif

ChibiClaw mengadopsi **tier executor pyramid**: tier0 (utility lokal) → tier1 (intent) → tier2 (content provider/API publik) → tier3 (AccessibilityService) → tier4 (Shizuku/Sui/root). Tier4 mengisi gap kapabilitas yang tidak dapat dijangkau accessibility, terutama: `pm grant <runtime-permission>`, `am force-stop`, `pm install`/`uninstall` non-interaktif, `cmd notification`, akses `settings put global/secure`, dan content provider write yang dilindungi permission system.

Inti tier4 = **Shizuku v13.x** (rikkaApps), open-source MIT, ekosistem dewasa, dipakai produksi oleh ratusan aplikasi (IceBox, LSPosed Manager, RootlessJamesDSP, LibChecker, Tasker 6.6+, Geto). Shizuku menjalankan satu Java process (`app_process`) dengan UID shell (2000) via ADB atau UID root (0) via Sui Magisk module. Aplikasi klien memanggil API system via binder yang dimediasi Shizuku server. Bahasa default Kotlin first-class; library AAR di Maven Central di bawah groupId `dev.rikka.shizuku`.

Risiko terbesar untuk pengguna awam: **setup wizard** (pairing wireless ADB), **persistence pasca-reboot** (perlu re-aktivasi tiap boot kecuali Sui/root atau Android 13+ dengan trusted-WiFi auto-start dari Shizuku 13.6.0), dan **vendor lock-in** MIUI/HyperOS yang menuntut SIM card + akun Mi + toggle "USB debugging (Security settings)".

Rekomendasi ChibiClaw: tier4 ditawarkan sebagai *opt-in advanced mode* dengan onboarding 5-langkah dan disclaimer eksplisit. Default produksi tetap tier0–tier3.

---

## 1. Shizuku Setup Modern (2026)

### 1.1 Repo Resmi & Library

| Komponen | Path | License | Catatan |
|---|---|---|---|
| Shizuku Manager APK | `github.com/RikkaApps/Shizuku` | Apache-2.0 | Aplikasi user-facing |
| Shizuku-API (lib) | `github.com/RikkaApps/Shizuku-API` | MIT | Yang di-link ChibiClaw |
| Sui (root mode) | `github.com/RikkaApps/Sui` | GPL-3.0 | Magisk/KernelSU module |
| Awesome-Shizuku | `github.com/timschneeb/awesome-shizuku` | CC0 | Referensi pengguna produksi |

Versi rilis stabil menurut pencarian pertengahan 2026: **Shizuku 13.6.0** (build r1091), mendukung Android 16 QPR1 dan auto-start non-root saat trusted Wi-Fi (Android 13+). Versi library AAR yang dipublish ke Maven Central dalam keluarga `13.x` (contoh 13.1.5 sampai 13.6.x). **Catatan verifikasi:** angka build & tanggal rilis perlu konfirmasi di Maven Central / GitHub Releases sebelum lock di `libs.versions.toml`.

### 1.2 Gradle Setup (Kotlin DSL)

```kotlin
// gradle/libs.versions.toml
[versions]
shizuku = "13.6.0"   # verify latest at https://mvnrepository.com/artifact/dev.rikka.shizuku/api

[libraries]
shizuku-api      = { module = "dev.rikka.shizuku:api",      version.ref = "shizuku" }
shizuku-provider = { module = "dev.rikka.shizuku:provider", version.ref = "shizuku" }
# Optional: AIDL helper module untuk UserService binding
shizuku-aidl     = { module = "dev.rikka.shizuku:aidl",     version.ref = "shizuku" }

// app/build.gradle.kts
dependencies {
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}
```

### 1.3 AndroidManifest Provider

```xml
<provider
    android:name="rikka.shizuku.ShizukuProvider"
    android:authorities="${applicationId}.shizuku"
    android:multiprocess="false"
    android:enabled="true"
    android:exported="true"
    android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />

<uses-permission android:name="moe.shizuku.manager.permission.API_V23" />
```

Provider ini dipanggil Shizuku Manager untuk mengirim binder ke proses klien. Atribut `exported=true` wajib agar Shizuku server bisa connect lintas-proses; permission `INTERACT_ACROSS_USERS_FULL` di sini berfungsi sebagai pagar (hanya proses dengan UID system/shell yang bisa memanggil provider — bukan untuk aplikasi pihak ketiga).

### 1.4 Flow Pairing Android 11+ (Wireless ADB) per ROM

**Generic AOSP / Pixel / OneUI:**

1. Settings → About phone → tap "Build number" 7 kali → masuk Developer mode.
2. Settings → System → Developer options → enable **Wireless debugging**.
3. Tap **Pair device with pairing code** → catat kode 6 digit & port.
4. Buka Shizuku → "Start via Wireless debugging" → "Pairing" → masukkan kode.
5. Kembali ke Shizuku home → "Start" → server jalan, UID = 2000.

**MIUI / HyperOS (Xiaomi/Redmi/POCO):**

Tambahkan setelah langkah 2:

- Aktifkan **USB debugging** *dan* **USB debugging (Security settings)** — toggle kedua wajib untuk `INJECT_EVENTS` dan grant runtime permission via ADB.
- Aktifkan **Install via USB** & **Install via USB (Security settings)**.
- Login akun Mi & pastikan SIM card terpasang (HyperOS menolak toggle Security tanpa SIM/akun).
- Disable VPN/firewall private (MIUI sering blok loopback ADB pairing).
- Beberapa region MIUI menerapkan **autorevocation 7 hari** untuk grant ADB pada akun baru; verifikasi di device.

**ColorOS / OxygenOS (Oppo/OnePlus):**

- Sebagian build OnePlus Android 13+ membatasi grant beberapa permission via ADB (issue RikkaApps/Shizuku #1224). Workaround: setup awal lewat PC ADB via USB.
- Hindari fitur "Optimized battery" untuk Shizuku Manager; ColorOS agresif membunuh background service.

**OneUI (Samsung):**

- Wireless debugging tersedia mulai OneUI 4 (Android 12+).
- Knox container & Secure Folder tidak meneruskan pair-code popup; lakukan pairing di profil utama.

### 1.5 Permission Lifecycle

- **Grant model:** per-UID, per-app, granular. Shizuku Manager menampilkan list aplikasi yang request `moe.shizuku.manager.permission.API_V23`. User menekan **Allow** → Shizuku menyimpan UID grant.
- **Pengecekan client-side:** `Shizuku.checkSelfPermission()` mengembalikan `PERMISSION_GRANTED` (0) atau `PERMISSION_DENIED` (-1). Cek sebelum tiap operasi sensitif, bukan sekali boot.
- **Revocation:** instan setelah user revoke di Shizuku Manager. Tambahan: jika user mematikan Shizuku server (atau reboot tanpa Sui), seluruh grant tetap di-cache tapi binder hilang → `binderDeadListener` ter-trigger.
- **Auto-revocation:** Android 11+ memiliki permission auto-revoke jika app idle >30 hari; permission Shizuku tidak otomatis di-revoke (custom permission, bukan runtime standar).

---

## 2. Katalog ADB Shell Command Tier4

Semua perintah berikut dapat dipanggil via Shizuku dengan dua pola:

1. **UserService binding** (rekomendasi) — bind service AIDL yang berjalan UID 2000, panggil method Kotlin biasa. Latensi ~5–30 ms/call setelah service hidup.
2. **`Shizuku.newProcess()`** — spawn `sh` subprocess, kirim command via stdin. Latensi ~50–200 ms/spawn. **Deprecated** menurut diskusi RikkaApps/Shizuku-API #276; gunakan UserService untuk produksi.

Tabel berikut menandai: API level minimum, *availability* di Android 14/15 setelah lockdown, output parse-ability, dan signal audit yang user lihat.

### 2.1 Activity Manager (`am`)

| Command | Use case | Min API | A14/15 status | Output | Audit signal |
|---|---|---|---|---|---|
| `am start -n pkg/.Activity` | Launch activity | 1 | OK | Plain text result | Activity tampil normal |
| `am start-foreground-service` | Start FGS | 26 | OK (butuh FGS type sejak A14) | Plain text | Notif FGS muncul |
| `am force-stop <pkg>` | Hentikan paksa | 11 | OK (shell), butuh `KILL_BACKGROUND_PROCESSES` untuk app non-system | Silent | App hilang dari recents |
| `am kill <pkg>` | Soft kill | 14 | OK | Silent | Tidak selalu kill foreground |
| `am broadcast -a ACTION` | Send broadcast | 1 | OK; banyak broadcast protected sejak A14 | Result code text | Tergantung receiver |
| `am instrument` | Test runner | 1 | OK | Plain text + stream | Tampak di logcat |
| `am set-inactive <pkg> true` | Force app standby | 23 | OK | Silent | App standby bucket berubah |
| `am get-standby-bucket <pkg>` | Cek standby bucket | 28 | OK | Integer (10/20/30/40/45/50) | – |
| `am compat enable/disable <CHANGE_ID> <pkg>` | Compat framework | 29 | OK | Plain text | Restart app |

Catatan A14: `am start` untuk activity yang exported=false menolak intent shell sejak A14; gunakan **content provider** atau tier3 jika target activity tidak exported.

### 2.2 Package Manager (`pm`)

| Command | Use case | Min API | A14/15 status | Output |
|---|---|---|---|---|
| `pm grant <pkg> <permission>` | Grant runtime permission | 23 | OK; sebagian permission "appop"-style butuh `appops` | Silent atau error |
| `pm revoke <pkg> <permission>` | Revoke | 23 | OK | Silent |
| `pm list packages [-d|-e|-s|-3]` | List app | 1 | OK | `package:com.x` per baris (parse-friendly) |
| `pm list permissions -g -d` | List permission grup | 1 | OK | Hierarki text |
| `pm install [-r] [-t] [--user 0] file.apk` | Install | 1 | OK; A14 block targetSdk<23 (`--bypass-low-target-sdk-block`) | "Success"/"Failure ..." |
| `pm install-create` → `pm install-write` → `pm install-commit` | Streamed/split APK | 21 | OK | Session ID + status |
| `pm uninstall [-k] [--user 0] <pkg>` | Uninstall | 1 | OK | "Success"/"Failure" |
| `pm disable-user --user 0 <pkg>` | Disable per user | 21 | OK | "Package new state: disabled-user" |
| `pm enable --user 0 <pkg>` | Re-enable | 1 | OK | "Package new state: enabled" |
| `pm hide --user 0 <pkg>` | Hide (suspend-like) | 24 | OK | Plain text |
| `pm clear <pkg>` | Wipe app data | 1 | OK; butuh `CLEAR_APP_USER_DATA` (shell punya) | "Success" |
| `pm set-installer <pkg> <installer>` | Set installer source | 23 | OK | Silent |
| `pm get-install-location` | Get install location | 21 | OK | `0`/`1`/`2` |

### 2.3 Dumpsys (read-only intelligence)

Dumpsys menghasilkan teks tidak terstruktur; parse pakai regex/state-machine. Rekomendasi: ambil **section-spesifik** (e.g. `dumpsys activity activities` bukan full `dumpsys`).

| Service | Command | Yang berguna untuk agent | Catatan |
|---|---|---|---|
| `activity` | `dumpsys activity activities` | Top resumed activity, task stack, current focus | Parse "ResumedActivity:" line |
| `activity` | `dumpsys activity recents` | Recent task list | – |
| `window` | `dumpsys window windows` | Window stack, IME state, fokus | Parse "mCurrentFocus" |
| `notification` | `dumpsys notification --noredact` | Notif aktif per channel | `--noredact` butuh shell |
| `alarm` | `dumpsys alarm` | Alarm queue per app | Untuk DND/wake-up debug |
| `jobscheduler` | `dumpsys jobscheduler` | Job pending/run | Output format berubah antar-versi (A6 vs A7+; sangat berbeda lagi A14+) — selalu test |
| `deviceidle` | `dumpsys deviceidle` | Doze state | Lihat sub-perintah `step/force-idle/get` |
| `sensorservice` | `dumpsys sensorservice` | Listener aktif & rate | Audit privacy |
| `battery` | `dumpsys battery` | State + simulasi (`unplug`, `set level`) | Tester berguna |
| `power` | `dumpsys power` | WakeLock list | Cari leak |
| `usagestats` | `dumpsys usagestats` | App usage timeline | Butuh A14 `PACKAGE_USAGE_STATS` (Shizuku grant) |

### 2.4 Input (`input`)

| Command | Min API | A14/15 | Catatan |
|---|---|---|---|
| `input keyevent <CODE>` | 1 | OK | Lihat enum `KeyEvent.KEYCODE_*` |
| `input tap <x> <y>` | 4 | OK | – |
| `input swipe <x1> <y1> <x2> <y2> [duration]` | 18 | OK | Latency ~20–50ms per call |
| `input text "string"` | 4 | OK; spasi → `%s` di sebagian build | – |
| `input draganddrop x1 y1 x2 y2` | 24 | OK | – |
| `input roll dx dy` | 18 | OK | Trackball event |

**Limitasi krusial vs Accessibility (tier3):**

- `input` *tidak* punya konteks node — hanya koordinat absolut. Untuk klik tombol yang lokasinya dinamis, tetap pakai tier3 (`AccessibilityNodeInfo.performAction(ACTION_CLICK)`).
- `input tap` menghasilkan event injection yang **terdeteksi** oleh banking app yang pakai `View#filterTouchesWhenObscured` atau `MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED`. Beberapa app (Google Pay, mobile-banking ID) reject tap injected dari shell.
- Touch injection via shell **tidak melewati** `dispatchKeyEvent` override di app — beberapa anti-cheat menolak.

### 2.5 Settings (`settings`)

```
settings [--user N] get|put|delete global|system|secure <key> [value]
```

| Key example | Namespace | Use case |
|---|---|---|
| `screen_brightness` | system | Set brightness 0–255 |
| `screen_off_timeout` | system | ms sampai sleep |
| `airplane_mode_on` | global | 0/1 |
| `bluetooth_on` | global | 0/1 |
| `wifi_on` | global | 0/1 (jangan diandalkan — `svc wifi enable/disable` lebih reliable) |
| `mobile_data` | global | 0/1 |
| `accessibility_enabled` | secure | Cek a11y master switch |
| `enabled_accessibility_services` | secure | List a11y service aktif |
| `default_input_method` | secure | IME aktif |
| `adb_wifi_enabled` | global | Toggle wireless debug (butuh `WRITE_SECURE_SETTINGS`) |
| `development_settings_enabled` | global | Toggle dev options |

Permission: `WRITE_SECURE_SETTINGS` cukup untuk `put secure`. Untuk `put global`, sebagian build A14+ menuntut UID system; Shizuku UID shell biasanya cukup.

### 2.6 Content (`content`)

```
content query --uri <uri> [--projection a:b:c] [--where "x=?"] [--sort "col"]
content insert --uri <uri> --bind name:s:value --bind id:i:42
content update --uri <uri> --bind col:s:newval --where "..."
content delete --uri <uri> --where "..."
```

Type prefix: `s` (string), `i` (int), `l` (long), `b` (boolean), `f` (float), `d` (double), `n` (null).

| URI | Permission yang biasanya butuh | Use case |
|---|---|---|
| `content://contacts/people` | `READ_CONTACTS` | Sudah deprecated; gunakan `content://com.android.contacts/data` |
| `content://com.android.contacts/contacts` | `READ_CONTACTS` | List kontak |
| `content://sms` | `READ_SMS` | Baca SMS (tier4 + special access; A14 batasi pengiriman) |
| `content://com.android.calendar/events` | `READ_CALENDAR` | Event |
| `content://media/external/images/media` | `READ_MEDIA_IMAGES` (A13+) | Galeri |
| `content://settings/secure` | `WRITE_SECURE_SETTINGS` | Backup of `settings put` |

Catatan A14: `READ_SMS` & `SEND_SMS` adalah *signature-protected* untuk default SMS app pasca A14 untuk app baru. ChibiClaw tidak bisa jadi default SMS handler tanpa user explicit set.

### 2.7 cmd (modern subcommand router)

`cmd <service> <action>` adalah versi modern dari sebagian dumpsys/setprop:

| Service | Contoh | Fungsi |
|---|---|---|
| `cmd package` | `cmd package list packages -e` | Setara `pm list`; lebih cepat di A12+ |
| `cmd notification` | `cmd notification post -S bigtext title body` | Post notifikasi langsung (debug) |
| `cmd notification` | `cmd notification allow_listener <component>` | Toggle NotificationListener |
| `cmd statusbar` | `cmd statusbar expand-notifications` / `collapse` | Buka/tutup shade |
| `cmd statusbar` | `cmd statusbar tile <tile>` | Manipulasi quick settings tile |
| `cmd role` | `cmd role get-role-holders android.app.role.SMS` | Cek default role |
| `cmd role` | `cmd role add-role-holder <role> <pkg>` | Set role (butuh shell; A14 batasi role tertentu) |
| `cmd jobscheduler` | `cmd jobscheduler run -f <pkg> <jobid>` | Trigger job paksa |
| `cmd alarm` | `cmd alarm set-time <ms>` | Set ulang waktu (jarang dipakai) |
| `cmd appops` | `cmd appops set <pkg> <op> allow` | App ops (ortogonal dengan permission) |
| `cmd power` | `cmd power set-mode 1` | Set power mode |
| `cmd display` | `cmd display set-night-mode 2` | Dark mode |
| `cmd shortcut` | `cmd shortcut get-shortcuts <pkg>` | Pin shortcut |
| `cmd usagestats` | `cmd usagestats set-app-standby-bucket <pkg> <bucket>` | Force bucket |

Lockdown A14/A15: beberapa `cmd role add-role-holder` pada role sensitif (e.g. `DIALER`, `SMS`, `ASSISTANT`) ditolak untuk UID shell; perlu signature platform. Test per device.

### 2.8 svc (service control)

```
svc power stayon true|usb|ac|wireless|false
svc wifi enable|disable
svc data enable|disable
svc bluetooth enable|disable
svc usb setFunctions <none|mtp|ptp|midi|...>
svc nfc enable|disable
```

`svc` lebih reliable daripada `settings put global` untuk toggle radio.

---

## 3. Security Boundary & Risk

### 3.1 Yang BISA Shizuku tapi TIDAK BISA Accessibility

- `pm grant <pkg> <runtime-permission>` — termasuk `READ_LOGS`, `WRITE_SECURE_SETTINGS`, `PACKAGE_USAGE_STATS`, `MANAGE_EXTERNAL_STORAGE` (A11+ proto-restricted).
- `pm install --user 0 file.apk` tanpa dialog Package Installer (saat ChibiClaw memiliki ACK install + Shizuku grant).
- `am force-stop` aplikasi lain (a11y hanya bisa "back" / "home", tidak kill).
- `appops set <pkg> <op> allow/deny` — toggle background-location, draw-overlays, ignore-battery-optimization tanpa dialog.
- `settings put secure enabled_accessibility_services` — ironisnya, **dengan Shizuku bisa enable tier3 sendiri** tanpa user buka Settings (ChibiClaw v4 menggunakan ini untuk auto-bootstrap tier3 setelah user grant tier4).
- Akses `dumpsys notification --noredact`, `dumpsys window`, full `usagestats` history.
- Spawn `app_process` lain → run privileged binder client.

### 3.2 Yang TIDAK BISA Shizuku (butuh root sebenarnya / Sui)

- Tulis ke `/system`, `/vendor`, `/product`. Shizuku UID 2000 tidak punya write.
- Modifikasi SELinux policy.
- Akses `/dev/block/*` (mount image).
- Kernel module load.
- `chmod`/`chown` di luar app sandbox & beberapa tmpfs.
- Iptables/nftables full rules (sebagian terbuka via `cmd netd`).

Untuk tier4+ yang butuh root, ChibiClaw harus mendeteksi Sui (`Shizuku.getUid() == 0`) atau Magisk dan mengaktifkan submode terpisah dengan UX warning yang lebih keras.

### 3.3 Vendor-Specific Gotcha

| Vendor | Issue |
|---|---|
| **MIUI/HyperOS** | Butuh "USB debugging (Security settings)" + akun Mi + SIM untuk grant runtime permission via ADB. Sebagian command `pm grant` masih ditolak tanpa security toggle aktif. |
| **HyperOS region China** | Beberapa region menerapkan rolling 7-day re-auth untuk ADB grant pada akun yang baru dibuat / SIM yang baru di-swap. |
| **ColorOS/OnePlus** | OnePlus Android 13+ batasi `pm grant` untuk subset permission spesifik (issue RikkaApps/Shizuku #1224); setup awal via PC USB lebih aman. |
| **OneUI (Samsung)** | Knox blokir beberapa `am instrument` di Secure Folder. |
| **HarmonyOS / Honor Magic OS** | Sebagian build menolak `app_process` spawn; verifikasi per ROM. |
| **MIUI/EMUI** | Power management agresif → Shizuku server bisa di-kill walau "foreground service" terlihat. Tambahkan autostart whitelist instruksi. |

### 3.4 Worst-Case Risk untuk User Awam

Jika user grant Shizuku ke aplikasi malicious:

- App bisa **install APK lain diam-diam** (`pm install`), termasuk APK trojan.
- App bisa **read SMS database** & token OTP (`content query --uri content://sms`).
- App bisa **disable Play Protect** (`pm disable com.google.android.gms` parsial), atau **enable tier3** untuk dirinya sendiri tanpa user buka Settings.
- App bisa **dump usagestats** untuk profiling penggunaan banking app, lalu trigger fishing overlay tepat saat user buka m-banking.

UX disclaimer ChibiClaw harus jelas: "Mengaktifkan Shizuku memberikan ChibiClaw kemampuan setara ADB. Hanya aktifkan jika Anda mengerti risikonya. ChibiClaw tidak akan menginstall app atau membaca SMS Anda kecuali Anda meminta secara eksplisit."

---

## 4. Kompatibilitas Non-Root vs Root

### 4.1 Mode Operasi

| Mode | UID | Persistence | Capability |
|---|---|---|---|
| Shizuku via ADB wireless | 2000 (shell) | Hilang saat reboot (kecuali Shizuku 13.6+ trusted-WiFi auto-start) | ADB-level |
| Shizuku via ADB USB (PC start) | 2000 | Hilang saat reboot | ADB-level |
| Shizuku via Sui (root) | 0 | Persist (Sui jalan di Zygisk) | Full root + ADB |
| Shizuku via app_process root | 0 | Persist jika `init.rc` patched | Full root |

### 4.2 Sui (Magisk/KernelSU Module)

- Sui adalah Zygisk module yang inject ke `system_server`, `SystemUI`, `Settings` (dan beberapa app target).
- Butuh Magisk Delta / KernelSU dengan Zygisk Next atau NeoZygisk aktif.
- API persis sama dengan Shizuku (binder), call `Sui.init(packageName)` di Application.onCreate untuk skip pairing flow.
- Tidak ada UI pairing — user grant lewat dialog Magisk.

### 4.3 Magisk + Shizuku Coexistence

Tidak konflik. Sui *mengganti* Shizuku binder dengan versi yang UID 0; jika Sui tidak terpasang, Shizuku APK berjalan normal UID 2000. ChibiClaw fallback:

```kotlin
val isRoot   = Shizuku.getUid() == 0
val isShell  = Shizuku.getUid() == 2000
val capability = when {
    isRoot   -> Capability.FULL_ROOT
    isShell  -> Capability.ADB_LEVEL
    else     -> Capability.NONE
}
```

### 4.4 WSA / Emulator Testing

- **Windows Subsystem for Android (WSA)** dihentikan Microsoft Maret 2025; sebagian developer migrasi ke Google Play Games for PC atau Android Studio Emulator API 34/35.
- Emulator AVD API 34+ mendukung Shizuku via `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh` (manual).
- **Catatan verifikasi:** behavior `app_process` di emulator x86_64 kadang berbeda dengan ARM64 device fisik — jangan hanya test di emulator.

---

## 5. Pattern Aplikasi Produksi (Referensi UX)

| App | Tujuan | UX Onboarding |
|---|---|---|
| **IceBox** (catchingnow.com) | Freeze app + bloatware | Wizard 3 langkah: install Shizuku → start Shizuku → grant IceBox |
| **LSPosed Manager** | Xposed framework | Mengandalkan Sui (root); jika belum, prompt install Sui module |
| **LibChecker** | Inspect APK library | Optional Shizuku — graceful degrade tanpa Shizuku |
| **Tasker** (6.6+) | Automation | Plugin "Shizuku Available" state + action; on-fail fallback to root/none |
| **Geto** | Per-app settings auto-apply | Wizard wajib Shizuku |
| **RootlessJamesDSP** | DSP equalizer | Adb dump audio policy, butuh `DUMP` permission grant via Shizuku |
| **VolumeManager** | Per-app volume | Audio policy reflection via Shizuku |
| **Brevent** (deprecated) | Background limit | Historis — diganti IceBox |

### Pelajaran UX (5 langkah yang sering bikin user stuck)

1. **"Saya install Shizuku tapi tidak bisa start"** — biasanya wireless debugging belum aktif. Solusi: in-app diagnostic yang cek `Settings.Global.ADB_WIFI_ENABLED`.
2. **"Pairing code expired"** — kode hanya valid ~5 menit; user lupa cepat. Solusi: link langsung dari ChibiClaw ke halaman pairing developer options via `Intent ACTION_DEVELOPMENT_SETTINGS`.
3. **"Sudah Allow tapi masih DENIED"** — Shizuku grant cached per UID; user butuh restart app klien. Solusi: deteksi `binderReceivedListener` & prompt restart.
4. **"Setelah reboot tidak jalan lagi"** — non-root limitation. Solusi: tutorial Shizuku 13.6+ trusted-WiFi auto-start dengan `WRITE_SECURE_SETTINGS` grant.
5. **"MIUI tetap denied"** — security setting toggle. Solusi: deteksi vendor (`Build.MANUFACTURER`), tampilkan ROM-specific tutorial.

---

## 6. Practical Concern untuk ChibiClaw

### 6.1 Latensi & Throughput

Perkiraan empiris dari sumber komunitas (perlu re-verifikasi di device target):

| Operasi | Latensi |
|---|---|
| Binder call ke UserService (warm) | 5–30 ms |
| Binder call cold start | 80–200 ms |
| `Shizuku.newProcess` spawn `sh` | 80–300 ms |
| Batch 10 settings put via UserService | 60–150 ms |
| Batch 10 settings put via 10 newProcess | 800–3000 ms |

**Rekomendasi:** untuk ChibiClaw tier4 yang chatty (e.g. agent batch grant permission ke beberapa app), wajib pakai **UserService AIDL**, bukan newProcess.

### 6.2 Lifecycle UserService

```kotlin
// AIDL: app/src/main/aidl/dev/bignet/chibiclaw/IClawShellService.aidl
interface IClawShellService {
    void destroy() = 16777114;
    String exec(String[] argv) = 1;
    int forceStop(String pkg) = 2;
    int grantPermission(String pkg, String permission, int userId) = 3;
    String dumpsysSection(String service, String[] args) = 4;
}

// Kotlin client
private val userServiceArgs = Shizuku.UserServiceArgs(
    ComponentName(BuildConfig.APPLICATION_ID, ClawShellService::class.java.name)
).daemon(false)
 .processNameSuffix(":claw_shell")
 .debuggable(BuildConfig.DEBUG)
 .version(BuildConfig.VERSION_CODE)

private val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        service = IClawShellService.Stub.asInterface(binder)
        _state.value = TierState.Ready
    }
    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
        _state.value = TierState.Disconnected
    }
}

fun start() = Shizuku.bindUserService(userServiceArgs, connection)
fun stop()  = Shizuku.unbindUserService(userServiceArgs, connection, true)
```

Daemon mode (`daemon(true)`) menjaga service hidup setelah unbind — berguna untuk job background, tapi user dapat melihat extra process di Settings → Apps. ChibiClaw default: `daemon(false)`, bind on-demand.

### 6.3 Kotlin Coroutine Wrapper

```kotlin
suspend fun Shizuku.awaitPermission(requestCode: Int = 1024): Boolean =
    suspendCancellableCoroutine { cont ->
        val listener = Shizuku.OnRequestPermissionResultListener { code, result ->
            if (code == requestCode) {
                cont.resume(result == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        cont.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            cont.resume(true)
        } else {
            Shizuku.requestPermission(requestCode)
        }
    }

val shizukuState: Flow<ShizukuState> = callbackFlow {
    val onReceived = Shizuku.OnBinderReceivedListener { trySend(ShizukuState.Ready(Shizuku.getUid())) }
    val onDead     = Shizuku.OnBinderDeadListener     { trySend(ShizukuState.Dead) }
    Shizuku.addBinderReceivedListener(onReceived)
    Shizuku.addBinderDeadListener(onDead)
    if (Shizuku.pingBinder()) trySend(ShizukuState.Ready(Shizuku.getUid()))
    else                      trySend(ShizukuState.Dead)
    awaitClose {
        Shizuku.removeBinderReceivedListener(onReceived)
        Shizuku.removeBinderDeadListener(onDead)
    }
}.flowOn(Dispatchers.Default)
```

### 6.4 UI Disclaimer & Setup Wizard

Komponen UI yang ChibiClaw v4 harus expose:

1. **Tier4 Education Card** — sebelum onboarding, jelaskan apa yang bisa & risiko (3 bullet).
2. **Detect & Diagnose** — cek: ADB wireless on? Shizuku terinstal? Server running? Grant aktif? Tampilkan 4 indikator hijau/merah.
3. **Step-by-step pairing** — embed YouTube/animated GIF per ROM (Pixel, MIUI, OneUI, ColorOS).
4. **Permission request UI** — saat tap "Aktifkan tier4", langsung panggil `Shizuku.requestPermission()` → tampilkan progress.
5. **Capability matrix** — setelah grant, tampilkan fitur ChibiClaw mana saja yang sekarang "Unlocked" (e.g. "Auto-grant permission untuk app baru", "Force-stop dari menu konteks", "Install update silently").
6. **Revoke entry** — link ke Shizuku Manager untuk revoke; ChibiClaw tidak menyimpan grant sendiri.

---

## 7. Source / Repo Top untuk Dipelajari

| Repo | URL | License | Aktivitas (per akses 2026-05-13) | Highlight |
|---|---|---|---|---|
| RikkaApps/Shizuku | github.com/RikkaApps/Shizuku | Apache-2.0 | Aktif; v13.6.x | App manager + server impl |
| RikkaApps/Shizuku-API | github.com/RikkaApps/Shizuku-API | MIT | Aktif | Library publik AAR |
| RikkaApps/Sui | github.com/RikkaApps/Sui | GPL-3.0 | Aktif | Root mode Zygisk |
| solrudev/Ackpine | github.com/solrudev/Ackpine | Apache-2.0 | Aktif | Package installer Kotlin-first dengan Shizuku plugin |
| zacharee/ShizukuHackApi | github.com/zacharee/ShizukuHackApi | MIT | Updates kadang | Helper hidden API + Shizuku |
| timschneeb/awesome-shizuku | github.com/timschneeb/awesome-shizuku | CC0 | Auto-crawler | List apps produksi |
| heruoxin/Ice-Box-Docs | github.com/heruoxin/Ice-Box-Docs | – | Stable | Dokumentasi IceBox + Shizuku UX |
| operando/Android-Command-Note | github.com/operando/Android-Command-Note | MIT | Stable | Referensi dumpsys output parsing |
| Kardelio/easy-dumpsys | github.com/Kardelio/easy-dumpsys | MIT | Aktif | Tool parsing dumpsys ringkas |

### Snippet kode Kotlin contoh: exec command via UserService

```kotlin
// ClawShellService.kt — runs UID 2000 in :claw_shell process
class ClawShellService : IClawShellService.Stub() {
    override fun destroy() { System.exit(0) }

    override fun exec(argv: Array<String>): String {
        val pb = ProcessBuilder(*argv).redirectErrorStream(true)
        val p = pb.start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        return out
    }

    override fun forceStop(pkg: String): Int =
        exec(arrayOf("am", "force-stop", pkg)).let { 0 }

    override fun grantPermission(pkg: String, permission: String, userId: Int): Int =
        exec(arrayOf("pm", "grant", "--user", userId.toString(), pkg, permission))
            .let { if (it.contains("Operation not allowed")) -1 else 0 }

    override fun dumpsysSection(service: String, args: Array<String>): String =
        exec(arrayOf("dumpsys", service, *args))
}

// Client side
suspend fun forceStopApp(pkg: String): Boolean = withContext(Dispatchers.IO) {
    runCatching { shellService.forceStop(pkg) == 0 }.getOrDefault(false)
}
```

---

## 8. Rekomendasi Implementasi ChibiClaw v4

### 8.1 Arsitektur Tier4

```
ChibiClaw App  ────────►  ShizukuKit (module)
                              │
                              ├── ShizukuStateFlow  (Flow<ShizukuState>)
                              ├── PermissionManager (Shizuku.requestPermission wrapper)
                              ├── UserServiceClient (bind/unbind IClawShellService)
                              └── CommandCatalog    (typed wrappers per command)
                                       │
                              ┌────────┼────────┐
                            am.kt    pm.kt   settings.kt
                            cmd.kt   dumpsys.kt input.kt
```

### 8.2 Sequence Default

1. ChibiClaw startup → init Sui jika ada → cek Shizuku ping.
2. Jika Ready dan grant valid → bind UserService → state `Tier4Active`.
3. Agent menerima task → cek capability matrix → pilih tier tertinggi yang available (default fallback ke tier3 lalu tier2).
4. Jika tier4 dipilih → call UserService method → result kembali ke agent.
5. Unbind UserService setelah idle 60 detik (kecuali daemon mode untuk power-user).

### 8.3 Telemetry & Audit Internal

Setiap tier4 invocation harus tercatat:

- Command yang dipanggil (sanitized, tanpa argument PII).
- Hasil sukses/gagal + latency.
- Trigger (agent name + user intent).
- Timestamp.

Log retention lokal 7 hari, tidak dikirim ke server kecuali user opt-in untuk crash report. Ini penting untuk **audit trail** jika ada laporan keamanan.

### 8.4 Edge Case Wajib Ditangani

- Shizuku service mati saat task berjalan → catch `RemoteException` & `IllegalStateException`, expose ke agent sebagai retryable error.
- ADB wireless terputus (user keluar Wi-Fi trusted) → state `Disconnected`; ChibiClaw downgrade ke tier3 otomatis.
- User revoke grant mid-task → handle `SecurityException`, prompt re-grant.
- Shizuku Manager update di background → binder hilang sementara; auto-rebind setelah 2 detik.
- Sui Zygisk crash → state `RootBroken`; fallback ADB-level.

---

## 9. Verifikasi yang Harus Dilakukan Sebelum Lock-In

Hal-hal yang **belum** saya yakini 100% dari riset dokumen ini dan butuh test di device target:

1. **Versi exact** `dev.rikka.shizuku:api` di Maven Central per 2026-05-13 — angka 13.6.0 dilaporkan komunitas, tapi versi AAR publish bisa lag/lead.
2. **`Shizuku.newProcess` deprecation status final** — diskusi RikkaApps/Shizuku-API #276 menyiratkan removal; cek changelog rilis terbaru.
3. **Behavior `cmd role add-role-holder` di A14/A15** untuk role `ASSISTANT`/`DIALER` — banyak laporan ditolak walau UID shell; verifikasi per ROM.
4. **Auto-start trusted-WiFi Shizuku 13.6+** — perlu test di MIUI/ColorOS karena power-management mereka agresif kill background.
5. **Latency angka** di tabel 6.1 — angka kasar dari beberapa post komunitas; benchmark sendiri di device target (Pixel 8 vs Xiaomi 14 Pro vs Galaxy S24).
6. **MIUI 7-day re-auth** — laporan TikTok/forum, belum saya temukan dokumentasi resmi Xiaomi; treat sebagai *anecdotal* sampai reproduksi.
7. **Behavior `am start` activity non-exported di A14** — beberapa report mengatakan diblok, tapi sebagian `am start -n pkg/.NonExported` masih jalan dengan flag `--allow-restricted-instant`; verifikasi.
8. **`pm install --bypass-low-target-sdk-block`** ketersediaan via shell UID 2000 — ada device yang menolak walau ADB; verifikasi.

---

## 10. Penutup

Tier4 Shizuku adalah pembeda utama ChibiClaw v4 untuk power-user Indonesia yang ingin agent benar-benar mengotomasi *system-level* tanpa root. Ekosistem matang, API Kotlin-first, dan ada library Ackpine + komunitas Tasker/IceBox yang bisa di-mining untuk pattern UX. Investasi engineer ChibiClaw fokus pada: (a) **setup wizard yang ROM-aware**, (b) **UserService AIDL yang lengkap** untuk menghindari overhead newProcess, (c) **graceful degradation** ke tier3/tier2 saat tier4 tidak tersedia, dan (d) **audit log lokal** untuk akuntabilitas.

Jangan janjikan "tier4 always available" ke user — Android 14/15 lockdown + vendor variance terlalu besar. Posisikan tier4 sebagai *advanced opt-in* dengan clear value proposition: "Aktifkan untuk auto-grant permission, force-stop dari menu konteks, install update tanpa pop-up, dan kontrol setting sistem yang biasanya butuh root."

---

## Sumber (akses 2026-05-13)

### Repo primer
- [RikkaApps/Shizuku-API (GitHub)](https://github.com/RikkaApps/Shizuku-API)
- [RikkaApps/Shizuku (GitHub)](https://github.com/RikkaApps/Shizuku)
- [RikkaApps/Sui (GitHub)](https://github.com/RikkaApps/Sui)
- [Shizuku official user manual](https://shizuku.rikka.app/guide/setup/)
- [Shizuku Introduction](https://shizuku.rikka.app/introduction/)
- [Maven Repository: dev.rikka.shizuku api](https://mvnrepository.com/artifact/dev.rikka.shizuku/api)
- [Shizuku-API rish README](https://github.com/RikkaApps/Shizuku-API/blob/master/rish/README.md)

### Diskusi & issue tracker
- [Discussion #462 — Auto-activate Shizuku Android 14+](https://github.com/RikkaApps/Shizuku/discussions/462)
- [Discussion #404 — Run ADB commands using Shizuku](https://github.com/RikkaApps/Shizuku/discussions/404)
- [Issue #276 — Petition to keep Shizuku.newProcess](https://github.com/RikkaApps/Shizuku-API/issues/276)
- [Issue #1224 — Limited adb permission OnePlus Android 13](https://github.com/RikkaApps/Shizuku/issues/1224)
- [Workaround Shizuku WiFi leave — Issue #864](https://github.com/RikkaApps/Shizuku/issues/864)
- [Hail Issue #202 — Shizuku + suspend permission denied A14](https://github.com/aistra0528/Hail/issues/202)
- [Core Concepts (DeepWiki Shizuku-API)](https://deepwiki.com/RikkaApps/Shizuku-API/2-core-concepts)

### Pattern produksi & referensi
- [timschneeb/awesome-shizuku](https://github.com/timschneeb/awesome-shizuku)
- [Ice-Box-Docs — Active with Shizuku](https://github.com/heruoxin/Ice-Box-Docs/blob/master/Active%20with%20Shizuku%20ManagerManager.md)
- [Ice Box Google Play](https://play.google.com/store/apps/details?id=com.catchingnow.icebox)
- [solrudev/Ackpine — Android package installer Kotlin](https://github.com/solrudev/Ackpine)
- [Ackpine Shizuku guide](https://ackpine.solrudev.ru/guide/shizuku/)
- [zacharee/ShizukuHackApi](https://github.com/zacharee/ShizukuHackApi)
- [Tasker Shizuku Available](https://tasker.joaoapps.com/userguide/en/help/sh_shizuku_available.html)

### Tutorial & artikel
- [Android Police — How to use Shizuku for ADB rootless mods](https://www.androidpolice.com/how-to-use-shizuku-for-adb-rootless-mods-on-any-android-device/)
- [Android Police — Shizuku freeze unwanted apps](https://www.androidpolice.com/shizuku-freeze-unwanted-system-apps-bloatware/)
- [XDA — Advanced Android Development: Elevate app permissions using Shizuku](https://www.xda-developers.com/implementing-shizuku/)
- [DroidWin — How to Set up and Run Shizuku](https://droidwin.com/how-to-set-up-and-run-shizuku-wireless-debugging-root-adb/)
- [Mobile Hacker — Shizuku Unlocking Advanced Android Capabilities](https://www.mobile-hacker.com/2025/07/14/shizuku-unlocking-advanced-android-capabilities-without-root/)
- [HackTricks — Shizuku Privileged API](https://book.hacktricks.wiki/en/mobile-pentesting/android-app-pentesting/shizuku-privileged-api.html)
- [Shizuku APK guide Android 15/16 without root](https://shizukuapk.com/use-shizuku-without-root-on-android-15-16-complete-guide/)
- [Shizuku APK — ADB Command PC](https://shizukuapk.org/shizuku-adb-command-pc/)

### ADB command references
- [Android Developers — dumpsys](https://developer.android.com/tools/dumpsys)
- [Android Developers — Notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- [Android Developers — Behavior changes Android 14](https://developer.android.com/about/versions/14/behavior-changes-all)
- [Android Developers — Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Repeato — ADB grant/revoke permissions](https://www.repeato.app/mastering-adb-commands-granting-and-revoking-app-permissions/)
- [XDA — Android 14+ Application permissions via command line](https://xdaforums.com/t/how-to-get-and-set-android-14-application-permissions-via-the-command-line-adb-root.4699038/)
- [XDA — disable system app bloatware Android no root](https://www.xda-developers.com/disable-system-app-bloatware-android/)
- [GitHub Gist — Android key events](https://gist.github.com/arjunv/2bbcca9a1a1c127749f8dcb6d36fb0bc)
- [GitHub Gist — ADB content command](https://gist.github.com/chris-piekarski/9521420)
- [Adb-shell — Content provider mastery](https://www.adb-shell.com/android/content/)
- [operando/Android-Command-Note dumpsys outputs](https://github.com/operando/Android-Command-Note/blob/master/outputs/dumpsys-l.md)
- [Kardelio/easy-dumpsys](https://github.com/Kardelio/easy-dumpsys)
- [GitHub Gist — Doze mode ADB commands](https://gist.github.com/y-polek/febff143df8dd92f4ed2ce4035c99248)

### MIUI/HyperOS specific
- [Xiaomi.eu — USB debugging Security settings Mi account](https://xiaomi.eu/community/threads/turning-on-usb-debugging-security-settings-without-mi-account.54030/)
- [MIUIROM — Enable USB Debugging Xiaomi mode HyperOS MIUI](https://miuirom.org/updates/usb-debugging)
- [Medium — INSTALL_FAILED_USER_RESTRICTED MIUI/HyperOS](https://medium.com/@resulcay/how-to-fix-the-install-failed-user-restricted-error-on-miui-hyperos-7675156e40d4)
- [scrcpy Issue #4690 — POCO F5 USB debug security](https://github.com/Genymobile/scrcpy/issues/4690)

### Kotlin coroutine/flow references
- [Android Developers — Coroutines best practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
- [Android Developers — Kotlin flows on Android](https://developer.android.com/kotlin/flow)
- [Kotlinlang — Asynchronous Flow](https://kotlinlang.org/docs/flow.html)
- [Medium — Simplifying APIs with coroutines and Flow](https://medium.com/androiddevelopers/simplifying-apis-with-coroutines-and-flow-a6fb65338765)
