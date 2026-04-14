package com.chibiclaw.ai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.chibiclaw.debug.DevLogger
import com.chibiclaw.util.ModelFileImporter
import com.chibiclaw.util.StoragePermissionHelper
import java.io.File
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

enum class EngineState { UNLOADED, LOADING, READY, ERROR }

/**
 * Dual-engine manager: holds a concurrent pair of LiteRT-LM engines — one
 * tuned for [ModelTier.E2B] (light, text-only, ~1.6 GB, fast) and one for
 * [ModelTier.E4B] (full multimodal, ~4.4 GB, slow). Commands can be routed
 * to whichever tier fits best via [getEngine].
 *
 * E2B is OPTIONAL — if no E2B model path is configured, every tier request
 * falls through to the E4B engine. This keeps existing single-engine setups
 * working transparently.
 *
 * Each engine runs on its own single-threaded dispatcher because LiteRT-LM
 * native code has strict thread affinity per Engine instance.
 */
@Singleton
class GemmaEngineManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val devLogger: DevLogger,
    private val modelFileImporter: ModelFileImporter,
    // Lazy to break the circular dependency: GemmaInference itself depends on
    // GemmaEngineManager. We only need the Inference instance to signal it
    // when an engine is being unloaded so it can drop its session reference.
    private val gemmaInferenceLazy: Lazy<GemmaInference>
) {
    // E4B owns the "legacy" single-threaded dispatcher — kept as the primary
    // so existing code using `engineDispatcher` continues to work.
    val engineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "gemma-engine-e4b").also { it.isDaemon = true }
    }.asCoroutineDispatcher()

    // Separate dispatcher for E2B so both engines can run truly in parallel
    // without blocking each other.
    val engineDispatcherE2B = Executors.newSingleThreadExecutor { r ->
        Thread(r, "gemma-engine-e2b").also { it.isDaemon = true }
    }.asCoroutineDispatcher()

    private val scope = CoroutineScope(SupervisorJob() + engineDispatcher)

    // Engines are stored per-tier. Access is always via [getEngine].
    private val engines = mutableMapOf<ModelTier, Engine?>()

    // Per-tier state flows so UI can show independent load progress.
    private val _stateE4B = MutableStateFlow(EngineState.UNLOADED)
    val stateE4B: StateFlow<EngineState> = _stateE4B.asStateFlow()

    private val _stateE2B = MutableStateFlow(EngineState.UNLOADED)
    val stateE2B: StateFlow<EngineState> = _stateE2B.asStateFlow()

    // Backwards-compatible single state flow — reflects E4B (primary) state.
    val state: StateFlow<EngineState> = _stateE4B.asStateFlow()

    private val _isBatteryLow = MutableStateFlow(false)
    val isBatteryLow: StateFlow<Boolean> = _isBatteryLow.asStateFlow()

    private var idleJobE4B: Job? = null
    private var idleJobE2B: Job? = null
    private val IDLE_TIMEOUT_MS = 5 * 60 * 1000L

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val percent = if (scale > 0) (level * 100 / scale) else 100
            val wasLow = _isBatteryLow.value
            _isBatteryLow.value = percent <= 15
            if (!wasLow && _isBatteryLow.value) {
                Log.w(TAG, "Battery low ($percent%) — unloading E4B to save power")
                devLogger.w("ENGINE", "Battery $percent% — unloading E4B, keeping E2B only")
                // Drop the heavy model, keep the light one if loaded.
                unloadModel(ModelTier.E4B)
            }
        }
    }

    init {
        context.registerReceiver(
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
    }

    /**
     * Returns the state flow for the given tier so screens can render
     * per-engine load progress.
     */
    fun stateFor(tier: ModelTier): StateFlow<EngineState> =
        if (tier == ModelTier.E2B) stateE2B else stateE4B

    /**
     * Backwards-compatible single-arg load — always targets E4B (primary).
     */
    fun loadModel(modelPath: String, backendName: String = "GPU") {
        loadModel(modelPath, backendName, ModelTier.E4B)
    }

    /**
     * Returns the absolute path of whatever model is currently loaded into
     * the E4B slot, or null if nothing is loaded. Used by [switchActive] to
     * decide whether a swap is needed.
     */
    fun currentLoadedPath(): String? = currentPaths[ModelTier.E4B]

    // Tracks which absolute path is loaded into each slot so switchActive()
    // can skip redundant reloads and tell the user when nothing changed.
    private val currentPaths = mutableMapOf<ModelTier, String?>()

    /**
     * Atomically switches the active model to [newPath]. If that path is
     * already loaded in E4B nothing happens. Otherwise the current E4B engine
     * is unloaded FIRST (freeing its native session) before the new one is
     * kicked off on the same dispatcher. Callers use this when the user
     * picks a different model from the library.
     */
    fun switchActive(newPath: String, backendName: String = "GPU") {
        if (currentPaths[ModelTier.E4B] == newPath && _stateE4B.value == EngineState.READY) {
            devLogger.i("ENGINE", "switchActive → already on $newPath, no-op")
            return
        }
        devLogger.i("ENGINE", "switchActive → ${newPath.substringAfterLast('/')}")
        // Unload current E4B (and E2B legacy slot) so native sessions release
        // cleanly before we stand up the new engine.
        if (engines[ModelTier.E4B] != null) unloadModel(ModelTier.E4B)
        if (engines[ModelTier.E2B] != null) unloadModel(ModelTier.E2B)
        loadModel(newPath, backendName, ModelTier.E4B)
    }

    /**
     * Tier-aware model load. Loads onto [tier]'s engine slot; idempotent if
     * that slot is already READY.
     */
    fun loadModel(modelPath: String, backendName: String, tier: ModelTier) {
        val stateFlow = if (tier == ModelTier.E2B) _stateE2B else _stateE4B
        if (stateFlow.value == EngineState.READY || stateFlow.value == EngineState.LOADING) return
        if (_isBatteryLow.value && tier == ModelTier.E4B) {
            Log.w(TAG, "Battery low — refusing to load E4B")
            return
        }

        val isAppPrivate = modelFileImporter.isAppPrivatePath(modelPath)
        if (!isAppPrivate && !StoragePermissionHelper.hasAllFilesAccess()) {
            Log.e(TAG, "MANAGE_EXTERNAL_STORAGE not granted for non-private path: $modelPath")
            devLogger.e("ENGINE", "✗ [$tier] Path di luar folder app — butuh 'All Files Access'")
            stateFlow.value = EngineState.ERROR
            return
        }
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            Log.e(TAG, "[$tier] Model file not found: $modelPath")
            devLogger.e("ENGINE", "✗ [$tier] File not found: $modelPath")
            stateFlow.value = EngineState.ERROR
            return
        }
        if (!modelFile.canRead()) {
            Log.e(TAG, "[$tier] Model file not readable: $modelPath")
            devLogger.e("ENGINE", "✗ [$tier] File exists but not readable: $modelPath")
            stateFlow.value = EngineState.ERROR
            return
        }

        stateFlow.value = EngineState.LOADING

        // Each tier loads on its own dispatcher so they can initialize in
        // parallel without blocking one another.
        val dispatcher = if (tier == ModelTier.E2B) engineDispatcherE2B else engineDispatcher
        CoroutineScope(dispatcher + SupervisorJob()).launch {
            try {
                devLogger.i(
                    "ENGINE",
                    "[$tier] Loading ${modelPath.substringAfterLast('/')} [$backendName]"
                )
                val backend = when (backendName.uppercase()) {
                    "CPU" -> Backend.CPU()
                    else -> {
                        try { Backend.GPU() }
                        catch (e: Exception) {
                            Log.w(TAG, "GPU unavailable for $tier, falling back to CPU: ${e.message}")
                            devLogger.w("ENGINE", "[$tier] GPU unavailable → CPU: ${e.message}")
                            Backend.CPU()
                        }
                    }
                }
                val config = EngineConfig(modelPath = modelPath, backend = backend)
                devLogger.d("ENGINE", "[$tier] Engine(config) on ${Thread.currentThread().name}")
                val engine = Engine(config)
                devLogger.i("ENGINE", "[$tier] Engine created — initializing…")

                val initStart = System.currentTimeMillis()
                engine.initialize()
                val initMs = System.currentTimeMillis() - initStart

                engines[tier] = engine
                currentPaths[tier] = modelPath
                stateFlow.value = EngineState.READY
                Log.d(TAG, "Gemma $tier initialized: $modelPath [$backendName] (${initMs}ms)")
                devLogger.i("ENGINE", "✓ [$tier] Ready in ${initMs}ms")
                scheduleIdleUnload(tier)
            } catch (e: Exception) {
                Log.e(TAG, "[$tier] Failed to load engine: ${e.message}")
                devLogger.e("ENGINE", "[$tier] Failed: ${e::class.simpleName}: ${e.message}")
                stateFlow.value = EngineState.ERROR
            }
        }
    }

    /**
     * Returns the engine for the requested [tier]. Falls through to the
     * other tier if the requested one isn't loaded — so routing decisions
     * never fail hard when only one model is available.
     */
    fun getEngine(tier: ModelTier): Engine? {
        engines[tier]?.let { return it }
        // Fallback: whichever engine IS loaded.
        return engines[ModelTier.E4B] ?: engines[ModelTier.E2B]
    }

    /**
     * Backwards-compatible zero-arg getEngine() — returns whichever engine
     * is available, preferring E4B. Kept so existing callers that don't
     * know about tiers still work.
     */
    fun getEngine(): Engine? = engines[ModelTier.E4B] ?: engines[ModelTier.E2B]

    /**
     * Dispatcher that owns [tier]'s engine thread — inference callers must
     * use this so they don't cross native thread boundaries.
     */
    fun dispatcherFor(tier: ModelTier) =
        if (tier == ModelTier.E2B) engineDispatcherE2B else engineDispatcher

    fun isReady(tier: ModelTier): Boolean =
        stateFor(tier).value == EngineState.READY && engines[tier] != null

    fun isReady(): Boolean = isReady(ModelTier.E4B) || isReady(ModelTier.E2B)

    fun resetIdleTimer() {
        // Reset both — we don't know which tier the next command will use.
        idleJobE4B?.cancel()
        idleJobE2B?.cancel()
        if (engines[ModelTier.E4B] != null) scheduleIdleUnload(ModelTier.E4B)
        if (engines[ModelTier.E2B] != null) scheduleIdleUnload(ModelTier.E2B)
    }

    /** Unloads both engines (back-compat). */
    fun unloadModel() {
        unloadModel(ModelTier.E4B)
        unloadModel(ModelTier.E2B)
    }

    fun unloadModel(tier: ModelTier) {
        if (tier == ModelTier.E4B) idleJobE4B?.cancel() else idleJobE2B?.cancel()
        // Tell Inference to drop its session reference BEFORE we close the
        // engine — touching a Conversation after engine.close() is UB.
        try {
            gemmaInferenceLazy.get().onEngineUnloaded(tier)
        } catch (e: Exception) {
            Log.w(TAG, "onEngineUnloaded($tier) notify failed: ${e.message}")
        }
        engines[tier]?.close()
        engines[tier] = null
        currentPaths[tier] = null
        val stateFlow = if (tier == ModelTier.E2B) _stateE2B else _stateE4B
        stateFlow.value = EngineState.UNLOADED
        Log.d(TAG, "Gemma $tier unloaded")
    }

    private fun scheduleIdleUnload(tier: ModelTier) {
        val job = scope.launch {
            delay(IDLE_TIMEOUT_MS)
            Log.d(TAG, "Idle timeout — unloading $tier")
            unloadModel(tier)
        }
        if (tier == ModelTier.E4B) idleJobE4B = job else idleJobE2B = job
    }

    companion object {
        private const val TAG = "GemmaEngineManager"
    }
}
