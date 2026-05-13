# Research 01 — Mobile AI Agents (Vision-First + Multi-Step Automation)

> **Konteks**: Riset untuk ChibiClaw v4 (Android AI assistant, Kotlin Compose, vision-first untuk app blacklist accessibility).
> **Penulis**: research agent (Claude Opus 4.7)
> **Tanggal dibuat**: 2026-05-13
> **Tanggal akses sumber**: 2026-05-13
> **Status**: Draft 1 — pre-implementation reference

---

## Executive Summary

State-of-the-art (SOTA) mobile AI agent per Mei 2026 sudah bergeser jauh dari pendekatan accessibility-tree-only menuju **vision-first end-to-end VLM (Vision-Language Model)** seperti `UI-TARS-2` (ByteDance), `Mobile-Agent-v3.5` + `GUI-Owl-1.5` (Alibaba), dan `UI-Ins` (Alibaba). Tren penting yang relevan untuk ChibiClaw v4:

1. **Pure-screenshot policy** (no accessibility tree) sudah jadi mainstream — UI-TARS, GUI-Owl, dan ShowUI semua menerima screenshot sebagai satu-satunya input visual. Ini sangat selaras dengan kebutuhan ChibiClaw v4 untuk app blacklist (TikTok/WA/Tokped/Shopee/IG) yang me-mark `accessibilityDataSensitive` di Android 16 dan 17.
2. **Planner-Executor split** (LMM besar sebagai planner, model grounding kecil sebagai executor) menjadi pola dominan. UI-Ins-7B mencapai 74.1% di AndroidWorld sebagai executor dengan planner cloud — pattern ini cocok untuk hybrid routing ChibiClaw (Gemma 4 lokal + Claude/GPT/Gemini cloud).
3. **AndroidWorld sudah saturated** (>90% di leaderboard SOTA). Benchmark baru `MobileWorld` (Des 2025, 201 tasks, 27.8 langkah rata-rata) dan `MVISU-Bench` menjadi referensi yang lebih jujur.
4. **Android 16/17 security tightening** terhadap AccessibilityService API jadi argumen kuat untuk arsitektur **vision-first dengan escalation Intent → Accessibility (jika tersedia) → Shizuku → Root**, persis seperti yang sudah ada di rencana ChibiClaw v4.
5. **On-device Gemma 4 E4B (4B effective params, ~2.5 GB Q4) + LiteRT-LM 0.11** sudah feasible di Snapdragon 8 Elite + 16GB RAM untuk task routing/planning, namun **execution grounding (klik koordinat) di app screenshot kompleks** masih kalah jauh dibanding cloud VLM 7B+. Untuk vision-first benar-benar bertenaga, ChibiClaw v4 sebaiknya tetap menyediakan rute cloud-fallback.

---

## 1. Repositori Open-Source yang Wajib Dipelajari

Disusun dari paling relevan ke kurang relevan untuk ChibiClaw v4. Semua data per akses **2026-05-13**.

### 1.1 Ringkasan Tabel

| # | Repo | Stars | Lisensi | Bahasa | Last Activity | Vision-First | Local LLM | Android |
|---|------|-------|---------|--------|---------------|--------------|-----------|---------|
| 1 | [droidrun/droidrun](https://github.com/droidrun/droidrun) | ~10k+ | MIT | Python 96% | 14 Apr 2026 (v0.5.9) | Hybrid (a11y + screenshot) | Ya (Ollama) | Native |
| 2 | [X-PLUG/MobileAgent](https://github.com/X-PLUG/MobileAgent) | 8.7k | MIT | Python 62%/HTML/JS | 31 Mar 2026 (v3.5) | Ya (pure vision di v3+) | Ya (GUI-Owl 2B/4B/8B) | Real-device + Wuying Cloud Phone |
| 3 | [bytedance/UI-TARS](https://github.com/bytedance/UI-TARS) | 27k+ | Apache-2.0 | Python 99% | 4 Sep 2025 (UI-TARS-2) | Ya (screenshot-only) | UI-TARS-1.5-7B open | Via UI-TARS-desktop |
| 4 | [TencentQQGYLab/AppAgent](https://github.com/TencentQQGYLab/AppAgent) + AppAgentX | ~5k | MIT | Python | 5 Mar 2025 (AppAgentX) | Hybrid (XML + screenshot) | Ya (Qwen-VL-Max) | Native (ADB) |
| 5 | [MobileLLM/AutoDroid](https://github.com/MobileLLM/AutoDroid) | ~1k | MIT | Python | MobiCom 2024 | Tidak (UI tree + memory) | Vicuna on-device tested | Native (DroidBot) |
| 6 | [alibaba/UI-Ins](https://github.com/alibaba/UI-Ins) | ~1k+ | Apache-2.0 | Python | 23 Okt 2025 | Ya (grounding) | UI-Ins-7B/32B open | Cross-platform |
| 7 | [niuzaisheng/ScreenAgent](https://github.com/niuzaisheng/ScreenAgent) | ~700 | Apache-2.0 | Python | IJCAI 2024 | Ya | CogAgent | Desktop-first |
| 8 | [showlab/ShowUI](https://github.com/showlab/ShowUI) | ~1.5k | MIT | Python | CVPR 2025 | Ya (2B VLA) | ShowUI-2B open | Cross-platform |
| 9 | [OS-Copilot/OS-Atlas](https://github.com/OS-Copilot/OS-Atlas) | ~1k | MIT | Python | ICLR 2025 | Ya (grounding 4B/7B) | Open weights | Cross-platform |
| 10 | [areu01or00/android-vision-agent](https://github.com/areu01or00/android-vision-agent) | ~300 | MIT | Python | 2025 | Ya (GPT-4o) | Tidak (cloud only) | Native (ADB) |

> Catatan: GitHub stars adalah perkiraan urutan magnitudo, bukan angka eksak. Verifikasi di repo langsung sebelum keputusan akhir.

### 1.2 Deep Dive: Top 5 Repo

#### (1) DroidRun — paling dekat dengan use case ChibiClaw

* **URL**: <https://github.com/droidrun/droidrun>
* **License**: MIT (permissive, fork-friendly)
* **Codebase size**: ~96% Python, ada portal Android (Kotlin) terpisah di `droidrun/portal`
* **Last activity**: v0.5.9 dirilis 14 April 2026 — sangat aktif
* **Technical highlight**:
  1. **LLM-agnostic** — adapter untuk OpenAI, Anthropic, Gemini, Ollama, DeepSeek. Ini exactly pattern multi-model adapter yang ChibiClaw v4 rencanakan.
  2. **Hybrid perception**: accessibility-tree (via portal app yang di-install ke device) + screenshot vision. Saat accessibility blocked, fallback ke vision.
  3. **Self-healing workflow**: retries, fallback memory, error handling — pattern ini layak ditiru di state machine ChibiClaw v4.
  4. Klaim **63% success rate on 116 real-world tasks (AndroidWorld)**, vs baseline ~30%.
* **Kelebihan vs ChibiClaw**: arsitektur sudah matang, ada portal app yang reusable, telemetry pakai Arize Phoenix (production-grade tracing).
* **Kekurangan / showstopper**:
  * Driver utama di Python — perlu adapter Kotlin atau JNI wrapper kalau ChibiClaw v4 mau on-device-only.
  * Portal app harus di-install + grant accessibility, jadi tidak sepenuhnya zero-permission untuk app blacklist.
  * Komersial cloud platform menyertai OSS — perhatikan boundary lisensi kalau ChibiClaw v4 ingin distribusi komersial.

#### (2) Mobile-Agent (X-PLUG / Alibaba) — paling SOTA untuk reasoning

* **URL**: <https://github.com/X-PLUG/MobileAgent>
* **License**: MIT
* **Codebase size**: 412 commits di main; Python 62%, HTML 17%, JS 16%
* **Last activity**: Mobile-Agent-v3.5 deployment di Wuying Cloud Phone, 31 Maret 2026
* **Technical highlight**:
  1. **Multi-agent framework**: Manager, Worker, Reflector, Notetaker — long-horizon planning yang bisa diadopsi parsial ke ChibiClaw v4 (cocok untuk task multi-step).
  2. **GUI-Owl foundation model** (2B/4B/7B/8B/32B/235B) — varian 2B/4B realistis untuk on-device di Snapdragon 8 Elite Gen 5.
  3. **Pure-vision policy** di v3+ — tidak butuh accessibility, sesuai kebutuhan TikTok/WA.
  4. **Self-Evolving Trajectory Production** + Trajectory-aware Relative Policy Optimization (TRPO) untuk training.
* **Performance**: GUI-Owl-7B 66.4 di AndroidWorld; Mobile-Agent-v3 framework boost ke 73.3 (SOTA open-source per Sep 2025).
* **Kelebihan vs ChibiClaw**: model weights open di HuggingFace `mPLUG/GUI-Owl-1.5-2B-Instruct` (16 Feb 2026), bisa di-quantize.
* **Kekurangan**: orchestrator Python-heavy; 2B-instruct masih butuh ~2-3GB RAM aktif setelah Q4 — kompetisi dengan Gemma 4 di slot on-device.

#### (3) UI-TARS / UI-TARS-desktop (ByteDance) — paling clean architecturally

* **URL**: <https://github.com/bytedance/UI-TARS>, <https://github.com/bytedance/UI-TARS-desktop>
* **License**: Apache-2.0
* **Codebase size**: Python 99.8%
* **Last activity**: UI-TARS-2 technical report 4 Sep 2025; aktif di UI-TARS-desktop
* **Technical highlight**:
  1. **Native end-to-end VLM** — perception, reasoning, grounding, memory dalam satu model. Tidak ada pipeline modular.
  2. **Screenshot-only input** — output langsung action (klik X,Y / type / scroll). Generalize across Windows/macOS/Linux/Android/web.
  3. UI-TARS-1.5-7B **open-sourced** (HuggingFace `ByteDance-Seed/UI-TARS-1.5-7B`). UI-TARS-2 (Sep 2025) masih research access (TARS@bytedance.com).
  4. SOTA 24.6@50 di OSWorld dan **46.6 di AndroidWorld** (UI-TARS-1.5, April 2025).
* **Kelebihan vs ChibiClaw**: arsitektur paling sederhana untuk ditiru. Kalau ChibiClaw v4 mau full vision-first, ini blueprint.
* **Kekurangan**:
  * 7B param butuh ~4-5GB Q4 — di-pair-kan dengan Gemma 4 + wake word + STT, RAM 16GB akan ketat. Realistik: pakai sebagai cloud model atau pilih varian lebih kecil.
  * Performansi di-train terutama untuk desktop & web; benchmark Android masih di bawah Mobile-Agent-v3.5.

#### (4) AppAgent / AppAgentX (Tencent) — paling matang untuk knowledge base reuse

* **URL**: <https://github.com/TencentQQGYLab/AppAgent>
* **License**: MIT
* **Last activity**: AppAgentX, 5 Maret 2025; paper CHI 2025
* **Technical highlight**:
  1. **Two-phase**: exploration phase (agent self-explore / learn from demo) menghasilkan structured knowledge base, deployment phase pakai RAG retrieve.
  2. Reduce inference cost — instead of re-perceiving everything, query KB.
  3. AppAgentX (2025) menambahkan evolving mechanism — self-update KB per session.
* **Kelebihan vs ChibiClaw**: kalau ChibiClaw v4 punya "skill library" / "macro replay", pattern KB-RAG ini bisa di-adopt. Cocok untuk app yang user pakai sering (TikTok, WA).
* **Kekurangan**: exploration phase butuh waktu signifikan; tidak cocok untuk one-shot task tanpa pre-warmup.

#### (5) AutoDroid (MobileLLM / Tsinghua) — paling ringan tapi UI-tree-based

* **URL**: <https://github.com/MobileLLM/AutoDroid>
* **Paper**: arxiv 2308.15272 (MobiCom '24)
* **License**: MIT
* **Technical highlight**:
  1. **Functionality-aware UI representation** — XML/UI tree, bukan vision. Tidak cocok untuk app blacklist ChibiClaw.
  2. Memory injection berbasis exploration.
  3. Action accuracy **90.9%**, task completion **71.3%** dengan GPT-4. On-device Vicuna juga di-eval.
  4. Built on **DroidBot** framework (mature).
* **Kelebihan**: Latency rendah, RAM hemat (text-only).
* **Kekurangan / showstopper untuk ChibiClaw v4**: tidak vision-first — gagal di TikTok/WA. Hanya cocok dipakai sebagai **fallback rute Accessibility** ketika app target whitelist (mis. settings, calendar, notes).

#### (6) Repo Pendukung yang Layak Disebut

* **alibaba/UI-Ins** (<https://github.com/alibaba/UI-Ins>, arxiv 2510.20286) — grounding model executor. UI-Ins-7B sebagai executor + cloud planner mencapai **74.1% di AndroidWorld**. Sangat layak jadi executor on-device di ChibiClaw v4 jika 7B feasible.
* **microsoft/OmniParser** (<https://github.com/microsoft/OmniParser>) — pure-vision UI parser: YOLOv8 untuk icon detection + PaddleOCR + Florence captioning. Berguna sebagai pra-prosesor untuk grounding model kecil.
* **OSU-NLP-Group/UGround** — universal visual grounding (ICLR'25 Oral).
* **showlab/ShowUI** — VLA 2B model, 75.1% zero-shot screenshot grounding, ringan.
* **google-research/android_world** (<https://github.com/google-research/android_world>) — environment, bukan agent. Wajib untuk benchmarking ChibiClaw v4.

---

## 2. Pattern Arsitektur Mobile Agent Modern (2025-2026)

### 2.1 Bagaimana mereka handle vision-first untuk app anti-accessibility?

Ada 4 sub-pattern utama:

1. **Screenshot-only end-to-end VLM** (UI-TARS, GUI-Owl, ShowUI): satu model menerima screenshot + history + instruksi, langsung output action `{tap, x, y}` atau `{type, "text"}`. Tidak perlu UI tree. Cocok untuk app blacklist tapi mahal di compute.
2. **Planner + Grounder split** (UI-Ins paper, Mobile-Agent-v3): LMM general (Claude/GPT/Qwen3-VL-235B) merencanakan high-level — "buka chat WhatsApp dengan Mama, kirim 'oke'". Grounder kecil (UI-Ins-7B, ShowUI-2B, OS-Atlas-4B) konversi step ke koordinat. **Pattern terbaik untuk ChibiClaw v4** — bisa kombinasi Claude cloud sebagai planner + Gemma 4 / model grounding kecil on-device.
3. **Pure-vision dengan structured prompting** (Mobile-Agent-v1, OmniParser): YOLOv8 deteksi UI elements → OCR teks → annotate dengan Set-of-Mark (SoM, arxiv 2310.11441) → kasih GPT-4V/Claude untuk pilih nomor mark. Hemat token, akurasi tinggi, tapi pipeline 3-step.
4. **Hybrid escalation** (DroidRun, AppAgent v2): coba accessibility tree dulu, fallback ke screenshot kalau gagal/blocked. Penting: deteksi `FLAG_SECURE` + `accessibilityDataSensitive` di runtime untuk auto-switch.

**Rekomendasi ChibiClaw v4**: kombinasi #2 (planner-grounder split) + #4 (escalation). Default pakai accessibility kalau diizinkan; deteksi app blacklist (package-name whitelist) → langsung skip ke pure-vision SoM (#3) + grounder.

### 2.2 Bagaimana eskalasi Intent → Accessibility → Shizuku → Root?

Pattern yang dipakai produksi (Tasker, AutoInput, MacroDroid pre-AI; DroidRun, Joi Assistant 2025 sudah AI-aware):

```
[User intent]
    ↓
1. Deep Link / Intent  ───── (paling aman, no permission)
    ↓ (gagal)
2. AccessibilityService ──── (need user grant + Android 17 detection)
    ↓ (gagal / blacklist)
3. Shizuku ADB shell ─────── (perform_input event, deteksi `accessibilityDataSensitive` bisa di-bypass karena event dari shell user)
    ↓ (gagal / root needed)
4. Root su exec (Magisk) ─── (last resort, distribusi terbatas)
```

Kunci yang sering missing:
* **Intent layer ditinggalkan** — banyak agent loncat langsung ke screen control. Padahal `Intent.ACTION_SEND`, `tel:`, `geo:`, deep link Tokopedia / Shopee bisa dapat hasil 5-10x lebih cepat tanpa OCR.
* **Shizuku underused**. Untuk Android 17, Shizuku akan tetap legitimate (Shizuku adalah user-launched ADB shell, bukan accessibility tool). Banyak agent baru (Mei 2026) lebih fokus ke Shizuku untuk `input tap`, `input text`, `input swipe` daripada accessibility.
* **State machine wajib** — eskalasi tidak boleh dilakukan dalam single LLM call. Sebaiknya planner-side: `try_intent_first()` → `if_failed_try_a11y()` → ... ChibiClaw v4 sudah punya state machine, jadi tinggal masukkan eskalasi sebagai tool selection di planner.

### 2.3 Tool catalog: 10 primitives vs 27 specific

Komparasi pendekatan:

| Approach | Pro | Con | Contoh |
|---|---|---|---|
| **Granular (27 tools)** | LLM jelas tahu intent (mis. `send_whatsapp_message`); easier guardrail | Catalog bloat → konteks panjang, harga token naik, model bingung memilih | AppAgent v1, DroidAgent |
| **Primitive (10 tools)** | Konteks tetap kecil; planner punya kebebasan compose | Lebih banyak hallucination koordinat; perlu reasoning lebih kuat | UI-TARS, Mobile-Agent-v3.5 |
| **Hybrid 2-layer** | Primitive di executor; specific intent di planner (sebagai "skill" / Macro) | Kompleksitas dua-layer harus dijaga | AppAgentX, DroidRun |

**Rekomendasi konkret untuk ChibiClaw v4**:
* Pertahankan **10 primitives** sebagai tool catalog yang dikirim ke LLM (`tap`, `type`, `key`, `screenshot`, `intent`, `query`, `system`, `messaging`, `shizuku`, `meta`).
* Tambahkan layer **"Skill Library"** (mirip AppAgentX) — macro tersimpan per-app yang di-resolve di sisi agent runtime, bukan dikirim ke LLM. Mis. `send_wa("Mama", "oke")` di skill library = sequence `[launch_app(WA) → query(chat:Mama) → tap → type → tap(send)]`.
* Catalog di prompt tetap pendek (10 entri × 3-4 baris deskripsi ~ 800 token), skill resolve di local agent.

### 2.4 On-device vs Cloud routing

Pola routing yang terbukti (per arxiv 2411.15399 "Less is More" dan TinyAgent arxiv 2409.00608):

```
Query → Intent classifier (Gemma 4 E2B 270m / FunctionGemma)
        │
        ├─ Simple Q&A / ringkasan / setting ──► On-device (Gemma 4 E4B)
        │
        ├─ Visual reasoning kompleks (multi-screen, app baru) ──► Cloud (Claude / GPT-4o / Gemini)
        │
        ├─ Privasi sensitif (PII, finansial, kesehatan) ──► On-device WAJIB (regulasi PDP-ID)
        │
        └─ Latency-critical (wake-word → first response) ──► On-device
```

Heuristik tambahan dari literatur:
* **Token threshold**: kalau prompt > 4k token atau butuh > 8k output, route ke cloud (latency on-device exploded).
* **Battery state**: di bawah 20% atau thermal throttle aktif → prefer cloud (tapi prompt user dulu kalau ada data sensitif).
* **Offline mode**: degrade gracefully ke on-device dengan disclaimer "akurasi terbatas".

---

## 3. State of the Art (Mei 2026)

### 3.1 Research paper paling relevan

| Paper | Arxiv | Kontribusi | Relevansi ChibiClaw |
|---|---|---|---|
| Mobile-Agent-v3 | [2508.15144](https://arxiv.org/abs/2508.15144) | GUI-Owl multi-agent (Manager/Worker/Reflector/Notetaker); 73.3 AndroidWorld | Blueprint multi-agent planning |
| Mobile-Agent-v3.5 | [2602.16855](https://arxiv.org/abs/2602.16855) | Multi-platform fundamental GUI agents (Feb 2026) | Cross-platform extension |
| UI-Ins | [2510.20286](https://arxiv.org/abs/2510.20286) | Multi-Perspective Instruction-as-Reasoning grounding; 74.1 AndroidWorld | Executor model paling akurat saat ini |
| UI-TARS-2 | [2509.02544](https://arxiv.org/abs/2509.02544) | End-to-end native GUI agent + multi-turn RL | End-to-end blueprint |
| ShowUI | [2411.17465](https://arxiv.org/abs/2411.17465) | VLA 2B, UI-Guided Visual Token Selection | Model executor ringan |
| OS-Atlas | [2410.23218](https://arxiv.org/abs/2410.23218) | Foundation action model + 13M cross-platform corpus | Pretrained grounding 4B/7B |
| AndroidWorld | [2405.14573](https://arxiv.org/abs/2405.14573) | 116 tasks, 20 apps, dynamic param (ICLR 2025) | Benchmark wajib |
| MobileWorld | [2512.19432](https://arxiv.org/abs/2512.19432) | 201 tasks, 27.8 step avg, MCP-augmented (Des 2025) | Benchmark sukses berikutnya |
| MobileAgentBench | [2406.08184](https://arxiv.org/abs/2406.08184) | Efficient + user-friendly evaluator | Test harness |
| MVISU-Bench | [2508.09057](https://arxiv.org/html/2508.09057v2) | Multi-app, vague, interactive, unethical | Adversarial test |
| Less is More (edge function calling) | [2411.15399](https://arxiv.org/html/2411.15399v1) | Optimasi function calling on-device | Routing strategy |
| TinyAgent | [2409.00608](https://arxiv.org/html/2409.00608v1) | 1-7B model function calling pada level GPT-4 Turbo | Cocok untuk Gemma 4 E2B |
| GUI Agents Survey | [2411.18279](https://arxiv.org/abs/2411.18279) | "Large Language Model-Brained GUI Agents" — comprehensive | Reading priority #1 |
| Set-of-Mark | [2310.11441](https://arxiv.org/abs/2310.11441) | Visual prompting untuk grounding | Implementasi vision-first |
| GUI Agents under Real-world Threats | [2507.04227](https://arxiv.org/html/2507.04227v2) | Security audit | Threat model |

### 3.2 SDK/framework yang muncul

* **LiteRT-LM** (<https://github.com/google-ai-edge/LiteRT-LM>) — v0.11.0 (awal 2026). Successor untuk MediaPipe LLM Inference di Android. Kotlin API native, support multimodal (Text, ImageBytes, ImageFile, AudioBytes, AudioFile), GPU & NPU acceleration. **Wajib di ChibiClaw v4**.
* **MediaPipe LLM Inference for Android** — di-deprecate untuk Android, Google merekomendasikan migrasi ke LiteRT-LM. Tetap berguna sebagai bridge sementara (`com.google.mediapipe:tasks-genai`, latest 0.10.27).
* **Google AI Edge Gallery / AICore Developer Preview** — Gemma 4 didistribusikan via AICore (sistem-level), tidak perlu bundle model di APK. Released April 2026 Android Developers blog ("Gemma 4: The new standard for local agentic intelligence on Android").
* **FunctionGemma-270M** — Google mengeluarkan `google/functiongemma-270m-it` di HuggingFace, model 270M khusus function calling. Cocok untuk router/intent classifier on-device dengan latency sub-100ms.
* **scrcpy-mcp** (<https://github.com/JuanCF/scrcpy-mcp>) — MCP server bridging Claude/IDE ke Android device via scrcpy. Layak untuk dev/test ChibiClaw v4, bukan production.
* **mllm** (<https://github.com/UbiquitousLearning/mllm>) — fast multimodal LLM inference engine for mobile, support Qwen3 + DeepSeek-OCR streaming di Android (2025).
* **droidrun-portal** — Android Kotlin portal app, contoh real-world bagaimana host agent runtime di Android.

### 3.3 Benchmark yang dipakai

| Benchmark | Domain | Size | Status saat ini |
|---|---|---|---|
| **AndroidWorld** | Android emulator | 116 tasks / 20 apps | Saturated (>90% SOTA). Tetap baseline obligatori |
| **MobileWorld** | Android, agent-user interactive + MCP | 201 tasks, avg 27.8 step | Aktif (Des 2025), challenge berikutnya |
| **MobileAgentBench** | Real Android device | ~100 tasks | Reproducible, lebih cepat dieval |
| **MVISU-Bench** | Multi-app, vague, interactive, unethical | (Ags 2025) | Adversarial, penting untuk safety |
| **AndroidLab** | ACL 2025 | Training + benchmarking | Cocok untuk fine-tune ChibiClaw skill |
| **A3 (Android Agent Arena)** | OpenReview 2025 | Cross-platform arena | Comparison ground-truth |

Rekomendasi untuk ChibiClaw v4 milestone: target **70-80% di AndroidWorld** dengan **planner cloud + grounder lokal**, lalu shift ke MobileWorld + MVISU-Bench untuk progress sebenarnya.

---

## 4. Trade-offs untuk ChibiClaw v4

### 4.1 Vision-first wajib foundation model multimodal — on-device cukup?

**Singkat: belum cukup, hybrid required.**

Data konkret per Mei 2026:

* **Gemma 4 E4B** (~4B effective, ~2.5 GB Q4) di Snapdragon 8 Elite: prefill 5.5× lebih cepat dari Gemma 3, decode ~10-25 t/s (Pixel 9 Pro / S25 Ultra). Cukup untuk planner sederhana, **bukan untuk grounding screenshot kompleks**.
* **UI-Ins-7B / GUI-Owl-7B / Qwen3-VL-8B** di mobile: butuh Q4 ~4-5 GB + KV cache. Realistis hanya di flagship 16GB sebagai single-purpose; tidak bisa co-eksis dengan wake word + STT + UI.
* **GUI-Owl-1.5-2B** + **ShowUI-2B** + **OS-Atlas-Base-4B**: feasible untuk slot grounding on-device. Tapi accuracy gap vs Claude/Gemini multimodal **masih 15-20 pp** di benchmark Android non-trivial.

**Konklusi**:

1. **Planner**: bisa on-device (Gemma 4 E4B) untuk task sederhana, cloud (Claude Opus 4.7 / Gemini 3 Pro) untuk multi-step / novel app.
2. **Grounder/Executor**: prefer cloud untuk akurasi (Claude computer-use beta dengan `computer-use-2025-11-24` header / GPT-5 / Gemini 3 Pro). On-device sebagai fallback dengan disclaimer.
3. **Hybrid routing wajib**, bukan optional. Patuhi pattern §2.4.

### 4.2 Wake word + STT + Gemma multimodal — feasibility di Snapdragon 8 Elite + 16GB?

**Verdict: feasible, tapi tight.**

Budget memory realistis di flagship 16GB RAM:

| Komponen | RAM aktif | Catatan |
|---|---|---|
| Android system + ChibiClaw foreground service | ~1.5 GB | base |
| OpenWakeWord (microWakeWord) | <100 MB | always-listening, <5% CPU 1 core |
| Sherpa-onnx Whisper Tiny INT8 (STT) | ~250 MB | on-demand setelah wake |
| Sherpa-onnx Piper / Kokoro TTS | ~150 MB | on-demand |
| Gemma 4 E4B Q4 (multimodal) | ~2.5 GB + ~1 GB KV cache | tetap di-warm sambil idle |
| Grounder kecil (ShowUI-2B Q4 atau OS-Atlas-4B Q4) | ~1.5-2.5 GB | hanya saat task aktif |
| Compose UI + WebView + dialog | ~500 MB | overhead |
| **Total peak** | **~8.5 GB** | masih < 16 GB |

Catatan:
* `OpenWakeWord` original (TFLite) lebih berat dari `microWakeWord`. Untuk ChibiClaw v4 dengan target SDK 36 dan ingin always-on background, pakai **microWakeWord** (Google Inception-based, sub-100 MB, dipakai Home Assistant Companion di Android).
* Alternatif komersial **Picovoice Porcupine**: ~1 MB, <4% CPU 1 core, 97%+ akurasi, license non-free di luar non-commercial. Layak jadi opsi untuk distribusi premium.
* STT: **sherpa-onnx Whisper Tiny INT8** (`sherpa-onnx-fire-red-asr2-zh_en-int8-2026-02-26`) reliable di Android, latency real-time. **Vosk** opsi yang lebih hemat tapi akurasi rendah untuk bahasa Indonesia campur Inggris.
* TTS: **Piper** (default OpenWakeWord ecosystem) atau native Android TTS untuk hemat. Indonesia voice tersedia di Piper.

**Risiko**:
1. **Thermal throttling** saat Gemma 4 multimodal + grounder jalan berbarengan. Test di Xiaomi 17 Pro Max wajib dengan thermal monitoring.
2. **Cold start** Gemma 4 E4B ~3-5 detik di-warm dari penyimpanan. Solusi: keep-warm di foreground service, atau LRU swap (unload grounder saat tidak dipakai 60 detik).
3. **Battery drain** always-on microWakeWord realistis 3-5% / jam di Snapdragon 8 Elite Gen 5 (efficient cores).

### 4.3 Memory footprint vs latency budget realistic

Target latency budget (UX premium):

| Fase | Target | Realistis on-device (S8 Elite Gen 5) | Realistis cloud (Claude) |
|---|---|---|---|
| Wake word detection | <300 ms | 150-250 ms (microWakeWord) | n/a (lokal) |
| STT first partial | <500 ms | 400-700 ms (Whisper Tiny INT8) | n/a (lokal) |
| Intent classification | <200 ms | 100-200 ms (FunctionGemma 270M) | 600-1200 ms (network round-trip) |
| Planner LLM response | <2 s | 3-5 s (Gemma 4 E4B, 100-200 token) | 1-3 s (Claude streaming) |
| Grounder action selection | <800 ms | 1-2 s (ShowUI-2B Q4) | 1-2 s (Claude vision) |
| Action execute | <100 ms | <100 ms (Shizuku / a11y) | n/a |
| TTS speak | <300 ms first audio | 200-400 ms (Piper) | n/a |
| **Total wake → first action** | **<5 s** | **~6-9 s on-device only** | **~4-7 s hybrid (cloud planner)** |

Kesimpulan praktis:
* Untuk perceived snappiness, gunakan **streaming everywhere** (STT partial, LLM streaming, TTS streaming).
* Cloud routing **mengurangi** total latency untuk task non-trivial karena planner cloud lebih cepat per-token & lebih akurat (mengurangi retry).
* Investasi penting: **prefetch + prediction**. Begitu wake word terdeteksi, mulai screenshot capture paralel sambil STT jalan.

### 4.4 Risiko showstopper yang harus dipikirkan dari awal

1. **Android 17 Advanced Protection Mode** akan auto-revoke AccessibilityService untuk app yang tidak `isAccessibilityTool=true`. ChibiClaw v4 perlu memutuskan: declare diri sebagai accessibility tool (compliance + review ketat) ATAU bypass via Shizuku/root. Backup plan kombinasi sangat dianjurkan.
2. **Google Play distribusi** untuk app yang automate UI app lain semakin ketat. Lihat kasus Bixby / Tasker. Strategi distribusi (sideload + Magisk module + GitHub Releases) perlu dipikirkan setara dengan Play Store.
3. **Prompt injection via screenshot** — ada paper khusus (arxiv 2507.04227) yang menunjukkan attacker bisa inject teks adversarial di gambar profil/notifikasi. Wajib ada guardrail kategori sensitif (transfer dana, password, PII) di sisi planner.
4. **Model license drift** — Gemma 4 punya Gemma Terms of Use yang masih punya batasan komersial subset; Qwen3 punya Tongyi Qianwen License. Setiap update model wajib re-review lisensi.

---

## 5. Rekomendasi Action Item Konkret untuk ChibiClaw v4

Diurutkan berdasarkan dampak × effort:

### Prioritas Tinggi (do this sprint)

1. **Fork & study DroidRun portal app** untuk pattern Android-side service. License MIT, low-risk.
2. **Adopsi planner-grounder split**: Claude/Gemini cloud sebagai planner, on-device adapter untuk Gemma 4 E4B sebagai backup planner. Executor: tap/type primitive ke Shizuku via Kotlin binding (jangan via Python).
3. **Implement microWakeWord ke ChibiClaw**, bukan OpenWakeWord original. Lebih ringan, sudah proven di Home Assistant Companion Android. Repo: <https://github.com/dscripka/openWakeWord> + `pip install microwakeword` (tooling), runtime TFLite.
4. **Integrasi LiteRT-LM 0.11+ Kotlin API** sebagai engine utama; deprecate plan MediaPipe LLM Inference. Migrasi cepat karena Google sudah deprecate.
5. **Buat package-name based blacklist** untuk routing eskalasi: TikTok, WhatsApp, Tokopedia, Shopee, Instagram, Gojek, GrabFood, BCA Mobile → langsung vision-first, skip accessibility check.

### Prioritas Menengah (next 2-3 sprints)

6. **Eval baseline ChibiClaw di AndroidWorld + MobileAgentBench** sebelum tuning. Target awal: 40% AndroidWorld dengan Claude planner, naik ke 65%+ dengan tambahan Skill Library.
7. **Implement Skill Library pattern dari AppAgentX** — macro storage per-app yang resolve di runtime, bukan di prompt LLM. Save token + boost reliability untuk app yang sering dipakai.
8. **Integrate OmniParser-mini (YOLOv8 ringan + ML Kit OCR)** sebagai pre-processor sebelum kirim ke planner. Reduce screenshot token cost di cloud.
9. **Set up telemetry sederhana** (mirip Arize Phoenix di DroidRun) — trace setiap action chain, simpan ke local Room DB. Jadi modal debugging + RAG untuk skill learning.

### Prioritas Rendah (after MVP)

10. **Eksperimen UI-Ins-7B atau GUI-Owl-1.5-2B Q4** sebagai grounder on-device — hanya kalau MVP cloud sudah jalan dan ada metric jelas.
11. **AppAgent v2 self-exploration** untuk auto-build knowledge base saat onboarding user. Bisa jadi UX moat.
12. **Migrasi ke MobileWorld benchmark** saat AndroidWorld sudah > 75%.

---

## 6. Catatan Sumber & Confidence

* **High confidence** (verifikasi langsung dari GitHub/arxiv saat akses): Mobile-Agent-v3.5, UI-TARS-1.5, UI-Ins, AndroidWorld, LiteRT-LM 0.11, Gemma 4 E2B/E4B specs.
* **Medium confidence** (referensi sekunder / blog): angka konkret memory footprint Gemma 4 E4B (`~2.5 GB Q4`) — verifikasi di Pixel 9 sebelum commit. Latency 10-25 t/s — angka indikatif, perlu re-benchmark di Xiaomi 17 Pro Max.
* **Low confidence / info terbatas**:
  * Exact GitHub stars/forks per repo (estimasi magnitudo).
  * Angka battery drain microWakeWord (estimasi berdasar Home Assistant report 2025-2026).
  * Behavior persis Android 17 Advanced Protection Mode terhadap Shizuku — belum ada konfirmasi resmi Google bahwa Shizuku akan tetap legitimate. **Wajib monitor changelog Android 17 final**.

---

## 7. Daftar URL Penting

### Repos
* DroidRun: <https://github.com/droidrun/droidrun>
* Mobile-Agent (X-PLUG): <https://github.com/X-PLUG/MobileAgent>
* UI-TARS: <https://github.com/bytedance/UI-TARS>
* UI-TARS-desktop: <https://github.com/bytedance/UI-TARS-desktop>
* AppAgent: <https://github.com/TencentQQGYLab/AppAgent>
* AutoDroid: <https://github.com/MobileLLM/AutoDroid>
* UI-Ins: <https://github.com/alibaba/UI-Ins>
* OS-Atlas: <https://github.com/OS-Copilot/OS-Atlas>
* ShowUI: <https://github.com/showlab/ShowUI>
* OmniParser: <https://github.com/microsoft/OmniParser>
* UGround: <https://github.com/OSU-NLP-Group/UGround>
* ScreenAgent: <https://github.com/niuzaisheng/ScreenAgent>
* AndroidWorld: <https://github.com/google-research/android_world>
* mllm (mobile multimodal engine): <https://github.com/UbiquitousLearning/mllm>
* Sherpa-onnx: <https://github.com/k2-fsa/sherpa-onnx>
* OpenWakeWord: <https://github.com/dscripka/openWakeWord>
* Awesome lists: [Awesome-GUI-Agent](https://github.com/showlab/Awesome-GUI-Agent), [awesome-mobile-llm](https://github.com/stevelaskaridis/awesome-mobile-llm), [awesome-edge-ai-agents](https://github.com/yh-yao/awesome-edge-ai-agents), [Awesome-LLM-Powered-Phone-GUI-Agents](https://github.com/PhoneLLM/Awesome-LLM-Powered-Phone-GUI-Agents)

### SDK & Docs
* LiteRT-LM: <https://github.com/google-ai-edge/LiteRT-LM>, <https://ai.google.dev/edge/litert-lm>
* MediaPipe LLM Inference Android: <https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android>
* Gemma 4 Android: <https://android-developers.googleblog.com/2026/04/AI-Core-Developer-Preview.html>
* Anthropic Computer Use: <https://platform.claude.com/docs/en/agents-and-tools/tool-use/computer-use-tool>
* HuggingFace GUI-Owl-1.5: <https://huggingface.co/mPLUG/GUI-Owl-1.5-2B-Instruct>
* HuggingFace UI-TARS-1.5-7B: <https://huggingface.co/ByteDance-Seed/UI-TARS-1.5-7B>
* HuggingFace FunctionGemma-270M: <https://huggingface.co/google/functiongemma-270m-it>

### Papers (Arxiv)
* Mobile-Agent-v3: <https://arxiv.org/abs/2508.15144>
* Mobile-Agent-v3.5: <https://arxiv.org/abs/2602.16855>
* UI-Ins: <https://arxiv.org/abs/2510.20286>
* UI-TARS-2: <https://arxiv.org/html/2509.02544v1>
* ShowUI: <https://arxiv.org/abs/2411.17465>
* OS-Atlas: <https://arxiv.org/html/2410.23218v1>
* AndroidWorld: <https://arxiv.org/abs/2405.14573>
* MobileWorld: <https://arxiv.org/abs/2512.19432>
* AutoDroid: <https://arxiv.org/abs/2308.15272>
* Set-of-Mark: <https://arxiv.org/abs/2310.11441>
* Less is More (edge function calling): <https://arxiv.org/html/2411.15399v1>
* TinyAgent: <https://arxiv.org/html/2409.00608v1>
* GUI Agents Survey: <https://arxiv.org/abs/2411.18279>
* GUI Agents under Real-world Threats: <https://arxiv.org/html/2507.04227v2>
* MobileFlow: <https://arxiv.org/html/2407.04346v3>

### Misc / Blog
* DroidRun homepage: <https://droidrun.ai/>
* Android 16 accessibility shield: <https://medium.com/@jacksonfdam/android-16s-new-security-feature-a-shield-against-accessibility-api-abuse-4bdaf4f214f3>
* Android 17 advanced protection: <https://www.androidheadlines.com/2026/02/android-advanced-protection-accessibility-api-restrictions-customization-automation.html>
* Google blog Gemma 4 Android: <https://android-developers.googleblog.com/2026/04/gemma-4-new-standard-for-local-agentic-intelligence.html>
* Picovoice wake word guide 2026: <https://picovoice.ai/blog/complete-guide-to-wake-word/>
* On-device voice AI stack RN: <https://medium.com/@frymanofer/built-a-full-on-device-voice-ai-stack-for-react-native-wake-word-stt-tts-speaker-5a2403a06d16>
* Snapdragon 8 Elite LLM: <https://grapeup.com/blog/running-llms-on-device-with-qualcomm-snapdragon-8-elite>
* Hybrid cloud-edge routing: <https://tianpan.co/blog/2026-04-10-hybrid-cloud-edge-llm-inference-routing>
* OmniParser deep dive: <https://learnopencv.com/omniparser-vision-based-gui-agent/>

---

## 8. Next Research Hops (untuk dokumen `02-*.md`)

Topik turunan yang belum tuntas dan layak masuk research berikutnya:

1. **Shizuku Kotlin binding mature** untuk `input tap/text/swipe` di Android 17 — verifikasi lifecycle, performance, stabilitas vs `UiAutomator` melalui ADB shell.
2. **Skill Library schema** — bagaimana format penyimpanan macro (Room? JSON? proto?), versioning, sharing antar device, sandboxing.
3. **Cost analysis cloud LLM** per task — Claude Opus 4.7 vs Gemini 3 Pro vs GPT-5 untuk computer-use beta, perbandingan rate & accuracy.
4. **Voice cloning lokal** untuk persona Fuu (TTS personality) — Piper voice training, Kokoro, atau XTTSv2 di Android.
5. **Privacy & compliance Indonesia PDP**: data retention untuk screenshot, audio clip, planner trace — apa boleh disimpan, berapa lama, opt-in/opt-out UX.

---

*Selesai. Dokumen ini menjadi baseline reference. Update saat ada model/SDK baru rilis atau saat ChibiClaw v4 hit milestone implementation.*
