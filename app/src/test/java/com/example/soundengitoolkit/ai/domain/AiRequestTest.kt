package com.example.soundengitoolkit.ai.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.soundengitoolkit.audio.Engine

class AiRequestTest {
    @Test fun blank_isInvalidInput() {
        val e = assertThrows(AiException::class.java) { AiRequestBuilder.build("  ") }
        assertEquals(AiError.InvalidInput, e.error)
    }

    @Test fun prompt_isTrimmed_historyLimitedToLast3() {
        val h = (1..5).map { ChatTurn("p$it", "r$it") }
        val r = AiRequestBuilder.build("  hi  ", null, h)
        assertEquals("hi", r.prompt)
        assertEquals(listOf("p3", "p4", "p5"), r.history.map { it.prompt })
    }

    @Test fun userMessage_containsOnlyStateHistoryAndPrompt() {
        val e = Engine(); e.reverb.on = true
        val msg = JSONObject(AiRequest("less reverb", FxState.from(e), listOf(ChatTurn("reverb on", "reverb on"))).toUserMessage())
        assertEquals(setOf("state", "history", "prompt"), msg.keys().asSequence().toSet())
        assertTrue(msg.getJSONObject("state").getJSONObject("reverb").getBoolean("on"))
        assertEquals(0.25, msg.getJSONObject("state").getJSONObject("reverb").getDouble("mix"), 1e-9)
        assertEquals(8, msg.getJSONObject("state").getJSONObject("delay").getInt("division"))
        assertEquals("less reverb", msg.getString("prompt"))
    }

    @Test fun userMessage_withoutState_hasOnlyPrompt() {
        val msg = JSONObject(AiRequest("x").toUserMessage())
        assertEquals(setOf("prompt"), msg.keys().asSequence().toSet())
        assertFalse(msg.has("state"))
    }
}
