# 03 — Execution Strategy: Intent-First Hierarchy

## Prinsip

> Selalu gunakan cara paling ringan dulu. Eskalasi hanya jika tier sebelumnya tidak bisa.

Diagram: `diagrams/execution-hierarchy.txt`

## Tier 1: Intent API (Paling Ringan)

Gratis, cepat, official Android API. Tidak butuh permission khusus.

| Perintah User | Intent | Permission |
|---------------|--------|------------|
| Telepon Budi | `ACTION_CALL` + `tel:08123...` | CALL_PHONE |
| Buka dialer | `ACTION_DIAL` + `tel:08123...` | — |
| Kirim SMS | `ACTION_SENDTO` + `smsto:08123...` | — |
| Buka WA ke Budi | `ACTION_VIEW` + `wa.me/62812...` | — |
| WA + teks | `ACTION_VIEW` + `wa.me/62812...?text=Otw` | — |
| Kirim email | `ACTION_SENDTO` + `mailto:...` | — |
| Buka Maps | `ACTION_VIEW` + `geo:lat,long` | — |
| Buka URL | `ACTION_VIEW` + `https://...` | — |
| Setting WiFi | `ACTION_WIFI_SETTINGS` | — |
| Setting Bluetooth | `ACTION_BLUETOOTH_SETTINGS` | — |
| Set alarm | `ACTION_SET_ALARM` | SET_ALARM |
| Buka app | `getLaunchIntentForPackage()` | — |
| Share teks | `ACTION_SEND` + `setPackage()` | — |

**Limitasi**: Intent membuka app + pre-fill data, tapi TIDAK auto-complete aksi (misal: WA deep link buka chat tapi user/Accessibility tetap harus klik Send).

## Tier 2: Content Provider & System API

Baca/tulis data tanpa interaksi UI.

| Data | API | Permission |
|------|-----|------------|
| Baca kontak | `ContactsContract` | READ_CONTACTS |
| Baca SMS | `Telephony.Sms` | READ_SMS |
| Baca/tulis kalender | `CalendarContract` | READ/WRITE_CALENDAR |
| Akses media | `MediaStore` | READ_MEDIA_* |
| Toggle WiFi | `WifiManager` | CHANGE_WIFI_STATE |
| Toggle Bluetooth | `BluetoothAdapter` | BLUETOOTH_CONNECT |
| Atur volume | `AudioManager` | — |
| Set brightness | `Settings.System` | WRITE_SETTINGS |
| Info baterai | `BatteryManager` | — |
| Info network | `ConnectivityManager` | ACCESS_NETWORK_STATE |

## Tier 3: Accessibility Service

UI automation. Hanya saat Tier 1-2 tidak cukup.

Kapan dipakai:
- Klik tombol Send di WhatsApp (setelah Intent pre-fill)
- Navigasi UI app tanpa deep link
- Scroll mencari item di list
- Baca konten layar app pihak ketiga

Dual-path perception (diagram: `diagrams/perception-dual-path.txt`):
- **Path A**: Accessibility Tree → Distiller → UI Map (primary)
- **Path B**: Screenshot → Gemma Vision (fallback jika Path A < 10 nodes)

## Tier 4: Shizuku / ADB Shell

Last resort. Butuh Wireless Debugging setup.

| Aksi | Command | Severity |
|------|---------|----------|
| Force stop app | `am force-stop {pkg}` | HIGH |
| Install APK | `pm install {path}` | HIGH |
| Clear app data | `pm clear {pkg}` | BLOCKED |
| Input tap | `input tap {x} {y}` | MEDIUM |
| Input text | `input text "..."` | MEDIUM |

## Perbandingan v1 vs v3

| Task | v1 (Lama) | v3 (Baru) |
|------|-----------|-----------|
| Telepon Budi | Accessibility → 5 langkah | Intent `ACTION_CALL` → 1 langkah |
| WA ke Budi: Otw | Accessibility → 7 langkah | Intent deep link + 1 click → 3 langkah |
| Nyalakan WiFi | Shizuku shell | `WifiManager` API → standar |
| Set alarm 7 pagi | Accessibility → 5+ langkah | Intent `ACTION_SET_ALARM` → 1 langkah |

## Router Logic (Pseudo-code)

```kotlin
suspend fun execute(action: ActionPlan): Result {
    return when {
        action.canUseIntent() -> IntentExecutor.execute(action)
        action.canUseContentProvider() -> ContentProviderExecutor.execute(action)
        action.requiresUiInteraction() -> {
            val uiMap = perceptionModule.scan() // lazy, hanya saat dibutuhkan
            AccessibilityExecutor.execute(action, uiMap)
        }
        action.requiresShell() -> {
            if (approvalGate.check(action) == APPROVED)
                ShizukuExecutor.execute(action)
            else Result.Denied("User denied")
        }
        else -> Result.Unsupported()
    }
}
```
