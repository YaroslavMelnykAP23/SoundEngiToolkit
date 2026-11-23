package com.example.soundengitoolkit.ai.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.soundengitoolkit.ai.data.FakeCommandSource
import com.example.soundengitoolkit.audio.Engine

class RunAiChatUseCaseTest {
    private val state = FxSnapshot.from(Engine())
    private fun errorOf(r: Result<AiPlan>) = (r.exceptionOrNull() as AiException).error

    @Test fun success_planWithWhyAndReply() = runBlocking {
        val p = RunAiChatUseCase(FakeCommandSource())("add reverb", state, emptyList()).getOrThrow()
        assertEquals(AiAction.ReverbFx(true, null, null), p.actions[0].action)
        assertNotNull(p.actions[0].why); assertEquals("Turned reverb on.", p.reply)
    }

    @Test fun stateAndHistory_reachTheSource() = runBlocking {
        val fake = FakeCommandSource()
        val h = (1..5).map { ChatTurn("p$it", "r$it") }
        RunAiChatUseCase(fake)("more", state, h)
        assertEquals(state, fake.lastRequest!!.state)
        assertEquals(3, fake.lastRequest!!.history.size)
    }

    @Test fun outOfRange_isClamped_withWarning() = runBlocking {
        val src = object : CommandSource {
            override suspend fun ask(request: AiRequest) =
                CommandReply("""{"actions":[{"fx":"delay","on":true,"feedback":1.5,"why":"x"}],"reply":"ok"}""", "stub")
        }
        val p = RunAiChatUseCase(src)("feedback 1.5", state, emptyList()).getOrThrow()
        assertEquals(AiAction.DelayFx(true, null, 0.9f, null), p.actions[0].action)
        assertEquals(1, p.warnings.size)
    }

    // AC-6
    @Test fun blank_isInvalidInput() = runBlocking { assertEquals(AiError.InvalidInput, errorOf(RunAiChatUseCase(FakeCommandSource())(" ", state, emptyList()))) }
    @Test fun offline_isNetwork() = runBlocking { assertEquals(AiError.Network, errorOf(RunAiChatUseCase(FakeCommandSource(FakeCommandSource.Mode.NETWORK_ERROR))("x", state, emptyList()))) }
    @Test fun config_isAiUnavailable() = runBlocking { assertEquals(AiError.AiUnavailable, errorOf(RunAiChatUseCase(FakeCommandSource(FakeCommandSource.Mode.UNAVAILABLE))("x", state, emptyList()))) }
    @Test fun busy_isBusy() = runBlocking { assertEquals(AiError.Busy, errorOf(RunAiChatUseCase(FakeCommandSource(FakeCommandSource.Mode.BUSY))("x", state, emptyList()))) }
    @Test fun invalidJson_isInvalidResponse() = runBlocking { assertEquals(AiError.InvalidResponse, errorOf(RunAiChatUseCase(FakeCommandSource(FakeCommandSource.Mode.INVALID_JSON))("x", state, emptyList()))) }
    @Test fun slow_isTimeout() = runTest { assertEquals(AiError.Timeout, errorOf(RunAiChatUseCase(FakeCommandSource(delayMs = 60_000))("x", state, emptyList()))) }

    @Test fun noActions_isSuccessWithReply() = runBlocking {
        val p = RunAiChatUseCase(FakeCommandSource(FakeCommandSource.Mode.NO_ACTIONS))("weather?", state, emptyList()).getOrThrow()
        assertEquals(0, p.actions.size); assertNotNull(p.reply)
    }

    // ---- moved from RunAiCommandUseCaseTest when the old "AI command" block was removed ----

    private class Stub(private val reply: String? = null, private val error: Throwable? = null) : CommandSource {
        var calls = 0; var lastPrompt: String? = null
        override suspend fun ask(request: AiRequest): CommandReply {
            calls++; lastPrompt = request.prompt
            error?.let { throw it }
            return CommandReply(reply!!, "stub")
        }
    }

    @Test fun tooLongPrompt_isInvalidInput_andSourceNotCalled() = runBlocking {
        val s = Stub("""{"actions":[]}""")
        val r = RunAiChatUseCase(s)("x".repeat(AiRequestBuilder.MAX_PROMPT + 1), state, emptyList())
        assertEquals(AiError.InvalidInput, errorOf(r)); assertEquals(0, s.calls)
    }

    @Test fun prompt_isTrimmed() = runBlocking {
        val s = Stub("""{"actions":[]}""")
        RunAiChatUseCase(s)("  hi  ", state, emptyList())
        assertEquals("hi", s.lastPrompt)
    }

    @Test fun unexpectedException_isUnknown_withoutRawMessage() = runBlocking {
        val r = RunAiChatUseCase(Stub(error = RuntimeException("secret detail")))("x", state, emptyList())
        assertEquals(AiError.Unknown, errorOf(r)); assertTrue(r.isFailure)
    }

    @Test fun cancellation_isNotWrapped() {
        val uc = RunAiChatUseCase(Stub(error = CancellationException("c")))
        assertThrows(CancellationException::class.java) { runBlocking { uc("x", state, emptyList()) } }
    }

    @Test fun answerJustBeforeTimeout_isSuccess() = runTest {
        val s = object : CommandSource {
            override suspend fun ask(request: AiRequest): CommandReply { delay(AiCall.TIMEOUT_MS - 1); return CommandReply("""{"actions":[]}""", "slow") }
        }
        assertTrue(RunAiChatUseCase(s)("x", state, emptyList()).isSuccess)
    }
}
