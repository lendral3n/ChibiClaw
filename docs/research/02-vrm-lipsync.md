# Deep Research: Lipsync Real-Time untuk VRM Avatar di Android

**Tanggal akses sumber**: 2026-05-13
**Konteks proyek**: VRM Assistant Android (Unity 6.2 UaaL + Kotlin/Compose, floating overlay), sister project ChibiClaw (Piper TTS offline / OpenAI TTS cloud)
**Target hardware**: Snapdragon 8 Elite Gen 5
**Spesifikasi target**: VRM 1.0 expression API (aa/ih/ou/ee/oh + emotion + blink), latency end-to-end <50 ms, VTuber-grade quality

> Catatan reliability: setiap baris "perlu verifikasi" menandakan klaim yang tidak penuh saya konfirmasi dari sumber primer. Tanggal release dan nomor versi diambil per 2026-05-13 — bisa berubah pasca tanggal tersebut.

---

## 1. Landscape Library Lipsync (Unity / Android)

### 1.1 Tabel ringkas perbandingan

| Library | URL | Lisensi | Versi / Terakhir Update | Unity / IL2CPP Android | Pendekatan | Latency real-time | RAM / Footprint | VRM 1.0 native |
|---|---|---|---|---|---|---|---|---|
| **uLipSync** | https://github.com/hecomi/uLipSync | MIT | v3.1.4 (31 Jul 2024) | Ya, pakai Job System + Burst (no native plugin); IL2CPP-safe | MFCC + cosine similarity vs profile fonem A/I/U/E/O/N | ~10–20 ms (frame audio buffer) + smoothing; profile bisa dikalibrasi | Kecil (~beberapa MB asset + Burst-compiled job) | Ya, sample `Samples/04. VRM` pakai `uLipSyncExpressionVRM` (define `USE_VRM10`) |
| **OVRLipSync (Meta)** | https://developers.meta.com/horizon/documentation/unity/audio-ovrlipsync-unity/ | Oculus SDK License (proprietary, free) | Tetap dipelihara untuk Quest; tidak ada commit publik major sejak 2023 (perlu verifikasi) | Plugin Unity tersedia, ada DSP offload Android (untuk perangkat Oculus); kompatibilitas Android non-Oculus terbatas | Neural net Oculus (15 viseme: sil/PP/FF/TH/DD/kk/CH/SS/nn/RR/aa/E/ih/oh/ou) | Real-time, ditujukan VR | Native .so wajib (heavier daripada uLipSync) | Tidak native; perlu adapter — mapping 15 viseme Oculus → 5 viseme VRM (aa/ih/ou/ee/oh) |
| **Rhubarb Lip Sync** | https://github.com/DanielSWolf/rhubarb-lip-sync | MIT | v1.14.0 (3 Apr 2025) | Hanya CLI Windows/macOS/Linux; **tidak ada binary Android** | Offline forced-alignment (PocketSphinx) → Preston Blair viseme (A–H, X) | Tidak realtime (batch processing) | N/A di mobile | Tidak; output JSON harus dipetakan manual ke VRM |
| **Wav2Lip** | https://github.com/Rudrabha/Wav2Lip (mirrors banyak) | Code: research-only / non-commercial untuk weight original; mirror ada lisensi MIT (perlu verifikasi tiap fork) | Original 2020 (paper); fork komunitas masih aktif 2025 | Tidak praktis di Android Unity — model GAN ~440 MB, butuh GPU; ada port ONNX/OpenVINO tapi target desktop/server | Generate piksel mulut 2D, bukan blendshape | Bukan untuk VRM 3D (output video pixel) | Berat (>500 MB RAM bayangan) | Tidak relevan untuk VRM 3D blendshape |
| **SadTalker** | https://github.com/OpenTalker/SadTalker | Apache 2.0 (kode); model weight non-commercial | Stabil sejak CVPR 2023; update sporadis 2025 | Tidak untuk realtime mobile — pipeline diffusion + 3DMM coefficients | Render video 2D end-to-end | Detik per frame di GPU desktop | Multi-GB | Tidak |
| **MuseTalk** | https://github.com/TMElyralab/MuseTalk | MIT | v1.5 (Maret 2025) | Realtime 30 fps+ di NVIDIA Tesla V100; **belum ada port Android Snapdragon NPU publik** | Latent space inpainting (face region 256×256) | ~33 ms/frame di V100 (desktop) | GB-class | Tidak (mengubah video frame, bukan blendshape) |
| **Audio2Face-3D (NVIDIA)** | https://github.com/NVIDIA/Audio2Face-3D & https://github.com/NVIDIA/Audio2Face-3D-SDK | NVIDIA Open Model License + MIT (SDK) | v2.3 regression + v3.0 diffusion (Agustus 2025) | C++/CUDA + TensorRT — **belum ada path Android publik**; output ARKit-compatible blendshape | CNN/GRU + PCA blendshape solver | 413–453 FPS (v2.3 RTX 4090); ARKit 52 blendshape | 0.6–4 GB GPU memory | Bisa, jika ARKit 52 di-remap ke VRM 1.0 expression (aa/ih/ou/ee/oh + emotion) |
| **CRI LipSync (via Live2D Cubism 5)** | https://blog.criware.com/index.php/2023/11/17/cri-lipsync-in-live2d-cubism-5-0/ | Commercial (CRI Middleware) | Standar di Cubism 5.0 (2023+) | iOS + Android native | Signal processing + ML viseme A/I/U/E/O | Realtime sub-frame | Ringan | Tidak (Cubism oriented, Live2D 2D); konsep mapping bisa diadopsi |
| **Live2DFrequencyLipSync** | https://github.com/DenchiSoft/Live2DFrequencyLipSync | MIT (perlu verifikasi) | Snapshot referensi, bukan library aktif | Cross-platform (kode rujukan) | FFT band amplitude → mouth open + smile dim | Sangat ringan (μs–ms) | Trivial | Tidak; tapi pola FFT bisa dipakai |
| **VU-VRM** | https://github.com/Automattic/VU-VRM | Tidak terlihat eksplisit di README (perlu verifikasi) | Aktif 2024–2025 (48 commit, perlu verifikasi tanggal terakhir) | Web (browser source OBS) — **bukan Android native** | VU meter (volume → bukaan mulut) | Realtime browser | Trivial | Ya, untuk VRM browser-based |

### 1.2 Catatan praktis untuk Android Snapdragon 8 Elite Gen 5

- **uLipSync** adalah default pragmatis. MIT, single-package, Burst Job System cocok dengan IL2CPP Android, dan author secara eksplisit menyediakan sample VRM 1.0 (`Samples/04. VRM` dengan komponen `uLipSyncExpressionVRM`). VTube Studio "Advanced Lipsync" sendiri dikonfirmasi *based on uLipSync by hecomi* — track record produksi mobile/desktop sudah ada.
- **OVRLipSync** kualitas viseme lebih tinggi (15 viseme phoneme-aware), tapi distribusi resmi via Oculus / Meta dengan native .so. Lisensi Oculus SDK membatasi distribusi ulang, dan integrasi non-Oculus Android perlu diuji manual. Pertimbangkan hanya jika butuh viseme konsonan presisi.
- **Wav2Lip / SadTalker / MuseTalk / Audio2Face-3D** terlalu berat untuk floating overlay HP. Audio2Face-3D *output blendshape* (relevan) tapi inference path belum dimaterialisasikan untuk Snapdragon NPU per Mei 2026 — Snapdragon 8 Elite Gen 5 Hexagon NPU bisa eksperimentasi ONNX/QNN tapi belum ada referensi publik (perlu verifikasi).
- **Rhubarb** menarik sebagai *server-side preprocessor*: jika kalimat TTS sudah pre-rendered (mis. Piper TTS offline ke buffer wav), bisa offline-align dan stream timeline ke Unity. Tapi target <50 ms streaming tidak tercapai karena PocketSphinx-nya batch.

---

## 2. Pendekatan Teknis Audio-to-Viseme

### 2.1 Empat keluarga pendekatan

| Pendekatan | Mekanisme | Pros (mobile realtime) | Cons | Vendor yang pakai |
|---|---|---|---|---|
| **FFT band / volume meter** | Bandpass + amplitudo → mouth open | Ultra-ringan, latency μs; deterministik | Tidak punya viseme phoneme — semua suara terlihat sama ("ah-ah-ah") | Live2D frequency demo, VU-VRM, VTube Studio "Simple Lipsync" lama |
| **MFCC + similarity ke profile fonem** | 13–40 koef MFCC per frame, cosine vs sampel A/I/U/E/O | Ringan, akurat untuk vowel; bisa dikalibrasi; bekerja di Burst Job | Konsonan (P/B/M, F/V, T/D, S/SH) tidak terbedakan; perlu profile tuning per pembicara | uLipSync, VTube Studio Advanced Lipsync, CRI LipSync (kombinasi signal + ML) |
| **Forced alignment phoneme ASR** | Wav2Vec2 / Vosk / Whisper.cpp menebak phoneme → mapping ke viseme | Akurat termasuk konsonan; bahasa-agnostik dengan Wav2Vec2 phoneme | Model 30–300 MB; latency real-time lebih sulit (window 200–500 ms); buffer ASR jarang <50 ms tanpa quantization agresif | WhisperX (offline), forced-aligner riset; di mobile masih marginal |
| **End-to-end neural audio→blendshape** | CNN/GRU/diffusion dari audio ke vertex/blendshape | Kualitas tertinggi, kualitas VTuber-grade, emotion-aware (Audio2Face-3D, PESTalk) | Belum ada model open Android-ready; latency NPU bergantung quantize INT8 dan port QNN/CoreML | NVIDIA Audio2Face-3D, MuseTalk, SAiD (riset), TalkingMachines |

### 2.2 Apa yang VTuber tools modern pakai

- **VSeeFace** (per manual & rilis): default ke audio-based hybrid lipsync, mengaktifkan blendshape standar A/I/U/E/O di VRM. Sejak v1.13.34 mendukung tambahan SIL/CH/DD/FF/KK/NN/PP/RR/SS/TH bila avatar punya blendshape clip-nya. Algoritma mixing dilakukan internal (mencegah dua viseme penuh bersamaan).
- **VTube Studio** "Advanced Lipsync": eksplisit *based on uLipSync*, output `VoiceA/I/U/E/O` 0–1 + `VoiceSilence`, `VoiceVolume`, `VoiceFrequency`, `VoiceFrequencyPlusMouthSmile`. Cross-platform (desktop & smartphone). Constraint: ParamSilence menahan netral, dan max-one viseme aktif logic.
- **Animaze** (FaceRig successor): viseme blending dari audio + opsional ARKit blendshape capture (kamera).
- **Live2D Cubism 5.0 + CRI LipSync**: combo signal-processing + ML, iOS/Android native, output viseme A/I/U/E/O kemudian motion-sync untuk blending halus.
- **VMagicMirror** (desktop VTube tool open-source): audio-based viseme dari mic, mapping ke VRM expression preset.

**Kesimpulan praktis**: Konsensus VTuber modern = **MFCC-based pada A/I/U/E/O + smoothing + heuristics untuk mute/silence**. Konsonan presisi (FF/PP/SS) opsional via blendshape ekstra atau viseme Oculus. End-to-end neural belum jadi default karena overhead — meski Audio2Face-3D dan PESTalk arahnya ke sana.

### 2.3 Rekomendasi untuk ChibiClaw / VRM Assistant

Pipeline yang saya rekomendasikan, tier:

1. **Tier 1 (MVP, low risk)**: uLipSync MFCC mode pada AudioSource Unity yang play balik PCM dari TTS. Kalibrasi profil pakai sample suara Piper voice yang akan dipakai. Map ke `Vrm10Instance.Runtime.Expression.SetWeight` untuk Aa/Ih/Ou/Ee/Oh.
2. **Tier 2 (kualitas++)**: Jika Piper TTS langsung mengeluarkan **phoneme timeline** (espeak-ng intermediate; Piper internal), bypass deteksi audio: TTS engine sudah tahu fonem yang sedang diucapkan beserta durasinya. Stream `(phoneme, t_start, t_end)` ke Unity dari Kotlin side, mapping ke viseme VRM via tabel statis. Ini akurasi maksimum dengan latency mendekati nol. Issue rhasspy/piper #25 mengkonfirmasi streaming Piper diperbaiki tapi masih tidak trivial (artifacts di sentence boundary).
3. **Tier 3 (eksperimen)**: ONNX Audio2Face-3D regression v2.3 di-quantize INT8 → QNN HTP di Snapdragon 8 Elite Gen 5. Output ARKit 52 → remap ke VRM expression preset. Belum ada referensi publik; perlu prototyping.

---

## 3. VRM 1.0 Spec & Blendshape Mapping

### 3.1 Preset expression (16) di VRM 1.0

Sumber: `vrm-c/vrm-specification`, file `VRMC_vrm-1.0/expressions.md`.

- **Lipsync (5)**: `aa`, `ih`, `ou`, `ee`, `oh`
- **Emotion (5)**: `happy`, `angry`, `sad`, `relaxed`, `surprised`
- **Blink (3)**: `blink`, `blinkLeft`, `blinkRight`
- **Gaze (4)**: `lookUp`, `lookDown`, `lookLeft`, `lookRight`
- **Compat (1)**: `neutral`

Setiap Expression punya:

- `isBinary`: bool — kalau true, weight thresholded di 0.5 (untuk gaya anime keras).
- `morphTargetBinds[]`: list `{node, index, weight}` — multi-mesh blend shape index aggregation.
- `materialColorBinds[]` + `textureTransformBinds[]`: untuk efek seperti blush (color binding) atau geser UV mata.
- `overrideMouth` / `overrideBlink` / `overrideLookAt`: enum `none`/`block`/`blend` untuk suppress kategori lain saat emotion aktif. Contoh: `happy` set `overrideMouth=blend` agar lipsync redam ketika tersenyum lebar.

### 3.2 API UniVRM (v0.131.0, Desember 2025; minimum Unity 2022.3 LTS, IL2CPP supported)

Setter weight per frame:

```csharp
using UniVRM10;
using UnityEngine;

public class VrmLipsyncBridge : MonoBehaviour {
    [SerializeField] Vrm10Instance vrm;
    // ExpressionKey statis untuk preset
    static readonly ExpressionKey AA   = ExpressionKey.CreateFromPreset(ExpressionPreset.aa);
    static readonly ExpressionKey IH   = ExpressionKey.CreateFromPreset(ExpressionPreset.ih);
    static readonly ExpressionKey OU   = ExpressionKey.CreateFromPreset(ExpressionPreset.ou);
    static readonly ExpressionKey EE   = ExpressionKey.CreateFromPreset(ExpressionPreset.ee);
    static readonly ExpressionKey OH   = ExpressionKey.CreateFromPreset(ExpressionPreset.oh);
    static readonly ExpressionKey HAPPY = ExpressionKey.CreateFromPreset(ExpressionPreset.happy);
    static readonly ExpressionKey BLINK = ExpressionKey.CreateFromPreset(ExpressionPreset.blink);

    public void SetViseme(float aa, float ih, float ou, float ee, float oh) {
        var rt = vrm.Runtime.Expression;
        rt.SetWeight(AA, aa);
        rt.SetWeight(IH, ih);
        rt.SetWeight(OU, ou);
        rt.SetWeight(EE, ee);
        rt.SetWeight(OH, oh);
    }
}
```

Snippet ini selaras dengan implementasi `uLipSyncExpressionVRM.OnApplyBlendShapes()` di sample uLipSync (`Samples/04. VRM/Runtime/`), yang juga melakukan pengurutan clip list dan multiply dengan `volume` untuk mute saat hening.

### 3.3 Mapping 15-viseme Oculus → 5-viseme VRM

Bila pakai OVRLipSync, perlu lookup table. Aturan praktis (heuristik komunitas Ready Player Me — perlu verifikasi numerik):

| Oculus viseme | VRM mapping dominan |
|---|---|
| sil | (semua 0 + neutral) |
| PP, FF | aa kecil + redam dengan mouth-close |
| TH, DD, nn, SS, CH | ih (lebar dikit) |
| kk, RR | aa medium |
| aa, E | aa, ee |
| ih | ih |
| oh | oh |
| ou | ou |

Kalau avatar punya extended blendshape (SIL/CH/DD/FF/KK/NN/PP/RR/SS/TH ala VSeeFace v1.13.34+), mapping bisa 1:1 untuk konsonan. VRM 1.0 spec memperbolehkan **custom expression** di luar preset, jadi viseme tambahan boleh.

### 3.4 Kompatibilitas VRM 0.x legacy

- VRM 0.x pakai `VRMBlendShapeProxy` (komponen) dengan `BlendShapeKey` enum.
- UniVRM punya migrasi otomatis VRM 0.x → 1.0 saat import.
- uLipSync menyediakan **dua komponen terpisah**: `uLipSyncBlendShapeVRM` (0.x) dan `uLipSyncExpressionVRM` (1.0). Scripting define `USE_VRM0X` vs `USE_VRM10` mengaktifkan masing-masing.
- Untuk avatar Jepang dari VRoid Studio yang viseme blendshape-nya berlabel あ/い/う/え/お, gunakan tool `ImLevath/vrm-viseme-tools` untuk auto-generate clip A/I/U/E/O. MIT, Unity 2019.4+, butuh UniVRM.

---

## 4. Emotion-Aware Lipsync + Facial Expression

### 4.1 Sumber emosi: tiga channel

1. **Dari text TTS (NLU)**: parser kalimat → sentiment / emotion tag sebelum TTS render. Untuk ChibiClaw bisa di Gemma 4 (router) atau heuristic lexicon — paling cepat sebagai metadata yang disisipkan ke `TTSRequest`.
2. **Dari audio acoustic features**: model Speech Emotion Recognition (SER) menghitung valence/arousal/dominance. Sumber paling matang:
   - **audeering/wav2vec2-large-robust-12-ft-emotion-msp-dim** (CC BY-NC-SA 4.0, ONNX export available). Model 3 dimensi 0–1.
   - **Wav2Small** (audEERING, 2024): distilled 72K params, 120 KB quantized ONNX — *ditujukan untuk hardware low-resource termasuk mobile*. Cocok di Snapdragon NPU. Lisensi sama (perlu verifikasi commercial use).
   - MobileNetV4/V3 distilled untuk SER (audeering open source).
3. **Dari LLM agent state**: di ChibiClaw kalau Gemma 4 sudah punya "mood" persona, langsung set expression preset.

### 4.2 Mapping emosi → VRM 1.0 expression

Pendekatan Russell circumplex (valence × arousal) → dispatch:

| Quadrant | Valence | Arousal | VRM preset |
|---|---|---|---|
| High V, High A | > 0.6 | > 0.5 | `happy` (full smile + raised cheeks + eye narrow) |
| High V, Low A | > 0.6 | < 0.5 | `relaxed` (soft smile) |
| Low V, High A | < 0.4 | > 0.5 | `angry` (brow down + jaw tense) |
| Low V, Low A | < 0.4 | < 0.5 | `sad` (brow inner up + lid heavy) |
| Sudden ΔA | — | spike | `surprised` (mata lebar + alis naik + mulut O kecil) |
| Mid | mid | mid | `neutral` (atau jangan override) |

Override semantics:

- Saat `happy` aktif penuh (weight 1.0), set `overrideMouth = blend` di JSON VRM agar lipsync viseme tetap berjalan tapi dengan amplitudo dikalikan (1 − happy.weight × k). Atau cukup biarkan lipsync menjalankan aa/ih/etc dan `happy` menambah smile + cheek raise sebagai *additive* via material color binds untuk blush.
- `surprised` biasanya `overrideMouth = block` untuk efek "o!" yang stabil.
- `blink` di-drive procedural (random 3–6 detik interval, durasi 100–150 ms) dan emotion `surprised` set `overrideBlink = block`.

### 4.3 Repositori demonstrasi realtime

Sayangnya, **belum ada single open-source repo yang demonstrate VRM 1.0 + lipsync + emotion-aware audio-driven di mobile end-to-end** per pencarian saya tanggal 2026-05-13 (perlu verifikasi terus). Pilihan terdekat:

- **uLipSync** + **emilianavt/VSeeFaceManual** (desktop, dokumentasi mapping viseme + emotion fallback).
- **VMagicMirror** (open-source desktop): contoh mapping audio → emotion + lipsync, lihat changelog untuk fitur "Word to Motion" yang mendispatch expression dari kata kunci.
- **NVIDIA/Audio2Face-3D-SDK**: menggabungkan Audio2Emotion + Audio2Face — desktop-only sekarang, tapi referensi arsitektural.
- **VU-VRM** (Automattic): contoh minimal VRM browser-based, bukan emotion-aware.

Practical recommendation: bangun layer integrasi sendiri di Unity, dengan `EmotionStateController` MonoBehaviour yang menerima sinyal dari Kotlin (LLM mood) + Wav2Small inference (audio mood) → blend weight ke VRM expression preset. Smoothing dengan exponential moving average (alpha 0.15–0.25) untuk transisi tidak jerky.

---

## 5. Mobile-Specific Concern

### 5.1 Latency budget Android Unity IL2CPP

Komponen latency (estimasi konservatif):

| Stage | Tipikal | Catatan |
|---|---|---|
| AudioTrack write → playback (Java side) | ~10–20 ms minimum di low-latency mode (Oboe/AAudio) | Unity default audio system FMOD lebih tinggi, sering 100–200 ms; native plugin (5argon Native Audio) menurunkan ke 20–60 ms |
| Unity DSP buffer (`AudioSettings.dspTime`) | DSP buffer size "Best latency" ~256 sample @ 48 kHz = 5.3 ms; "Best performance" 1024 = 21 ms | Set di Project Settings > Audio |
| `OnAudioFilterRead` callback period | ~20 ms tipikal (Unity docs); dipanggil di audio thread (bukan main) | Tidak boleh panggil Unity API non-thread-safe |
| MFCC extraction (Burst Job) | <1 ms per frame di Snapdragon 8 Elite Gen 5 | uLipSync di-Burst, sangat cepat |
| Cosine similarity + smoothing | <0.5 ms | Negligible |
| `SetWeight` ke VRM Expression + frame render | Sub-frame (16.6 ms @ 60 fps) | Apply blendshape pada `LateUpdate`; UniVRM update di sini |
| Visual update di overlay window | Bersamaan dengan render Unity → SurfaceView overlay Android | UaaL render ke `SurfaceView` ditampilkan via WindowManager `TYPE_APPLICATION_OVERLAY` |

**Total realistic end-to-end** untuk audio yang dimainkan via AudioSource Unity (uLipSync membaca buffer di `OnAudioFilterRead` yang sama): **~25–45 ms** dengan DSP buffer "Best latency". Memenuhi target <50 ms.

**Pitfall**: kalau audio diputar **di sisi Kotlin via `AudioTrack`** (mis. Piper TTS di-stream raw dari layer Android tanpa lewat Unity AudioSource), maka uLipSync tidak punya akses langsung ke buffer. Dua solusi:

### 5.2 Streaming PCM Kotlin → Unity

**Opsi A (recommended): Pump audio ke Unity AudioSource**

- TTS engine (Piper) di Kotlin menghasilkan PCM 16-bit mono 22050 Hz.
- Konversi ke `float[]` Unity-friendly via JNI: copy ke shared `ByteBuffer` (allocated direct, off-heap).
- Di Unity, gunakan `AudioClip.Create(name, length, channels, frequency, true, OnPCMRead)` dengan pcm-read callback yang membaca dari ring buffer. AudioSource memutar AudioClip ini.
- uLipSync menempel ke AudioSource — `OnAudioFilterRead` melihat samples yang sama dengan yang diputar. Lipsync **otomatis sync** karena clock yang sama.

**Opsi B: Pump phoneme timeline**

- TTS engine output `[(phoneme, t_offset_ms, duration_ms), ...]` plus PCM.
- PCM diputar via Android AudioTrack low-latency, Unity hanya menerima timeline events via JNI call ke C# method (atau MessageBus).
- Unity tidak butuh decode audio — langsung schedule viseme di main loop sesuai timeline + waktu audio yang diputar Android (perlu sync clock via `System.nanoTime()` shared).
- Lebih akurat tapi butuh TTS engine yang expose phoneme alignment.

**Opsi C: Hybrid**

- Audio decode dilakukan di Kotlin (low-latency AudioTrack), tapi salinan kedua dikirim ke Unity untuk uLipSync.
- Risiko: double audio playback jika Unity AudioSource volume tidak nol. Set `AudioSource.volume = 0` agar Unity hanya analisis tanpa output.

**Opsi A paling sederhana** dan diuji komunitas (lihat issue uLipSync soal `OnPCMRead`). Disarankan untuk MVP.

### 5.3 IPC Sync clock Kotlin ↔ Unity

- Gunakan `System.nanoTime()` di Kotlin sebagai master clock.
- Saat AudioTrack.write() return, catat timestamp.
- Di Unity, periodic call `UnityPlayer.UnitySendMessage("LipsyncBridge", "SetAudioClock", currentNanos.toString())`.
- Unity simpan offset = `AudioSettings.dspTime` (double, detik) vs Kotlin nanos. Apply ke timeline phoneme.

### 5.4 Memory & power di overlay floating

- VRM model + Unity scene runtime: 80–200 MB RAM tipikal (perlu verifikasi per model).
- uLipSync Burst Job: footprint <10 MB.
- Audio2Face-3D di NPU (jika dipakai): tambahan ~50–200 MB (model + workspace, perlu verifikasi).
- Overlay floating + render terus-menerus ~30 fps: drain baterai signifikan di Snapdragon 8 Elite Gen 5 (perlu profiling). Disarankan throttle ke 24 fps idle, 30 fps saat bicara, dan pause render saat avatar di-minimize.

---

## 6. Tutorial / Reference Project End-to-End

Per pencarian 2026-05-13, *tidak ada single repo* yang menggabungkan VRM 1.0 + lipsync audio + voice + Android floating overlay. Berikut yang terdekat:

| Repo | URL | Yang ada | Yang kurang |
|---|---|---|---|
| **hecomi/uLipSync — Samples/04. VRM** | https://github.com/hecomi/uLipSync/blob/main/Assets/uLipSync/Samples/04.%20VRM/Runtime/uLipSyncExpressionVRM.cs | Lipsync + UniVRM 1.0 di Unity, runtime VRM load (`Vrm10.LoadPathAsync`), mic input | Bukan Android-specific; tidak ada overlay |
| **ReForge-Mode/Live_Lipsync_Examples** | https://github.com/ReForge-Mode/Live_Lipsync_Examples | Tutorial 3D + URP, UniVRM + uLipSync siap pakai | Desktop |
| **AISebas runtime gist** | https://gist.github.com/AISebas/635006819c8ae628ae9448d90f93ecab | Inisialisasi uLipSync untuk VRM10 yang di-load runtime | Snippet, bukan project lengkap |
| **Unity-Technologies/uaal-example** | https://github.com/Unity-Technologies/uaal-example | UaaL Android sample (MainUnityActivity, AndroidLibrary) | Tidak ada VRM/lipsync |
| **paulirwin/apps_vrm_android** | https://github.com/paulirwin/apps_vrm_android | VRM viewer Android (perlu verifikasi current state) | Tidak lipsync |
| **VMagicMirror (Malay Baku)** | https://github.com/malaybaku/VMagicMirror | Desktop reference penuh VRM + audio + expression word-to-motion | Desktop Windows |
| **Automattic/VU-VRM** | https://github.com/Automattic/VU-VRM | VRM web minimal | Web, bukan Android Unity |

**Rekomendasi setup end-to-end** untuk VRM Assistant Android Anda:

1. **Base**: clone `uaal-example` untuk struktur Android host + Unity library.
2. **Tambah UniVRM v0.131.0** ke Unity project (UPM via git URL `https://github.com/vrm-c/UniVRM.git?path=/Assets/VRM10`).
3. **Tambah uLipSync v3.1.4** via .unitypackage release; di Player Settings, tambahkan `USE_VRM10` ke Scripting Define Symbols.
4. **Import Sample 04. VRM** dari uLipSync Package Manager.
5. **Bridge audio**: TTS engine (Piper Kotlin / OpenAI cloud) → pump PCM ke Unity via JNI ke `AudioClip` callback (Opsi A di §5.2).
6. **Overlay**: WindowManager `TYPE_APPLICATION_OVERLAY` + `SYSTEM_ALERT_WINDOW` permission + `UnityPlayer` di custom `Activity` yang attach ke window.
7. **Emotion layer**: tambahkan Wav2Small ONNX runtime (`onnxruntime-android`) di Kotlin, pump valence/arousal/dominance ke Unity via UnitySendMessage, mapping ke `EmotionStateController`.
8. **Blink procedural**: Coroutine sederhana di Unity, random interval 3–6 s.

---

## 7. Decision Matrix Singkat

**Skenario ChibiClaw / VRM Assistant Anda → pilih uLipSync MFCC + UniVRM 1.0**:

- Lisensi MIT, tidak ada vendor lock.
- Burst + Job System aman untuk IL2CPP Android Snapdragon ARM64.
- Sample VRM 1.0 sudah disediakan author.
- Track record VTube Studio (Advanced Lipsync) memvalidasi production-readiness.
- Latency MFCC + smoothing well under 50 ms budget bila pipa audio dipasang ke AudioSource Unity.

Reserve untuk fase 2:

- Tambah viseme konsonan (FF/PP/SS) jika model VRM punya blendshape clip-nya, atau eksperimen integrasi OVRLipSync.
- Tambah Wav2Small SER ONNX untuk emotion-aware expression layer.

Reserve untuk fase 3 (R&D):

- Coba Audio2Face-3D regression ONNX di QNN HTP Snapdragon untuk full-face VTuber quality. Per Mei 2026 belum ada referensi publik — Anda akan jadi pionir kalau berhasil port-nya.

---

## 8. Sumber & Referensi

Diakses 2026-05-13. Tanggal/versi yang tercatat di body dokumen, di sini hanya URL utama.

- uLipSync repository: https://github.com/hecomi/uLipSync
- uLipSync releases: https://github.com/hecomi/uLipSync/releases
- uLipSync VRM sample: https://github.com/hecomi/uLipSync/blob/main/Assets/uLipSync/Samples/04.%20VRM/Runtime/uLipSyncExpressionVRM.cs
- Live Lipsync Examples (ReForge-Mode): https://github.com/ReForge-Mode/Live_Lipsync_Examples
- UniVRM repository: https://github.com/vrm-c/UniVRM
- VRM 1.0 expression spec: https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_vrm-1.0/expressions.md
- vrm.dev UniVRM Expression tutorial: https://vrm.dev/en/univrm1/vrm1_tutorial/expression/
- ImLevath/vrm-viseme-tools: https://github.com/ImLevath/vrm-viseme-tools
- Oculus Lipsync (Meta) Unity docs: https://developers.meta.com/horizon/documentation/unity/audio-ovrlipsync-unity/
- Oculus Lipsync viseme reference (15 viseme): https://developers.meta.com/horizon/documentation/unity/audio-ovrlipsync-viseme-reference/
- Ready Player Me OVR LipSync notes: https://docs.readyplayer.me/ready-player-me/api-reference/avatars/morph-targets/oculus-ovr-libsync
- Rhubarb Lip Sync: https://github.com/DanielSWolf/rhubarb-lip-sync
- Wav2Lip realtime fork: https://github.com/devkrish23/realtimeWav2lip
- SadTalker: https://github.com/OpenTalker/SadTalker
- MuseTalk: https://github.com/TMElyralab/MuseTalk
- MuseTalk paper: https://arxiv.org/html/2410.10122v1
- NVIDIA Audio2Face-3D: https://github.com/NVIDIA/Audio2Face-3D
- NVIDIA Audio2Face-3D SDK: https://github.com/NVIDIA/Audio2Face-3D-SDK
- Audio2Face-3D paper: https://arxiv.org/html/2508.16401v1
- Audio Driven Real-Time Facial Animation for Social Telepresence (SIGGRAPH Asia 2025): https://arxiv.org/abs/2510.01176
- PESTalk: https://arxiv.org/pdf/2512.05121
- SAiD diffusion blendshape: https://arxiv.org/abs/2401.08655
- VTube Studio Lipsync wiki: https://github.com/DenchiSoft/VTubeStudio/wiki/Lipsync
- VSeeFace manual repo: https://github.com/emilianavt/VSeeFaceManual
- Live2D Cubism Lipsync SDK manual: https://docs.live2d.com/en/cubism-sdk-manual/lipsync/
- CRI LipSync Live2D 5.0 announce: https://blog.criware.com/index.php/2023/11/17/cri-lipsync-in-live2d-cubism-5-0/
- Live2DFrequencyLipSync: https://github.com/DenchiSoft/Live2DFrequencyLipSync
- VMagicMirror docs: https://malaybaku.github.io/VMagicMirror/en/
- VU-VRM: https://github.com/Automattic/VU-VRM
- Piper TTS: https://github.com/rhasspy/piper
- Piper streaming issue #25: https://github.com/rhasspy/piper/issues/25
- audeering Wav2Vec2 dimensional emotion (ONNX): https://huggingface.co/audeering/wav2vec2-large-robust-12-ft-emotion-msp-dim
- audeering Wav2Small paper: https://arxiv.org/html/2408.13920v4
- audeering open source models: https://www.audeering.com/research/open-source/
- Forced Alignment with Wav2Vec2 (TorchAudio): https://docs.pytorch.org/audio/stable/tutorials/forced_alignment_tutorial.html
- WhisperX: https://github.com/m-bain/whisperX
- Unity OnAudioFilterRead docs: https://docs.unity3d.com/ScriptReference/MonoBehaviour.OnAudioFilterRead.html
- Android Oboe low-latency: https://developer.android.com/games/sdk/oboe/low-latency-audio
- 5argon Android Native Audio primer: https://gametorrahod.com/android-native-audio-primer-for-unity-developers/
- Unity UaaL example: https://github.com/Unity-Technologies/uaal-example
- Unity UaaL Android docs: https://docs.unity3d.com/Manual/UnityasaLibrary-Android.html

---

**Catatan tindak lanjut**: sebelum kunci library, lakukan smoke test (1 hari):

1. Empty Unity 6.2 project + UniVRM 0.131 + uLipSync 3.1.4 + sample VRM 1.0 model.
2. Build IL2CPP ARM64 Android, deploy ke device Snapdragon target.
3. Profil: latency dari mic input ke blendshape, FPS overlay, memory delta. Jika <50 ms dan stabil, lock tier 1; jika tidak, debug DSP buffer dan native audio plugin.
