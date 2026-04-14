package com.chibiclaw.ai

import com.chibiclaw.executor.*
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import kotlinx.coroutines.runBlocking

class ChibiClawTools(
    private val onAction: suspend (ChibiAction) -> String
) : ToolSet {

    @Tool(
        description = "Kirim Intent API Android asli. Gunakan HANYA action string resmi Android seperti: " +
            "android.intent.action.VIEW, android.intent.action.CALL, android.intent.action.DIAL, " +
            "android.intent.action.SEND, android.intent.action.WEB_SEARCH, " +
            "android.settings.WIFI_SETTINGS, android.settings.BLUETOOTH_SETTINGS. " +
            "JANGAN karang action baru — kalau action tidak ada di daftar ini, tool akan mengembalikan error. " +
            "Untuk senter pakai system_control, untuk alarm pakai set_alarm."
    )
    fun intentSend(
        @ToolParam(description = "Action string resmi Android (contoh: android.intent.action.VIEW)") action: String,
        @ToolParam(description = "Target URI (contoh: tel:+62xxx, https://wa.me/xxx, geo:lat,lng). Kosongkan untuk action yang tidak butuh data.") uri: String,
        @ToolParam(description = "Package name target app, kosongkan jika tidak spesifik") packageName: String = ""
    ): Map<String, String> {
        val result = runBlocking {
            onAction(IntentAction(action = action, uri = uri, packageName = packageName))
        }
        return mapOf("result" to result)
    }

    @Tool(
        description = "Kontrol hardware sistem langsung (via API, bukan Intent): senter/flashlight, volume, brightness. " +
            "WAJIB dipakai untuk senter — tidak ada intent senter di Android."
    )
    fun systemControl(
        @ToolParam(description = "Target: flashlight, volume, brightness") target: String,
        @ToolParam(description = "State: on, off, toggle, atau angka 0-100 untuk volume/brightness") state: String
    ): Map<String, String> {
        val result = runBlocking {
            onAction(SystemControlAction(target = target, state = state))
        }
        return mapOf("result" to result)
    }

    @Tool(
        description = "Buka app terinstall berdasarkan NAMA (bukan package). " +
            "WAJIB dipakai untuk semua perintah 'buka X' / 'open X' / 'launch X'. " +
            "JANGAN tebak package name di intent_send — gunakan tool ini, PackageManager " +
            "yang resolve nama app ke package yang benar. " +
            "Contoh: launch_app(appName=\"TikTok\"), launch_app(appName=\"Honor of Kings\"), " +
            "launch_app(appName=\"YouTube Music\")."
    )
    fun launchApp(
        @ToolParam(description = "Nama app yang terlihat user (contoh: TikTok, WhatsApp, YouTube Music)") appName: String
    ): Map<String, String> {
        val result = runBlocking { onAction(LaunchAppAction(appName = appName)) }
        return mapOf("result" to result)
    }

    @Tool(
        description = "Buat alarm di clock app device. Format jam 24 jam. " +
            "Contoh: \"besok jam 07:03\" → hour=7, minute=3."
    )
    fun setAlarm(
        @ToolParam(description = "Jam (0-23)") hour: Int,
        @ToolParam(description = "Menit (0-59)") minute: Int,
        @ToolParam(description = "Label alarm (opsional, contoh: 'Bangun kerja')") label: String = ""
    ): Map<String, String> {
        val result = runBlocking {
            onAction(SetAlarmAction(hour = hour, minute = minute, label = label))
        }
        return mapOf("result" to result)
    }

    @Tool(description = "Query data dari Content Provider Android (kontak, SMS, kalender, sistem)")
    fun contentQuery(
        @ToolParam(description = "Provider: contacts, sms, calendar, system") provider: String,
        @ToolParam(description = "Search query atau parameter spesifik") query: String
    ): Map<String, String> {
        val result = runBlocking { onAction(ContentQueryAction(provider = provider, query = query)) }
        return mapOf("result" to result)
    }

    @Tool(description = "Interaksi UI via Accessibility Service. Gunakan untuk tap/ketik/scroll di app lain. " +
        "Actions: click, type, scroll_down, scroll_up, back, home, recents, notifications, " +
        "quick_settings, lock_screen, split_screen, power_dialog. " +
        "Target boleh berupa label text, content description, atau resource ID suffix — " +
        "TargetResolver akan cari node yang paling cocok. Untuk action global (back/home/dll) " +
        "isi target kosong. System otomatis observe layar setelah ui_interact, " +
        "jadi TIDAK perlu panggil scan_ui manual.")
    fun uiInteract(
        @ToolParam(description = "Action: click, type, scroll_down, scroll_up, back, home, recents, notifications, quick_settings, lock_screen, split_screen, power_dialog") action: String,
        @ToolParam(description = "Target: teks node, content description, atau resource ID suffix. Kosongkan untuk action global.") target: String,
        @ToolParam(description = "Teks untuk diketik (hanya untuk action 'type')") text: String = ""
    ): Map<String, String> {
        val result = runBlocking { onAction(UiInteractAction(action = action, target = target, text = text)) }
        return mapOf("result" to result)
    }

    @Tool(description = "Gesture low-level via AccessibilityService.dispatchGesture. Gunakan ketika " +
        "ui_interact gagal (node_not_found) dan kamu tahu koordinat dari vision_analyze. " +
        "Kinds: tap_coord (butuh x1,y1), long_press (butuh x1,y1), swipe (butuh x1,y1,x2,y2), " +
        "drag (butuh x1,y1,x2,y2,duration_ms), pinch_in, pinch_out, double_tap.")
    fun gesture(
        @ToolParam(description = "Kind: tap_coord, long_press, swipe, drag, pinch_in, pinch_out, double_tap") kind: String,
        @ToolParam(description = "Start X in pixels (dari vision_analyze bounds)") x1: Int = 0,
        @ToolParam(description = "Start Y in pixels") y1: Int = 0,
        @ToolParam(description = "End X (swipe/drag only)") x2: Int = 0,
        @ToolParam(description = "End Y (swipe/drag only)") y2: Int = 0,
        @ToolParam(description = "Duration millis (default: tap=100, longPress=800, swipe=300)") durationMs: Int = 0
    ): Map<String, String> {
        val result = runBlocking {
            onAction(GestureAction(kind = kind, x1 = x1, y1 = y1, x2 = x2, y2 = y2, durationMs = durationMs))
        }
        return mapOf("result" to result)
    }

    @Tool(description = "Analisa tampilan layar via Gemma vision + OCR. " +
        "Gunakan untuk: (a) find_element — cari elemen UI berdasarkan deskripsi " +
        "natural (\"tombol kirim hijau di pojok kanan bawah\") dan dapatkan koordinat bounds; " +
        "(b) describe — dapatkan deskripsi lengkap layar saat accessibility tree kosong " +
        "(Canvas, WebView, game); (c) ocr — ekstrak semua text dari screenshot; " +
        "(d) count — hitung jumlah elemen tertentu. Butuh MediaProjection grant sekali.")
    fun visionAnalyze(
        @ToolParam(description = "Mode: describe, find_element, ocr, count") mode: String,
        @ToolParam(description = "Query spesifik untuk find_element/count (contoh: 'tombol login', 'notifikasi merah')") query: String = ""
    ): Map<String, String> {
        val result = runBlocking { onAction(VisionAnalyzeAction(mode = mode, query = query)) }
        return mapOf("result" to result)
    }

    @Tool(description = "Scan layar manual untuk mendapatkan UI map tekstual. " +
        "JARANG dibutuhkan — ui_interact sudah auto-observe setelah aksi. " +
        "Pakai ini hanya ketika user secara eksplisit meminta snapshot layar " +
        "atau saat debugging layout.")
    fun scanUi(
        @ToolParam(description = "Method: accessibility (default) atau screenshot") method: String = "accessibility"
    ): Map<String, String> {
        val result = runBlocking { onAction(ScanUiAction(method = method)) }
        return mapOf("ui_map" to result)
    }

    @Tool(description = "Cari informasi di long-term memory (kontak, history, pola)")
    fun memoryQuery(
        @ToolParam(description = "Search query") query: String,
        @ToolParam(description = "Scope: contacts, history, patterns, all") scope: String = "all"
    ): Map<String, String> {
        val result = runBlocking { onAction(MemoryQueryAction(query = query, scope = scope)) }
        return mapOf("result" to result)
    }

    @Tool(description = "Tunggu beberapa detik sebelum aksi berikutnya (untuk menunggu UI load)")
    fun wait(
        @ToolParam(description = "Jumlah detik untuk ditunggu (max 10)") seconds: Int
    ): Map<String, String> {
        val clamped = seconds.coerceIn(1, 10)
        val result = runBlocking { onAction(WaitAction(seconds = clamped)) }
        return mapOf("result" to result)
    }

    @Tool(description = "Minta konfirmasi atau informasi tambahan dari user sebelum melanjutkan")
    fun askUser(
        @ToolParam(description = "Pertanyaan yang ingin ditanyakan ke user") question: String
    ): Map<String, String> {
        val result = runBlocking { onAction(AskUserAction(question = question)) }
        return mapOf("result" to result)
    }

    @Tool(description = "Laporkan hasil akhir aksi ke user. Selalu panggil ini di akhir task.")
    fun report(
        @ToolParam(description = "Pesan laporan untuk user") message: String,
        @ToolParam(description = "Status: success, partial, failed") status: String = "success"
    ): Map<String, String> {
        val result = runBlocking { onAction(ReportAction(message = message, status = status)) }
        return mapOf("result" to result)
    }

    @Tool(
        description = "Kirim pesan ke orang lain via SMS, WhatsApp, Telegram, atau Email. " +
            "Kind 'sms' langsung kirim tanpa buka app (butuh izin SEND_SMS). " +
            "Kind 'whatsapp'/'telegram' buka app chat tujuan — user tinggal tap Send. " +
            "Kind 'email' buka email composer. " +
            "Untuk WhatsApp/Telegram, recipient = nomor telepon atau @username."
    )
    fun messaging(
        @ToolParam(description = "Kind: sms, whatsapp, telegram, email") kind: String,
        @ToolParam(description = "Penerima: nomor telepon (08xxx/+62xxx) atau @username Telegram atau email address") recipient: String,
        @ToolParam(description = "Isi pesan yang mau dikirim") body: String,
        @ToolParam(description = "Subject email (hanya untuk kind email, kosongkan untuk jenis lain)") subject: String = ""
    ): Map<String, String> {
        val result = runBlocking {
            onAction(MessagingAction(kind = kind, recipient = recipient, body = body, subject = subject))
        }
        return mapOf("result" to result)
    }

    @Tool(
        description = "Operasi clipboard: copy teks ke clipboard, paste/baca isi clipboard, " +
            "lihat history clipboard (20 item terakhir), atau clear clipboard. " +
            "Gunakan untuk copy-paste antar app, simpan teks sementara, " +
            "atau baca apa yang terakhir di-copy user."
    )
    fun clipboard(
        @ToolParam(description = "Operasi: get (baca clipboard), set (copy teks), history (lihat riwayat), clear (hapus)") op: String,
        @ToolParam(description = "Teks untuk di-copy (hanya untuk op 'set')") text: String = ""
    ): Map<String, String> {
        val result = runBlocking { onAction(ClipboardAction(op = op, text = text)) }
        return mapOf("result" to result)
    }

    @Tool(
        description = "Buka kamera untuk foto/video, atau scan QR/barcode, atau OCR dari gambar gallery. " +
            "Kind 'photo' buka kamera foto, 'video' buka kamera video, " +
            "'qr_scan' scan barcode/QR dari URI gambar, " +
            "'ocr_gallery' ekstrak teks dari gambar di gallery."
    )
    fun capture(
        @ToolParam(description = "Kind: photo, video, qr_scan, ocr_gallery") kind: String,
        @ToolParam(description = "URI gambar untuk qr_scan/ocr_gallery (kosongkan untuk photo/video)") uri: String = ""
    ): Map<String, String> {
        val args = if (uri.isNotEmpty()) mapOf("uri" to uri) else emptyMap()
        val result = runBlocking { onAction(CaptureAction(kind = kind, args = args)) }
        return mapOf("result" to result)
    }

    @Tool(
        description = "Kontrol media player yang sedang aktif (Spotify, YouTube Music, dll). " +
            "Bisa play, pause, skip, previous, stop, fast forward, rewind, " +
            "atur volume (volume_up/volume_down/mute/unmute), " +
            "atau info/now_playing untuk lihat lagu yang sedang diputar."
    )
    fun mediaSession(
        @ToolParam(description = "Command: play, pause, toggle, next, prev, stop, ff, rw, volume_up, volume_down, mute, unmute, info, now_playing") command: String,
        @ToolParam(description = "Target app (opsional, kosongkan untuk kontrol player aktif)") target: String = ""
    ): Map<String, String> {
        val result = runBlocking { onAction(MediaSessionAction(command = command, target = target)) }
        return mapOf("result" to result)
    }

    @Tool(
        description = "Utilitas kalkulasi dan konversi tanpa internet. " +
            "Kind 'calc': hitung ekspresi matematika (contoh: '2+3*4', '100/7'). " +
            "Kind 'unit_convert': konversi satuan — input='5 km', param='mi' → 5 km = 3.1 mi. " +
            "Satuan yang didukung: km/mi/m/cm/mm/ft/in/yd (jarak), kg/g/lb/oz (berat), " +
            "l/ml/gal (volume), c/f/k (suhu). " +
            "Kind 'timezone': waktu sekarang di timezone tertentu — input='Asia/Tokyo'. " +
            "Kind 'translate': terjemahan offline — input='hello', param='en->id'."
    )
    fun utility(
        @ToolParam(description = "Kind: calc, unit_convert, timezone, translate") kind: String,
        @ToolParam(description = "Input: ekspresi math / '<angka> <unit>' / timezone ID / teks untuk translate") input: String,
        @ToolParam(description = "Parameter tambahan: target unit untuk konversi / 'src->dst' untuk translate. Kosongkan untuk calc/timezone.") param: String = ""
    ): Map<String, String> {
        val result = runBlocking { onAction(UtilityAction(kind = kind, input = input, param = param)) }
        return mapOf("result" to result)
    }

    @Tool(
        description = "Jalankan perintah sistem privileged via Shizuku (ADB-level). " +
            "HANYA gunakan ini untuk hal yang tidak bisa dilakukan tool biasa. " +
            "Kind yang diizinkan: " +
            "put_secure/put_global/put_system — tulis Settings (payload: 'key value'), " +
            "get_secure/get_global/get_system — baca Settings (payload: 'key'), " +
            "force_stop — paksa stop app (payload: package name), " +
            "grant_permission/revoke_permission — kelola izin app (payload: 'package permission'), " +
            "uninstall_user — uninstall app user (payload: package name). " +
            "JANGAN pakai untuk app sistem (com.android.*, com.google.android.*). " +
            "Membutuhkan Shizuku Manager aktif di device."
    )
    fun shizukuCommand(
        @ToolParam(description = "Kind: put_secure, put_global, put_system, get_secure, get_global, get_system, force_stop, grant_permission, revoke_permission, uninstall_user") kind: String,
        @ToolParam(description = "Payload: 'key value' untuk put, 'key' untuk get, 'package' untuk force_stop/uninstall, 'package permission' untuk grant/revoke") payload: String
    ): Map<String, String> {
        val result = runBlocking { onAction(ShizukuAction(kind = kind, payload = payload)) }
        return mapOf("result" to result)
    }

    // ═══════════════════════════════════════════════════════════════
    // Phase 11 — Extended device control tools (C1–C14)
    // ═══════════════════════════════════════════════════════════════

    @Tool(
        description = "Kontrol device hardware/network/display/audio/power. " +
            "Category 'hardware': target=ir/vibrate/rotation/screen_timeout/refresh_rate/nfc. " +
            "Category 'network': target=wifi_info/mobile_data/hotspot/airplane/dns/vpn/network_info. " +
            "Category 'display': target=dark_mode/night_light/aod/dpi/font_size/screenshot. " +
            "Category 'audio': target=ring/notif/alarm/dnd/ringer_mode/mic/speaker. " +
            "Category 'power': target=battery_saver/lock/wake/reboot/battery_status/screen_on. " +
            "Command: on/off/toggle/set/get. Value: angka untuk volume (0-100), brightness, dll."
    )
    fun deviceControl(
        @ToolParam(description = "Category: hardware, network, display, audio, power") category: String,
        @ToolParam(description = "Target spesifik dalam category") target: String,
        @ToolParam(description = "Command: on, off, toggle, set, get, status") command: String,
        @ToolParam(description = "Value: angka untuk set (volume 0-100, timeout 30s/1m/5m), atau parameter tambahan") value: String = ""
    ): Map<String, String> {
        val result = runBlocking {
            onAction(DeviceControlAction(category = category, target = target, command = command, value = value))
        }
        return mapOf("result" to result)
    }

    @Tool(
        description = "Query telephony: riwayat panggilan, info SIM, angkat/tolak telepon, kirim USSD. " +
            "Operations: call_log (riwayat panggilan), sim_info (info SIM & operator), " +
            "answer (angkat telepon), reject (tolak telepon), end (tutup telepon), " +
            "ussd (kirim kode USSD), phone_number (nomor sendiri), network (info jaringan)."
    )
    fun telephony(
        @ToolParam(description = "Operation: call_log, sim_info, answer, reject, end, ussd, phone_number, network") operation: String,
        @ToolParam(description = "Value: jumlah log (untuk call_log), kode USSD (untuk ussd)") value: String = ""
    ): Map<String, String> {
        val result = runBlocking { onAction(TelephonyAction(operation = operation, value = value)) }
        return mapOf("result" to result)
    }

    @Tool(
        description = "Kelola aplikasi: list semua app, info app, force stop, clear data, uninstall, buka di Store. " +
            "Operations: list (semua app), info (detail app), force_stop (paksa berhenti), " +
            "clear_data (hapus data), uninstall (hapus app), open_info (buka Settings app), " +
            "store (buka di Play Store), is_installed (cek terinstall)."
    )
    fun appManage(
        @ToolParam(description = "Operation: list, info, force_stop, clear_data, uninstall, open_info, store, is_installed") operation: String,
        @ToolParam(description = "Nama app (contoh: TikTok, WhatsApp)") appName: String = "",
        @ToolParam(description = "Value tambahan: 'all' untuk list termasuk system app") value: String = ""
    ): Map<String, String> {
        val result = runBlocking {
            onAction(AppManageAction(operation = operation, appName = appName, value = value))
        }
        return mapOf("result" to result)
    }

    @Tool(
        description = "Kelola file: list folder, info file, copy, move, delete, share, zip, search, cek storage. " +
            "Operations: list (isi folder), info (detail file), copy, move, delete, share, " +
            "zip (compress), search (cari file), storage_info (info penyimpanan), " +
            "read (baca file teks), mkdir (buat folder), exists (cek ada)."
    )
    fun fileManage(
        @ToolParam(description = "Operation: list, info, copy, move, delete, share, zip, search, storage_info, read, mkdir, exists") operation: String,
        @ToolParam(description = "Path file/folder (contoh: /storage/emulated/0/Download)") path: String = "",
        @ToolParam(description = "Destination path (untuk copy/move/zip) atau search query (untuk search)") destination: String = ""
    ): Map<String, String> {
        val result = runBlocking {
            onAction(FileAction(operation = operation, path = path, destination = destination))
        }
        return mapOf("result" to result)
    }

    @Tool(
        description = "Kontrol notifikasi: list semua, list per app, dismiss, reply ke notifikasi, count. " +
            "Operations: list (semua notifikasi), list_app (per package), count (jumlah), " +
            "dismiss (hapus satu/semua), dismiss_all, reply (balas notifikasi WA/Telegram/dll)."
    )
    fun notificationControl(
        @ToolParam(description = "Operation: list, list_app, count, dismiss, dismiss_all, reply, action") operation: String,
        @ToolParam(description = "Package name app (untuk list_app/dismiss per app)") packageName: String = "",
        @ToolParam(description = "Notification key (untuk dismiss/reply spesifik, dapat dari list)") notificationKey: String = "",
        @ToolParam(description = "Text reply (untuk reply) atau action title (untuk action)") replyText: String = ""
    ): Map<String, String> {
        val result = runBlocking {
            onAction(NotificationAction(operation = operation, packageName = packageName,
                notificationKey = notificationKey, replyText = replyText))
        }
        return mapOf("result" to result)
    }

    @Tool(
        description = "Query lokasi: status GPS, lokasi sekarang, toggle GPS. " +
            "Operations: status (status GPS & provider), current (koordinat + alamat sekarang), " +
            "gps_on/gps_off (toggle GPS, butuh Shizuku)."
    )
    fun locationQuery(
        @ToolParam(description = "Operation: status, current, gps_on, gps_off, high_accuracy, battery_saving") operation: String,
        @ToolParam(description = "Value tambahan") value: String = ""
    ): Map<String, String> {
        val result = runBlocking { onAction(LocationAction(operation = operation, value = value)) }
        return mapOf("result" to result)
    }

    @Tool(
        description = "Info device lengkap: identitas HP, CPU/GPU, RAM, storage, jaringan, sensor, OS, layar. " +
            "Target: identity (merek/model), cpu (chipset/cores), gpu, memory (RAM), " +
            "storage (penyimpanan), network (koneksi), sensors (daftar sensor), " +
            "display (resolusi/DPI), os (versi Android/kernel), all (semua info)."
    )
    fun deviceInfo(
        @ToolParam(description = "Target: identity, cpu, gpu, memory, storage, network, sensors, display, os, all") target: String
    ): Map<String, String> {
        val result = runBlocking { onAction(DeviceInfoAction(target = target)) }
        return mapOf("result" to result)
    }

    @Tool(
        description = "Jadwalkan tugas otomatis. Buat, list, atau batalkan scheduled task. " +
            "Gunakan untuk: 'ingatkan aku jam 7 pagi', 'tiap 30 menit cek notifikasi'. " +
            "Minimum interval 15 menit untuk periodic task."
    )
    fun scheduleTask(
        @ToolParam(description = "Operation: create, list, cancel, cancel_all, status") operation: String,
        @ToolParam(description = "Command yang akan dijalankan (contoh: 'buka WhatsApp')") command: String = "",
        @ToolParam(description = "Waktu delay untuk one-time (contoh: '30m', '2h', '1d')") scheduleTime: String = "",
        @ToolParam(description = "Interval untuk periodic (contoh: '30m', '1h', '1d'). Minimum 15m.") repeatInterval: String = "",
        @ToolParam(description = "Task ID (untuk cancel/status)") taskId: String = ""
    ): Map<String, String> {
        val result = runBlocking {
            onAction(ScheduleAction(operation = operation, command = command,
                scheduleTime = scheduleTime, repeatInterval = repeatInterval, taskId = taskId))
        }
        return mapOf("result" to result)
    }

    @Tool(
        description = "Kelola kontak: tambah, edit, hapus, cari kontak. " +
            "Add: name + phone wajib. Edit: contactId wajib (dapatkan dari search). " +
            "Delete: contactId atau name."
    )
    fun contactManage(
        @ToolParam(description = "Operation: add, edit, delete, search") operation: String,
        @ToolParam(description = "Nama kontak") name: String = "",
        @ToolParam(description = "Nomor telepon (contoh: 08123456789)") phone: String = "",
        @ToolParam(description = "Email (opsional)") email: String = "",
        @ToolParam(description = "Contact ID (untuk edit/delete, dapatkan dari search)") contactId: String = ""
    ): Map<String, String> {
        val result = runBlocking {
            onAction(ContactWriteAction(operation = operation, name = name,
                phone = phone, email = email, contactId = contactId))
        }
        return mapOf("result" to result)
    }
}
