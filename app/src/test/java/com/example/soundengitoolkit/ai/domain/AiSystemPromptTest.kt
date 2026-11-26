package com.example.soundengitoolkit.ai.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lab 7: guards the wording of Ai.SYSTEM that caused defects found on the control set.
 * The model itself is checked on the device (docs/evaluation.md, section 6); here only the instruction text.
 */
class AiSystemPromptTest {
    private val p = Ai.SYSTEM

    // BUG-02: "a smaller division" was read by the model as "a shorter note" (1/4 -> 1/16)
    @Test fun delayDirection_isUnambiguous() {
        assertFalse(p.contains("smaller division"))
        assertTrue(p.contains("4 = 1/4 note = the LONGEST"))
        assertTrue(p.contains("16 = 1/16 = the SHORTEST"))
        assertTrue(p.contains("already at the limit"))
    }

    // BUG-03: answers came in Russian / English to a Ukrainian request
    @Test fun answerLanguage_followsPrompt_neverRussian() {
        assertTrue(p.contains("same language as \"prompt\""))
        assertTrue(p.contains("Never answer in Russian"))
    }

    // The Lab 7 fixes must not add examples from the control set (no fitting to the test).
    // "punchier", "more space" were already in the Lab 6 list of colorings: noted in evaluation.md 6.5.
    @Test fun noControlSetExamples() {
        listOf("make sound warmer", "300 bpm", "зроби delay довшим", "наполовину")
            .forEach { assertFalse("prompt contains control example: $it", p.contains(it, ignoreCase = true)) }
    }

    // BUG-06: the model returned {"fx":"delay","why":...} / {"fx":"bpm","why":...} without values
    @Test fun actionsMustHaveValues() {
        assertTrue(p.contains("every action must contain at least one value"))
    }
}
