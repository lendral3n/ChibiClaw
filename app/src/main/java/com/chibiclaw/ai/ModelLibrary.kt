package com.chibiclaw.ai

import android.util.Log
import com.chibiclaw.memory.pref.SecurePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents one model file the user has added to the app. A ModelEntry is
 * immutable once created — the user just picks which one is active.
 *
 * @param id         Stable random UUID, used as the primary key everywhere.
 * @param name       Human-readable label (defaults to file name, user-editable).
 * @param path       Absolute path on disk (always inside app-private /files/models).
 * @param sizeBytes  File size captured at import time (so we don't stat on every list).
 * @param addedAt    Unix millis for sorting "most recent first".
 */
data class ModelEntry(
    val id: String,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val addedAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("path", path)
        put("size", sizeBytes)
        put("addedAt", addedAt)
    }

    val fileExists: Boolean get() = File(path).exists()

    val sizeDisplay: String get() = when {
        sizeBytes < 0 -> "?"
        sizeBytes < 1024L * 1024 -> "%.0f KB".format(sizeBytes / 1024.0)
        sizeBytes < 1024L * 1024 * 1024 -> "%.1f MB".format(sizeBytes / (1024.0 * 1024))
        else -> "%.2f GB".format(sizeBytes / (1024.0 * 1024 * 1024))
    }

    companion object {
        fun fromJson(obj: JSONObject): ModelEntry = ModelEntry(
            id = obj.optString("id").ifEmpty { UUID.randomUUID().toString() },
            name = obj.optString("name").ifEmpty { "Model" },
            path = obj.optString("path"),
            sizeBytes = obj.optLong("size", -1L),
            addedAt = obj.optLong("addedAt", System.currentTimeMillis())
        )
    }
}

/**
 * User-managed library of uploaded model files. Works like ChatGPT / Claude's
 * model picker: the user can have many models in the library, but only ONE is
 * "active" and actually loaded into GemmaEngineManager at any given time.
 *
 * Single source of truth for:
 *   - the list of uploaded models (persisted as JSON in SecurePreferences)
 *   - which one is currently selected as active
 *
 * This replaces the old dual-slot (E4B primary + E2B optional) design, which
 * was confusing AND caused a session-collision bug when the router said
 * "use E2B" but only E4B was uploaded (the tier fallback silently piggybacked
 * on the E4B engine and LiteRT-LM refused the second Conversation).
 */
@Singleton
class ModelLibrary @Inject constructor(
    private val securePreferences: SecurePreferences
) {
    private val _models = MutableStateFlow<List<ModelEntry>>(emptyList())
    val models: StateFlow<List<ModelEntry>> = _models.asStateFlow()

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    init {
        reload()
    }

    /** Re-read the JSON blob from SecurePreferences into memory. */
    private fun reload() {
        val list = parseList(securePreferences.getModelLibraryJson())
        // Prune entries whose file is gone — but keep them for ONE session
        // so we can show "missing" status to the user. Actually we DO drop
        // them here because stale entries will confuse auto-load; the user
        // can re-import.
        val alive = list.filter { File(it.path).exists() }
        if (alive.size != list.size) {
            Log.w(TAG, "Dropped ${list.size - alive.size} dead library entries")
            persistList(alive)
        }
        _models.value = alive.sortedByDescending { it.addedAt }

        val saved = securePreferences.getActiveModelId().ifEmpty { null }
        // If the active id still refers to a living entry, keep it. Otherwise,
        // default to the most recent one (if any).
        _activeId.value = when {
            saved != null && alive.any { it.id == saved } -> saved
            alive.isNotEmpty() -> alive.maxByOrNull { it.addedAt }?.id
            else -> null
        }
        // Persist the (possibly-changed) active id back.
        _activeId.value?.let { securePreferences.setActiveModelId(it) }
    }

    /** Adds a fresh entry from a freshly-imported file. Returns its id. */
    fun add(name: String, path: String, sizeBytes: Long): String {
        val entry = ModelEntry(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { File(path).nameWithoutExtension },
            path = path,
            sizeBytes = sizeBytes,
            addedAt = System.currentTimeMillis()
        )
        val updated = _models.value + entry
        _models.value = updated.sortedByDescending { it.addedAt }
        persistList(updated)
        // First upload becomes active automatically — rest of the app needs
        // SOMETHING to load on next boot.
        if (_activeId.value == null) {
            setActive(entry.id)
        }
        return entry.id
    }

    /** Removes an entry from the library AND deletes its file from disk. */
    fun remove(id: String) {
        val entry = _models.value.firstOrNull { it.id == id } ?: return
        runCatching { File(entry.path).delete() }.onFailure {
            Log.w(TAG, "Failed to delete ${entry.path}: ${it.message}")
        }
        val updated = _models.value.filter { it.id != id }
        _models.value = updated
        persistList(updated)
        // If we just deleted the active one, fall back to most recent.
        if (_activeId.value == id) {
            _activeId.value = updated.maxByOrNull { it.addedAt }?.id
            securePreferences.setActiveModelId(_activeId.value.orEmpty())
        }
    }

    /** Sets which entry is active. No-op if id doesn't exist. */
    fun setActive(id: String) {
        if (_models.value.none { it.id == id }) return
        _activeId.value = id
        securePreferences.setActiveModelId(id)
    }

    /** Renames an entry (UI niceness — not used by inference at all). */
    fun rename(id: String, newName: String) {
        val updated = _models.value.map {
            if (it.id == id) it.copy(name = newName.trim()) else it
        }
        _models.value = updated
        persistList(updated)
    }

    /** Convenience accessors for the rest of the app. */
    fun active(): ModelEntry? {
        val id = _activeId.value ?: return null
        return _models.value.firstOrNull { it.id == id }
    }

    /**
     * Returns the best candidate to pre-load into the E2B (lightweight) slot
     * on service boot. Heuristic: smallest model in the library that ISN'T
     * already the active E4B entry. The idea is — if the user has two
     * models (e.g. a 4B main + a 2B secondary), we want both ready so
     * [ModelRouter] can route simple commands to the smaller one without
     * paying a cold-load penalty on the first tap.
     *
     * Returns null if:
     *   - The library has zero or only one entry (nothing separate to warm),
     *   - Or all remaining models are >3 GB (too big to justify as "E2B" —
     *     we'd rather let the OS decide later).
     *
     * The 3 GB threshold is intentionally generous: Gemma 3n 2B quantised
     * sits around 1.6 GB and Gemma 3n 4B around 4.4 GB, so anything under
     * 3 GB is definitively "the smaller one".
     */
    fun e2bCandidate(): ModelEntry? {
        val activeE4B = active() ?: return null
        val candidates = _models.value
            .filter { it.id != activeE4B.id }
            .filter { it.sizeBytes in 1 until 3L * 1024 * 1024 * 1024 }
        return candidates.minByOrNull { it.sizeBytes }
    }

    fun isEmpty(): Boolean = _models.value.isEmpty()

    private fun persistList(list: List<ModelEntry>) {
        val arr = JSONArray()
        for (e in list) arr.put(e.toJson())
        securePreferences.setModelLibraryJson(arr.toString())
    }

    private fun parseList(json: String): List<ModelEntry> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { ModelEntry.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.w(TAG, "parseList failed: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private const val TAG = "ModelLibrary"
    }
}
