# 21 — Phase 0: Foundation

**Durasi:** 2 minggu
**Tujuan:** App skeleton yang jalan: ChibiService foreground service + overlay bubble + privacy/consent infrastructure. No agent logic.

---

## Outcome

Setelah Phase 0:
- APK install di HP, ChibiService running di background dengan notif foreground
- Floating bubble overlay (collapsed dot 56dp) visible di atas semua app
- Bubble color = status (gray idle, akan jadi dinamis di Phase 1)
- Privacy notice di first launch, granular consent flow
- Vendor wizard auto-detect 11 OEM + per-OEM guidance
- SQLCipher Room + EncryptedSharedPreferences ready
- AuditLogger infrastructure (table + writer, belum dipakai)
- BOOT_COMPLETED receiver → ChibiService restart

---

## Deliverable per Minggu

### Minggu 1: Project skeleton + service

**M1.1: Gradle setup**
- `app/build.gradle.kts`: dependencies (Compose, Hilt, Room, SQLCipher, kotlinx-serialization, EncryptedSharedPrefs)
- `libs.versions.toml`: version catalog updated
- ProGuard rules basic
- AndroidManifest.xml: permission declarations + activity + service

**M1.2: Modul base**
- `core/`: Application class (Hilt), AppContainer
- `service/ChibiService.kt`: ForegroundService skeleton, lifecycle hooks
- `service/ChibiServiceStarter.kt`: BroadcastReceiver BOOT_COMPLETED
- `ui/MainActivity.kt`: entry point Compose

**M1.3: Foreground service notification**
- Build notification channel
- Foreground notification dengan ChibiClaw branding
- `foregroundServiceType="specialUse"` initially, upgrade ke `microphone` di Phase 2

### Minggu 2: Overlay + privacy + storage

**M2.1: Overlay bubble**
- `service/overlay/OverlayWindowManager.kt`: WindowManager.LayoutParams setup (TYPE_APPLICATION_OVERLAY, NOT_FOCUSABLE, NOT_TOUCH_MODAL)
- `ui/overlay/BubbleOverlay.kt`: Compose floating bubble (CCBlob component)
- Drag handler + snap-to-edge
- Tap to expand (placeholder panel kosong) → Phase 1 isi dengan chat

**M2.2: Privacy + consent**
- `ui/setup/PrivacyNoticeScreen.kt`: first launch privacy notice (full text dari [19-compliance-privacy.md](19-compliance-privacy.md))
- `ui/setup/ConsentFlowScreen.kt`: granular per-permission step (mic, accessibility, overlay, notif listener, location, contacts)
- `data/ConsentRepository.kt`: state per consent action di SecurePreferences

**M2.3: Storage infrastructure**
- `data/database/AppDatabase.kt`: Room dengan SQLCipher
- `data/database/AuditDao.kt` + AuditLogEntity (Phase 0 buat table, Phase 1+ pakai)
- `data/prefs/SecurePreferences.kt`: EncryptedSharedPreferences wrapper

**M2.4: Vendor wizard**
- `ui/setup/VendorWizardScreen.kt`: auto-detect `Build.MANUFACTURER` (Xiaomi, Oppo, Vivo, Samsung, Honor, Tecno, Realme, OnePlus, Pixel, Nothing, Infinix)
- Per-vendor guidance text + intent ke OEM settings (e.g. autostart Xiaomi, Battery Optimization OnePlus)
- State tracking: "Done" per vendor step

---

## Modul yang Dibuat di Phase 0

```
app/src/main/java/com/chibiclaw/
├── ChibiApplication.kt              # Hilt @HiltAndroidApp
├── di/
│   ├── AppModule.kt                 # Provides Database, Context
│   ├── SecurityModule.kt            # SQLCipher passphrase, encrypted prefs
│   └── ServiceModule.kt             # ChibiService dependencies
├── service/
│   ├── ChibiService.kt              # Foreground service
│   ├── ChibiServiceStarter.kt       # BOOT_COMPLETED receiver
│   └── overlay/
│       ├── OverlayWindowManager.kt
│       ├── OverlayLifecycleOwner.kt
│       └── BubbleOverlay.kt         # Compose UI
├── data/
│   ├── database/
│   │   ├── AppDatabase.kt
│   │   ├── AuditDao.kt
│   │   ├── AuditLogEntity.kt
│   │   └── migrations/Migration1to2.kt  # placeholder
│   └── prefs/
│       └── SecurePreferences.kt
├── compliance/
│   └── AuditLogger.kt               # infrastructure, no usage yet
├── ui/
│   ├── MainActivity.kt
│   ├── theme/ChibiClawTheme.kt
│   └── setup/
│       ├── PrivacyNoticeScreen.kt
│       ├── ConsentFlowScreen.kt
│       └── VendorWizardScreen.kt
└── util/
    └── VendorDetector.kt
```

---

## Dependencies

```kotlin
// build.gradle.kts (app)
dependencies {
    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    
    // Room + SQLCipher
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)
    
    // EncryptedSharedPreferences
    implementation(libs.androidx.security.crypto)
    
    // kotlinx serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    
    // WorkManager (Phase 0 stub, Phase 6 used)
    implementation(libs.androidx.work.runtime)
    
    // Logging (Timber atau Logback)
    implementation(libs.timber)
}
```

---

## AndroidManifest (Phase 0)

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Phase 0 permissions -->
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <!-- Phase 1+: tambah mic, accessibility, dll -->
    
    <application
        android:name=".ChibiApplication"
        android:label="ChibiClaw"
        android:icon="@mipmap/ic_launcher">
        
        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        
        <service
            android:name=".service.ChibiService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="ai_assistant" />
        </service>
        
        <receiver
            android:name=".service.ChibiServiceStarter"
            android:exported="false">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>
```

Phase 1+ wajib upgrade `foregroundServiceType` ke `microphone|specialUse|mediaPlayback` saat add mic + audio.

---

## Setup Wizard Flow

```
First launch (no setup_complete in SecurePreferences):
   ↓
PrivacyNoticeScreen
   - Display full privacy notice
   - [Tidak setuju] → exit app
   - [Setuju, mulai] → store consent_privacy_notice=true, next
   ↓
ConsentFlowScreen step 1: SYSTEM_ALERT_WINDOW
   - Explain why needed
   - [Beri akses] → ACTION_MANAGE_OVERLAY_PERMISSION
   - [Skip] → consent_overlay=false (overlay won't show)
   ↓
ConsentFlowScreen step 2: POST_NOTIFICATIONS
   - Standard runtime permission request (Android 13+)
   ↓
VendorWizardScreen
   - Auto-detect manufacturer
   - Per-OEM guidance (clickable intent ke settings)
   - [Done] → consent_vendor_setup=true
   ↓
PhaseCompleteScreen
   - "Phase 0 setup selesai. ChibiClaw ready (more features di Phase 1+)"
   - [Mulai] → store setup_complete=true → start ChibiService → MainActivity dismiss
```

Subsequent launch: kalau `setup_complete=true`, skip wizard, MainActivity show settings dashboard (or fallback launcher).

---

## Verifikasi Phase 0 (Manual Test, Optional)

Lendra opt-in test setelah Phase 0 selesai (atau bisa skip ke Phase 9 mass test sesuai ADR-011):

1. Install APK debug
2. Buka, lewati privacy notice + consent
3. Cek overlay bubble muncul di home screen
4. Drag bubble — snap to edge
5. Lock HP, unlock — bubble masih ada
6. Reboot HP, tunggu 1 menit — ChibiService restart, bubble muncul lagi
7. Buka Settings → Apps → ChibiClaw → cek battery optimization "No restrictions" sesuai vendor wizard

Kalau test skip, just continue ke Phase 1.

---

## Risk

| Risk | Mitigasi |
|------|----------|
| Overlay permission denied | Setup wizard guide user re-grant; app graceful kalau overlay off (notif only mode) |
| Vendor kill ChibiService mid-boot | BOOT_COMPLETED receiver re-launch; service "respond to vendor kill" via JobScheduler retry |
| SQLCipher init slow | Async init di Application onCreate, blocking via Hilt Singleton |
| Compose overlay TYPE_APPLICATION_OVERLAY tidak compatible Android 17+ | Test Android 16 first, prepare fallback Bubble API kalau perlu |

---

## Definition of Done

- [ ] App install dari APK debug (manual via adb)
- [ ] First launch → privacy notice + consent flow tampil
- [ ] Setelah setup_complete, ChibiService start, bubble muncul
- [ ] Bubble drag + snap-to-edge works
- [ ] Reboot → ChibiService auto-restart
- [ ] Tidak ada crash di logcat selama 5 menit idle
- [ ] AuditLogger table created di Room (verify via DB inspector kalau perlu)

Manual test optional. Bug fixing di Phase 9.

---

## Next: [22-phase-1-agent-core.md](22-phase-1-agent-core.md)
