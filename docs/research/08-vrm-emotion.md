# 08 - VRM Avatar Emotion + Facial Expression Real-Time System

**Konteks:** VRM Assistant Android - floating VRM avatar overlay di atas layar HP. Sudah ada research lipsync (uLipSync, doc 02). Doc ini meng-extend ke emotion + full facial expression supaya avatar terasa hidup seperti VTuber manusia, bukan robot statis.

**Tanggal akses sumber:** 2026-05-13
**Target platform:** Android (Xiaomi 17 Pro Max - Snapdragon 8 Elite Gen 5), Unity 6.2 UaaL (Unity-as-a-Library) + Kotlin/Compose host.
**Avatar runtime:** UniVRM 0.131.0 (VRM 1.0 + VRM 0.x backward compat).

---

## 1. VRM 1.0 Expression Preset - Spesifikasi Lengkap

VRM 1.0 (ekstensi glTF `VRMC_vrm-1.0`) mendefinisikan **18 preset expression** standar yang dijamin tersedia di semua model VRM 1.0 yang valid. Spesifikasi ini diaudit dari `vrm-c/vrm-specification` master branch (akses 2026-05-13).

### 1.1 Daftar Preset Expression

| Kategori | Preset Name (camelCase) | Jumlah |
|---|---|---|
| **Lip Sync / Viseme** | `aa`, `ih`, `ou`, `ee`, `oh` | 5 |
| **Emotion** | `happy`, `angry`, `sad`, `relaxed`, `surprised` | 5 |
| **Blink** | `blink`, `blinkLeft`, `blinkRight` | 3 |
| **Gaze (LookAt blendshape)** | `lookUp`, `lookDown`, `lookLeft`, `lookRight` | 4 |
| **Legacy/Default** | `neutral` | 1 |

Total: **18 preset**. Catatan: prompt user menyebut "16 preset" - spesifikasi resmi adalah 18 (lookAt 4 arah + neutral + blink 3 + emotion 5 + viseme 5).

### 1.2 Custom Expression

Custom expression disimpan di JSON path `expressions.custom` (bukan `expressions.preset`). Nama custom **tidak boleh bentrok** dengan nama preset. Tidak ada field literal `isCustom` di JSON - klasifikasi ditentukan oleh **lokasi** (preset object vs custom object). Custom expression umum dipakai untuk: cheek blush, eyebrow raise, tongue-out, blendshape tubuh (chest puff), expression khas karakter.

### 1.3 Override Semantics

Tiap expression dapat declare 3 properti override (default: `none`):

- **`overrideBlink`** - control prosedural blink
- **`overrideLookAt`** - control prosedural gaze
- **`overrideMouth`** - control prosedural lipsync (viseme)

Nilai yang diperbolehkan: **`none`**, **`block`** (set target ke 0), **`blend`** (atenuasi linear).

**Formula override (mode blend):**
```
factor = 1.0 - saturate( sum of weights from overriding expressions )
finalProceduralWeight = originalProceduralWeight * factor
```

Override hanya aktif ketika weight expression > 0. Contoh: jika `happy.overrideMouth = blend` dan `happy.weight = 0.7`, maka semua viseme (aa/ih/ou/ee/oh) di-attenuasi: `viseme_weight * (1.0 - 0.7) = viseme_weight * 0.3`.

### 1.4 isBinary Flag

Field `isBinary: true` membuat weight di-quantize: nilai >0.5 menjadi 1.0, lainnya 0.0. Implikasi: kalau preset `blink` di-mark `isBinary` lalu di-override oleh `happy` (mode `block` atau `blend`), maka blink **completely suppressed** jika `happy.weight > 0`. Ini penting untuk gaya anime stilis (mata terbuka penuh saat tersenyum).

### 1.5 Strategi Koeksistensi Lipsync + Emotion + Blink + Gaze

Untuk VRM Assistant, kombinasi yang harus jalan bersamaan tanpa konflik:

| Source | Target Preset | Mode |
|---|---|---|
| uLipSync output | `aa/ih/ou/ee/oh` | weight 0-1 per frame |
| Emotion engine | `happy/sad/angry/relaxed/surprised` | fade in/out |
| Idle blink scheduler | `blink` | pulse pattern |
| LookAt system | `lookUp/Down/Left/Right` ATAU bone | continuous |

**Rekomendasi konfigurasi expression file (VRoid Studio export):**
- `happy.overrideBlink = blend`, `overrideMouth = none` (boleh tersenyum sambil bicara)
- `sad.overrideBlink = none`, `overrideLookAt = blend` (mata melihat ke bawah saat sedih)
- `surprised.overrideBlink = block` (mata melotot, blink di-suppress)
- `angry.overrideMouth = none`, `overrideLookAt = block` (tatapan fix ke target)
- Viseme (aa/ih/ou/ee/oh): keep all overrides = `none`

---

## 2. UniVRM 0.131.0 - Runtime Expression API

UniVRM 0.131.0 (release Q1 2026, support Unity 2022.3 LTS+) memindahkan `Assets/UniGLTF`, `VRM`, `VRM10` ke folder `Packages/`. Dependency `com.vrmc.vrmshaders` sudah di-merge dan tidak perlu di manifest.json lagi.

### 2.1 Set Weight - Pola Standar

```csharp
using UniVRM10;
using UniGLTF.Extensions.VRMC_vrm;

// Reference to instance
Vrm10Instance vrm10 = GetComponent<Vrm10Instance>();

// Set preset expression (happy)
var happyKey = ExpressionKey.CreateFromPreset(ExpressionPreset.Happy);
vrm10.Runtime.Expression.SetWeight(happyKey, 1.0f);

// Set custom expression (by name string)
var customKey = ExpressionKey.CreateCustom("smirk");
vrm10.Runtime.Expression.SetWeight(customKey, 0.5f);

// Viseme
var aaKey = ExpressionKey.CreateFromPreset(ExpressionPreset.Aa);
vrm10.Runtime.Expression.SetWeight(aaKey, visemeStrength);
```

`ExpressionPreset` enum values: `Happy, Angry, Sad, Relaxed, Surprised, Aa, Ih, Ou, Ee, Oh, Blink, BlinkLeft, BlinkRight, LookUp, LookDown, LookLeft, LookRight, Neutral, Custom`.

### 2.2 Timing - LateUpdate vs Update

`Vrm10Runtime` internal flow:
1. `Update()` - user code apply weight via `SetWeight()`
2. `LateUpdate()` UniVRM internal - jalankan LookAt -> Expression apply (dengan override) -> SpringBone

**Aturan emas:** user code panggil `SetWeight()` di **`Update()`** atau dari **callback async** (TTS chunk, emotion event). UniVRM akan auto-flush ke skinned mesh blendshape di `LateUpdate()`. Hindari `SetWeight()` di `LateUpdate()` post-UniVRM - akan tertinggal 1 frame.

Untuk uLipSync (yang fire callback dari Burst Job di main thread), pola di sample `uLipSyncExpressionVRM.cs` adalah:
- `OnApplyBlendShapes()` callback dipanggil setelah analisa MFCC selesai
- Iterasi phoneme detected -> hitung `weight = bs.weight * bs.maxWeight * volume`
- Panggil `vrm10.Runtime.Expression.SetWeight(key, weight)` langsung
- UniVRM apply di `LateUpdate` frame yang sama

### 2.3 BlendShapeProxy Legacy (VRM 0.x) Compatibility

UniVRM 0.131.0 masih support VRM 0.x via `VRMBlendShapeProxy.ImmediatelySetValue(BlendShapeKey, weight)` atau `proxy.ImmediatelySetValue("happy", 1.0f)` (by string). Recommendation: paksa VRM 1.0 untuk semua karakter baru di VRM Assistant; jika user import legacy 0.x, deteksi dengan `vrm10Instance == null` -> fallback ke proxy lama.

### 2.4 Default Validator

`DefaultExpressionValidator` (file `Assets/VRM10/Runtime/Components/Expression/DefaultExpressionValidator.cs`) menjalankan: clamp `[0, 1]`, apply binary quantize, lalu eksekusi override formula sebelum hand-off ke skinned mesh updater. Tidak perlu user code clamp manual.

---

## 3. Emotion Detection: Audio + Text Pipeline

### 3.1 Text-Based (Primary - LLM Output)

**Strategi MVP:** karena response LLM (Gemma 4 / Claude) sudah kita generate text, gunakan **text-based emotion classifier** sebagai primary signal. Tidak perlu audio inference yang berat.

**Model rekomendasi: `SamLowe/roberta-base-go_emotions-onnx`** (Hugging Face).
- Dataset: GoEmotions (Google) - 58k Reddit comments, 28 emotion labels (admiration, amusement, anger, annoyance, approval, caring, confusion, curiosity, desire, disappointment, disapproval, disgust, embarrassment, excitement, fear, gratitude, grief, joy, love, nervousness, optimism, pride, realization, relief, remorse, sadness, surprise, neutral).
- Macro-F1 0.46 (paper original) → 0.78 (modernbert variant 2025).
- ONNX INT8 quantized: **125 MB** (75% lebih kecil dari FP32 ~500 MB), ~2x lebih cepat inference per batch=1 di CPU 8-core (~5x vs Transformers FP32).
- Multi-label: satu kalimat bisa trigger >1 emosi (joy + admiration).

**Alternatif lebih ringan untuk mobile:**
- `cirimus/modernbert-base-go-emotions` - ModernBERT variant, macro-F1 0.78 (full 28-label).
- Distil-BERT GoEmotions (200 MB FP32, ~50 MB INT8).
- **DistilBERT-SST2 binary sentiment** untuk valence-only signal (60 MB INT8).

**ONNX Runtime di Unity Android:**
- Package `Microsoft.ML.OnnxRuntime` via UPM atau native .aar.
- Akselerator: **NNAPI** (Android 8.1+), **XNNPACK** CPU SIMD fallback, **QNN** untuk Snapdragon (Hexagon NPU).
- Latency target: <50 ms per inference di Snapdragon 8 Elite Gen 5 (input length ~50 tokens).
- Reference integration: Koki Ibukuro "ONNX Runtime on Unity" (medium.com/@asus4).

### 3.2 Audio-Based (Secondary - Future Phase)

Optional layer kalau ingin emotion dari **prosodi TTS output** (intonasi, kecepatan, energi) - tidak dari content text saja.

| Model | Size | A/D/V Output | License | Notes |
|---|---|---|---|---|
| **Wav2Small (audEERING)** | 72 K param, **120 KB ONNX quantized** | Arousal, Dominance, Valence (continuous) | Apache-2 | Distilled dari Wav2Vec2 SotA teacher. Ideal mobile. arXiv:2408.13920 |
| **SpeechBrain emotion-recognition-wav2vec2-IEMOCAP** | ~315 MB | 4 class (neutral/happy/angry/sad), F1 78.7% | Apache-2 | Tidak ada ONNX official. PyTorch saja. |
| **openSMILE GeMAPS** | C++ lib ~2 MB | 62 acoustic features (LLD) | research license | Feature extractor saja, butuh classifier downstream. Support Android/iOS native. |
| **Hume AI Speech Prosody** | Cloud API | 48 emotional dimensions | Commercial (sunset 2026-06-14!) | EVI 3 ultra-low latency <300 ms tapi cloud-only - **TIDAK COCOK** untuk offline VRM Assistant. |

**Rekomendasi audio:** **Wav2Small** untuk fase 2. 120 KB ONNX, output langsung VAD continuous yang map ke Russell circumplex (section 4). Hume di-skip karena sunset June 2026 + butuh cloud.

### 3.3 Multimodal Fusion

Kalau audio + text dipakai bersamaan, weighted fusion:
```
valence_final = 0.6 * valence_text + 0.4 * valence_audio
arousal_final = 0.3 * arousal_text + 0.7 * arousal_audio  // prosodi lebih informatif
```
Text dominan untuk valence (content semantik), audio dominan untuk arousal (energi, kecepatan bicara). Rasio empiris dari EmoFace paper (Liu et al. 2024, arXiv:2407.12501) dan EmoTalk (ICCV 2023).

### 3.4 Real-Time Budget

Target end-to-end (text generation -> emotion detected -> expression apply -> audio play): **<100 ms** dari onset text chunk LLM streaming.
- LLM token streaming: tidak block (sudah async).
- Text emotion (ONNX INT8 NNAPI): ~30-50 ms.
- Mapping VAD -> expression weight: <1 ms (lookup table).
- `SetWeight()` + skinned mesh flush: 1 frame = 16.67 ms @ 60 fps.

Total worst case: ~70 ms - **achievable**.

---

## 4. Russell Circumplex / Dimensional Emotion

### 4.1 Model VAD

Russell (1980) "A Circumplex Model of Affect" mendefinisikan emosi sebagai titik di ruang 2D:
- **Valence** (x-axis): unpleasant ↔ pleasant (-1.0 .. +1.0)
- **Arousal** (y-axis): low energy ↔ high energy (-1.0 .. +1.0)

Plus dimensi ketiga **Dominance** (PAD model Mehrabian, 1996): submissive ↔ dominant. Wav2Small dan SpeechBrain output A/D/V langsung.

### 4.2 Mapping VAD → VRM Preset Weight

Lookup table empiris (diaudit terhadap MorphCast Emotion AI labelling dan Mapping Discrete Emotions paper MDPI 10/23/2950):

| Emotion (Russell) | Valence | Arousal | VRM preset weight |
|---|---|---|---|
| Happy / Joy | +0.7 .. +1.0 | +0.4 .. +0.8 | `happy = 0.7..1.0`, `relaxed = 0.2` |
| Excited | +0.5 .. +0.8 | +0.7 .. +1.0 | `happy = 0.6`, `surprised = 0.4` |
| Surprised | +0.0 .. +0.3 | +0.8 .. +1.0 | `surprised = 0.8..1.0` |
| Angry | -0.7 .. -0.4 | +0.6 .. +1.0 | `angry = 0.7..1.0` |
| Sad | -0.8 .. -0.4 | -0.6 .. -0.2 | `sad = 0.6..1.0` |
| Bored | -0.3 .. +0.0 | -0.8 .. -0.5 | `relaxed = 0.3`, `sad = 0.2` |
| Calm / Relaxed | +0.3 .. +0.6 | -0.6 .. -0.2 | `relaxed = 0.7..1.0` |
| Neutral | -0.1 .. +0.1 | -0.1 .. +0.1 | semua weight = 0 |

**Implementasi (pseudo C#):**
```csharp
struct VAD { public float v, a, d; }

public void ApplyEmotion(VAD vad)
{
    // Reset all emotion weights
    var presets = new[] { ExpressionPreset.Happy, ExpressionPreset.Sad,
                          ExpressionPreset.Angry, ExpressionPreset.Relaxed,
                          ExpressionPreset.Surprised };
    foreach (var p in presets) targetWeights[p] = 0f;

    // Joy quadrant: high V + high A
    if (vad.v > 0.4f && vad.a > 0.3f)
        targetWeights[ExpressionPreset.Happy] = Mathf.Clamp01(vad.v + vad.a * 0.5f);

    // Sad quadrant: low V + low A
    if (vad.v < -0.3f && vad.a < -0.1f)
        targetWeights[ExpressionPreset.Sad] = Mathf.Clamp01(-vad.v - vad.a * 0.5f);

    // Anger quadrant: low V + high A
    if (vad.v < -0.3f && vad.a > 0.4f)
        targetWeights[ExpressionPreset.Angry] = Mathf.Clamp01(-vad.v + vad.a * 0.5f);

    // Surprise spike: arousal high regardless of valence
    if (vad.a > 0.7f && Mathf.Abs(vad.v) < 0.3f)
        targetWeights[ExpressionPreset.Surprised] = vad.a;

    // Relaxed: positive V + low A
    if (vad.v > 0.2f && vad.a < -0.2f)
        targetWeights[ExpressionPreset.Relaxed] = Mathf.Clamp01(vad.v - vad.a * 0.3f);
}
```

### 4.3 Smooth Transition

Emotion **tidak boleh snap** ke target weight - butuh fade. Pola standar:
- **Fade in:** 250-400 ms (cepat tapi terlihat natural)
- **Hold:** 2-4 detik sesuai durasi audio TTS chunk
- **Fade out:** 400-700 ms (lebih lambat, biar emosi "lingering")

Easing: **EaseOutQuad** untuk fade in (snap awal lalu melambat), **EaseInOutSine** untuk fade out (alami).

**Implementasi DOTween:**
```csharp
DOTween.To(
    () => currentWeight,
    x => { currentWeight = x; vrm10.Runtime.Expression.SetWeight(key, x); },
    targetWeight,
    0.3f  // 300 ms
).SetEase(Ease.OutQuad);
```

### 4.4 Decay Rate

Setelah TTS chunk selesai dan tidak ada emotion input baru selama 3-5 detik, decay ke neutral:
```csharp
// Per frame in Update
foreach (var key in activeEmotionKeys)
{
    var w = vrm10.Runtime.Expression.GetWeight(key);
    if (w > 0.001f && timeSinceLastEmotionEvent > 3.0f)
    {
        w = Mathf.MoveTowards(w, 0f, decayRate * Time.deltaTime); // decayRate = 0.5/sec
        vrm10.Runtime.Expression.SetWeight(key, w);
    }
}
```

---

## 5. Idle Micro-Expression - Membuat Avatar "Hidup"

Tanpa micro-expression saat idle, avatar terlihat statis seperti foto. Empat layer wajib:

### 5.1 Spontaneous Blink

Human spontaneous blink rate: **15-20 blink/menit** rata-rata (3-4 detik antar blink). Modulated by attention, emotion, fatigue. Research Disney "Modeling and Animating Eye Blinks" (Trutoiu et al., 2011) merekomendasi distribusi Poisson dengan rate parameter λ = 1/3.5 sec.

**Implementasi:**
```csharp
// Coroutine
IEnumerator BlinkScheduler()
{
    while (true)
    {
        // Poisson-distributed interval: -ln(1-uniform) / lambda
        float u = Random.Range(0.001f, 0.999f);
        float interval = -Mathf.Log(1f - u) * 3.5f; // mean 3.5 sec, range ~0.5-12 sec
        interval = Mathf.Clamp(interval, 1.5f, 8f); // sanity clamp

        yield return new WaitForSeconds(interval);

        yield return BlinkOnce(); // 150 ms close + 80 ms open

        // 8% chance double-blink (natural human pattern)
        if (Random.value < 0.08f)
        {
            yield return new WaitForSeconds(0.2f);
            yield return BlinkOnce();
        }
    }
}

IEnumerator BlinkOnce()
{
    var key = ExpressionKey.CreateFromPreset(ExpressionPreset.Blink);
    // close 0 -> 1 in 80ms
    yield return DOTween.To(() => 0f, w => vrm10.Runtime.Expression.SetWeight(key, w),
                             1f, 0.08f).SetEase(Ease.OutQuad).WaitForCompletion();
    yield return new WaitForSeconds(0.05f); // closed hold
    // open 1 -> 0 in 100ms
    yield return DOTween.To(() => 1f, w => vrm10.Runtime.Expression.SetWeight(key, w),
                             0f, 0.10f).SetEase(Ease.InQuad).WaitForCompletion();
}
```

Tuning: **18 blink/min** menghasilkan kesan "paling ramah" untuk human-style avatar (Takashima et al., Graphics Interface 2008).

### 5.2 Saccade / Microsaccade

Microsaccade: 1-2 per detik saat fixation, amplitudo 2-120 arcminutes. Saccade besar saat shift gaze: 2-3 per detik di natural viewing.

Untuk VRM idle, **mini-saccade** lebih cocok daripada microsaccade akurat (avatar tidak butuh medical realism). Implementasi: tiap 0.8-2.5 detik random, geser LookAt target ±5° dari center, durasi shift 80-120 ms.

```csharp
IEnumerator SaccadeScheduler()
{
    while (true)
    {
        yield return new WaitForSeconds(Random.Range(0.8f, 2.5f));

        Vector3 offset = new Vector3(
            Random.Range(-0.05f, 0.05f), // horizontal
            Random.Range(-0.03f, 0.03f), // vertical
            0f);
        Vector3 originalTarget = lookAtTarget.localPosition;
        Vector3 newTarget = originalTarget + offset;

        yield return DOTween.To(() => originalTarget,
            v => lookAtTarget.localPosition = v,
            newTarget, 0.10f).SetEase(Ease.OutCubic).WaitForCompletion();

        yield return new WaitForSeconds(Random.Range(0.3f, 1.5f)); // fixation

        // Return ke center 60% probability, atau lanjut wander
        if (Random.value < 0.6f)
            lookAtTarget.localPosition = originalTarget;
    }
}
```

### 5.3 Subtle Smile / Brow Twitch

Setiap 6-15 detik (random), apply lightweight `happy = 0.08..0.15` selama 500-900 ms lalu fade out. Atau alternasi `relaxed = 0.1` (default baseline). Ini bikin avatar terasa "mood positive baseline" daripada stoic.

### 5.4 Breathing (Chest Bone)

VRM humanoid bone hierarchy: `Hips -> Spine -> Chest -> UpperChest -> Neck -> Head`. Field `chest` dan `upperChest` opsional di rig - check `vrm10Instance.Humanoid.GetBoneTransform(HumanBodyBones.Chest)` apakah null.

**Procedural breathing (additive Animator layer):**
```csharp
void Update()
{
    if (chestBone == null) return;

    // Sine wave: 14 breath/min = 0.233 Hz
    float breathPhase = (Time.time * 0.233f) % 1.0f;
    float scale = Mathf.Sin(breathPhase * Mathf.PI * 2f) * 0.012f; // ±1.2%

    chestBone.localScale = baseChestScale * (1f + scale);
    // Slight rotation untuk shoulder rise
    chestBone.localRotation = baseChestRot *
        Quaternion.Euler(-scale * 5f, 0f, 0f);
}
```

Lebih baik via Animator additive layer dengan animation clip `Idle_Breathing.anim` di-loop, blend weight = 1.0. Avoid GC alloc dari `Quaternion.Euler` per frame - cache hasil dan gunakan struct only.

### 5.5 Idle Pose Variation

Animator State Machine state `IdleBase` -> after 8-15 sec random transition ke:
- `IdleWeightShift` (geser BB ke kaki kiri/kanan)
- `IdleHeadTilt` (miringkan kepala 3-5°)
- `IdleShoulderRoll` (gerakan bahu kecil)

Semua additive layer di atas base humanoid pose, bukan override - supaya tidak konflik dengan gesture/emotion utama.

---

## 6. Gaze / LookAt System

### 6.1 Dua Mode VRM 1.0

Spec VRM 1.0 mendukung dua tipe `lookAt.type`:

**Bone mode** (`bone`): rotate eye bones (`LeftEye`, `RightEye`) berdasarkan sudut head-to-target. Curve range degree (default 90°) menentukan sensitivity - sudut head-target = curve range → eye rotation max. Cocok untuk: model realistic dengan eye bone rig.

**Expression mode** (`expression`): driving 4 blendshape `lookUp/Down/Left/Right`. Saat sudut head-target 90°, blendshape weight = 1.0. Cocok untuk: anime stylized dengan eye yang digambar di texture (UV offset effectively via blendshape).

**Cek mode programmatically:**
```csharp
var lookAtType = vrm10Instance.Vrm.LookAt.LookAtType;
// VRM10ObjectLookAt.LookAtTypes.Bone atau .Expression
```

### 6.2 Target Selection - Floating Overlay Context

Avatar VRM Assistant Android berada di **floating overlay** di atas UI HP. Target gaze contextual:

| Konteks | Look target |
|---|---|
| Default idle | Kamera virtual (face the user) |
| User tap notifikasi | Notif element position |
| User scrolling app | Subtle follow scroll direction |
| TTS speaking | Camera (eye contact 60% time) |
| User silent >10s | Random wander (window edge, occasional re-fixate kamera) |

### 6.3 Eye Contact 60% / 40% Break

Research Hessels et al. (Scientific Reports 2018) menemukan natural eye-to-eye mutual gaze rata-rata **hanya 12% waktu percakapan**, kontak singkat. Tapi konteks avatar VTuber tidak sama dengan dual-person - avatar **memandang user** (one-way). Convention industri VTuber:

- **Speaking:** 60% eye contact ke kamera, 40% glance away (down ~30%, side ~10%). PNAS Brooks et al. (2021) menemukan eye contact menandai "rise and fall of shared attention" - bukan sustain.
- **Listening:** 70% kontak (user lagi bicara/tap input).
- **Idle:** wander pattern (section 5.2 saccade).

**Scheduler:**
```csharp
IEnumerator GazeContactScheduler()
{
    while (true)
    {
        // 60% chance contact mode
        if (Random.value < 0.6f)
        {
            lookAtTarget = mainCamera.transform;
            yield return new WaitForSeconds(Random.Range(1.5f, 3.5f));
        }
        else
        {
            // Break direction
            Vector3 breakDir = Random.value < 0.7f ?
                new Vector3(0, -0.2f, 1f) :  // look down
                new Vector3(Random.Range(-0.3f, 0.3f), 0f, 1f);
            breakTarget.position = mainCamera.transform.position + breakDir;
            lookAtTarget = breakTarget;
            yield return new WaitForSeconds(Random.Range(0.4f, 1.2f));
        }
    }
}
```

### 6.4 Set LookAt Target di UniVRM

```csharp
// VRM 1.0
vrm10Instance.LookAtTargetType = VRM10ObjectLookAt.LookAtTargetTypes.SpecifiedTransform;
vrm10Instance.LookAtTarget = userCameraTransform;

// VRM 0.x
var lookAt = GetComponent<VRMLookAtHead>();
lookAt.Target = userCameraTransform;
lookAt.UpdateType = UpdateType.LateUpdate;
```

**Penting:** Expression must be applied **setelah** LookAt (per spec) karena LookAt-blendshape mode driving `lookUp/Down/Left/Right` weight - kalau expression dengan `overrideLookAt = blend` aktif duluan, hasil tidak deterministic. UniVRM internal pipeline sudah handle order ini.

---

## 7. Open-Source Implementation Reference

### 7.1 VSeeFace
- **URL:** https://www.vseeface.icu/
- **License:** Freeware (closed source, gratis non-commercial + commercial OK)
- **VRM support:** **VRM 0.x only** (tidak VRM 1.0)
- **Emotion engine:** Webcam tracking via OpenSeeFace (BSD-2). Expression detection facial landmark, custom expression hotkey trigger.
- **Performance:** desktop only, ~10-15 ms tracking latency.
- **Relevance untuk Android:** **Low** - desktop-focused, tapi referensi untuk expression hotkey config & VMC protocol.

### 7.2 VTube Studio
- **URL:** https://denchisoft.com/, wiki https://github.com/DenchiSoft/VTubeStudio/wiki
- **License:** Commercial (Steam $14.99), API gratis
- **Avatar:** Live2D primary (bukan VRM)
- **Emotion mechanism:** `.exp3.json` expression file. Hotkey trigger via API JSON over WebSocket. Auto-deactivate after N seconds. Auto-blink configurable per parameter.
- **API pattern paling relevan:** TriggerHotkey API call dengan hotkey ID/name -> apply expression dengan auto-deactivate. Pola ini bisa diadopt untuk VRM Assistant: LLM emit hotkey name -> bridge ke `SetWeight()`.

### 7.3 Open-LLM-VTuber
- **URL:** https://github.com/Open-LLM-VTuber/Open-LLM-VTuber
- **License:** MIT (sample Live2D models terpisah Live2D Free Material License)
- **Avatar:** Live2D primary, VRM minimal support
- **Emotion implementation (paling relevan untuk kita):**
  - `live2d_model.emo_str` = list emotion keyword di-inject ke system prompt
  - LLM emit emotion tag inline: `Hello [joy] how are you?`
  - Backend parse tag dengan regex, strip dari TTS text, kirim emotion event ke frontend via WebSocket
  - Frontend trigger expression via Live2D parameter set
  - Config file `model_dict.json`:
    ```json
    "emotionMap": {
      "neutral": 0, "anger": 2, "joy": 3, "surprise": 5, "sadness": 1
    }
    ```
  - Placeholder `[<insert_emomap_keys>]` di system prompt diganti dengan key dari emotionMap
- **Latency:** tidak ada claim eksplisit, tapi streaming SentenceOutput → AudioOutput pipeline minimize delay.

### 7.4 VRM4U (Unreal)
- **URL:** https://ruyo.github.io/VRM4U/, https://github.com/ruyo/VRM4U
- **License:** MIT
- **Emotion:** Morphtarget/BlendShapeGroup API. ControlRig generic dari rig humanoid. VMC protocol receiver (terima emotion dari VSeeFace eksternal).
- **Relevance untuk Unity Android:** **Low** (Unreal-focused) tapi VMC protocol reference berguna kalau VRM Assistant ingin support external face tracker.

### 7.5 FACSvatar
- **URL:** https://github.com/NumesSanguis/FACSvatar
- **License:** GPLv3 (caution - copyleft, hati-hati kalau commercial)
- **Engine:** Unity3D / Blender. FACS Action Unit (AU) → blendshape mapping real-time, modular ZeroMQ messaging.
- **Relevance:** referensi FACS-to-blendshape mapping. AU1 (inner brow raiser), AU12 (lip corner puller=smile) bisa di-mapping ke custom VRM expression.

### 7.6 VMC Protocol (Virtual Motion Capture)
- **URL:** https://protocol.vmc.info/english.html
- **OSC-based:** port 39539 (receiver), 39540 (sender). Message `/VMC/Ext/Blend/Val (string name) (float value)` + `/VMC/Ext/Blend/Apply`.
- **Relevance untuk VRM Assistant:** kalau Phase 3 ingin external face tracker (webcam laptop user → kirim ke HP via WiFi VMC), sudah ada protokol terstandar.

### 7.7 Tabel Ringkas

| Project | License | VRM | Mobile? | Use untuk kita |
|---|---|---|---|---|
| VSeeFace | Freeware | 0.x | No | Reference expression hotkey config |
| VTube Studio | Commercial + API | No (Live2D) | iOS/Android tracker | API pattern hotkey JSON |
| Open-LLM-VTuber | MIT | Minimal | No (desktop) | **Emotion tag pattern - paling relevan** |
| VRM4U | MIT | 1.0 | No (Unreal) | VMC protocol reference |
| FACSvatar | GPLv3 | Generic | No | FACS AU mapping (academic) |

---

## 8. LLM-Driven Expression - Tag, Function Call, JSON Sidecar

### 8.1 Tiga Pola Output Structured

**Pola 1: Inline emotion tag (Open-LLM-VTuber style)**
```
Sistem prompt: "Available emotions: [joy] [sad] [angry] [surprised] [neutral]. 
Embed tag in response, e.g. 'Halo [joy] senang bertemu!'"

LLM output: "Aku sangat senang [joy] kamu kembali! Tapi maaf [sad] aku belum 
beresin tugas yang tadi."

Backend regex: r'\[(\w+)\]'
  -> match [joy] at offset 14
  -> match [sad] at offset 60
  -> strip dari text untuk TTS
  -> emit events: { emotion: "joy", time: 0ms }, { emotion: "sad", time: ~1500ms based on TTS phoneme alignment }
```

Kelebihan: simple, tidak butuh function calling support, robust di model kecil (Gemma 4 2B).
Kekurangan: parsing rapuh kalau LLM emit malformed tag.

**Pola 2: Function call / tool use**
```json
{
  "tool": "set_expression",
  "args": { "name": "happy", "weight": 0.7, "duration_ms": 2000 }
}
```
Lebih structured, model harus support function calling (Claude, GPT-4o, Gemma 3 instruct). Berat untuk inline-mixed text generation.

**Pola 3: JSON sidecar dengan timeline**
```
LLM output (dual channel):
  text: "Halo, senang bertemu! Tapi maaf aku belum beresin tugas tadi."
  expression_timeline: [
    { "t_ms": 0, "emotion": "happy", "weight": 0.8 },
    { "t_ms": 1500, "emotion": "happy", "weight": 0.2 },
    { "t_ms": 2200, "emotion": "sad", "weight": 0.6 }
  ]
```
Paling ideal untuk sync TTS audio, tapi butuh output schema enforcement (Outlines, Instructor, atau grammar-constrained sampling).

**Rekomendasi MVP: Pola 1 (inline tag)** - sederhana, robust, mudah implement. Switch ke Pola 3 di Phase 2 untuk sinkronisasi presisi.

### 8.2 Sync dengan TTS Audio

Sub-issue: emotion event terjadi di **text offset N**, tapi audio TTS belum di-render. Solusi:

**a) Streaming TTS dengan boundary marker:**
- Split text di tag position: `["Aku sangat senang", JOY_EVENT, "kamu kembali! Tapi maaf", SAD_EVENT, ...]`
- TTS render chunk-by-chunk
- Antara chunk i dan chunk i+1, emit emotion event di main thread
- Audio mixer queue: `audio_chunk_0.play()` → on `OnAudioFinished` → emit event → `audio_chunk_1.play()`

**b) Phoneme alignment (advanced):**
- TTS engine (e.g. Piper, Coqui-TTS) emit phoneme timestamp
- Map text offset → phoneme index → audio millisecond
- Schedule emotion `SetWeight()` di exact audio ms via DOTween delay atau MainThreadDispatcher

**Sample latency budget:**
- LLM token (Gemma 4 2B local) ~50 tok/s = 20 ms/token
- TTS (Piper local) ~150 ms per sentence  
- ONNX emotion classifier (kalau dipakai post-hoc): 30 ms
- Network/IPC overhead: 10 ms
- Total perceived: **<200 ms** dari teks generate sampai expression muncul = acceptable

---

## 9. Animation System Unity

### 9.1 Mecanim Animator Controller

Layered approach:
- **Layer 0 - Base Body** (weight 1.0, Override): humanoid idle, walk, gesture
- **Layer 1 - Face Emotion** (weight 1.0, Additive, Mask=face only): emotion blendshape via animation clip OR direct `SetWeight()` dari script
- **Layer 2 - Face Lipsync** (weight 1.0, Additive, Mask=mouth only): viseme dari uLipSync
- **Layer 3 - Face Blink** (weight 1.0, Override, Mask=eyelid only): blink scheduler

**Penting:** Mecanim animation clip akan **override** `SetWeight()` jika clip animate blendshape yang sama. Solusi: jangan animate blendshape via clip - semua via script `SetWeight()`. Animator hanya animate **bone** (humanoid pose, breathing chest).

Referensi Unity Discussions "Mecanim Locking my facial blendshapes" - konflik klasik antara clip dan script.

### 9.2 DOTween untuk Easing

- Package: `com.demigiant.dotween` (UPM via OpenUPM atau Asset Store gratis core version).
- Pakai untuk: fade in/out emotion, blink animation, saccade smooth.
- Easing: `Ease.OutQuad`, `Ease.InOutSine`, `Ease.OutBack` (slight overshoot untuk surprise).
- **Avoid `DOVirtual.Float` di Update loop** - allocate Tween object. Gunakan cached tween yang di-restart dengan `Restart()` atau manual lerp.

**Pattern minimal-alloc:**
```csharp
class EmotionFader
{
    float current, target, velocity;
    public void SetTarget(float t) => target = t;
    public void Tick(float dt) =>
        current = Mathf.SmoothDamp(current, target, ref velocity, 0.3f);
}
```
SmoothDamp = no alloc, no Tween object.

### 9.3 Coroutine Timing

- Blink scheduler: long-lived coroutine ok (alloc 1x).
- Saccade: long-lived coroutine ok.
- Per-emotion fade: hindari `StartCoroutine` per-event - reuse pool atau gunakan UniTask (zero-alloc).

### 9.4 GC Allocation Audit

Audit checklist untuk Update loop:
- ❌ `new Vector3(...)` per frame -> cache di field
- ❌ `string.Format()` atau `$"..."` -> avoid log per frame
- ❌ `ExpressionKey.CreateFromPreset()` per frame -> cache `static readonly` keys
- ❌ `Quaternion.Euler()` per frame -> kalau perlu, pakai struct local
- ❌ Allocating list/array per frame -> reuse buffer

**Cache pattern:**
```csharp
static readonly ExpressionKey HappyKey = ExpressionKey.CreateFromPreset(ExpressionPreset.Happy);
static readonly ExpressionKey SadKey   = ExpressionKey.CreateFromPreset(ExpressionPreset.Sad);
// ...etc
```

---

## 10. Mobile Performance Concern

### 10.1 Snapdragon 8 Elite Gen 5 Budget

- CPU: Oryon 3rd gen 2x prime + 6x perf ~ 4.3 GHz
- GPU: Adreno 840
- NPU: Hexagon Tensor v3 (~75 TOPS INT8)
- Memory: LPDDR5X 12-16 GB

VRM avatar typical:
- Vertex count: 10-25K (VRoid default ~18K). Skinned mesh.
- Blendshape count: 30-80 (preset + custom). VRM 0.x limit 64 morph targets.
- Texture: 2048x2048 main, 1024x1024 face = ~30 MB uncompressed, ~6 MB ASTC 6x6.
- Material: 3-6 sub-mesh dengan MToon shader (URP variant).

### 10.2 URP Mobile Optimization

- **Store Actions = Auto/Discard** (URP setting): hindari render target copy overhead.
- **Disable post-processing** di overlay (kecuali subtle bloom).
- **Reduce blendshape per mesh** - VRM consortium recommend: pisah face mesh + body mesh, body mesh tanpa blendshape.
- **Texture format ASTC 6x6** untuk Android - ~3x lebih kecil dari ETC2, quality cukup.
- **Shader LOD** - MToon mobile preset (less feature, no MatCap fallback).

### 10.3 Frame Budget Target

Floating overlay app jalan bersamaan dengan app lain - **frame budget ketat**:
- Target 60 fps -> 16.67 ms/frame
- Allocate untuk VRM: **5-6 ms/frame** max
  - Skinned mesh blendshape update: 1-2 ms (40 blendshapes × 18K vert)
  - LookAt + Expression logic: 0.3 ms
  - URP draw call (4 sub-mesh): 1.5 ms
  - SpringBone (hair, skirt physics): 1-2 ms
- Sisanya untuk Unity URP base + host Compose

### 10.4 Battery Impact - 30 min Idle

Estimasi (Snapdragon 8 Elite Gen 5, brightness 50%, AMOLED on):
- Idle avatar (no LLM, no audio): ~250-350 mA = ~3-4% battery/hour
- Active conversation (LLM streaming + TTS + emotion classifier ONNX): ~600-800 mA = ~8-11% battery/hour
- Untuk 30 menit idle: ~1.5-2% battery drain - **acceptable**

Optimasi tambahan:
- **Lower target FPS saat idle**: 30 fps saat no interaction (toggle `Application.targetFrameRate`)
- **Disable SpringBone saat user tidak interact** 10s+
- **Skip emotion classifier inference** kalau LLM dalam idle state

### 10.5 Memory Footprint

Estimasi total memory VRM Assistant runtime:
- VRM avatar (mesh + textures + animation): **80-150 MB**
- UniVRM runtime: ~5 MB
- ONNX emotion classifier (INT8): **30-50 MB**
- TTS model (Piper small): **20-40 MB**
- LLM (Gemma 4 2B quantized): **1.5 GB** (terbesar)
- Unity URP base: 80 MB
- **Total: 1.7-2 GB** - aman untuk HP 12 GB+ tapi tight untuk 8 GB

Recommendation: VRM Assistant **tidak untuk HP <8 GB RAM** karena bottleneck LLM.

---

## 11. Konkret Recommendation - MVP & Roadmap

### 11.1 MVP (Fase 1 - Sprint 1-2)

**Emotion engine:**
- 5 emosi: `happy`, `sad`, `angry`, `surprised`, `neutral`
- Source: **inline tag dari LLM output** (`[joy]`, `[sad]` etc.) - Open-LLM-VTuber pattern
- Fade in 300 ms (`Ease.OutQuad`), hold 2-4 sec, fade out 500 ms (`Ease.InOutSine`)
- Decay rate 0.5/sec saat no event >3 sec → neutral

**Idle:**
- Blink: Poisson λ=1/3.5 sec, clamp [1.5, 8] sec, 80 ms close + 50 ms hold + 100 ms open
- Saccade: every 0.8-2.5 sec, ±5° offset, 100 ms duration
- Subtle smile: every 8-15 sec, `happy=0.1`, hold 600 ms
- Breathing: 14 breath/min sine, ±1.2% chest scale

**Gaze:**
- Default: lock to camera (face the user)
- Speaking: 60% camera contact, 40% break (70% down, 30% side)
- Idle wander: enable saccade scheduler

**Override config (di VRoid expression export):**
- `happy.overrideBlink = blend`
- `surprised.overrideBlink = block`
- viseme all `override = none`

### 11.2 Fase 2 (Sprint 3-4)

- Audio-based emotion via **Wav2Small ONNX** (120 KB) - VAD continuous dari TTS audio prosodi
- Fusion text+audio (60/40 valence, 30/70 arousal)
- 28-emotion GoEmotions classifier untuk fine-grained mood tracking (background, tidak per-utterance trigger)
- JSON sidecar timeline output dari LLM (kalau model support structured output)

### 11.3 Fase 3 (Sprint 5+)

- Expression phoneme alignment (Piper TTS timestamp → expression event ms-accurate)
- External face tracker via VMC protocol (user webcam → VRM mirror mode)
- Custom expression user-trainable: tap+hold area wajah avatar → "remember this expression as ___"
- Personality emotion bias (model "shy" vs "cheerful" base mood)

### 11.4 Risk & Mitigation

| Risk | Likelihood | Mitigation |
|---|---|---|
| VRM model user tidak punya expression preset lengkap | High | Fallback ke neutral + log warning, recommend VRoid Studio export |
| ONNX emotion classifier lag (>50 ms) | Medium | Run inference di background thread, cache last result |
| Battery drain >5%/hour idle | Medium | Lower fps idle, disable SpringBone non-interact |
| LLM emit malformed tag `[joy ]` (trailing space) | Medium | Regex `\[(\w+)\s*\]`, log telemetry |
| Konflik Mecanim clip animate blendshape vs script SetWeight | High | Audit semua .anim file, hapus blendshape track, gunakan script saja |

---

## 12. Sumber Referensi (akses 2026-05-13)

**Spec & Official Docs:**
- VRM 1.0 Expression spec: https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_vrm-1.0/expressions.md
- VRM 1.0 Animation spec: https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_vrm_animation-1.0/README.md
- UniVRM Expression System: https://deepwiki.com/vrm-c/UniVRM/3.2-expression-system
- UniVRM v0.131.0 release: https://github.com/vrm-c/UniVRM/releases/tag/v0.131.0
- UniVRM LookAt Settings: https://github.com/vrm-c/UniVRM/wiki/LookAt-Settings
- vrm.dev expression tutorial: https://vrm.dev/en/univrm1/vrm1_tutorial/expression/
- VMC Protocol: https://protocol.vmc.info/english.html

**LipSync Integration:**
- uLipSync VRM sample: https://github.com/hecomi/uLipSync/blob/main/Assets/uLipSync/Samples/04.%20VRM/Runtime/uLipSyncExpressionVRM.cs
- uLipSync PR #27 (VRM expression): https://github.com/hecomi/uLipSync/pull/27

**Emotion Models:**
- Wav2Small paper arXiv:2408.13920: https://arxiv.org/abs/2408.13920
- Wav2Small GitHub: https://github.com/dkounadis/wav2small
- SamLowe roberta-base-go_emotions-onnx: https://huggingface.co/SamLowe/roberta-base-go_emotions-onnx
- SpeechBrain wav2vec2-IEMOCAP: https://huggingface.co/speechbrain/emotion-recognition-wav2vec2-IEMOCAP
- ModernBERT GoEmotions: https://huggingface.co/cirimus/modernbert-base-go-emotions
- openSMILE 3.0: https://www.audeering.com/research/opensmile/
- Hume Expression Measurement (sunset 2026-06-14): https://dev.hume.ai/docs/expression-measurement/overview

**Emotion Research:**
- Russell circumplex 1980 PDF: https://pdodds.w3.uvm.edu/research/papers/others/1980/russell1980a.pdf
- Circumplex Emotion AI MorphCast: https://www.morphcast.com/blog/circumplex-model-of-affects/
- Mapping Discrete Emotions Acoustic (MDPI): https://www.mdpi.com/2079-9292/10/23/2950
- Audio2Face-3D arXiv:2508.16401: https://arxiv.org/abs/2508.16401
- EmoFace arXiv:2407.12501: https://arxiv.org/abs/2407.12501
- EmoTalk ICCV 2023: https://openaccess.thecvf.com/content/ICCV2023/papers/Peng_EmoTalk_Speech-Driven_Emotional_Disentanglement_for_3D_Face_Animation_ICCV_2023_paper.pdf

**Idle Behavior Research:**
- Eye blink modeling Disney: https://la.disneyresearch.com/wp-content/uploads/Modeling-and-Animating-Eye-Blinks-Paper.pdf
- Avatar blink impressions Graphics Interface 2008: https://dl.acm.org/doi/10.5555/1375714.1375744
- Microsaccade compact field guide: https://pmc.ncbi.nlm.nih.gov/articles/PMC4537412/
- Eye contact PNAS Brooks et al.: https://www.pnas.org/doi/10.1073/pnas.2106645118
- Natural eye contact dual mobile eye tracking: https://www.nature.com/articles/s41598-023-38346-9

**Open-Source Projects:**
- Open-LLM-VTuber: https://github.com/Open-LLM-VTuber/Open-LLM-VTuber
- Open-LLM-VTuber Live2D config wiki: https://deepwiki.com/Open-LLM-VTuber/Open-LLM-VTuber/10.1-live2d-model-configuration
- VSeeFace: https://www.vseeface.icu/
- VTube Studio Wiki Expressions: https://github.com/DenchiSoft/VTubeStudio/wiki/Expressions-(a.k.a.-Stickers-or-Emotes)
- VTube Studio API: https://github.com/DenchiSoft/VTubeStudio
- VRM4U: https://github.com/ruyo/VRM4U
- FACSvatar: https://github.com/NumesSanguis/FACSvatar

**Unity & Mobile:**
- Unity Animation Layers: https://docs.unity3d.com/Manual/AnimationLayers.html
- URP Configure for performance: https://docs.unity3d.com/Packages/com.unity.render-pipelines.universal@14.0/manual/configure-for-better-performance.html
- DOTween docs: https://dotween.demigiant.com/documentation.php
- ONNX Runtime on Unity (asus4): https://medium.com/@asus4/onnx-runtime-on-unity-a40b3416529f
- ONNX Runtime mobile: https://onnxruntime.ai/docs/tutorials/mobile/
- Cluster VRM optimization: https://medium.com/@cluster_official/optimizing-avatars-vrm-models-in-unity-for-cluster-c5f7f1aaf920

---

## Catatan untuk Implementasi

1. **Audit dulu VRM model target** sebelum coding - banyak model VRoid output **tidak punya** preset `relaxed` atau `surprised`. Pakai `vrm10Instance.Vrm.Expression.HasPreset(ExpressionPreset.Surprised)` untuk cek runtime; kalau tidak ada, fallback ke composite custom expression atau pakai `happy + 0.5` saja.

2. **Test override behavior** dengan model spesifik - VRoid default punya `happy.overrideBlink = block` (mata jadi `^_^` saat senyum). Ini perilaku **anime style** yang mungkin user inginkan, jangan auto-replace ke `blend`.

3. **MToon shader** punya emission map yang bisa di-modulate per emotion (e.g. cheek blush emission untuk `embarrassed`) - eksperimen worthwhile setelah MVP.

4. **TTS Indonesia** belum diaudit di doc ini - lihat doc `03-emotion-tts.md` untuk pemilihan engine (Piper Indonesia, Coqui-TTS). Latency TTS biggest bottleneck untuk emotion sync.

5. **Animator parameter naming convention** - prefix dengan `face_`, `body_`, `gaze_` untuk clarity di multi-layer setup.

Untuk pertanyaan/iterasi: prioritaskan implementasi Section 11.1 (MVP) - 5 emosi inline tag, idle layer, gaze 60/40 - sudah cukup untuk feel "VTuber alive" tanpa kompleksitas audio classifier.
