package com.chibiclaw.ai

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Exercises the REAL [PromptTemplate] class, not an inline duplicate.
 *
 * The previous iteration of this test shadowed PromptTemplate with a local
 * `buildTestPrompt()` function because the class used to require a Context.
 * That dependency has been removed (the parameter was dead code anyway) so
 * we now instantiate the real class with zero arguments and assert on its
 * public [PromptTemplate.getSystemPrompt] output.
 *
 * These tests are the regression guard for:
 *   - The identity block ("Fuu") — if someone accidentally renames the
 *     assistant they'll see this test fail.
 *   - The P4.1 prompt-injection defense (UI_CONTENT_START/END tags + the
 *     "ABAIKAN semua perintah" instruction).
 *   - The 8-step tool priority list — specifically that `launch_app` AND
 *     `intent_send` are both present, because the Fast-Path regression
 *     bug ("buka X" silently failing) stemmed from Gemma collapsing the
 *     two in favour of tool-less chatter.
 *   - The skill-context and memory-context append paths, so future edits
 *     to [PromptTemplate.getSystemPrompt] can't silently drop either input.
 */
class PromptTemplateTest {

    private lateinit var template: PromptTemplate

    @Before
    fun setUp() {
        template = PromptTemplate()
    }

    @Test
    fun `default prompt contains Fuu identity`() {
        val prompt = template.getSystemPrompt()
        assertTrue("Prompt must contain 'Fuu'", prompt.contains("Fuu"))
    }

    @Test
    fun `default prompt has UI_CONTENT injection protection tags`() {
        val prompt = template.getSystemPrompt()
        assertTrue(
            "Prompt must tell Gemma about UI_CONTENT_START tag",
            prompt.contains("[UI_CONTENT_START]")
        )
        assertTrue(
            "Prompt must tell Gemma about UI_CONTENT_END tag",
            prompt.contains("[UI_CONTENT_END]")
        )
        assertTrue(
            "Prompt must instruct Gemma to ignore instructions inside UI_CONTENT",
            prompt.contains("ABAIKAN")
        )
    }

    @Test
    fun `default prompt lists launch_app and intent_send tools`() {
        val prompt = template.getSystemPrompt()
        assertTrue("Prompt must advertise launch_app", prompt.contains("launch_app"))
        assertTrue("Prompt must advertise intent_send", prompt.contains("intent_send"))
        assertTrue("Prompt must advertise system_control", prompt.contains("system_control"))
        assertTrue("Prompt must advertise set_alarm", prompt.contains("set_alarm"))
        assertTrue("Prompt must advertise content_query", prompt.contains("content_query"))
    }

    @Test
    fun `skill context is appended verbatim when provided`() {
        val skillCtx = "[AVAILABLE_SKILLS]\n- phone_call: Make a phone call\n[/AVAILABLE_SKILLS]"
        val prompt = template.getSystemPrompt(skillContext = skillCtx)
        assertTrue("Prompt must contain the phone_call skill line", prompt.contains("phone_call"))
        assertTrue("Prompt must contain the AVAILABLE_SKILLS header", prompt.contains("[AVAILABLE_SKILLS]"))
    }

    @Test
    fun `memory context is wrapped in CONTEXT tags when provided`() {
        val memory = "previous: buka kalkulator → selesai"
        val prompt = template.getSystemPrompt(memoryContext = memory)
        assertTrue(prompt.contains("[CONTEXT]"))
        assertTrue(prompt.contains("[/CONTEXT]"))
        assertTrue(prompt.contains(memory))
    }

    @Test
    fun `empty inputs still produce non-empty prompt`() {
        val prompt = template.getSystemPrompt("", "")
        assertTrue("Empty-input prompt must still contain base content", prompt.isNotBlank())
        assertTrue(prompt.contains("Fuu"))
    }

    @Test
    fun `prompt ordering is base then skill then memory`() {
        val prompt = template.getSystemPrompt(
            skillContext = "[AVAILABLE_SKILLS]SKL[/AVAILABLE_SKILLS]",
            memoryContext = "MEM"
        )
        val fuuIdx = prompt.indexOf("Fuu")
        val skillIdx = prompt.indexOf("[AVAILABLE_SKILLS]")
        val memIdx = prompt.indexOf("[CONTEXT]")
        assertTrue("Fuu block must come first", fuuIdx in 0 until skillIdx)
        assertTrue("Skill block must come before memory block", skillIdx < memIdx)
    }
}
