package com.chibiclaw.skills

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillRegistry @Inject constructor(
    private val skillLoader: SkillLoader
) {
    private val skills: MutableList<SkillDefinition> = mutableListOf()

    fun initialize() {
        skills.clear()
        skills.addAll(skillLoader.loadAll())
    }

    fun getAll(): List<SkillDefinition> = skills.toList()

    fun getByName(name: String): SkillDefinition? =
        skills.find { it.name.equals(name, ignoreCase = true) }

    /**
     * Agentic skill lookup — dipanggil oleh model via tool lookup_skill().
     *
     * Tier 1: cocokkan query terhadap trigger keywords setiap skill.
     * Tier 2: jika Tier 1 kosong, cocokkan berdasarkan kata-kata dalam
     *         nama dan deskripsi skill (word-overlap).
     *
     * Mengembalikan panduan terformat untuk max 2 skill yang paling relevan,
     * atau pesan fallback jika tidak ada yang cocok (model tetap bisa pakai
     * tool primitif tanpa panduan skill spesifik).
     */
    fun searchByQuery(query: String): String {
        if (skills.isEmpty()) return "Tidak ada skill terdaftar."
        val lower = query.lowercase()

        // Tier 1: trigger keyword match
        val byTrigger = skills.filter { skill ->
            skill.triggers.any { trigger ->
                lower.contains(trigger.lowercase()) || trigger.lowercase().contains(lower)
            }
        }
        if (byTrigger.isNotEmpty()) return formatSkillGuide(byTrigger.take(2))

        // Tier 2: word-overlap pada nama + deskripsi skill
        val queryWords = lower.split(" ").filter { it.length > 2 }.toSet()
        if (queryWords.isNotEmpty()) {
            val byWords = skills.filter { skill ->
                val text = "${skill.name} ${skill.description}".lowercase()
                queryWords.any { word -> text.contains(word) }
            }
            if (byWords.isNotEmpty()) return formatSkillGuide(byWords.take(2))
        }

        return "Tidak ada panduan skill spesifik untuk \"$query\". " +
            "Gunakan tool primitif yang tersedia (content_query, intent_send, device_control, dll)."
    }

    private fun formatSkillGuide(matched: List<SkillDefinition>): String =
        matched.joinToString("\n---\n") { skill ->
            buildString {
                append("[SKILL: ${skill.name}]\n")
                append("Deskripsi: ${skill.description}\n")
                if (skill.intentAction.isNotEmpty()) append("Intent action: ${skill.intentAction}\n")
                if (skill.intentUriTemplate.isNotEmpty()) append("URI template: ${skill.intentUriTemplate}\n")
                if (skill.targetPackage.isNotEmpty()) append("Package: ${skill.targetPackage}\n")
                if (skill.examples.isNotEmpty()) {
                    append("Contoh:\n")
                    skill.examples.take(2).forEach { append("  - $it\n") }
                }
            }.trimEnd()
        }
}
