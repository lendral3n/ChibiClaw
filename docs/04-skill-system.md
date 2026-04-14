# 04 — Skill System

## Konsep

Setiap kemampuan agent didefinisikan sebagai file JSON di `assets/skills/`. Gemma membaca skill definition sebagai bagian prompt, lalu function calling untuk eksekusi.

Diagram: `diagrams/skill-system.txt`

## Format Skill File

```json
{
  "id": "whatsapp_messaging",
  "name": "WhatsApp Messaging",
  "description": "Kirim dan baca pesan WhatsApp",
  "version": "1.0.0",
  "target_package": "com.whatsapp",
  "severity": "MEDIUM",
  "triggers": ["kirim wa", "balas wa", "whatsapp", "wa ke", "chat wa"],
  "capabilities": [
    {
      "name": "send_message",
      "description": "Kirim pesan WA ke kontak",
      "preferred_execution": "intent",
      "steps": [
        { "tool": "content_query", "params": { "provider": "contacts", "query": "{contact}" } },
        { "tool": "intent_send", "params": { "action": "ACTION_VIEW", "uri": "wa.me/{phone}?text={msg}", "package": "com.whatsapp" } },
        { "tool": "ui_interact", "params": { "action": "click", "target": "send_button" }, "wait_before": 2 }
      ]
    }
  ]
}
```

## Built-in Skills (17 skills)

| ID | Deskripsi | Tier Utama |
|----|-----------|------------|
| phone_call | Telepon | Tier 1 |
| sms_messaging | Kirim/baca SMS | Tier 1+2 |
| whatsapp_messaging | WA messaging | Tier 1+3 |
| telegram_messaging | Telegram | Tier 1+3 |
| email_compose | Tulis email | Tier 1 |
| calendar_manage | Buat/baca jadwal | Tier 1+2 |
| alarm_timer | Set alarm/timer | Tier 1 |
| app_launcher | Buka/tutup app | Tier 1+4 |
| wifi_control | Toggle WiFi | Tier 2 |
| bluetooth_control | Toggle Bluetooth | Tier 2 |
| volume_control | Atur volume | Tier 2 |
| brightness_control | Atur kecerahan | Tier 2 |
| camera_capture | Ambil foto | Tier 1 |
| navigation | Maps/navigasi | Tier 1 |
| web_search | Cari di browser | Tier 1 |
| file_manager | Kelola file | Tier 1+2 |
| system_info | Info baterai dll | Tier 2 |

## Custom Skills

User bisa buat skill lewat Skill Editor:
```json
{
  "id": "morning_routine",
  "name": "Rutinitas Pagi",
  "triggers": ["rutinitas pagi", "morning routine"],
  "severity": "LOW",
  "capabilities": [{
    "name": "run",
    "steps": [
      { "tool": "intent_send", "params": { "action": "ACTION_VIEW", "uri": "https://news.google.com" } },
      { "tool": "wait", "params": { "seconds": 3 } },
      { "tool": "intent_send", "params": { "action": "ACTION_VIEW", "uri": "https://weather.google.com" } }
    ]
  }]
}
```

## Loading Strategy

Tidak semua skill di-load ke prompt (boros token):
1. E2B classify intent → tentukan skill yang relevan
2. Hanya skill relevan di-load ke context E4B
3. Jika tidak ada match → Gemma plan dari function primitives

