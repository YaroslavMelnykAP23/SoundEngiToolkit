package com.example.soundengitoolkit.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.soundengitoolkit.audio.Engine

class ExplanationBuilderTest {
    /** Apply on a real Engine exactly like the ViewModel will: before -> apply -> after -> answer. */
    private fun run(e: Engine, actions: List<PlannedAction>, reply: String? = null, warnings: List<String> = emptyList()): ChatAnswer {
        val before = FxSnapshot.from(e)
        ActionExecutor.apply(e, actions.map { it.action })
        return ExplanationBuilder.build(actions, reply, warnings, before, FxSnapshot.from(e))
    }

    // AC-3: the listed changes are exactly the real difference (warmer: only EQ, nothing else)
    @Test fun changes_equalRealDiff() {
        val e = Engine()
        val a = run(e, listOf(PlannedAction(AiAction.EqFx(true, 4f, null, -3f), "More lows, fewer highs = warmer.")), "Warmer.")
        a as ChatAnswer.Changed
        assertEquals(listOf("EQ", "EQ low", "EQ high"), a.changes.map { it.param })
        assertEquals("+4 dB", a.changes[1].to)
        assertEquals("More lows, fewer highs = warmer.", a.why["eq"])
    }

    // AC-2: rules give no "why" -> default explanation, so every changed effect is explained
    @Test fun rules_withoutWhy_getDefaultExplanation() {
        val e = Engine(); e.reverb.on = true
        val a = run(e, listOf(PlannedAction(AiAction.ReverbFx(false, null, null))))
        a as ChatAnswer.Changed
        assertEquals("on", a.changes[0].from); assertEquals("off", a.changes[0].to)
        assertTrue(a.why["reverb"]!!.contains("drier"))
    }

    // E5 when reverb is already off: no fake success
    @Test fun alreadyOff_isNothingChanged() {
        val a = run(Engine(), listOf(PlannedAction(AiAction.ReverbFx(false, null, null))))
        assertTrue(a is ChatAnswer.NothingChanged)
        assertTrue((a as ChatAnswer.NothingChanged).message.startsWith("Nothing changed"))
    }

    // The bare {"fx":"eq","on":true} seen at 18:29: EQ "on" with flat bands is not an audible change
    @Test fun flatEqOn_isNothingChanged() {
        val a = run(Engine(), listOf(PlannedAction(AiAction.EqFx(true, null, null, null), "warmer")))
        a as ChatAnswer.NothingChanged
        assertTrue(a.message.contains("0 dB"))
    }

    @Test fun noActions_showsModelReply_orHelp() {
        assertEquals("Only effects.", (run(Engine(), emptyList(), "Only effects.") as ChatAnswer.NothingChanged).message)
        assertEquals(ExplanationBuilder.HELP, (run(Engine(), emptyList()) as ChatAnswer.NothingChanged).message)
    }

    @Test fun warnings_areShownInText() {
        val e = Engine()
        val a = run(e, listOf(PlannedAction(AiAction.DelayFx(true, null, 0.9f, null), "echo")), warnings = listOf("delay feedback 1.5 → 0.9"))
        val t = ExplanationBuilder.text(a)
        assertTrue(t.contains("• Delay: off → on")); assertTrue(t.contains("⚠ delay feedback 1.5 → 0.9"))
    }

    @Test fun unchangedParameters_areNotListed() {
        val e = Engine(); e.reverb.on = true
        val a = run(e, listOf(PlannedAction(AiAction.ReverbFx(true, 0.12f, null), "less reverb"))) as ChatAnswer.Changed
        assertEquals(listOf("Reverb mix"), a.changes.map { it.param })   // "Reverb: on" was already on
        assertFalse(ExplanationBuilder.text(a).contains("Reverb: "))
    }

    @Test fun summary_forHistory() {
        val e = Engine()
        val a = run(e, listOf(PlannedAction(AiAction.Bpm(90f))))
        assertEquals("BPM 120→90", ExplanationBuilder.summary(a))
    }

    // Lab 7, BUG-01: explicit "eq on" (rules) is listed as a change, with an honest note about flat bands
    @Test fun explicitEqOn_fromRules_isListed_withFlatBandsNote() {
        val e = Engine()
        val actions = listOf(PlannedAction(AiAction.EqFx(true, null, null, null)))
        val before = FxSnapshot.from(e)
        ActionExecutor.apply(e, actions.map { it.action })
        val a = ExplanationBuilder.build(actions, null, emptyList(), before, FxSnapshot.from(e), explicitCommand = true)
        a as ChatAnswer.Changed
        assertEquals(listOf("EQ"), a.changes.map { it.param })
        assertEquals("off", a.changes[0].from); assertEquals("on", a.changes[0].to)
        assertTrue(a.why["eq"]!!.contains("0 dB"))
        assertFalse(a.why["eq"]!!.contains("Try again"))
    }

    // Lab 7, BUG-01: "вимкни компресор і увімкни eq" lists both parts of the command
    @Test fun compressorOffAndEqOn_fromRules_listsBoth() {
        val e = Engine(); e.comp.on = true
        val actions = listOf(PlannedAction(AiAction.CompressorFx(false, null, null)), PlannedAction(AiAction.EqFx(true, null, null, null)))
        val before = FxSnapshot.from(e)
        ActionExecutor.apply(e, actions.map { it.action })
        val a = ExplanationBuilder.build(actions, null, emptyList(), before, FxSnapshot.from(e), explicitCommand = true)
        a as ChatAnswer.Changed
        assertEquals(listOf("Compressor", "EQ"), a.changes.map { it.param })
    }

    // Lab 7, BUG-06 (seen on device: {"actions":[{"fx":"bpm","why":"..."}],"reply":"Темп встановлено на 240"}):
    // the fact comes first, the model's text only as an attributed quote, never as "settings already match"
    @Test fun actionWithoutValues_factFirst_modelReplyQuoted() {
        val a = run(Engine(), listOf(PlannedAction(AiAction.Bpm(null), "set to 240")), "Tempo set to 240 bpm.")
        a as ChatAnswer.NothingChanged
        assertTrue(a.message.startsWith(ExplanationBuilder.NO_VALUES_SHORT))
        assertTrue(a.message.contains("AI said: \"Tempo set to 240 bpm.\""))
        assertFalse(a.message.contains("already match"))
    }

    @Test fun actionWithoutValues_noReply_suggestsRephrasing() {
        val a = run(Engine(), listOf(PlannedAction(AiAction.DelayFx(null, null, null, null))))
        assertEquals(ExplanationBuilder.NO_VALUES, (a as ChatAnswer.NothingChanged).message)
    }

    // A real no-op from the model (value equal to the current one) keeps the model's explanation, e.g. A-07
    @Test fun realNoOp_keepsModelReply() {
        val e = Engine(); e.delay.on = true; e.delay.denom = 4
        val a = run(e, listOf(PlannedAction(AiAction.DelayFx(null, 4, null, null), "x")), "Delay is already at the maximum length.")
        a as ChatAnswer.NothingChanged
        assertTrue(a.message.contains("already at the maximum"))
    }

    // Lab 7, BUG-07 (FT-05): model action equal to the current state + a reply claiming a change -> no "already match"
    @Test fun modelNoOp_withClaimingReply_factFirst_quoted() {
        val e = Engine(); e.reverb.on = true
        val a = run(e, listOf(PlannedAction(AiAction.ReverbFx(true, null, null), "x")), "Я додав реверберації.")
        a as ChatAnswer.NothingChanged
        assertTrue(a.message.startsWith(ExplanationBuilder.MATCHES_CURRENT))
        assertTrue(a.message.contains("AI said: \"Я додав реверберації.\""))
        assertFalse(a.message.contains("already match"))
    }

    // Rules no-op ("reverb off" when it is off) keeps the true "settings already match the request"
    @Test fun rulesNoOp_saysAlreadyMatch() {
        val actions = listOf(PlannedAction(AiAction.ReverbFx(false, null, null)))
        val s = FxSnapshot.from(Engine())
        val a = ExplanationBuilder.build(actions, null, emptyList(), s, s, explicitCommand = true)
        assertEquals("Nothing changed: the settings already match the request.", (a as ChatAnswer.NothingChanged).message)
    }
}
