package com.chibiclaw.skills

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads [SkillDefinition]s from two sources and merges them:
 *
 *   1. **Built-in**   — JSON files packaged under `assets/skills/` at build
 *                       time. These ship with the app and are read-only.
 *   2. **Custom**     — User-authored JSON files stored in
 *                       `context.filesDir/custom_skills/`. These are
 *                       editable at runtime by [com.chibiclaw.ui.skills.SkillEditorScreen].
 *
 * Resolution order: built-ins load first, then custom skills are overlaid on
 * top. Custom skills with the same [SkillDefinition.name] as a built-in
 * **replace** the built-in entry entirely — this lets power users override
 * stock behaviour without forking the assets.
 *
 * Parse errors are tolerated per-file: a single malformed JSON doesn't kill
 * the whole load, it just gets logged and skipped. The rest of the list still
 * materialises so the app stays functional.
 */
@Singleton
class SkillLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadAll(): List<SkillDefinition> {
        val builtIn = loadBuiltIn()
        val custom = loadCustom()

        // Overlay: custom-by-name wins over built-in-by-name.
        val byName = LinkedHashMap<String, SkillDefinition>()
        builtIn.forEach { byName[it.name] = it }
        custom.forEach { byName[it.name] = it }

        val merged = byName.values.toList()
        Log.d(TAG, "Loaded skills: ${builtIn.size} built-in + ${custom.size} custom → ${merged.size} final")
        return merged
    }

    /** Reads JSON files shipped inside `assets/skills/`. */
    private fun loadBuiltIn(): List<SkillDefinition> {
        val skills = mutableListOf<SkillDefinition>()
        try {
            val assetFiles = context.assets.list("skills") ?: return emptyList()
            for (file in assetFiles) {
                if (!file.endsWith(".json")) continue
                try {
                    val raw = context.assets.open("skills/$file").bufferedReader().readText()
                    skills.add(json.decodeFromString(raw))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load built-in skill $file: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing built-in skills: ${e.message}")
        }
        return skills
    }

    /**
     * Reads user-authored JSON files from [customSkillsDir]. Called by both
     * [loadAll] and the editor screen (via [CustomSkillStore]). Returns an
     * empty list if the directory doesn't exist yet — it's created lazily by
     * [CustomSkillStore.save].
     */
    private fun loadCustom(): List<SkillDefinition> {
        val dir = customSkillsDir()
        if (!dir.exists()) return emptyList()
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return emptyList()
        val skills = mutableListOf<SkillDefinition>()
        for (file in files) {
            try {
                val raw = file.readText()
                skills.add(json.decodeFromString(raw))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load custom skill ${file.name}: ${e.message}")
            }
        }
        return skills
    }

    /**
     * Filesystem root for custom skills. Intentionally separate from the
     * ModelImporter's `models/` directory so `adb shell run-as` debugging is
     * straightforward — one directory per content type.
     */
    fun customSkillsDir(): File = File(context.filesDir, "custom_skills")

    companion object {
        private const val TAG = "SkillLoader"
    }
}
