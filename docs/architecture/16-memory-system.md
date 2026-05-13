# 16 — Memory System

Persistent knowledge tentang user, kontak, habit, fact, preference. Vector RAG + simple JSON KB (ADR-009).

---

## Tujuan

Fuu butuh kenal user supaya respons relevan:
- "Kirim ke Mama" → Fuu tahu kontak Mama (number, preferred channel)
- "Pagi seperti biasa" → Fuu tahu habit morning routine
- "Suara lebih pelan" → Fuu apply preference voice setting

Tanpa memory persistent, setiap session kosong, harus tanya ulang. Itu friction yang membunuh value agent.

---

## Schema Recap

Lihat juga [11-data-model.md#memoryrecord](11-data-model.md#memoryrecord).

```kotlin
@Entity(tableName = "memory_record")
data class MemoryRecordEntity(
    @PrimaryKey val id: String,
    val category: MemoryCategory,
    val key: String,                 // unique semantic key
    val valueJson: String,           // structured payload
    val embeddingBlob: ByteArray,    // 384-dim float32
    val createdAt: Instant,
    val lastAccessedAt: Instant,
    val accessCount: Int = 0,
    val confidence: Float = 1.0f,
    val ttlUntil: Instant? = null,
    val sourceTaskId: String? = null,
)

enum class MemoryCategory {
    USER_PROFILE, CONTACT, HABIT, FACT, PREFERENCE
}
```

---

## Embedding Provider

**Model:** `intfloat/multilingual-e5-small` ONNX INT8.

- 384-dim output
- ~50MB quantized
- Support 94 bahasa termasuk Indonesia
- Latency Snapdragon 8 Elite Gen 5: ~50ms per encode (256 token max)

```kotlin
@Singleton
class EmbeddingProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var ortSession: OrtSession? = null
    private val tokenizer: HuggingfaceTokenizer by lazy { initTokenizer() }
    
    suspend fun init() {
        val env = OrtEnvironment.getEnvironment()
        val modelBytes = context.assets.open("models/e5_small_q8.onnx").readBytes()
        ortSession = env.createSession(modelBytes)
    }
    
    suspend fun encode(text: String): FloatArray {
        // E5 family: prefix dengan "passage: " atau "query: "
        val prefixed = "passage: $text"  // gunakan "query: " saat search
        val tokens = tokenizer.encode(prefixed, maxLength = 256)
        
        val inputIds = LongArray(tokens.ids.size) { tokens.ids[it].toLong() }
        val attentionMask = LongArray(tokens.ids.size) { if (tokens.attentionMask[it] == 1) 1L else 0L }
        
        val output = ortSession?.run(mapOf(
            "input_ids" to OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), longArrayOf(1, tokens.ids.size.toLong())),
            "attention_mask" to OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), longArrayOf(1, tokens.ids.size.toLong())),
        ))
        
        // Mean pool atas attention mask
        val lastHidden = (output?.get(0)?.value as Array<Array<FloatArray>>)[0]  // [seq_len, 384]
        val pooled = meanPool(lastHidden, attentionMask)
        return normalize(pooled)  // L2 normalize untuk cosine similarity
    }
    
    suspend fun encodeQuery(query: String): FloatArray {
        val prefixed = "query: $query"
        // ... same as encode but with query prefix
    }
    
    private fun meanPool(hidden: Array<FloatArray>, mask: LongArray): FloatArray {
        val pooled = FloatArray(hidden[0].size)
        var maskedCount = 0
        for (i in hidden.indices) {
            if (mask[i] == 1L) {
                maskedCount++
                for (j in pooled.indices) pooled[j] += hidden[i][j]
            }
        }
        return pooled.map { it / maskedCount }.toFloatArray()
    }
    
    private fun normalize(vec: FloatArray): FloatArray {
        val norm = sqrt(vec.sumOf { (it * it).toDouble() }).toFloat()
        return vec.map { it / norm }.toFloatArray()
    }
}
```

---

## MemoryStore Implementation

```kotlin
@Singleton
class MemoryStore @Inject constructor(
    private val dao: MemoryDao,
    private val embedder: EmbeddingProvider,
) {
    
    suspend fun remember(
        category: MemoryCategory,
        key: String,
        value: JsonElement,
        confidence: Float = 1.0f,
        ttlDays: Int? = null,
        sourceTaskId: String? = null,
    ) {
        val textForEmbedding = buildTextForEmbedding(category, key, value)
        val embedding = embedder.encode(textForEmbedding)
        
        val record = MemoryRecordEntity(
            id = UUID.randomUUID().toString(),
            category = category,
            key = key,
            valueJson = value.toString(),
            embeddingBlob = embedding.toEmbeddingBlob(),
            createdAt = Instant.now(),
            lastAccessedAt = Instant.now(),
            confidence = confidence,
            ttlUntil = ttlDays?.let { Instant.now().plus(Duration.ofDays(it.toLong())) },
            sourceTaskId = sourceTaskId,
        )
        
        dao.upsert(record)
    }
    
    suspend fun recall(
        query: String,
        category: MemoryCategory? = null,
        topK: Int = 5,
        minSimilarity: Float = 0.3f,
    ): List<MemoryHit> {
        val queryEmbedding = embedder.encodeQuery(query)
        
        // Load candidates (filter by category if specified)
        val candidates = if (category != null) {
            dao.listByCategory(category)
        } else {
            dao.listAll(limit = 1000)  // cap untuk perf
        }
        
        // Compute cosine similarity
        val scored = candidates.map { record ->
            val embedding = record.embeddingBlob.toEmbeddingArray()
            val similarity = cosineSimilarity(queryEmbedding, embedding)
            MemoryHit(record, similarity)
        }
        
        // Top-K above threshold
        val results = scored
            .filter { it.similarity >= minSimilarity }
            .sortedByDescending { it.similarity }
            .take(topK)
        
        // Update access tracking
        results.forEach { hit ->
            dao.touch(hit.record.id, accessCount = hit.record.accessCount + 1)
        }
        
        return results
    }
    
    suspend fun listByCategory(category: MemoryCategory): List<MemoryRecordEntity> {
        return dao.listByCategory(category)
    }
    
    suspend fun forget(idOrKey: String) {
        if (idOrKey.matches(UUID_REGEX)) {
            dao.deleteById(idOrKey)
        } else {
            dao.deleteByKey(idOrKey)
        }
    }
    
    suspend fun cleanup() {
        // TTL-based delete
        dao.deleteExpired(Instant.now())
        
        // LRU evict kalau >5000 records
        val total = dao.count()
        if (total > 5000) {
            dao.deleteOldestAccessed(limit = total - 4500)
        }
    }
    
    private fun buildTextForEmbedding(category: MemoryCategory, key: String, value: JsonElement): String {
        // Compose text representasi untuk embedding
        return "${category.name.lowercase()}: $key — ${value.toString().take(200)}"
    }
}

data class MemoryHit(
    val record: MemoryRecordEntity,
    val similarity: Float,
)
```

---

## Cosine Similarity

```kotlin
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size)
    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }
    return dot / (sqrt(normA) * sqrt(normB) + 1e-8f)
}
```

Saat embedding sudah L2-normalized (dilakukan di `normalize()`), cosine sim = dot product. Optimasi:

```kotlin
fun dotProduct(a: FloatArray, b: FloatArray): Float {
    var sum = 0f
    for (i in a.indices) sum += a[i] * b[i]
    return sum
}
```

---

## Tool Integration

LLM access memory via 4 tools (lihat [13-tool-catalog.md](13-tool-catalog.md)):

- `memory_remember(category, key, value, confidence?, ttl_days?)` — save
- `memory_recall(query, category?, top_k?, min_similarity?)` — semantic search
- `memory_forget(id | key)` — delete
- `memory_list_by_category(category)` — list all dalam kategori

LLM decide kapan call. Code tidak auto-extract entity dari conversation.

---

## Memory Lifecycle

```
Task running:
  LLM observe useful fact (e.g. "user said his work address is X")
  ↓
  LLM emit ToolCall:
    memory_remember(category=FACT, key="work_address", value={...}, confidence=0.9, ttl_days=null)
  ↓
  MemoryStore embed + persist

Later task:
  User: "berapa lama ke kantor?"
  ↓
  LLM context include memory snippets via auto-include (kalau pakai recall di context build)
  OR LLM emit ToolCall: memory_recall(query="kantor address", category=FACT)
  ↓
  Get hit "work_address" → LLM use untuk planning task
```

---

## Auto-Include vs Tool-Recall

Saat AgentRuntime build context untuk LLM call, ada 2 strategi memory injection:

### Strategy A: Auto-Include Top-K saat context build

```kotlin
fun buildContext(task: TaskEntity): AgentPrompt {
    val memorySnippets = memoryStore.recall(
        query = task.goal,
        topK = 5,
        minSimilarity = 0.3f,
    )
    // ... include di prompt
}
```

- Pro: LLM langsung punya memory relevant tanpa explicit call
- Con: cost embedding setiap task; mungkin include yang tidak useful

### Strategy B: Tool-Only (LLM explicit recall)

```kotlin
fun buildContext(task: TaskEntity): AgentPrompt {
    // tidak include memory di context default
    // LLM emit ToolCall memory_recall kalau perlu
}
```

- Pro: LLM yang putus relevansi, hemat token saat tidak perlu
- Con: 1 extra round-trip per task butuh memory

### Strategy C: Hybrid (default)

Auto-include hanya top-3 dengan similarity > 0.5 (high confidence). Untuk yang less relevant, LLM bisa explicit call recall.

```kotlin
fun buildContext(task: TaskEntity): AgentPrompt {
    val memorySnippets = memoryStore.recall(
        query = task.goal,
        topK = 3,
        minSimilarity = 0.5f,  // high confidence only
    )
    // include hanya kalau ada match cukup kuat
}
```

**Default ChibiClaw v4: Hybrid (C)**. ADR-003.

---

## Memory Ingestion Workflow

LLM-driven, no auto-extract heuristic.

Saat task complete, LLM bisa optionally emit memory write:

```
Task: "kirim WA ke Budi soal meeting"
... agent loop berhasil ...
LLM iteration final:
{
  "thought": "Task selesai. Aku perhatikan user sering chat Budi soal kerjaan. Sebaiknya kuingat.",
  "tool_calls": [
    {"tool": "memory_remember", "args": {
      "category": "CONTACT",
      "key": "contact.budi",
      "value": {
        "name": "Budi",
        "relation": "kerja",
        "preferred_channel": "whatsapp",
        "context_tags": ["meeting_reguler"]
      },
      "confidence": 0.7
    }}
  ],
  "next": "done",
  "summary": "Pesan terkirim ke Budi"
}
```

LLM yang putus relevansi. Code tidak auto-detect "ini fact penting".

---

## Confidence + Re-Confirm

`confidence` di MemoryRecord 0.0-1.0. Initial confidence dari LLM judgement.

Re-confirm via repetition: kalau LLM observe fakta yang sama lagi (key match, value similar), confidence increase.

```kotlin
suspend fun reinforce(key: String, valueMatch: Boolean) {
    val record = dao.getByKey(key) ?: return
    val newConfidence = if (valueMatch) {
        (record.confidence + 0.1f).coerceAtMost(1.0f)
    } else {
        (record.confidence - 0.2f).coerceAtLeast(0.0f)
    }
    dao.updateConfidence(record.id, newConfidence)
}
```

Threshold untuk hard-delete: kalau confidence < 0.2 selama 30 hari, otomatis forget.

---

## Category-Specific Patterns

### USER_PROFILE

Sparse, jarang berubah. Keys: `name`, `pronoun`, `timezone`, `language_primary`, `role`, `birthdate?`.

LLM bertanya satu kali di onboarding (kalau user tidak skip), simpan, tidak tanya lagi.

### CONTACT

Per orang yang user sering interact. Key: `contact.{first_name_lowercase}`.

Value:
```json
{
  "display_name": "Budi Santoso",
  "phone": "+62...",
  "email": "budi@...",
  "relation": "kerja | keluarga | teman | unknown",
  "preferred_channel": "whatsapp | sms | call | email",
  "tags": ["meeting", "klien"],
  "notes": "freeform"
}
```

Bisa multiple kontak dengan nama mirip (Budi 1, Budi 2). LLM disambiguate kalau ada konflik.

### HABIT

Pattern yang user lakukan secara regular. Key: `habit.{name}`.

Value:
```json
{
  "title": "Morning routine",
  "frequency": "daily | weekly | monthly",
  "time_window": "06:00-07:30",
  "actions": ["check_email", "olahraga_15min", "kopi"],
  "last_observed": "2026-05-13T..."
}
```

LLM bisa infer dari command history pattern (Phase 7 maturity).

### FACT

Standalone fakta yang user kasih tahu. Key: `fact.{topic}`.

Value:
```json
{
  "subject": "kantor_alamat",
  "value": "Gedung X, Jl. Sudirman No. 123, Jakarta",
  "verified": true,
  "source": "user_provided"
}
```

### PREFERENCE

Pengaturan personal. Key: `preference.{scope}`.

Value:
```json
{
  "scope": "voice",
  "settings": {
    "speed": "normal",
    "emotion_intensity": "medium",
    "language_mode": "id_native"
  }
}
```

Atau `preference.notification`, `preference.ui_theme`, dll.

---

## Privacy & Compliance

- Memory **tidak sync ke cloud**. Local-only Room + SQLCipher.
- Saat LLM call cloud (Gemini/Claude/GPT), memory snippet relevant dikirim sebagai context **per-call** (transient).
- User bisa export memory ke JSON via Settings → Privacy → Export Memory.
- User bisa erase semua memory via Settings → Privacy → Erase Memory.
- Audit log setiap `memory_remember` / `memory_recall` (action: MEMORY_WRITE / MEMORY_READ).

Detail compliance: [19-compliance-privacy.md](19-compliance-privacy.md).

---

## Future Enhancements (Phase 7+)

- **Graph relations** ringan: `(contact.budi)-[relation: kerja]-(fact.kantor_alamat)`. Tidak full Neo4j, tapi adjacency table simple.
- **Pattern miner**: WorkManager periodic scan command_history → infer habit + auto-create memory dengan confidence rendah, user approve.
- **bge-m3 upgrade**: kalau e5-small accuracy jadi bottleneck, swap ke bge-m3 (~600MB Q4, 1024-dim, multilingual + sparse hybrid).

---

## Next Files

- Standing instructions: [17-standing-instructions.md](17-standing-instructions.md)
- Phase 7 memory maturity detail: [28-phase-7-memory.md](28-phase-7-memory.md)
