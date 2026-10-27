package com.example.soundengitoolkit

import kotlinx.coroutines.CancellationException
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class AiErrorMapperTest {
    @Test fun io_isNetwork() = assertEquals(AiError.Network, AiErrorMapper.map(IOException("offline")))
    @Test fun json_isInvalidResponse() = assertEquals(AiError.InvalidResponse, AiErrorMapper.map(JSONException("bad")))
    @Test fun illegalState_isAiUnavailable() = assertEquals(AiError.AiUnavailable, AiErrorMapper.map(IllegalStateException("x")))
    @Test fun other_isUnknown() = assertEquals(AiError.Unknown, AiErrorMapper.map(RuntimeException("x")))
    @Test fun aiException_keepsCategory() =
        assertEquals(AiError.InvalidInput, AiErrorMapper.map(AiException(AiError.InvalidInput)))
    @Test fun cancellation_isRethrown() {
        assertThrows(CancellationException::class.java) { AiErrorMapper.map(CancellationException("c")) }
    }
}