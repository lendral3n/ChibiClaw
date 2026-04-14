package com.chibiclaw.core

import android.content.Context
import android.content.Intent
import android.util.Log
import com.chibiclaw.ai.EngineState
import com.chibiclaw.ai.GemmaEngineManager
import com.chibiclaw.ai.ModelLibrary
import com.chibiclaw.memory.pref.SecurePreferences
import com.chibiclaw.service.ChibiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 10 — deferred initialization orchestrator.
 *
 * Owns the "cold start → ready" pipeline so nothing heavy runs before
 * the user picks a mode. [ChibiApp.onCreate] used to auto-load the
 * Gemma model and [MainActivity.onCreate] used to eager-start
 * [ChibiService] — both have been moved here and only fire after
 * [bootstrap] is called from [com.chibiclaw.ui.bootstrap.BootstrapScreen]
 * once the user taps "User Mode" or unlocks "Dev Mode".
 *
 * State machine:
 *
 *   IDLE                      — mode selection screen visible
 *     → SERVICE_STARTING      — startForegroundService dispatched
 *     → MODEL_LOADING         — Gemma engine being loaded
 *     → READY                 — dashboard can render
 *     → FAILED(reason)        — blocking error, user sees retry UI
 *
 * All transitions flow through [_state] so the BootstrapScreen can
 * render step-by-step progress. bootstrap() is idempotent — calling
 * it twice on a READY instance is a no-op.
 */
@Singleton
class ChibiBootstrapper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePreferences: SecurePreferences,
    private val gemmaEngineManager: GemmaEngineManager,
    private val modelLibrary: ModelLibrary
) {

    sealed class State {
        data object Idle : State()
        data object ServiceStarting : State()
        data object ModelLoading : State()
        data object Ready : State()
        data class Failed(val reason: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    // Phase 10 fix — must be Main so that framework calls (startForegroundService,
    // Lifecycle transitions etc.) run on the UI looper. Heavy work (GemmaEngine
    // native init) already dispatches to its own engineDispatcher inside
    // GemmaEngineManager.loadModel(), so running the orchestration on Main
    // adds zero jank — it's purely suspend/StateFlow plumbing here.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** True once bootstrap() has successfully completed at least once. */
    @Volatile
    private var completed: Boolean = false

    fun isReady(): Boolean = completed && _state.value is State.Ready

    /**
     * Kicks off the full cold-start pipeline. Safe to call multiple
     * times — idempotent after first success.
     *
     * **Phase 10 fix:** this method no longer accepts an `onReady`
     * callback. The old design invoked `onReady` (which contained
     * `navController.navigate("home")`) from [Dispatchers.IO], crashing
     * with *"Method setCurrentState must be called on the main thread"*.
     * The proper Compose pattern is to have [BootstrapScreen] observe
     * [state] via `collectAsState()` and trigger navigation from a
     * `LaunchedEffect` — that always runs on the Main dispatcher.
     */
    fun bootstrap() {
        if (isReady()) return
        val current = _state.value
        if (current is State.ServiceStarting || current is State.ModelLoading) {
            return // Already in progress — just let it finish.
        }

        scope.launch {
            try {
                _state.value = State.ServiceStarting
                startService()

                _state.value = State.ModelLoading
                val modelOk = loadActiveModel()

                if (!modelOk) {
                    Log.w(TAG, "bootstrap: no active model — marking Ready anyway")
                    _state.value = State.Ready
                    completed = true
                    return@launch
                }

                val ready = withTimeoutOrNull(MODEL_LOAD_TIMEOUT_MS) {
                    gemmaEngineManager.state.first { it == EngineState.READY || it == EngineState.ERROR }
                }

                when (ready) {
                    EngineState.READY -> {
                        _state.value = State.Ready
                        completed = true
                        Log.d(TAG, "bootstrap: engine READY")
                    }
                    EngineState.ERROR -> {
                        _state.value = State.Failed("Engine gagal dimuat")
                    }
                    else -> {
                        Log.w(TAG, "bootstrap: engine load timed out after ${MODEL_LOAD_TIMEOUT_MS}ms")
                        _state.value = State.Ready
                        completed = true
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "bootstrap failed: ${t.message}", t)
                _state.value = State.Failed(t.message ?: "Unknown error")
            }
        }
    }

    /**
     * Manual reset — used by the retry button on the BootstrapScreen
     * failure state. Puts us back to Idle so the next bootstrap() call
     * runs the full pipeline again.
     */
    fun reset() {
        completed = false
        _state.value = State.Idle
    }

    private suspend fun awaitReady() {
        _state.first { it is State.Ready || it is State.Failed }
    }

    private fun startService() {
        try {
            val intent = Intent(context, ChibiService::class.java)
            context.startForegroundService(intent)
            Log.d(TAG, "ChibiService foreground start dispatched")
        } catch (t: Throwable) {
            // Don't fail the whole bootstrap — the service may already
            // be running (e.g. after process restart) and startForegroundService
            // only errors on rare OEM quirks.
            Log.w(TAG, "startForegroundService failed: ${t.message}")
        }
    }

    /**
     * Mirrors the old [com.chibiclaw.core.ChibiApp.autoLoadModel]. Returns
     * true iff a load was actually triggered (i.e. there is an active
     * model and the backing file exists).
     *
     * SharedPreferences reads and File.exists() are cheap but technically
     * disk I/O, so we jump to [Dispatchers.IO] for the probing part,
     * then call [GemmaEngineManager.loadModel] back on Main (it
     * internally dispatches to its own engine thread).
     */
    private suspend fun loadActiveModel(): Boolean {
        return withContext(Dispatchers.IO) {
            if (!securePreferences.isSetupComplete()) {
                Log.d(TAG, "loadActiveModel: setup not complete — skip")
                return@withContext false
            }
            val active = modelLibrary.active()
            if (active == null) {
                Log.d(TAG, "loadActiveModel: no active model in library")
                return@withContext false
            }
            val file = File(active.path)
            if (!file.exists()) {
                Log.w(TAG, "loadActiveModel: file missing → ${active.path}")
                return@withContext false
            }
            val backend = securePreferences.getModelBackend()
            // loadModel() internally dispatches to engineDispatcher —
            // safe to call from any thread.
            gemmaEngineManager.loadModel(active.path, backend)
            Log.d(TAG, "loadActiveModel: loading '${active.name}' [$backend]")
            true
        }
    }

    companion object {
        private const val TAG = "ChibiBootstrapper"
        private const val MODEL_LOAD_TIMEOUT_MS = 30_000L
    }
}
