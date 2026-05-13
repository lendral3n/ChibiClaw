# Research 07 — Floating Overlay App Android (Production-Grade 2025-2026)

> **Konteks**: Riset untuk ChibiClaw v4 (Android AI assistant) + VRM Assistant Android (floating VRM overlay app, Unity 6.2 UaaL + Kotlin/Compose).
> **Penulis**: research agent (Claude Opus 4.7)
> **Tanggal dibuat**: 2026-05-13
> **Tanggal akses sumber**: 2026-05-13
> **Status**: Draft 1 — pre-implementation reference
> **Target**: Single ChibiService bind Compose overlay + Unity VRM renderer dengan mic concurrent listening.

---

## Executive Summary

Floating overlay app Android per Mei 2026 sudah jauh dari era "Facebook Chat Heads". Pola produksi modern wajib menggabungkan **tujuh pilar** sekaligus:

1. **Foreground service dengan `foregroundServiceType` ganda** (`microphone|specialUse|mediaPlayback`) — wajib di Android 14+ karena `MissingForegroundServiceTypeException` di-throw saat `startForeground()` tanpa deklarasi tipe yang sah.
2. **Compose embedded di custom WindowManager** dengan `OverlayLifecycleOwner` yang implement `LifecycleOwner + ViewModelStoreOwner + SavedStateRegistryOwner` secara manual — `ViewTreeLifecycleOwner` di-set ke root view sebelum `addView()`.
3. **Vendor-survival strategy** lewat Don't Kill My App registry (Xiaomi/Oppo/Vivo/Realme/Honor/Samsung) dengan onboarding wizard yang memandu user buka setting masing-masing OEM — **tidak ada API resmi** untuk bypass restriksi mereka.
4. **Mic concurrent + privacy indicator** Android 12+ wajib transparan ke user: indicator hijau muncul setiap mic aktif, `AudioFocusRequest` dengan `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` untuk koeksistensi dengan asisten lain.
5. **BOOT_COMPLETED tidak boleh start mic FGS** di Android 14+ — pakai `WorkManager` deferred + user-initiated re-entry, atau pakai `specialUse` type sebagai bridge.
6. **`FLAG_NOT_TOUCH_MODAL | NOT_FOCUSABLE`** untuk pass-through touch ke app di bawah. Android 12+ block untuk overlay yang obscure dengan opacity >0.8 saat ada sensitive transaction.
7. **Bubbles API (Android 11+)** harus dipertimbangkan dulu sebelum custom `SYSTEM_ALERT_WINDOW`. Bubbles API resmi, hidup di system, dan tidak butuh permission khusus — tapi terbatas pada conversation notification, jadi untuk asisten VRM yang persistent wajib tetap pakai SAW.

**Update wajib ke ChibiClaw v4 / VRM Assistant plan**:

- Architecture **ChibiService = ForegroundService satu instance** yang host kedua-duanya: ComposeView overlay (bubble + chat panel) dan Unity UaaL `UnityPlayer` di window WindowManager terpisah. Audio capture dishare lewat single `AudioRecord` pipe dari service (hindari double-acquire mic).
- Memory budget: overlay UI <50 MB (Compose + bubble + chat), Unity VRM <300 MB (model loaded), total target <400 MB stabil. Pakai `ActivityManager.MemoryInfo` listener untuk auto-unload VRM saat `lowMemory=true`.
- Reboot recovery **tidak bisa** auto-restart mic FGS — wajib user-initiated dengan persistent notification "Tap to resume ChibiClaw" setelah boot. Atau pakai `specialUse` (lebih longgar restriksi).
- Wake word listening **harus duty-cycle** (e.g. 100ms scan / 50ms idle) untuk hemat baterai di vendor restricted ROM. Porcupine + microWakeWord dua-duanya support pattern ini.

---

## 1. Foundation Pattern — Anatomi Floating Overlay Production

### 1.1 Stack arsitektur

```
┌─────────────────────────────────────────────────────────┐
│ ChibiService (ForegroundService, type=microphone|       │
│                                       specialUse|       │
│                                       mediaPlayback)    │
│  ├── PersistentNotification (IMPORTANCE_LOW, ongoing)   │
│  ├── OverlayLifecycleOwner                              │
│  │   ├── LifecycleRegistry                              │
│  │   ├── ViewModelStore                                 │
│  │   └── SavedStateRegistryController                   │
│  ├── BubbleOverlayWindow (WindowManager.addView)        │
│  │   └── ComposeView (bubble icon, drag-snap)           │
│  ├── ExpandedPanelWindow (WindowManager.addView)        │
│  │   └── ComposeView (chat UI, VRM placeholder slot)    │
│  ├── UnityVrmWindow (WindowManager.addView, optional)   │
│  │   └── UnityPlayer (SurfaceView, transparent)         │
│  ├── WakeWordEngine (Porcupine/microWakeWord)           │
│  │   └── AudioRecord shared via Channel<ByteArray>      │
│  └── ConcurrentMicCoordinator (AudioFocusRequest)       │
└─────────────────────────────────────────────────────────┘
```

### 1.2 Manifest minimum (Android 14+)

```xml
<!-- Permission -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
                 tools:ignore="BatteryLife" />

<!-- Service -->
<service
    android:name=".overlay.ChibiService"
    android:foregroundServiceType="microphone|specialUse|mediaPlayback"
    android:exported="false">
  <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
            android:value="persistent_assistant_overlay" />
</service>

<!-- Boot receiver -->
<receiver android:name=".boot.BootReceiver"
          android:exported="true"
          android:permission="android.permission.RECEIVE_BOOT_COMPLETED">
  <intent-filter>
    <action android:name="android.intent.action.BOOT_COMPLETED" />
    <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
  </intent-filter>
</receiver>
```

**Catatan kritis**:
- `specialUse` wajib di-justify di Play Console (Form "Foreground Service Use") dengan deskripsi persistent assistant. Hanya `mediaPlayback` dan `mediaProjection` yang otomatis lulus review.
- Sejak Android 14, `MissingForegroundServiceTypeException` dilempar saat `startForeground()` tanpa tipe — wajib `startForeground(id, notif, FOREGROUND_SERVICE_TYPE_MICROPHONE or FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`.

### 1.3 WindowManager.LayoutParams baseline

```kotlin
val bubbleParams = WindowManager.LayoutParams(
    WRAP_CONTENT, WRAP_CONTENT,
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else
        @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
    PixelFormat.TRANSLUCENT
).apply {
    gravity = Gravity.TOP or Gravity.START
    x = lastSavedX
    y = lastSavedY
    // Android 12+ wajib softInputMode untuk IME interaction
    softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
}
```

**Bedakan dua window**:
- **Bubble** (collapsed): `FLAG_NOT_FOCUSABLE | NOT_TOUCH_MODAL` — touch di luar bubble lewat ke app di bawah.
- **Expanded panel**: butuh focusable kalau ada `TextField` (keyboard input). Toggle `FLAG_NOT_FOCUSABLE` off saat panel terbuka via `updateViewLayout()`.

---

## 2. Compose Integration di Overlay (Tanpa Activity)

### 2.1 Masalah `ViewTreeLifecycleOwner not found`

`ComposeView` butuh tiga owner: `LifecycleOwner`, `ViewModelStoreOwner`, `SavedStateRegistryOwner`. Tanpa Activity host, kita harus inject manual.

### 2.2 Implementasi `OverlayLifecycleOwner`

```kotlin
class OverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController =
        SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry =
        savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onStart() { lifecycleRegistry.currentState = Lifecycle.State.STARTED }
    fun onResume() { lifecycleRegistry.currentState = Lifecycle.State.RESUMED }
    fun onPause()  { lifecycleRegistry.currentState = Lifecycle.State.STARTED }
    fun onStop()   { lifecycleRegistry.currentState = Lifecycle.State.CREATED }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }

    fun attachToDecorView(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }
}
```

### 2.3 Sambungkan ke ComposeView

```kotlin
class ChibiService : Service() {
    private lateinit var owner: OverlayLifecycleOwner
    private var bubbleRoot: FrameLayout? = null

    override fun onCreate() {
        super.onCreate()
        owner = OverlayLifecycleOwner().apply { onCreate() }
        startForegroundInternal()
    }

    private fun showBubble() {
        val frame = FrameLayout(this).also { bubbleRoot = it }
        owner.attachToDecorView(frame)
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
            )
            setContent { ChibiBubbleUi(viewModel = bubbleViewModel) }
        }
        frame.addView(composeView)
        windowManager.addView(frame, bubbleParams)
        owner.onStart()
        owner.onResume()
    }

    override fun onDestroy() {
        owner.onPause(); owner.onStop()
        bubbleRoot?.let { runCatching { windowManager.removeView(it) } }
        owner.onDestroy()
        super.onDestroy()
    }
}
```

### 2.4 `ViewCompositionStrategy` — pilih yang tepat

| Strategy | Use case di overlay |
|---|---|
| `DisposeOnDetachedFromWindow` | Default — bagus untuk bubble yang stay attached |
| `DisposeOnDetachedFromWindowOrReleasedFromPool` | **Recommended** untuk overlay yang sering add/remove |
| `DisposeOnLifecycleDestroyed(owner)` | Saat owner di-tear-down bersama service `onDestroy` |
| `DisposeOnViewTreeLifecycleDestroyed` | Sub-window yang lifecycle-nya beda dari root |

### 2.5 Theme & dark mode propagation

```kotlin
setContent {
    val config by configuration.collectAsState() // System config dari Service
    val isDark = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                 Configuration.UI_MODE_NIGHT_YES
    ChibiTheme(darkTheme = isDark) {
        ChibiBubble()
    }
}
```

Service tidak auto-update `Resources.getSystem().configuration`. Subscribe `ComponentCallbacks2.onConfigurationChanged` di service, emit ke `MutableStateFlow<Configuration>`.

---

## 3. Touch Handling & Gesture

### 3.1 Drag + snap-to-edge minimal

```kotlin
@Composable
fun DraggableBubble(onPositionChange: (IntOffset) -> Unit) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var screenWidthPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .onGloballyPositioned {
                screenWidthPx = (it.parentLayoutCoordinates?.size?.width ?: 0)
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { _, drag ->
                        scope.launch {
                            offsetX.snapTo(offsetX.value + drag.x)
                            offsetY.snapTo(offsetY.value + drag.y)
                            onPositionChange(IntOffset(
                                offsetX.value.toInt(), offsetY.value.toInt()
                            ))
                        }
                    },
                    onDragEnd = {
                        // Snap to nearest edge
                        scope.launch {
                            val target = if (offsetX.value < screenWidthPx / 2)
                                0f else (screenWidthPx - bubbleSizePx).toFloat()
                            offsetX.animateTo(
                                target,
                                spring(Spring.DampingRatioMediumBouncy)
                            )
                        }
                    }
                )
            }
    ) { /* bubble icon */ }
}
```

### 3.2 Touch pass-through nuance (`FLAG_NOT_TOUCH_MODAL`)

| Flag combo | Behavior touch outside bubble |
|---|---|
| `NOT_FOCUSABLE` only | Touch outside **diblokir** — sample crash di game/full-screen app |
| `NOT_FOCUSABLE \| NOT_TOUCH_MODAL` | **Pass-through** ke app di bawah — pola standard |
| `NOT_FOCUSABLE \| NOT_TOUCH_MODAL \| LAYOUT_NO_LIMITS` | Pass-through + bisa render di luar bound layar (cutout/notch) |
| `NOT_TOUCHABLE` | Display-only, **tidak bisa ditap** — untuk indicator saja |

**Android 12+ obscuring rule**: Kalau bubble opacity total >0.8 (default `MAX_OBSCURING_OPACITY`) dan ada `SYSTEM_ALERT_WINDOW` lain di atas form input sensitive (password, payment), system **drop touch**. Solusi: pastikan bubble alpha background <0.8 atau pakai PNG dengan transparency.

### 3.3 Konflik gesture navigation Android 10+

Edge swipe (back/home) area di 24-32 dp dari kiri/kanan. Kalau bubble mendarat di area itu, swipe back user di-intercept bubble — frustrasi besar.

```kotlin
// Di view bubble setelah attach
val exclusionRects = listOf(
    Rect(left = bubbleLeft, top = bubbleTop,
         right = bubbleRight, bottom = bubbleBottom)
)
ViewCompat.setSystemGestureExclusionRects(rootView, exclusionRects)
```

Limit: 200 dp tinggi total exclusion per side. Cukup untuk satu bubble, tapi jangan abuse.

### 3.4 Tap-vs-drag disambiguation

`detectDragGestures` di Compose punya threshold default 14 dp. Untuk bubble, naikkan jadi 8 dp supaya tap responsif:

```kotlin
.pointerInput(Unit) {
    awaitPointerEventScope {
        val down = awaitFirstDown()
        val drag = awaitTouchSlopOrCancellation(down.id) { _, _ -> }
        if (drag == null) {
            // Tap
            onTap()
        } else {
            // Drag started
            ...
        }
    }
}
```

---

## 4. Android Version Compatibility Matrix

| Android | API | Hal kritis untuk overlay | Solusi |
|---|---|---|---|
| **10** (Q) | 29 | Background activity start dilarang — `PendingIntent` ke Activity dari service di-block | Full-screen intent + `USE_FULL_SCREEN_INTENT` permission, atau cukup expand panel dalam window manager (no Activity) |
| **11** (R) | 30 | Package visibility: `queryIntentActivities` need `<queries>` manifest. `SYSTEM_ALERT_WINDOW` masih granted via Settings intent | Tambah `<queries>` block, intent `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` |
| **12** (S) | 31 | Mic/cam **privacy indicator** (dot hijau). Overlay tidak boleh obscure sensitive touch (opacity >0.8 block). `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` untuk alarm | Edukasi user di onboarding ada dot hijau saat wake word listen |
| **13** (T) | 33 | `POST_NOTIFICATIONS` runtime permission. `RECEIVER_EXPORTED`/`NOT_EXPORTED` flag wajib untuk context-registered receiver | Request notif permission saat onboarding sebelum start FGS |
| **14** (U) | 34 | `foregroundServiceType` wajib di manifest **dan** di `startForeground()` flag. `MissingForegroundServiceTypeException`. Mic FGS tidak boleh start dari BOOT_COMPLETED. `RECEIVER_NOT_EXPORTED` mandatory | Multi-type service: `microphone\|specialUse\|mediaPlayback`. Boot recovery pakai `specialUse` then user-tap upgrade ke mic |
| **15** (V) | 35 | Edge-to-edge default. `requestAudioFocus` hanya bisa dari top app atau audio FGS. Media projection tidak boleh BOOT_COMPLETED start | Pakai `AudioFocusRequest` baru dari mic FGS. Edge-to-edge: pastikan bubble respect `WindowInsets` |
| **16** (16) | 36 | FGS jobs dipanggil dari WorkManager kena job quota. Health FGS granular permission. `shortService` makin restricted (3 menit max) | Hindari spawn child WorkManager dari FGS untuk long task — lakukan langsung di service |
| **17** (predicted) | 37 | (per riset 01) Accessibility `isAccessibilityTool` enforcement. Overlay listener app non-tool **kemungkinan kena auto-disable** sama seperti A11y | Argument kuat untuk dual-mode: SAW overlay (asisten standalone) + optional A11y if user grants |

### 4.1 Compile-time vs runtime

```kotlin
// Di service onStartCommand
val fgsTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
} else 0

if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    startForeground(NOTIF_ID, buildNotification(), fgsTypes)
} else {
    startForeground(NOTIF_ID, buildNotification())
}
```

---

## 5. Mic Access Concurrent — Wake Word Persistent

### 5.1 Three-tier mic strategy

| Tier | Mode | Battery | Latency to wake | Use case |
|---|---|---|---|---|
| **Tier A** | Continuous mic (always `AudioRecord.read`) | 8-12%/hari | ~50ms | Premium mode, charger plugged |
| **Tier B** | Duty-cycle (100ms on / 50ms off) | 3-5%/hari | ~200ms | **Default**, batt >30% |
| **Tier C** | Voice trigger HW (low-power DSP) | <1%/hari | ~300ms | Pixel/Snapdragon hexagon DSP saja |
| **Tier D** | Push-to-talk only | 0% | manual | Batt <15% or user toggle |

Porcupine dan microWakeWord dua-duanya support Tier B. Untuk Tier C butuh `SoundTrigger` API yang vendor-locked dan jarang exposed ke 3rd party app.

### 5.2 Single AudioRecord pipe pattern

Anti-pattern: dua engine (wake word + STT) buka `AudioRecord` paralel — pasti tabrakan `AudioFocus`.

Pattern benar:

```kotlin
class AudioPipeline {
    private val audioRecord = AudioRecord.Builder()
        .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        .setAudioFormat(AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(16_000)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build())
        .setBufferSizeInBytes(bufferSize)
        .build()

    private val frameBus = MutableSharedFlow<ShortArray>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    suspend fun start() = withContext(Dispatchers.IO) {
        audioRecord.startRecording()
        val buf = ShortArray(512)
        while (currentCoroutineContext().isActive) {
            val n = audioRecord.read(buf, 0, buf.size)
            if (n > 0) frameBus.emit(buf.copyOf(n))
        }
    }

    fun subscribe(): Flow<ShortArray> = frameBus.asSharedFlow()
}

// Wake word consumer
audioPipeline.subscribe().collect { frame ->
    if (porcupine.process(frame) >= 0) onWakeWord()
}

// STT consumer (after wake word, separate scope)
audioPipeline.subscribe().take(durationFrames).collect { frame ->
    whisperEngine.feed(frame)
}
```

### 5.3 AudioFocus dengan Google Assistant koeksistensi

```kotlin
val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
    .setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build())
    .setOnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> pauseListening()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> mute()
            AudioManager.AUDIOFOCUS_GAIN -> resumeListening()
        }
    }.build()
audioManager.requestAudioFocus(focusReq)
```

`USAGE_ASSISTANT` di Android 11+ memberi tahu system bahwa kita asisten — Google Assistant akan duck atau pause saat kita aktif (dan sebaliknya).

### 5.4 Android 15 audio focus restriction

Sejak Android 15, **hanya top app atau audio-related FGS** yang boleh `requestAudioFocus`. Karena kita FGS tipe `microphone`, OK. Tapi pastikan jangan request dari background non-FGS context — di-deny.

### 5.5 Mic indicator UX

Privacy indicator (dot hijau pojok kanan atas) muncul **selama mic aktif**. Bahkan saat duty-cycle 100ms on / 50ms off, indicator tetap show karena <1 detik gap dianggap continuous.

**Onboarding wajib jelaskan**:
- "Dot hijau itu ChibiClaw lagi dengerin 'Hey Fuu'. Suaramu tidak direkam atau dikirim ke cloud."
- Toggle "Mute Mic" di expanded panel untuk Tier D fallback.

---

## 6. Vendor Quirks — The Battlefield

Sumber: [dontkillmyapp.com](https://dontkillmyapp.com), akses 2026-05-13.

### 6.1 Skor user-action vendor (Death Score)

| Vendor | OS | Death Score | Catatan |
|---|---|---|---|
| **Xiaomi/Redmi/POCO** | HyperOS / MIUI | **Severe** | Autostart denied by default, Battery saver "Restricted" force-kill, X-swipe close kill FGS, MIUI Optimization toggle |
| **Oppo** | ColorOS | **High** | Startup manager separate dari battery saver, "Background app freeze" agresif |
| **Realme** | Realme UI | **High** | Inherits ColorOS, sama agresifnya |
| **OnePlus** | OxygenOS | **High** | Adaptive battery, "Optimize battery use" excludes still bisa di-kill di low memory |
| **Vivo** | OriginOS / FuntouchOS | **High** | "High background CPU usage" permission tersembunyi, autostart manual |
| **iQOO** | OriginOS (gaming) | **High** | Sama Vivo + ultra game mode kill background |
| **Honor** | MagicOS | **High** | Inherits ex-Huawei EMUI agresivitas, "Protected apps" list |
| **Samsung** | OneUI 7 | **Medium-High** | "Put unused apps to sleep" auto 3-16 hari, Adaptive battery, "Sleeping apps" list |
| **Tecno/Infinix** | HiOS / XOS | **Medium** | Power Marshall, autostart manager mirip MIUI |
| **Huawei** | EMUI/HarmonyOS | **Severe** | Protected apps mandatory, App Launch manual, no Play Store on newer |
| **Nothing** | Nothing OS | **Low** | Mostly AOSP-stock + Glyph, jarang kill |
| **Google Pixel** | Stock Android | **Lowest** | Doze + App Standby buckets predictable, FGS aman |

### 6.2 Vendor-specific actions detail

#### Xiaomi (MIUI/HyperOS)

User action wizard:
1. Settings → Apps → Manage Apps → ChibiClaw → **Autostart ON**
2. Settings → Apps → Manage Apps → ChibiClaw → Battery saver → **No restrictions**
3. Settings → Apps → Manage Apps → ChibiClaw → Other permissions → **Show on Lock screen, Display pop-up windows while running in the background**
4. Recent apps → Long-press ChibiClaw card → **Lock** (pin icon)
5. Settings → Battery & performance → Battery saver → ChibiClaw → **No restrictions**
6. (HyperOS 2) Settings → Apps → Permissions → **Special permissions** → **Display over other apps** → ChibiClaw ON

Intent untuk shortcut:
```kotlin
val miuiAutostart = Intent().setComponent(ComponentName(
    "com.miui.securitycenter",
    "com.miui.permcenter.autostart.AutoStartManagementActivity"
))
```

#### Oppo / Realme (ColorOS / Realme UI)

1. Settings → Battery → **App energy saver** → ChibiClaw → **Allow background activity** ON, **Allow auto-launch** ON
2. Settings → Privacy permissions → **Startup manager** → ChibiClaw ON
3. Settings → App management → ChibiClaw → **Allow background activity** ON
4. Recent apps → lock the card

Intent shortcut:
```kotlin
val oppoAutostart = Intent().setComponent(ComponentName(
    "com.coloros.safecenter",
    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
))
```

#### Vivo / iQOO

1. Settings → Battery → **Background power consumption management** → ChibiClaw → **Allow**
2. Settings → More settings → Applications → **Autostart** → ChibiClaw ON
3. Settings → More settings → Permission manager → **High background CPU usage** → ChibiClaw allow
4. iManager app (jika ada) → App manager → Autostart manager

#### Samsung OneUI

1. Settings → Device care → Battery → **Background usage limits** → **Sleeping apps** — pastikan ChibiClaw **tidak** di list
2. Settings → Apps → ChibiClaw → Battery → **Unrestricted**
3. Settings → Apps → ChibiClaw → Battery → **Allow background activity** ON
4. Settings → Battery → More battery settings → **Adaptive battery** OFF (atau exclude ChibiClaw)
5. Recent apps → Long-press → **Keep open** (lock)

Intent untuk Samsung battery settings:
```kotlin
val samsungBattery = Intent("com.samsung.android.sm.ACTION_BATTERY")
```

#### Honor MagicOS

1. Settings → Battery → **App launch** → ChibiClaw → **Manage manually** → semua toggle ON (Auto-launch, Secondary launch, Run in background)
2. Settings → Apps → ChibiClaw → Battery → **Power consumption details** → **Allow background activity** ON

### 6.3 Onboarding wizard pattern

```kotlin
// Detect manufacturer dan generate per-step instruction
object VendorOnboarding {
    fun stepsFor(): List<OnboardingStep> = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" -> xiaomiSteps
        "oppo", "realme" -> oppoSteps
        "vivo", "iqoo" -> vivoSteps
        "samsung" -> samsungSteps
        "honor" -> honorSteps
        "huawei" -> huaweiSteps
        "oneplus" -> onePlusSteps
        else -> genericSteps
    }
}

data class OnboardingStep(
    val titleId: Int,
    val descriptionId: Int,
    val intentSupplier: () -> Intent?,
    val screenshotResId: Int?
)
```

Best UX: tampilkan **screenshot setting page vendor + arrow ke toggle yang relevan**. Library [DontKillMyApp app](https://play.google.com/store/apps/details?id=com.urbandroid.dontkillmyapp) bisa jadi referensi.

### 6.4 Battery optimization request (whitelist Doze)

```kotlin
@SuppressLint("BatteryLife")
fun requestBatteryOptimizationExemption(activity: Activity) {
    val pm = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (!pm.isIgnoringBatteryOptimizations(activity.packageName)) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${activity.packageName}"))
        activity.startActivity(intent)
    }
}
```

**Caveat Play Store**: Permission `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` masuk **policy-restricted list**. App harus masuk kategori yang dibolehkan (alarm clock, accessibility, fitness tracker, communication, etc). Assistant overlay generally bisa argue under "communication" atau "accessibility-adjacent". Siapkan justifikasi di Play Console form.

---

## 7. Production-Proven Apps — Studi Kasus

### 7.1 Floating Bubble references

| App | Pattern | Public source |
|---|---|---|
| **Facebook Messenger Chat Heads** (legacy) | SAW + foreground service, removed di Android 11 (in-app messaging shift ke Bubbles API) | Closed source |
| **WhatsApp Floating Video Call** | PiP API untuk video, SAW untuk audio-only call indicator | Closed source |
| **Bobble Keyboard / Hike Sticker** | SAW + IME extension, complex pass-through | Closed source |
| **Samsung Bixby Voice Bar** | Persistent FGS + SAW dengan voice indicator | Closed source, leak di XDA |
| **Greenify / Brevent** | SAW untuk hibernate trigger, accessibility complement | Closed (Greenify) / Open (Brevent) |
| **Talon** (Twitter) | SAW dengan PiP fallback untuk new tweet quick reply | Open-source historical |
| **Tasker AutoTools** | SAW + Tasker plugin pattern | Closed |
| **Speech Notes** | SAW bubble untuk dictation start | Open-source |
| **Pico VR Mobile Companion** | Unity UaaL + Compose + SAW floating preview | Closed |

### 7.2 OSS yang produksi-grade

#### `dofire/Floating-Bubble-View` (Apache 2.0, 260+ stars)

URL: https://github.com/dofire/Floating-Bubble-View

Features:
- XML + Compose dual support via `bubbleCompose { @Composable }` lambda
- `BubbleBuilder` + `ExpandedBubbleBuilder` config DSL
- Snap-to-edge dengan dynamic close-mode (close icon mengikuti bubble)
- min SDK 21
- Last release Maret 2024

Strength: production-tested, dual API, Compose 1.6 compatible.

Weakness: tidak handle vendor wake-up issues, gak ada audio integration baked-in.

#### `luiisca/floating-views` (MIT, 20+ stars, baru)

URL: https://github.com/luiisca/floating-views

Features:
- 100% declarative Compose API
- `MainFloatConfig` + `ExpandedFloatConfig` + `CloseFloatConfig`
- `FloatServiceStateManager.isServiceRunning` flow untuk observe
- Built-in runtime permission flow
- Mendukung Android 14+ `FOREGROUND_SERVICE_SPECIAL_USE`

Strength: clean modern API, MIT permissive, mendukung multi-float.

Weakness: 1.0.5 (Sep 2024) — stale ~8 bulan, butuh fork atau audit untuk Android 16+ compat. Star count masih kecil = community kecil.

#### `BijoySingh/Floating-Bubble-Library-Android` (Apache 2.0)

URL: https://github.com/BijoySingh/Floating-Bubble-Library-Android

Features simpler, older (mostly XML), kurang relevan untuk Compose.

### 7.3 Bubbles API (Android 11+) — bukan ganti SAW

URL: https://developer.android.com/develop/ui/compose/notifications/bubbles

```kotlin
val bubbleIntent = PendingIntent.getActivity(/* expanded activity */)
val bubble = NotificationCompat.BubbleMetadata.Builder(bubbleIntent, icon)
    .setDesiredHeight(600)
    .setAutoExpandBubble(false)
    .setSuppressNotification(true)
    .build()

val notif = NotificationCompat.Builder(this, CONVERSATION_CHANNEL)
    .setBubbleMetadata(bubble)
    .setShortcutId(shortcutId) // wajib conversation shortcut
    .build()
```

Pros:
- System-managed (immune dari vendor kill mostly, karena di-shepherd notif manager)
- No `SYSTEM_ALERT_WINDOW` permission
- User toggle from notif itself (UX clean)

Cons:
- **Conversation-only**: harus link ke `ShortcutInfoCompat` dengan `setLongLived(true)` dan `setIsConversation(true)`
- Bubble expanded launches **Activity**, bukan Service overlay — tidak cocok untuk Unity UaaL inline rendering
- No drag pass-through ke app di bawah (bubble di-paint oleh system, layout fixed)

**Verdict untuk ChibiClaw + VRM Assistant**: Bubbles API **tidak cukup** untuk use case kita karena butuh inline Unity rendering. Tapi bisa **complement** sebagai "lite mode" yang user pilih kalau gak mau granting SAW.

---

## 8. Reboot Recovery & Death Notification

### 8.1 Boot recovery limitations

| Android | BOOT_COMPLETED restriction |
|---|---|
| 11- | Bisa start `microphone` FGS langsung |
| 12 | Foreground service start dari background di-block dengan `ForegroundServiceStartNotAllowedException`. BOOT_COMPLETED exempt. |
| 13 | Sama 12 |
| **14+** | **`microphone` FGS dari BOOT_COMPLETED dilarang** — `ForegroundServiceStartNotAllowedException` |
| **15+** | Tambah `camera`, `mediaProjection`, `phoneCall`, `dataSync` ke list larangan dari BOOT_COMPLETED |
| 16+ | Sama 15 + tighten short_service quota |

### 8.2 Recovery pattern Android 14+

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        // Tidak boleh start mic FGS langsung. Start specialUse FGS dulu (idle).
        val serviceIntent = Intent(ctx, ChibiBootService::class.java)
            .putExtra("mode", "boot_recovery")
        ContextCompat.startForegroundService(ctx, serviceIntent)

        // ChibiBootService akan:
        // 1. startForeground(id, notif, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        // 2. Show notification "ChibiClaw siap. Tap untuk aktifkan mic."
        // 3. Saat user tap notif → Activity → request RECORD_AUDIO confirm → 
        //    upgrade service ke type=microphone via Service.notifyForegroundServiceType()
    }
}
```

Atau pakai **direct user-initiated launch** via notification full-screen intent:

```kotlin
val notif = NotificationCompat.Builder(this, CHANNEL_ID)
    .setSmallIcon(R.drawable.ic_bubble)
    .setContentTitle("ChibiClaw siap aktif")
    .setContentText("Tap untuk lanjutkan listening")
    .setPriority(NotificationCompat.PRIORITY_HIGH)
    .setCategory(NotificationCompat.CATEGORY_SERVICE)
    .setContentIntent(activateMicPendingIntent)
    .setOngoing(true)
    .build()
```

### 8.3 Service death detection

Service tidak punya `onKilled()` callback dari vendor force-stop. Cara detect:

1. **Heartbeat pattern**: service tulis timestamp ke `DataStore` tiap 30 detik. App main activity check kalau gap >1 menit, prompt re-enable.
2. **AlarmManager exact alarm**: schedule alarm ke `RTC_WAKEUP` 5 menit dari sekarang dengan PI ke service. Kalau service mati, alarm bangunkan dan service bisa restart.
3. **JobScheduler periodic** (15 menit min): untuk monitoring, tapi tidak guaranteed di vendor restricted ROM (Xiaomi delay sampai jam-an).

### 8.4 START_STICKY vs START_REDELIVER_INTENT

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // ...
    return START_STICKY // Sistem coba restart kalau di-kill
}
```

Caveat: `START_STICKY` honored di stock Android. Di Xiaomi/Oppo restricted, service yang di-kill manual tidak akan auto-restart sama sekali — user **harus** open app or tap notif.

---

## 9. Concurrent Mic dengan Asisten Lain & Voice Recognition System

### 9.1 Cohabitation dengan Google Assistant

Google Assistant punya:
- Always-on hotword "Hey Google" yang berjalan di HW low-power (Pixel Tensor / Snapdragon hexagon)
- Pakai `SoundTrigger` API privileged (system app), tidak conflict dengan `AudioRecord` 3rd party
- **Tapi**: saat user said "Hey Google", Assistant `requestAudioFocus(GAIN)` — kita kena `AUDIOFOCUS_LOSS_TRANSIENT`, harus pause wake word detection

### 9.2 Detect Assistant active

```kotlin
val sttListener = object : AudioManager.OnAudioFocusChangeListener {
    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Possibly Google Assistant active. Pause wake word.
                audioPipeline.pause()
            }
            AUDIOFOCUS_GAIN -> {
                audioPipeline.resume()
            }
        }
    }
}
```

### 9.3 RoleManager — declare ourselves as Assistant

Android 10+ `ROLE_ASSISTANT`:

```kotlin
val rm = getSystemService<RoleManager>()
if (rm?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true &&
    !rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
    val intent = rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
    activity.startActivityForResult(intent, REQ_ROLE_ASSISTANT)
}
```

Kalau user grant, kita jadi default assistant — long-press home button trigger ChibiClaw, bukan Google. Co-existence: kalau Google Assistant **tidak** held role, dia tidak compete untuk audio focus pada hotword event.

### 9.4 Honor Mic Mute hardware switch

Beberapa device (Pixel 6+, Samsung S24+) punya hardware mic mute toggle. `AudioManager.isMicrophoneMute` return true. **Tetap** start FGS-microphone — tapi `AudioRecord.read()` return frame nol. Show indicator "Mic muted by user" di overlay.

---

## 10. Compose UI Memory & Performance

### 10.1 Anti-leak checklist

- Tidak hold Activity reference di Service. Pakai `applicationContext` atau Service `this`.
- `ComposeView.disposeComposition()` dipanggil sebelum `windowManager.removeView()`. `ViewCompositionStrategy` yang benar auto-handle.
- `viewModelStore.clear()` di `onDestroy` service — clear semua ViewModel scoped ke overlay.
- `Job.cancel()` semua coroutine scope sebelum service stop.
- `WindowManager.removeViewImmediate()` saat emergency cleanup (`removeView` async, kadang race dengan `onDestroy`).

### 10.2 Memory budget

| Component | Target | Trigger unload |
|---|---|---|
| Compose overlay (bubble + collapsed) | 15-25 MB | Permanent |
| Compose expanded panel (chat UI + viewmodels) | 20-30 MB | Hide ke bubble saat user collapse |
| Unity VRM player + model loaded | 200-300 MB | `onLowMemory()`, user dismiss VRM |
| Wake word engine (Porcupine) | 1-3 MB | Permanent |
| Wake word engine (microWakeWord) | 0.5-1 MB | Permanent |
| Whisper.cpp small (STT on-device) | 80-150 MB | Lazy load saat wake word triggered |

Total **idle** (bubble + wake word) target: **40 MB**. Active dengan VRM: **350 MB**.

### 10.3 Memory pressure listener

```kotlin
override fun onTrimMemory(level: Int) {
    when {
        level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
            // Unload Unity VRM, collapse to bubble only
            unityBridge?.unloadModel()
            expandedPanelHidden = true
        }
        level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
            // Clear Whisper cache, downgrade animation quality
            whisperEngine?.clearCache()
        }
    }
}
```

### 10.4 Unity UaaL integration di overlay window

UnityPlayer di-instantiate dari Service context:

```kotlin
class UnityVrmHost(private val service: Service) {
    private var unityPlayer: UnityPlayer? = null
    private var unityWindow: SurfaceView? = null

    fun showVrm() {
        // Unity butuh Activity context, jadi wrap dengan ContextWrapper
        val unityCtx = object : ContextWrapper(service) {
            override fun getApplicationContext() = service.applicationContext
        }
        unityPlayer = UnityPlayer(unityCtx)
        val params = WindowManager.LayoutParams(
            WRAP_CONTENT, WRAP_CONTENT,
            TYPE_APPLICATION_OVERLAY,
            FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL or FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        service.windowManager.addView(unityPlayer, params)
        unityPlayer?.resume()
    }
}
```

**Caveat besar**: Unity `UnityPlayer` historically **mengharapkan Activity host**. Beberapa Unity version (sebelum 2022.3) crash kalau di-attach ke Service. Unity 6.2 sudah lebih lentur tapi **belum officially supported** untuk Service overlay. Workaround community: spawn invisible transparent Activity yang bridge Unity rendering, lalu overlay Compose di atasnya. Risk: 2-Activity-1-Service architecture complex.

Alternative yang lebih aman: **Unity render ke `Surface`** (off-screen FBO), lalu Compose `AndroidView` host `SurfaceView` yang sama. Production VRM apps (VRoid Mobile, VTube Studio Mobile) confirmed pakai surface bridge.

---

## 11. ChibiClaw + VRM Assistant — Concrete Architecture

### 11.1 Service blueprint

```kotlin
class ChibiService : LifecycleService() {

    private lateinit var overlayOwner: OverlayLifecycleOwner
    private lateinit var audioPipeline: AudioPipeline
    private lateinit var wakeWord: WakeWordEngine
    private lateinit var bubbleHost: BubbleOverlayHost
    private lateinit var panelHost: ExpandedPanelHost
    private lateinit var vrmHost: UnityVrmHost?

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        overlayOwner = OverlayLifecycleOwner().apply { onCreate() }
        startForegroundWithTypes()

        audioPipeline = AudioPipeline(this)
        wakeWord = WakeWordEngine.microWakeWord(this, model = "hey_fuu.tflite")
        bubbleHost = BubbleOverlayHost(this, overlayOwner)
        panelHost = ExpandedPanelHost(this, overlayOwner)

        serviceScope.launch {
            audioPipeline.start()
            wakeWord.observe(audioPipeline.frames).collect { event ->
                when (event) {
                    is WakeEvent.Detected -> onWakeWord()
                    is WakeEvent.Error -> handleErr(event)
                }
            }
        }

        bubbleHost.show()
        bubbleHost.onTap { panelHost.expand() }
        bubbleHost.onLongPress { vrmHost?.toggle() }
    }

    private fun startForegroundWithTypes() {
        val fgsTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                       ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                       ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(), fgsTypes
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
            vrmHost?.unload()
            panelHost.collapse()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        vrmHost?.destroy()
        panelHost.destroy()
        bubbleHost.destroy()
        audioPipeline.stop()
        wakeWord.destroy()
        overlayOwner.onDestroy()
        super.onDestroy()
    }
}
```

### 11.2 Onboarding flow

```
1. App first launch (regular Activity)
   ├── Splash: "ChibiClaw butuh izin untuk hidup di atas semua app"
   ├── Request POST_NOTIFICATIONS (Android 13+)
   ├── Request RECORD_AUDIO 
   ├── Request SYSTEM_ALERT_WINDOW (Settings intent)
   ├── Request IGNORE_BATTERY_OPTIMIZATIONS
   └── Vendor-specific wizard (auto-detect Build.MANUFACTURER):
       ├── [Xiaomi] Open Autostart, screenshot guide, "Sudah?" button
       ├── [Oppo] Open Startup manager
       ├── [Samsung] Open Sleeping apps list
       ├── ...
       └── Verification: ping service after each step

2. Setelah semua granted:
   ├── Start ChibiService (FGS microphone+specialUse+mediaPlayback)
   ├── Show bubble di pojok kanan tengah default
   └── Tutorial overlay: "Tap bubble untuk chat. Bilang 'Hey Fuu' untuk voice."

3. Per-reboot: 
   ├── BootReceiver receive BOOT_COMPLETED
   ├── Start ChibiBootService (specialUse only — gak butuh mic dulu)
   ├── Show notif persistent: "ChibiClaw idle. Tap untuk aktifkan listening."
   └── Saat user tap: upgrade ke type=microphone
```

### 11.3 Battery budget per state

| State | Mic | Unity VRM | Battery/jam |
|---|---|---|---|
| Bubble only, wake word duty-cycle | On (B) | Off | ~1.5%/h |
| Bubble + expanded chat (idle) | On | Off | ~2%/h |
| Wake triggered → STT active | On (continuous) | Off | ~5%/h |
| Full mode: VRM visible + voice active | On | On (rendering) | ~12-18%/h |
| Bubble hidden (notification only) | Off | Off | ~0.3%/h |

Target user experience: **default mode (bubble + duty-cycle wake word) = 30-40% baterai/hari** di Snapdragon 8 Elite + 5000mAh. Acceptable kalau dibandingkan Google Assistant always-on.

### 11.4 Tracking metric production

| Metric | Tool | Threshold |
|---|---|---|
| Service uptime per 24h | Heartbeat ke local DataStore | >95% di Pixel, >80% di Xiaomi |
| Wake word false-trigger / hari | Local log | <3/hari per user |
| Wake word miss rate | Manual user feedback | <10% |
| Memory peak | `ActivityManager.getMemoryInfo()` snapshot | <450 MB |
| Cold start latency (boot → bubble visible) | Trace | <3 detik (post-user-tap) |
| Crash rate | Sentry / Firebase Crashlytics | <1% sessions |

---

## 12. Risk Register

| # | Risk | Likelihood | Impact | Mitigasi |
|---|---|---|---|---|
| R1 | Play Store reject `FOREGROUND_SERVICE_SPECIAL_USE` justification | High | High | Drafting justifikasi detail: "persistent voice assistant overlay yang user explicit launch dan kontrol via notification" + screenshot UX |
| R2 | Xiaomi MIUI kill service tanpa warning meskipun all permission granted | Very high | Very high | Onboarding wizard explicit per-vendor, heartbeat detection, prompt user re-enable |
| R3 | Unity UaaL crash di Service context (no Activity host) | Medium | High | Spawn helper transparent Activity untuk Unity, atau pakai surface-bridge pattern (off-screen Unity render ke `Surface`, Compose host) |
| R4 | Android 17 (predicted) auto-revoke overlay listener untuk non-assistance apps | Medium | High | Argue ChibiClaw masuk role assistant, request `ROLE_ASSISTANT` |
| R5 | Mic concurrent dengan Google Assistant: user trigger "Hey Google" → kita pause, balik lupa resume | Medium | Medium | Robust `AudioFocusChangeListener` resume + watchdog 60s timer |
| R6 | Compose overlay memory leak di rotation event (config change) | Medium | Medium | `OverlayLifecycleOwner` handle config change, recreate ComposeView clean |
| R7 | Touch pass-through gagal di game full-screen immersive | Low | Medium | Document limitation, suggest user hide bubble in gamer mode (via DND or quick toggle) |
| R8 | Battery optimization request denied Play Store policy | Medium | Medium | Argue assistance category, fallback ke onboarding manual deep-link |
| R9 | Privacy indicator (dot hijau) bikin user paranoid | Low | Low | Onboarding edukasi + toggle "Pause Mic" prominent |

---

## 13. Action Items untuk ChibiClaw v4 + VRM Assistant

Berdasarkan riset, berikut yang **harus** masuk ke spec implementation:

1. **Service architecture**: single `ChibiService` extends `LifecycleService` dengan tiga FGS type sekaligus (`microphone|specialUse|mediaPlayback`). Tidak bikin service terpisah per fungsi.
2. **`OverlayLifecycleOwner` reusable component**: extract jadi library internal, share antara ChibiClaw + VRM Assistant.
3. **Audio pipeline single-source**: `AudioRecord` di satu tempat, fan-out lewat `SharedFlow`. Tidak ada engine buka `AudioRecord` sendiri.
4. **Vendor wizard mandatory di onboarding**: detect 9 manufacturer utama (Xiaomi, Oppo, Realme, Vivo, iQOO, Samsung, Honor, OnePlus, Huawei), generate per-step intent + screenshot guide. Sisanya fallback generic.
5. **Heartbeat watchdog**: setiap 30 detik tulis timestamp, app main process check gap → prompt re-enable. Implementasi pakai `DataStore` Proto.
6. **Bubbles API sebagai "Lite Mode"**: opsional kalau user gak mau granting `SYSTEM_ALERT_WINDOW`. Conversation-only, terbatas, tapi survival rate lebih tinggi.
7. **Onboarding mic indicator edukasi**: jelaskan dot hijau itu normal, tidak rekam ke cloud (untuk wake word lokal).
8. **Memory pressure listener**: auto-unload Unity VRM di `TRIM_MEMORY_RUNNING_CRITICAL`. Bubble + wake word **tetap hidup** sampai `TRIM_MEMORY_COMPLETE`.
9. **`ROLE_ASSISTANT` request**: opsional, beri user pilihan ChibiClaw replace Google Assistant. Beri benefit (long-press home), tidak block normal flow kalau menolak.
10. **Boot recovery dengan specialUse bridge**: BootReceiver start specialUse-only FGS, notif tap upgrade ke microphone. Tidak coba auto-resume mic di boot karena Android 14+ block.
11. **Library evaluasi**: prototype dulu dengan `dofire/Floating-Bubble-View` (production-tested) untuk validate desain bubble. Migrate ke implementation kustom kalau butuh integrasi Unity dalam.
12. **VRM rendering strategy**: prototype dengan helper transparent Activity host UnityPlayer dulu (lowest risk), benchmark performance. Kalau OK, biarkan. Kalau ada glitches, migrate ke surface-bridge pattern.

---

## 14. Open Questions / Need Verification di Android 16+

Item-item berikut belum confirmed pasti di Android 16 (May 2026) dan butuh verifikasi:

- **`FOREGROUND_SERVICE_SPECIAL_USE` policy enforcement Q3 2026**: rumor Google akan tighten Play Store review. Belum ada announcement resmi sampai 13 Mei 2026.
- **Job quota dari WorkManager call di FGS**: behavior di Android 16 baru, butuh empirical test di device riil.
- **Android 17 accessibility-tightening spillover ke overlay**: hipotesis dari riset 01 — kalau Android 17 enforce `isAccessibilityTool`, mungkin spillover ke `SYSTEM_ALERT_WINDOW` non-justified juga. Belum confirmed.
- **Unity 6.2 UaaL Service-context stability**: Unity blog tidak explicit support Service overlay. Butuh stress test 24+ jam idle untuk validate.
- **HyperOS 3 (predicted Q4 2026)**: rumor Xiaomi akan loosen autostart rules untuk app yang request `RECEIVER_NOT_EXPORTED` properly. Belum confirmed.

---

## 15. Sumber Utama

Semua diakses **13 Mei 2026**.

### Dokumentasi resmi Android
- [Foreground services types are required (Android 14)](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Changes to foreground service types for Android 15](https://developer.android.com/about/versions/15/changes/foreground-service-types)
- [Restrictions on starting a foreground service from the background](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Behavior changes: Apps targeting Android 14 or higher](https://developer.android.com/about/versions/14/behavior-changes-14)
- [Behavior changes: Apps targeting Android 15 or higher](https://developer.android.com/about/versions/15/behavior-changes-15)
- [Privacy indicators (AOSP)](https://source.android.com/docs/core/permissions/privacy-indicators)
- [Notification bubbles for conversations](https://developer.android.com/develop/ui/compose/notifications/bubbles)
- [Picture-in-picture](https://developer.android.com/develop/ui/views/picture-in-picture)
- [Ensure compatibility with gesture navigation](https://developer.android.com/develop/ui/views/touch-and-input/gestures/gesturenav)
- [Foreground services in Android 11](https://developer.android.com/about/versions/11/privacy/foreground-services)
- [WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)
- [Lifecycle in Jetpack Compose](https://developer.android.com/topic/libraries/architecture/lifecycle)
- [ViewModel overview](https://developer.android.com/topic/libraries/architecture/viewmodel)
- [Saved State module for ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate)

### Vendor restriction registry
- [Don't Kill My App (main)](https://dontkillmyapp.com/)
- [Don't Kill My App — Xiaomi](https://dontkillmyapp.com/xiaomi)
- [Don't Kill My App — Samsung](https://dontkillmyapp.com/samsung)
- [Don't Kill My App — Oppo](https://dontkillmyapp.com/oppo)
- [Don't Kill My App — Vivo](https://dontkillmyapp.com/vivo)
- [Don't Kill My App — OnePlus](https://dontkillmyapp.com/oneplus)
- [Don't Kill My App — Mission](https://dontkillmyapp.com/problem)
- [DontKillMyApp Android benchmark](https://play.google.com/store/apps/details?id=com.urbandroid.dontkillmyapp)
- [HyperOS background apps guide (2024)](https://www.allabouthyperos.com/2024/11/hyperos-how-to-prevent-apps.html)
- [Samsung OneUI 7 battery (May 2025)](https://www.sammyfans.com/2025/05/03/save-your-samsung-galaxys-battery-after-one-ui-7/)

### Compose overlay implementation
- [Using Jetpack Compose in WindowManager Overlays (Medium, Dec 2025)](https://medium.com/@rohitkasanwal/using-jetpack-compose-in-windowmanager-overlays-a-case-study-a8aea57cb44d)
- [How to Use Jetpack Compose Inside Android Service](https://www.techyourchance.com/jetpack-compose-inside-android-service/)
- [Jetpack Compose OverlayService gist](https://gist.github.com/handstandsam/6ecff2f39da72c0b38c07aa80bbb5a2f)
- [Compose Overlay Service Kishan Vadoliya gist](https://gist.github.com/kishan-vadoliya/9fbd1e3c1590de1e4a1a830c5d4edb3f)

### OSS libraries
- [dofire/Floating-Bubble-View](https://github.com/dofire/Floating-Bubble-View)
- [luiisca/floating-views](https://github.com/luiisca/floating-views)
- [BijoySingh/Floating-Bubble-Library-Android](https://github.com/BijoySingh/Floating-Bubble-Library-Android)
- [noln/system-alert-window-example](https://github.com/noln/system-alert-window-example)
- [Vitor720/FloatingBubble Kotlin sample](https://github.com/Vitor720/FloatingBubble)
- [GitHub topic: floating-window kotlin](https://github.com/topics/floating-window?l=kotlin)

### Wake word & audio
- [Porcupine Wake Word documentation](https://picovoice.ai/docs/porcupine/)
- [Porcupine Wake Word Android quick start](https://picovoice.ai/docs/quick-start/porcupine-android/)
- [Wake Word Detection Guide 2026](https://picovoice.ai/blog/complete-guide-to-wake-word/)
- [Porcupine GitHub](https://github.com/Picovoice/porcupine)

### WorkManager & Service revival
- [Foreground Services vs WorkManager (Medium 2025)](https://medium.com/@amar90aqi/foreground-service-vs-workmanager-in-android-choosing-the-right-tool-for-background-tasks-32c1242f9898)
- [From Foreground Services to WorkManager: 70% battery cut (DEV 2025)](https://dev.to/suridevs_861b8a311a101be4/from-foreground-services-to-workmanager-how-we-cut-battery-drain-by-70-2d2c)
- [Long-running workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)
- [WorkManager StopReason](https://proandroiddev.com/why-has-my-background-worker-stopped-exploring-android-workmangers-stopreason-a0f743e6411c)
- [Android Foreground Services in 2026 (dev.to)](https://dev.to/joe_wang_6a4a3e51566e8b52/android-foreground-services-in-2026-what-changed-and-how-to-adapt-2o3d)

### Touch handling & gesture
- [Untrusted Touch Events in Android (Medium)](https://medium.com/androiddevelopers/untrusted-touch-events-2c0e0b9c374c)
- [Gesture Navigation: handling conflicts (Chris Banes)](https://medium.com/androiddevelopers/gesture-navigation-handling-gesture-conflicts-8ee9c2665c69)

### Unity UaaL
- [Unity as Library Android docs](https://docs.unity3d.com/Manual/UnityasaLibrary-Android.html)
- [Unity-Technologies/uaal-example](https://github.com/Unity-Technologies/uaal-example)
- [How to Integrate Unity AR as Library (ITNEXT)](https://itnext.io/how-to-integrate-a-unity-ar-project-as-a-library-in-android-uaal-geospatial-ar-45618bb21aa0)

### BOOT_COMPLETED restrictions
- [BOOT_COMPLETED foreground service issue 356](https://github.com/Dev-hwang/flutter_foreground_task/issues/356)
- [Android 15 mediaprojection BOOT_COMPLETED warning](https://community.cometchat.com/t/android-15-warning-restricted-foreground-service-started-from-boot-completed-by-webrtcmodule-mediaprojectionservice/511)

### Memory leak prevention
- [WindowLeaked Exception Guide 2026](https://copyprogramming.com/howto/android-windowmanager-android-view-windowleaked-activity)
- [Memory Leak in Android — Root Cause and Fixes](https://medium.com/@manishkumar_75473/memory-leak-in-android-understand-root-cause-and-its-fixes-b81041b88c9a)

---

**Disclaimer**: Riset ini valid per 13 Mei 2026. Android 16 baru rilis stable Q1 2026, Android 17 masih Developer Preview di tahap riset ini. Sebelum implementasi production, verifikasi ulang:
1. Play Store policy `FOREGROUND_SERVICE_SPECIAL_USE` justification template terkini
2. Android 17 Developer Preview behavior change (DP1 expected Feb 2026)
3. Vendor restriction policy update Q2-Q3 2026 (HyperOS 3, ColorOS 16, OneUI 8 rumor)
