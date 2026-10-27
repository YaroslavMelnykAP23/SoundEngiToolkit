package com.example.soundengitoolkit

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RunAiCommandUseCaseTest {
    private class Stub(private val reply: String? = null, private val error: Throwable? = null) : CommandSource {
        var calls = 0; var lastPrompt: String? = null
        override suspend fun ask(prompt: String): CommandReply {
            calls++; lastPrompt = prompt
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
}