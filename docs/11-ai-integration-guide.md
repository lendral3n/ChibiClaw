# 11 — AI Integration Guide (LiteRT-LM)

> Dokumen ini adalah panduan teknis UTAMA untuk developer yang akan mengimplementasikan
> Gemma on-device di ChibiClaw. Baca ini SEBELUM menulis kode apapun di package `ai/`.

## Keputusan Arsitektural

### GUNAKAN: LiteRT-LM library langsung di ChibiClaw
```gradle
dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
}
```

### JANGAN: Berkomunikasi dengan Edge Gallery app via IPC
### JANGAN: Fork Edge Gallery source code

### REFERENSI: Edge Gallery source code untuk pattern implementasi
- Repository: https://github.com/google-ai-edge/gallery
- Function Calling Guide: /Function_Calling_Guide.md
- Mobile Actions pattern: /Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/mobileactions/

## LiteRT-LM Core Concepts

### 1. Engine (Entry Point)
Engine adalah entry point utama. Inisialisasi dengan model path dan backend config.
HARUS dilakukan di background thread (heavy operation).

```kotlin
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Backend

// Inisialisasi engine
val engineConfig = EngineConfig(
    modelPath = "/path/to/gemma-4-E4B-it.litertlm",
    backend = Backend.GPU()  // atau Backend.CPU(), Backend.NPU(nativeLibraryDir)
)
val engine = Engine(engineConfig)

// PENTING: selalu close engine saat tidak dipakai untuk free resources
engine.close()
```

### 2. Conversation (Multi-turn Chat)
Conversation maintain stateful history dan support streaming via Kotlin Flow.

```kotlin
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig

val conversationConfig = ConversationConfig(
    systemInstruction = """
        Kamu adalah Fuu, asisten virtual Android.
        Selalu gunakan intent_send sebagai pilihan pertama.
        Gunakan ui_interact hanya jika intent tidak tersedia.
    """.trimIndent(),
    samplerConfig = SamplerConfig(
        temperature = 0.7f,
        topK = 40,
        topP = 0.95f
    ),
    automaticToolCalling = true,  // KRITIS: enable auto tool calling
    tools = listOf(chibiClawToolset)  // toolset kita
)

val conversation = engine.createConversation(conversationConfig)

// Kirim pesan dan stream response
conversation.sendMessage("Balas WA Budi bilang Otw").collect { chunk ->
    // Handle streaming response
    println(chunk.text)
}
```

### 3. Tool Use / Function Calling (PALING PENTING)
Ini jantung ChibiClaw — cara Gemma memanggil fungsi Android.

Pattern dari Edge Gallery yang HARUS diadopsi:

#### Step A: Define Tool dengan @Tool dan @ToolParam annotations

```kotlin
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.Toolset

class ChibiClawTools(
    val onActionRequested: (ChibiAction) -> Unit
) : Toolset {

    @Tool(description = "Kirim Intent API ke app Android. Gunakan ini sebagai pilihan PERTAMA.")
    fun intentSend(
        @ToolParam(description = "Intent action: ACTION_VIEW, ACTION_SEND, ACTION_CALL, dll") action: String,
        @ToolParam(description = "Target URI atau deep link") uri: String,
        @ToolParam(description = "Target package name (optional)") packageName: String = ""
    ): Map<String, String> {
        onActionRequested(IntentAction(action = action, uri = uri, packageName = packageName))
        return mapOf("result" to "intent_fired")
    }

    @Tool(description = "Query data dari Content Provider Android (kontak, SMS, kalender)")
    fun contentQuery(
        @ToolParam(description = "Provider: contacts, sms, calendar, media") provider: String,
        @ToolParam(description = "Search query") query: String
    ): Map<String, String> {
        val result = onActionRequested(ContentQueryAction(provider = provider, query = query))
        return mapOf("result" to result.toString())
    }

    @Tool(description = "Interaksi UI via Accessibility Service. Gunakan HANYA jika Intent tidak tersedia.")
    fun uiInteract(
        @ToolParam(description = "Action: click, type, scroll, swipe, back, home") action: String,
        @ToolParam(description = "Target: node text, content description, atau resource ID") target: String,
        @ToolParam(description = "Teks yang diketik (untuk action 'type')") text: String = ""
    ): Map<String, String> {
        onActionRequested(UiInteractAction(action = action, target = target, text = text))
        return mapOf("result" to "ui_action_performed")
    }

    @Tool(description = "Scan layar untuk mendapatkan UI map. Panggil sebelum uiInteract.")
    fun scanUi(
        @ToolParam(description = "Method: accessibility atau screenshot") method: String = "accessibility"
    ): Map<String, String> {
        val uiMap = onActionRequested(ScanUiAction(method = method))
        return mapOf("ui_map" to uiMap.toString())
    }

    @Tool(description = "Cari informasi di long-term memory")
    fun memoryQuery(
        @ToolParam(description = "Search query") query: String,
        @ToolParam(description = "Scope: contacts, history, patterns, all") scope: String = "all"
    ): Map<String, String> {
        val result = onActionRequested(MemoryQueryAction(query = query, scope = scope))
        return mapOf("result" to result.toString())
    }

    @Tool(description = "Tunggu beberapa detik sebelum aksi berikutnya")
    fun wait(
        @ToolParam(description = "Jumlah detik") seconds: Int
    ): Map<String, String> {
        Thread.sleep(seconds * 1000L)
        return mapOf("result" to "waited_${seconds}s")
    }

    @Tool(description = "Minta konfirmasi atau informasi dari user")
    fun askUser(
        @ToolParam(description = "Pertanyaan untuk user") question: String
    ): Map<String, String> {
        onActionRequested(AskUserAction(question = question))
        return mapOf("result" to "waiting_user_response")
    }

    @Tool(description = "Laporkan hasil aksi ke user")
    fun report(
        @ToolParam(description = "Pesan laporan") message: String,
        @ToolParam(description = "Status: success, partial, failed") status: String = "success"
    ): Map<String, String> {
        onActionRequested(ReportAction(message = message, status = status))
        return mapOf("result" to "reported")
    }
}
```

#### Step B: Handle Action di Execution Engine

```kotlin
// Di ExecutionRouter.kt
fun handleAction(action: ChibiAction, context: Context): String {
    return when (action) {
        is IntentAction -> {
            // Tier 1: Intent API
            val intent = buildIntent(action)
            context.startActivity(intent)
            "intent_success"
        }
        is ContentQueryAction -> {
            // Tier 2: Content Provider
            queryContentProvider(action, context)
        }
        is UiInteractAction -> {
            // Tier 3: Accessibility
            accessibilityExecutor.perform(action)
        }
        is ScanUiAction -> {
            // Perception module
            perceptionRouter.scan(action.method)
        }
        // ... etc
    }
}
```

#### Step C: automaticToolCalling Flow

Saat `automaticToolCalling = true` di ConversationConfig:
1. Gemma menerima prompt user
2. Gemma menghasilkan function call (misal: `intentSend(action="ACTION_VIEW", uri="wa.me/...")`)
3. LiteRT-LM otomatis memanggil method Kotlin yang sesuai via reflection
4. Return value dikirim kembali ke Gemma sebagai tool result
5. Gemma bisa memanggil function lain (chain) atau menghasilkan text response final
6. Loop ini berjalan otomatis sampai Gemma selesai

INI KRUSIAL: Ini berarti ChibiClaw TIDAK perlu manually parse JSON function call.
LiteRT-LM sudah handle semuanya. Kita hanya perlu define @Tool methods.

### 4. Model Format

LiteRT-LM menggunakan format `.litertlm` (bukan GGUF, bukan SafeTensors).
Model tersedia di HuggingFace LiteRT Community:
- `litert-community/gemma-4-E2B-it-litert-lm`
- `litert-community/gemma-4-E4B-it-litert-lm`

### 5. Backend Options

```kotlin
// CPU (universal, paling lambat)
Backend.CPU()

// GPU (recommended, XNNPack + ML Drift acceleration)
Backend.GPU()

// NPU (fastest, tapi hanya di device tertentu)
Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
```

Strategy: coba GPU dulu, fallback ke CPU jika tidak support.

### 6. Vision Input (Multimodal)

```kotlin
// Kirim gambar (screenshot) ke Gemma untuk analisis
val imageContent = Contents.image(screenshotBitmap)
val textContent = Contents.text("Apa saja UI elements yang terlihat di screenshot ini?")

conversation.sendMessage(listOf(imageContent, textContent)).collect { chunk ->
    // Handle vision response
}
```

### 7. Error Handling

```kotlin
try {
    val engine = Engine(engineConfig)
    // ...
} catch (e: LiteRtLmJniException) {
    // Error dari native layer (model corrupt, out of memory, dll)
    Log.e("ChibiClaw", "LiteRT-LM native error: ${e.message}")
} catch (e: IllegalStateException) {
    // Lifecycle error (engine sudah di-close, dll)
    Log.e("ChibiClaw", "Engine lifecycle error: ${e.message}")
}
```

### 8. Benchmark

```kotlin
import com.google.ai.edge.litertlm.benchmark

val benchmarkResult = benchmark(engineConfig, promptText = "Hello")
println("Init time: ${benchmarkResult.initTimeInSecond}s")
println("Time to first token: ${benchmarkResult.timeToFirstTokenInSecond}s")
println("Prefill speed: ${benchmarkResult.lastPrefillTokensPerSecond} tok/s")
println("Decode speed: ${benchmarkResult.lastDecodeTokensPerSecond} tok/s")
```

## Model Download Strategy

### Opsi A: Bundle di APK (TIDAK recommended)
- Model E4B = 2.5GB → APK terlalu besar
- Play Store limit 150MB (meski ChibiClaw tidak di Play Store)

### Opsi B: Download saat first-run dari HuggingFace ✅
- User download model setelah install app
- Progress bar di Setup Wizard
- Simpan di app internal storage (`context.filesDir`)
- Referensi: lihat cara Edge Gallery download model dari HuggingFace

### Opsi C: Import model yang sudah ada
- Jika user sudah punya model dari Edge Gallery atau Ollama
- Provide "Import Model" button di Settings
- Scan file system untuk `.litertlm` files

Recommended: Opsi B sebagai primary, Opsi C sebagai alternative.

## Mapping ke ChibiClaw Modules

| LiteRT-LM Component | ChibiClaw Module | File |
|---------------------|-----------------|------|
| `Engine` + `EngineConfig` | `ai/GemmaEngineManager.kt` | Lifecycle, load/unload |
| `Conversation` + `ConversationConfig` | `ai/GemmaInference.kt` | Send message, stream |
| `@Tool` + `Toolset` | `ai/ChibiClawTools.kt` | Tool definitions |
| `Backend` selection | `ai/ModelRouter.kt` | GPU/CPU/NPU routing |
| `Message` + `Contents` | `ai/ContextAssembler.kt` | Build prompt |
| `BenchmarkInfo` | `ui/settings/AiSettingsScreen.kt` | Show performance |
| Tool result → Action | `executor/ExecutionRouter.kt` | Handle tool calls |

## Hal yang TIDAK perlu kita implementasi sendiri

Karena LiteRT-LM sudah handle:
- ❌ JSON parsing function call output (otomatis via reflection)
- ❌ Chat template / tokenization (handled by engine)
- ❌ Model quantization (model sudah pre-quantized di .litertlm format)
- ❌ GPU memory management (handled by backend)
- ❌ Constrained decoding (built into tool calling system)
- ❌ Multi-turn history management (handled by Conversation)

## Hal yang HARUS kita implementasi sendiri

- ✅ Tool definitions (@Tool methods) yang map ke Android APIs
- ✅ Model download & storage management
- ✅ Engine lifecycle (load/unload berdasarkan usage)
- ✅ System prompt engineering (persona + skill definitions)
- ✅ Dual model routing (E2B untuk classify, E4B untuk plan)
- ✅ Vision input pipeline (screenshot → bitmap → Gemma)
- ✅ Error recovery saat tool execution gagal
