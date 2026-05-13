# 15 — Voice Pipeline

Wake/STT/TTS/Emotion. MVP skip wake word (ADR-006), pakai manual button overlay. Bisa di-tambah Phase 10+.

---

## Pipeline End-to-End

```
[USER TAP MIC ICON DI OVERLAY]
        ↓
AudioRecord acquire mic (16kHz mono PCM)
        ↓
Audio stream multiplex:
   ├──→ WhisperStreaming (STT)
   └──→ Wav2Small (emotion audio)
        ↓
[STT result: text]
        ↓
ConversationManager.handleUserInput(text, emotionVAD)
        ↓
... [agent loop, see 12-agent-loop.md] ...
        ↓
LLM response: text + emotion tag
        ↓
[Text + emotion → TTS]
        ↓
ResponseComposer build (emotion mapping + pre-recorded loop)
        ↓
ElevenLabs streaming API
        ↓
AudioTrack playback
```

---

## STT — Whisper.cpp Small Q5_1

**Lib:** `sherpa-onnx` (Android, Kotlin binding) atau `whisper.cpp` JNI wrapper.

**Model:** `ggml-small.bin` quantized Q5_1 (~245MB).

**Setup:**
```kotlin
class WhisperStt @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var recognizer: OnlineRecognizer? = null
    
    suspend fun init() {
        val modelPath = ensureModelDownloaded()
        val config = OnlineRecognizerConfig.builder()
            .setModelConfig(OnlineModelConfig.builder()
                .setWhisperModel(WhisperModelConfig.builder()
                    .setEncoder("$modelPath/encoder.onnx")
                    .setDecoder("$modelPath/decoder.onnx")
                    .setLanguage("id")
                    .setTask("transcribe")
                    .build())
                .build())
            .setEndpointConfig(OnlineEndpointConfig.default())
            .build()
        recognizer = OnlineRecognizer(config)
    }
    
    fun streamingTranscribe(samples: FloatArray): Flow<SttResult> = flow {
        recognizer?.acceptWaveform(16000, samples)
        while (recognizer?.isReady == true) {
            recognizer?.decode()
            val partial = recognizer?.getText() ?: ""
            emit(SttResult.Partial(partial))
        }
        if (recognizer?.isEndpoint == true) {
            val final = recognizer?.getText() ?: ""
            emit(SttResult.Final(final))
            recognizer?.reset()
        }
    }
}

sealed class SttResult {
    data class Partial(val text: String) : SttResult()
    data class Final(val text: String) : SttResult()
}
```

**Latency estimate Snapdragon 8 Elite Gen 5:** ~400-800ms untuk utterance 3-5 detik.

**Multi-language:** default `id`, fallback `auto` untuk code-switching.

---

## TTS — ElevenLabs Streaming v3

**Voice ID:** `gMIZZcmZCnyySbZdSZrZ` (Fuu clone, sudah di-setup Lendra).

**API endpoint:** `POST https://api.elevenlabs.io/v1/text-to-speech/{voice_id}/stream`

**Model:** `eleven_v3` (latest, support emotion tag inline).

```kotlin
class ElevenLabsTts @Inject constructor(
    private val httpClient: OkHttpClient,
    private val securePrefs: SecurePreferences,
    private val audioTrack: AudioTrackManager,
) {
    private val voiceId = "gMIZZcmZCnyySbZdSZrZ"
    
    suspend fun speak(text: String, emotionTag: String? = null) {
        val apiKey = securePrefs.getElevenLabsApiKey()
            ?: throw IllegalStateException("ElevenLabs API key not set")
        
        val processedText = applyEmotionTags(text, emotionTag)
        val voiceSettings = computeVoiceSettings(emotionTag)
        
        val payload = TtsPayload(
            text = processedText,
            modelId = "eleven_v3",
            voiceSettings = voiceSettings,
            outputFormat = "pcm_22050"
        )
        
        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$voiceId/stream")
            .post(Json.encodeToString(payload).toRequestBody("application/json".toMediaType()))
            .header("xi-api-key", apiKey)
            .header("Accept", "audio/pcm")
            .build()
        
        val response = httpClient.newCall(request).execute()
        val inputStream = response.body!!.byteStream()
        
        // Stream PCM chunks ke AudioTrack
        audioTrack.startStream(sampleRate = 22050)
        val buffer = ByteArray(4096)
        while (true) {
            val read = inputStream.read(buffer)
            if (read == -1) break
            audioTrack.write(buffer, 0, read)
        }
        audioTrack.stopStream()
    }
    
    private fun applyEmotionTags(text: String, emotion: String?): String {
        if (emotion.isNullOrBlank()) return text
        // ElevenLabs v3 emotion tags inline format
        return when (emotion) {
            "joy", "happy", "excited" -> "[excited] $text"
            "sad" -> "[whispers] $text"
            "angry" -> "[sternly] $text"
            "surprised" -> "[gasps] $text"
            "neutral" -> text
            else -> text
        }
    }
    
    private fun computeVoiceSettings(emotion: String?): VoiceSettings {
        return when (emotion) {
            "joy", "happy", "excited" -> VoiceSettings(stability = 0.4f, similarity = 0.75f, style = 0.6f)
            "sad" -> VoiceSettings(stability = 0.7f, similarity = 0.75f, style = 0.2f)
            "angry" -> VoiceSettings(stability = 0.5f, similarity = 0.75f, style = 0.7f)
            "neutral", null -> VoiceSettings(stability = 0.55f, similarity = 0.75f, style = 0.3f)
            else -> VoiceSettings(stability = 0.55f, similarity = 0.75f, style = 0.3f)
        }
    }
}
```

**Latency estimate:** first byte ~400-800ms, full utterance streaming behind playback (no perceptible wait).

**Cost:** ElevenLabs Creator subscription, ~10k karakter/bulan dengan auto-renewal. Cukup untuk 50 commands/day.

---

## Emotion Detection

### Audio: Wav2Small ONNX

**Model:** `audeering/wav2small` (120KB INT8). Output VAD (valence-arousal-dominance) vector.

```kotlin
class Wav2SmallEmotion @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var ortSession: OrtSession? = null
    
    suspend fun init() {
        val env = OrtEnvironment.getEnvironment()
        val modelBytes = context.assets.open("models/wav2small.onnx").readBytes()
        ortSession = env.createSession(modelBytes)
    }
    
    suspend fun analyze(audioPcm: FloatArray): VadVector {
        // PCM 16kHz mono, length matches model input (typically 3s window)
        val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(audioPcm), longArrayOf(1, audioPcm.size.toLong()))
        val output = ortSession?.run(mapOf("audio" to inputTensor))
        val vad = (output?.get(0)?.value as Array<FloatArray>)[0]
        return VadVector(valence = vad[0], arousal = vad[1], dominance = vad[2])
    }
}

data class VadVector(val valence: Float, val arousal: Float, val dominance: Float)
```

### Text: roberta-base-go_emotions INT8

**Model:** ONNX INT8 fine-tuned roberta untuk 28 emotion multi-label.

```kotlin
class TextEmotionClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var ortSession: OrtSession? = null
    
    suspend fun classify(text: String): Map<String, Float> {
        val tokens = tokenize(text)
        val inputIds = LongArray(tokens.size) { tokens[it].toLong() }
        val attentionMask = LongArray(tokens.size) { 1L }
        
        val output = ortSession?.run(mapOf(
            "input_ids" to OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), longArrayOf(1, tokens.size.toLong())),
            "attention_mask" to OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), longArrayOf(1, tokens.size.toLong())),
        ))
        
        val logits = (output?.get(0)?.value as Array<FloatArray>)[0]
        val probs = sigmoid(logits)
        return EMOTION_LABELS.mapIndexed { i, label -> label to probs[i] }.toMap()
    }
}

val EMOTION_LABELS = listOf(
    "admiration", "amusement", "anger", "annoyance", "approval", "caring",
    "confusion", "curiosity", "desire", "disappointment", "disapproval",
    "disgust", "embarrassment", "excitement", "fear", "gratitude", "grief",
    "joy", "love", "nervousness", "optimism", "pride", "realization",
    "relief", "remorse", "sadness", "surprise", "neutral"
)
```

### EmotionContext (composite)

```kotlin
class EmotionDetector @Inject constructor(
    private val audioEmotion: Wav2SmallEmotion,
    private val textEmotion: TextEmotionClassifier,
) {
    
    private var lastUserAudioVad: VadVector? = null
    private var lastUserTextEmotions: Map<String, Float>? = null
    
    suspend fun observeUserVoice(audioPcm: FloatArray) {
        lastUserAudioVad = audioEmotion.analyze(audioPcm)
    }
    
    suspend fun observeUserText(text: String) {
        lastUserTextEmotions = textEmotion.classify(text)
    }
    
    fun currentContext(): EmotionContext? {
        val vad = lastUserAudioVad
        val textEmo = lastUserTextEmotions
        if (vad == null && textEmo == null) return null
        
        return EmotionContext(
            audioVad = vad,
            textTopEmotions = textEmo?.entries
                ?.sortedByDescending { it.value }
                ?.take(3)
                ?.associate { it.key to it.value }
                ?: emptyMap(),
        )
    }
}

data class EmotionContext(
    val audioVad: VadVector?,
    val textTopEmotions: Map<String, Float>,
) {
    fun toContextLine(): String {
        val parts = mutableListOf<String>()
        if (audioVad != null) {
            parts.add("audio_emotion: V=${audioVad.valence.format(2)} A=${audioVad.arousal.format(2)} D=${audioVad.dominance.format(2)}")
        }
        if (textTopEmotions.isNotEmpty()) {
            val top = textTopEmotions.entries.joinToString(", ") { "${it.key}=${it.value.format(2)}" }
            parts.add("text_emotion: $top")
        }
        return parts.joinToString(" | ")
    }
}
```

Emotion context dikirim ke LLM sebagai input — bukan filter, bukan decision policy. LLM interpret & decide emotion response output.

---

## Response Composer

LLM output emotion tag → ResponseComposer process untuk TTS:

```kotlin
class ResponseComposer @Inject constructor() {
    
    fun compose(
        summary: String?,
        emotionTag: String?,
        taskStatus: TaskStatus,
    ): ComposedResponse {
        if (summary.isNullOrBlank()) {
            return ComposedResponse(
                text = "Hmm, aku belum bisa kerjain ini.",
                emotionTag = "apologetic",
                preRecordedPrefix = null,
            )
        }
        
        val preRecorded = computePreRecordedAudio(emotionTag, intensity = inferIntensity(summary))
        
        return ComposedResponse(
            text = summary,
            emotionTag = emotionTag,
            preRecordedPrefix = preRecorded,
        )
    }
    
    private fun computePreRecordedAudio(emotion: String?, intensity: Float): String? {
        if (intensity < 0.7f) return null  // only inject for high intensity
        return when (emotion) {
            "joy", "happy", "excited" -> "samples/giggle.wav"
            "sad" -> "samples/sigh.wav"
            "surprised" -> "samples/gasp.wav"
            else -> null
        }
    }
}

data class ComposedResponse(
    val text: String,
    val emotionTag: String?,
    val preRecordedPrefix: String?,
)
```

Pre-recorded loop di-inject sebelum TTS speech (cross-fade audio). Phase 6 detail.

---

## AudioTrack Manager

```kotlin
class AudioTrackManager @Inject constructor() {
    private var track: AudioTrack? = null
    
    fun startStream(sampleRate: Int = 22050) {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build())
            .setBufferSizeInBytes(bufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track?.play()
    }
    
    fun write(buffer: ByteArray, offset: Int, length: Int) {
        track?.write(buffer, offset, length, AudioTrack.WRITE_BLOCKING)
    }
    
    fun stopStream() {
        track?.stop()
        track?.release()
        track = null
    }
}
```

Audio session usage `VOICE_COMMUNICATION` supaya tidak ducking app musik (Spotify) — agar Fuu speak tidak full-mute musik. Trade-off: kompromi audio quality, tapi UX lebih baik.

---

## Mic Conflict Resolution

| Konflik | Strategy |
|---------|----------|
| User pakai Google Assistant + Fuu listen | AudioFocusRequest with `AUDIOFOCUS_LOSS_TRANSIENT` — Fuu pause mic, resume after Assistant done |
| User record video di app lain | Fuu detect mic acquire fail → graceful degrade text-only mode |
| Multiple Fuu task butuh mic | Single global mic lock di AgentRuntime scheduler |

```kotlin
class MicLock @Inject constructor(
    private val audioManager: AudioManager,
) {
    private val lock = Mutex()
    
    suspend fun withMic(block: suspend () -> Unit) {
        lock.withLock {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build())
                .setOnAudioFocusChangeListener { /* handle loss */ }
                .build()
            
            val granted = audioManager.requestAudioFocus(focusRequest)
            if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                throw IllegalStateException("Mic focus denied")
            }
            
            try {
                block()
            } finally {
                audioManager.abandonAudioFocusRequest(focusRequest)
            }
        }
    }
}
```

---

## Mic Indicator (Android 12+ Privacy)

Android 12+ otomatis tampilkan mic indicator di status bar saat AudioRecord aktif. ChibiClaw cukup pakai default — user lihat indikator hijau saat Fuu listen.

UI feedback tambahan: overlay bubble berubah warna (idle gray → listening blue) saat mic acquired.

---

## Phase Timeline untuk Voice Pipeline

- **Phase 1**: text input only via overlay chat panel. No voice.
- **Phase 2**: Voice MVP: 
  - Whisper STT init + button-triggered record
  - ElevenLabs TTS streaming
  - Wav2Small + roberta emotion as context
  - Pre-recorded loop ekslusif untuk Phase 6
- **Phase 6 polish**: emotion advance (audio echo, pre-recorded mix, dynamic voice settings)

---

## Open Questions

1. **Whisper offline vs Gemini Realtime online STT**: kapan switch user toggle? Default offline (privacy-first).
2. **Pre-recorded sample library**: record dari voice clone Fuu (10-15 samples laughter/sigh/breath) atau pakai generic dari ElevenLabs library?
3. **Streaming interrupt UX**: user bilang "stop" mid-Fuu-speaking, gimana implement? Detect via Whisper streaming → kill AudioTrack → flush buffer.

Ditangani di Phase 2 dan Phase 6.

---

## Next Files

- Memory system detail: [16-memory-system.md](16-memory-system.md)
- Phase 2 execution detail: [23-phase-2-voice-emotion.md](23-phase-2-voice-emotion.md)
