package com.chibiclaw.ai.llm

import com.chibiclaw.agent.tools.ToolSpec
import com.chibiclaw.data.database.TaskChannel

/**
 * Format AgentPrompt → backend-specific text. Phase 1: Gemma instruct template.
 *
 * Phase 4 akan add Gemini, Claude, GPT format methods (different chat templates).
 */
object PromptBuilder {

    private const val SYSTEM_PROMPT_FUU = """
Kamu adalah Fuu, AI asisten yang berjalan di Android device milik Lendra.

Karakter kamu:
- Lembut, kawaii, profesional, to-the-point
- Bahasa default Indonesia kasual (boleh mix English kalau user code-switch)
- Tidak basa-basi panjang
- Selalu konfirmasi sebelum action sensitif (HIGH severity tool)

Kamu adalah AGENT, bukan chatbot. Untuk setiap task:
1. Pahami goal dari user atau trigger
2. Reason langkah-langkah yang perlu (di field "thought")
3. Emit tool call kalau perlu action (di field "tool_calls")
4. Observe tool result di iterasi berikutnya
5. Iterasi sampai task selesai (emit "next": "done") atau butuh user input ("next": "await_user")

Format response WAJIB JSON valid:
{
  "thought": "alasan kamu pilih action ini (string)",
  "tool_calls": [
    {"tool": "tool_name", "args": {"param": "value"}}
  ],
  "next": "continue" | "done" | "await_user" | "escalate",
  "summary": "ringkasan task (kalau next=done)",
  "question": "pertanyaan ke user (kalau next=await_user)",
  "emotion": "joy | sad | angry | surprised | neutral | satisfied | uncertain (optional)"
}

Pakai memory_remember kalau ada fakta penting tentang user yang perlu diingat.
Pakai memory_recall kalau perlu lookup info user.
"""

    fun toGemmaFormat(prompt: AgentPrompt): String = buildString {
        append("<start_of_turn>system\n")
        append(SYSTEM_PROMPT_FUU)
        append("\n\n## Persona\n")
        append(prompt.personaTraits)
        append("\n\n## World State\n")
        append(prompt.worldSnapshot)
        if (prompt.relevantMemory.isNotEmpty()) {
            append("\n\n## Relevant Memory\n")
            prompt.relevantMemory.forEach { append("- $it\n") }
        }
        append("\n\n## Available Tools\n")
        prompt.toolCatalog.forEach { tool ->
            append(formatTool(tool))
            append("\n\n")
        }
        if (prompt.emotionSignal != null) {
            append("\n## Emotion Signal\n")
            append(prompt.emotionSignal)
            append("\n")
        }
        append("<end_of_turn>\n")

        append("<start_of_turn>user\n")
        append("## Task Goal\n")
        append(prompt.taskGoal)
        append("\n\n## Channel: ${prompt.taskChannel}\n")
        if (prompt.taskHistory.isNotEmpty()) {
            append("\n## Task History (this task)\n")
            prompt.taskHistory.forEach { append("$it\n") }
        }
        if (prompt.recentTasks.isNotEmpty()) {
            append("\n## Recent Tasks (other tasks for context)\n")
            prompt.recentTasks.forEach { append("- $it\n") }
        }
        append("\n\nIteration ${prompt.iteration}/${prompt.maxIteration}. Respond in JSON only.\n")
        append("<end_of_turn>\n")

        append("<start_of_turn>model\n")
    }

    private fun formatTool(tool: ToolSpec): String = buildString {
        append("[${tool.name}]\n")
        append("Description: ${tool.description.trim()}\n")
        if (tool.parameters.isNotEmpty()) {
            append("Args: ")
            append(tool.parameters.entries.joinToString(", ") { (name, type) -> "$name ($type)" })
            append("\n")
        }
        append("Latency: ${tool.capability.latencyMsRange.first}-${tool.capability.latencyMsRange.last}ms · ")
        append("Severity: ${tool.safety.severity}\n")
        if (tool.capability.knownFail.isNotEmpty()) {
            append("Known fail: ${tool.capability.knownFail.joinToString(", ")}\n")
        }
    }
}
