package com.chibiclaw.ui.skills

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chibiclaw.gateway.CommandGateway
import com.chibiclaw.gateway.CommandSource
import com.chibiclaw.skills.CustomSkillStore
import com.chibiclaw.skills.SkillDefinition
import com.chibiclaw.skills.SkillRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [SkillEditorScreen]. Exposes the currently-loaded skill list
 * (built-in + custom merged by [SkillRegistry]) plus the set of
 * custom-by-name so the UI can render a "CUSTOM" badge and delete action.
 *
 * Write paths (add / import / delete) all round-trip through
 * [CustomSkillStore] and then re-initialize [SkillRegistry] so the
 * visible list stays consistent with on-disk state.
 */
@HiltViewModel
class SkillEditorViewModel @Inject constructor(
    private val skillRegistry: SkillRegistry,
    private val commandGateway: CommandGateway,
    private val customSkillStore: CustomSkillStore
) : ViewModel() {

    private val _skills = MutableStateFlow<List<SkillDefinition>>(emptyList())
    val skills: StateFlow<List<SkillDefinition>> = _skills

    private val _customNames = MutableStateFlow<Set<String>>(emptySet())
    val customNames: StateFlow<Set<String>> = _customNames

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult

    /**
     * Non-null whenever we want the UI to show a toast / snackbar.
     * Cleared by [clearEditorResult] once the UI has consumed it.
     */
    private val _editorResult = MutableStateFlow<String?>(null)
    val editorResult: StateFlow<String?> = _editorResult

    init {
        refresh()
    }

    fun testSkill(skill: SkillDefinition) {
        val testCommand = skill.triggers.firstOrNull() ?: skill.name
        _testResult.value = "Testing: $testCommand..."
        viewModelScope.launch {
            commandGateway.submitDirect(testCommand, CommandSource.WIDGET)
            _testResult.value = "Test command sent: $testCommand"
        }
    }

    fun clearTestResult() {
        _testResult.value = null
    }

    fun clearEditorResult() {
        _editorResult.value = null
    }

    fun refresh() {
        skillRegistry.initialize()
        _skills.value = skillRegistry.getAll()
        _customNames.value = customSkillStore.listCustomNames()
    }

    /**
     * Parses the user-supplied JSON and writes it to disk as a custom
     * skill. On success the registry is reloaded so the new entry shows
     * up in the list immediately.
     */
    fun addCustomSkillFromJson(rawJson: String) {
        customSkillStore.saveRaw(rawJson)
            .onSuccess { skill ->
                _editorResult.value = "Skill '${skill.name}' saved"
                refresh()
            }
            .onFailure { e ->
                _editorResult.value = "Save failed: ${e.message}"
            }
    }

    /**
     * Reads JSON from a content:// URI picked via ACTION_GET_CONTENT and
     * saves it as a custom skill. Errors are surfaced through
     * [editorResult] instead of crashing — imports are best-effort.
     */
    fun importFromUri(uri: Uri) {
        customSkillStore.importFromUri(uri)
            .onSuccess { skill ->
                _editorResult.value = "Imported '${skill.name}'"
                refresh()
            }
            .onFailure { e ->
                _editorResult.value = "Import failed: ${e.message}"
            }
    }

    /**
     * Removes a custom skill by name. No-op for built-in skills — the
     * UI only offers the delete action when [customNames] contains the
     * target, so this path is defensive against races with external
     * edits.
     */
    fun removeCustom(name: String) {
        if (customSkillStore.delete(name)) {
            _editorResult.value = "Deleted '$name'"
            refresh()
        } else {
            _editorResult.value = "Cannot delete '$name' (built-in or missing)"
        }
    }
}
