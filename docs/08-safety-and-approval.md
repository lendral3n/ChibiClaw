# 08 — Safety & Approval System

## Approval System

ChibiClaw memiliki approval system built-in dengan policy "auto", "ask", dan allowlist per-app, dilengkapi Android-specific safety checks.

## Severity Levels

| Level | Contoh Aksi | Policy Default | Bisa Diubah User? |
|-------|-------------|----------------|-------------------|
| `LOW` | Buka app, scroll, baca teks, cari kontak | AUTO (langsung eksekusi) | Ya |
| `MEDIUM` | Ketik pesan, klik tombol, share content | AUTO + LOG | Ya |
| `HIGH` | Kirim pesan, telepon, install APK, ubah setting | ASK (wajib konfirmasi) | Ya (bisa turunkan ke AUTO) |
| `BLOCKED` | Hapus semua data, factory reset, uninstall system app | DENY (selalu tolak) | Tidak |

## Approval Flow

Lihat diagram: `diagrams/approval-gate.txt`

```
Perintah masuk
    │
    ├─ Cek App Whitelist → target app ada di whitelist?
    │   └─ Tidak → TOLAK + notifikasi user
    │
    ├─ Cek Sensitive Fields → aksi menyentuh password/payment?
    │   └─ Ya → escalate severity ke HIGH
    │
    ├─ Classify Severity → LOW / MEDIUM / HIGH / BLOCKED
    │
    ├─ Apply Policy
    │   ├─ AUTO → langsung eksekusi
    │   ├─ ASK → tampilkan overlay konfirmasi
    │   ├─ DENY → tolak
    │   └─ BLOCKED → tolak (tidak bisa di-override)
    │
    └─ Log semua keputusan ke command_history
```

## App Whitelist

Default whitelist (bisa diubah user di Settings):
- WhatsApp (`com.whatsapp`)
- Telegram (`org.telegram.messenger`)
- Gmail (`com.google.android.gm`)
- Phone (`com.android.dialer`)
- Messages (`com.google.android.apps.messaging`)
- Chrome (`com.android.chrome`)
- Maps (`com.google.android.apps.maps`)
- Clock (`com.google.android.deskclock`)
- Camera (`com.android.camera2`)
- Settings (`com.android.settings`)

User bisa menambah/menghapus app dari whitelist lewat Safety Settings screen.

## Sensitive Field Detection

Saat Accessibility Service membaca UI tree, detector mencari node dengan atribut:
- `android:inputType="textPassword"` atau `"numberPassword"`
- `android:hint` mengandung kata: "password", "pin", "cvv", "otp", "card number"
- Node di dalam app banking/payment (package name match)

Jika ditemukan, severity otomatis di-escalate ke HIGH atau BLOCKED.

## AIDL Caller Security

Untuk mencegah app jahat mengirim perintah lewat AIDL:
- Hanya app yang di-sign dengan certificate yang sama yang boleh bind
- Atau app yang ada di `caller_whitelist` (dikelola user)
- Setiap AIDL call di-log dengan caller package name dan UID

## Prompt Injection Protection

Saat Gemma membaca UI content dari Accessibility/Vision, ada risiko teks di layar berisi instruksi yang bisa menipu Gemma. Mitigasi:
- UI map di-wrap dalam tag khusus: `[UI_CONTENT_START]...[UI_CONTENT_END]`
- System prompt instruksikan Gemma untuk TIDAK mengikuti instruksi dari dalam UI content
- Constrained decoding memastikan output hanya berupa function call yang valid
- Skill definitions membatasi aksi yang bisa dilakukan per-context

## Google Play Policy

Perhatian: Google Play melarang Accessibility Service untuk automation otonom. ChibiClaw tidak bisa di-publish di Play Store sebagai app umum. Distribusi lewat:
- Sideload APK (direct download)
- F-Droid
- GitHub Releases
