package com.example.soundengitoolkit.ai.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.example.soundengitoolkit.ai.data.FakeCommandSource
import com.example.soundengitoolkit.ai.domain.ActionExecutor
import com.example.soundengitoolkit.ai.domain.AiError
import com.example.soundengitoolkit.ai.domain.ChatAnswer
import com.example.soundengitoolkit.ai.domain.FxSnapshot
import com.example.soundengitoolkit.ai.domain.RunAiChatUseCase
import com.example.soundengitoolkit.audio.Engine

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val engine = Engine()
    private val read = { FxSnapshot.from(engine) }
    private val apply = { a: List<com.example.soundengitoolkit.ai.domain.AiAction> -> ActionExecutor.apply(engine, a); Unit }
    private val history = mutableListOf<Pair<String, Boolean>>()   // source, ok

    private fun vm(fake: FakeCommandSource) = AiChatViewModel(RunAiChatUseCase(fake)) { _, _, _, s, ok -> history += s to ok }
    private fun advance() = main.dispatcher.scheduler.advanceUntilIdle()

    @Test fun success_userBubbleThenChangedAnswer_andEngineChanged() {
        val vm = vm(FakeCommandSource())
        vm.send("add reverb", read, apply); advance()
        val m = vm.state.value.messages
        assertEquals(2, m.size)
        assertEquals("add reverb", (m[0] as ChatMessage.User).text)
        val a = (m[1] as ChatMessage.Answer).answer as ChatAnswer.Changed
        assertEquals("Reverb", a.changes[0].param)
        assertTrue(engine.reverb.on)
        assertFalse(vm.state.value.loading)
        assertEquals(listOf("chat:fake" to true), history)
    }

    @Test fun loading_isShownWhileWaiting_andEnds() {
        val vm = vm(FakeCommandSource(delayMs = 1000))
        vm.send("x", read, apply)
        main.dispatcher.scheduler.advanceTimeBy(500)
        assertTrue(vm.state.value.loading)
        advance()
        assertFalse(vm.state.value.loading)
    }

    @Test fun secondSend_whileLoading_isIgnored() {
        val fake = FakeCommandSource(delayMs = 1000)
        val vm = vm(fake)
        vm.send("a", read, apply); vm.send("b", read, apply); advance()
        assertEquals(1, fake.calls)
        assertEquals(1, vm.state.value.messages.count { it is ChatMessage.User })
    }

    // AC-6: offline -> error with Retry; Retry replaces the error bubble and does not duplicate the user bubble
    @Test fun offline_thenRetry_succeeds() {
        val fake = FakeCommandSource(FakeCommandSource.Mode.NETWORK_ERROR)
        val vm = vm(fake)
        vm.send("add reverb", read, apply); advance()
        val f = vm.state.value.messages.last() as ChatMessage.Failure
        assertEquals(AiError.Network, f.error); assertTrue(vm.state.value.canRetry)
        fake.mode = FakeCommandSource.Mode.SUCCESS
        vm.retry(read, apply); advance()
        val m = vm.state.value.messages
        assertEquals(2, m.size)
        assertTrue(m[1] is ChatMessage.Answer)
        assertFalse(vm.state.value.canRetry)
    }

    // AC-6: slow model -> Timeout after 20 s (virtual), never an endless "thinking"
    @Test fun slowModel_isTimeout_notEndlessLoading() {
        val vm = vm(FakeCommandSource(delayMs = 60_000))
        vm.send("x", read, apply); advance()
        assertEquals(AiError.Timeout, (vm.state.value.messages.last() as ChatMessage.Failure).error)
        assertFalse(vm.state.value.loading)
    }

    @Test fun configError_isAiUnavailable_onlyManualRetry() {
        val fake = FakeCommandSource(FakeCommandSource.Mode.UNAVAILABLE)
        val vm = vm(fake)
        vm.send("x", read, apply); advance()
        assertEquals(AiError.AiUnavailable, (vm.state.value.messages.last() as ChatMessage.Failure).error)
        assertEquals(1, fake.calls)                                     // no automatic repeats
    }

    @Test fun busy_isBusy() {
        val vm = vm(FakeCommandSource(FakeCommandSource.Mode.BUSY))
        vm.send("x", read, apply); advance()
        assertEquals(AiError.Busy, (vm.state.value.messages.last() as ChatMessage.Failure).error)
    }

    @Test fun blank_isInvalidInput_notRetryable() {
        val vm = vm(FakeCommandSource())
        vm.send("   ", read, apply); advance()
        val f = vm.state.value.messages.last() as ChatMessage.Failure
        assertEquals(AiError.InvalidInput, f.error); assertFalse(f.retryable)
    }

    @Test fun noActions_isNothingChanged_withModelReply() {
        val vm = vm(FakeCommandSource(FakeCommandSource.Mode.NO_ACTIONS))
        vm.send("weather?", read, apply); advance()
        val a = (vm.state.value.messages.last() as ChatMessage.Answer).answer as ChatAnswer.NothingChanged
        assertTrue(a.message.startsWith("I can change"))
        assertEquals(listOf("chat:fake" to false), history)
    }

    // The second request carries the first one as history (for "more", "undo")
    @Test fun history_isSentWithNextRequest() {
        val fake = FakeCommandSource()
        val vm = vm(fake)
        vm.send("add reverb", read, apply); advance()
        vm.send("more", read, apply); advance()
        val h = fake.lastRequest!!.history
        assertEquals(1, h.size)
        assertEquals("add reverb", h[0].prompt)
        assertEquals("Reverb off→on", h[0].result)
        assertTrue(fake.lastRequest!!.state!!.reverbOn)                 // state after the first change
    }
}
