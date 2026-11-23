package com.example.soundengitoolkit.ai

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
 * Control examples E1–E5 (docs/evaluation.md). Was Lab6BaselineTest: it recorded the behaviour at tag lab6-baseline.
 * Expectations were changed on purpose in step 2a (routing); the "before" values stay in evaluation.md.
 * Only the offline part is tested here: what goes to rules and what goes to the cloud.
 */
class Lab6ExamplesTest {
    /** Cloud stub: counts calls; either answers with [reply] or throws IOException (offline). */
    private class CloudStub(private val reply: String? = null) : CommandSource {
        var calls = 0
        override suspend fun ask(request: AiRequest): CommandReply {
            calls++
            return CommandReply(reply ?: throw java.io.IOException("offline"), "cloud")
        }
    }

    private val cloudReply = """{"actions":[],"reply":"stub"}"""

    private fun ask(prompt: String, cloud: CloudStub) =
        runBlocking { HybridSource(RulesSource(), cloud).ask(AiRequest(prompt)) }

    private fun actions(json: String) = JSONObject(json).getJSONArray("actions")

    // E1: simple command, rules, unchanged (regression anchor)
    @Test fun e1_fullCommand_rulesOnly() {
        val cloud = CloudStub()
        val r = ask("add delay 1/8 at 120 bpm and turn on compressor", cloud)
        assertEquals("rules", r.source)
        assertEquals(0, cloud.calls)
        assertEquals(3, actions(r.json).length())
        assertEquals(3, Ai.apply(Engine(), r.json).lines().count { it.startsWith("✓") })
    }

    // E2: not simple -> cloud. Offline the error is visible now (before: hidden "No actions recognized")
    @Test fun e2_warmer_goesToCloud_offlineErrorIsVisible() {
        val cloud = CloudStub()
        assertThrows(java.io.IOException::class.java) { ask("make sound warmer", cloud) }
        assertEquals(1, cloud.calls)
    }

    // E3: relative command is no longer caught by the word "delay": it goes to the cloud (with state in the chat)
    @Test fun e3_longerDelay_goesToCloud() {
        val cloud = CloudStub(cloudReply)
        assertEquals("cloud", ask("зроби delay довшим", cloud).source)
        assertEquals(1, cloud.calls)
    }

    // E4: "reduce reverb by half" -> cloud; reverb is no longer switched ON by the rules
    @Test fun e4_halfReverb_goesToCloud() {
        val cloud = CloudStub(cloudReply)
        assertEquals("cloud", ask("зменш reverb наполовину", cloud).source)
        assertEquals(1, cloud.calls)
    }

    // E5: "reverb off" is simple -> rules, offline, and it really switches reverb off (before: stayed on)
    @Test fun e5_reverbOff_rulesSwitchOff() {
        val cloud = CloudStub()
        val r = ask("reverb off", cloud)
        assertEquals("rules", r.source)
        assertEquals(0, cloud.calls)
        val e = Engine()
        e.reverb.on = true
        Ai.apply(e, r.json)
        assertFalse(e.reverb.on)
    }

    @Test fun e5b_delayOff_staysOff() {
        val e = Engine()
        Ai.apply(e, ask("delay off", CloudStub()).json)
        assertFalse(e.delay.on)
    }

    @Test fun e5c_ukrainianOff() {
        val e = Engine()
        e.comp.on = true
        Ai.apply(e, ask("вимкни компресор", CloudStub()).json)
        assertFalse(e.comp.on)
    }

    @Test fun e1_offline_stillWorks() {
        // whole E1 handled by rules: no exception even though the cloud is "offline"
        assertTrue(actions(ask("add delay 1/8 at 120 bpm and turn on compressor", CloudStub()).json).length() == 3)
    }
}
