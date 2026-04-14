package com.chibiclaw.skills

import kotlinx.serialization.Serializable

@Serializable
data class SkillDefinition(
    val name: String,
    val description: String,
    val triggers: List<String>,
    val defaultTier: Int = 1,
    val intentAction: String = "",
    val intentUriTemplate: String = "",
    val targetPackage: String = "",
    val examples: List<String> = emptyList()
)
