# Vision-LLM Multimodal Benchmark di Mobile NPU/CPU/GPU — Mei 2026

**Tanggal akses sumber**: 2026-05-13
**Konteks**: ChibiClaw v4 (vision-first executor) butuh model multimodal untuk screenshot → action grounding di Xiaomi 17 Pro Max (Snapdragon 8 Elite Gen 5, Hexagon NPU, 16GB RAM).
**Status**: Riset literatur + leaderboard publik. Angka latency yang tidak diverifikasi pada device aktual akan diberi penanda **[VERIFY]**.

---

## 1. Lanskap Model Multimodal Mobile-Grade (2-7B params)

### 1.1 Ringkasan Model

| Model | Params | Vendor | License | Resolusi Input | Context | Format Distribusi | Catatan |
|---|---|---|---|---|---|---|---|
| Gemma 4 E2B / E4B | 2B / 4B effective (MoE-style) | Google | Gemma License (komersial OK, ada AUP) | Native ANY (interleaved text+image, variable aspect ratio) | 128K | `.litertlm`, `.safetensors`, GGUF (community) | Multimodal native: text+image+audio (E2B/E4B). Edge-first. Rilis 2 Apr 2026. |
| Phi-4-Multimodal-Instruct | 5.6B | Microsoft | MIT | 224, 448, variable | 128K | `.safetensors`, ONNX | "Mixture of LoRAs" — text+image+speech satu model. Skor ScreenSpot v2: 88.2 (varian reasoning-vision 15B). |
| Phi-4-Reasoning-Vision | 15B | Microsoft | MIT | high-res | 128K | `.safetensors` | AI2D 84.8 / ChartQA 83.3 / MMMU 54.3. Terlalu besar untuk on-device penuh. |
| Llama 3.2 11B-Vision | 11B | Meta | Llama 3.2 Community License | 560×560 (tile) | 128K | `.safetensors`, ExecuTorch `.pte` | Tidak ada varian 3B vision official; 1B/3B text-only. Vision butuh 11B+. |
| Llama 3.2 3B (text) | 3B | Meta | Llama 3.2 Community | text-only | 128K | ExecuTorch QNN | ~10 tok/s di Snapdragon 8 Elite (Llama 3.2 3B Instruct, Q4 QNN). |
| Qwen2.5-VL-3B-Instruct | 3B | Alibaba | Apache 2.0 (kebanyakan varian) | dynamic (ViT native res) | 32K-128K | `.safetensors`, GGUF | Mature; banyak fine-tune turunan. |
| Qwen2.5-VL-7B-Instruct | 7B | Alibaba | Apache 2.0 | dynamic | 128K | `.safetensors`, GGUF | Sweet spot accuracy/size; basis banyak GUI agent. |
| Qwen3-VL-2B / 4B / 8B | 2/4/8B dense + 30B-A3B MoE | Alibaba | Apache 2.0 (dense) | dynamic | 256K native | `.safetensors`, GGUF (Q2-Q8) | Rilis Q4 2025 — dilanjut Q1 2026. Vision+video+long-context. GGUF resmi dari Qwen di HF. |
| InternVL3-1B / 2B / 4B / 8B / 14B | 1B–14B | Shanghai AI Lab (OpenGVLab) | MIT (model), data ada Apache | 448 tile (dynamic) | 32K | `.safetensors` | Lineup mobile-friendly. |
| InternVL3.5-2B / 4B / 8B | 2B / 4B / 8B | OpenGVLab | MIT | dynamic | 32K | `.safetensors` | InternVL3.5-2B MMMU 50.7 (vs 32.4 InternVL3-2B). 4B mencapai 57.4 MMMU. Fokus GUI grounding meningkat (ScreenSpot-v2, OSWorld-G). |
| MiniCPM-V 4.6 | 1.3B (SigLIP2-400M + Qwen3.5-0.8B) | OpenBMB | MIT-style (cek Apache header) | dynamic up to 1.8M pixels | 262K | GGUF (~2GB), BNB int4, AWQ, GPTQ | Rilis 11 Mei 2026. **Fokus phone deployment** (referensi resmi: iPhone 17 Pro Max, Redmi K70, Huawei nova 14). |
| MiniCPM-V 4.5 | 8B | OpenBMB | MIT-style | dynamic | 96K | GGUF, AWQ | Sebelum 4.6; masih populer karena akurasi. |
| SmolVLM2-2.2B-Instruct | 2.2B | HuggingFaceTB | Apache 2.0 | 384 tile (pixel-shuffle 9×) | 16K | `.safetensors`, GGUF, MLX | Berbasis Idefics3 + SmolLM2-1.7B LM. Video-aware. RAM peak ~5.2GB. |
| SmolVLM-256M / 500M | 0.25B / 0.5B | HuggingFaceTB | Apache 2.0 | 384 | 8K | `.safetensors`, GGUF | <1GB VRAM. Outperform Idefics-80B (klaim vendor) di throughput per param. |
| Idefics 3 (8B) | 8B | HuggingFace + Mistral team | Apache 2.0 | dynamic | 10K | `.safetensors` | Predecessor SmolVLM; lebih akurat tapi lebih berat. |
| Molmo-7B-D / 7B-O | 7B | Allen AI (Ai2) | Apache 2.0 | dynamic | 4K | `.safetensors` | Berbasis Qwen2-7B / OLMo-7B. PixMo dataset pointing 2D — relevan untuk UI grounding karena training task pointing. |
| Molmo 2 (video) | 7B+ | Ai2 | Apache 2.0 | video frame seq | varies | `.safetensors` | Video understanding + pointing tracking. |
| UI-TARS-1.5-7B | 7B | ByteDance | Apache 2.0 (cek release notes) | dynamic | 32K | `.safetensors` | Predecessor UI-TARS-2. |
| UI-TARS-2 | 7B / 72B | ByteDance | Apache 2.0 | dynamic | 32K | `.safetensors` | **GUI-specialized**. AndroidWorld 73.3, OSWorld 47.5. |
| OS-Atlas-7B / OS-Atlas-Base-7B | 7B | HKU + UNS | Apache 2.0 | up to 1344×1344 tile | 32K | `.safetensors` | GUI grounding-focused. Baseline ScreenSpot-Pro 18.9 → 48.1 dengan agentic refinement (ScreenSeekeR). |
| GUI-Owl-7B (Mobile-Agent-v3 base) | 7B | Alibaba Qwen Team | Apache 2.0 | dynamic | 32K | `.safetensors` | Skor AndroidWorld 66.4 (single model); 73.3 dengan multi-agent framework. ScreenSpot-Pro 54.9. |
| PaliGemma 2 — 3B / 10B / 28B | 3 / 10 / 28B | Google | Gemma License | 224 / 448 / 896 | 8K | `.safetensors`, JAX | SigLIP + Gemma 2. **Pretrained, butuh fine-tune untuk task**. |
| PaliGemma 2 Mix | 3B / 10B / 28B | Google | Gemma License | 224 / 448 | 8K | `.safetensors` | Versi instruction-mixed siap pakai untuk caption, VQA, OCR. |
| FastVLM-0.5B / 1.5B / 7B | 0.5 / 1.5 / 7B | Apple | Apple Sample Code License (cek repo) | dynamic high-res | 4K-8K | MLX, CoreML | Fokus iOS, **bukan native Android** — tapi ada port via ONNX. NPU port Snapdragon dilaporkan 11k tok/s prefill (vendor claim). |

### 1.2 Catatan License & Pemilihan untuk ChibiClaw

- **Apache 2.0 / MIT** (Qwen series, InternVL, SmolVLM, Molmo, UI-TARS, OS-Atlas, GUI-Owl, MiniCPM-V) — paling aman untuk produk komersial.
- **Gemma License** (Gemma 4, PaliGemma 2) — komersial diizinkan tapi ada Acceptable Use Policy Google. Cek pasal "Use Restrictions" sebelum ship.
- **Llama 3.2 Community License** — 700M MAU cap (tidak relevan untuk early-stage product) dan butuh attribution.

### 1.3 Repo & Model Card URL (untuk download/dev)

| Model | URL Repo / Model Card |
|---|---|
| Gemma 4 E2B | `https://huggingface.co/google/gemma-4-E2B` |
| Gemma 4 E4B-it | `https://huggingface.co/google/gemma-4-E4B-it` |
| Gemma 4 LiteRT-LM | `https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm` |
| Phi-4-Multimodal-Instruct | `https://huggingface.co/microsoft/Phi-4-multimodal-instruct` |
| Qwen3-VL family | `https://github.com/QwenLM/Qwen3-VL` |
| Qwen3-VL-2B-Instruct-GGUF | `https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct-GGUF` |
| Qwen3-VL-4B-Instruct-GGUF | `https://huggingface.co/Qwen/Qwen3-VL-4B-Instruct-GGUF` |
| Qwen2.5-VL-3B | `https://huggingface.co/Qwen/Qwen2.5-VL-3B-Instruct` |
| InternVL family | `https://github.com/OpenGVLab/InternVL` |
| InternVL3.5-8B | `https://huggingface.co/OpenGVLab/InternVL3_5-8B` |
| MiniCPM-V 4.6 | `https://huggingface.co/openbmb/MiniCPM-V-4.6` |
| MiniCPM-V 4.5 GGUF | `https://huggingface.co/openbmb/MiniCPM-V-4_5-gguf` |
| SmolVLM2-2.2B-Instruct | `https://huggingface.co/HuggingFaceTB/SmolVLM2-2.2B-Instruct` |
| Molmo-7B-D | `https://huggingface.co/allenai/Molmo-7B-D-0924` |
| Molmo-7B-O | `https://huggingface.co/allenai/Molmo-7B-O-0924` |
| UI-TARS | `https://github.com/bytedance/UI-TARS` |
| PaliGemma 2 3B mix-224 | `https://huggingface.co/google/paligemma2-3b-mix-224` |
| Mobile-Agent (X-PLUG) | `https://github.com/X-PLUG/MobileAgent` |
| AppAgent (Tencent) | `https://github.com/TencentQQGYLab/AppAgent` |

### 1.4 Paper Reference Pendek

- Gemma 4 technical report — Google blog & HF Gemma 4 model card (2026-04-02).
- Phi-4-Multimodal: Microsoft technical report (Feb-Mar 2026).
- Qwen3-VL Technical Report — arXiv 2511.21631.
- InternVL3.5 — arXiv 2508.18265.
- InternVL3 — arXiv 2504.10479.
- MiniCPM-V 4.6 — OpenBMB blog + HF card 2026-05-11.
- SmolVLM — arXiv 2504.05299.
- Molmo & PixMo — arXiv 2409.17146 (CVPR 2025).
- UI-TARS-2 — arXiv 2509.02544.
- ScreenSpot-Pro — arXiv 2504.07981.
- Mobile-Agent-v3 / GUI-Owl — arXiv 2508.15144.
- MVISU-Bench — arXiv 2508.09057 (ACM MM 2025).
- MobileWorld — arXiv 2512.19432 (ACL 2026).
- FastVLM — arXiv 2412.13303 (CVPR 2025).
- Cambrian-1 — arXiv 2406.16860 (NeurIPS 2024).

---

## 2. UI Grounding Benchmark Spesifik

### 2.1 AndroidWorld (Google)

Lingkungan dynamic Android emulator dengan 116 tasks across 20 apps. AndroidWorld kini dianggap **mendekati saturasi**: SOTA agent >90% di subset, dengan top end-to-end model meraih 60-80%.

| Agent / Model | AndroidWorld success rate | Sumber |
|---|---|---|
| UI-TARS-2 (ByteDance) | **73.3%** | UI-TARS-2 technical report (arXiv 2509.02544) |
| Mobile-Agent-v3 (Alibaba, on GUI-Owl-7B) | **73.3%** | arXiv 2508.15144 |
| GUI-Owl-7B (single model, no multi-agent) | 66.4% | Same as above |
| UI-TARS-1.5 | ~47% (sebelumnya 46.6% UI-TARS-1) | UI-TARS-2 paper baseline |
| GPT-4o (zero-shot) | 34.5% | UI-TARS-1 paper |
| DroidRun framework | 43% (subset 65 tasks) | aimultiple.com 2026 review |

Catatan: angka di atas berbasis cloud model (UI-TARS-2 7B/72B server-side). Untuk on-device 7B dengan kuantisasi Q4, akurasi diharapkan turun **5-12 percentage points** (estimasi dari pola degradasi standard Q4 vs FP16; **[VERIFY]** di device).

### 2.2 OSWorld

Lingkungan desktop multi-app (Linux dekstop), 369 tasks. Lebih sulit dari AndroidWorld.

| Model | OSWorld score |
|---|---|
| UI-TARS-2 | 47.5% |
| UI-TARS-1.5 | ~33% |
| GPT-4o | ~12% |

### 2.3 ScreenSpot / ScreenSpot-v2 / ScreenSpot-Pro

**ScreenSpot** (original): GUI elements grounding accuracy on mobile, desktop, web — ~600 images.

**ScreenSpot-v2**: Re-curated, lebih bersih.

**ScreenSpot-Pro**: High-resolution profesional GUI (CAD, IDE, dashboards). Sangat sulit — banyak model dasar <10% accuracy.

| Model | ScreenSpot-v2 | ScreenSpot-Pro | Catatan |
|---|---|---|---|
| Phi-4-Reasoning-Vision (15B) | 88.2 | n/a | Microsoft data |
| GUI-Owl-7B (Mobile-Agent-v3 base) | SOTA-class | **54.9** | Mengalahkan UI-TARS-72B & Qwen2.5-VL-72B di ScreenSpot-Pro |
| OS-Atlas-7B (base) | tinggi (mobile/desktop) | 18.9 | Baseline; bisa naik ke 48.1 dengan ScreenSeekeR agentic refinement |
| ZonUI-3B (LoRA-adapted) | strong | improved | Two-stage cross-platform pretraining |
| GUI-G² (reward modeling) | +5.9% over sparse baseline | improved | Gaussian reward RL |

### 2.4 VisualWebBench

Benchmark untuk web GUI agent — 1.5K samples, 7 sub-tasks. Tidak ditelusuri detail di sesi ini; relevansi untuk ChibiClaw rendah (target = Android native apps).

### 2.5 MoTIF (Mobile App Tasks with Iterative Feedback)

Diketahui exist (Burns et al. CVPR 2022). Kurang up-to-date untuk era VLM 2026 — sebagian besar digantikan AndroidWorld + MobileAgentBench.

### 2.6 MVISU-Bench

Bilingual benchmark, 404 tasks, 137 mobile apps. 5 kategori: **M**ulti-App, **V**ague, **I**nteractive, **S**ingle-App, **U**nethical Instructions. Aider module sebagai prompt prompter naik 19.55% success rate dari SOTA. Ini bukan strictly "adversarial" tapi mencakup ambiguity & ethical edge cases — relevan untuk safety routing ChibiClaw.

Repo: `https://huggingface.co/MVISU-Bench/Qwen2.5-VL-3B-Mobile-Aider` (model + benchmark).

### 2.7 MobileWorld (ACL 2026, **baru**)

Penerus AndroidWorld yang sengaja lebih sulit. End-to-end best model: 20.9%. Best agentic framework: 51.7%. Agent-User Interaction sub-task: model end-to-end <10%, GPT-5 leads at 62.2%.

Implication: untuk ChibiClaw v4 yang vision-first, baseline on-device dengan model 7B kelas Q4 kemungkinan **hanya bisa solve subset task ringan** (~20-30% raw). Cloud planner masih jadi keharusan untuk task kompleks.

### 2.8 MobileAgentBench

100 tasks across 10 open-source apps. Established benchmark sebelum AndroidWorld saturasi. Cocok untuk regression test ChibiClaw karena open-source apps.

### 2.9 CV-Bench (Cambrian Vision-Centric Benchmark)

2D & 3D visual understanding (spasial, count, depth) — fundamental capability check, tidak GUI-specific.

---

## 3. Mobile Inference Engine 2025-2026

### 3.1 Ringkasan

| Engine | Versi (Mei 2026) | Akselerator | Bahasa API Android | Vision Support | Catatan |
|---|---|---|---|---|---|
| **MediaPipe LLM Inference** | tasks-genai 0.10.27 | CPU (XNNPACK), GPU (OpenCL), NPU (via AI Edge SDK) | Java/Kotlin | Yes (text+image+audio) | **Deprecated** untuk Android/iOS — Google rekomendasi migrate ke LiteRT-LM. |
| **LiteRT-LM** | Stable rilis 7-8 Apr 2026 | CPU, GPU, NPU (Hexagon via AI Edge) | **Kotlin idiomatic API** (JNI bawah) + Python | Yes — multi-modality dari awal | Successor MediaPipe; supports tool use, multi-turn conversation lifecycle. Maven `com.google.ai.edge.litertlm`. |
| **ONNX Runtime QNN EP** | onnxruntime-qnn (PyPI) — QAIRT-aligned | NPU (Hexagon HTP), GPU (Adreno preview 2025-05) | C/C++ via JNI; ORT-Generate API | Vision via ONNX export | TTFT ~100ms untuk Llama 3.2 3B prompt 128 tokens di Snapdragon 8 Elite (klaim ONNX RT blog). |
| **ExecuTorch** | 0.5+ (Mei 2026) | XNNPACK (Arm CPU), QNN (Snapdragon NPU), CoreML, Vulkan | Java/Kotlin | Llama 3.2 Vision lewat `.pte` | 50.2 tok/s decode + 260 tok/s prefill untuk Llama 3.2 1B Q4 di OnePlus 12. |
| **MLC-LLM** | mlc-llm 0.1.x | Vulkan / OpenCL / Adreno (Snapdragon) | Java + native | Limited (TVM-Unity stack) | Backend Android = OpenCL (bukan Vulkan secara default per issue #3372). Vision support kurang matang vs llama.cpp. |
| **llama.cpp** Android | b6000+ | CPU NEON, OpenCL (Snapdragon Adreno) | C++ via JNI; bind: `llama-cpp-android` | **GGUF + mmproj** (CLIP/SigLIP projector terpisah) | Vision works untuk Qwen2.5-VL, MiniCPM-V, Gemma 3 vision, dll. Tantangan: **quantization mmproj** masih issue terbuka (#18881) — projector cenderung dijalankan FP16. |
| **Qualcomm AI Hub + QAIRT SDK** | QAIRT (formerly QNN SDK) | NPU Hexagon native | C++ + Python compile | Vision khusus via Hub Models | Kompilasi model ke `.bin` QNN binary; deploy via app native. Best performance untuk NPU murni. |
| **mllm (UbiquitousLearning)** | aktif maintained | QNN NPU + CPU | C++ | Multimodal | Research framework; benchmark CPU/NPU hybrid. |

### 3.2 Rekomendasi Stack ChibiClaw

Berdasarkan integrasi Kotlin/Compose ChibiClaw + kebutuhan multimodal:

**Prioritas tertinggi**:
1. **LiteRT-LM (Kotlin)** untuk Gemma 4 E2B/E4B (multimodal native, NPU acceleration via AI Edge SDK, Maven artifact siap pakai).
2. **llama.cpp via JNI binding** untuk fleksibilitas model lain (Qwen3-VL-2B/4B GGUF, MiniCPM-V 4.6). Mature, banyak community.
3. **ONNX Runtime QNN EP** untuk Qualcomm-optimized SLM (Phi-4 multimodal jika Microsoft rilis ONNX QNN-ready, Llama 3.2 3B text-only).

**Hindari untuk produksi awal**:
- MLC-LLM Android: vision support lebih lemah, kompilasi tooling lebih kompleks.
- MediaPipe LLM Inference: deprecated jalur Android/iOS.

### 3.3 NPU-Specific Stack

- **Qualcomm Hexagon SDK + QAIRT** — performa puncak, tapi lock-in vendor. Butuh compile per-architecture.
- **Samsung Eden** — Galaxy-only; ChibiClaw bukan target.
- **Apple Neural Engine** — off-topic Android.

---

## 4. Benchmark Spesifik di Snapdragon 8 Elite Gen 5

### 4.1 Spesifikasi Hardware Xiaomi 17 Pro Max

- **Chipset**: Snapdragon 8 Elite Gen 5 (TSMC 3nm)
- **CPU**: 2× Oryon V3 Phoenix L @ 4.6 GHz + 6× Oryon V3 Phoenix M @ 3.62 GHz
- **NPU**: Hexagon NPU — **37% lebih cepat dari Gen 4** (klaim Qualcomm). Estimasi industry: ~45 TOPS untuk Snapdragon 8 Elite series.
- **RAM**: 16 GB LPDDR5X (varian 16+512 / 16+1TB)
- **Battery**: 7500 mAh (battery besar membantu sustained inference)
- **Storage**: UFS 4.1
- **NPU context window**: untuk 3B LLM, context 32K (8× lebih besar dari Gen 4).

### 4.2 LLM/VLM Throughput Numbers

Angka berikut **dari klaim Qualcomm + measurement third-party**. Tanda **[V]** = independent verified; **[V-Vendor]** = vendor claim; **[VERIFY]** = perlu uji device aktual.

| Workload | Engine | Token/s decode | Prefill | TTFT | RAM | Source |
|---|---|---|---|---|---|---|
| Llama 3.2 3B Q4 (text) | ONNX RT QNN | ~10 tok/s [V] | n/a | ~100ms (prompt 128 tok) [V-Vendor] | ~2.5 GB | ONNX RT blog, grapeup.com |
| Llama 3.1 8B Q4 (text) | ONNX RT QNN | ~5 tok/s [V] | n/a | n/a | ~5 GB | grapeup.com |
| Llama 3.2 1B Q4 (text) | ExecuTorch QNN | 50.2 tok/s decode [V] | 260 tok/s prefill [V] | <100ms | ~600 MB | learn.arm.com (device OnePlus 12, SM8650) |
| 7B LLM (umum, INT4) | not specified | ~32 tok/s [V] | n/a | n/a | ~4 GB | independent tester via grapeup.com |
| Peak Qualcomm-claim (3B INT2/FP8) | NPU optimal path | up to 220 tok/s [V-Vendor] | n/a | n/a | varies | Qualcomm Snapdragon 8 Elite Gen 5 marketing |
| FastVLM-0.5B (vision) | NPU optimal path | >100 tok/s decode [V-Vendor] | >11,000 tok/s prefill [V-Vendor] | ~120 ms (image 1024×1024) [V-Vendor] | n/a | Qualcomm + Apple FastVLM port |
| Gemma 4 E4B (multimodal Q4) | LiteRT-LM AI Edge | 10-25 tok/s [V-Vendor, range] | n/a | n/a | ~3 GB | mindstudio.ai blog (Snapdragon 8 Gen 2+ class) |

**Catatan kritis**:

1. Klaim 220 tok/s peak Qualcomm adalah **theoretical peak NPU dengan INT2/FP8 fully fused** dan **bukan throughput end-to-end aplikasi**. Real-world untuk Llama 3.2 3B di independent test hanya **~10 tok/s** dengan ONNX RT QNN.
2. Vision prefill 11,000 tok/s adalah klaim Qualcomm untuk FastVLM-0.5B (image-only, **encoder pass**). Untuk model 7B kelas Qwen2.5-VL-7B yang ChibiClaw kemungkinan butuh, expect prefill 1-3 detik untuk screenshot 1080p **[VERIFY]**.
3. Tidak ada **third-party benchmark public** untuk Snapdragon 8 Elite Gen 5 + Qwen2.5-VL-7B atau MiniCPM-V 4.6 di **device-level** dengan latency end-to-end (screenshot → JSON action). Harus diuji sendiri.

### 4.3 Estimasi Latency Screenshot → Action JSON (1080p)

Berdasarkan ekstrapolasi data di atas (**estimate; verify di device**):

| Model | Image preprocess + ViT encode | LLM TTFT | LLM decode 50 token JSON | Total estimate |
|---|---|---|---|---|
| MiniCPM-V 4.6 (1.3B Q4 GGUF on llama.cpp + Adreno OpenCL) | ~200-400 ms | ~300 ms | ~1.2 s | **~1.7-2.0 s** |
| Qwen3-VL-2B Q4 (llama.cpp Adreno) | ~250-450 ms | ~350 ms | ~1.5 s | **~2.1-2.3 s** |
| Qwen3-VL-4B Q4 | ~300-500 ms | ~500 ms | ~2.0 s | **~2.8-3.0 s** |
| Gemma 4 E2B (LiteRT-LM NPU) | ~150-300 ms | ~200 ms | ~1.0 s | **~1.3-1.5 s** [VERIFY] |
| Gemma 4 E4B (LiteRT-LM NPU) | ~200-400 ms | ~300 ms | ~1.5 s | **~2.0-2.2 s** [VERIFY] |
| Qwen2.5-VL-7B Q4 (llama.cpp OpenCL) | ~400-700 ms | ~800 ms | ~3.0 s | **~4.2-4.5 s** |
| UI-TARS-2-7B Q4 (llama.cpp) | similar to Qwen2.5-VL-7B | similar | similar | **~4.2-4.5 s** |

Target ChibiClaw v4: **<2s end-to-end** untuk responsiveness. Berdasarkan tabel, hanya **MiniCPM-V 4.6 (1.3B) dan Gemma 4 E2B (2B)** yang **berpotensi memenuhi**. Model 7B kelas UI-TARS-2 harus di cloud, atau dengan speculative decoding + NPU full optimize (still tight).

### 4.4 RAM Peak per Model Size

| Model + Quantization | Estimated RAM peak | Headroom di 16GB (OS Android + Chrome + ChibiClaw + cache) |
|---|---|---|
| MiniCPM-V 4.6 Q4 GGUF | ~2.0 GB | OK (sisa ~6-8 GB untuk app + sistem) |
| MiniCPM-V 4.6 BNB int4 | ~3.0 GB | OK |
| Gemma 4 E2B (LiteRT-LM Q4 quant via AI Edge) | ~2-3 GB | OK |
| Gemma 4 E4B Q4 | ~4-5 GB | OK tapi tight (Chrome bisa pakai 3-4 GB) |
| Qwen3-VL-2B Q4 GGUF | ~2.5 GB | OK |
| Qwen3-VL-4B Q4 GGUF | ~4-5 GB | OK marginal |
| Qwen2.5-VL-7B Q4 | ~6-7 GB | **Tight** — risk OOM saat Chrome heavy |
| UI-TARS-2-7B Q4 | ~6-7 GB | **Tight** |
| Llama 3.2 11B-Vision Q4 | ~9-10 GB | **Tidak realistis** untuk on-device dengan multitasking |

Asumsi Android 15 + AOSP services ~3-4 GB, Chrome heavy ~2-3 GB, ChibiClaw app + state ~500 MB. Sisa effective untuk model = **8-10 GB max** di 16 GB device.

### 4.5 Sustained vs Burst Inference (Thermal Throttling)

- Snapdragon 8 Elite series memiliki **thermal budget yang ketat**: NPU sustained max-util akan throttle dalam **2-3 menit** (laporan multi-source termasuk XPU.pub & arXiv 2603.23640 study).
- Untuk continuous agent (e.g., 1 hour continuous activity ChibiClaw), expect:
  - **5-15 menit** burst performance penuh.
  - Setelah throttle: tok/s drop **20-40%**, latency naik proporsional.
- Mitigasi: **batch inference** (kumpulkan beberapa screenshot, hindari per-frame), **prefetch** next-action prediction saat user idle, **adaptive precision** (turunkan ke INT4 dari FP16 saat thermal naik).

### 4.6 Battery Drain Estimate

Per literatur multi-source:
- Sustained NPU inference: **5-15% battery / 30 menit** (rentang lebar tergantung model size + optimasi).
- 1 jam continuous agent activity ChibiClaw (asumsi 1 inference / 3-5 detik): **10-25% battery drain** [estimate; **[VERIFY]**].
- Baseline tanpa AI (Chrome scrolling pure): ~5-8% / jam.

NPU **lebih efisien per TOP** dibanding CPU/GPU untuk inferensi neural. Strategi: **route everything NPU-first**, fallback CPU hanya untuk pre/post-processing string.

---

## 5. Hybrid Routing Strategy

### 5.1 Tier Routing Matrix

| Tier | Latency Budget | RAM Budget | Akurasi UI Grounding | Model Pick | Use Case |
|---|---|---|---|---|---|
| **A: On-device fast** | <1.5s | <3 GB | Acceptable (60-75% ScreenSpot-v2 estimate) | MiniCPM-V 4.6 Q4 (GGUF, llama.cpp Adreno) atau Gemma 4 E2B | Quick UI tap, simple grounding |
| **B: On-device accurate** | 1.5-3s | <6 GB | Better (70-85% estimate Q4) | Qwen3-VL-4B Q4 (llama.cpp) atau Gemma 4 E4B (LiteRT-LM) | Multi-step task local |
| **C: Cloud planner + local grounder** | 1.5-4s (parallel) | <3 GB local | High (cloud >90% SOTA) | Cloud: Claude 4.6 Sonnet / Gemini 3 Pro planner. Local: MiniCPM-V 4.6 atau Qwen3-VL-2B grounder | Complex multi-app workflow |
| **D: Full cloud** | 2-6s | minimal local | Highest | Claude 4.6 Sonnet vision / GPT-5.5 vision / Gemini 3 Pro vision | Long-horizon plan, ambiguous instruction |

### 5.2 Cost Cloud API (per 1 image + small JSON output, est. 1334 input tok + 100 output tok)

Asumsi 1024×1024 input = ~1334 tokens (Claude), gunakan rate kartu pricing.

| API | Input cost/M tok | Output cost/M tok | Cost per call estimate |
|---|---|---|---|
| **Claude Sonnet 4.6** | $3.00 | $15.00 | (1334 × 3 + 100 × 15) / 1M = **$0.0055/call** (≈$0.0040/image-only avg per blog) |
| **Gemini 3 Pro** (text input + 1 image at 560 tok) | $2.00 | $12.00 | (560 × 2 + 100 × 12) / 1M = **$0.0023/call** (input image) |
| **GPT-5.4** | $2.50 (with image tok) | $15.00 | (1334 × 2.5 + 100 × 15) / 1M = **$0.0048/call** |
| **GPT-5.5** | $5.00 | $30.00 | (1334 × 5 + 100 × 30) / 1M = **$0.0097/call** |
| **Claude Opus 4.7** | varies (premium) | varies | per OpenRouter ~$15/$75 → much higher; hindari untuk per-frame |

**1000 inferensi cloud / hari** = $2.30 (Gemini 3 Pro) hingga $9.70 (GPT-5.5). Untuk ChibiClaw user heavy 5000 actions/hari = **$11.50-48.50/hari user**. Tidak sustainable kalau full cloud untuk every user; **harus hybrid**.

### 5.3 Split Architecture Rekomendasi

**Strategi A: Local-first dengan cloud fallback** (best untuk privacy & cost):

```
1. Capture screenshot
2. Run on-device grounder (Gemma 4 E2B / MiniCPM-V 4.6) → confidence score + initial action
3. If confidence < threshold OR task labeled "complex" by lightweight router:
   → Send compressed screenshot + history to cloud planner (Claude 4.6 Sonnet)
   → Cloud returns refined plan
4. Local executor follows plan, re-prompt cloud if state changes unexpected
```

End-to-end latency: 1.5s (local-only path) atau 2.5-4s (hybrid path, dengan network).

**Strategi B: Speculative local + cloud verify** (latency-optimal):

```
1. Local fast model emits draft action (~1.5s)
2. Parallel: send to cloud for verification
3. Execute draft action; rollback if cloud disagrees within 2s
```

Tradeoff: lebih boros battery (parallel work) tapi user perceived latency rendah.

---

## 6. Real-World Report ChibiClaw-Like Project

### 6.1 Implementasi Multimodal Agent di Snapdragon 8 Series

Berikut yang ditemukan dari riset (bukan klaim halusinasi):

- **Apple FastVLM port ke Snapdragon NPU** (Qualcomm developer blog): demo running FastVLM-0.5B di NPU Snapdragon 8 Elite Gen 5, klaim TTFT 0.12s untuk image 1024×1024. **Vendor demo, bukan production app**.
- **Nexa AI on Snapdragon X2 Elite** (Qualcomm blog Mar 2026): framework multimodal AI agent untuk PC Snapdragon (bukan phone). Relevan untuk arsitektur tapi target device beda.
- **MiniCPM-V 4.6 deployment phones** (HuggingFace blog + OpenBMB): demo resmi iPhone 17 Pro Max, Redmi K70, Huawei nova 14. **Open-source demo apps tersedia**. Latency real-world tidak dispesifikasi numerik.
- **DroidRun** (aimultiple 2026 review): 43% success rate AndroidWorld 65 tasks subset; tidak clear apakah on-device penuh atau cloud-backed.
- **AutoDroid, AppAgent, Mobile-Agent v1/v2** — kebanyakan **cloud GPT-4o based** untuk planner. AppAgent menunjukkan latency ~180s per task multimodal (cloud bottleneck), AutoDroid 57s (faster, accessibility-tree based bukan murni vision).
- **Reddit r/LocalLLaMA**: banyak diskusi running Llama 3 / Qwen / Gemma di Snapdragon 8 Elite, tapi **mayoritas text-only**. Multimodal di mobile masih early.
- **r/Android**: laporan user reproducible terbatas untuk vision LLM mobile; sebagian besar review fokus camera AI dan ImageGen, bukan agent action grounding.

**Implikasi untuk ChibiClaw**: Tidak ada production app open-source yang verified bisa run **<2s vision-LLM action loop on-device** di Snapdragon 8 Elite Gen 5 per Mei 2026. ChibiClaw v4 vision-first **akan jadi early-mover** di area ini — butuh prototype sendiri untuk benchmark valid.

### 6.2 Latency Real-World (verified)

Hanya angka yang **bukan vendor claim**:
- Llama 3.2 3B Q4 di Snapdragon 8 Elite: **~10 tok/s** decode (third-party, multi-source).
- 7B model INT4: **~32 tok/s** (third-party).
- Llama 3.2 1B Q4 di OnePlus 12 (Gen 3): 50.2 tok/s decode, 260 tok/s prefill (ARM Learning Paths benchmark; **Gen 3 not Gen 5** — Gen 5 should be ~37% faster).
- ExecuTorch Llama 3.2 1B/3B: decode latency improved 2.5×, prefill 4.2× vs baseline (Meta blog).

### 6.3 Battery 1 Jam Continuous

Tidak ada laporan canonical 1-jam continuous agent activity. Ekstrapolasi:
- 50% battery in 90 min (one user report local AI heavy session) → ~30% / 1 hour worst case.
- 5% / 30 min optimized → 10% / 1 hour optimal case.
- Estimate ChibiClaw realistis: **15-25% / jam** untuk active agent dengan inference 1× per 3-5 detik.

---

## 7. Practical Recommendation untuk ChibiClaw v4

### 7.1 Phase 1: Multi-Adapter (Cloud Only Planner)

**Planner**: Claude Sonnet 4.6 (best document/chart understanding untuk Indonesian apps) atau Gemini 3 Pro (cheaper, large context 1M, good native multimodal).

**Justifikasi**:
- Tidak ada bottleneck local; iterate cepat.
- Cost per user heavy ~$5-15/hari acceptable untuk early adopter / beta.
- Untuk skala, pakai **prompt caching** (Anthropic & Google support) — bisa hemat 50-90% input cost untuk repeat-prefix.

**Model file size local**: 0 (cloud only).

### 7.2 Phase 2: Voice Integration (Cloud + Optional On-device Draft)

**Voice ASR**: Whisper.cpp small (74 MB) atau MiniCPM-V 4.6 (yang punya audio support 1.3B) → on-device untuk privacy.

**Voice planner**: tetap cloud (sama Phase 1).

**Optional on-device draft**: Gemma 4 E2B sebagai "predictive autocomplete" untuk command parsing sebelum cloud call. Hemat round-trip 200-400 ms.

**RAM budget**: ~3 GB (whisper-small ~250 MB + Gemma 4 E2B ~2.5 GB cached).

### 7.3 Phase 3: Vision-First (On-device Grounder + Cloud Fallback)

**On-device grounder (primary)**: **MiniCPM-V 4.6 (1.3B, GGUF Q4, ~2 GB)** via llama.cpp Android binding.

Rasionalisasi:
- Size ideal (1.3B param) — fits NPU/Adreno comfortably di Snapdragon 8 Elite Gen 5.
- **OpenBMB explicitly target phone deployment**; ada demo Android Redmi K70 (Snapdragon 8 Gen 3) referensi.
- License Apache-like, comfortable komersial.
- 256K context — bisa stack multiple screenshots untuk task multi-step.
- GGUF mature di llama.cpp — debugging community besar.

**Alternative on-device grounder**: **Gemma 4 E2B (LiteRT-LM)** kalau LiteRT-LM Kotlin API matang dan NPU acceleration verified di Hexagon Gen 5. Advantage: native Google AI Edge pipeline, Kotlin idiomatic, future-proof.

**On-device planner (mid-tier)**: opsional **Qwen3-VL-4B Q4** (~4 GB) jika user opt-in offline mode penuh. Latency ~3s tapi privacy 100%.

**Cloud fallback**: **Claude Sonnet 4.6** untuk task ambigu, multi-app, atau confidence local rendah. **Gemini 3 Pro** untuk video/screen-recording analysis (cheaper bulk).

**RAM budget realistis 16 GB Snapdragon device**:
```
- Android 15 + AOSP services         : 3.5 GB
- Chrome heavy (multi-tab)            : 2.5 GB
- ChibiClaw app + UI + state          : 0.5 GB
- Whisper-small (audio cached)        : 0.3 GB
- MiniCPM-V 4.6 Q4 (loaded NPU)       : 2.0 GB
- Inference scratch + KV cache 8K     : 1.0 GB
- OS reserve + buffer                 : 2.0 GB
-------------------------------------------
- Total                                : 11.8 GB / 16 GB
- Headroom buat foreground app target  : 4.2 GB
```

Realistis. Kalau user multitask 5+ Chrome tab + game di background, ada risk OOM kill — mitigate dengan **model lazy-load + unload on inactivity (e.g., 5 menit idle drop NPU weights)**.

### 7.4 Tabel Quick Pick Final

| Skenario | Pilihan Primary | Pilihan Alternative | Catatan |
|---|---|---|---|
| **On-device fast UI grounder** | MiniCPM-V 4.6 Q4 GGUF (llama.cpp) | Gemma 4 E2B (LiteRT-LM) | Both <2s estimate |
| **On-device mid-tier (offline mode)** | Qwen3-VL-4B Q4 GGUF | Gemma 4 E4B (LiteRT-LM) | Trade 1 sec latency for accuracy |
| **Cloud planner (default)** | Claude Sonnet 4.6 | Gemini 3 Pro | Sonnet better Indo/chart, Gemini cheaper |
| **Cloud heavy reasoning** | Claude Opus 4.7 (sparingly) | GPT-5.5 | Reserved for ambiguous / planning |
| **Local ASR** | Whisper.cpp small (250 MB) | MiniCPM-V 4.6 audio path | Privacy-first speech |
| **GUI-specialized (offline experiment)** | UI-TARS-2-7B Q4 (~6 GB) | GUI-Owl-7B Q4 | Tight RAM but **best GUI accuracy on-device** |

### 7.5 Action Items Konkret

1. **Prototype 1 minggu**: load MiniCPM-V 4.6 GGUF di Xiaomi 17 Pro Max via llama.cpp Android binding. Measure: TTFT untuk screenshot 1080p + 5-token JSON prompt, decode tok/s, RAM peak. Bandingkan dengan estimate Section 4.3.
2. **Prototype paralel 1 minggu**: integrate LiteRT-LM Kotlin API dengan Gemma 4 E2B. Measure same metrics. Compare developer ergonomics (Kotlin idiomatic vs JNI llama.cpp).
3. **Cloud benchmark**: pasang Claude 4.6 Sonnet + Gemini 3 Pro Bali workspace, jalankan 50 test prompt screenshot → action. Measure latency end-to-end (termasuk network ID-AU avg ~150 ms), accuracy vs ground truth.
4. **Eval suite internal**: rakit 30-50 task ChibiClaw-spesifik (buka app, scroll feed, beli sesuatu, fill form) di Android emulator + Xiaomi device. Jalankan baseline 3 model (MiniCPM-V 4.6, Gemma 4 E2B, Claude 4.6 cloud). Track: success rate, latency p50/p95, battery delta.
5. **Confidence router**: kalibrasi threshold confidence local (e.g., logprob avg < -2.0 → escalate cloud) berdasarkan eval suite hasil.
6. **Thermal soak test**: 30-menit continuous agent activity Xiaomi 17 Pro Max, log tok/s drop curve, battery drain. Build adaptive precision strategy.

---

## 8. Catatan Confidence & Validasi

Berikut klaim dalam laporan ini dan tingkat confidence:

| Klaim | Confidence | Catatan |
|---|---|---|
| Gemma 4 E2B/E4B rilis 2 Apr 2026 multimodal | Tinggi | Multi-source HF/Google blog |
| MiniCPM-V 4.6 rilis 11 Mei 2026, 1.3B, GGUF ~2 GB | Tinggi | OpenBMB & HF |
| Qwen3-VL 2B/4B GGUF tersedia | Tinggi | Repo HF resmi |
| UI-TARS-2 AndroidWorld 73.3% | Tinggi | Technical report arXiv 2509.02544 |
| GUI-Owl-7B ScreenSpot-Pro 54.9 | Tinggi | arXiv 2508.15144 |
| Snapdragon 8 Elite Gen 5 NPU 37% faster vs Gen 4 | Tinggi (vendor claim) | Qualcomm marketing |
| Llama 3.2 3B Q4 ~10 tok/s Snapdragon 8 Elite | Sedang-Tinggi | Multi-source independent |
| Peak 220 tok/s Snapdragon 8 Elite Gen 5 | Rendah-Sedang | **Theoretical peak**; bukan end-to-end realistic |
| Latency MiniCPM-V 4.6 ~1.7-2.0s end-to-end Xiaomi 17 Pro Max | Rendah | **[VERIFY]** — ekstrapolasi, butuh benchmark device |
| RAM peak estimate per model | Sedang | Berbasis quantization typical Q4_K_M assumptions |
| Battery 15-25% / jam continuous agent | Rendah-Sedang | Range data multi-source; perlu device validation |
| Cloud API pricing (per Mei 2026) | Tinggi | Multi-source vendor pricing pages |
| Snapdragon 8 Elite Gen 5 thermal throttle 2-3 menit sustained | Sedang | Multi-source termasuk arXiv 2603.23640 |

---

## 9. Sumber Utama

**Model & Repo**
- Gemma 4 — `https://blog.google/innovation-and-ai/technology/developers-tools/gemma-4/`, HF cards
- Phi-4-Multimodal — `https://huggingface.co/microsoft/Phi-4-multimodal-instruct`
- Qwen3-VL — `https://github.com/QwenLM/Qwen3-VL`, arXiv 2511.21631
- Qwen2.5-VL — HuggingFace org Qwen
- InternVL3.5 — arXiv 2508.18265, `https://github.com/OpenGVLab/InternVL`
- MiniCPM-V 4.6 — `https://huggingface.co/openbmb/MiniCPM-V-4.6`
- SmolVLM2 — `https://huggingface.co/HuggingFaceTB/SmolVLM2-2.2B-Instruct`, arXiv 2504.05299
- Molmo & PixMo — arXiv 2409.17146, Ai2 blog
- UI-TARS-2 — arXiv 2509.02544, `https://github.com/bytedance/UI-TARS`
- OS-Atlas — `https://github.com/OSU-NLP-Group/UGround`, ScreenSpot-Pro arXiv 2504.07981
- GUI-Owl & Mobile-Agent-v3 — arXiv 2508.15144, `https://github.com/X-PLUG/MobileAgent`
- PaliGemma 2 — `https://huggingface.co/blog/paligemma2`
- FastVLM — `https://machinelearning.apple.com/research/fast-vision-language-models`

**Benchmark**
- AndroidWorld — `https://openreview.net/forum?id=il5yUQsrjC`
- MobileWorld — arXiv 2512.19432
- MVISU-Bench — arXiv 2508.09057
- ScreenSpot-Pro — `https://likaixin2000.github.io/papers/ScreenSpot_Pro.pdf`, leaderboard `https://gui-agent.github.io/grounding-leaderboard/`
- MobileAgentBench — `https://mobileagentbench.github.io/`

**Inference Engine**
- MediaPipe LLM (Android) — `https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android`
- LiteRT-LM — `https://github.com/google-ai-edge/LiteRT-LM`, Kotlin guide `https://ai.google.dev/edge/litert-lm/android`
- ONNX Runtime QNN — `https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html`, blog Snapdragon `https://onnxruntime.ai/docs/genai/tutorials/snapdragon.html`
- ExecuTorch — `https://github.com/pytorch/executorch`, PyTorch beta blog
- MLC-LLM — `https://github.com/mlc-ai/mlc-llm`, blog Android
- llama.cpp Multimodal — `https://github.com/ggml-org/llama.cpp/blob/master/docs/multimodal.md`
- Qualcomm Hexagon NPU SDK — `https://www.qualcomm.com/developer/software/hexagon-npu-sdk`
- Qualcomm AI Hub — `https://aihub.qualcomm.com/models`

**Hardware & Benchmark**
- Xiaomi 17 Pro Max — `https://www.gsmarena.com/xiaomi_17_pro_max_5g-14181.php`
- Snapdragon 8 Elite Gen 5 review — `https://xpu.pub/2025/10/06/qualcomm-snapdragon-8-elite-gen-5/`, `https://futurumgroup.com/insights/is-snapdragon-8-elite-gen-5-the-benchmark-for-next-gen-flagship-phones/`
- LLM mobile platform benchmark — arXiv 2410.03613
- LLM Inference Edge Sustained Load — arXiv 2603.23640
- Edge & Mobile LLM Leaderboard 2026 — `https://awesomeagents.ai/leaderboards/edge-mobile-llm-leaderboard/`

**Cloud API Pricing**
- Claude API — `https://platform.claude.com/docs/en/about-claude/pricing`, `https://benchlm.ai/blog/posts/claude-api-pricing`
- Gemini API — `https://ai.google.dev/gemini-api/docs/pricing`
- OpenAI API — `https://openai.com/api/pricing/`
- Vision API comparison — `https://tokenmix.ai/blog/vision-api-comparison`

---

**Akhir laporan**. Update dianjurkan kuartalan — rilis model multimodal mobile sangat cepat (MiniCPM-V 4.6 baru rilis 2 hari lalu).
