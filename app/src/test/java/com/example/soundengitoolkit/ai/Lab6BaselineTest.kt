package com.example.soundengitoolkit.ai

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.soundengitoolkit.ai.data.HybridSource
import com.example.soundengitoolkit.ai.data.RulesSource
import com.example.soundengitoolkit.ai.domain.Ai
import com.example.soundengitoolkit.ai.domain.CommandReply
import com.example.soundengitoolkit.ai.domain.CommandSource
import com.example.soundengitoolkit.audio.Engine

/**
 * Lab 6, stage 1: ACTUAL behaviour of the AI command at tag lab6-baseline (offline path).
 * These tests record what the app does now, not what it should do.
 * When lab 6 changes a behaviour on purpose, the test is updated and the change goes to docs/evaluation.md.
 */
class Lab6BaselineTest {
    /** Cloud stub: counts calls; either answers with [reply] or throws. */
    private class CloudStub(private val reply: String? = null) : CommandSource {
        var calls = 0
        override suspend fun ask(prompt: String): CommandReply {
            calls++
            return CommandReply(reply ?: throw java.io.IOException("offline"), "cloud")
        }
    }

    private fun ask(prompt: String, cloud: CloudStub) =
        runBlocking { HybridSource(RulesSource(), cloud).ask(prompt) }

    private fun actions(json: String) = JSONObject(json).getJSONArray("actions")

    // E1: full command is handled by rules, no explanation anywhere
    @Test fun e1_fullCommand_rulesOnly() {
        val cloud = CloudStub()
        val r = ask("add delay 1/8 at 120 bpm and turn on compressor", cloud)
        assertEquals("rules", r.source)
        assertEquals(0, cloud.calls)
        assertEquals(3, actions(r.json).length())
        val log = Ai.apply(Engine(), r.json)
        assertEquals(3, log.lines().count { it.startsWith("✓") })
    }

    // E2: "warmer" is not in the rules; offline the cloud fails and the user gets an empty result
    @Test fun e2_warmer_offline_emptyAndHidden() {
        val cloud = CloudStub()
        val r = ask("make sound warmer", cloud)
        assertEquals(1, cloud.calls)
        assertEquals("rules (cloud failed)", r.source)          // L-25: shown as Success, no Retry
        assertEquals(0, actions(r.json).length())
    }

    // E3: relative command. Rules catch the word "delay" first, cloud is never asked,
    // division is reset to the default 8, which equals the current value: "success" with no real change
    @Test fun e3_longerDelay_rulesPreemptCloud_noRealChange() {
        val cloud = CloudStub("""{"actions":[{"fx":"delay","division":4}]}""")
        val r = ask("зроби delay довшим", cloud)
        assertEquals("rules", r.source)
        assertEquals(0, cloud.calls)
        val e = Engine()                                        // default delay: 1/8
        val log = Ai.apply(e, r.json)
        assertEquals(8, e.delay.denom)                          // not longer
        assertTrue(log.startsWith("✓"))
    }

    // E4: "reduce reverb by half" -> rules only know "reverb on": reverb is switched ON, mix unchanged
    @Test fun e4_halfReverb_turnsReverbOnInstead() {
        val cloud = CloudStub()
        val r = ask("зменш reverb наполовину", cloud)
        assertEquals("rules", r.source)
        assertEquals(0, cloud.calls)
        val e = Engine()
        assertFalse(e.reverb.on)
        Ai.apply(e, r.json)
        assertTrue(e.reverb.on)                                 // opposite of the intent
        assertEquals(0.25f, e.reverb.mix, 1e-6f)                // not halved
    }

    // E5: "reverb off" -> rules look only for the word "reverb" and always put on=true:
    // the effect stays ON, the log still says "✓ reverb on=true". Same for "delay off", "compressor off"
    @Test fun e5_reverbOff_staysOn() {
        val cloud = CloudStub()
        val r = ask("reverb off", cloud)
        assertEquals("rules", r.source)
        assertEquals(0, cloud.calls)
        val e = Engine()
        e.reverb.on = true                                      // user turned it on earlier
        val log = Ai.apply(e, r.json)
        assertTrue(e.reverb.on)                                 // not switched off
        assertTrue(log.startsWith("✓ reverb on=true"))
    }

    @Test fun e5b_delayOff_turnsDelayOn() {
        val e = Engine()                                        // delay is off by default
        Ai.apply(e, ask("delay off", CloudStub()).json)
        assertTrue(e.delay.on)                                  // opposite of the intent
    }
}
