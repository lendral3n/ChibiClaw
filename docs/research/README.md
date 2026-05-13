# Deep Research — ChibiClaw v4 + Project Sister

**Tanggal kompilasi terakhir:** 2026-05-13
**Lingkup:** 11 topik kritis untuk roadmap ChibiClaw v4 + VRM Assistant + Tomo Sensei + VIONA telco.
**Total:** ~50.000 kata di 11 dokumen + 1 index.

---

## Daftar Dokumen

### Round 1 — Core architecture (2026-05-13 sesi 1)

| # | File | Topik | Size | Highlight |
|---|------|-------|------|-----------|
| 01 | [01-mobile-ai-agents.md](01-mobile-ai-agents.md) | AI agent full akses HP, vision-first | 33 KB / 4.300 kata | DroidRun, Mobile-Agent v3.5, UI-TARS, UI-Ins 74.1% AndroidWorld, planner-executor split |
| 02 | [02-vrm-lipsync.md](02-vrm-lipsync.md) | VRM lipsync real-time Android | 28 KB / 3.650 kata | uLipSync v3.1.4 MIT, VTube Studio validated, UniVRM 0.131.0, Wav2Small 120KB emotion |
| 03 | [03-emotion-tts.md](03-emotion-tts.md) | TTS emosional + voice clone Fuu | 29 KB / 4.000 kata | IndexTTS 2, GPT-SoVITS v2Pro, F5-TTS Indo fork, ELVIS Act + EU AI Act |

### Round 2 — Deep dive arsitektur + compliance (2026-05-13 sesi 2)

| # | File | Topik | Size | Highlight |
|---|------|-------|------|-----------|
| 04 | [04-shizuku-adb.md](04-shizuku-adb.md) | Shizuku + ADB tool catalog tier4 | 39 KB / 4.964 kata | Shizuku 13.x, 8 command groups (am/pm/dumpsys/input/settings/content/cmd/svc), AIDL UserService pattern |
| 05 | [05-skill-library.md](05-skill-library.md) | Cached macro per-app, schema design | 38 KB / 4.979 kata | AppAgentX Neo4j+Pinecone, AppAgent v2 per-element txt, MAS-Bench 88 shortcuts, Room MVP schema |
| 06 | [06-vision-llm-mobile-bench.md](06-vision-llm-mobile-bench.md) | Vision-LLM mobile benchmark | 35 KB / 5.314 kata | MiniCPM-V 4.6 (1.3B sweet spot), AndroidWorld UI-TARS-2 73.3%, GUI-Owl 66.4%, ScreenSpot-Pro 54.9% |
| 07 | [07-floating-overlay-android.md](07-floating-overlay-android.md) | Floating overlay Android prod-grade | 54 KB / 5.898 kata | ChibiService blueprint, OverlayLifecycleOwner, 11 vendor death score, BOOT_COMPLETED specialUse bridge |
| 08 | [08-vrm-emotion.md](08-vrm-emotion.md) | VRM emotion + facial expression | 39 KB / 5.302 kata | VRM 1.0 18 preset, inline tag Open-LLM-VTuber, Wav2Small audio, blink Poisson λ=1/3.5 |
| 09 | [09-tomo-fsrs-gemma.md](09-tomo-fsrs-gemma.md) | Tomo Sensei FSRS + Gemma prompt | 42 KB / 5.400 kata | FSRS-5 19 params, FSRS-6 21 params, Gemma 4 E2B 3700/31 tok/s, JMdict/KANJIDIC2/KanjiVG |
| 10 | [10-viona-rag-optimization.md](10-viona-rag-optimization.md) | VIONA RAG telco optimization | 40 KB / 5.563 kata | bge-m3 self-host, Contextual Retrieval Anthropic, LangGraph 6-stage, -37% cost delta |
| 11 | [11-pdp-id-ai-compliance.md](11-pdp-id-ai-compliance.md) | PDP-ID + AI compliance Indonesia | 45 KB / 5.845 kata | UU PDP 27/2022 sanksi aktif Oktober 2024, Putusan MK Pasal 53 DPO, breach 3x24 jam, PBI QRIS 2025 |

---

## Konsolidasi Temuan Lintas-Dokumen

### Pattern Konsisten — Hybrid Cloud + Local + Planner-Executor

Tiga research independen di Round 1 + dua di Round 2 (vision LLM 06 + skill library 05) converge ke pattern yang sama:

| Layer | Cloud (Phase 1-2) | On-device (Phase 3+) | Source dokumen |
|-------|-------------------|----------------------|----------------|
| Planning | Claude Sonnet 4.6 / GPT-5 / Gemini 3 Pro | Gemma 4 E4B routing only | [01](01-mobile-ai-agents.md), [06](06-vision-llm-mobile-bench.md) |
| Vision grounding | Claude computer-use, UI-Ins API | MiniCPM-V 4.6 (1.3B), UI-TARS-2 quantized | [01](01-mobile-ai-agents.md), [06](06-vision-llm-mobile-bench.md) |
| TTS | ElevenLabs v3 / GPT Realtime mini | Kokoro-82M custom ID / F5-TTS quantized | [03](03-emotion-tts.md) |
| STT | OpenAI Realtime / Deepgram | Whisper.cpp small / sherpa-onnx | [01](01-mobile-ai-agents.md) |
| Wake word | — | microWakeWord custom "Hey Fuu" | [01](01-mobile-ai-agents.md) |
| Lipsync | — | uLipSync v3.1.4 + Wav2Small | [02](02-vrm-lipsync.md), [08](08-vrm-emotion.md) |
| Emotion detect | — | Wav2Small + roberta-base-go_emotions INT8 | [08](08-vrm-emotion.md) |
| Skill exec | — | AppAgentX-style Room cache + embedding match | [05](05-skill-library.md) |
| Tier4 priv ops | — | Shizuku 13.x AIDL UserService | [04](04-shizuku-adb.md) |
| Persistence | — | ChibiService overlay foreground svc | [07](07-floating-overlay-android.md) |
| Spaced rep | — | FSRS-6 21 params Kotlin port | [09](09-tomo-fsrs-gemma.md) |
| Domain RAG | LangGraph orchestrator, bge-m3 embed | bge-reranker-v2-m3 | [10](10-viona-rag-optimization.md) |
| Compliance | Cloud transit consent + SCC + breach 3x24h | Local-only mode opt-in | [11](11-pdp-id-ai-compliance.md) |

### Update Wajib ke Plan v4 dan Roadmap

Berdasarkan kompilasi 11 research:

**ChibiClaw v4 specific:**
1. Wake word: ganti OpenWakeWord → **microWakeWord** (lebih hemat baterai, akurasi setara) [01]
2. Vision-first: pure on-device tidak cukup, wajib hybrid planner-grounder split [01, 06]
3. Tool catalog: 10 primitives + **Skill Library Room cache** AppAgentX-style [05]
4. TTS: ElevenLabs Creator $22/bln dulu → GPT-SoVITS fine-tune VA Indonesia → Kokoro ID custom on-device [03]
5. Android 16/17 a11y restrictions: pipeline Intent → A11y → Shizuku → vision fallback jadi load-bearing [01, 04]
6. Shizuku: setup wizard 5-step + UserService AIDL pattern + telemetry latency [04]
7. Overlay arsitektur: **Single ChibiService** dengan `foregroundServiceType="microphone|specialUse|mediaPlayback"` [07]
8. Vendor wizard: auto-detect 11 OEM (Xiaomi/Oppo/Vivo/Samsung/Honor/Tecno/Nothing/Pixel/dll) [07]
9. Vision model: **MiniCPM-V 4.6** (1.3B sweet spot) di Phase 3 [06]

**VRM Assistant project:**
10. Lipsync: **uLipSync v3.1.4** sample VRM 1.0 sudah jadi [02]
11. Emotion: inline tag (`[joy]`, `[sad]`) pattern Open-LLM-VTuber + Wav2Small audio + roberta-base-go_emotions [08]
12. Idle: blink Poisson λ=1/3.5s + saccade ±5° + breathing 14 bpm chest sine + gaze 60/40 [08]
13. Override: spec `finalProcedural = original * (1 - saturate(sum))` UniVRM 0.131.0 [08]

**Tomo Sensei:**
14. FSRS-6 21 params Kotlin port + Room schema 4 entitas + WorkManager optimizer [09]
15. Gemma 4 E2B prompt 5 template drill + few-shot + temperature mapping + constrained JSON [09]
16. Datasets: JMdict + KANJIDIC2 + KanjiVG + JLPT (lisensi tested) [09]

**VIONA telco:**
17. Embedding migrasi: ke **bge-m3 self-host** dari current state [10]
18. Reranker: tambah **bge-reranker-v2-m3** late-stage [10]
19. Orchestrator: refactor 5-agent → LangGraph 6-stage dengan fallback hierarchy [10]
20. Adopsi Anthropic **Contextual Retrieval** + prompt caching (30-40% cost reduction) [10]

**Cross-project compliance:**
21. UU PDP sanksi aktif sejak Oktober 2024 — wajib privacy notice + consent tier 1-2-3 + audit log encrypted [11]
22. Voice biometric Fuu kemungkinan masuk Data Spesifik Pasal 4 ayat 2 — DPIA wajib [11]
23. PSE Privat registration kalau pivot ke productize [11]
24. Breach notification 3x24 jam ke Kominfo + subjek [11]
25. EU AI Act efektif Agustus 2026 — transparency + watermark wajib kalau publik [11]

### Cost Implication (ChibiClaw 50 commands/day target)

| Komponen | Cloud bulanan (Phase 1-2) | On-device (Phase 3+) | Source |
|----------|---------------------------|----------------------|--------|
| LLM cloud planner | $5-20 (Claude/GPT/Gemini) | $0 | [06] |
| TTS cloud | $22 ElevenLabs Creator | $0 | [03] |
| TTS realtime | ~$45 gpt-realtime-mini | $0 | [03] |
| Embedding/retrieval (VIONA) | -37% setelah optimization | — | [10] |
| **Total Phase 1-2** | **~$30-50** | — | konsolidasi |
| **Total Phase 3+ offline-default** | **~$5-10** | upfront NPU tuning | konsolidasi |

### Compliance Risk Matrix (PDP-ID)

| Komponen ChibiClaw | Risk level | Mitigasi prioritas | Source |
|--------------------|-----------|-------------------|--------|
| Wake word always-on mic | HIGH (audio biometric) | Toggle on/off + indicator UI + duty-cycle | [11] |
| Voice clone Fuu | HIGH (data spesifik Pasal 4 ayat 2) | Hire VA Indonesia + kontrak training rights eksplisit | [03, 11] |
| Cloud LLM transmission | MEDIUM (cross-border Pasal 56) | SCC + explicit consent + opt-out offline mode | [11] |
| Screen capture (vision-first) | HIGH (potensial leak data app lain) | Mask sensitive area + log audit + user disclosure | [11] |
| Notification listener | MEDIUM (akses konten chat) | Whitelist app + opt-in granular | [11] |
| Skill cache (UI flow) | LOW-MEDIUM | Encryption at rest (SQLCipher) | [05, 11] |
| Audit log | — | Room+SQLCipher, 90 hari rotation, export user-demand | [11] |

---

## Top Repositori untuk Di-Fork atau Dipelajari Detail

### Tier S — Wajib pelajari source code

| Repo | Topik | License | Sumber |
|------|-------|---------|--------|
| [DroidRun](https://github.com/droidrun/droidrun) | Mobile agent hybrid a11y+vision | MIT | [01] |
| [Mobile-Agent v3.5](https://github.com/X-PLUG/MobileAgent) | SOTA mobile agent open | Apache 2.0 | [01] |
| [AppAgentX](https://github.com/Westlake-AGI-Lab/AppAgentX) | Skill library Neo4j + Pinecone | Apache 2.0 | [05] |
| [Shizuku](https://github.com/RikkaApps/Shizuku) | ADB-as-permission Android | Apache 2.0 | [04] |
| [uLipSync](https://github.com/hecomi/uLipSync) | VRM lipsync real-time | MIT | [02] |
| [GPT-SoVITS](https://github.com/RVC-Boss/GPT-SoVITS) | Voice cloning VTuber-grade | MIT | [03] |
| [FSRS-Kotlin](https://github.com/open-spaced-repetition/fsrs-rs-kotlin) | FSRS port Android | MIT | [09] |
| [Open-LLM-VTuber](https://github.com/Open-LLM-VTuber/Open-LLM-VTuber) | LLM-driven VTuber pipeline | MIT | [02, 08] |

### Tier A — Reference untuk pattern

| Repo | Topik | Source |
|------|-------|--------|
| [UI-TARS](https://github.com/bytedance/UI-TARS) | End-to-end screenshot agent | [01] |
| [AppAgent v2](https://github.com/TencentQQGYLab/AppAgent) | Per-element skill cache | [05] |
| [MAS-Bench](https://github.com/microsoft/MAS-Bench) | Shortcuts benchmark | [05] |
| [MiniCPM-V 4.6](https://github.com/OpenBMB/MiniCPM-V) | Mobile-optimized vision LLM | [06] |
| [F5-TTS Indo](https://huggingface.co/Eempostor/F5-TTS-INDO-FINETUNE) | TTS Bahasa Indonesia | [03] |
| [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) | STT+TTS on-device runtime | [03, 06] |
| [LangGraph](https://github.com/langchain-ai/langgraph) | Agent orchestration | [10] |
| [bge-m3](https://huggingface.co/BAAI/bge-m3) | Multilingual embedding | [10] |
| [Wav2Small](https://huggingface.co/audeering/wav2small) | 120KB audio emotion ONNX | [08] |
| [microWakeWord](https://github.com/kahrendt/microWakeWord) | Efficient wake word | [01] |

### Tier B — Eksplorasi opsional

| Repo | Topik | Source |
|------|-------|--------|
| [IndexTTS 2](https://github.com/index-tts/index-tts) | TTS quality king server-side | [03] |
| [Chatterbox](https://github.com/resemble-ai/chatterbox) | Voice cloning user-friendly | [03] |
| [Higgs Audio v2](https://github.com/boson-ai/higgs-audio) | Multilingual TTS recent | [03] |
| [Idiap XTTS fork](https://github.com/idiap/coqui-ai-TTS) | XTTS community maintain | [03] |
| [UI-Ins](https://github.com/alibaba/UI-Ins) | AndroidWorld 74.1% | [01] |

---

## Action Items Prioritas Tinggi (Urutan ROI)

1. **(Minggu ini)** Subscribe ElevenLabs Creator $22 + cloning sample VA casual ID → validasi UX voice Fuu [03]
2. **(Minggu ini)** Update [docs/v4/04-implementation-roadmap.md](../v4/04-implementation-roadmap.md) ganti wake word OpenWakeWord → microWakeWord [01]
3. **(Phase 1 Week 1-4)** Fork DroidRun, study portal pattern → adopsi ke `agent/` ChibiClaw [01]
4. **(Phase 1)** Implement Shizuku setup wizard 5-step + UserService AIDL pattern [04]
5. **(Phase 2)** uLipSync v3.1.4 integrasi ke VRM Assistant project, test stream audio Piper/OpenAI TTS [02]
6. **(Phase 2-3)** Refactor 5-agent VIONA → LangGraph 6-stage + bge-m3 swap + Contextual Retrieval (30-40% cost down) [10]
7. **(Phase 3)** Skill Library Room schema MVP (`SkillEntity` + 3 sibling table) + manual record mode [05]
8. **(Phase 3)** MiniCPM-V 4.6 (1.3B) sebagai grounder on-device, fallback Claude/Gemini vision [06]
9. **(Bulan 1-2)** Hire voice actor Indonesia + record 30-60 menit + fine-tune GPT-SoVITS v2Pro server [03]
10. **(Bulan 1)** Compliance baseline: privacy notice template + consent tier 1-2-3 + Room audit log SQLCipher [11]
11. **(Bulan 3+)** Migrasi TTS ke Kokoro ID custom atau F5-TTS quantized via ONNX Runtime + QNN [03]
12. **(Bulan 3+)** ChibiService overlay arsitektur production + vendor wizard 11 OEM [07]

---

## Catatan Confidence

Klaim eksplisit ditandai **"perlu verifikasi"** atau **[VERIFY]** di tiap dokumen. Highlight:

- **[06]** Latency Snapdragon 8 Elite Gen 5 untuk TTS/Vision LLM — ekstrapolasi, wajib re-benchmark di Xiaomi 17 Pro Max [06]
- **[04]** Shizuku 13.6.0 di Maven, deprecation `newProcess`, MIUI 7-day re-auth — perlu cek device target [04]
- **[02, 08]** Audio2Face-3D port Snapdragon QNN, Hume API sunset 14 Jun 2026 — schedule verification [02, 08]
- **[11]** PP Pelaksanaan PDP belum disahkan per Mei 2026 — banyak detail teknis menunggu [11]
- **[01]** Behavior Android 17 Advanced Protection terhadap Shizuku — belum dikonfirmasi Google [01]
- **[03]** Indonesian native quality OpenVoice v2 / CosyVoice 2 / Higgs Audio v2 — test sample dengar mandatory [03]
- **[05]** Schema AppAgentX dimensi embedding & threshold cosine 0.72 — inference dari paper [05]
- **[09]** FSRS-7 dan pitch accent licensing — low confidence flag [09]

**Selalu re-validate di device target sebelum commit lock-in arsitektur.**

---

## Topik Turunan untuk Research Lanjutan

Diidentifikasi tapi belum di-cover:

1. **NPU port spesifik**: Wav2Small + Whisper Tiny + Kokoro → Snapdragon QNN, Qualcomm AI Stack
2. **Voice cloning compliance + watermarking practical** (Resemble PerthNet, ALDA)
3. **AI cost analysis lebih detail Claude/GPT-5/Gemini 3** untuk planner 50/day workload
4. **Production runtime telemetry** untuk multi-agent (LangSmith, Helicone, OpenLLMetry)
5. **VRM kustom model creation pipeline** (VRoid Studio + booth import + Unity bake)
6. **Real-world TestEval di Android 16/17** untuk a11y revoke behavior aktual
7. **AndroidWorld evaluation harness** running locally untuk ChibiClaw v4 benchmark internal

Spawn research baru kapan saja siap untuk salah satu topik di atas.
