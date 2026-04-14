# 02 — Integrasi Gemma 4 On-Device

## Kenapa Gemma-Only

| Aspek | Cloud AI | Gemma On-Device |
|-------|----------|-----------------|
| Biaya | Per-token, tidak terkontrol | Rp 0 selamanya |
| Latency | 500ms-2s (network) | 100-500ms (local) |
| Privacy | Data ke server | Data di device |
| Offline | Tidak bisa | 100% offline |

## Model yang Digunakan

### Gemma 4 E2B (Fast)
- **Ukuran**: ~1.3GB (Q4_K_M)
- **RAM**: < 1.5GB
- **Speed**: ~20-30 tok/s flagship, ~10-15 mid-range
- **Tugas**: Intent classification, simple parsing
- **Status**: Selalu loaded saat service aktif

### Gemma 4 E4B (Smart)
- **Ukuran**: ~2.5GB (Q4_K_M)
- **RAM**: ~2.5GB
- **Speed**: ~7-15 tok/s flagship
- **Tugas**: Complex reasoning, planning, vision, function calling
- **Status**: Loaded on-demand, unload setelah idle 5 menit

Diagram: `diagrams/gemma-model-routing.txt`

## Lifecycle

```
App Start → Load E2B (selalu siap)
Perintah sederhana → E2B handles langsung
Perintah kompleks → Load E4B → Process → Idle 5min → Unload E4B
Battery < 15% → E2B only mode
Device idle > 30min → Unload semua
```

## Runtime

| Runtime | Kegunaan |
|---------|----------|
| **LiteRT-LM** (primary) | Quantization, constrained decoding, GPU accel |
| **ML Kit GenAI** (secondary) | AICore devices (Pixel, Samsung flagship) |
| **llama.cpp** (fallback) | CPU-only, universal compatibility |

## Function Calling Schema

```json
{
  "tools": [
    {
      "name": "intent_send",
      "description": "Kirim Intent API ke app",
      "parameters": { "action": "string", "package": "string", "uri": "string", "extras": "object" }
    },
    {
      "name": "content_query",
      "description": "Query Content Provider",
      "parameters": { "provider": "string", "query": "string", "limit": "number" }
    },
    {
      "name": "ui_interact",
      "description": "Interaksi UI via Accessibility",
      "parameters": { "action": "string", "target": "string", "text": "string", "coordinates": "object" }
    },
    {
      "name": "shell_exec",
      "description": "Shell command via Shizuku (HIGH severity)",
      "parameters": { "command": "string" }
    },
    {
      "name": "scan_ui",
      "description": "Scan layar untuk UI map",
      "parameters": { "method": "string (accessibility/screenshot/both)" }
    },
    {
      "name": "memory_query",
      "description": "Cari di long-term memory",
      "parameters": { "query": "string", "scope": "string" }
    },
    {
      "name": "ask_user",
      "description": "Minta konfirmasi/info dari user",
      "parameters": { "question": "string", "options": "array" }
    },
    {
      "name": "wait",
      "description": "Tunggu N detik",
      "parameters": { "seconds": "number" }
    },
    {
      "name": "report",
      "description": "Laporkan hasil ke user",
      "parameters": { "message": "string", "status": "string" }
    }
  ]
}
```

## Prompt Architecture

```
[System] Kamu adalah {persona}, asisten virtual Android.
         Selalu gunakan intent_send sebagai pilihan pertama.
         Gunakan ui_interact hanya jika intent tidak tersedia.
         Gunakan shell_exec hanya sebagai pilihan terakhir.
[Skills] {loaded_skill_definitions}
[Memory] {relevant_memory}
[UI State] {ui_map — hanya jika sudah di-scan}
[User] {perintah}
```

## Contoh Thinking Mode

```
User: "Balas WA Budi bilang Otw"

Gemma Think:
- Intent = kirim pesan WhatsApp
- Cara efisien: deep link wa.me + 1x accessibility click send
- Perlu nomor Budi dari kontak dulu

Plan:
1. content_query(contacts, "Budi") → phone_number
2. intent_send(ACTION_VIEW, "wa.me/{phone}?text=Otw", "com.whatsapp")
3. wait(2)
4. scan_ui(accessibility) → find send button
5. ui_interact(click, "send_button")
6. report("Pesan Otw sudah dikirim ke Budi", "success")
```
