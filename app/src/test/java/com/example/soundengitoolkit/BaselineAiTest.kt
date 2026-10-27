package com.example.soundengitoolkit

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BaselineAiTest {
    private class Stub(private val reply: String? = null, private val error: Throwable? = null) : CommandSource {
        override suspend fun ask(prompt: String): CommandReply {
            error?.let { throw it }
            return CommandReply(reply!!, "stub")
        }
    }

    private fun rules(p: String) =
        JSONObject(runBlocking { RulesSource().ask(p) }.json).getJSONArray("actions")

    // ---- RulesSource ----

    @Test fun rules_fullExample() {
        val a = rules("add delay 1/8 at 120 bpm and turn on compressor")
        assertEquals(3, a.length())
        assertEquals("bpm", a.getJSONObject(0).getString("fx"))
        assertEquals(120, a.getJSONObject(0).getInt("value"))
        assertEquals(8, a.getJSONObject(1).getInt("division"))
        assertTrue(a.getJSONObject(1).getBoolean("on"))
        assertEquals("compressor", a.getJSONObject(2).getString("fx"))
    }

    @Test fun rules_unrecognized_isEmpty() = assertEquals(0, rules("hello").length())

    @Test fun rules_ukrainianDelay_division16() =
        assertEquals(16, rules("ділей 1/16").getJSONObject(0).getInt("division"))

    // ---- Ai.apply ----

    @Test fun apply_delay_setsFields() {
        val e = Engine()
        val log = Ai.apply(e, """{"actions":[{"fx":"delay","on":true,"division":16,"feedback":0.5,"mix":0.2}]}""")
        assertTrue(e.delay.on); assertEquals(16, e.delay.denom)
        assertEquals(0.5f, e.delay.fb, 1e-6f); assertEquals(0.2f, e.delay.mix, 1e-6f)
        assertTrue(log.startsWith("✓"))
    }

    @Test fun apply_unknownFx_isLogged() {
        assertEquals("? foo\n", Ai.apply(Engine(), """{"actions":[{"fx":"foo"}]}"""))
    }

    @Test fun apply_eqWithoutOn_enablesAndResets() {          // L-04
        val e = Engine()
        Ai.apply(e, """{"actions":[{"fx":"eq","low":3}]}""")
        assertTrue(e.eq.on); assertEquals(3f, e.eq.lowDb, 1e-6f); assertEquals(0f, e.eq.midDb, 1e-6f)
    }

    @Test fun apply_compressorMissingFields_resetToDefaults() {
        val e = Engine(); e.comp.set(-30f, 8f)
        Ai.apply(e, """{"actions":[{"fx":"compressor","on":true}]}""")
        assertEquals(-18f, e.comp.thr, 1e-6f); assertEquals(4f, e.comp.ratio, 1e-6f)
    }

    @Test fun apply_invalidJson_throws() {
        assertThrows(JSONException::class.java) { Ai.apply(Engine(), "not json") }
    }

    @Test fun apply_invalidAction_appliesNothing() {   // deliberate change, step 3 (was: partial apply)
        val e = Engine()
        assertThrows(JSONException::class.java) {
            Ai.apply(e, """{"actions":[{"fx":"reverb","on":true},{}]}""")
        }
        assertFalse(e.reverb.on)
    }
    // ---- HybridSource ----

    @Test fun hybrid_cloudFails_fallsBackToRules() = runBlocking {
        val h = HybridSource(RulesSource(), Stub(error = java.io.IOException("offline")))
        val r = h.ask("make it warmer")
        assertEquals(0, JSONObject(r.json).getJSONArray("actions").length())
        assertEquals("rules (cloud failed)", r.source)
    }

    @Test fun hybrid_rulesRecognized_cloudNotNeeded() = runBlocking {
        val h = HybridSource(RulesSource(), Stub(error = IllegalStateException("must not be called")))
        assertEquals("rules", h.ask("reverb").source)
    }

    @Test fun hybrid_cloudSucceeds_sourceIsCloud() = runBlocking {
        val h = HybridSource(RulesSource(), Stub(reply = """{"actions":[{"fx":"reverb","on":true}]}"""))
        val r = h.ask("make it warmer")
        assertEquals("cloud", r.source)
        assertEquals(1, JSONObject(r.json).getJSONArray("actions").length())
    }

    @Test fun hybrid_cancellation_isNotSwallowed() {
        val h = HybridSource(RulesSource(), Stub(error = CancellationException("cancelled")))
        assertThrows(CancellationException::class.java) { runBlocking { h.ask("make it warmer") } }
    }
}