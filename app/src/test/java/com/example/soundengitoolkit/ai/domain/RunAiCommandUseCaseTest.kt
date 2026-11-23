package com.example.soundengitoolkit.ai.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RunAiCommandUseCaseTest {
    private class Stub(private val reply: String? = null, private val error: Throwable? = null) : CommandSource {
        var calls = 0; var lastPrompt: String? = null; var lastRequest: AiRequest? = null
        override suspend fun ask(request: AiRequest): CommandReply {
            calls++; lastPrompt = request.prompt; lastRequest = request
            error?.let { throw it }
            return CommandReply(reply!!, "stub")
        }
    }

    private fun errorOf(r: Result<AiReply>) = (r.exceptionOrNull() as AiException).error

    @Test fun success_returnsTypedActionsAndSource() = runBlocking {
        val uc = RunAiCommandUseCase(Stub("""{"actions":[{"fx":"reverb","on":true}]}"""))
        val r = uc("add reverb").getOrThrow()
        assertEquals(listOf<AiAction>(AiAction.ReverbFx(true, null, null)), r.actions)
        assertEquals("stub", r.source)
    }

    @Test fun blankPrompt_isInvalidInput_andSourceNotCalled() = runBlocking {
        val s = Stub("""{"actions":[]}""")
        val r = RunAiCommandUseCase(s)("   ")
        assertEquals(AiError.InvalidInput, errorOf(r))
        assertEquals(0, s.calls)
    }

    @Test fun prompt_isTrimmed() = runBlocking {
        val s = Stub("""{"actions":[]}""")
        RunAiCommandUseCase(s)("  hi  ")
        assertEquals("hi", s.lastPrompt)
    }

    @Test fun ioError_isNetwork() = runBlocking {
        assertEquals(AiError.Network, errorOf(RunAiCommandUseCase(Stub(error = IOException("offline")))("x")))
    }

    @Test fun badJson_isInvalidResponse() = runBlocking {
        assertEquals(AiError.InvalidResponse, errorOf(RunAiCommandUseCase(Stub("not json"))("x")))
    }

    @Test fun unexpectedException_isUnknown_withoutRawMessage() = runBlocking {
        val r = RunAiCommandUseCase(Stub(error = RuntimeException("secret detail")))("x")
        assertEquals(AiError.Unknown, errorOf(r))
        assertTrue(r.isFailure)
    }

    @Test fun cancellation_isNotWrapped() {
        val uc = RunAiCommandUseCase(Stub(error = CancellationException("c")))
        assertThrows(CancellationException::class.java) { runBlocking { uc("x") } }
    }

    // ---- lab 6, step 2a ----

    @Test fun tooLongPrompt_isInvalidInput_andSourceNotCalled() = runBlocking {
        val s = Stub("""{"actions":[]}""")
        val r = RunAiCommandUseCase(s)("x".repeat(AiRequestBuilder.MAX_PROMPT + 1))
        assertEquals(AiError.InvalidInput, errorOf(r))
        assertEquals(0, s.calls)
    }

    @Test fun oldScenario_sendsNoStateAndNoHistory() = runBlocking {
        val s = Stub("""{"actions":[]}""")
        RunAiCommandUseCase(s)("make it warmer")
        assertNull(s.lastRequest!!.state)
        assertTrue(s.lastRequest!!.history.isEmpty())
    }

    /** Slow model: the user gets Timeout after 20 s instead of waiting for the SDK (minutes). Virtual time. */
    @Test fun slowSource_isTimeout() = runTest {
        val slow = object : CommandSource {
            override suspend fun ask(request: AiRequest): CommandReply { delay(60_000); return CommandReply("""{"actions":[]}""", "slow") }
        }
        assertEquals(AiError.Timeout, errorOf(RunAiCommandUseCase(slow)("x")))
    }

    @Test fun answerJustBeforeTimeout_isSuccess() = runTest {
        val s = object : CommandSource {
            override suspend fun ask(request: AiRequest): CommandReply { delay(AiCall.TIMEOUT_MS - 1); return CommandReply("""{"actions":[]}""", "slow") }
        }
        assertTrue(RunAiCommandUseCase(s)("x").isSuccess)
    }

    @Test fun busy_isBusy() = runBlocking {
        assertEquals(AiError.Busy, errorOf(RunAiCommandUseCase(Stub(error = AiException(AiError.Busy)))("x")))
    }
}
