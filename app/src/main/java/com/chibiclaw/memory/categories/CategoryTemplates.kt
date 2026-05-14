package com.chibiclaw.memory.categories

import com.chibiclaw.data.database.MemoryCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Category templates — schema validator + suggested keys per MemoryCategory.
 *
 * Goal: konsistensi struktur valueJson per kategori supaya:
 *  - LLM tau field apa yang umum (lebih akurat saat emit memory_remember)
 *  - Inspector UI bisa render structured form per kategori
 *  - Pattern miner bisa anticipate field yang ada
 *
 * Validator opsional — Phase 7 minimal warn kalau field missing,
 * tidak block (LLM bisa add metadata custom).
 */
object CategoryTemplates {

    private val parser = Json { ignoreUnknownKeys = true }

    /** Schema descriptor per kategori — dipakai oleh LLM prompt + inspector UI. */
    fun describe(category: MemoryCategory): CategoryTemplate = when (category) {
        MemoryCategory.USER_PROFILE -> USER_PROFILE
        MemoryCategory.CONTACT -> CONTACT
        MemoryCategory.HABIT -> HABIT
        MemoryCategory.FACT -> FACT
        MemoryCategory.PREFERENCE -> PREFERENCE
    }

    /** True kalau valueJson punya minimal field yang direkomendasikan kategori. */
    fun validate(category: MemoryCategory, valueJson: String): ValidationResult {
        val template = describe(category)
        val obj = runCatching { parser.parseToJsonElement(valueJson).jsonObject }.getOrNull()
            ?: return ValidationResult(false, listOf("valueJson bukan JSON object valid"))
        val missing = template.recommendedFields.filter { it !in obj.keys }
        return ValidationResult(
            valid = true,
            warnings = missing.map { "Field rekomendasi '$it' tidak ada (bisa diisi nanti)" },
        )
    }

    /** Prompt snippet untuk LLM saat memory_remember (di-inject via PromptBuilder). */
    fun llmPromptHint(): String = buildString {
        append("Kategori memory + field yang umum:\n")
        listOf(USER_PROFILE, CONTACT, HABIT, FACT, PREFERENCE).forEach { tmpl ->
            append("- ${tmpl.category.name}: ${tmpl.summary}\n")
            append("  Field umum: ${tmpl.recommendedFields.joinToString(", ")}\n")
        }
    }

    // ── Templates ────────────────────────────────────────────────────────────

    val USER_PROFILE = CategoryTemplate(
        category = MemoryCategory.USER_PROFILE,
        summary = "Identitas user (nama, panggilan, timezone, bahasa, role)",
        recommendedFields = listOf(
            "name", "pronoun", "timezone", "language_primary", "language_secondary", "role",
        ),
        keyExamples = listOf("identity", "language", "role", "name"),
    )

    val CONTACT = CategoryTemplate(
        category = MemoryCategory.CONTACT,
        summary = "Kontak yang user kenal (nama, hubungan, no HP, alias)",
        recommendedFields = listOf(
            "name", "relation", "phone", "whatsapp_handle", "telegram_handle", "aliases",
        ),
        keyExamples = listOf("contact:mama", "contact:budi", "contact:bos"),
    )

    val HABIT = CategoryTemplate(
        category = MemoryCategory.HABIT,
        summary = "Pola berulang user (jam bangun, jam tidur, jadwal rutin)",
        recommendedFields = listOf(
            "name", "schedule", "frequency", "context", "first_observed_at", "confidence_evidence",
        ),
        keyExamples = listOf("habit:morning_routine", "habit:gym_schedule"),
    )

    val FACT = CategoryTemplate(
        category = MemoryCategory.FACT,
        summary = "Fakta umum tentang dunia user (alamat, tempat kerja, hobi)",
        recommendedFields = listOf(
            "topic", "value", "source", "asserted_at",
        ),
        keyExamples = listOf("fact:office_address", "fact:home_city", "fact:dietary"),
    )

    val PREFERENCE = CategoryTemplate(
        category = MemoryCategory.PREFERENCE,
        summary = "Preferensi user (gaya komunikasi, app favorit, kebijakan)",
        recommendedFields = listOf(
            "domain", "preference", "rationale", "weight",
        ),
        keyExamples = listOf("pref:reply_tone", "pref:wake_word", "pref:music_app"),
    )
}

data class CategoryTemplate(
    val category: MemoryCategory,
    val summary: String,
    val recommendedFields: List<String>,
    val keyExamples: List<String>,
)

data class ValidationResult(
    val valid: Boolean,
    val warnings: List<String> = emptyList(),
)
