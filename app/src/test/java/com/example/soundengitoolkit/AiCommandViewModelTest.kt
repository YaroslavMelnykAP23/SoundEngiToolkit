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
}