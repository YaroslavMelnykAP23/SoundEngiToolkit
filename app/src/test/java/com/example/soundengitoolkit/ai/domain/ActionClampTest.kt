package com.example.soundengitoolkit.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionClampTest {
    private fun one(a: AiAction) = ActionClamp.clamp(listOf(PlannedAction(a, "why")))

    // AC-5: the dangerous value from baseline (feedback 1.5) is limited and the user is warned
    @Test fun feedback_1_5_isLimitedTo_0_9_withWarning() {
        val r = one(AiAction.DelayFx(true, null, 1.5f, null))
        assertEquals(AiAction.DelayFx(true, null, 0.9f, null), r.actions[0].action)
        assertEquals(1, r.warnings.size)
        assertTrue(r.warnings[0].startsWith("delay feedback 1.5 → 0.9"))
    }

    @Test fun bpm_300_isLimitedTo_240() = assertEquals(AiAction.Bpm(240f), one(AiAction.Bpm(300f)).actions[0].action)
    @Test fun eqBands_areLimited() = assertEquals(AiAction.EqFx(true, 12f, -12f, 3f), one(AiAction.EqFx(true, 20f, -30f, 3f)).actions[0].action)
    @Test fun division_isSnappedToNearestAllowed() = assertEquals(AiAction.DelayFx(null, 16, null, null), one(AiAction.DelayFx(null, 32, null, null)).actions[0].action)

    @Test fun valuesInRange_unchanged_noWarnings() {
        val a = AiAction.CompressorFx(true, -20f, 4f)
        val r = one(a)
        assertEquals(a, r.actions[0].action); assertTrue(r.warnings.isEmpty())
    }

    @Test fun whyIsKept() = assertEquals("why", one(AiAction.Bpm(300f)).actions[0].why)
}
