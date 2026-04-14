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

    fun toContextString(): String {
        if (skills.isEmpty()) return ""
        val sb = StringBuilder("[AVAILABLE_SKILLS — referensi siap pakai, bukan daftar tool]\n")
        skills.forEach { skill ->
            sb.append("- ${skill.name}: ${skill.description}\n")
            sb.append("  Triggers: ${skill.triggers.take(3).joinToString(", ")}\n")
            if (skill.intentAction.isNotEmpty()) {
                sb.append("  Intent action: ${skill.intentAction}\n")
            }
            if (skill.intentUriTemplate.isNotEmpty()) {
                sb.append("  URI template: ${skill.intentUriTemplate}\n")
            }
            if (skill.targetPackage.isNotEmpty()) {
                sb.append("  Package: ${skill.targetPackage}\n")
            }
        }
        sb.append("[/AVAILABLE_SKILLS]\n")
        sb.append("CATATAN: Kalau skill di atas tidak punya 'Intent action', " +
            "itu artinya skill ini DIKERJAKAN oleh system_control (senter/volume/brightness), " +
            "bukan intent_send.")
        return sb.toString()
    }
}
