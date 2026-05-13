# Riset Mendalam: Voice/TTS dengan Emosi Natural untuk Persona "Fuu"

**Project**: ChibiClaw v4 + VRM Assistant Android
**Tanggal akses sumber**: 13 Mei 2026
**Versi dokumen**: 1.0
**Target**: TTS expressive setara VTuber/manusia untuk persona Fuu (lembut, kawaii), bahasa Indonesia native, latency <2.5 detik di Snapdragon 8 Elite Gen 5

---

## 1. Ringkasan Eksekutif

Lanskap TTS open-source di Mei 2026 berbeda jauh dibanding 2024. Tiga tren besar yang relevan dengan kebutuhan persona "Fuu":

1. **Model 0.3-2B parameter** kini standar untuk zero-shot voice cloning yang ekspresif (IndexTTS 2, Chatterbox, F5-TTS, Higgs Audio v2, Spark-TTS, CosyVoice 2).
2. **Kontrol emosi** beralih dari "label kategori" (happy/sad) menjadi tag teks natural language plus reference audio emosi terpisah dari reference timbre (terobosan IndexTTS 2 dan Higgs Audio v2).
3. **Deployment Android** matang lewat ONNX Runtime + QNN Execution Provider Qualcomm, ExecuTorch 1.2, dan Sherpa-ONNX. Namun model yang benar-benar bisa real-time on-device masih terbatas pada keluarga **Piper, Kokoro-82M, dan Matcha-TTS**; sisanya butuh GPU atau cloud.

**Bottom line untuk Fuu**:
- **Prototype tercepat (1-2 minggu)**: pakai **ElevenLabs v3 Multilingual** atau **gpt-realtime-mini** sebagai cloud TTS sambil bangun pipeline. Suara langsung expressive, ada Indonesian, latency cloud 200-400 ms.
- **MVP offline (1-2 bulan)**: **GPT-SoVITS v2Pro** atau **F5-TTS-Indo-Finetune** untuk fine-tuning persona Fuu di server, lalu inference via WebSocket dari Android. Kualitas studio, kontrol gaya via reference audio.
- **Endgame on-device (3-6 bulan)**: **Kokoro-82M custom Indonesian** (perlu nunggu/kontribusi voice ID di Kokoro, atau finetune lokal) via Sherpa-ONNX di Android. Belum tentu ekspresif setara cloud, tapi 100% offline dan privasi terjamin.

Detail lengkap di bawah.

---

## 2. Lanskap TTS Engine Open Source (Mei 2026)

### 2.1 Tabel Ringkas

Catatan: Angka parameter dan latency adalah perkiraan dari benchmark publik per Mei 2026. Beberapa angka **perlu verifikasi terbaru** karena rilis baru muncul mingguan.

| Engine | Versi | Lisensi | Param | ID Support | Emotion Control | Voice Clone | Mobile-ready | Catatan VTuber-grade |
|---|---|---|---|---|---|---|---|---|
| **Kokoro-82M** | v1.0+ | Apache-2.0 | 82M | Belum (issue terbuka) | Terbatas (per-voice prosody) | Tidak (preset suara) | Ya, ONNX | Suara studio tapi datar; fondasi StyleTTS 2 |
| **Piper** | Stable | MIT | 5-25M | Ya (multi-voice) | Tidak | Tidak | Ya, ONNX, RTF 0.2 di RPi | Robotik untuk persona kawaii; bagus untuk fallback |
| **StyleTTS 2** | v1 (yl4579) | MIT (kode) / CC-BY (model) | ~150M | Tidak native | embedding_scale (CFG) untuk emotionality | Ya (sampel pendek) | Berat, butuh GPU; ada port ONNX eksperimental | Bagus base research, kurang siap produksi mobile |
| **IndexTTS 2** | 2.0 (Sep 2025) + 2.5 TR (Jan 2026) | Apache-2.0 | ~1B | Mainly EN/ZH, multilingual-ready | Reference audio emosi terpisah + Qwen3 soft instruction | Zero-shot 3-10 dtk | GPU saja saat ini | **Top pick untuk VTuber-grade emotion**; perlu fine-tune ID |
| **GPT-SoVITS v2Pro** | 20250606v2pro (Jun 2025) | MIT | ~330M | Bisa via fine-tune (community fork) | Reference audio gaya | Few-shot 1 menit | Server saja praktis | **Standar de facto VTuber/anime clone**, Bilibili origin |
| **F5-TTS** | v1.x + Indo finetune (Eempostor) | MIT | ~330M | Ya (community finetune `Eempostor/F5-TTS-INDO-FINETUNE`) | Reference audio | Zero-shot 5-15 dtk | DakeQQ/F5-TTS-ONNX → bisa mobile, perlu quantization | Indonesia tersedia tapi reviewer note: emotion match tidak konsisten |
| **OpenVoice v2** | 2.0 (MyShell) | MIT | ~150M | Tidak listed (EN, ES, FR, ZH, JA, KO) | Granular: emosi, accent, rhythm, pauses, intonation | Instant clone | Cukup ringan tapi belum optimized ARM | Bisa "zero-shot lintas bahasa"; tapi ID belum native |
| **CosyVoice 2** | 2.0 + 0.5B | Apache-2.0 | 0.5B | Multilingual + dialect, ID belum dikonfirmasi | Tag emosi + dialect/aksen | Zero-shot | GPU; streaming via Alibaba | Reduce pronunciation error 30-50% vs v1 |
| **Chatterbox / Chatterbox-Turbo** | Latest 2026 | MIT | 350M (Turbo) | EN + Multilingual variant; ID tidak listed | **Emotion exaggeration knob** + tag `[laugh][cough]` | Zero-shot | Realtime di GPU, mobile butuh quantization | Pertama open-source dengan emotion exaggeration; cocok karakter dramatis |
| **Higgs Audio v2** | 2.0 (3B base) | Apache-2.0 | 3B | Multi-language (perlu verifikasi ID) | Auto prosody + humming + multi-speaker | 3-10 dtk reference | GPU saja realistis | Win-rate 75.7% vs gpt-4o-mini-tts pada EmergentTTS Emotions |
| **Sesame CSM-1B** | 1B (Apr 2025) + Q1 2026 demo | Apache-2.0 | 1B + 100M decoder | Multilingual finetune memungkinkan | Behavior tokens (pauses, "ums", breaths) | Yes (custom voices) | Edge belum praktis | Conversational; cocok untuk LLM-pipeline bukan dub script |
| **VibeVoice / VibeVoice-Realtime** | 0.5B Realtime, 1.5B | MIT | 0.5B / 1.5B | EN + ZH primary | Multi-speaker turn-taking | Reference voice | Realtime variant ringan | **CATATAN PENTING**: Microsoft menarik kode VibeVoice-TTS dari repo (responsible AI); cek availability terbaru |
| **Spark-TTS** | 0.5B (Mar 2025) | Apache-2.0 | 0.5B | EN + ZH native | Pitch, rate, emotion, gender control | Zero-shot | Streaming-ready | LLM-based (Qwen2.5), arsitektur single-stream |
| **Fish-Speech / Fish-Audio S2 Pro** | S2 Pro (Q1 2026) | Open weights + komersial | 1.5B+ | 80+ bahasa, perlu cek ID | Sub-word prosody tags via natural language | Zero-shot kuat | API/server | Pemenang Audio Turing Test 2026 (0.515) vs Seed-TTS, MiniMax |
| **Style-Bert-VITS2** | Latest litagin02 | AGPL-3.0/MIT mix | ~80M | JA/EN/ZH; ID via finetune sendiri | Style vector + intensitas fine-grained | Yes | Cukup ringan (CPU-friendly) | **VTuber Jepang favorit** (Hololive-style models ada di Replicate) |
| **XTTS v2 (Idiap fork)** | Maintained fork | CPML (model) | ~470M | 17 bahasa termasuk Indonesia? perlu verifikasi | Reference audio | 6 detik | GPU disarankan | Coqui shutdown Des 2025; community fork aktif `idiap/coqui-ai-TTS` |
| **MeloTTS** | Latest | MIT | Kecil | Multi-language termasuk ID? cek per voice | Terbatas | Tidak (preset) | CPU-friendly | Sering muncul di pipeline VTuber LLM |

### 2.2 Ranking untuk "VTuber-grade ID emotional voice"

Kriteria gabungan: kualitas emosi + dukungan bahasa Indonesia + ekosistem komunitas + feasibility on-device:

**Tier S (target endgame)**
1. **IndexTTS 2 / 2.5** — kontrol emosi terbaik di kelas open-source. Reference audio terpisah untuk timbre vs emosi adalah game-changer. Kelemahan: butuh GPU server, fine-tune ID belum tersedia publik.
2. **GPT-SoVITS v2Pro** — kualitas voice clone level produksi VTuber Mandarin/Jepang sudah terbukti di Bilibili. Fine-tuning ID praktis dengan 30-60 menit audio.

**Tier A (prototype-ready cloud/server)**
3. **F5-TTS Indonesia (Eempostor)** — satu-satunya open-source ID-native yang siap dipakai. Reviewer mencatat emotion kadang kurang nempel, tapi untuk teks normal sudah natural.
4. **Higgs Audio v2** — emosi bagus, prosody otomatis, tapi 3B besar dan ID belum dikonfirmasi.
5. **Chatterbox Multilingual / Turbo** — emotion exaggeration knob unik; cek Indonesia di multilingual variant.

**Tier B (offline-ready ringan)**
6. **Kokoro-82M** — Indonesia belum ada tapi arsitektur StyleTTS 2 mendukung. Kualitas studio. Suara ekspresif terbatas pada preset voices.
7. **Style-Bert-VITS2** — kalau mau effort training sendiri dengan dataset ID, kontrol gaya paling fine-grained di kelas ringan.

**Tier C (fallback)**
8. **Piper** — sebagai emergency offline fallback yang pasti jalan di Android. Suara robotik tidak cocok untuk Fuu kawaii.

---

## 3. Voice Cloning untuk Persona Character

### 3.1 Persyaratan Audio Sample

Berdasarkan dokumentasi resmi tiap model per Mei 2026:

- **Zero-shot 3-10 detik**: Higgs Audio v2, F5-TTS, OpenVoice v2, Chatterbox, CosyVoice 2, IndexTTS 2, XTTS v2. Hasil "okay" tapi belum studio.
- **Few-shot 30 detik - 1 menit**: GPT-SoVITS (≥1 menit) menghasilkan kemiripan 80-95%.
- **Fine-tune full quality 30 menit - 2 jam**: Style-Bert-VITS2, F5-TTS finetune, GPT-SoVITS v2Pro extended training. Hi-Fi TTS Dataset mensyaratkan minimal 30 menit per speaker.
- **Production VTuber clone (Hololive-style models di Replicate)**: 3-10 jam diarisasi rapi, transkripsi forced-aligned, denoising agresif.

### 3.2 Cloning Character Voice (Anime / Dub / Voice Actor)

Secara teknis: **bisa**. Banyak komunitas memakai `animespeechdataset` (deeplearningcafe/animespeechdataset) untuk ekstrak suara karakter dari subtitle anime dan melatih voice clone.

Secara legal/etis (per Mei 2026): **risiko tinggi dan tidak disarankan** kecuali ada izin tertulis.

- **Tennessee ELVIS Act (2024)** dan **US AI Transparency and Voice Rights Act (awal 2026)** memberi hak publisitas khusus untuk replikasi suara digital tanpa izin.
- **EU AI Act** efektif penuh 2 Agustus 2026 mensyaratkan disclosure deepfake.
- **Hololive guidelines** secara eksplisit melarang ekstraksi suara talent dari lagu untuk speech synthesis.
- Yurisprudensi AS sejak Bette Midler vs Ford Motor (1988) dan Tom Waits vs Frito-Lay (1992) menegaskan suara distinctive = identitas. Sound-alike komersial = tortious appropriation.

**Rekomendasi legal untuk Fuu**:
- Hindari clone seiyuu/voice actor komersial spesifik.
- Hire voice actor Indonesia (mis. via marketplace seperti Sribu, Fiverr ID, atau studio dubbing lokal) untuk rekam 30-60 menit dialog persona Fuu dengan kontrak yang **eksplisit memberi hak training dan synthesis turunan**.
- Estimasi biaya VA ID hobi: Rp 500k-2 jt untuk 60 menit unscripted; profesional Rp 5-15 jt.
- Alternative legal-safe: blend beberapa preset Kokoro voices, atau buat persona suara baru via fine-tune dari dataset public-domain Indonesia (Common Voice ID).

### 3.3 Dataset Public Indonesia

- **Mozilla Common Voice Indonesian**: gratis CC0, kualitas variabel (crowd-sourced), bisa di-filter via vote score. Cocok base pre-training.
- **OpenSLR resources** untuk Indonesia: LJ-style dataset belum sebanyak EN/JA.
- **AniSpeech dataset (Hugging Face `ShoukanLabs/AniSpeech`)**: anime EN, bukan ID, tapi useful untuk transfer intonasi anime ke pelafalan ID.
- **TITML-IDN, INDspeech, Indonesia LibriVox derivatives**: variabel kualitas; cek lisensi per dataset.

### 3.4 Fine-tune ID + Intonasi Anime

Strategi yang terbukti di komunitas (per diskusi GPT-SoVITS dan F5-TTS issues):

1. **Pre-train base** pada Common Voice ID (50-200 jam) untuk fonem ID.
2. **Fine-tune speaker** dengan 30-60 menit voice actor yang sengaja membaca dengan intonasi anime-kawaii.
3. **Style adapter** (kalau pakai Style-Bert-VITS2 atau IndexTTS 2): ekstrak style vector dari 5-10 detik reference audio anime per emosi (happy, sad, excited, shy).
4. **Code-switching ID/EN**: GPT-SoVITS v3/v4 dan F5-TTS handle code-switch baik kalau ada di training data; pastikan dataset campur kalimat ID dengan istilah EN.

---

## 4. Real-Time Inference di Android (Snapdragon 8 Elite Gen 5)

### 4.1 Stack yang Tersedia

| Stack | Cocok untuk | Catatan |
|---|---|---|
| **ONNX Runtime Mobile + QNN EP** | Kokoro, Piper, F5-TTS-ONNX, StyleTTS 2 port | QNN Execution Provider akselerasi via Hexagon NPU di Snapdragon. Dukungan stabil ORT 1.19+ |
| **ExecuTorch 1.2** (PyTorch) | Voxtral Realtime, Parakeet, model PyTorch native | Maven Central `org.pytorch:executorch-android`. Belum banyak TTS examples |
| **Sherpa-ONNX** | Kokoro-82M, Piper, Matcha-TTS, VITS | Wrapper paling siap pakai. Sudah ada VoxSherpa TTS APK referensi |
| **LiteRT (Google)** | TFLite-converted models | NPU support via Qualcomm partnership 2025 |
| **MediaPipe Audio Edge** | Solusi end-to-end Google | Belum punya TTS expressive lineup setara di atas |
| **llama.cpp / GGML** | LLM, beberapa eksperimen TTS | Tidak ideal untuk TTS karena pipeline non-LM |

### 4.2 Latency Budget untuk Snapdragon 8 Elite Gen 5

Snapdragon 8 Elite Gen 5 mengeksekusi Llama 3.2 3B dengan time-to-first-token ~100 ms (prompt 128 tokens). Untuk TTS yang lebih ringan:

Pipeline target end-to-end <2.5 detik untuk Fuu:

```
Text input
  -> [Phoneme/G2P + BERT context]  ~50-100 ms
  -> [Acoustic model (DiT / VITS / Flow-match)]  bottleneck utama
  -> [Vocoder (HiFi-GAN / iSTFT-Net / WaveNet)]  ~50-200 ms
  -> [PCM 24kHz output]
```

**Benchmark publik per Mei 2026 (semua RTF; lebih kecil lebih cepat)**:
- Piper di Raspberry Pi 4 CPU: RTF 0.20
- Kokoro-82M di Android G99 CPU: RTF 0.45 (≈ 1 detik output butuh 0.45 detik proses)
- Kokoro-82M di GPU RTX 4090: RTF 0.03-0.06
- StyleTTS 2 di CPU desktop: RTF 0.5-1.0 (borderline real-time)
- F5-TTS di GPU: RTF 0.1-0.3; mobile ARM CPU **diperkirakan** RTF 1.0+ (perlu verifikasi)

Untuk Snapdragon 8 Elite Gen 5 (CPU 8-core ARMv9 + Hexagon NPU + Adreno GPU), perkiraan optimistis dengan QNN/NPU:
- **Kokoro-82M**: RTF ~0.15-0.25 (sangat feasible)
- **Piper**: RTF ~0.10 (over-spec'd, jadi cocok fallback)
- **F5-TTS quantized INT8**: RTF ~0.5-0.8 (borderline; perlu streaming agar TTFB rendah)
- **IndexTTS 2 / Higgs Audio**: **tidak praktis on-device** karena ukuran >1B parameter

**Angka di atas sebagian estimasi**; benchmark eksklusif Snapdragon 8 Elite Gen 5 untuk TTS expressive belum diterbitkan publik per Mei 2026.

### 4.3 Streaming vs Full-Utterance

- **Streaming** (chunk-by-chunk): TTFB (time-to-first-byte audio) bisa <300 ms kalau model dan vocoder mendukung incremental. Cocok untuk persepsi "ngomong langsung". Kokoro, Spark-TTS, VibeVoice-Realtime, CosyVoice 2 mendukung streaming. GPT-SoVITS juga punya streaming inference mode.
- **Full-utterance**: lebih simpel implementasinya, total latency = panjang teks × RTF + overhead startup. Untuk teks pendek (10-15 detik audio) latency 1.5-2.5 detik OK; untuk teks panjang harus streaming.

**Rekomendasi**: Untuk Fuu yang respon LLM, pakai **streaming pipeline** dengan partial token feeding — sinkron dengan token streaming dari LLM (mis. Gemma 4 atau backend cloud). Cegah audio gap dengan buffer 1-2 chunk.

---

## 5. Mekanisme Kontrol Emosi

### 5.1 Pendekatan dan Maturitas

| Pendekatan | Contoh Model | Maturitas | Cocok untuk Fuu? |
|---|---|---|---|
| **Reference audio prompt** (style transfer dari sampel) | IndexTTS 2 (emotion ref terpisah), F5-TTS, OpenVoice v2, GPT-SoVITS | Tinggi | **Sangat** — siapkan bank 5-10 reference Fuu per emosi |
| **Text tags / paralinguistic** (`[laugh]`, `<emotion happy>`) | Chatterbox Turbo, Fish-Audio S2, Sesame CSM, IndexTTS 2 (soft instruction) | Tinggi | Bisa diintegrasikan dengan output LLM |
| **Continuous emotion vector** (arousal-valence-dominance) | EmoSphere-TTS, EmoSphere++, ECE-TTS | Akademik, belum production | Powerful tapi belum siap pakai |
| **LLM-guided prosody** (LLM keluarkan SSML/marker) | OpenAI gpt-realtime, Anthropic experimental, Sesame CSM | Cloud-mature, on-device baru | Strategi terkuat untuk persona konsisten |
| **Emotion exaggeration scalar** | Chatterbox (`exaggeration` parameter) | Production-ready | Cocok untuk "high-pitch kawaii" dramatic moments |
| **CFG scale untuk emotionality** | StyleTTS 2 `embedding_scale`, Kokoro turunan | Sederhana, terbukti | Tuning kasar, OK sebagai knob runtime |

### 5.2 Repo dengan Demo Emotion Convincing

- **IndexTTS 2 demo**: https://index-tts.github.io/ (zero-shot dengan emotion ref yang berbeda dari timbre ref)
- **Higgs Audio v2 demo**: Boson AI blog menunjukkan humming, multi-speaker dialogue, breath
- **EmoSphere-TTS samples**: arxiv.org/abs/2406.07803 (akademik tapi audio listenable)
- **Sesame CSM demo**: sesame.com/research/crossing_the_uncanny_valley_of_voice — kualitas konversasional
- **Chatterbox demopage**: resemble-ai.github.io/chatterbox_demopage/ (emotion exaggeration A/B)
- **Hololive-Style-Bert-VITS2**: replicate.com/zsxkib/hololive-style-bert-vits2 (style intensity per karakter)

### 5.3 Pipeline LLM-Guided yang Layak Dipakai

Karena ChibiClaw v4 sudah punya LLM (Gemma / Claude / Llama), strategi paling pragmatis:

1. LLM diberi system prompt: "Saat respond, tulis dialog Fuu, lalu tag `<emo:happy intensity:0.6>` per kalimat berdasarkan konteks."
2. Parser di Android ambil tag, map ke parameter TTS:
   - Untuk **GPT-SoVITS**: pilih reference audio dari folder per emosi.
   - Untuk **Chatterbox**: set `exaggeration` dan `cfg_weight`.
   - Untuk **IndexTTS 2**: pilih emotion reference clip.
3. Audio output buffered + streamed ke speaker/avatar lipsync.

Pendekatan ini lebih konsisten dibanding mengandalkan TTS auto-detect emosi dari teks.

---

## 6. VTuber-Grade Pipeline Reference

### 6.1 Industri Jepang

- **VOICEVOX** — open-source, suara karakter Zundamon/Metan, sintetis tapi memorable. Cocok untuk character voice non-realistic. Free + commercial.
- **VOICEPEAK** — komersial AI Inc. Kualitas naratif natural, intuitif. Lisensi per voice character.
- **CoeFont** — SaaS Jepang berbayar, lebih natural daripada VOICEVOX.
- **CeVIO AI** — VTuber-friendly singing + speech.

**Equivalen open-source**: tidak ada yang persis menyamai polish karakter, tapi **Style-Bert-VITS2 + Hololive-style finetune** mendekati. Untuk konteks Indonesia, ini juga template yang bisa direplikasi.

### 6.2 Pipeline Hololive / Nijisanji

Berdasarkan informasi publik (per Mei 2026):

- Talent live streams pakai **suara asli + Live2D/3D rigging**; **bukan TTS**.
- TTS pakai untuk shorts, reaction memes, fan content — sering dengan Style-Bert-VITS2 community models di Replicate (mis. `zsxkib/hololive-style-bert-vits2`).
- **Hololive guidelines (2024+)** secara eksplisit melarang ekstraksi suara talent untuk synthesis pihak ketiga.

**Implikasi**: jangan jadikan "pipeline Hololive resmi" sebagai referensi karena memang tidak pakai TTS di main content. Yang relevan adalah **community VTuber AI** seperti Neuro-sama (Vedal), yang pakai stack custom mirip GPT-SoVITS / Style-Bert-VITS2 + LLM custom.

### 6.3 Open-Source VTuber Framework

- **Open-LLM-VTuber** (GitHub `Open-LLM-VTuber/Open-LLM-VTuber`) — hands-free voice interaction, voice interruption, Live2D, dukung beragam TTS backend (sherpa-onnx, MeloTTS, Coqui-TTS, GPT-SoVITS, Bark, CosyVoice, Edge TTS, Fish Audio, Azure TTS).
- **VoiceToJapanese** (0Xiaohei0) — pipeline AI VTuber Whisper + translate + VOICEVOX.
- **Linly-Talker** — LLM + visual model + lip-sync MuseTalk.

Stack-stack ini bagus sebagai referensi arsitektur untuk VRM Assistant.

---

## 7. Cloud Option vs Offline

### 7.1 Cloud Options (per Mei 2026)

| Provider | Model Terbaik | Harga | Latency | Emotion | Character Persistence | Catatan |
|---|---|---|---|---|---|---|
| **ElevenLabs** | Multilingual v3, Flash, Turbo v3 | $0.06-0.12 per 1k karakter | TTFB ~75 ms (Flash/Turbo) | v3 punya emotional tags, audio tags | Voice library + Professional Voice Clone | Tier Creator $22/bln sudah profesional clone. Bahasa Indonesia didukung di v3 multilingual |
| **OpenAI gpt-realtime / gpt-realtime-mini** | gpt-realtime, GPT-Realtime-2 (high/xhigh) | $32/M input audio token, $64/M output (~$0.30 per menit dialog) | Sub-detik (realtime API) | Instruction-driven (LLM steering) | Preset 8-10 voices; consistency via prompt | gpt-realtime-mini di-rilis akhir 2025 untuk cost-sensitive |
| **Google Cloud TTS Chirp HD / Chirp 3** | Chirp HD voices | Per-character, mirip kisaran $4-16 per 1M karakter | <300 ms | Terbatas (SSML prosody) | 200+ voice ID inc. Indonesia | Konservatif emosionalnya |
| **Azure Cognitive Speech** | Neural voices + Custom Neural Voice | Tier mulai $4 per 1M karakter | <300 ms | Express-as styles untuk subset voice | Indonesia (id-ID) Andika, Gadis | Express-as belum di voice ID |
| **Fish Audio S2 (API)** | S2 Pro | Per character via API | Sub-detik | Sub-word prosody natural-language tag | Voice library + custom clone | Pemenang Audio Turing Test 2026 |
| **Resemble AI hosted Chatterbox** | Production hosting | Per character | Sub-detik | Exaggeration knob | Voice library | Open-source mirror dengan SLA |

### 7.2 Justifikasi Biaya untuk ChibiClaw

Target ChibiClaw: 50 commands/day, asumsi rata-rata 100 karakter respons (Fuu reply pendek):

- **ElevenLabs Flash**: 50 × 100 × 30 hari = 150k karakter/bulan = **~$9/bulan** (di bawah tier Creator $22). Bisa cukup pakai pay-as-you-go Starter $5/bulan + overage atau tier Creator.
- **gpt-realtime-mini**: 50 commands × ~6 detik audio reply × 30 hari = 150 menit/bulan ≈ **~$45/bulan** (lebih mahal karena bayar input + output audio token).
- **Google Cloud TTS Chirp HD**: ~$2-3/bulan untuk volume itu, tapi emosionalnya kurang VTuber.
- **Fish Audio S2 Pro API**: estimasi ~$10-15/bulan, kualitas top.

**Verdict ChibiClaw personal use**:
- **Bulan 1-3 (prototype)**: ElevenLabs Creator $22/bln, pakai Indonesian voice + emotion tags v3. **Justified** untuk validasi UX cepat.
- **Bulan 4+ (offline-first)**: pindah ke GPT-SoVITS / Kokoro on-device atau di-host di server pribadi (mis. Lightsail BIGNET-ID yang sudah ada untuk VIONA). Biaya jadi sunk-cost server, bukan per-command.

### 7.3 Privasi

Untuk persona Fuu yang mungkin dialog personal/sensitif, **on-device offline** secara fundamental lebih aman daripada cloud TTS yang harus echo teks ke server. Pertimbangan ini menjustifikasi investasi engineering ke jalur offline meski lebih lambat ROI.

---

## 8. Concrete Recommendation untuk Fuu

### 8.1 Prototype Cepat (Minggu 1-2)

**Tujuan**: validasi UX, lihat seberapa "alive" Fuu terasa.

1. **ElevenLabs API** Creator tier ($22/bln) dengan voice Indonesian dari library + Professional Voice Clone kalau sudah ada actress.
2. Pipeline Android: ChibiClaw → LLM → text + emotion tag → ElevenLabs streaming WebSocket → AudioTrack.
3. Latency target: 200-500 ms TTFB.
4. Tag emotion via Eleven v3 `[whispers]`, `[laughs]`, `[soft]`, `[excited]` di teks LLM.
5. Backup voice 1-2 character lain di voice library untuk A/B test persona.

**Deliverable**: video demo Fuu ngomong responsive dengan emosi konteks-aware.

### 8.2 MVP Server-Backed (Bulan 1-2)

**Tujuan**: kontrol penuh persona, lepas dari biaya per-karakter.

1. **GPT-SoVITS v2Pro** di server pribadi (Lightsail GPU instance atau workstation rumah).
2. Rekam **30-60 menit voice actor Indonesia** dengan beragam intonasi (neutral, excited, sad, shy, playful) — kontrak training rights eksplisit.
3. Fine-tune GPT-SoVITS dengan dataset itu (1-2 hari training di RTX 4090 atau setara).
4. Setup WebSocket inference server. Pipeline Android tetap streaming.
5. Folder reference audio per emosi (5-10 clip 3-5 detik) untuk style switching runtime.
6. Latency target: 500-1500 ms (jaringan + inference). Streaming partial chunk.

**Deliverable**: Fuu dengan suara unik, konsisten, milik project sendiri, biaya marginal nol.

### 8.3 Endgame On-Device (Bulan 3-6)

**Tujuan**: privasi total, offline kapan saja, no recurring cost.

Dua jalur paralel:

**Jalur A: Kokoro Indonesian custom**
1. Tunggu (atau kontribusi) dukungan ID resmi di Kokoro upstream.
2. Atau: fine-tune StyleTTS 2 base + Common Voice ID + 30 menit Fuu voice → export ke ONNX → quantize INT8 → deploy via Sherpa-ONNX.
3. Inference RTF target <0.3 di Snapdragon 8 Elite Gen 5.

**Jalur B: F5-TTS quantized**
1. Mulai dari `Eempostor/F5-TTS-INDO-FINETUNE` di Hugging Face.
2. Fine-tune lebih lanjut dengan voice Fuu.
3. Export ONNX via `DakeQQ/F5-TTS-ONNX`.
4. INT8 quantize untuk Hexagon NPU via QNN.
5. Deploy lewat ONNX Runtime Mobile.

**Risiko**: F5-TTS di mobile masih eksperimental, mungkin perlu fallback ke Kokoro/Piper kalau RTF tidak achievable.

### 8.4 Untuk VRM Assistant Specific

Karena VRM perlu lip-sync, prioritaskan:

1. TTS yang **output viseme/phoneme timeline** atau bisa di-derive (semua model di atas bisa via forced alignment).
2. **Mascot Bot SDK / Mascot Engine** sudah handle real-time viseme playback dari TTS output — bisa jadi inspirasi arsitektur.
3. Untuk VRM avatar, sinkronisasi VRMBlendShape + Unity SAPI / VRMA jaw-sync dengan output PCM dari pipeline TTS sama (apapun engine).

---

## 9. Kewaspadaan dan Catatan Verifikasi

Item yang **perlu verifikasi terbaru** karena ekosistem bergerak cepat:

- Status update versi terbaru StyleTTS 2 (yl4579 jarang push; cek dialohq/StyleTTS2-pkg fork).
- Kepastian Indonesian native di OpenVoice v2 dan CosyVoice 2 (kemungkinan via finetune saja).
- Apakah Microsoft sudah mengembalikan VibeVoice-TTS code atau tetap dicabut.
- Benchmark presisi TTS pada Snapdragon 8 Elite Gen 5 — angka di atas adalah ekstrapolasi.
- Lisensi pasti F5-TTS-INDO-FINETUNE community fork.
- Detail IndexTTS 2.5 Technical Report (Jan 2026) — apakah ada ID di pretrain set.

Untuk go-decision, **verifikasi langsung ke**:
- GitHub releases page tiap repo.
- Hugging Face model card untuk lisensi terbaru.
- Discord komunitas (VoicePedia, Sherpa-ONNX, GPT-SoVITS Bilibili).
- Subreddit r/LocalLLaMA dan r/TTS untuk benchmark mobile terbaru.

---

## 10. Action Items Konkret untuk Anda

Berurutan dari pengaruh tertinggi dan effort terendah:

1. **Hari 1-3**: Daftar ElevenLabs Creator $22, generate 5 sample Fuu Indonesian dengan beragam emotion tag v3, lakukan blind test sama 3-5 teman untuk pilih voice character.
2. **Hari 4-7**: Implementasi streaming TTS Android dengan ElevenLabs WebSocket di ChibiClaw v4. Ukur perceived latency end-to-end.
3. **Minggu 2-3**: Tulis system prompt LLM untuk emit emotion tag yang Fuu-konsisten. Validasi konteks-awareness.
4. **Minggu 3-4**: Cast voice actor Indonesia (cek Sribu / network telco BIGNET-ID), kontrak training rights, rekam 30 menit.
5. **Bulan 2**: Setup GPT-SoVITS server, fine-tune Fuu, integrasi WebSocket pengganti ElevenLabs.
6. **Bulan 3-4**: Eksperimen export ONNX (F5-TTS quantized atau Kokoro custom) untuk jalur on-device.
7. **Bulan 5-6**: Integrasi VRM Assistant lip-sync menggunakan pipeline TTS yang sudah matang.

Total budget perkiraan:
- ElevenLabs 3 bulan: ~$66
- Voice actor 30-60 menit: Rp 2-15 juta
- Server inference GPU: Rp 1-2 juta/bulan kalau pakai GPU spot, atau gratis kalau pakai PC desktop sendiri
- Engineering time: 6 bulan part-time

---

## 11. Daftar Tautan Sumber Utama

### Engine Inti
- StyleTTS 2: https://github.com/yl4579/StyleTTS2
- Kokoro-82M: https://huggingface.co/hexgrad/Kokoro-82M
- Piper TTS: https://github.com/rhasspy/piper
- F5-TTS: https://github.com/SWivid/F5-TTS
- F5-TTS Indonesia finetune: https://huggingface.co/Eempostor/F5-TTS-INDO-FINETUNE
- GPT-SoVITS: https://github.com/RVC-Boss/GPT-SoVITS
- OpenVoice v2: https://github.com/myshell-ai/OpenVoice
- CosyVoice 2: https://github.com/FunAudioLLM/CosyVoice
- IndexTTS 2: https://github.com/index-tts/index-tts
- Chatterbox: https://github.com/resemble-ai/chatterbox
- Higgs Audio v2: https://github.com/boson-ai/higgs-audio
- VibeVoice: https://github.com/microsoft/VibeVoice
- Sesame CSM: https://github.com/SesameAILabs/csm
- Spark-TTS: https://github.com/SparkAudio/Spark-TTS
- Fish-Speech: https://github.com/fishaudio/fish-speech
- Style-Bert-VITS2: https://github.com/litagin02/Style-Bert-VITS2
- Bert-VITS2 (original): https://github.com/fishaudio/Bert-VITS2
- Idiap Coqui TTS fork: https://github.com/idiap/coqui-ai-TTS

### Deployment Mobile
- Sherpa-ONNX: https://github.com/k2-fsa/sherpa-onnx
- ExecuTorch: https://github.com/pytorch/executorch
- ONNX Runtime Mobile: https://onnxruntime.ai/docs/tutorials/mobile/
- QNN Execution Provider: https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html
- F5-TTS ONNX port (DakeQQ): https://github.com/DakeQQ/F5-TTS-ONNX

### Dataset
- Mozilla Common Voice ID: https://commonvoice.mozilla.org/id
- AniSpeech: https://huggingface.co/datasets/ShoukanLabs/AniSpeech
- Japanese Anime Speech: https://huggingface.co/datasets/joujiboi/japanese-anime-speech
- AnimeSpeech tooling: https://github.com/deeplearningcafe/animespeechdataset

### Penelitian Emotion Control
- EmoSphere-TTS: https://arxiv.org/abs/2406.07803
- EmoSphere++: https://arxiv.org/html/2411.02625v1
- IndexTTS 2 paper: https://arxiv.org/pdf/2506.21619
- Cross-Lingual F5-TTS: https://arxiv.org/abs/2509.14579

### Cloud Provider
- ElevenLabs pricing: https://elevenlabs.io/pricing
- OpenAI Realtime: https://openai.com/index/introducing-gpt-realtime/
- Fish Audio: https://fish.audio
- Google Cloud TTS: https://cloud.google.com/text-to-speech

### Pipeline VTuber Reference
- Open-LLM-VTuber: https://github.com/Open-LLM-VTuber/Open-LLM-VTuber
- VoxSherpa TTS discussion: https://github.com/k2-fsa/sherpa-onnx/discussions/3383
- Hololive-Style-Bert-VITS2: https://replicate.com/zsxkib/hololive-style-bert-vits2

### Hukum dan Etika
- Tennessee ELVIS Act overview: https://holonlaw.com/entertainment-law/synthetic-media-voice-cloning-and-the-new-right-of-publicity-risk-map-for-2026/
- Skadden NY court ruling on voice cloning: https://www.skadden.com/insights/publications/2025/07/new-york-court-tackles-the-legality-of-ai-voice-cloning

---

**Catatan akhir**: Dokumen ini disusun berdasarkan sumber publik per 13 Mei 2026. Ekosistem TTS bergerak sangat cepat — kalau menunggu 2-3 bulan, kemungkinan ada model baru yang lebih cocok. Strategi paling aman: **mulai prototype sekarang dengan ElevenLabs**, sambil monitor rilis IndexTTS 3 / Kokoro v2 ID / F5-TTS-v2 yang kemungkinan keluar Q3-Q4 2026.
