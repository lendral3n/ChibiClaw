# 26 — Phase 5: Vision Tools

**Durasi:** 2.5 minggu
**Tujuan:** Screenshot + visual grounding sebagai fallback saat Accessibility gagal (TikTok, WA, IG, Shopee, Tokped).

---

## Outcome

- MediaProjection permission once-off + persistent token
- MiniCPM-V 4.6 1.3B Q4 GGUF loaded via llama.cpp Android atau ONNX Runtime
- Tools: `vision_tap(query)`, `vision_describe(query)`, `vision_extract_text(region)`
- Tool description sebutkan known fail accessibility → LLM yang adapt
- World query tambahan: `world_get_installed_apps`, `world_get_location`, `world_get_schedule`
- Cloud vision fallback (Gemini Flash multimodal) via `escalate_to_cloud`

**Test target:** "Balas WA Budi 'OK'" → LLM coba a11y_click search di WA, gagal SELECTOR_NOT_FOUND, switch ke vision_tap("search icon WhatsApp"), sukses balas.

---

## Deliverable per Minggu

### Minggu 1: MediaProjection + screenshot

**M1.1: MediaProjection setup**
- `vision/projection/MediaProjectionManager.kt`
- Request MediaProjection permission via Activity (once)
- Persistent token via foreground service notification (Android 14+)
- VirtualDisplay + ImageReader untuk capture screenshot

**M1.2: Screenshot capture**
- `vision/screenshot/ScreenCapture.kt`
- Capture single frame on-demand
- Format: RGBA_8888 → Bitmap → byte[] PNG/JPEG
- Resolution: target Snapdragon 8 Elite Gen 5 ~1080x2400, downsample untuk LLM input (1024 max dim)

**M1.3: Permission wizard**
- Setup wizard step "Aktifkan vision (screenshot fallback)"
- Disclosure: "Fuu screenshot layar saat butuh fallback. Layar tidak disimpan, cuma diproses lokal."
- Request once, save token persistent

### Minggu 2: MiniCPM-V model + inference

**M2.1: Model download**
- MiniCPM-V 4.6 1.3B Q4 GGUF dari Hugging Face (`openbmb/MiniCPM-V-4_6-int4`)
- ~800MB file
- Download on first vision use (lazy), progress UI
- Verify checksum

**M2.2: llama.cpp Android setup**
- Add llama.cpp JNI wrapper (build .so untuk arm64-v8a)
- `vision/llm/MiniCPMVInference.kt`
- Init model load (slow first time, ~30s)
- inference(prompt, image) → text output

**M2.3: Visual grounding prompt**
- System prompt untuk grounding: "Given screenshot dan query, return coordinates atau description"
- Output format constrained: `{x: int, y: int, confidence: float, label: string}`

**M2.4: Performance optimization**
- ImageProxy → bitmap conversion async
- Resize image to 1024 max dim
- Cache last screenshot kalau scene tidak berubah (avoid recapture per tool call dalam window 1s)

### Minggu 0.5 (3): Vision tools + integration

**M3.1: vision_tap tool**
- `agent/tools/impl/VisionTapTool.kt`
- Args: query (string), app_package (optional)
- Flow: capture screenshot → MiniCPM-V grounding → parse coordinates → dispatch tap via Accessibility Service (`AccessibilityNodeInfo.performAction` or gesture API)

**M3.2: vision_describe tool**
- `agent/tools/impl/VisionDescribeTool.kt`
- Args: query (e.g. "what's visible on screen", "extract text top region")
- Capture → MiniCPM-V describe → return text

**M3.3: vision_extract_text tool**
- `agent/tools/impl/VisionExtractTextTool.kt`
- Args: region (x, y, w, h)
- OCR via ML Kit Text Recognition v2 (sudah ada di build.gradle) atau MiniCPM-V extract

**M3.4: World query tools**
- `agent/tools/impl/WorldGetInstalledAppsTool.kt`: PackageManager.queryIntentActivities
- `agent/tools/impl/WorldGetLocationTool.kt`: FusedLocationProviderClient (perm runtime)
- `agent/tools/impl/WorldGetScheduleTool.kt`: CalendarContract.Events query

**M3.5: Cloud vision fallback**
- Update escalate_to_cloud untuk vision context: pass image bytes ke Gemini Flash multimodal (sudah support di GeminiFreeAdapter Phase 4)
- LLM bisa emit "kalau vision local kurang, escalate"

---

## Modul Phase 5

```
app/src/main/java/com/chibiclaw/vision/
├── projection/
│   ├── MediaProjectionManager.kt
│   └── ProjectionTokenStore.kt
├── screenshot/
│   ├── ScreenCapture.kt
│   └── ImageProcessor.kt
├── llm/
│   ├── MiniCPMVInference.kt
│   └── VisionPromptBuilder.kt
└── ocr/
    └── MlKitOcr.kt

app/src/main/java/com/chibiclaw/agent/tools/impl/
├── VisionTapTool.kt
├── VisionDescribeTool.kt
├── VisionExtractTextTool.kt
├── WorldGetInstalledAppsTool.kt
├── WorldGetLocationTool.kt
└── WorldGetScheduleTool.kt
```

---

## Dependencies tambahan

```kotlin
dependencies {
    // llama.cpp Android (JNI custom build atau llama.cpp.android library)
    implementation(files("libs/llama-cpp-android.aar"))
    
    // ML Kit OCR
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")  // optional
    
    // FusedLocation
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
```

---

## MediaProjection Permission Persistence

Android 14+: MediaProjection token tidak persistent across activity. Workaround:

```kotlin
class ProjectionTokenStore @Inject constructor(
    private val context: Context,
) {
    private var mediaProjection: MediaProjection? = null
    private var resultCode: Int = 0
    private var resultData: Intent? = null
    
    fun saveToken(code: Int, data: Intent) {
        resultCode = code
        resultData = data
        // Project recreate kalau ChibiService restart
    }
    
    fun recreateProjection(): MediaProjection? {
        val data = resultData ?: return null
        val mpm = context.getSystemService(MediaProjectionManager::class.java)
        return mpm.getMediaProjection(resultCode, data).also {
            mediaProjection = it
        }
    }
}
```

Token expire kalau ChibiService killed → user re-tap re-grant. Lifecycle observer detect token loss + show notif.

---

## Vision Prompt untuk MiniCPM-V

```
System: You are a visual grounding assistant. Given an Android screenshot 
and a query, identify the target element and return:
- Coordinates (x, y) in pixel
- Confidence (0.0-1.0)
- Label/description

Output format: JSON
{
  "x": 945,
  "y": 280,
  "confidence": 0.9,
  "label": "Search icon top right"
}

User query: "search icon di pojok kanan atas WhatsApp"
[Image attached]
```

LLM grounding output parsed → koordinat → tap via gesture API.

---

## Risk

| Risk | Mitigasi |
|------|----------|
| MediaProjection permission revoked when ChibiService killed | Detect token loss → notif user re-grant; lifecycle observer |
| MiniCPM-V model size (~800MB) + slow load | Lazy load, progress UI, fallback ke cloud vision (Gemini Flash multimodal) saat local unavailable |
| Vision grounding accuracy varies per app UI | Tool description honest about limitations; LLM observe error → escalate cloud |
| Snapdragon NPU not utilized by llama.cpp | Test ONNX Runtime path (might be better NPU support) |
| Screenshot include sensitive data (passwords) | Audit log only summary; no screenshot persistent; warning di privacy notice |
| Tap coordinate wrong (off by pixel scale) | Verify screen density factor; test variety device |

---

## Performance Target

| Metric | Target |
|--------|--------|
| MediaProjection capture | <100ms per frame |
| MiniCPM-V 4.6 load (cold) | <30s |
| MiniCPM-V inference (warm, 1024px image) | <3s |
| vision_tap end-to-end (capture → grounding → dispatch tap) | <4s |
| ML Kit OCR | <500ms typical |

---

## Definition of Done

- [ ] MediaProjection setup wizard works, token saved
- [ ] Screenshot capture via service works (test capture 10x, save PNG, verify visual)
- [ ] MiniCPM-V 4.6 loaded, inference returns coordinates
- [ ] vision_tap test di Settings app: "tap pada About" → tap koordinat correct
- [ ] vision_tap test di TikTok: tap search button works (saat a11y gagal)
- [ ] vision_describe return reasonable text untuk current screen
- [ ] vision_extract_text OCR notification text
- [ ] world_get_installed_apps return list
- [ ] world_get_location works (with consent)
- [ ] world_get_schedule query calendar (with consent)
- [ ] LLM observation: error a11y → adapt ke vision in next iteration (test scenario "balas WA")

---

## Next: [27-phase-6-initiative.md](27-phase-6-initiative.md)
