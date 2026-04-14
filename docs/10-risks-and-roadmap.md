# 10 — Risks, Limitations & Roadmap

## Risiko Teknis

| Risiko | Impact | Mitigasi |
|--------|--------|----------|
| Gemma reasoning quality tidak cukup untuk task 10+ steps | Task kompleks gagal | Max 20 steps per task, fallback ke ask_user jika confidence rendah |
| Memory pressure (model + service + DB = 3-4GB RAM) | App lain di-kill di HP 4GB | Aggressive model unloading, lazy loading E4B |
| Battery drain (model loaded terus) | User uninstall | Idle timeout 5 menit, battery-aware mode (disable saat < 15%) |
| Accessibility Service di-kill OEM (Xiaomi, Huawei, Samsung) | ChibiClaw mati diam-diam | Auto-restart mechanism, user guide disable battery optimization |
| Cold start Gemma model 3-5 detik | Perintah pertama terasa lambat | Pre-load E2B saat boot, warm-up inference |
| Intent API tidak bisa auto-send di WhatsApp | Butuh Accessibility untuk klik Send | Hybrid: Intent pre-fill + 1x Accessibility click |

## Risiko UX

| Risiko | Impact | Mitigasi |
|--------|--------|----------|
| First-time setup: download 2.5GB + 5 permissions | User abandon | Progressive setup, mulai E2B (1.3GB), suggest upgrade kemudian |
| Inference latency 3-5s untuk planning | Terasa lambat untuk task sederhana | Cache common patterns, skip reasoning untuk known tasks |
| Shizuku setup terlalu teknis | User biasa tidak bisa setup Tier 4 | Tier 4 opsional, semua fitur utama jalan tanpa Shizuku |

## Risiko Keamanan

| Risiko | Impact | Mitigasi |
|--------|--------|----------|
| Malicious AIDL caller | App jahat kirim perintah | Certificate-based verification, caller whitelist |
| Prompt injection via UI content | Gemma salah eksekusi | UI content tagging, constrained decoding, skill boundaries |
| User grant terlalu banyak permission | Privacy concern | Progressive permission request, penjelasan per-permission |

## Limitasi yang Tidak Bisa Dihindari

1. **Google Play Store**: ChibiClaw tidak bisa di-publish karena policy Accessibility Service
2. **WhatsApp auto-send**: Intent API hanya pre-fill, butuh 1x click via Accessibility
3. **Game/Canvas apps**: UI tree kosong, harus pakai vision (lebih lambat)
4. **Background execution**: Android agresif membunuh background service, perlu fight battery optimization
5. **Model size**: Minimal 1.3GB storage untuk E2B, 2.5GB untuk E4B

## Development Roadmap

### Phase 1: MVP (4-6 minggu)
- [ ] Gemma E4B integration via LiteRT-LM
- [ ] Command Gateway (AIDL + voice)
- [ ] Intent executor (Tier 1) — phone call, SMS, deep links
- [ ] Content Provider executor (Tier 2) — contacts, calendar
- [ ] Basic Accessibility executor (Tier 3) — click, type
- [ ] 5 built-in skills: phone, sms, whatsapp, alarm, app_launcher
- [ ] Basic state machine (IDLE → PLANNING → EXECUTING → COMPLETED)
- [ ] Dashboard UI

### Phase 2: Safety + Polish (3-4 minggu)
- [ ] Approval Gate (whitelist, severity, confirmation overlay)
- [ ] Error recovery engine
- [ ] Timeout guard + kill switch
- [ ] Floating overlay status
- [ ] Command history logging
- [ ] Full state machine (7 states)
- [ ] 10 more built-in skills

### Phase 3: Advanced (4-6 minggu)
- [ ] Vision fallback (screenshot + Gemma vision)
- [ ] Notification listener + auto-response
- [ ] App pattern caching (learn UI patterns)
- [ ] Gemma E2B integration (dual model routing)
- [ ] Cron scheduler (proactive tasks)
- [ ] Custom skill editor UI
- [ ] Voice engine (Gemma native audio)

### Phase 4: Optimization (ongoing)
- [ ] Battery optimization + idle unloading
- [ ] Fine-tune Gemma untuk ChibiClaw tasks
- [ ] Benchmark on 10+ target devices
- [ ] Skill sharing / community skills
- [ ] Multi-language support
