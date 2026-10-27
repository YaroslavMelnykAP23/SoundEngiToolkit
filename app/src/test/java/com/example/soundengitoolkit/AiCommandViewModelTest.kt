package com.example.soundengitoolkit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

@OptIn(ExperimentalCoroutinesApi::class)
class AiCommandViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val history = mutableListOf<Triple<String, String, Boolean>>()   // prompt, source, ok

    private fun vm(source: CommandSource) = AiCommandViewModel(RunAiCommandUseCase(source)) { p, _, _, s, ok ->
        history.add(Triple(p, s, ok))
    }

    private fun advance() = main.dispatcher.scheduler.advanceUntilIdle()

    @Test fun initialState_isIdle() {
        assertEquals(AiUiState.Idle, vm(FakeCommandSource()).state.value)
    }

    @Test fun success_idleLoadingSuccess_appliesActions_andLogsHistory() {
        val vm = vm(FakeCommandSource())
        val applied = mutableListOf<AiAction>()
        vm.run("add reverb") { a -> applied += a; "✓ reverb" }
        assertEquals(AiUiState.Loading, vm.state.value)
        advance()
        assertEquals(AiUiState.Success("✓ reverb", "fake"), vm.state.value)
        assertEquals(listOf<AiAction>(AiAction.ReverbFx(true, null, null)), applied)
        assertEquals(listOf(Triple("add reverb", "fake", true)), history)
    }

    @Test fun networkError_isErrorState_withoutApplying() {
        val vm = vm(FakeCommandSource(FakeCommandSource.Mode.NETWORK_ERROR))
        var applied = false
        vm.run("x") { applied = true; "" }
        advance()
        val s = vm.state.value as AiUiState.Error
        assertEquals(AiError.Network, s.error)
        assertTrue(s.message.isNotBlank())
        assertFalse(applied)
        assertEquals(listOf(Triple("x", "error", false)), history)
    }

    @Test fun blankPrompt_isInvalidInput_andSourceNotCalled() {
        val fake = FakeCommandSource()
        val vm = vm(fake)
        vm.run("   ") { "" }
        advance()
        assertEquals(AiError.InvalidInput, (vm.state.value as AiUiState.Error).error)
        assertEquals(0, fake.calls)
    }

    @Test fun secondRunWhileLoading_isIgnored() {
        val fake = FakeCommandSource()
        val vm = vm(fake)
        vm.run("a") { "" }
        vm.run("b") { "" }
        advance()
        assertEquals(1, fake.calls)
        assertEquals(1, history.size)
    }

    @Test fun noActions_showsNotRecognized_andIsNotOk() {
        val empty = object : CommandSource {
            override suspend fun ask(prompt: String) = CommandReply("""{"actions":[]}""", "stub")
        }
        val vm = vm(empty)
        vm.run("hello") { "" }
        advance()
        assertEquals(AiUiState.Success("No actions recognized", "stub"), vm.state.value)
        assertEquals(listOf(Triple("hello", "stub", false)), history)
    }

    @Test fun retryAfterError_goesToSuccess() {
        val fake = FakeCommandSource(FakeCommandSource.Mode.NETWORK_ERROR)
        val vm = vm(fake)
        vm.run("add reverb") { "ok" }
        advance()
        assertTrue(vm.state.value is AiUiState.Error)
        fake.mode = FakeCommandSource.Mode.SUCCESS
        vm.run("add reverb") { "ok" }
        assertEquals(AiUiState.Loading, vm.state.value)
        advance()
        assertEquals(AiUiState.Success("ok", "fake"), vm.state.value)
    }

    @Test fun slowSource_staysLoading_andRepeatedRunIsIgnored() {
        val fake = FakeCommandSource(delayMs = 1000)
        val vm = vm(fake)
        vm.run("a") { "" }
        main.dispatcher.scheduler.advanceTimeBy(500)
        main.dispatcher.scheduler.runCurrent()
        assertEquals(AiUiState.Loading, vm.state.value)      // still waiting for the source
        vm.run("b") { "" }                                   // must be ignored
        advance()
        assertEquals(1, fake.calls)
        assertEquals(1, history.size)
        assertTrue(vm.state.value is AiUiState.Success)
    }

    // ---- Retry (step 7) ----

    @Test fun retry_afterFailure_rerunsSamePrompt_andSucceeds() {
        val fake = FakeCommandSource(FakeCommandSource.Mode.FAIL_ONCE)
        val vm = vm(fake)
        vm.run("add reverb") { "ok" }
        advance()
        val err = vm.state.value as AiUiState.Error
        assertEquals(AiError.Network, err.error)
        assertTrue(err.retryable)
        vm.retry { "ok" }
        assertEquals(AiUiState.Loading, vm.state.value)
        advance()
        assertEquals(AiUiState.Success("ok", "fake"), vm.state.value)
        assertEquals(2, fake.calls)
        assertEquals(listOf("add reverb", "add reverb"), history.map { it.first })   // same prompt, not new text
    }

    @Test fun retry_afterSecondFailure_staysRetryable() {
        val fake = FakeCommandSource(FakeCommandSource.Mode.NETWORK_ERROR)
        val vm = vm(fake)
        vm.run("x") { "" }
        advance()
        vm.retry { "" }
        advance()
        assertTrue((vm.state.value as AiUiState.Error).retryable)
        assertEquals(2, fake.calls)
    }

    @Test fun invalidInput_isNotRetryable_andRetryDoesNothing() {
        val fake = FakeCommandSource()
        val vm = vm(fake)
        vm.run("   ") { "" }
        advance()
        assertFalse((vm.state.value as AiUiState.Error).retryable)
        vm.retry { "" }
        advance()
        assertEquals(0, fake.calls)
    }

    @Test fun retry_withoutFailure_doesNothing() {
        val fake = FakeCommandSource()
        val vm = vm(fake)
        vm.retry { "" }
        advance()
        assertEquals(AiUiState.Idle, vm.state.value)
        assertEquals(0, fake.calls)
    }

    @Test fun retry_afterSuccess_doesNothing() {
        val fake = FakeCommandSource()
        val vm = vm(fake)
        vm.run("add reverb") { "ok" }
        advance()
        vm.retry { "ok" }
        advance()
        assertEquals(1, fake.calls)
    }
}
