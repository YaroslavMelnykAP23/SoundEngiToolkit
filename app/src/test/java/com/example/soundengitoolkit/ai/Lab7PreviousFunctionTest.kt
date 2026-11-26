package com.example.soundengitoolkit.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.soundengitoolkit.ai.data.HybridSource
import com.example.soundengitoolkit.ai.data.RulesSource
import com.example.soundengitoolkit.ai.domain.Ai
import com.example.soundengitoolkit.ai.domain.AiRequest
import com.example.soundengitoolkit.ai.domain.CommandReply
import com.example.soundengitoolkit.ai.domain.CommandSource
import com.example.soundengitoolkit.audio.Engine

/**
 * Lab 7, task 4: regression of the previous AI function (simple commands handled by rules, Lab 5).
 * Examples P-01…P-05 from docs/evaluation.md, section 6. The cloud must never be called: these work offline.
 */
class Lab7PreviousFunctionTest {
    private class OfflineCloud : CommandSource {
        var calls = 0
        override suspend fun ask(request: AiRequest): CommandReply { calls++; throw java.io.IOException("offline") }
    }

    /** Runs the prompt through the real routing on a fresh Engine; returns the Engine after applying. */
    private fun run(prompt: String, prepare: Engine.() -> Unit = {}): Engine {
        val cloud = OfflineCloud()
        val reply = runBlocking { HybridSource(RulesSource(), cloud).ask(AiRequest(prompt)) }
        assertEquals("rules", reply.source)
        assertEquals(0, cloud.calls)
        return Engine().apply(prepare).also { Ai.apply(it, reply.json) }
    }

    // P-01 = E1
    @Test fun p01_fullCommand() {
        val e = run("add delay 1/8 at 120 bpm and turn on compressor") { bpm = 90f }
        assertEquals(120f, e.bpm); assertTrue(e.delay.on); assertEquals(8, e.delay.denom); assertTrue(e.comp.on)
    }

    // P-02 = E5
    @Test fun p02_reverbOff() {
        val e = run("reverb off") { reverb.on = true }
        assertFalse(e.reverb.on)
    }

    @Test fun p03_tempoUkrainian() {
        assertEquals(90f, run("темп 90").bpm)
    }

    @Test fun p04_delaySixteenthUkrainian() {
        val e = run("ділей 1/16")
        assertTrue(e.delay.on); assertEquals(16, e.delay.denom)
    }

    @Test fun p05_twoSwitches() {
        val e = run("вимкни компресор і увімкни eq") { comp.on = true }
        assertFalse(e.comp.on); assertTrue(e.eq.on)
    }

    // Lab 7, BUG-04 (A-08): out-of-range tempo -> rules, no cloud call, clamped to 240 with a warning
    @Test fun a08_tempo300_rulesClampTo240_withWarning() = runBlocking {
        val cloud = OfflineCloud()
        val plan = com.example.soundengitoolkit.ai.domain.RunAiChatUseCase(HybridSource(RulesSource(), cloud))(
            "set the tempo to 300 bpm", com.example.soundengitoolkit.ai.domain.FxSnapshot.from(Engine()), emptyList()
        ).getOrThrow()
        assertEquals("rules", plan.source)
        assertEquals(0, cloud.calls)
        assertEquals(com.example.soundengitoolkit.ai.domain.AiAction.Bpm(240f), plan.actions.single().action)
        assertTrue(plan.warnings.single().contains("240"))
    }
}
