package com.chibiclaw.ui.setup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chibiclaw.ai.EngineState
import com.chibiclaw.ai.GemmaEngineManager
import com.chibiclaw.ai.ModelEntry
import com.chibiclaw.ai.ModelLibrary
import com.chibiclaw.memory.pref.SecurePreferences
import com.chibiclaw.util.ModelFileImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class ImportState {
    object Idle : ImportState()
    data class InProgress(val percent: Int, val copiedMb: Long, val totalMb: Long) : ImportState()
    data class Done(val path: String) : ImportState()
    data class Failed(val message: String) : ImportState()
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val gemmaEngineManager: GemmaEngineManager,
    private val modelFileImporter: ModelFileImporter,
    private val modelLibrary: ModelLibrary
) : ViewModel() {

    val engineState: StateFlow<EngineState> = gemmaEngineManager.state

    /** The user's current library of uploaded models. */
    val libraryModels: StateFlow<List<ModelEntry>> = modelLibrary.models

    /** The currently-selected model id (null if nothing picked yet). */
    val activeModelId: StateFlow<String?> = modelLibrary.activeId

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    /** Loads the model with [id] from the library into the engine. */
    fun loadModelFromLibrary(id: String) {
        val entry = modelLibrary.models.value.firstOrNull { it.id == id } ?: return
        val backend = securePreferences.getModelBackend()
        modelLibrary.setActive(id)
        gemmaEngineManager.switchActive(entry.path, backend)
    }

    /**
     * Loads the current active model from the library. Called automatically
     * after an import finishes to give the user instant "Lanjut" availability.
     */
    fun loadActive() {
        val active = modelLibrary.active() ?: return
        val backend = securePreferences.getModelBackend()
        gemmaEngineManager.switchActive(active.path, backend)
    }

    /**
     * Copies a user-selected file (SAF content URI) to app-private storage and
     * adds it to [ModelLibrary] on success. The newly-imported entry becomes
     * the active model if nothing else was previously active.
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
                        val displayName = f.name
                        val id = modelLibrary.add(
                            name = displayName,
                            path = progress.destinationPath,
                            sizeBytes = f.length()
                        )
                        // Back-compat: legacy callers still read getModelPath().
                        securePreferences.setModelPath(progress.destinationPath)
                        // Auto-load the freshly imported model so the user can
                        // continue without an extra "Load" tap.
                        loadModelFromLibrary(id)
                        ImportState.Done(progress.destinationPath)
                    }
                    is ModelFileImporter.Progress.Error -> ImportState.Failed(progress.message)
                }
            }
        }
    }

    fun removeModel(id: String) {
        modelLibrary.remove(id)
    }

    fun renameModel(id: String, newName: String) {
        modelLibrary.rename(id, newName)
    }

    fun resetImportState() {
        _importState.value = ImportState.Idle
    }

    fun markSetupComplete() {
        securePreferences.setSetupComplete(true)
    }

    fun isSetupDone(): Boolean = securePreferences.isSetupComplete()
}
