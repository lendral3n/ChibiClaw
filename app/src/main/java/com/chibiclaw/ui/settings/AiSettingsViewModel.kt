package com.chibiclaw.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chibiclaw.ai.EngineState
import com.chibiclaw.ai.GemmaEngineManager
import com.chibiclaw.ai.ModelEntry
import com.chibiclaw.ai.ModelLibrary
import com.chibiclaw.executor.DryRunMode
import com.chibiclaw.memory.pref.SecurePreferences
import com.chibiclaw.ui.setup.ImportState
import com.chibiclaw.util.ModelFileImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val gemmaEngineManager: GemmaEngineManager,
    private val modelFileImporter: ModelFileImporter,
    private val modelLibrary: ModelLibrary,
    private val dryRunMode: DryRunMode
) : ViewModel() {

    /** The user's current library of uploaded models. */
    val libraryModels: StateFlow<List<ModelEntry>> = modelLibrary.models

    /** The currently-selected model id. */
    val activeModelId: StateFlow<String?> = modelLibrary.activeId

    private val _backend = MutableStateFlow(securePreferences.getModelBackend())
    val backend: StateFlow<String> = _backend

    val engineState: StateFlow<EngineState> = gemmaEngineManager.state

    /**
     * Phase 10 — dry-run toggle wired in. When enabled, every
     * side-effectful action returned by Gemma is intercepted by
     * [DryRunMode.simulate] inside [com.chibiclaw.executor.ExecutionRouter]
     * instead of actually firing the underlying intent / gesture /
     * shell call. Read-only actions (ScanUi, MemoryQuery, etc.) still
     * run normally so the planner keeps fresh context.
     */
    val dryRunEnabled: StateFlow<Boolean> = dryRunMode.enabled

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    fun setBackend(b: String) {
        _backend.value = b
        securePreferences.setModelBackend(b)
    }

    fun setDryRun(enabled: Boolean) {
        dryRunMode.set(enabled)
    }

    fun resetImportState() {
        _importState.value = ImportState.Idle
    }

    /**
     * Copies a user-selected file (SAF URI) into app-private storage, adds
     * it to the [ModelLibrary], sets it active, and reloads the engine.
     */
    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            modelFileImporter.importModel(uri).collect { progress ->
                _importState.value = when (progress) {
                    is ModelFileImporter.Progress.Copying -> ImportState.InProgress(
                        percent = progress.percent,
                        copiedMb = progress.copiedBytes / (1024 * 1024),
                        totalMb = progress.totalBytes / (1024 * 1024)
                    )
                    is ModelFileImporter.Progress.Done -> {
                        val f = File(progress.destinationPath)
                        val id = modelLibrary.add(
                            name = f.name,
                            path = progress.destinationPath,
                            sizeBytes = f.length()
                        )
                        // Back-compat: keep legacy getModelPath() pointing at the
                        // freshly-imported file so any code still reading it works.
                        securePreferences.setModelPath(progress.destinationPath)
                        selectModel(id)
                        ImportState.Done(progress.destinationPath)
                    }
                    is ModelFileImporter.Progress.Error -> ImportState.Failed(progress.message)
                }
            }
        }
    }

    /**
     * Switches the active model to [id]. If the user picks a model that isn't
     * currently loaded, we unload the old engine and boot the new one.
     * Instant no-op when picking the already-active entry.
     */
    fun selectModel(id: String) {
        val entry = modelLibrary.models.value.firstOrNull { it.id == id } ?: return
        modelLibrary.setActive(id)
        securePreferences.setModelPath(entry.path)
        gemmaEngineManager.switchActive(entry.path, _backend.value)
    }

    fun removeModel(id: String) {
        modelLibrary.remove(id)
        // If we just removed the loaded engine's backing file, unload so the
        // state flow reports UNLOADED and the UI prompts the user to pick
        // a different model.
        val stillActive = modelLibrary.active()
        if (stillActive == null) {
            gemmaEngineManager.unloadModel()
        } else if (stillActive.path != gemmaEngineManager.currentLoadedPath()) {
            gemmaEngineManager.switchActive(stillActive.path, _backend.value)
        }
    }

    fun renameModel(id: String, newName: String) {
        modelLibrary.rename(id, newName)
    }
}
