# Research 05 — Skill Library / Cached Macro per-App untuk Mobile-Agent Pattern

> **Konteks**: Riset untuk ChibiClaw v4 Phase 3 — adopsi pattern AppAgentX/Mobile-Agent v3.5 untuk runtime skill cache per-app, mengurangi vision-grounding berulang.
> **Penulis**: research agent (Claude Opus 4.7)
> **Tanggal dibuat**: 2026-05-13
> **Tanggal akses sumber**: 2026-05-13
> **Status**: Draft 1 — referensi desain Phase 3, belum lock-in implementasi.

---

## Executive Summary

Pattern "Skill Library" (skill = ringkasan deterministik dari step UI yang berhasil di-replay) sudah menjadi salah satu axis differentiasi paling tegas di research mobile-agent 2025-2026. Tiga insight utama:

1. **Tidak ada satu skema kanonik yang menang.** AppAgentX (Westlake) memakai graph DB Neo4j + Pinecone vektor visual, Mobile-Agent-v3.5 (Alibaba/X-PLUG) memakai `InfoPool` string-based + memory built-in di model GUI-Owl-1.5, AppAgent v2 (Tencent) memakai per-element JSON doc berkunci `resource_id`, Voyager (MineDojo) memakai code+description+vectordb. MAS-Bench (vivo+ZJU, Sep 2025) menyediakan benchmark formal untuk hybrid shortcut yang menggabungkan API + deep-link + RPA-script.
2. **Locator hybrid lebih realistis untuk Android 2026.** Pure visual (ResNet-50 embedding ala AppAgentX) bagus untuk app blacklist anti-a11y, tapi terlalu mahal untuk app biasa. AppAgent v2 dan AutoDroid sudah pakai dual-key: `resource_id` (a11y) sebagai primary key + visual anchor (OCR + bbox) sebagai fallback. Ini sejalan dengan eskalasi 4-tier ChibiClaw v3 (Intent → ContentProvider → A11y → Shizuku).
3. **Replay fidelity masih lemah untuk app yang sering update.** Paper "Get Experience from Practice" (Mei 2025, arxiv 2505.17716) eksplisit menulis "100% reliable replay remains elusive" — tidak ada mekanisme auto-repair yang sudah matang. AndroTMem (Mar 2026, arxiv 2603.18429) menawarkan "Anchored State Memory" (5-30% TCR gain) sebagai mitigasi, tapi belum diuji untuk app komersial Indonesia (TikTok ID, GoPay, dst).

**Rekomendasi cepat untuk ChibiClaw v4 Phase 3** ada di Bab 9. TL;DR: schema MVP per Voyager + AppAgent v2, storage Room dengan tabel `skill` + `skill_step` + `skill_index_fts`, retrieval embedding via Gemma 4 embedding head (atau fallback BM25 lokal), eviction berbasis success-rate + staleness, mining offline dari ExecutorLog dengan kriteria k-step repeat ≥ 3 dalam 14 hari.

---

## 1. Schema Design Skill Library — State of the Art 2025-2026

### 1.1 AppAgentX (Westlake-AGI-Lab, Mar 2025) — Graph + Vector Hybrid

Paper: [arxiv 2503.02268v3](https://arxiv.org/abs/2503.02268). Repo: [github.com/Westlake-AGI-Lab/AppAgentX](https://github.com/Westlake-AGI-Lab/AppAgentX). License Apache 2.0.

Arsitektur stored sebagai **graph chain** di Neo4j dengan **embedding visual** di Pinecone. Tiga tipe node:

| Node | Fields (dikonfirmasi dari `chain_evolve.py`) | Catatan |
|------|----------------------------------------------|---------|
| **Page** | description (text), element_list (JSON OmniParser: pos, OCR), screenshot_id, timestamp | Description awalnya kosong, di-summarize LLM saat triple muncul |
| **Element** | description (semantic purpose), visual_embedding (ResNet-50, dim tidak disebut di code yang ter-fetch), bbox, ocr_text, interaction_type | Embedding visual jadi kunci match cross-screenshot |
| **Shortcut (ActionNode)** | `action_id`, `name`, `description`, `preconditions: List[str]`, `element_sequence: List[Dict]`, `template_pattern: Dict`, `is_high_level: True` | Pydantic class `ActionNodeGeneration` di repo |

Relasi: `HAS_ELEMENT` (Page→Element), `LEADS_TO` (Element→Page transition), `COMPOSED_OF` (Shortcut→Element ordered).

Penilaian apakah suatu chain layak jadi shortcut dilakukan LLM dengan output `ChainEvaluationResult` (Pydantic): `is_templateable: bool`, `confidence_score: float (0-1)`, `reason: str`, `suggested_name: str`. **Konfirmasi**: kode aktual `chain_evolve.py` mengeskpos lima field ini sebagai schema (terkonfirmasi langsung dari source).

**Yang TIDAK ditemukan secara eksplisit di paper / source code yang ter-akses publik:**
- Embedding dimension untuk ResNet-50 (paper menyebut "Microsoft variant ResNet-50" — asumsi standar 2048-D, perlu cek source `backend/image_feature_service`).
- Eviction policy (Section 5 paper tidak detail).
- TTL atau quota per app.

### 1.2 Mobile-Agent-v3 / v3.5 + GUI-Owl-1.5 (Alibaba X-PLUG, Aug-Nov 2025)

Paper: [arxiv 2508.15144](https://arxiv.org/pdf/2508.15144) (v3), v3.5 di arxiv 2602.16855. Repo: [github.com/X-PLUG/MobileAgent](https://github.com/X-PLUG/MobileAgent).

**Konfirmasi dari README + DeepWiki**: v3.5 tidak memiliki skill library eksplisit seperti AppAgentX. Sebagai gantinya:

- **`InfoPool`** = central state manager bertipe string-based `important_notes` yang diisi role **Notetaker** dan dibaca oleh Manager + Executor. Berfungsi sebagai working memory cross-page (mis. "harga di page A = 50K, bandingkan dengan page B").
- **"Long-horizon memory built-in"**: klaim README v3.5 menyebut memory native di GUI-Owl-1.5 tanpa orkestrasi workflow eksternal, leading di MemGUI-Bench. Schema internal tidak di-expose publik di README — **tidak boleh diasumsikan ada skill library terstruktur** ala Voyager.
- Roles: planner, executor, verifier, notetaker. Notetaker output format **tidak terdokumentasi spesifik** di README publik (kode `notetaker.py` di subdirektori `mobile_v3/` tidak ter-fetch publik via WebFetch saat akses 2026-05-13).

**Inference (bukan fakta)**: pattern v3.5 lebih ke "in-context memory yang di-summarize tiap step", bukan persistent skill JSON. Untuk ChibiClaw, ini berarti referensi v3.5 lebih relevan untuk *working memory per-task*, bukan *skill library per-app*.

### 1.3 AppAgent v2 (Tencent QQGY Lab, Aug 2024) — Per-Element JSON

Paper: [arxiv 2408.11824v3](https://arxiv.org/html/2408.11824v3). Repo asal: [TencentQQGYLab/AppAgent](https://github.com/TencentQQGYLab/AppAgent).

Dari source code `scripts/self_explorer.py` (terkonfirmasi langsung), schema per UI element:

```
file: <app_dir>/auto_docs/<resource_id>.txt
content (Python dict serialized as string):
{
  "tap": "...deskripsi efek ketika di-tap...",
  "text": "...deskripsi efek ketika di-input text...",
  "v_swipe": "...",
  "h_swipe": "...",
  "long_press": "..."
}
```

**Konfirmasi:**
- File extension `.txt`, isi `str(dict)` (bukan JSON proper — eccentricity Tencent).
- Key element = `resource_id` Android (a11y attribute).
- 5 action fields fixed: tap, text input, vertical swipe, horizontal swipe, long press.

AppAgent v2 paper menambah: Android ID + numerical label OCR-based + visual features (color/shape) + coordinates. Retrieval = **self-query retriever** dengan embedding di vector store, kunci pencarian `resource_id` ATAU OCR-derived text. Ini adalah arsitektur **per-element knowledge base**, bukan per-task macro. Cocok untuk grounding deterministik, tidak cocok untuk "replay 5-step send-message-to-Andi".

### 1.4 UI-TARS / UI-TARS-2 (ByteDance, Jan 2025 / 2025) — End-to-End, Memory Native

Repo: [github.com/bytedance/UI-TARS](https://github.com/bytedance/UI-TARS).

UI-TARS arsitektur 4-modul: perception, action, **memory**, reasoning. Memory module dideskripsikan paper sebagai "store explicit knowledge and historical experience yang dirujuk saat decision making", tapi **schema penyimpanan eksternal tidak terekspos di repo publik** — memory di-embed sebagai bagian dari training data + KV cache di model. UI-TARS-2 menambah Game/Code/Tool. **Konfirmasi**: tidak ada skill library yang user-editable di sini. Tidak relevan sebagai referensi schema untuk ChibiClaw, tapi sangat relevan sebagai **executor model** (lihat Research 01).

### 1.5 Voyager (MineDojo NVIDIA, May 2023) — Code as Skill

Paper: arxiv 2305.16291. Repo: [github.com/MineDojo/Voyager](https://github.com/MineDojo/Voyager).

Schema (terkonfirmasi dari `skill_library/` repo structure):

```
skill_library/
  trial1/
    skill/
      code/
        catchThreeFishWithCheck.js     # executable JS code
        collectBamboo.js
      description/
        catchThreeFishWithCheck.txt    # natural language one-liner
        collectBamboo.txt
      skills.json                       # index file
    vectordb/                            # embedding store
```

**Sifat penting**:
- Skill = **executable code**, bukan list of UI actions. Ini fundamental difference dari mobile-agent pattern. Replay deterministik karena kode bukan koordinat.
- Description di-embed (paper menyebut OpenAI text-embedding-ada-002 era; modern equivalent = OpenAI 3-small, Voyage 3-lite, atau bge-large-en-v1.5).
- Retrieval = top-5 cosine similarity, di-inject ke prompt sebagai few-shot example bukan langsung di-execute.

### 1.6 MAS-Bench (vivo + ZJU + Huzhou, Sep 2025) — Shortcut Hybrid Formalisasi

Paper: [arxiv 2509.06477](https://arxiv.org/abs/2509.06477). Repo: [github.com/Pengxiang-zhao/MAS-Bench](https://github.com/Pengxiang-zhao/MAS-Bench).

Membenchmark 88 shortcuts pre-defined dalam 3 kategori (terkonfirmasi paper):

| Kategori | Jumlah | Format |
|----------|--------|--------|
| **API** | 11 | Intent action + data URI + extras, dijalankan via `adb shell am start` |
| **Deep Link** | 70 | URL scheme + path dari AndroidManifest |
| **RPA Script** | 7 | Workflow gabungan, identifikasi element by **UI tree**, bukan koordinat |

**Klaim paper**: hybrid agent (shortcut + GUI fallback) capai 68.3% success rate vs GUI-only baseline, +57% efficiency, dan agent Gemini-2.0-Flash dengan shortcut boost cross-app dari 0% → 23.4%. **Konfirmasi**: paper tidak meng-expose JSON schema formal per-shortcut, hanya menjelaskan komponen. Untuk skema implementasi, harus inspect repo kode (di luar scope research ini).

### 1.7 AndroTMem (Mar 2026, arxiv 2603.18429) — Anchored State Memory

Konsep baru: state-anchor compact dengan causal link, retrieval per-subgoal. **+5% sampai +30.16% TCR vs full-sequence replay**. Schema spesifik anchored state belum di-detail di abstract publik. Sangat menjanjikan untuk long-horizon ChibiClaw (mis. "send message + screenshot reply"), tapi **bukti praktis Android komersial belum ada**.

### 1.8 Anthropic Agent Skills Spec (Dec 2025) — `SKILL.md`

Spec: [agentskills.io/specification](https://agentskills.io/specification). Format: folder + `SKILL.md` (YAML frontmatter `name, description, version` + Markdown body). **Catatan kritis**: spec ini designed untuk **assistant general-purpose** (Claude/Codex/Gemini CLI) di environment dev, **bukan untuk UI replay mobile**. Bisa di-borrow sebagai filesystem format (1 skill = 1 folder dengan SKILL.md + assets), tapi step UI tetap perlu schema sendiri.

### 1.9 Sintesis — Skema MVP yang Defensible

Tidak ada satu schema yang menang absolut. Untuk ChibiClaw v4, kombinasi best-of:

```
Skill {
  // Identity (Anthropic SKILL.md style)
  id: ULID
  name: str (short, imperative — "Send WhatsApp to contact")
  description: str (1-2 sentences, untuk di-embed)
  version: int (semver-lite: bump saat layout app berubah)
  app_package: str (mis. "com.whatsapp")
  app_version_range: str (mis. ">=2.25.10", optional)

  // Trigger / Discovery
  intent_examples: List[str]   // ["kirim wa ke andi", "wa andi halo", ...]
  intent_embedding: float[]    // dimensi tergantung model — see Bab 4
  preconditions: List[Precondition]
    // Precondition = {type: "app_installed"|"screen_unlock"|"network", value}

  // Execution
  steps: List[Step]   // ordered, see schema below
  parameters: List[Param]   // var name + type + default + extract_from
  success_criteria: List[Check]
    // Check = {type: "element_visible"|"text_match"|"screen_hash"|"deeplink_status", value}
  failure_recovery: Recovery
    // {on_step_fail: "retry_3x"|"fallback_vision"|"abort_ask_user", on_drift: "auto_repair_v0"|"abort"}

  // Telemetry
  success_count: int
  failure_count: int
  last_success_at: epoch
  last_layout_hash: str   // untuk drift detection
  source: "user_demo"|"agent_explore"|"shared_community"
  severity: "low"|"medium"|"high"   // OTP/payment = high
  approved_by_user: bool
}

Step {
  index: int
  action: "tap"|"long_press"|"swipe"|"input_text"|"wait"|"deeplink"|"intent"|"back"|"home"
  locator: Locator   // see Bab 2
  wait_condition: WaitCondition
    // {type: "element_visible"|"network_idle"|"animation_done"|"duration_ms", value}
  retry_policy: {max: int, backoff_ms: int}
  parameter_ref: str | null   // mis. "$contact_name" untuk binding
}

Locator {
  primary: "a11y_resource_id" | "a11y_text" | "deeplink" | "ocr_text" | "visual_template"
  primary_value: str
  fallback_chain: List<Locator>   // urutan retry kalau primary miss
  bbox_hint: [x, y, w, h] | null   // relatif resolusi reference
  anchor_relative_to: str | null   // mis. "parent_resource_id"
}
```

Schema ini **gabungan inference dari AppAgent v2 + Voyager + Anthropic Skill spec + best practice locator hybrid**, bukan copy dari satu paper. **Eksplisit disebut sebagai desain ChibiClaw, bukan klaim SOTA terdokumentasi.**

---

## 2. Locator Strategy

### 2.1 Pilihan dan Trade-off

| Strategi | Pro | Con | Cocok untuk |
|----------|-----|-----|-------------|
| `resource_id` (a11y) | Stabil bila app rapi, deterministik | Hilang di app blacklist (TikTok/WA/IG `accessibilityDataSensitive`), null di Compose tanpa testTag | App "tame" (Banking ID, Telkomsel, GOJEK partner) |
| `a11y_text` / content-desc | Robust ke perubahan layout id | Sensitif ke localization (ID vs EN UI) | Backup untuk resource_id |
| Deep link (`Intent.ACTION_VIEW`) | Skip UI completely, fastest, paling stabil | Hanya untuk action yang exposed app (lihat MAS-Bench 70/88 = 80% deep-link) | First-class shortcut |
| OCR + bbox | Cross-app universal, work di anti-a11y | Tergantung OCR accuracy, fragile ke perubahan font | App blacklist |
| Visual template match (OmniCV / pixel diff) | Sangat stabil untuk icon yang sama | Mahal CPU, sensitive ke theme/dark-mode | Icon-driven action |
| ResNet-50 embedding (AppAgentX style) | Semantic visual similarity | Butuh vector DB + backend image service | Heavy production setup |
| LLM attention map (mis. UI-TARS, UI-Ins) | Best quality, semantik | Latency tinggi tanpa NPU | Fallback grounding |

### 2.2 Hybrid Recommendation untuk ChibiClaw

Pattern eskalasi locator (mirror dari pattern executor v3):

```
1. Try deep link / Intent if available  → 80% kasus done sub-100ms
2. Try a11y resource_id (jika service aktif & app non-blacklist)
3. Try a11y text/content-desc (i18n-aware)
4. Try OCR + bbox anchor + visual template (per-skill)
5. Fallback: ask LLM (Gemma 4 lokal) to do grounding from screenshot
```

Setiap step di skill JSON menyimpan `primary + fallback_chain` agar **drift di satu layer tidak hancurkan keseluruhan**.

### 2.3 Drift Detection dan Auto-Repair

Drift = layout berubah, locator miss. Realistis 2026 berdasarkan survey:

- **Anchor strategy** (Exadel, AskUI 2025): mulai dari unique stable element, locator anak relatif ke parent. Implementasi: simpan `anchor_relative_to` di Locator + relative coordinate.
- **OCR-healing**: bila resource_id miss tapi text label yang ekspektasi masih tampak di screenshot, auto-fix locator → `a11y_text`. Klaim AskUI: smart selectors kurangi maintenance 40%, tapi tidak ada paper akademik untuk validasi ini.
- **Layout hash**: simpan `last_layout_hash` (hash dari list resource_id top-level di screen tujuan). Bila beda > threshold, mark skill as "needs review", drop dari retrieval sampai user re-record atau agent re-explore.
- **AndroTMem-inspired** (Mar 2026): state anchor causally-linked + subgoal retrieval. Implementasi konkret belum public, **disclaimer**: ini referensi future direction, tidak boleh jadi requirement Phase 3.

**Honest take**: Auto-repair locator sepenuhnya otomatis **belum solved** per Mei 2026. Yang feasible: detect drift → invalidate skill → trigger re-exploration (LLM agent) → user approval untuk skill baru.

---

## 3. Skill Discovery

Tiga modalitas, urutan rekomendasi adoption:

### 3.1 Manual Recording (User Demo Mode) — Phase 1 MVP

Pattern AppAgent (manual demo mode) dan UFO2 (record-replay):

- User klik tombol "Record skill" di ChibiClaw overlay.
- Service Accessibility mencatat setiap event `TYPE_VIEW_CLICKED`, `TYPE_VIEW_TEXT_CHANGED` + screenshot pre-action.
- Selesai recording, LLM diminta nama skill + parameter extraction (mis. "step 3 isi teks ini, parameterkan jadi `$message`?").
- Skill disimpan, user confirm sebelum aktif.

**Kelebihan**: 100% akurat untuk user-specific flow (mis. urutan tap-tap-tap khas user di TikTok). **Kekurangan**: butuh effort eksplisit, scale-nya rendah.

### 3.2 Implicit Learning (Agent Self-Exploration)

Pattern AppAgent autonomous exploration + AppAgentX chain evolution:

- Setiap kali agent run task vision-grounding penuh, trace 100% di-log.
- Background job (idle/charging): scan log 7-30 hari terakhir, cari **k-step subsequence yang muncul ≥ N kali** dengan keberhasilan ≥ 80%.
- LLM evaluator (pattern `ChainEvaluationResult` AppAgentX) menilai `is_templateable: bool`.
- Skill candidate di-queue ke "pending review" — user approve sebelum jadi production skill.

**Konfirmasi AppAgentX**: pattern ini terbukti di paper (Mar 2025) dan repo public.

### 3.3 Crowdsourced / Community Share

Konsep: 1 user record skill "Login Mandiri OneKlik via biometrik" → upload (anonymized) ke marketplace ChibiClaw → user lain pull.

**Risiko serius**:
- App locator (resource_id) bisa berbeda per-device karena dynamic theming, A/B test app, app version.
- Skill bisa di-craft jahat (mis. skill "Send WA" yang sebenarnya transfer DANA ke nomor attacker).
- Liability: kalau crowdsourced skill rusak akun bank, siapa tanggung jawab?

**Rekomendasi**: **Defer ke v4.x post-launch**, atau buat allow-list curated only (mis. official ChibiClaw team upload skill). Jangan buka open marketplace di Phase 3.

### 3.4 Mining dari Log Histori

Mirip 3.2 tapi lebih kuantitatif. Algoritma sederhana:

```
for each app_package in last_30d_logs:
  traces = [t for t in all_traces if t.app == app_package and t.success]
  for window_size in [3, 4, 5, 6]:
    candidates = sliding_window_count(traces, window=window_size)
    for cand in candidates:
      if cand.count >= 3 and cand.success_rate >= 0.8:
        emit_skill_candidate(cand)
```

Implementasi practical: pakai Room query + Kotlin coroutine job, hindari LLM untuk fase pre-filter (cheap heuristic dulu, LLM hanya untuk naming + parameter extraction).

---

## 4. Similarity Matching saat Task Incoming

### 4.1 Modalitas Retrieval

| Strategi | Latency | Akurasi | Cost | Cocok |
|----------|---------|---------|------|-------|
| **Embedding cosine** (intent text → skill description) | 50-200ms (on-device) | Tinggi semantik | Embedding model ~ 100-500MB | Default Phase 3 |
| **BM25 / keyword** (lokal SQLite FTS) | <10ms | Medium, miss paraphrase | Hampir nol | Fallback cold start |
| **LLM router** (Gemma 4 dengan list skill names) | 500ms-3s | Kontekstual paling baik | Token cost / kompute | Tie-breaker top-3 candidates |
| **Hybrid** (BM25 → top-20 → embed re-rank → top-3 → LLM choose) | 100-300ms | Optimal | Moderate | Production recommended |

### 4.2 Embedding Model Pilihan untuk On-Device Indonesia

Per akses 2026-05-13:

- **Gemma 4 embedding head** (jika exposed di LiteRT-LM 0.11+) — preferred kalau model sudah loaded untuk routing, marginal cost rendah.
- **bge-small-en-v1.5** (33M, 384-D) — quantized di Snapdragon QNN feasible, akurasi medium.
- **multilingual-e5-small** (118M, 384-D) — mendukung Indonesia, ukuran moderat.
- **OpenAI text-embedding-3-small** (cloud, $0.02/1M tokens) — kalau user opt-in cloud, akurasi tertinggi tapi privacy concern untuk intent message.

**Rekomendasi**: Phase 3 default = bge-small-en-v1.5 atau multilingual-e5-small (on-device ONNX), opsional cloud OpenAI bila BYOK set.

### 4.3 Cold-Start

Kalau no skill cached untuk intent baru: **fallback ke full vision-grounding pipeline planner-executor** (lihat Research 01). Pattern:

```
matched_skills = retrieve_top_k(intent, k=3)
if matched_skills[0].score < THRESHOLD (mis. 0.72 cosine):
    -> full LLM planning + execution
    -> after success: log trace, queue for mining (Bab 3.4)
else:
    -> execute matched skill, fallback ke planning kalau gagal
```

Threshold 0.72 cosine adalah **heuristic awal**, perlu tuning empiris setelah ada data 1000+ task.

---

## 5. Storage & Sync

### 5.1 Room Entity Concrete (Phase 3 MVP)

```kotlin
@Entity(tableName = "skill",
        indices = [Index("app_package"), Index("severity"), Index("approved_by_user")])
data class SkillEntity(
    @PrimaryKey val id: String,                  // ULID
    val name: String,
    val description: String,                     // untuk embedding + display
    val version: Int = 1,
    val appPackage: String,
    val appVersionRange: String? = null,
    val intentExamplesJson: String,              // JSON array
    val intentEmbedding: ByteArray,              // serialized float[]
    val preconditionsJson: String,
    val parametersJson: String,
    val successCriteriaJson: String,
    val failureRecoveryJson: String,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val lastSuccessAt: Long = 0L,
    val lastLayoutHash: String? = null,
    val source: String,                          // "user_demo"|"agent_explore"|"shared"
    val severity: String,                        // "low"|"medium"|"high"
    val approvedByUser: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "skill_step",
        foreignKeys = [ForeignKey(entity = SkillEntity::class,
                                   parentColumns = ["id"],
                                   childColumns = ["skillId"],
                                   onDelete = ForeignKey.CASCADE)],
        indices = [Index("skillId")])
data class SkillStepEntity(
    @PrimaryKey val id: String,
    val skillId: String,
    val stepIndex: Int,
    val action: String,                          // tap|swipe|input|deeplink|...
    val locatorJson: String,                     // Locator object with fallback_chain
    val waitConditionJson: String?,
    val retryMax: Int = 2,
    val retryBackoffMs: Long = 500L,
    val parameterRef: String? = null
)

@Fts4(contentEntity = SkillEntity::class)
@Entity(tableName = "skill_fts")
data class SkillFtsEntity(
    val name: String,
    val description: String,
    val intentExamplesJson: String
)

@Entity(tableName = "skill_run_log",
        indices = [Index("skillId"), Index("ranAt")])
data class SkillRunLogEntity(
    @PrimaryKey val id: String,
    val skillId: String,
    val ranAt: Long,
    val success: Boolean,
    val durationMs: Long,
    val failedStepIndex: Int?,
    val failureReason: String?
)
```

Catatan:
- `intentEmbedding: ByteArray` — Float32 array di-serialize. Untuk 384-D, size = 1.5 KB. 1000 skill = 1.5 MB, masih ringan.
- `skill_fts` untuk BM25 fallback dan cold-start search via FTS4.
- `skill_run_log` enables drift detection: turunkan rolling success rate, kalau anjlok → mark skill stale.

### 5.2 Cloud Sync

Pertimbangan utama:
- **BYOK secret tantangan**: ChibiClaw v4 pakai BYOK API key untuk LLM cloud. Tidak ada "ChibiClaw cloud account" default. Mau sync skill ke mana?
- **Opsi A — User cloud storage** (Google Drive / iCloud-like / GitHub Gist): user bring own backend. Privacy bagus, complexity tinggi (auth tiap provider).
- **Opsi B — Self-host backend opsional**: deploy backend kecil (FastAPI + SQLite) di Lightsail user, mirror pattern VIONA RAG. Privacy nominal user, tapi maintenance burden user.
- **Opsi C — No sync, export/import JSON manual**: paling murah, user export JSON saat ganti device.

**Rekomendasi Phase 3**: **Opsi C dulu**, defer cloud sync ke Phase 4+. Versioning konflik tidak perlu diselesaikan kalau export manual.

### 5.3 Versioning App Update

Skenario: skill "Send WhatsApp" recorded saat WA 2.25.10, lalu update ke 2.26.0 dan layout login changed. Strategi:

- `app_version_range` di SkillEntity dipakai sebagai pre-flight check. Bila installed app version di luar range, skill **tidak masuk retrieval**, agent fallback ke planning + opportunistic re-mine.
- Bisa di-implementasi sebagai semver match library (Kotlin `Version.parse`) atau simple lexical compare untuk MVP.

---

## 6. Skill Execution Layer

### 6.1 Step Interpreter

Pseudo-Kotlin:

```kotlin
class SkillExecutor(
    private val executor: ActionDispatcher,       // existing v3 4-tier
    private val visionGrounder: VisionGrounder,   // existing v4 planner-executor
    private val a11yResolver: A11yResolver
) {
    suspend fun runSkill(skill: SkillEntity, params: Map<String, String>): SkillResult {
        for (step in skill.steps.sortedBy { it.stepIndex }) {
            checkUserInterrupted()      // abort kalau user touch screen
            val locator = resolveLocator(step.locator)
                ?: return SkillResult.Failure(step.stepIndex, "locator_miss")
            val ok = executeStep(step.action, locator, params, step.retryPolicy)
            if (!ok) return handleFailure(skill, step)
            waitFor(step.waitCondition)
        }
        return SkillResult.Success
    }
}
```

### 6.2 Wait Conditions

Tipe-tipe practical:
- `element_visible(resource_id, timeout_ms)` — poll A11y tree.
- `text_visible(regex, timeout_ms)` — OCR poll.
- `screen_changed(prev_hash, timeout_ms)` — hash list of resource_id, deteksi page transition.
- `network_idle(quiet_ms)` — pakai Android `ConnectivityManager` proxy.
- `duration_ms(ms)` — last-resort fixed delay.

### 6.3 Retry Policy

Per step level: `max=2, backoff_ms=500`. Backoff exponential opsional. Setelah max retry, eskalasi ke fallback locator (Locator.fallback_chain) sebelum fail step.

### 6.4 Stop on User Intervention

Kritis untuk UX: user touch screen saat skill running = **immediate abort**. Implementasi:

- Subscribe `AccessibilityEvent.TYPE_VIEW_CLICKED` di luar action ChibiClaw sendiri → set flag `userInterrupted = true`.
- Alternatif: deteksi touch event di overlay layer ChibiClaw (lebih invasive).
- Bila abort, **rollback partial state tidak dijamin** (UI sudah berubah). Skill harus idempotent / continueable, atau eksplisit warn user "sebagian sudah dijalankan".

---

## 7. Risk & Safety

### 7.1 Skill Exclusion Zone

**WAJIB tidak boleh masuk skill cache**:

| Kategori | Contoh | Alasan |
|----------|--------|--------|
| OTP input | SMS OTP forwarding, OTP autofill | Bypass anti-fraud user |
| Password / PIN | Input PIN m-banking, input PIN store | Credential storage di plaintext skill |
| Biometric | Auto-trigger fingerprint, face scan | Defeat consent flow |
| Payment final confirm | Tap "Bayar" di GoPay/DANA, signing crypto | Liability irreversible action |
| App installation grant | Tap "Install" Play Store | Risiko malware install |
| Permission grant | Auto-grant Accessibility, Location | Defeat OS-level consent |

Mekanisme enforcement:
- Skill discovery (manual + mining) **menolak step yang menyentuh field dengan keyword sensitif**: regex match resource_id/text mengandung `otp`, `pin`, `password`, `cvv`, `biometric`, `pay`, `confirm`, `install`, `grant`.
- LLM evaluator final check (saat user approve skill): "Apakah skill ini menyentuh OTP/pembayaran/perizinan?". Bila ya, set `severity = "high"` atau reject.

### 7.2 Severity Tagging

- `low`: read-only action (scroll, screenshot, search, info read).
- `medium`: send message, post comment, like, follow (revertible secara sosial).
- `high`: payment, financial transaction, permission grant — **WAJIB konfirmasi user setiap run, no auto-replay** meski cached.

Implementasi reuse pattern v3 `AutoControlGate` (existing per-app policy + inline safety gate di 4 HIGH-risk tools).

### 7.3 Sandbox: App Cross-Boundary

Skill `target_app = "com.whatsapp"` **tidak boleh** execute step di package lain. Cek di SkillExecutor:

```kotlin
if (currentForegroundPackage != skill.appPackage && step.action != "deeplink") {
    return SkillResult.Failure(step.stepIndex, "wrong_app_in_foreground")
}
```

Pengecualian: step `deeplink` boleh transit ke app target (mis. start WhatsApp via intent). Setelah deeplink, expected foreground == target.

### 7.4 Provenance & Approval

- Setiap skill punya field `source: "user_demo"|"agent_explore"|"shared_community"`. Source `shared_community` di Phase 3 disabled by default.
- `approved_by_user: bool` harus `true` sebelum skill aktif di retrieval. Default agent_explore skill = false, user dipanggil review.

---

## 8. Reference Repos & Papers Detail

Disusun dari paling actionable ke optional.

### Tier S — Wajib study source code

| Repo / Paper | URL | License | Last Activity | Skill Schema | Catatan |
|--------------|-----|---------|---------------|--------------|---------|
| **AppAgentX** | [github.com/Westlake-AGI-Lab/AppAgentX](https://github.com/Westlake-AGI-Lab/AppAgentX) | Apache 2.0 | Active 2025 | Neo4j graph + Pinecone, `ActionNodeGeneration` Pydantic terkonfirmasi di `chain_evolve.py` | Paper [arxiv 2503.02268](https://arxiv.org/abs/2503.02268). Closest match konsep. |
| **AppAgent v2 (Tencent)** | [github.com/TencentQQGYLab/AppAgent](https://github.com/TencentQQGYLab/AppAgent) | Apache 2.0 | Maintained | Per-element `.txt` keyed by `resource_id`, 5 action fields | Paper [arxiv 2408.11824](https://arxiv.org/abs/2408.11824). Schema element-level concrete. |
| **Mobile-Agent-v3.5** | [github.com/X-PLUG/MobileAgent/tree/main/Mobile-Agent-v3.5](https://github.com/X-PLUG/MobileAgent/tree/main/Mobile-Agent-v3.5) | Apache 2.0 | Aug-Nov 2025 active | `InfoPool` string-based, memory built-in di GUI-Owl-1.5 | Paper [arxiv 2508.15144](https://arxiv.org/pdf/2508.15144). Bukan skill library eksplisit, working memory pattern. |
| **MAS-Bench** | [github.com/Pengxiang-zhao/MAS-Bench](https://github.com/Pengxiang-zhao/MAS-Bench) | Tidak terverifikasi (perlu cek `LICENSE`) | Sep 2025 | 88 shortcuts: 11 API + 70 deeplink + 7 RPA script | Paper [arxiv 2509.06477](https://arxiv.org/abs/2509.06477). Benchmark + sample shortcuts terbuka. |
| **Voyager** | [github.com/MineDojo/Voyager](https://github.com/MineDojo/Voyager) | MIT | Maintained | Code+description+vectordb folder pattern | Foundational pattern, code-as-skill. Tidak Android-specific. |

### Tier A — Reference untuk pattern

| Repo | URL | Catatan |
|------|-----|---------|
| AutoDroid + V2 | [arxiv 2308.15272](https://arxiv.org/pdf/2308.15272), MarkTechPost 2025 untuk V2 | UTG (UI Transition Graph) memory pattern, MobiCom 2024 |
| AndroTMem | [arxiv 2603.18429](https://arxiv.org/abs/2603.18429) | Anchored State Memory, Mar 2026 |
| MemGUI-Bench | [lgy0404.github.io/MemGUI-Bench](https://lgy0404.github.io/MemGUI-Bench/), arxiv 2602.06075 | Benchmark formal memory mobile agent |
| WorkflowGen | [arxiv 2604.19756](https://arxiv.org/abs/2604.19756) | Trajectory experience-driven workflow induction, 40% token reduction |
| AgentRR (Record & Replay) | [arxiv 2505.17716](https://arxiv.org/html/2505.17716v1) | "100% reliable replay remains elusive" — honest assessment |
| UFO2 | (lihat blog Microsoft) | OS-level integration record-replay |
| Agent Skills spec | [agentskills.io/specification](https://agentskills.io/specification) | Anthropic open spec, SKILL.md format |
| UI-TARS / UI-TARS-2 | [github.com/bytedance/UI-TARS](https://github.com/bytedance/UI-TARS) | Memory di-bundle di model, bukan eksternal skill |

### Tier B — Tambahan

- OS-Atlas: [arxiv 2410.23218](https://arxiv.org/abs/2410.23218) — foundation grounding model, tidak fokus skill.
- SeeAct: [github.com/OSU-NLP-Group/SeeAct](https://github.com/OSU-NLP-Group/SeeAct) — web agent (bukan mobile), pattern action-grounding split.
- Sikuli, MacroPilot, OpenAdapt — desktop macro tools, **bukan skill library agentic**, hanya record-replay manual.
- AskUI smart selectors — komersial, klaim 40% maintenance reduction tapi no academic paper.

---

## 9. Praktis untuk ChibiClaw v4 Phase 3

### 9.1 Schema MVP (sudah ada di Bab 1.9 + Bab 5.1)

Pakai Room entity `SkillEntity` + `SkillStepEntity` + `SkillFtsEntity` + `SkillRunLogEntity` di Bab 5.1. Versi 1 schema (`version: Int = 1`) cukup; siapkan migration plan kalau kelak ganti embedding model.

### 9.2 Discovery Strategy

**Phase 1 (Week 1-2 Phase 3)**: Manual recording only. UI overlay tombol "Record" → user demo → LLM naming + parameterize → user approve. Cukup untuk validate UX dan storage flow.

**Phase 2 (Week 3-6)**: Tambah implicit mining offline. Background WorkManager job (constrained: charging + idle). Algoritma sliding-window k=3..6, threshold count ≥ 3, success rate ≥ 0.8. LLM evaluator pakai Gemma 4 lokal untuk naming dan templateability assessment.

**Phase 3 (Week 7+)**: Skill-share opsional via export/import JSON file. Crowdsourced marketplace **postpone** ke v4.1+.

### 9.3 Eviction Policy

Skill di-archive (bukan dihapus, untuk audit) kalau memenuhi salah satu:

| Kriteria | Threshold MVP |
|----------|---------------|
| Stale by time | `last_success_at < now - 60 days` AND `success_count < 5` |
| Stale by failure rate | Rolling 7-day success rate < 50% setelah ≥ 5 run |
| App version out of range | `app_version_range` mismatch installed app |
| Layout drift | `last_layout_hash != current_screen_hash_at_first_step` |

Archived skill bisa di-re-explore otomatis (queue ulang ke discovery 3.2/3.4).

### 9.4 Embedding Pipeline

Default: **multilingual-e5-small** (118M, 384-D), quantized ONNX, jalankan di CPU thread (snapdragon NPU optional via QNN). Latency ditarget < 50ms per query embedding.

Storage: embedding di-cache di `SkillEntity.intentEmbedding` saat skill create. Re-embed seluruh DB kalau ganti model (versioning di SharedPreferences `embedding_model_id`).

Fallback ke FTS4 BM25 kalau embedding model belum ready (cold-start aplikasi).

### 9.5 Integration Points dengan v3 Existing

- **ActionDispatcher** (existing v3): reuse sebagai backend executor untuk Step. Tambah method `runStep(step: SkillStep)` di luar tool registry biasa.
- **AutoControlGate**: extend untuk evaluate skill severity sebelum run, bukan per-tool.
- **ChibiAgent**: tambah `SkillRouter` di layer planning: cek skill match dulu sebelum call full vision planner.
- **Tabel DB v4**: tambah 4 tabel skill (skill, skill_step, skill_fts, skill_run_log) → migration v4 → v5.

### 9.6 Open Questions untuk Lendra Decide

1. **Embedding model**: Gemma 4 embedding head (jika exposed LiteRT-LM) atau multilingual-e5-small standalone? Trade-off RAM vs accuracy.
2. **Severity LLM gate**: pakai Gemma 4 lokal (cepat, possibly hallucination) atau Claude/GPT cloud (akurat, latency + cost)? Untuk "approve high-severity skill", justification cloud rasional.
3. **Vision-template storage**: simpan crop screenshot per locator di `assets/skill/<id>/<step>.png`? Storage growth 50-200KB per step → 1000 skill x 5 step = 250 MB-1GB. Acceptable?
4. **Crowdsourced timeline**: Phase 3 simply skip, atau allow read-only import dari kurator? Tidak prioritas.
5. **Drift auto-repair**: invest engineer di OCR-healing locator (asumsi Phase 3), atau cukup invalidate skill + trigger re-mine (lebih murah)? **Saran**: yang kedua untuk MVP.

---

## 10. Catatan Confidence dan Hallucination Disclosure

Klaim yang **TIDAK** terkonfirmasi langsung dari source publik per akses 2026-05-13:

1. **Dimensi embedding ResNet-50 AppAgentX**: kode `image_feature_service` tidak ter-fetch, asumsi 2048-D standar. Verifikasi diperlukan kalau adopsi langsung.
2. **Notetaker schema Mobile-Agent-v3.5 detail**: file `notetaker.py` tidak public via WebFetch (404). Inference berbasis README/DeepWiki saja.
3. **MAS-Bench JSON formal shortcut**: paper tidak expose schema explicit, hanya kategorisasi 3 jenis. Detail field perlu repo inspect.
4. **AndroTMem Anchored State Memory schema konkret**: abstract paper Mar 2026, kode publik / detail field belum diakses.
5. **Auto-healing locator AskUI 40% reduction**: klaim vendor, tidak ada paper akademik validasi.
6. **Gemma 4 embedding head exposure di LiteRT-LM 0.11**: tidak diverifikasi langsung di docs LiteRT, asumsi best-case scenario.
7. **Threshold cosine 0.72 untuk match**: heuristic awal, tidak diturunkan dari benchmark spesifik.
8. **Pricing on-device embedding latency 50ms**: ekstrapolasi dari benchmark Snapdragon 8 Elite untuk model 100M param, perlu re-bench aktual.

Yang **dikonfirmasi langsung** dari source code / paper:

- AppAgent v2 `auto_docs/<resource_id>.txt` 5-action schema (terkonfirmasi `scripts/self_explorer.py`).
- AppAgentX `ActionNodeGeneration` Pydantic 6 field + `ChainEvaluationResult` 4 field (terkonfirmasi `chain_evolve.py`).
- Voyager skill_library folder structure code+description+skills.json+vectordb (terkonfirmasi repo).
- MAS-Bench 88 shortcuts split 11 API / 70 deeplink / 7 RPA (terkonfirmasi paper abstract + body).
- Mobile-Agent-v3.5 4 roles planner/executor/verifier/notetaker + `InfoPool.important_notes` (terkonfirmasi README + DeepWiki).
- UI-TARS 4-modul perception/action/memory/reasoning (terkonfirmasi paper + MarkTechPost).
- Anthropic Agent Skills SKILL.md YAML+Markdown format (terkonfirmasi spec public Dec 2025).

---

## 11. Action Items Konkret untuk Lendra

Prioritas tinggi, urutan eksekusi:

1. **(Minggu 1)** Fork AppAgent v2 dan AppAgentX. Baca `chain_evolve.py` (300 lines) dan `scripts/self_explorer.py` (lihat actual schema). Konfirmasi field yang dipakai.
2. **(Minggu 1)** Decision call: embedding model pilihan (Gemma 4 head vs e5-small vs bge-small). Test latency di Snapdragon 8 Elite Gen 5.
3. **(Minggu 2)** Implementasi `SkillEntity` + `SkillStepEntity` di Room v5. Migration script dari DB v4.
4. **(Minggu 2-3)** UI prototype "Record skill" overlay + flow user demo recording.
5. **(Minggu 3-4)** SkillRouter di ChibiAgent — retrieval embedding + fallback to vision planner. Test cold-start dengan 0 skill, 10 skill, 100 skill scenario.
6. **(Minggu 4-5)** Severity classifier + AutoControlGate integration. Test high-severity skill auto-confirm.
7. **(Minggu 6+)** Background mining job (k-step subsequence detector). Tunggu trace data terkumpul ≥ 100 task sebelum activate.
8. **(Bulan 2+)** Drift detection v0: layout hash + rolling success rate. Auto-repair locator: **defer**.
9. **(Bulan 3+)** Export/import JSON. Crowdsourced marketplace: defer indefinitely.

---

## Lampiran A — Diff vs Plan v4 Eksisting

Update yang perlu masuk ke [docs/v4/README.md](../v4/README.md):

1. **Tabel database v4**: tambah 4 tabel skill (skill, skill_step, skill_fts, skill_run_log). Bump schema version v4 → v5.
2. **Phase 3 timeline**: alokasikan 4-6 minggu untuk skill library MVP (manual record → mining → router → eviction).
3. **Dependency baru**: ONNX Runtime Android + multilingual-e5-small quantized model (~50-100MB increment), atau verify Gemma 4 embedding head feasibility.
4. **Tool catalog (Research 01 finding #3)**: explicitly disebut "Skill Library layer di runtime untuk macro per-app (pattern AppAgentX)" — confirmed di research ini.
5. **Update Risk Matrix**: tambah baris "Skill drift across app updates" (med likelihood, high impact, mitigation: invalidate + re-mine).

---

**End of Research 05.**

Tanggal akses semua URL di atas: **2026-05-13**.
