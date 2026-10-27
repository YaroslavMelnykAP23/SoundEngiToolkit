package com.example.soundengitoolkit

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AiActionMapperTest {
    @Test fun delay_allFields() {
        val a = AiActionMapper.parse("""{"actions":[{"fx":"delay","on":true,"division":16,"feedback":0.5,"mix":0.2}]}""")
        assertEquals(listOf<AiAction>(AiAction.DelayFx(true, 16, 0.5f, 0.2f)), a)
    }

    @Test fun missingFields_areNull() {
        val a = AiActionMapper.parse("""{"actions":[{"fx":"eq","low":3}]}""")
        assertEquals(listOf<AiAction>(AiAction.EqFx(null, 3f, null, null)), a)
    }

    @Test fun unknownFx_keptAsUnknown() {
        assertEquals(listOf<AiAction>(AiAction.Unknown("foo")), AiActionMapper.parse("""{"actions":[{"fx":"foo"}]}"""))
    }

    @Test fun order_isPreserved() {
        val a = AiActionMapper.parse("""{"actions":[{"fx":"bpm","value":90},{"fx":"reverb","on":true}]}""")
        assertEquals(AiAction.Bpm(90f), a[0]); assertEquals(AiAction.ReverbFx(true, null, null), a[1])
    }

    @Test fun invalidJson_throws() {
        assertThrows(JSONException::class.java) { AiActionMapper.parse("not json") }
    }

    @Test fun missingFx_throws() {
        assertThrows(JSONException::class.java) { AiActionMapper.parse("""{"actions":[{}]}""") }
    }
}