package com.chibiclaw.skills

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistence layer for **user-authored custom skills**.
 *
 * Where [SkillLoader] handles the *read* path (merging assets + filesDir),
 * this class owns the *write* path. It deliberately lives outside
 * [SkillRegistry] so the registry can stay a simple in-memory cache.
 *
 * Design rules:
 *   - Custom skills are stored as one JSON file per skill in
 *     [SkillLoader.customSkillsDir], filename = `<skill.name>.json`. The
 *     name doubles as the primary key; saving a skill with an existing
 *     name overwrites it (this is how a user "edits" a skill).
 *   - All writes go through [Json] so we fail-fast on malformed input
 *     instead of letting garbage land on disk and trip the reader later.
 *   - `isCustom(name)` is a pure disk check — no in-memory cache — so
 *     it stays correct even after external file changes (e.g. adb push).
 *
 * This class is Hilt-singleton and safe to inject anywhere. In practice
 * only [SkillEditorViewModel] uses it directly; the rest of the app
 * reads through [SkillRegistry] which transparently picks up new files
 * on its next [SkillRegistry.initialize] call.
 */
@Singleton
class CustomSkillStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val skillLoader: SkillLoader
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Parses raw JSON, validates it, then persists the result. */
    fun saveRaw(rawJson: String): Result<SkillDefinition> = runCatching {
        val skill = json.decodeFromString<SkillDefinition>(rawJson)
        require(skill.name.isNotBlank()) { "Skill name must not be blank" }
        require(skill.triggers.isNotEmpty()) { "Skill must have at least one trigger" }
        persist(skill)
        skill
    }.onFailure { e ->
        Log.w(TAG, "saveRaw failed: ${e.message}")
    }

    /** Persists a pre-built [SkillDefinition] (e.g. from a structured form). */
    fun save(skill: SkillDefinition): Result<Unit> = runCatching {
        require(skill.name.isNotBlank()) { "Skill name must not be blank" }
        persist(skill)
    }

    /**
     * Reads the given [uri] (typically from ACTION_GET_CONTENT), parses
     * it as a skill, and persists it. Returns the parsed skill on
     * success so the UI can show a confirmation toast.
     */
    fun importFromUri(uri: Uri): Result<SkillDefinition> = runCatching {
        val raw = context.contentResolver.openInputStream(uri)?.use {
            it.bufferedReader().readText()
        } ?: throw IllegalStateException("Could not open URI: $uri")
        saveRaw(raw).getOrThrow()
    }.onFailure { e ->
        Log.w(TAG, "importFromUri failed: ${e.message}")
    }

    /**
     * Deletes the custom skill with the given [name]. Returns true if a
     * file was actually removed — false means the skill was built-in
     * (and therefore cannot be deleted via this store) or never existed.
     */
    fun delete(name: String): Boolean {
        val file = fileFor(name)
        if (!file.exists()) return false
        val deleted = file.delete()
        if (!deleted) Log.w(TAG, "Failed to delete ${file.path}")
        return deleted
    }

    /**
     * Returns true iff [name] exists as a custom-authored JSON file on
     * disk. Used by the editor UI to decide whether to show the
     * "CUSTOM" badge and the delete button.
     */
    fun isCustom(name: String): Boolean = fileFor(name).exists()

    /** List of names (not paths) of currently-persisted custom skills. */
    fun listCustomNames(): Set<String> {
        val dir = skillLoader.customSkillsDir()
        if (!dir.exists()) return emptySet()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.map { it.nameWithoutExtension }
            ?.toSet()
            ?: emptySet()
    }

    private fun persist(skill: SkillDefinition) {
        val dir = skillLoader.customSkillsDir()
        if (!dir.exists()) dir.mkdirs()
        val file = fileFor(skill.name)
        file.writeText(json.encodeToString(SkillDefinition.serializer(), skill))
        Log.d(TAG, "Persisted custom skill '${skill.name}' → ${file.path}")
    }

    private fun fileFor(name: String): File {
        // Sanitize: file name only — no path separators, no hidden files.
        val safe = name.trim().replace(Regex("[^A-Za-z0-9_\\-]"), "_")
        return File(skillLoader.customSkillsDir(), "$safe.json")
    }

    companion object {
        private const val TAG = "CustomSkillStore"
    }
}
