# VIONA Telco RAG Optimization — Hybrid Retrieval, Embedding Selection, Evaluation

**Status**: Deep research draft untuk roadmap VIONA v1.2+
**Tanggal akses sumber**: 2026-05-13
**Versi current**: v1.1.7 (production, 5-agent pool, hybrid retrieval, JWT, AWS Lightsail + Cloudflare Tunnel)
**Author**: research compile untuk Lendra / BIGNET-ID

---

## 0. Ringkasan Eksekutif

Dokumen ini adalah konsolidasi riset untuk roadmap optimasi VIONA — production RAG chatbot telco berbahasa Indonesia dengan code-switching ke English. Fokus utama:

1. Naik akurasi hybrid retrieval (BM25 + dense + reranker) tanpa blow-up biaya.
2. Pilih embedding model multilingual yang strong untuk ID + telco jargon.
3. Tune ANN index (HNSW) ke target latency p95 < 500 ms.
4. Bangun evaluation framework (RAGAS + custom telco set) supaya tiap deploy bisa diukur.
5. Refactor 5-agent pool jadi pipeline router/retriever/reranker/composer/safety yang punya fallback jelas.
6. Adopsi teknik 2024-2026: Contextual Retrieval (Anthropic), Late Chunking (Jina), CRAG, prompt caching.

Rekomendasi singkat untuk v1.2:
- **Swap embedding**: `BAAI/bge-m3` (open source, multilingual, 1024-dim, dense+sparse+ColBERT dalam satu model) untuk hapus dependency ke API embedding berbayar.
- **Tambah reranker**: `BAAI/bge-reranker-v2-m3` di Lightsail GPU shape kecil atau CPU dengan ONNX/INT8.
- **Fusion**: pakai Reciprocal Rank Fusion (RRF, k=60) sebagai default; siapkan A/B test convex combination dengan tuned α.
- **Contextual Retrieval**: prepend chunk-context yang di-generate Claude Haiku (dengan prompt caching) sebelum embedding.
- **Eval**: RAGAS (faithfulness, context recall, answer relevance) + golden set telco 200-500 pertanyaan, dijalankan tiap CI.
- **HNSW**: M=32, efConstruction=256, efSearch=128 sebagai starting point; tune per-corpus.

Estimasi cost delta v1.1.7 → v1.2: turun ~30-50% LLM cost berkat prompt caching + reranker yang reduce konteks, naik sedikit di compute lokal untuk bge-m3 + reranker.

---

## 1. Hybrid Retrieval Tuning (BM25 + Dense)

### 1.1 Strategi Fusion: RRF vs Linear Combination vs Learned

**Reciprocal Rank Fusion (RRF)** — formula klasik: `score(d) = Σ 1 / (k + rank_i(d))` dengan `k=60` default. Sifatnya score-scale agnostic karena hanya pakai rank, bukan raw score BM25 / cosine. Cocok untuk start cepat tanpa labeled data.

- Plus: minimal tuning, robust di out-of-domain query.
- Minus: convex/linear combination yang ter-tune sering kalahkan RRF kalau ada ~40+ labeled pair. Studi 2024 (ACM TOIS, "Analysis of Fusion Functions for Hybrid Retrieval") menunjukkan convex combination outperform RRF di in-domain dan OOD. Studi praktis Elasticsearch (Wands furniture dataset, 2025) menunjukkan plain RRF hanya +1.3% nDCG vs BM25 baseline, sementara tiered weighting bisa +7.5%.

**Linear Combination**: `score = α · norm(BM25) + (1-α) · cosine`. Wajib normalisasi (min-max atau z-score) karena BM25 dan cosine punya skala berbeda. Trend 2025: **per-query dynamic α** — classifier ringan deteksi query tipe keyword vs semantik, lalu set α berbeda.

**Learning-to-Rank (LTR)**: pakai LambdaMART / XGBRanker pada fitur (BM25, cosine, recency, click-rate, intent prob). Butuh feedback log >5k labeled. Bisa nambah +5-8% nDCG@10 di production, tapi maintenance heavy.

**Rekomendasi VIONA v1.2**: RRF default (k=60) + collect logs untuk train convex α di v1.3.

### 1.2 BM25 k1/b Tuning

Default Lucene/Elasticsearch: `k1=1.2`, `b=0.75`. Untuk teks Indonesia campuran istilah telco:

- **k1 (term frequency saturation)**: naikkan ke 1.4-1.8 kalau query sering muncul keyword teknis (misal "APN konfigurasi LTE band 3"). Lower kalau dokumen pendek FAQ.
- **b (length normalization)**: turunkan ke 0.5-0.6 kalau dokumen panjang (knowledge article 1-2 halaman) karena `b=0.75` over-penalize doc panjang. Pertahankan 0.75 kalau corpus FAQ pendek.
- **Stopwords Indonesia**: WAJIB custom (Sastrawi list + telco-specific filter "yang", "dengan", "atau"). Default English stopwords akan keep "yang" yang noise.
- **Stemming**: Sastrawi (Indonesian Porter-like) — penting untuk match "isi ulang" vs "mengisi ulang" vs "diisi".

Library:
- Python: `rank_bm25` (BM25Okapi/BM25Plus/BM25L) untuk prototype, tapi single-thread.
- Production: Elasticsearch / OpenSearch / Lucene index, atau Vespa.
- Tantric: `bm25s` (lebih cepat 100x dari rank_bm25, scipy sparse based).

### 1.3 Chunking Strategy

Konsensus 2025 untuk RAG production:

- **Chunk size**: 256-512 token, sweet spot 400-512 untuk FAQ-style.
- **Overlap**: 10-20% (50-100 token). Studi Januari 2026 (SPLADE + Mistral-8B di Natural Questions) menemukan overlap tidak memberi benefit signifikan, hanya nambah indexing cost. Verifikasi di golden set VIONA dulu sebelum drop overlap.
- **Recursive character splitter**: pakai LangChain `RecursiveCharacterTextSplitter` dengan separator `["\n\n", "\n", ". ", " "]`. Hindari fixed-size byte chunker karena bisa potong di tengah kata Indonesia.
- **Semantic chunking**: split berdasarkan cosine drop antar kalimat (LlamaIndex `SemanticSplitterNodeParser`). Quality lebih tinggi tapi 3-5x lebih lambat. Tradeoff worth kalau corpus stable.
- **Markdown / structured**: split per heading H2/H3 untuk knowledge base, lalu sub-split kalau >800 token.

### 1.4 Late Chunking (Jina, 2024 paper)

Late Chunking (arXiv:2409.04701) — encode seluruh dokumen dulu di context window 8192 token, baru apply pooling per chunk. Tiap chunk embedding "sadar" konteks tetangga (anaphora, pronoun resolution). Diaktifkan via `late_chunking=True` di Jina embeddings v3 API.

Manfaat: chunk yang berisi "harga paket ini Rp 50.000" tetap punya context "ini" merujuk Yellow Combo karena pooling pakai full-doc representation. Eval di paper menunjukkan +5-10% nDCG@10 di setting where pronouns matter.

**Catatan VIONA**: late chunking butuh model dengan context window panjang (≥8k). `bge-m3` mendukung 8192 token, jadi feasible. Jangan campur late-chunked embedding dengan naive-chunked di satu collection — bias evaluasi.

### 1.5 Contextual Retrieval (Anthropic, Sep 2024)

Teknik dari Anthropic — sebelum embedding, prepend ke tiap chunk satu paragraf konteks pendek (50-100 token) hasil generate LLM:

```
Konteks: Chunk ini dari halaman "Paket Yellow Combo Smartfren",
membahas detail kuota harian dan masa aktif.

[isi chunk asli]
```

Hasil yang dilaporkan Anthropic:
- Contextual Embeddings saja: top-20 retrieval failure rate turun 35% (5.7% → 3.7%).
- Contextual Embeddings + Contextual BM25 + reranking: turun 67%.

Cost trick: pakai **Claude Haiku** untuk generate context, dan **prompt caching** ke base prompt + dokumen induk. Anthropic hitung untuk corpus 100k doc, biaya context generation ~$1.02 per 1M token (one-time). Pakai cache 90% discount.

**Implementasi VIONA**:
1. Untuk tiap chunk, prompt Haiku: "Berikan 1-2 kalimat ringkasan konteks chunk ini dalam dokumen induk."
2. Cache base prompt (dokumen induk) selama batch processing.
3. Re-embed semua chunk dengan prefix konteks.
4. Index BM25 juga di field gabungan (konteks + chunk).
5. Rerun eval — kalau improvement nyata, persist ke v1.2.

### 1.6 Reranker Selection

| Reranker | Modal | Multi-lingual | nDCG@10 (general) | Latency (CPU) |
|---|---|---|---|---|
| `BAAI/bge-reranker-v2-m3` | Open source, 568M | Yes (100+ bahasa, dilatih atas bge-m3) | 0.715 | ~80ms/pair batch 32, ONNX INT8 |
| `BAAI/bge-reranker-v2.5-gemma2-lightweight` | Open source | Yes | sedikit di atas v2-m3 | lebih ringan |
| Cohere Rerank 3 / 4 multilingual | API | Yes | 0.735 (top of class) | ~50-100ms/call API |
| Voyage `rerank-2` / `rerank-2-lite` | API | 26 bahasa (ID belum eksplisit di doc) | beat BGE v2-m3 ~5% di multilingual bench | API |
| `jina-reranker-v2-multilingual` | API + open | Yes | recall parity dengan Cohere di multilingual | API |

**Rekomendasi VIONA**: `bge-reranker-v2-m3` self-hosted (open source, predictable cost, latency controllable). Kalau perlu boost akurasi 5-10% lagi dan budget OK, fallback ke Cohere Rerank 3 untuk top-tier query.

Tuning: retrieve top-50 dari hybrid (BM25+dense), rerank jadi top-5 untuk konteks LLM. Reranker biasanya kasih +10-20% Recall@5 vs hybrid alone.

---

## 2. Embedding Model State untuk Indonesia

### 2.1 Multilingual Open Source

**`BAAI/bge-m3`** (paper arXiv:2402.03216) — currently the most versatile choice untuk multilingual telco RAG ID:
- Multi-Linguality (100+ bahasa, ID supported), Multi-Functionality (dense, sparse/lexical, multi-vector/ColBERT-style dalam satu model), Multi-Granularity (sentence sampai 8192 token).
- Dimension 1024.
- License: MIT.
- Karena single model bisa output dense + sparse + multi-vector, mengurangi kompleksitas pipeline.
- Catatan: BAAI tidak publish skor MTEB resmi karena MTEB fokus mono-bahasa.

**Jina Embeddings v3** (arXiv:2409.10173, rilis Sep 2024) — frontier multilingual, ranked top-3 MTEB <1B param, support task-specific LoRA, MRL (Matryoshka), late chunking. Dimension 1024 (truncatable down ke 256/512 via MRL). License Apache-2.0 (model) tapi commercial use lewat API berbayar.

**Cohere `embed-multilingual-v3.0`** — 100+ bahasa, 1024-dim. **Tidak bisa fine-tune** (limit besar untuk domain telco). Input 512 token limit (kecil). API only.

**Voyage `voyage-multilingual-2`** atau `voyage-3-large` — high quality multilingual, custom fine-tune tersedia (plus). Daftar 26 bahasa multilingual eval (ID belum eksplisit di docs Voyage, perlu verifikasi).

### 2.2 Indonesia-specific

**IndoBERT** (Koto et al., COLING 2020 — IndoLEM) — base monolingual ID BERT. Bagus untuk klasifikasi intent, tapi bukan sentence-encoder.

**IndoSBERT** (IndoLEM ecosystem) — IndoBERT yang di-fine-tune siamese (SBERT-style) di STS Benchmark ID-translated. Dimension 768. Berguna untuk semantic similarity ID murni.

**`denaya/indoSBERT-large`** — versi large di HuggingFace, dimension 1024.

**`indo-sentence-bert-base`** — 768-dim sentence transformer khusus ID.

**LazarusNLP Indonesian Sentence Embeddings** (update Mei 2024, v4) — koleksi model yang di-fine-tune di seluruh supervised dataset ID. Dieval di MIRACL-ID dan TyDiQA-ID subset (metrik R@1, MRR@10, nDCG@10). Salah satu kandidat untuk fine-tune di atas bge-m3 dengan data telco.

**NusaBERT / NusaCrowd** (LazarusNLP) — IndoBERT yang ditambah multilingualisme + budaya ID. Resource korpus mencakup 137 dataset di 19 bahasa nusantara. Useful untuk fine-tuning data augmentation.

### 2.3 Telco Domain Adaptation

Tidak ada embedding pre-trained untuk telco ID, jadi pilihan:

1. **Lexical augmentation**: tambahkan glossary telco (paket, kuota, modem, sinyal, APN, IMEI, MSISDN) ke index BM25 dengan synonyms. Misal "kuota" ⇔ "data" ⇔ "internet".
2. **Fine-tune bge-m3** dengan triplet loss `(query telco, positive chunk, negative chunk)` dari log VIONA. Target: 1k-5k pasangan high-quality. Library: `FlagEmbedding`, `sentence-transformers` v3+.
3. **Hybrid signal injection**: di reranker, inject feature seperti "intent_match_score" dari intent classifier IndoBERT.

### 2.4 Dimension vs Latency Tradeoff

| Dim | Memory (per 1M vector) | Cosine latency | Recommended |
|---|---|---|---|
| 384 | ~1.5 GB | 0.1ms | Speed-critical, accept -3-5% recall |
| 768 | ~3.0 GB | 0.2ms | Balanced legacy |
| 1024 | ~4.0 GB | 0.25ms | Default modern (bge-m3, Cohere v3, Jina v3) |
| 1536-3072 | ~6-12 GB | 0.5ms+ | OpenAI v3, voyage-3-large; recall plateau di 1024 |

**Matryoshka (MRL)**: train 1 model, slice ke dim 256/512/1024 di runtime. Jina v3 dan voyage-3 support. Berguna untuk first-pass cheap retrieval (256-dim) + second-pass full 1024.

### 2.5 Open Source vs API Cost (estimate, 2026-05)

| Provider | Embed price | Notes |
|---|---|---|
| OpenAI `text-embedding-3-small` | $0.02/1M token | 1536-dim, cheap |
| OpenAI `text-embedding-3-large` | $0.13/1M token | 3072-dim |
| Cohere `embed-multilingual-v3.0` | $0.10/1M token | 1024-dim |
| Voyage `voyage-3` | gratis 200M token, lalu per-token | 1024-dim |
| Jina v3 API | tier free + per-token | 1024-dim |
| **Self-host bge-m3** | ~$0 marginal | GPU/CPU operasional. ~50-100 doc/s CPU dengan ONNX INT8 |

**Rekomendasi VIONA**: bge-m3 self-host di Lightsail (atau 1 instance GPU shape G2 kecil) untuk reproducibility dan privasi data telco (PII customer).

---

## 3. ANN Index Optimization

### 3.1 HNSW Tuning

Parameter inti:

- **M (max connections per node)**: default 16. Naikkan ke 32-48 untuk recall lebih baik di corpus >100k. Memory linear di M.
- **efConstruction**: default 200. Set 256-400 untuk build quality lebih tinggi (one-time cost). Tidak impact query memory.
- **efSearch**: query-time, dynamic candidate list. Default 64-128. Naikkan untuk recall, turunkan untuk latency.

Studi praktis 2025 (OpenSearch, Marqo, Pinecone):
- Preset OpenSearch: {M=16, ef=128} sampai {M=128, ef=256} sesuai size.
- Marqo recommended: `efConstruction=512, M=16, efSearch=2000` untuk balance high-recall.
- Trade: di 10M vector, ef=500 → 98% recall @ 5ms; ef=100 → 85% recall @ 1ms.

**Starting point VIONA** (corpus telco knowledge ~50-500k chunk):
- M=32, efConstruction=256, efSearch=128.
- Target: Recall@10 ≥ 95% di golden set, p95 retrieval ≤ 80ms (di luar LLM).

### 3.2 IVF-PQ untuk Skala Besar

IVF-PQ (Inverted File + Product Quantization):
- Cluster vektor ke `nlist` partition (k-means), saat query hanya scan `nprobe` partition.
- PQ compress vektor jadi byte codes (mis 8-16 byte vs 4096 byte float32).
- Memory ~4-8x lebih kecil dari HNSW.
- Recall ~95% dengan tuning, latency ~30-100ms di billion-scale.

Pakai IVF-PQ kalau:
- Corpus > 10M vector.
- Memory budget ketat (Lightsail tier kecil).
- Latency 50-100ms acceptable.

Pakai HNSW kalau:
- Corpus < 5M vector.
- Latency p95 < 30ms wajib.
- Memory cukup.

### 3.3 Vector DB Selection

| DB | Sweet spot | Filtering | Hybrid built-in | Notes |
|---|---|---|---|---|
| **Qdrant** | <10M, payload-heavy filter | Excellent (query planning, no pre/post filter) | Yes (sparse + dense) | Rust, Docker-friendly, fit Lightsail |
| **Milvus** | >50M, scale-out cluster | OK | Yes | Heavier ops, k8s preferred |
| **Weaviate** | hybrid + module-based | Good | Yes (BM25 + vector built-in, RRF native) | GraphQL, GenAI modules |
| **pgvector** | Postgres-native, <10M | SQL filter | DIY (with pg_search BM25) | Simplest if you have Postgres |
| **Vespa** | Search engine + RAG, phased ranking | Excellent | Yes (BM25 + tensor) | Powerful tapi steep learning |
| **FAISS** | Embedded, library | N/A (do it in app) | N/A | No server, lib only |

Benchmark 2025-2026: pgvector ~5K-15K QPS single-instance dengan HNSW 1024-dim. Qdrant & Weaviate smooth recall-latency tradeoff via ef tuning. Milvus menang index build time + memory di skala billion.

**Rekomendasi VIONA**: stay di stack yang ada atau migrate ke **Qdrant** kalau perlu payload filtering yang complex (tenant_id, intent_type, doc_recency). Migrasi via dual-write pattern (lihat Section 9).

### 3.4 Sharding untuk 5-Agent Pool

5-agent pool VIONA berarti 5 worker process / container instance. Strategi:

- **Single shared index, multiple readers**: agen baca dari Qdrant/Milvus shared cluster. Skala read replicas. Paling sederhana, dianjurkan untuk start.
- **Per-tenant shard**: kalau VIONA serve beberapa telco BU dengan corpus berbeda, shard by tenant_id. Filtering native Qdrant.
- **Hot/cold tiering**: corpus aktif (FAQ paket aktif) di RAM HNSW, corpus arsip (promo expired) di disk IVF-PQ.

### 3.5 Updateable Index

Real-time doc add — penting untuk telco karena promo & tariff sering berubah:

- **HNSW**: supports incremental add tapi delete adalah "tombstone" (penuh-kah index lama-lama). Periodic re-build / compact tiap 1-7 hari.
- **IVF**: rebuild centroids lebih costly, biasanya batch nightly.
- **pgvector 0.9+**: HNSW with on-disk build & online add, plus parallel index build.

**Pattern VIONA**: dual-index (live + nightly compacted), promote nightly index via blue-green swap.

### 3.6 Latency Target

Telco chat UX standar p95 < 1-2 detik end-to-end. Decompose:

| Stage | Budget p95 |
|---|---|
| Auth + parse + intent | 50ms |
| Hybrid retrieve top-50 | 80ms |
| Rerank top-50 → top-5 | 100ms |
| LLM generation (Claude/Gemini) | 800-1200ms (with prompt cache) |
| Safety + post-process | 50ms |
| **Total e2e p95** | **1.1-1.5s** |

Cloudflare Tunnel + AWS Lightsail base latency dari customer ID: ~50-150ms RTT. Pertimbangkan colocate vector DB dengan API node (sama region/AZ).

---

## 4. Evaluation Framework

### 4.1 RAGAS

RAGAS (paper arXiv:2309.15217, EACL 2024 demo) — reference-free / minimal-ground-truth evaluation untuk RAG. Metrik utama:

| Metrik | Apa | Target VIONA |
|---|---|---|
| **Faithfulness** | Berapa fraction klaim jawaban yang konsisten dengan retrieved context (no hallucination) | ≥ 0.90 |
| **Answer Relevance** | Apakah jawaban menjawab intent question, tanpa info berlebih/kurang | ≥ 0.85 |
| **Context Precision** | Apakah retrieved chunk yang ranked tinggi relevan | ≥ 0.80 |
| **Context Recall** | Apakah retrieval cover semua info yang diperlukan untuk jawaban ideal | ≥ 0.85 |
| **Context Entities Recall** | Entitas (nama paket, tarif, nomor) yang harus muncul | ≥ 0.90 |
| **Noise Sensitivity** | Robustness kalau ada chunk irrelevant masuk top-K | ≥ 0.80 |

Lib: `pip install ragas` (versi 0.2+). Integrasi dengan LangSmith / LangFuse.

### 4.2 DeepEval

DeepEval (Confident AI) — open source, 50+ metrik, native Pytest integration. Strong untuk CI/CD gate.

Plus: red-teaming module (toxicity, bias, jailbreak, PII leak). Cocok untuk safety guardrail VIONA, terutama L1/L2 dari organization policy.

```python
# contoh CI gate
@deepeval.dataset(golden_telco_v1)
def test_faithfulness(case):
    assert FaithfulnessMetric(threshold=0.85).measure(case) >= 0.85
```

### 4.3 TruLens

TruLens — strong observability via OpenTelemetry. Pakai untuk production tracing (span-level retrieval, rerank, LLM call). Bagus untuk diagnose pipeline failure post-deploy. Tidak punya red-teaming.

**Stack rekomendasi**: RAGAS untuk offline eval (golden set), DeepEval untuk CI + safety, TruLens untuk online observability.

### 4.4 Golden Telco Eval Set

Struktur dataset golden (target awal 200, growth ke 500-1000):

```jsonl
{"id": "tc-001", "intent": "check_quota",
 "query": "kuota saya tinggal berapa ya?",
 "lang": "id", "register": "colloquial",
 "expected_answer_keywords": ["sisa kuota", "MB", "GB"],
 "must_retrieve_doc_ids": ["KB-001", "KB-021"],
 "forbidden_phrases": ["tidak tahu", "hubungi CS"]}
```

Kategori coverage:
1. **FAQ langsung** (40%): "cara isi ulang", "harga paket Yellow"
2. **Multi-hop** (20%): "kalau saya beli paket A lalu B, masa aktif gimana?"
3. **Code-switching** (15%): "transfer ke nomor mama dong"
4. **Colloquial / typo** (15%): "internet lemot bgt nih", "kouta abis"
5. **Edge / grievance** (10%): complain sinyal, refund, double charge

Bangun bertahap dari log production v1.1.7 + manual annotation oleh CS team.

### 4.5 Human Eval Rubric

Blind A/B antara v1.1.7 vs v1.2 candidate, 100-200 query, 3 rater. Rubric 1-5:

- **Correctness** (factually right vs KB): 1=salah; 3=parsial; 5=tepat.
- **Helpfulness** (menjawab niat user): 1=tidak; 5=lengkap.
- **Tone** (sesuai brand telco ID, sopan, ringkas): 1=kasar/kaku; 5=natural.
- **Safety** (no PII leak, no false promise): 1=violation; 5=clean.

Inter-rater agreement target Cohen's κ ≥ 0.6. Hitung win-rate v1.2 vs v1.1.7.

### 4.6 LLM-as-Judge Bias Mitigation

Bias umum:
- **Position bias**: judge cenderung pilih response A vs B karena urutan. Mitigasi: randomize order, swap test.
- **Verbosity bias**: judge prefer jawaban panjang. Mitigasi: prompt eksplisit hindari verbosity preference.
- **Self-enhancement bias**: GPT-4 judge prefer output GPT-4. Mitigasi: pakai cross-family judge (Claude judge GPT, vice versa) atau jury voting 3 LLM.
- **Prompt sensitivity**: Run 3x dengan paraphrase prompt, agg median.

Anjuran: pakai dua judge berbeda (Claude 4.7 + Gemini 2.5 Pro) untuk faithfulness. Pakai Claude Haiku batch untuk context relevance (cheap).

### 4.7 Cost Eval

Hitung per-1000-query:
- LLM input/output token (split cached vs uncached)
- Embedding generation (kalau pakai API)
- Vector DB read ops
- Reranker compute
- Infrastructure idle (Lightsail base)

Target VIONA v1.2: ≤ $0.005 per query end-to-end (asumsi Claude Haiku composer + bge-m3 self-host + bge-reranker self-host).

---

## 5. Multi-Agent Orchestration Pattern

### 5.1 Refactor 5-Agent Pool

Saat ini 5-agent pool di VIONA likely worker pool homogen. Disarankan refactor jadi role-specialized pipeline:

| Agent | Role | Model rekomendasi |
|---|---|---|
| **Router / Intent** | Klasifikasi intent + decide retrieval vs direct response | IndoBERT fine-tuned atau Claude Haiku |
| **Query Rewriter** | Expand colloquial → formal, resolve pronoun, multi-query | Haiku |
| **Retriever** | Hybrid search (BM25 + dense) | bge-m3 + BM25 |
| **Reranker** | Rerank top-50 → top-5 | bge-reranker-v2-m3 |
| **Composer** | Generate answer dari context | Claude Sonnet/Opus (prompt-cached) |
| **Safety / PII** | Filter PII keluar, refuse jika L1 | DeepEval-style metric + rule + Haiku judge |

Bisa pakai 5-agent pool sebagai parallel worker, masing-masing menjalankan pipeline ini end-to-end. Atau pakai LangGraph yang model satu graph multi-node.

### 5.2 LangGraph vs LlamaIndex Workflows

- **LangGraph**: stateful graph (node + edge), conditional routing, human-in-the-loop interrupt, time-travel debug, integrasi LangSmith observability. Cocok untuk pipeline branching kompleks (router decision → tool call → retry).
- **LlamaIndex Workflows**: event-driven async, lebih ringan, kuat di RAG-centric tasks (query engine, router, fuser built-in). Klaim +35% retrieval accuracy boost di 2025 release.

**Rekomendasi**: LangGraph untuk VIONA karena butuh branching (fallback ke human, multi-turn dialog state, intent-conditional retrieval).

### 5.3 Sequential vs Parallel

- **Sequential** (default): router → rewrite → retrieve → rerank → compose → safety. Total latency = sum.
- **Parallel speculation**:
  - BM25 dan dense retrieve paralel (sudah default di hybrid).
  - Multi-query rewrite: generate 3 paraphrase query, retrieve paralel, fuse. Nambah recall ~5-10% di colloquial query, latency +50-100ms (paralel).
  - Speculative composer: mulai stream LLM saat reranker selesai top-3, append top-5 saat siap.

### 5.4 Caching Agent Output

- **Prompt cache (Anthropic)**: cache base system prompt + few-shot + KB summary. 90% diskon read token, 1.25-2x write. Cocok untuk composer dengan large system prompt static.
- **Result cache (Redis)**: cache `hash(query_normalized) → response` selama 5-15 menit untuk query identik (cek kuota umum, jam operasional). Hit rate 15-30% di telco FAQ.
- **Embedding cache**: cache query embedding by hash. Hit rate 40-60% untuk repeated FAQ.
- **Reranker cache**: cache `hash(query, top50_doc_ids) → ranked`. Hit rate sedang.

Estimasi penghematan VIONA: 30-40% LLM cost dari prompt cache + 10-20% dari result cache.

### 5.5 Failure Mode & Fallback

Pattern fallback hierarchy (tinggi ke rendah confidence):

1. **Happy path**: full pipeline normal.
2. **Reranker timeout/fail**: skip rerank, pakai top-5 dari hybrid.
3. **Dense retrieve fail**: fallback ke BM25-only.
4. **LLM composer fail**: serve template answer dari intent + top-1 chunk (no LLM).
5. **All retrieval kosong**: handoff ke human agent (escalation) + log untuk corpus gap analysis.

Circuit breaker per dependency (LLM API, vector DB, embedding service) dengan retry exponential backoff 3x.

---

## 6. Telco Domain Specifics

### 6.1 Intent Taxonomy

Berdasar pattern industri telco ID (Telkomsel, Indosat, Smartfren, XL/Axiata):

1. `check_quota` — sisa kuota, paket aktif
2. `check_balance` — pulsa, e-money
3. `buy_package` — beli paket data/voice/combo
4. `change_package` — upgrade/downgrade
5. `transfer_quota` / `transfer_pulsa`
6. `complaint_signal` — sinyal lemah, drop call
7. `complaint_speed` — internet lemot
8. `complaint_billing` — tagihan tidak sesuai, double charge
9. `sim_management` — ganti SIM, eSIM, PUK
10. `device_setup` — APN, MMS, hotspot
11. `promo_info` — promo aktif, kupon
12. `account_info` — registrasi, KYC NIK
13. `cancel_subscription`
14. `network_status` — gangguan area
15. `escalate_human` — semua yang tidak resolve

Train intent classifier IndoBERT-base atau IndoBERTweet (untuk colloquial) di 5k-10k labeled example. Target macro-F1 ≥ 0.85.

### 6.2 Entity Extraction

Entitas wajib di-extract:

- `package_name`: "Yellow Combo", "Smartfren Unlimited 50GB", "Telkomsel Halo".
- `quota_amount` + `unit`: "5 GB", "100 MB", "Unlimited".
- `price`: "Rp 50.000", "50rb", "Rp25K".
- `duration`: "harian", "mingguan", "30 hari".
- `phone_number`: MSISDN 08xxxxx — **SENSITIVE**, jangan log raw.
- `promo_code`.
- `area`: kota/kabupaten/provinsi untuk network status.
- `device_model`: "iPhone 15", "Xiaomi 17 Pro Max".

Gunakan IndoBERT NER fine-tuned atau hybrid rule + spaCy id-core-news + LLM verifier.

### 6.3 Colloquial & Code-Switching

Variasi yang harus dihandle:

- **Slang**: "lemot" (lambat), "kepo" (curious), "gabut" (idle), "bingits".
- **Typo umum**: "kouta", "kuata", "kuoata" → "kuota".
- **Singkatan**: "gpp" (gak apa-apa), "btw", "yg", "dgn", "bgt".
- **Bahasa daerah leak**: Jawa "lho", Sunda "atuh", Betawi "doang".
- **Code-switching**: "transfer ke nomor mama dong", "ada paket data unlimited gak?", "package nya lapse ya?".
- **Affix-heavy**: "ngabisin", "ngirim", "diisi", "kepake", "abisin".

Strategi:
1. Pre-normalisasi pakai Sastrawi stemmer + custom typo dictionary telco.
2. Multi-query expansion: generate formal version + colloquial version, retrieve dua-duanya, fuse.
3. Embedding bge-m3 / Jina v3 sudah handle code-switching decent, tapi reranker fine-tuned di telco data lebih kuat.
4. Index dokumen KB versi "ringkasan colloquial" parallel dengan versi formal.

### 6.4 Riset Lokal Relevan

- Studi Brilliance ITScience: chatbot intent classification LSTM untuk Kadin Indonesia, 90+ intent, akurasi 92.08% top-1.
- IndoBERT untuk intent overtime telco-adjacent: 87% vs SVM 85%.
- Bitext Telco LLM Chatbot Training Dataset (HuggingFace): multilingual training data dengan tag colloquial/formal/jargon.
- LazarusNLP Indonesian Sentence Embeddings: di-eval di MIRACL-ID + TyDiQA-ID, baseline kuat untuk semantic search ID.

---

## 7. Production Benchmark + Monitoring

### 7.1 Metrik Wajib di Dashboard

Latency (per stage + e2e):
- p50, p95, p99 retrieve, rerank, LLM, end-to-end.
- Alarm p95 > target 1.5s sustained 5 menit.

Cost:
- $/query (split: LLM input, LLM output, embedding API, vector DB, reranker compute).
- Daily cost ledger per intent / per tenant.

Quality:
- **Hit Rate**: fraction query yang reranker confidence > threshold (proxy untuk "answerable").
- **Faithfulness rolling**: sample 100 query/hari, eval RAGAS faithfulness.
- **Escalation rate** ke human: target < 15% (depend on telco SLA).
- **CSAT proxy**: thumb up/down rate inline di chat UI.

Drift:
- Embedding drift: cosine query↔retrieved rolling avg, alarm kalau drop > 2σ sustained.
- Intent distribution drift: KL divergence vs baseline mingguan.
- Corpus update lag: time-since-last-ingest per source.

### 7.2 Stack Observability

- **OpenTelemetry**: trace lintas-agent (router → retrieve → rerank → LLM → safety).
- **Prometheus + Grafana**: metrik kuantitatif.
- **TruLens**: deep RAG-specific span trace.
- **LangFuse / LangSmith**: LLM trace + cost ledger + prompt versioning.
- **Sentry**: error tracking.

### 7.3 SLO Suggestion

| SLO | Target | Window |
|---|---|---|
| Availability (200 response) | 99.5% | 30-day |
| p95 e2e latency | ≤ 1.5s | 7-day |
| Faithfulness rolling | ≥ 0.85 | 7-day |
| Escalation rate | ≤ 15% | 7-day |
| Cost/query | ≤ $0.005 | monthly |

---

## 8. Recent Paper & Blog (2024-2026)

### 8.1 Contextual Retrieval — Anthropic (Sep 2024)

URL: anthropic.com/news/contextual-retrieval. Detail di Section 1.5. Klaim 49% reduction failed retrieval, 67% dengan reranking. Anjuran adopsi untuk VIONA — kombinasi Haiku + prompt cache murah.

### 8.2 Late Chunking — Jina (arXiv:2409.04701, Sep 2024)

Implementasi di jina-embeddings-v3 dan reproducible di bge-m3 / model context-long. Detail Section 1.4.

### 8.3 Self-RAG (Asai et al., ICLR 2024)

LLM self-reflect: keluarkan token reflection `[Retrieve]`, `[Relevant]`, `[Supported]`, `[Useful]` selama generation. LLM bisa decide kapan retrieve, kapan skip. Bagus untuk reduce unnecessary retrieval cost. Trade: butuh fine-tune LLM (atau prompt heavy untuk frozen LLM).

### 8.4 CRAG — Corrective RAG (Yan et al., 2024)

Lightweight retrieval evaluator menilai retrieved doc (Correct / Incorrect / Ambiguous). Kalau Incorrect, lakukan web search fallback. Kalau Ambiguous, gabung internal + web. Cocok untuk VIONA kalau ada toleransi web search public (info promo aktual).

### 8.5 GraphRAG (Microsoft, Edge et al., 2024)

Bangun knowledge graph dari corpus + hierarchical community summaries. Bagus untuk multi-hop reasoning ("kalau paket A overlap B, masa aktif gimana?"). Studi 2025 (arXiv:2502.11371) menunjukkan +4.5% di HotpotQA multi-hop tapi 2.3x lebih lambat. Tidak diprioritaskan untuk VIONA v1.2 (FAQ-heavy), evaluate di v1.3 kalau multi-hop nyata.

### 8.6 ColBERT v2 + PLAID + Jina-ColBERT-v2 (89 bahasa)

Late interaction (token-level matching). Jina-ColBERT-v2 multilingual 89 bahasa, dim 128 (truncatable 64). Lebih akurat dari dense single-vector tapi storage 5-10x. PLAID engine reduce search latency 7x via centroid interaction.

Untuk VIONA: pakai bge-m3 multi-vector mode (sudah include ColBERT-style) kalau perlu, atau Jina-ColBERT-v2 sebagai reranker stage-2 lokal.

### 8.7 RAGOps (arXiv:2506.03401)

Survey OPS untuk RAG production: testing, deployment, monitoring patterns. Baik sebagai checklist maturity.

### 8.8 Agentic RAG Survey (arXiv:2501.09136)

Comprehensive survey 2025 tentang router, tool-use, iterative refinement, dan integrasi guardrails. Reference arsitektur untuk VIONA multi-agent.

### 8.9 MMTEB Benchmark (arXiv:2502.13595, ICLR 2025)

500+ task, 250+ bahasa termasuk ID. Acuan utama untuk evaluasi multilingual embedding di luar MTEB English-only. ID subset: MIRACL-ID, TyDiQA-ID.

### 8.10 Anthropic Prompt Caching

Sustained 90% cost discount untuk cached read, 85% latency reduction untuk long prefix. Wajib enable untuk composer agent VIONA (system prompt + few-shot biasanya 1-3k token static).

---

## 9. Concrete Recommendation untuk VIONA v1.2+

### 9.1 Priority Roadmap (90-day plan)

**Phase 1 — Foundation (week 1-3)**
1. Bangun golden eval set telco ID 200 query (CS team annotation).
2. Setup RAGAS + DeepEval di CI pipeline. Baseline measure v1.1.7.
3. Setup TruLens observability di staging.
4. Add prompt caching ke composer agent (low risk, immediate cost reduction).

**Phase 2 — Retrieval upgrade (week 4-8)**
5. Migrate embedding ke `BAAI/bge-m3` self-hosted (Docker, ONNX INT8 untuk CPU atau small GPU shape). Dual-write pattern.
6. Add `bge-reranker-v2-m3` setelah hybrid retrieval. Initially di top-50 → top-5.
7. Implement Contextual Retrieval (Haiku generate context prefix, prompt-cached). Re-embed corpus.
8. Tune HNSW M=32, efConstruction=256, efSearch=128. Validate Recall@10 ≥ 95%.
9. Tune BM25 k1=1.4, b=0.6, Sastrawi stopword + stem.
10. A/B test v1.1.7 vs v1.2-candidate di 20% traffic, freeze if win-rate ≥ 55%.

**Phase 3 — Multi-agent refactor (week 9-12)**
11. Refactor 5-agent pool jadi LangGraph pipeline (router/rewrite/retrieve/rerank/compose/safety).
12. Add fallback hierarchy (rerank → BM25-only → template → human escalation).
13. Add multi-query rewrite untuk colloquial query (paralel retrieve, fuse).
14. Add intent-conditional caching (FAQ stable intent → cache 15 min).
15. Add DeepEval red-team safety gate di CI.

**Backlog (post-v1.2)**:
- Fine-tune bge-m3 di triplet telco (mining dari log v1.2).
- GraphRAG eval untuk multi-hop billing question.
- Self-RAG style reflection untuk reduce unnecessary retrieval.
- Voyage rerank-2 A/B kalau bge-reranker plateau.

### 9.2 Cost Estimate Delta

Asumsi 100k query/day, current v1.1.7 baseline ~$0.008/query.

| Komponen | v1.1.7 | v1.2 estimate | Delta |
|---|---|---|---|
| LLM composer | $0.005 (Sonnet uncached) | $0.0015 (Sonnet + 90% cache) | -$0.0035 |
| Embedding API | $0.0005 (Cohere/OpenAI) | $0.0001 (self-host bge-m3) | -$0.0004 |
| Reranker | $0 (none) | $0.0003 (self-host bge-reranker) | +$0.0003 |
| Contextual prefix gen (Haiku, amortized one-time + delta) | $0 | $0.0001 | +$0.0001 |
| Vector DB | $0.0005 | $0.0005 | 0 |
| Infrastructure (Lightsail base + new GPU shape) | $0.0015 | $0.0020 | +$0.0005 |
| **Total / query** | **$0.008** | **$0.005** | **-37%** |

Faithfulness target naik dari ~0.82 ke ≥ 0.90, escalation rate target turun dari ~18% ke ≤ 12%.

### 9.3 Zero-Downtime Migration Strategy

**Pola dual-column embedding**:

1. Tambah kolom `embedding_v2` (bge-m3 1024-dim) parallel `embedding_v1`. `CREATE INDEX CONCURRENTLY` di pgvector atau Qdrant new collection.
2. Background job re-embed corpus, write ke `embedding_v2`. Rate-limit supaya tidak ganggu live read.
3. Feature flag di app layer: routing query ke v1 (default) atau v2.
4. Eval pipeline jalan paralel — golden set diuji di v1 dan v2 tiap commit.
5. Canary 5% → 25% → 50% → 100%, dengan rollback otomatis kalau metrik degrade.
6. Setelah 100% v2 stabil minimal 7 hari, drop `embedding_v1`.

**Untuk multi-stage migration** (embedding + reranker + LangGraph pipeline):
- Hindari big-bang. Ship satu komponen per minggu, observe metrik, baru lanjut.
- Maintain feature flag granular: `use_bge_m3`, `use_reranker`, `use_langgraph_pipeline`, `use_contextual_prefix`.

### 9.4 Risk Register

| Risk | Mitigasi |
|---|---|
| bge-m3 self-host latency spike | Pre-load model di startup, ONNX INT8, scale-out replica behind LB |
| Reranker overhead bikin p95 nembus 1.5s | Set hard timeout 100ms, fallback skip rerank |
| Contextual prefix bias / noise | A/B test sebelum full deploy, evaluasi faithfulness |
| Drift saat corpus refresh nightly | Schedule eval RAGAS post-ingest, alert kalau drop >5% |
| LLM API outage (Anthropic / GCP) | Multi-provider fallback (Claude primary, Gemini secondary) |
| PII leakage di log/embedding | DeepEval PII red-team di CI, mask MSISDN/NIK sebelum log |
| Vendor lock prompt caching | Abstract via Bedrock / OpenRouter adapter |

### 9.5 Checklist Pre-Production v1.2

- [ ] Golden eval set ≥ 200 query, coverage 15 intent.
- [ ] RAGAS baseline v1.1.7 documented (faithfulness, recall, precision).
- [ ] CI gate: faithfulness ≥ 0.85, recall ≥ 0.85, latency p95 ≤ 1.5s.
- [ ] Load test 10x peak QPS, validasi p99 ≤ 2s.
- [ ] Red-team DeepEval: bias, toxic, PII leak, jailbreak — semua pass.
- [ ] Rollback plan documented (feature flag toggle <5 menit).
- [ ] Cost dashboard live di Grafana.
- [ ] Runbook on-call: vector DB down, LLM API down, embedding lag.
- [ ] Backup index snapshot harian.

---

## 10. Referensi & Sumber

**Hybrid retrieval & fusion**
- Anthropic — Contextual Retrieval (Sep 2024) — https://www.anthropic.com/news/contextual-retrieval
- Elastic — Hybrid Search Guide — https://www.elastic.co/what-is/hybrid-search
- OpenSearch — RRF for hybrid search — https://opensearch.org/blog/introducing-reciprocal-rank-fusion-hybrid-search/
- ACM TOIS — Analysis of Fusion Functions — https://dl.acm.org/doi/10.1145/3596512
- TianPan blog — Hybrid Search Production (BM25 wins) — https://tianpan.co/blog/2026-04-12-hybrid-search-production-bm25-dense-embeddings

**Embedding models**
- BAAI/bge-m3 — https://huggingface.co/BAAI/bge-m3 ; arXiv:2402.03216 — https://arxiv.org/abs/2402.03216
- FlagEmbedding repo — https://github.com/FlagOpen/FlagEmbedding
- Jina Embeddings v3 — https://jina.ai/news/jina-embeddings-v3-a-frontier-multilingual-embedding-model/
- Late Chunking paper — arXiv:2409.04701 — https://arxiv.org/pdf/2409.04701
- Cohere Embed v3 — https://cohere.com/blog/introducing-embed-v3
- Voyage AI pricing — https://docs.voyageai.com/docs/pricing
- LazarusNLP Indonesian Sentence Embeddings — https://github.com/LazarusNLP/indonesian-sentence-embeddings
- IndoSBERT-large — https://huggingface.co/denaya/indoSBERT-large
- IndoLEM / IndoBERT — https://indolem.github.io/IndoBERT/
- MMTEB paper — arXiv:2502.13595 — https://arxiv.org/abs/2502.13595
- MTEB Leaderboard — https://huggingface.co/spaces/mteb/leaderboard

**Reranker**
- BAAI/bge-reranker-v2-m3 — https://huggingface.co/BAAI/bge-reranker-v2-m3
- Voyage rerank-2 — https://blog.voyageai.com/2024/09/30/rerank-2/
- Cohere models — https://docs.cohere.com/docs/models
- Reranker benchmark — https://agentset.ai/rerankers

**ANN index**
- OpenSearch HNSW hyperparameters — https://opensearch.org/blog/a-practical-guide-to-selecting-hnsw-hyperparameters/
- Milvus HNSW vs IVF — https://milvus.io/blog/understanding-ivf-vector-index-how-It-works-and-when-to-choose-it-over-hnsw.md
- Marqo HNSW recall — https://www.marqo.ai/blog/understanding-recall-in-hnsw-search
- Pinecone HNSW — https://www.pinecone.io/learn/series/faiss/hnsw/

**Vector DB benchmark**
- Qdrant Benchmarks — https://qdrant.tech/benchmarks/
- CallSphere DB Bench 2026 — https://callsphere.ai/blog/vector-database-benchmarks-2026-pgvector-qdrant-weaviate-milvus-lancedb
- VectorDBBench — https://github.com/zilliztech/VectorDBBench
- Vespa hybrid search docs — https://docs.vespa.ai/en/learn/tutorials/hybrid-search.html
- Vespa phased ranking — https://docs.vespa.ai/en/ranking/phased-ranking.html

**Evaluation**
- RAGAS docs — https://docs.ragas.io/en/stable/concepts/metrics/available_metrics/
- RAGAS paper — arXiv:2309.15217 — https://arxiv.org/abs/2309.15217
- DeepEval — https://deepeval.com / https://github.com/confident-ai/deepeval
- TruLens — https://www.trulens.org
- Atlan comparison — https://atlan.com/know/llm-evaluation-frameworks-compared/

**Multi-agent & framework**
- LangGraph — https://www.langchain.com/langgraph
- LlamaIndex Workflows — https://www.llamaindex.ai/workflows
- Agentic RAG survey — arXiv:2501.09136 — https://arxiv.org/abs/2501.09136
- RAGOps — arXiv:2506.03401 — https://arxiv.org/html/2506.03401v1

**Recent papers**
- GraphRAG vs RAG — arXiv:2502.11371 — https://arxiv.org/abs/2502.11371
- Self-RAG (Asai et al., 2023/2024) — https://arxiv.org/abs/2310.11511
- CRAG — https://arxiv.org/abs/2401.15884
- Jina-ColBERT-v2 — arXiv:2408.16672 — https://arxiv.org/abs/2408.16672
- ColBERTv2 — arXiv:2112.01488 — https://arxiv.org/abs/2112.01488

**Production / monitoring**
- Dextra Labs Production RAG 2025 — https://dextralabs.com/blog/production-rag-in-2025-evaluation-cicd-observability/
- StackPulsar Drift Detection — https://stackpulsar.com/blog/llm-model-drift-detection/
- Anthropic Prompt Caching — https://platform.claude.com/docs/en/build-with-claude/prompt-caching
- Google Cloud zero-downtime embedding migration — https://medium.com/google-cloud/migrating-vector-embeddings-in-production-without-downtime-8a0464af6f55

**Telco / Indonesian**
- Bitext Telco LLM dataset — https://huggingface.co/datasets/bitext/Bitext-telco-llm-chatbot-training-dataset
- IndoBERT vs RoBERTa intent — https://ejurnal.seminar-id.com/index.php/josh/article/view/6051
- Kadin Indonesia chatbot LSTM — https://itscience.org/jurnal/index.php/brilliance/article/download/7438/5295/37315
- Aubergine — Customer support RAG case — https://www.aubergine.co/insights/revolutionizing-customer-support-with-rag-powered-chatbot

---

## 11. Catatan Akhir

Dokumen ini adalah baseline arah teknis VIONA v1.2+. Beberapa keputusan masih perlu validasi empiris di golden eval set telco BIGNET-ID — terutama:

- Apakah Contextual Retrieval beneran kasih +30-49% di domain telco ID (klaim Anthropic dari general corpus).
- Apakah bge-m3 di-self-host bisa cover quality vs Cohere/Voyage paid API untuk colloquial ID.
- Optimal k1/b BM25 untuk corpus telco — bisa beda signifikan dengan default.
- Apakah refactor LangGraph 6-stage worth complexity vs current 5-agent pool homogen.

Saran: prioritize Phase 1 (eval framework) sebelum apa pun, supaya semua keputusan downstream punya metric basis, bukan vibe. Build untuk measurable, bukan untuk indah arsitekturnya.

Sumber-sumber di Section 10 sudah diakses 2026-05-13. Jika dipakai di paper / dokumen formal, harap re-verify link masih live dan versi paper terbaru.

---
*End of document — 10-viona-rag-optimization.md*
