# Deep Research: FSRS Tuning + Prompt Engineering Gemma 4 untuk Tomo Sensei

**Tanggal akses sumber:** 2026-05-13
**Konteks proyek:** Tomo Sensei — Android app belajar Bahasa Jepang. Multi-module Compose (kana / kanji / vocab / grammar / sentence drill). LLM engine: Gemma 4 (LiteRT-LM) untuk generate practice content + grade user response. Memori: FSRS untuk schedule review.
**Tujuan dokumen:** Memberikan basis teknis end-to-end untuk integrasi FSRS sebagai scheduler memori dan Gemma 4 sebagai content/grader engine pada arsitektur on-device.

---

## 1. State of the Art FSRS (per Mei 2026)

### 1.1 Lineage versi

FSRS (Free Spaced Repetition Scheduler) dikembangkan oleh Jarrett Ye (L.M.Sherlock) sebagai algoritma scheduler berbasis model DSR (Difficulty, Stability, Retrievability). Lineage utama:

| Versi | Tahun rilis | Parameter | Inti perubahan |
|-------|-------------|-----------|---------------|
| FSRS-3 | 2022 | 13 | Versi awal stabil di Anki plugin |
| FSRS-4 | 2023 | 17 | Pengganti exponential forgetting curve dengan power function |
| FSRS-4.5 | 2024 awal | 17 | Power function direvisi lagi, fit ke data lebih baik |
| FSRS-5 | 2024 pertengahan | 19 | Tambah 4 initial stability params (w0-w3); memperhitungkan same-day reviews saat training |
| FSRS-6 | 2025 (Apr) | 21 | Tambah w19 (post-lapse stability damping) + w20 (decay parameter per-user untuk forgetting curve); Anki 25.07+ native support |

> Catatan kewaspadaan: FSRS-7 sempat disebut di forum sebagai work-in-progress untuk model short-term memory yang lebih akurat, tetapi pada Mei 2026 versi stable produksi adalah FSRS-6. Confidence rendah untuk klaim spesifik tentang FSRS-7 — jangan asumsikan rilis di Tomo Sensei v1.

### 1.2 Default parameter weights

**FSRS-5 (19 params)** — masih banyak dipakai di Anki/AnkiDroid:

```
w = [0.40255, 1.18385, 3.173, 15.69105, 7.1949,
     0.5345, 1.4604, 0.0046, 1.54575, 0.1192,
     1.01925, 1.9395, 0.11, 0.29605, 2.2698,
     0.2315, 2.9898, 0.51655, 0.6621]
```

**FSRS-6 (21 params)** — default rekomendasi 2025:

```
w = [0.212, 1.2931, 2.3065, 8.2956, 6.4133,
     0.8334, 3.0194, 0.001, 1.8722, 0.1666,
     0.796, 1.4835, 0.0614, 0.2629, 1.6483,
     0.6014, 1.8729, 0.5425, 0.0912,
     0.0658,   // w19 — short-term S damping
     0.1542]   // w20 — decay parameter (0.1-0.8, mayoritas user <0.2)
```

Parameter mapping (FSRS-6):
- `w0..w3` — Initial stability after first review by grade (Again/Hard/Good/Easy)
- `w4` — Initial difficulty offset
- `w5` — Initial difficulty grade slope
- `w6` — Difficulty update slope
- `w7` — Difficulty mean reversion factor
- `w8..w11` — Stability gain factors (saturation curve)
- `w12..w14` — Difficulty/retrievability penalties pada stability
- `w15` — Hard penalty multiplier
- `w16` — Easy bonus multiplier
- `w17..w18` — Same-day review stability change
- `w19` — Saturation kuadratik (mirip w9 untuk same-day)
- `w20` — Decay parameter (controls forgetting curve flatness per-user)

### 1.3 Formulae inti

**Retrievability (FSRS-6):**

```
R(t, S) = (1 + FACTOR * t / S) ^ (-1 / DECAY)
DECAY = w20
FACTOR = 0.9 ^ (-DECAY) - 1   // ensure R(S, S) = 0.9
```

Catatan: di FSRS-4.5/5, DECAY adalah konstanta 0.5 (=> R = (1 + t/(9S))^(-0.5)). Di FSRS-6, DECAY dapat dipersonalisasi via w20.

**Next interval (target retention R_d):**

```
I(R_d, S) = (S / FACTOR) * (R_d ^ (-1/DECAY) - 1)
```

**Stability update after successful recall (Hard/Good/Easy):**

```
S' = S * (1 + e^w8 * (11 - D) * S^(-w9) * (e^((1-R)*w10) - 1)
         * hard_penalty * easy_bonus)

hard_penalty  = w15 (kalau grade=Hard, else 1)
easy_bonus    = w16 (kalau grade=Easy, else 1)
```

**Stability after lapse (Again):**

```
S_post_lapse = w11 * D^(-w12) * ((S+1)^w13 - 1) * e^((1-R)*w14)
S_short      = min(S_post_lapse, S)   // clamp; FSRS-6 menambah saturasi w19
```

**Difficulty update:**

```
ΔD = -w6 * (rating - 3)
D_new = D + ΔD * (10 - D) / 9         // smoothing
D_target = w4 - e^(w5 * (rating - 1)) + 1   // for initial
D_new = w7 * D_target + (1 - w7) * D_new    // mean reversion ke easy baseline
D_new = clamp(D_new, 1, 10)
```

Difficulty selalu dalam range [1, 10]. Stability dalam hari, biasanya 1–36500 (clamp atas).

### 1.4 Optimizer

FSRS optimizer melakukan **maximum likelihood estimation** dengan loss function **binary cross-entropy (log-loss)** pada outcome review (pass/fail):

```
L = -Σ [y_i * log(R_i) + (1 - y_i) * log(1 - R_i)]
```

di mana `y_i` = 1 jika user recall sukses (rating >= Hard), `R_i` = prediksi retrievability saat review ke-i.

**Workflow optimizer:**
1. Estimasi initial stability (w0..w3) langsung dari first/second review outcomes.
2. Gradient descent (Adam optimizer di py-fsrs / fsrs-optimizer) pada semua weights.
3. Validation split untuk anti-overfit.
4. Output: 21 weights baru yang ditulis kembali ke storage.

**Minimum review count:**
- Lama (FSRS-4): 1000 reviews.
- Anki 24.04: 400.
- Versi 24.06+ dan riset Sherlock/Expertium: 16 atau lebih bahkan bisa optimization (eksperimental). Disclaimer: hasil <100 reviews biasanya overfit; rekomendasi praktis **≥400 reviews** sebelum override default weights, **≥1000** sebelum hasil benar-benar reliable.

### 1.5 FSRS vs SuperMemo

**Cross-comparison benchmark (Expertium, Mar 2025):**
- FSRS-6 menang di 83.3% dataset terhadap SM-17(exp).
- Metode: log-loss + RMSE pada 10k+ user Anki real reviews.

**Kritik dari SuperMemo (Piotr Wozniak):**
- Anki dataset bias ke crammer/procrastinator → unrepresentative.
- Log-loss inappropriate metric untuk SRS evaluation.
- SM-17(exp) hanya approximation linear, bukan actual SM-17 dynamic predictions.

**Pros/Cons untuk Tomo Sensei:**

| Aspek | FSRS-6 | SM-2 (Anki legacy) | SM-17/18 |
|-------|--------|-------------------|----------|
| Open source | Ya (MIT) | Ya | Tidak |
| Optimizer | Per-user gradient descent | Tidak | Internal |
| Akurasi | State of the art | Lemah | Klaim terbaik tapi unverified |
| Implementasi mobile | py-fsrs/fsrs-rs/FSRS-Kotlin tersedia | Trivial | Tidak tersedia |
| Lisensi komersial | Bebas | Bebas | Restricted |

**Rekomendasi:** FSRS-6 untuk Tomo Sensei. Tidak ada alasan teknis maupun lisensi untuk pilih SM-2/17.

---

## 2. Implementasi FSRS di Android Kotlin

### 2.1 Library pilihan

Tiga jalur viable, urut preferensi:

1. **`open-spaced-repetition/FSRS-Kotlin`** — Kotlin port resmi, MIT, sudah ke FSRS-6, dipublikasi Maven (`io.github.open-spaced-repetition:fsrs:<version>`). Pilihan utama.
2. **`open-spaced-repetition/android-fsrs`** — wrapper Android-spesifik (kalau ada utility extra seperti optimizer worker).
3. **Implement ulang dari `py-fsrs` reference** — pilih kalau lib di atas tidak meng-expose API yang dibutuhkan (mis. custom retention per-card).

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("io.github.open-spaced-repetition:fsrs:<latest>") // cek mvnrepository.com
}
```

Verifikasi versi terbaru sebelum commit — Maven Central daftar `io.github.open-spaced-repetition:fsrs` di-update reguler.

### 2.2 Data class & API

Berdasarkan pola di py-fsrs / ts-fsrs (FSRS-Kotlin mengikuti pola serupa):

```kotlin
enum class Rating(val value: Int) {
    AGAIN(1), HARD(2), GOOD(3), EASY(4)
}

enum class State { NEW, LEARNING, REVIEW, RELEARNING }

data class Card(
    val id: Long,
    val due: Instant,
    val stability: Double,
    val difficulty: Double,
    val elapsedDays: Long,
    val scheduledDays: Long,
    val reps: Int,
    val lapses: Int,
    val state: State,
    val lastReview: Instant?
)

data class ReviewLog(
    val cardId: Long,
    val rating: Rating,
    val scheduledDays: Long,
    val elapsedDays: Long,
    val reviewedAt: Instant,
    val state: State
)

data class SchedulingInfo(val card: Card, val log: ReviewLog)

class FSRS(parameters: Parameters = Parameters.default()) {
    fun repeat(card: Card, now: Instant): Map<Rating, SchedulingInfo>
    fun next(card: Card, now: Instant, rating: Rating): SchedulingInfo
}
```

**Initial card (state = NEW):** stability dan difficulty diisi dengan w0..w3 setelah rating pertama; sebelum itu nilai default 0 atau placeholder. `next()` mengisi nilai sebenarnya.

### 2.3 Room schema

Skema minimal untuk Tomo Sensei. Entity `Card` dipisah dari `ContentItem` agar FSRS bisa reusable untuk modul mana pun (kana/kanji/vocab/grammar/sentence).

```kotlin
@Entity(tableName = "content_items")
data class ContentItem(
    @PrimaryKey val id: Long,
    val type: ContentType,         // KANA, KANJI, VOCAB, GRAMMAR, SENTENCE
    val payload: String,           // JSON: kanji char, vocab pair, grammar pattern, dst
    val jlptLevel: Int?,           // 5,4,3,2,1
    val tags: String               // comma-separated
)

@Entity(
    tableName = "cards",
    foreignKeys = [ForeignKey(
        entity = ContentItem::class,
        parentColumns = ["id"],
        childColumns = ["contentId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("contentId"), Index("due")]
)
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentId: Long,
    val direction: Direction,      // RECOGNITION, RECALL, READING, MEANING
    val due: Long,                 // epoch millis
    val stability: Double,
    val difficulty: Double,
    val elapsedDays: Long,
    val scheduledDays: Long,
    val reps: Int,
    val lapses: Int,
    val state: Int,                // ordinal of State enum
    val lastReview: Long?
)

@Entity(
    tableName = "review_logs",
    foreignKeys = [ForeignKey(
        entity = CardEntity::class,
        parentColumns = ["id"],
        childColumns = ["cardId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("cardId"), Index("reviewedAt")]
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val rating: Int,
    val scheduledDays: Long,
    val elapsedDays: Long,
    val reviewedAt: Long,
    val stateAtReview: Int,
    val elapsedMillisGrade: Long?   // optional: berapa lama user pikir sebelum jawab (untuk Hard/Good cutoff)
)

@Entity(tableName = "fsrs_params")
data class FsrsParameters(
    @PrimaryKey val profileId: String = "default",
    val weightsJson: String,       // serialize 21 doubles
    val requestRetention: Double,  // target retention (0.85-0.95)
    val maximumInterval: Int,      // hari, biasanya 36500
    val updatedAt: Long
)
```

DAO contoh:

```kotlin
@Dao
interface CardDao {
    @Query("""SELECT * FROM cards
              WHERE due <= :now
              ORDER BY due ASC
              LIMIT :limit""")
    suspend fun dueCards(now: Long, limit: Int): List<CardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: CardEntity)
}
```

### 2.4 Background optimizer (WorkManager)

```kotlin
class FsrsOptimizerWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val logs = reviewLogDao.allReviewsForOptimization(minCount = 400)
        if (logs.size < 400) return Result.success()  // belum cukup data

        val newWeights = FSRSOptimizer().optimize(
            reviews = logs.toFsrsTrainingSet(),
            iterations = 200,
            learningRate = 0.05
        )
        fsrsParamDao.upsert(currentParams.copy(
            weightsJson = newWeights.toJson(),
            updatedAt = System.currentTimeMillis()
        ))
        return Result.success()
    }
}

// Schedule periodic refit setiap 7 hari, dengan constraint device idle + charging
val request = PeriodicWorkRequestBuilder<FsrsOptimizerWorker>(7, TimeUnit.DAYS)
    .setConstraints(Constraints.Builder()
        .setRequiresCharging(true)
        .setRequiresDeviceIdle(true)
        .build())
    .build()
```

> Catatan: FSRS-Kotlin pada Mei 2026 belum 100% feature-parity dengan py-fsrs untuk optimizer. Kalau optimizer Kotlin belum tersedia, opsi pragmatis: (a) ekspor reviews ke JSON, (b) jalankan `fsrs-optimizer` Python di server backend (opsional Tomo Sensei), atau (c) port ulang gradient descent dari `fsrs-rs` (Rust → Kotlin JNI) untuk fully offline. Untuk MVP Tomo Sensei v1, **gunakan default weights saja** dan defer optimizer ke v1.x.

### 2.5 Edge case

- **Card baru, belum ada review:** `state = NEW`, due = now. Saat user grade pertama kali, FSRS mengisi `stability = w[rating-1]`, `difficulty = D_init(rating)`.
- **Anki-style relearning steps:** FSRS hanya butuh state RELEARNING + same-day reschedule. Tomo Sensei bisa pakai default Anki convention: "Again" → 10 menit → 1 hari relearn cycle.
- **Time zone & date boundary:** simpan epoch millis UTC; "same-day" calculation di app pakai user's local timezone (Anki default 4 AM cutoff).
- **Bulk import deck:** initial schedule semua card due = now; jangan auto-grade Good agar tidak miskalkulasi stability.

---

## 3. Prompt Engineering Gemma 4 untuk Japanese Learning

### 3.1 Pilihan model

Gemma 4 (Apr 2026) hadir dalam 4 ukuran. Untuk Android on-device:

| Model | Effective params | File size (int4) | Konteks | Use-case Tomo Sensei |
|-------|------------------|------------------|---------|---------------------|
| Gemma 4 E2B | ~2.3B | ~2.6 GB | 32k | **Default rekomendasi**: drill gen + grading |
| Gemma 4 E4B | ~4.5B | ~3.8 GB | 32k | Premium tier device flagship |
| Gemma 3 270M | 270M | ~125 MB | 32k | Fallback ultra-low-end, function-call only |

**Rekomendasi:** Gemma 4 E2B untuk drill generation & grading. Gemma 3 270M sebagai fallback (kanji recognition match, romaji normalization—task non-generatif).

### 3.2 Sistem persona "Tomo Sensei"

Sistem prompt yang konsisten lintas modul. Disimpan di asset, di-template per session.

```
Anda adalah Tomo Sensei, asisten belajar Bahasa Jepang yang sabar dan
ramah. Pengguna adalah penutur Bahasa Indonesia yang sedang belajar
Jepang setara JLPT N{LEVEL}.

Aturan respons:
1. Selalu output JSON valid sesuai schema yang diminta. Tidak ada teks
   di luar JSON.
2. Gunakan kanji + furigana untuk semua kata Jepang. Format furigana:
   漢字[かんじ].
3. Penjelasan gunakan Bahasa Indonesia. Contoh Jepang murni.
4. Level scaling: maksimal pakai kanji & grammar JLPT N{LEVEL} ke atas.
   Hindari N{LEVEL-1} dan lebih sulit kecuali diminta eksplisit.
5. Jangan menebak makna kata yang tidak Anda yakini. Kalau ragu, output
   field `confidence` di JSON dengan nilai "low".
6. Tidak boleh memuat romaji kecuali field meminta romaji.
```

Konteks Indonesia + JLPT level + JSON-strict + confidence flag = pondasi anti-halusinasi. Field `confidence` adalah safety net agar app bisa fallback ke kamus offline (JMdict) saat low.

### 3.3 Template prompt per jenis drill

#### 3.3.1 Vocab translation (recall ID → JP)

```
[SYSTEM PROMPT di atas, LEVEL=4]

Tugas: Buat 1 latihan terjemahan untuk kata di bawah.

Kata target: 食べる (たべる) — makan

Output JSON schema:
{
  "prompt_id": "ID kalimat sederhana yang harus diterjemahkan ke Jepang",
  "expected_jp": "kalimat Jepang lengkap dengan kanji+furigana",
  "expected_alt": ["1-3 variasi jawaban yang dianggap benar"],
  "grammar_focus": "pola tata bahasa N4 yang dipakai",
  "hint_id": "petunjuk Bahasa Indonesia 1 kalimat",
  "confidence": "high|low"
}

Contoh:
{
  "prompt_id": "Saya makan sushi di restoran.",
  "expected_jp": "私[わたし]はレストランで寿司[すし]を食[た]べます。",
  "expected_alt": ["レストランで寿司を食べます。"],
  "grammar_focus": "～で (tempat aktivitas)",
  "hint_id": "Pakai partikel で untuk lokasi aktivitas dan を sebelum kata kerja.",
  "confidence": "high"
}

Sekarang buat untuk: {WORD}
```

#### 3.3.2 Kanji reading drill (recognition)

```
Buat soal pilihan ganda reading kanji.

Kanji target: 学校
Reading benar: がっこう
Level: N5

Output JSON:
{
  "question": "Bagaimana cara baca 学校?",
  "options": ["がっこう", "がくこう", "かくこう", "がこう"],
  "correct_index": 0,
  "explanation_id": "学 (gaku, manabu) + 校 (kou). Saat digabung jadi がっこう dengan small tsu karena rendaku rhythm.",
  "confidence": "high"
}
```

Tip: untuk options yang plausible-but-wrong, prompt secara eksplisit: "Buat 3 distractor yang menjebak (mis. salah rendaku, salah on/kun-yomi)". Ini mengurangi opsi yang terlalu mudah dibedakan.

#### 3.3.3 Grammar fill-in

```
Pola grammar: ～てもいいですか (meminta izin) — N5

Buat 1 cloze sentence di mana user mengisi conjugation kata kerja.

Output JSON:
{
  "sentence_template": "ここで写真[しゃしん]を___もいいですか。",
  "verb_dictionary": "撮[と]る",
  "expected_answer": "撮[と]って",
  "translation_id": "Bolehkah saya mengambil foto di sini?",
  "explanation_id": "Bentuk te-form dari 撮る adalah 撮って (kelompok 1, akhiran る → って).",
  "confidence": "high"
}
```

#### 3.3.4 Sentence comprehension (reading)

```
Generate 1 short passage N3 + 2 pertanyaan komprehensi.

Topik: rutinitas pagi mahasiswa
Panjang: 3-4 kalimat
Vocab target (harus muncul): 通[かよ]う, 朝食[ちょうしょく]

Output JSON: {
  "passage": "...",
  "vocab_used": [{ "word": "...", "reading": "...", "meaning_id": "..." }],
  "questions": [
    {
      "q": "...",
      "options": ["A","B","C","D"],
      "correct_index": 0,
      "rationale_id": "..."
    }
  ],
  "confidence": "high"
}
```

#### 3.3.5 Grading user response

Untuk grading, prompt singkat + temperatur rendah. Output schema simpler:

```
Tugas: Grade jawaban user untuk soal terjemahan.

Soal: "Saya makan sushi di restoran."
Jawaban benar: "レストランで寿司を食べます。"
Jawaban user: "{USER_INPUT}"

Output JSON:
{
  "verdict": "correct|partial|incorrect",
  "fsrs_rating": 1|2|3|4,  // 1=Again, 2=Hard, 3=Good, 4=Easy
  "errors": [
    {
      "type": "particle|verb_form|word_order|kanji|spelling|other",
      "user_wrote": "...",
      "should_be": "...",
      "explanation_id": "..."
    }
  ],
  "encouragement_id": "1 kalimat singkat motivasi"
}
```

> Penting: `fsrs_rating` mapping ke enum Rating Kotlin (Again=1, Hard=2, Good=3, Easy=4). Verdict "partial" → Hard. Verdict "correct" cepat → Easy, biasa → Good.

### 3.4 Few-shot examples

Best practice: 2-5 contoh in-prompt. Untuk Gemma 4 E2B yang lebih kecil, **3 contoh** adalah sweet spot — cukup memperkuat pattern, tidak membuang konteks untuk drill panjang.

Strategi:
- Include 1 positive example (correct JSON).
- Include 1 negative example yang ditandai `// BAD` agar model belajar apa yang harus dihindari (misal: romaji bocor, output di luar JSON).
- Include 1 contoh dengan `confidence: "low"` agar model tahu kapan harus flag.

### 3.5 Temperature settings

| Task | Temperature | Top-p | Reasoning |
|------|-------------|-------|-----------|
| Grading user input | 0.0-0.2 | 0.9 | Deterministik, konsisten |
| Quiz generation (multiple choice) | 0.5-0.7 | 0.95 | Variasi distractor |
| Example sentence | 0.7-0.9 | 0.95 | Diversitas konteks |
| Dialogue practice | 0.8-1.0 | 0.95 | Natural turn-taking |
| Mnemonic hint | 0.6-0.8 | 0.9 | Cukup kreatif tapi tidak liar |

LiteRT-LM expose `temperature` dan `top_k`/`top_p` via session config — set per session manager class.

### 3.6 Output JSON safety

LiteRT-LM **mendukung constrained decoding** via `SetConstraintProviderConfig` (llguidance-based JSON schema enforcement) sejak v0.10.x. Ini adalah game-changer untuk reliability.

**Strategi defensif berlapis:**

1. **Lapisan 1 — Constrained decoding** (kalau version LiteRT-LM mendukung): pass JSON schema saat session init.
2. **Lapisan 2 — Prompt structure**: schema in-prompt + "Tidak ada teks di luar JSON" rule.
3. **Lapisan 3 — Post-process parser**:
   ```kotlin
   fun safeParseJson(raw: String): JsonElement? {
       val cleaned = raw
           .substringAfter("```json", raw).substringBefore("```", raw)
           .let { it.substring(it.indexOf("{"), it.lastIndexOf("}") + 1) }
       return runCatching { Json.parseToJsonElement(cleaned) }.getOrNull()
   }
   ```
4. **Lapisan 4 — Fallback**: kalau parse gagal, retry 1× dengan temperature lebih rendah; kalau masih gagal, fallback ke template-based question dari dictionary lookup.

---

## 4. Mobile Inference Edge Case (LiteRT-LM)

### 4.1 Latency target

Berdasarkan publikasi Google AI Edge dan field reports (Apr-Mei 2026):

| Backend | Prefill | Decode | Catatan |
|---------|---------|--------|---------|
| Snapdragon 8 Elite NPU (Hexagon, QNN delegate, Gemma 4 E2B) | 3700 tok/s | 31 tok/s | Hanya 8 Elite atau Dragonwing IQ8 reference; legacy SoC tidak ada binary pre-compiled |
| Snapdragon 8 Gen 3 GPU (OpenCL, Adreno) | ~800-1200 tok/s | 25-40 tok/s | Approximation, varies device |
| CPU (XNNPACK, 4 thread) | 50-150 tok/s | 4-8 tok/s | Fallback |

**Target Tomo Sensei untuk drill generation ~500 token output:**
- 8 Elite NPU: ~16 detik decode kalau full 500 tok. **Stream output** agar UX <2 detik time-to-first-token (TTFT).
- 8 Gen 3 GPU: ~12-20 detik. Stream wajib.
- CPU mid-range: 60-120 detik. **Avoid generative tasks**; gunakan template-based + Gemma 3 270M untuk normalization saja.

Untuk grading (output ~50-100 tok), latency lebih jinak: 8 Elite ~2-3 detik, GPU mid ~3-5 detik, acceptable tanpa streaming.

### 4.2 Context window

Drill prompt typical:
- Sistem prompt: ~250 token
- Few-shot examples (3×): ~600 token
- Task-specific instruction: ~150 token
- **Total input: ~1000 token**
- Output: 50-500 token tergantung task

Gemma 4 mendukung 32k context, jauh dari batas. Tetapi **prefill 1000 token di CPU ~10 detik** — hindari atau cache prefix via session cloning (LiteRT-LM mendukung KV cache reuse).

**Optimasi: Persistent session per modul.** Sistem prompt + few-shot dipre-kompute satu kali per app launch, lalu setiap drill hanya kirim delta task. Hemat 80%+ prefill latency.

### 4.3 Japanese tokenization

Gemma 3 dan Gemma 4 menggunakan tokenizer baru (262k vocab) yang **secara eksplisit di-tuning untuk CJK** (Chinese-Japanese-Korean). Improvement vs Gemma 2:
- Hiragana/katakana: 1 token per kana mayoritas.
- Kanji umum (jouyou): 1 token.
- Kanji rare (jinmeiyou, hyougai): 1-2 token.
- Furigana annotation `漢字[かんじ]`: terpisah jadi 3-4 token (kanji, [, kana sequence, ]).

**Implikasi praktis:** Output Jepang 50 karakter ≈ 60-80 token. Decoding 80 tok di NPU 31 tok/s = ~2.6 detik. Acceptable.

### 4.4 Streaming UX

Strategi UI untuk Compose:

```kotlin
val sessionFlow = remember { liteRtSession.generateStream(prompt) }
val accumulated by sessionFlow
    .scan("") { acc, token -> acc + token }
    .collectAsState(initial = "")

Text(text = accumulated)  // Streaming render
```

Tunggu sampai JSON closing brace `}` muncul untuk parsing final, tapi UI bisa show partial content (cukup dengan placeholder "Tomo sedang menyiapkan soal...").

Untuk grading: **jangan stream**. Tampilkan loader → render setelah JSON complete (~100 tok = 3 detik max).

### 4.5 Battery & thermal

Field report Apr 2026 di Mali-G715 (Tensor G3) menunjukkan sustained inference 5+ menit menyebabkan thermal throttling: decode tok/s drop 40-60%. Untuk Tomo Sensei:

- **Hard limit:** 20 drill per session (bukan jam wall-clock) sebelum cooldown 60 detik.
- **Smart routing:** kalau battery <20% atau temperatur tinggi, fallback ke template-based drill (FSRS tetap jalan, content static dari dictionary).
- **Background mode:** WorkManager FSRS optimizer **tidak** boleh trigger inference; jadwalkan deep maintenance saat charging.

---

## 5. Japanese Learning-Specific Data

### 5.1 Datasets (rekomendasi MIT/CC-BY compatible)

| Dataset | Konten | Lisensi | Use di Tomo Sensei |
|---------|--------|---------|---------------------|
| JMdict (EDRDG) | ~200k entries Japanese-Multilingual dict | EDRDG license (free, attribution) | Vocab database, grounding LLM |
| KANJIDIC2 (EDRDG) | 13,108 kanji + reading, JLPT level, stroke count | EDRDG license | Kanji metadata |
| KanjiVG | SVG stroke order untuk ~6500 kanji | CC-BY-SA 3.0 | Stroke order display |
| Bluskyo/JLPT_Vocabulary | N5-N1 vocab JSON+CSV | MIT | Quick start vocab list |
| AnchorI/jlpt-kanji-dictionary | JLPT-leveled kanji+vocab | MIT | Alternative dengan translasi |
| OJAD pitch accent | 9000+ noun + 3500 verb/adj pitch pattern | Free research use | Pitch accent display (advanced N3+ feature) |
| Aozora Bunko corpus | Public domain literature | Public domain | Sentence example mining |
| japanese-subtitles-word-kanji-frequency-lists | Word freq dari drama/anime | Varies | Frequency-based card prioritization |

**Strategi data layer Tomo Sensei:**
1. Bundle JMdict subset + KANJIDIC2 + KanjiVG di APK (compressed ~30 MB) sebagai SQLite read-only DB.
2. JLPT level mapping dari `jlpt-kanji-dictionary` untuk auto-tag content.
3. OJAD pitch hanya untuk tier N3+ atau opt-in (data download terpisah agar APK ringan).

### 5.2 Mnemonic & radical (WaniKani-style)

WaniKani pakai pendekatan **radical-first decomposition**:
1. Ajarkan radical (komponen visual, bukan harus Kangxi 214 tradisional).
2. Komposisi kanji = cerita radical.
3. Vocab = cerita kanji + reading.

Untuk Tomo Sensei, hybrid pragmatis:
- Dataset radical: kanjialive `kanji-data-media` atau extract dari KANJIDIC2 `nanori` + custom radical mapping.
- LLM-generated mnemonic: prompt Gemma 4 dengan radical list → "Buat cerita 2 kalimat menggunakan radical X, Y, Z untuk kanji Z." dengan confidence flag.
- Cache mnemonic per-user agar konsisten across review.

### 5.3 Pitch accent (opsional, N3+)

Tokyo standard 4 pattern:
- 平板 (heiban) — flat, naik 1 mora lalu stay (e.g. 学生 がくせい LHHH)
- 頭高 (atamadaka) — drop setelah mora 1 (e.g. 雨 あめ HL)
- 中高 (nakadaka) — peak di tengah (e.g. お菓子 おかし LHL)
- 尾高 (odaka) — drop di particle (e.g. 弟 おとうと LHHH-L pada partikel)

Tomo Sensei: tampilkan pitch sebagai overlay garis di atas kana (mengikuti convention OJAD). Buat opt-in karena risiko **over-correction**: pitch accent variasi regional, fokus untuk advanced learner saja.

### 5.4 Quiz format effectiveness

Berdasarkan riset cognitive psychology + observasi WaniKani/Bunpro:

| Format | Recall depth | Difficulty | Recommended use |
|--------|--------------|-----------|-----------------|
| Multiple choice | Recognition | Easy | First learning, kanji reading drill |
| Cloze fill-in | Production | Medium | Grammar, particle |
| Typing answer | Free recall | Hard | Vocab consolidation (Bunpro style) |
| Speak/listen | Audio recall | Hard | Listening drill (N5-N4 awal) |
| Translation | Composition | Very hard | N3+ sentence drill |

Tomo Sensei recommendation: **graduated difficulty per card lifecycle**.
- New card (state=NEW, 1st-3rd review): multiple choice.
- LEARNING (3rd-10th review): cloze fill-in.
- REVIEW mature (>10 reviews): typing answer.

FSRS difficulty parameter (1-10) bisa dijadikan trigger format switch (D<4: MC; 4-7: cloze; >7: typing).

---

## 6. Reference App Benchmark

### 6.1 Apps comparison (Mei 2026)

| App | Algorithm | Konten | Pricing | Strength | Weakness |
|-----|-----------|--------|---------|----------|----------|
| Anki + FSRS plugin | FSRS-6 native | User decks (Jpopular: Core 6k, Kaishi 1.5k) | Free desktop, $25 iOS | Algorithm SOTA, fleksibel | UX kurang ramah pemula |
| AnkiDroid | FSRS-5/6 via Anki backend | Sama Anki | Free | Open-source, Anki ecosystem | Setting kompleks |
| Bunpro | Internal SRS (claim modified FSRS in 2024) | Grammar N5-N1 + vocab | $5/mo, $150 lifetime | Best grammar drill | Subscription |
| WaniKani | Custom SRS (8 stages) | Kanji + vocab radical-based | $9/mo | Mnemonic system | Tidak ajar grammar/conversation |
| Renshuu | Custom SRS adaptive | All-in-one vocab+kanji+grammar+listening | Mostly free | All-in-one murah | Algorithm tidak open |
| JPDB | FSRS-based | Vocab via media (anime/novel) | Free | Frequency-based prioritization | Less UI polish |

### 6.2 Retention rate realistis

- Anki+FSRS default target: 90% retention. Actual measured median: 87-92%.
- WaniKani Apprentice→Guru pass rate: ~80% (cards yang lulus berarti 4-7 hari interval consolidation).
- Bunpro fill-in: lebih ketat, kerap user feel 70-75% retention.

**Tomo Sensei target awal:**
- v1 default: 90% requested retention.
- Setelah ≥1000 reviews per user: optimizer + CMRR (Compute Minimum Recommended Retention) untuk personalisasi.
- Bahkan untuk casual user, **75-85% acceptable** kalau review per hari <15 menit; jangan paksa 95% retention untuk learner casual (workload 4× lipat untuk gain 5pp).

---

## 7. Hybrid FSRS + LLM Architecture

### 7.1 Pemisahan tanggung jawab

```
┌──────────────────────────────────────────────────────────┐
│              Tomo Sensei Session Engine                  │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  FSRS Scheduler ────┐                                   │
│  (kapan show)       │                                   │
│                     ▼                                   │
│              ┌──────────────┐                           │
│              │ Card Selector │ ── pull due cards        │
│              └──────┬───────┘                           │
│                     ▼                                   │
│              ┌──────────────┐                           │
│              │ Content Hub  │ ◄── JMdict / KANJIDIC2    │
│              │ (grounding)  │     KanjiVG / JLPT lists  │
│              └──────┬───────┘                           │
│                     ▼                                   │
│              ┌──────────────┐                           │
│              │ Gemma 4 E2B  │ ── enrich: variation,     │
│              │ (content gen)│    context, mnemonic      │
│              └──────┬───────┘                           │
│                     ▼                                   │
│              ┌──────────────┐                           │
│              │ Drill UI     │                           │
│              └──────┬───────┘                           │
│                     ▼                                   │
│              user input ──► Gemma 4 (grading)           │
│                                  │                       │
│                                  ▼                       │
│                          FSRS Rating ──► next() ──► DB  │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

Prinsip:
- **FSRS decides "when"** dan **"what card."**
- **Content Hub (dictionary) provides ground truth** untuk reading, meaning, stroke order.
- **LLM provides "how"** — variasi soal, mnemonic, contoh kalimat, grading feedback.
- LLM **tidak pernah** jadi sumber tunggal untuk meaning/reading kata. Selalu cross-check ke JMdict/KANJIDIC2 sebelum render.

### 7.2 Grounding pipeline

```kotlin
suspend fun generateVocabDrill(card: CardEntity): Drill {
    val word = jmdict.lookup(card.contentId)
        ?: return Drill.fallbackError("kata tidak ditemukan")

    // Ground truth dari kamus
    val groundedContext = """
        Kata: ${word.kanji}
        Reading: ${word.reading}
        Meaning ID: ${word.meaningId}
        JLPT: ${word.jlptLevel}
    """.trimIndent()

    val response = gemma.generate(
        systemPrompt = TomoSenseiSystemPrompt,
        userPrompt = """
            Buat 1 contoh kalimat menggunakan kata ini.
            $groundedContext
            Output JSON schema: { ... }
        """.trimIndent(),
        temperature = 0.7,
        maxTokens = 300
    )

    val parsed = safeParseJson(response) ?: return Drill.template(word)

    // Validate: kata target HARUS muncul di output
    if (!parsed.expectedJp.contains(word.kanji)) {
        return Drill.template(word)  // LLM halusinasi, fallback
    }

    return Drill.fromLLM(parsed, word)
}
```

Validation layer di akhir adalah kunci. Kalau LLM menulis reading berbeda dari JMdict atau makna tidak konsisten, fallback ke template static.

### 7.3 Cache strategy

LLM call mahal (battery + latency). Cache aggressive:
- Per-card LLM output disimpan di tabel `cached_drills` (TTL 30 hari).
- Variasi: regen drill baru hanya kalau user sudah lihat soal sebelumnya >2x.
- Mnemonic per-user-per-kanji: cache permanen, hanya regen kalau user request.

---

## 8. Concrete Recommendations untuk Tomo Sensei

### 8.1 Tech stack final

```
┌─ Memory layer ──────────────────────────────────────┐
│  • FSRS-Kotlin (io.github.open-spaced-repetition)   │
│  • Default weights FSRS-6 (21 params)               │
│  • Target retention 0.90                            │
│  • Optimizer deferred ke v1.x (≥1000 reviews)       │
│  • Storage: Room SQLite                             │
└──────────────────────────────────────────────────────┘
┌─ Content layer ─────────────────────────────────────┐
│  • JMdict subset (vocab) — bundled SQLite           │
│  • KANJIDIC2 (kanji metadata) — bundled             │
│  • KanjiVG SVG (stroke order) — bundled, lazy load  │
│  • JLPT vocab tagging dari Bluskyo/JLPT_Vocabulary  │
│  • OJAD pitch — optional download untuk tier N3+    │
└──────────────────────────────────────────────────────┘
┌─ AI layer ──────────────────────────────────────────┐
│  • Gemma 4 E2B (int4) via LiteRT-LM                 │
│    - drill generation (temp 0.7)                    │
│    - grading (temp 0.1)                             │
│  • Gemma 3 270M (fallback ultra-low-end)            │
│  • Persistent session (KV cache reuse)              │
│  • Constrained JSON decoding (LiteRT-LM >=0.10)     │
│  • Streaming UI untuk gen >100 tok                  │
└──────────────────────────────────────────────────────┘
┌─ Background ────────────────────────────────────────┐
│  • WorkManager periodic 7 hari → FSRS refit         │
│    (constraint: charging + idle)                    │
│  • Daily review reminder via NotificationCompat     │
│  • Stats aggregator: retention rate harian          │
└──────────────────────────────────────────────────────┘
```

### 8.2 Module breakdown 5 jenis drill

| Modul | Format awal (new card) | Format mature | Konten source | LLM use |
|-------|------------------------|---------------|---------------|---------|
| Kana | MC (gambar → kana) | Typing | Hardcoded 46+46 chart | Tidak (deterministic) |
| Kanji reading | MC (kanji → reading) | Typing reading | KANJIDIC2 + JLPT | LLM: explanation rendaku |
| Vocab translation | MC (ID → JP) | Typing JP | JMdict + Bluskyo list | LLM: example sentence, hint |
| Grammar fill-in | Cloze MC | Cloze typing | Bunpro-style pattern list (custom) | LLM: contoh kalimat variant |
| Sentence comprehension | MC passage | Free recall | Aozora corpus + LLM generation | LLM: passage gen + Q gen |

### 8.3 Evaluation metric (KPI v1)

| Metric | Target v1 | How to measure |
|--------|-----------|----------------|
| Monthly review retention | 85%+ | Pass rate (Hard/Good/Easy) di review log |
| Daily active reviews | 30-60 cards/user | aggregat ReviewLog |
| Cards mastered/day | 5-10 (kasual) | transitions state REVIEW dengan stability >7 hari |
| Drill gen latency p50 (SoC flagship) | <2s TTFT | telemetri LLM session |
| Drill gen latency p95 | <8s | idem |
| Grading accuracy vs gold standard | 90%+ | manual eval 200 samples |
| Crash-free rate | 99.5%+ | Crashlytics |

### 8.4 Phasing roadmap

**v1.0 (MVP, 4-6 minggu):**
- Modul kana (deterministic, no LLM)
- FSRS-6 default weights
- Room DB schema lengkap
- Basic UI Compose
- Tidak ada LLM dulu — template-based drill

**v1.1 (LLM enablement):**
- Integrasi LiteRT-LM + Gemma 3 270M untuk grading romaji normalization
- Modul kanji reading (MC, LLM optional explanation)

**v1.2 (full content gen):**
- Gemma 4 E2B drill generation
- Modul vocab translation
- Modul grammar fill-in

**v1.3 (advanced):**
- Sentence comprehension
- Pitch accent overlay (opt-in)
- FSRS optimizer (WorkManager)

**v2.0:**
- Speaking drill (audio in/out)
- Dialogue practice multi-turn
- Personalized mnemonic library

---

## 9. Risk & Open Question

### 9.1 Risk teknis

| Risk | Mitigasi |
|------|----------|
| LiteRT-LM constrained decoding belum stabil di Android release | Layer parse defensif + retry; fallback template |
| Gemma 4 E2B size 2.6 GB terlalu besar untuk entry-level device | Tier device: <6GB RAM pakai Gemma 3 270M + template, >=8GB pakai E2B |
| FSRS-Kotlin maven artifact lag dari fsrs-rs (kalau ada update FSRS-7) | Vendoring fork internal kalau perlu; pin version, manual upgrade |
| OJAD pitch data licensing untuk redistribusi | Konfirmasi ke EDRDG / U-Tokyo; alternative: pitch annotation hanya saat user opt-in download |
| LLM halusinasi reading kanji rare | Always cross-check JMdict; reject output kalau reading tidak match |
| Battery drain inference berulang | Cache LLM output, batch generate saat charging, hard limit per session |

### 9.2 Open question untuk follow-up research

1. **Optimizer Kotlin native vs server-side:** apakah port `fsrs-optimizer` ke pure Kotlin/Multiplatform feasible? Atau cukup invoke via Python backend opsional?
2. **FSRS-7 timeline:** kalau rilis pertengahan 2026, apakah upgrade path mudah (parameter migrasi otomatis)? Pantau release notes.
3. **Custom desired retention per modul:** apakah kanji butuh retention lebih tinggi (95%) dibanding vocab (85%)? Eksperimen A/B post-launch.
4. **Personalisasi mnemonic dengan user history:** lebih efektif fine-tune local LoRA per user atau cukup few-shot dari past correct answers?
5. **Audio modality:** Gemma 4 E2B mendukung audio in/out. Roadmap speaking drill kapan masuk akal (latency listening + transcription)?

---

## 10. Referensi (akses 2026-05-13)

**FSRS core:**
- https://github.com/open-spaced-repetition/fsrs4anki — canonical repo + wiki
- https://github.com/open-spaced-repetition/free-spaced-repetition-scheduler — DSR model paper basis
- https://github.com/open-spaced-repetition/fsrs-rs — Rust reference impl
- https://github.com/open-spaced-repetition/py-fsrs — Python reference
- https://github.com/open-spaced-repetition/FSRS-Kotlin — Kotlin port (rekomendasi Tomo Sensei)
- https://github.com/open-spaced-repetition/android-fsrs — Android wrapper
- https://github.com/open-spaced-repetition/fsrs-optimizer — optimizer Python
- https://github.com/open-spaced-repetition/srs-benchmark — cross-algorithm benchmark
- https://github.com/open-spaced-repetition/awesome-fsrs — daftar implementasi

**FSRS-6 reference (formula & parameter):**
- https://expertium.github.io/Algorithm.html — technical explanation
- https://expertium.github.io/Benchmark.html — benchmark vs SM-17
- https://chrislongros.com/2025/04/18/fsrs6-on-its-way/ — FSRS-6 release writeup
- https://github.com/open-spaced-repetition/fsrs4anki/wiki/The-optimal-retention — retention vs workload
- https://docs.ankiweb.net/deck-options.html — Anki deck options
- https://forums.ankiweb.net/t/fsrs-research-suggests-minimum-can-be-reduced-to-16 — minimum reviews

**Gemma 4 / LiteRT-LM:**
- https://blog.google/innovation-and-ai/technology/developers-tools/gemma-4/ — Gemma 4 launch
- https://ai.google.dev/edge/litert-lm/overview — LiteRT-LM docs
- https://ai.google.dev/edge/litert-lm/android — Android guide
- https://github.com/google-ai-edge/LiteRT-LM — repo + issues
- https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm — model weights
- https://medium.com/google-developer-experts/bringing-multimodal-gemma-4-e2b-to-the-edge-... — performance writeup
- https://developers.googleblog.com/own-your-ai-fine-tune-gemma-3-270m-for-on-device/ — Gemma 3 270M finetune

**Japanese learning data:**
- https://www.edrdg.org/jmdict/edict.html — JMdict project
- https://www.edrdg.org/wiki/index.php/KANJIDIC_Project — KANJIDIC2
- https://kanjivg.tagaini.net/ — KanjiVG stroke order
- https://github.com/Bluskyo/JLPT_Vocabulary — JLPT vocab JSON/CSV
- https://github.com/AnchorI/jlpt-kanji-dictionary — kanji + JLPT
- https://github.com/davidluzgouveia/kanji-data — kanji + WaniKani info
- https://www.gavo.t.u-tokyo.ac.jp/ojad/eng/pages/home — OJAD pitch accent
- https://github.com/taishi-i/awesome-japanese-nlp-resources — curated NLP list
- https://www.tofugu.com/japanese/kanji-radicals-mnemonic-method/ — WaniKani method overview

**Reference apps:**
- https://www.wanikani.com/ — WaniKani
- https://bunpro.jp/ — Bunpro
- https://jpdb.io/ — JPDB
- https://www.renshuu.org/ — Renshuu

**Prompt engineering & constrained decoding:**
- https://www.promptingguide.ai/techniques/fewshot — few-shot guide
- https://mbrenndoerfer.com/writing/constrained-decoding-structured-llm-output — constrained decoding
- https://arxiv.org/html/2501.10868v1 — structured output benchmark

---

**Catatan kepercayaan:**
- High confidence: FSRS-5/6 parameter values, dataset URL, LiteRT-LM tooling.
- Medium confidence: Gemma 4 latency angka spesifik (varies per device build); FSRS optimizer minimum review (debate aktif).
- Low confidence: FSRS-7 timeline (klaim forum, belum rilis); pitch accent licensing redistribution detail (perlu konfirmasi langsung ke EDRDG/U-Tokyo).

**Status dokumen:** Draft research v1, siap untuk review tech lead Tomo Sensei. Update direncanakan setelah Gemma 4 stable di production LiteRT-LM v0.11+ dan setelah benchmark internal device flagship vs mid-range.
