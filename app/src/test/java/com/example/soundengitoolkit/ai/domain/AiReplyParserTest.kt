package com.example.soundengitoolkit.ai.domain

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AiReplyParserTest {
    @Test fun whyAndReply_areRead() {
        val r = AiReplyParser.parse("""{"actions":[{"fx":"eq","on":true,"low":4,"why":"More lows = warmer."}],"reply":"Warmer now."}""")
        assertEquals(listOf(PlannedAction(AiAction.EqFx(true, 4f, null, null), "More lows = warmer.")), r.actions)
        assertEquals("Warmer now.", r.reply)
    }

    @Test fun rulesJson_withoutWhyAndReply() {
        val r = AiReplyParser.parse("""{"actions":[{"fx":"reverb","on":false}]}""")
        assertEquals(listOf(PlannedAction(AiAction.ReverbFx(false, null, null), null)), r.actions)
        assertNull(r.reply)
    }

    @Test fun emptyActions_keepReply() {
        val r = AiReplyParser.parse("""{"actions":[],"reply":"I only control effects."}""")
        assertEquals(0, r.actions.size); assertEquals("I only control effects.", r.reply)
    }

    @Test fun blankWhy_isNull() =
        assertNull(AiReplyParser.parse("""{"actions":[{"fx":"bpm","value":90,"why":"  "}]}""").actions[0].why)

    @Test fun invalidJson_throws() {
        assertThrows(JSONException::class.java) { AiReplyParser.parse("not json") }
    }
}
